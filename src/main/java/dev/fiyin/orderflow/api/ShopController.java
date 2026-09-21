package dev.fiyin.orderflow.api;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.*;
import dev.fiyin.orderflow.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ShopController {
    private final ShopRepository shop;
    private final CheckoutService checkout;
    private final RecoveryService recovery;
    private final String transport;
    public ShopController(ShopRepository shop, CheckoutService checkout, RecoveryService recovery,
                          @Value("${orderflow.transport}") String transport) {
        this.shop=shop; this.checkout=checkout; this.recovery=recovery; this.transport=transport;
    }
    private boolean operator(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"));
    }
    @GetMapping("/session")
    public Map<String,Object> session(Authentication auth, CsrfToken csrf) {
        return Map.of("username",auth.getName(),"operator",operator(auth),"transport",transport,
            "csrfHeader",csrf.getHeaderName(),"csrfToken",csrf.getToken());
    }
    @GetMapping("/products") public List<ShopRepository.Product> products() { return shop.products(); }
    @GetMapping("/orders") public List<ShopOrder> orders(Authentication auth) { return shop.orders(auth.getName(),operator(auth)); }
    @PostMapping("/orders")
    public ShopOrder create(Authentication auth, @RequestHeader("Idempotency-Key") String key,
                            @Valid @RequestBody CheckoutRequest request) {
        return checkout.checkout(auth.getName(),key,request);
    }
    @GetMapping("/orders/{id}/events")
    public List<ShopRepository.TimelineEntry> events(@PathVariable String id, Authentication auth) {
        ShopOrder order = shop.order(id,false);
        if (!operator(auth) && !order.username().equals(auth.getName())) throw new ShopException(404,"Order not found.");
        return shop.timeline(id);
    }
    @GetMapping("/admin/metrics") public ShopRepository.Metrics metrics() { return shop.metrics(); }
    @PostMapping("/admin/orders/{id}/recover") public ShopOrder recover(@PathVariable String id) { return recovery.recover(id); }
    @PostMapping("/admin/orders/{id}/cancel") public ShopOrder cancel(@PathVariable String id) { return recovery.cancel(id); }
    @PostMapping("/admin/orders/{id}/redeliver") public Map<String,String> redeliver(@PathVariable String id) {
        recovery.redeliver(id); return Map.of("message","Outstanding job queued again.");
    }
}
