# Learn OrderFlow a little at a time

You do not need to understand every file on day one. Treat each stage as a few short sessions. Six weeks is only a suggested pace; move on when you can explain the current stage in your own words.

## Week 1 — run it and follow one order

1. Run the app and place a successful order.
2. Write down the stock before and after. Place a declined order and compare.
3. Open `CheckoutRequest.java`. A Java record is a small object for carrying values; here it carries product ID, quantity, and a simulation choice.
4. Open `ShopController.java`. Find the method with `@PostMapping("/orders")`. That is where the browser's order request arrives.
5. Follow its call to `CheckoutService.checkout`.

Small change: add a fourth fictional product using a **new** `V2__add_product.sql` migration. Do not edit V1 once it has already run on a database.

Explain it aloud: “The browser sends a product ID and quantity. The server looks up the price, so the browser cannot choose a cheaper price.”

## Week 2 — SQL and transactions

Read `V1__shop.sql` beside `ShopRepository.java`. Match each INSERT or UPDATE to a table.

- `products` holds the current available stock.
- `shop_orders` holds a purchase and its state.
- `outbox` holds payment work that needs delivery.
- `payments` is the fake provider's receipt ledger.
- `order_events` is the readable timeline.

`@Transactional` means the work in that service method should commit together. If a runtime exception escapes, the writes roll back together.

Run only the rollback test:

```bash
mvn -Dtest=ReliabilityTest#laterFailureRollsBackTheEarlierStockChange test
```

Why use a deliberately huge price there? It causes a failure **after** the stock update, so the test can check that the earlier update was undone.

Small change: add a maximum allowed order value and a useful error message. Test the boundary and verify rejected orders do not consume stock.

## Week 3 — two requests at the same time

Imagine there is one lamp left. Two people both read “1 left” before either writes “0 left.” A separate read-then-write can sell two lamps.

Find this statement in `reserve`:

```sql
UPDATE products SET stock=stock-? WHERE id=? AND stock>=?
```

The stock check and change happen together. If no row changes, there was not enough stock (or the product did not exist).

Now read `concurrentCustomersCannotBuyMoreThanTheStock`. The latch is just a starting gate so the threads begin together. Read `race` last; you can understand the test's goal before every detail of its helper.

Then read `lockCustomer` and `previous`. A request key answers: “Have I already accepted this exact request from this customer?” It is not a login token.

Small change: build a multi-product cart. Sort product IDs before taking locks, reserve every item in one transaction, and test that a shortage in the second item rolls back the first reservation. Explain why a stable lock order matters.

## Week 4 — work that happens later

Read `OutboxPublisher`, `PaymentTransport`, and `LocalTransport`.

Why not save an order and then send a message directly? The app could crash between those steps. The database would contain an order but no payment job. The outbox puts the order and the job in the same transaction.

Why can the publisher send twice? The message may reach the broker just before the app crashes, before it records `sent=true`. On restart it still sees an unsent job.

Read the first few lines of `PaymentWorker.process`. The order lock makes workers take turns; the expected attempt number rejects old or repeated work.

Small change: add a “jobs waiting” count visible to the current shopper only. Do not expose other customers' orders.

## Week 5 — payments that are unclear

Try “Charge, but lose the reply.” Read `lostReplyUsesTheExistingReceipt`.

A timeout only means we did not get an answer in time. It does not prove the other side did nothing. This is why blindly retrying a payment can be dangerous.

Read the `retry` method. The first retry waits 3 seconds, the next waits 6 seconds. The schedule is saved as a database timestamp; the worker does not sleep while holding the order lock.

Try a DEAD order. Explain why stock stays reserved until review. Read `RecoveryService`.

Small change: add a reason field for operator actions. Store who acted and why in a structured audit table. Write a test that a shopper cannot use those endpoints.

## Week 6 — real infrastructure and your own extension

Start Docker Desktop and run the integration tests, then Compose. Read `RabbitTransport`.

- A publisher confirm means RabbitMQ accepted a publish. It does not mean payment is done.
- A consumer acknowledgement happens after processing succeeds.
- A return tells us a message could not be routed to a queue.
- A dead-letter queue holds messages the listener could not process after its technical retries.

Stop the broker while placing orders. Start it again and observe the outbox recover. Follow `EXPERIMENTS.md`.

Choose **one** larger extension:

1. Separate fake payment provider with its own database and an HTTP idempotency API. Reproduce a provider commit followed by an HTTP timeout.
2. Multi-product cart with transaction rollback and concurrency tests.
3. Real user registration, per-user password hashes, and persistent accounts.
4. Refund state machine with duplicate refund protection.
5. Search and pagination, including ownership checks and database indexes.

For each change, keep a short note: what was broken or missing, what you changed, how you tested it, and one tradeoff. Those notes make it easier to explain your work later.

## A few interview practice questions

- Why does a second click return the old order?
- What happens if the same key arrives with a different quantity?
- How does the database prevent overselling across two app processes?
- What is the failure window between broker confirmation and `sent=true`?
- Why is this at-least-once delivery rather than exactly-once delivery?
- Why is a payment timeout different from a payment decline?
- Which behaviours are simulated, and which use a real database or broker?
- What would you change before using a real payment provider?

Do not memorize a speech. Trace one real request through the code and use its timeline as your example.
