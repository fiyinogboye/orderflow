package dev.fiyin.orderflow;

import dev.fiyin.orderflow.data.ShopRepository;
import dev.fiyin.orderflow.domain.*;
import dev.fiyin.orderflow.messaging.OutboxPublisher;
import dev.fiyin.orderflow.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

// Run these same tests against H2 locally and PostgreSQL with the infra profile.
public abstract class ReliabilityCases {
    @Autowired CheckoutService checkout;
    @Autowired PaymentWorker worker;
    @Autowired RecoveryService recovery;
    @Autowired ShopRepository shop;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void cleanDatabase() {
        db.update("DELETE FROM payments");
        db.update("DELETE FROM order_events");
        db.update("DELETE FROM outbox");
        db.update("DELETE FROM shop_orders");
        db.update("UPDATE products SET stock=8,price_cents=7900 WHERE id=1");
    }
    ShopOrder order(Scenario scenario) {
        return checkout.checkout("student",UUID.randomUUID().toString(),new CheckoutRequest(1,1,scenario));
    }
    String event(ShopOrder order, int attempt) {
        return db.queryForObject("SELECT id FROM outbox WHERE order_id=? AND attempt=?",String.class,order.id(),attempt);
    }
    int stock() { return db.queryForObject("SELECT stock FROM products WHERE id=1",Integer.class); }
    int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class); }

    @Test
    void sameKeyReturnsTheSameOrderAndReservesOnlyOnce() {
        var request = new CheckoutRequest(1,2,Scenario.SUCCESS);
        var first = checkout.checkout("student","repeat-key-123",request);
        var second = checkout.checkout("student","repeat-key-123",request);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(stock()).isEqualTo(6);
        assertThat(count("shop_orders")).isEqualTo(1);
        assertThat(count("outbox")).isEqualTo(1);
    }
    @Test
    void aKeyCannotSilentlyMeanADifferentRequest() {
        checkout.checkout("student","repeat-key-123",new CheckoutRequest(1,1,Scenario.SUCCESS));
        assertThatThrownBy(() -> checkout.checkout("student","repeat-key-123",new CheckoutRequest(1,2,Scenario.SUCCESS)))
            .isInstanceOf(ShopException.class).hasMessageContaining("different details");
        assertThat(stock()).isEqualTo(7);
    }
    @Test
    void differentCustomersCanUseTheSameKey() {
        var request = new CheckoutRequest(1,1,Scenario.SUCCESS);
        var first = checkout.checkout("student","shared-key-123",request);
        var second = checkout.checkout("friend","shared-key-123",request);
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(stock()).isEqualTo(6);
    }
    @Test
    void invalidQuantityCannotIncreaseStock() {
        assertThatThrownBy(() -> checkout.checkout("student","invalid-key-123",new CheckoutRequest(1,-2,Scenario.SUCCESS)))
            .isInstanceOf(ShopException.class);
        assertThat(stock()).isEqualTo(8);
    }
    @Test
    void laterFailureRollsBackTheEarlierStockChange() {
        db.update("UPDATE products SET price_cents=? WHERE id=1",Long.MAX_VALUE);
        assertThatThrownBy(() -> checkout.checkout("student","overflow-key-123",new CheckoutRequest(1,2,Scenario.SUCCESS)))
            .isInstanceOf(ArithmeticException.class);
        assertThat(stock()).isEqualTo(8);
        assertThat(count("shop_orders")).isZero();
        assertThat(count("outbox")).isZero();
    }
    @Test
    void concurrentCustomersCannotBuyMoreThanTheStock() throws Exception {
        db.update("UPDATE products SET stock=5 WHERE id=1");
        String prefix = UUID.randomUUID().toString().substring(0,8);
        for (int i=0;i<16;i++) db.update("INSERT INTO customers VALUES (?)",prefix+i);
        var results = race(16, i -> {
            try {
                checkout.checkout(prefix+i,"race-key-123",new CheckoutRequest(1,1,Scenario.SUCCESS));
                return true;
            } catch (ShopException exception) {
                assertThat(exception.status()).isEqualTo(409);
                return false;
            }
        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
        assertThat(stock()).isZero();
        assertThat(count("shop_orders")).isEqualTo(5);
    }
    @Test
    void concurrentRetriesOfOneCheckoutCreateOneOrder() throws Exception {
        var ids = race(12, i -> checkout.checkout("student","concurrent-key-123",new CheckoutRequest(1,1,Scenario.SUCCESS)).id());
        assertThat(new HashSet<>(ids)).hasSize(1);
        assertThat(stock()).isEqualTo(7);
        assertThat(count("outbox")).isEqualTo(1);
    }
    @Test
    void concurrentDuplicateMessagesChargeOnlyOnce() throws Exception {
        var order = order(Scenario.SUCCESS);
        String eventId = event(order,1);
        race(12, i -> { worker.process(eventId); return true; });
        assertThat(count("payments")).isEqualTo(1);
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID");
        assertThat(shop.order(order.id(),false).attempts()).isEqualTo(1);
    }
    @Test
    void declineReturnsStockOnlyOnce() {
        var order = order(Scenario.DECLINE);
        worker.process(event(order,1));
        worker.process(event(order,1));
        assertThat(stock()).isEqualTo(8);
        assertThat(count("payments")).isZero();
        assertThat(shop.order(order.id(),false).status()).isEqualTo("DECLINED");
    }
    @Test
    void aTemporaryTimeoutRetriesAndSucceeds() {
        var order = order(Scenario.TIMEOUT_ONCE);
        worker.process(event(order,1));
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PENDING");
        worker.process(event(order,2));
        worker.process(event(order,1)); // An old message arrives late.
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID");
        assertThat(shop.order(order.id(),false).attempts()).isEqualTo(2);
        assertThat(count("payments")).isEqualTo(1);
    }
    @Test
    void lostReplyUsesTheExistingReceipt() {
        var order = order(Scenario.CHARGE_THEN_TIMEOUT);
        worker.process(event(order,1));
        assertThat(count("payments")).isEqualTo(1);
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PENDING");
        worker.process(event(order,2));
        assertThat(count("payments")).isEqualTo(1);
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID");
        assertThat(shop.timeline(order.id()).get(shop.timeline(order.id()).size()-1).message()).contains("No second charge");
    }
    @Test
    void exhaustedWorkKeepsStockAndCanRecover() {
        var order = order(Scenario.ALWAYS_TIMEOUT);
        for (int i=1;i<=3;i++) worker.process(event(order,i));
        assertThat(shop.order(order.id(),false).status()).isEqualTo("DEAD");
        assertThat(stock()).isEqualTo(7);
        assertThat(count("outbox")).isEqualTo(3);
        recovery.recover(order.id());
        assertThatThrownBy(() -> recovery.recover(order.id())).isInstanceOf(ShopException.class);
        worker.process(event(order,4));
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID");
        assertThat(count("payments")).isEqualTo(1);
        assertThat(stock()).isEqualTo(7);
    }
    @Test
    void cancellationReturnsStockOnlyOnceAndStaleEventsDoNothing() {
        var order = order(Scenario.ALWAYS_TIMEOUT);
        for (int i=1;i<=3;i++) worker.process(event(order,i));
        recovery.cancel(order.id());
        assertThatThrownBy(() -> recovery.cancel(order.id())).isInstanceOf(ShopException.class);
        worker.process(event(order,3));
        assertThat(stock()).isEqualTo(8);
        assertThat(count("payments")).isZero();
        assertThat(shop.order(order.id(),false).status()).isEqualTo("CANCELLED");
    }
    @Test
    void anUnavailableTransportLeavesWorkInTheOutbox() {
        order(Scenario.SUCCESS);
        new OutboxPublisher(shop,id -> { throw new IllegalStateException("Broker offline"); }).publishReady();
        assertThat(shop.ready()).hasSize(1);
        assertThat(count("payments")).isZero();
        new OutboxPublisher(shop,worker::process).publishReady();
        assertThat(shop.ready()).isEmpty();
        assertThat(count("payments")).isEqualTo(1);
    }
    @Test
    void aLostPublishConfirmationCanSafelyCauseRedelivery() {
        order(Scenario.SUCCESS);
        new OutboxPublisher(shop,id -> {
            worker.process(id);
            throw new IllegalStateException("Lost confirmation after processing");
        }).publishReady();
        assertThat(shop.ready()).hasSize(1);
        new OutboxPublisher(shop,worker::process).publishReady();
        assertThat(shop.ready()).isEmpty();
        assertThat(count("payments")).isEqualTo(1);
    }
    @Test
    void pendingJobCanBeRedeliveredAfterInfrastructureFailure() {
        var order = order(Scenario.SUCCESS);
        shop.sent(event(order,1));
        assertThat(shop.ready()).isEmpty();
        recovery.redeliver(order.id());
        assertThat(shop.ready()).hasSize(1);
        worker.process(event(order,1));
        assertThat(shop.order(order.id(),false).status()).isEqualTo("PAID");
    }
    private <T> List<T> race(int people, java.util.function.IntFunction<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(people);
        var ready = new CountDownLatch(people);
        var start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i=0;i<people;i++) {
                int number=i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return action.apply(number);
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future:futures) results.add(future.get(20,TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }
}
