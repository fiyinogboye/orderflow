package dev.fiyin.orderflow.data;

import dev.fiyin.orderflow.domain.*;
import dev.fiyin.orderflow.service.ShopException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class ShopRepository {
    private final JdbcTemplate db;
    public ShopRepository(JdbcTemplate db) { this.db = db; }

    private final RowMapper<ShopOrder> orderMapper = (rs, row) -> new ShopOrder(
        rs.getString("id"), rs.getString("username"), rs.getLong("product_id"),
        rs.getInt("quantity"), rs.getLong("total_cents"), Scenario.valueOf(rs.getString("scenario")),
        rs.getString("status"), rs.getInt("attempts"), rs.getInt("failures"),
        rs.getObject("created_at", OffsetDateTime.class));

    public void lockCustomer(String username) {
        // Two checkouts from this customer must take turns, even on different app servers.
        db.queryForObject("SELECT username FROM customers WHERE username=? FOR UPDATE", String.class, username);
    }

    public Optional<ShopOrder> previous(String username, String key, String signature) {
        var rows = db.query("SELECT * FROM shop_orders WHERE username=? AND request_key=?", orderMapper, username, key);
        if (rows.isEmpty()) return Optional.empty();
        String saved = db.queryForObject("SELECT request_signature FROM shop_orders WHERE id=?", String.class, rows.get(0).id());
        if (!signature.equals(saved)) throw new ShopException(409, "That request key was used with different details. Make a new key.");
        return Optional.of(rows.get(0));
    }

    public long reserve(long productId, int quantity) {
        // Checking AND changing stock in one SQL statement prevents overselling.
        int changed = db.update("UPDATE products SET stock=stock-? WHERE id=? AND stock>=?", quantity, productId, quantity);
        if (changed == 0) throw new ShopException(409, "Product unavailable or not enough stock.");
        return db.queryForObject("SELECT price_cents FROM products WHERE id=?", Long.class, productId);
    }

    public ShopOrder insert(String username, String key, CheckoutRequest request, long price) {
        String id = UUID.randomUUID().toString();
        db.update("""
            INSERT INTO shop_orders(id,username,request_key,request_signature,product_id,quantity,total_cents,scenario,status)
            VALUES (?,?,?,?,?,?,?,?,'PENDING')
            """, id, username, key, request.signature(), request.productId(), request.quantity(),
            Math.multiplyExact(price, request.quantity()), request.scenario().name());
        return order(id, false);
    }

    public ShopOrder order(String id, boolean lock) {
        var rows = db.query("SELECT * FROM shop_orders WHERE id=?" + (lock ? " FOR UPDATE" : ""), orderMapper, id);
        if (rows.isEmpty()) throw new ShopException(404, "Order not found.");
        return rows.get(0);
    }
    public List<ShopOrder> orders(String username, boolean operator) {
        return operator
            ? db.query("SELECT * FROM shop_orders ORDER BY created_at DESC LIMIT 100", orderMapper)
            : db.query("SELECT * FROM shop_orders WHERE username=? ORDER BY created_at DESC LIMIT 100", orderMapper, username);
    }
    public record Product(long id, String name, long priceCents, int stock) {}
    public List<Product> products() {
        return db.query("SELECT * FROM products ORDER BY id", (rs, n) -> new Product(rs.getLong("id"), rs.getString("name"), rs.getLong("price_cents"), rs.getInt("stock")));
    }
    public void enqueue(String orderId, int attempt, long delayMs) {
        db.update("INSERT INTO outbox(id,order_id,attempt,due_at) VALUES (?,?,?,?)",
            UUID.randomUUID().toString(), orderId, attempt, OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
    }
    public List<OutboxEvent> ready() {
        return db.query("SELECT * FROM outbox WHERE sent=FALSE AND due_at<=CURRENT_TIMESTAMP ORDER BY due_at LIMIT 20",
            (rs, n) -> new OutboxEvent(rs.getString("id"), rs.getString("order_id"), rs.getInt("attempt")));
    }
    public Optional<OutboxEvent> event(String eventId) {
        return db.query("SELECT * FROM outbox WHERE id=?", (rs,n) ->
            new OutboxEvent(rs.getString("id"), rs.getString("order_id"), rs.getInt("attempt")), eventId).stream().findFirst();
    }
    public void sent(String eventId) { db.update("UPDATE outbox SET sent=TRUE WHERE id=?", eventId); }
    public void state(String id, String status, int attempts, int failures) {
        db.update("UPDATE shop_orders SET status=?,attempts=?,failures=? WHERE id=?", status, attempts, failures, id);
    }
    public void release(ShopOrder order) {
        db.update("UPDATE products SET stock=stock+? WHERE id=?", order.quantity(), order.productId());
    }
    public boolean charged(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?", Integer.class, id) > 0;
    }
    public void charge(ShopOrder order) {
        // The worker holds this order's row lock. A second worker cannot charge it at the same time.
        db.update("INSERT INTO payments(order_id,total_cents) VALUES (?,?)", order.id(), order.totalCents());
    }
    public void note(String id, String text) { db.update("INSERT INTO order_events(order_id,message) VALUES (?,?)", id, text); }
    public record TimelineEntry(long id, String message, OffsetDateTime createdAt) {}
    public List<TimelineEntry> timeline(String id) {
        return db.query("SELECT * FROM order_events WHERE order_id=? ORDER BY id", (rs,n) ->
            new TimelineEntry(rs.getLong("id"),rs.getString("message"),rs.getObject("created_at",OffsetDateTime.class)), id);
    }
    public void recover(String id) {
        db.update("UPDATE shop_orders SET status='PENDING',failures=0,scenario='SUCCESS' WHERE id=?", id);
    }
    public void redeliver(String id, int attempt) {
        db.update("UPDATE outbox SET sent=FALSE,due_at=CURRENT_TIMESTAMP WHERE order_id=? AND attempt=?", id, attempt);
    }
    public record Metrics(int pending, int paid, int dead, int unsent, int charges) {}
    public Metrics metrics() {
        return new Metrics(count("SELECT COUNT(*) FROM shop_orders WHERE status='PENDING'"),
            count("SELECT COUNT(*) FROM shop_orders WHERE status='PAID'"),
            count("SELECT COUNT(*) FROM shop_orders WHERE status='DEAD'"),
            count("SELECT COUNT(*) FROM outbox WHERE sent=FALSE"), count("SELECT COUNT(*) FROM payments"));
    }
    private int count(String sql) { return db.queryForObject(sql, Integer.class); }
}
