package dev.fiyin.orderflow;

import dev.fiyin.orderflow.domain.*;
import dev.fiyin.orderflow.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired CheckoutService checkout;

    @Test void realDemoPasswordCanSignIn() throws Exception {
        mvc.perform(formLogin().user("student").password("learn1234"))
            .andExpect(authenticated().withUsername("student"));
    }
    @Test void wrongPasswordCannotSignIn() throws Exception {
        mvc.perform(formLogin().user("student").password("wrong-password"))
            .andExpect(unauthenticated());
    }

    @Test void anonymousApiAccessIsDenied() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
    @Test void shoppersCannotReadOperatorMetrics() throws Exception {
        mvc.perform(get("/api/admin/metrics").with(user("student").roles("SHOPPER"))).andExpect(status().isForbidden());
    }
    @Test void shoppersCannotRecoverOrders() throws Exception {
        mvc.perform(post("/api/admin/orders/unknown/recover").with(user("student").roles("SHOPPER")).with(csrf()))
            .andExpect(status().isForbidden());
    }
    @Test void changesNeedACsrfToken() throws Exception {
        mvc.perform(post("/api/orders").with(user("student")).contentType("application/json")
            .header("Idempotency-Key","security-key-123")
            .content("{\"productId\":1,\"quantity\":1,\"scenario\":\"SUCCESS\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void invalidQuantityIsRejectedAtTheApi() throws Exception {
        mvc.perform(post("/api/orders").with(user("student")).with(csrf()).contentType("application/json")
            .header("Idempotency-Key","security-key-123")
            .content("{\"productId\":1,\"quantity\":-2,\"scenario\":\"SUCCESS\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void anotherCustomerCannotReadYourTimeline() throws Exception {
        var order = checkout.checkout("student",UUID.randomUUID().toString(),new CheckoutRequest(2,1,Scenario.SUCCESS));
        mvc.perform(get("/api/orders/"+order.id()+"/events").with(user("friend")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/"+order.id()+"/events").with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk());
    }
}
