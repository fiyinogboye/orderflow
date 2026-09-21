# OrderFlow

A Java project for learning what happens when checkout goes wrong.

You can place a fictional order, make the payment provider time out, and watch background jobs recover. The interesting part is keeping stock, orders, and payment receipts consistent when requests arrive twice or at the same time.

All products and payments are made up. No card details are needed.

## Start here — about 5 minutes

1. Open the **orderflow** folder in VS Code. Open a terminal in that folder (the folder containing `pom.xml`).
2. Run:

   ```bash
   mvn spring-boot:run
   ```

3. Wait for a log line containing **Started OrderFlowApplication**. The terminal stays busy while the app runs. That is normal.
4. Open **http://localhost:8081**.
5. Sign in with **student** / **learn1234**.
6. Leave “Accept the payment” selected and place an order. Watch it become PAID.

The app uses port 8081 so OpsHub can keep using 8080. The first Maven command downloads dependencies and can take a few minutes. An internet connection is needed for that download.

Use Java **17** for the tested build target. Maven 3.9.x works. If your Mac uses Java 25 and you hit build problems, use JDK 17 for this project; see `docs/MAC_SETUP.md`. You do not need Docker for this first run.

Press **Control+C** in the terminal to stop the app. Orders stay in `data/` when you restart. For a fresh local database, stop the app and **rename** `data` to `data-backup`; a new database will be created on the next run. Keep the backup if you want the old orders.

## Accounts

| Username | Password | What it can do |
| --- | --- | --- |
| student | learn1234 | Place orders and see its own history |
| friend | learn1234 | A second customer for checking privacy |
| operator | learn1234 | See all orders, metrics, and recovery controls |

These are local demo accounts. Set the `DEMO_PASSWORD` environment variable to change their shared password. The simple in-memory account setup is intentional; building registration and separate stored passwords is a later exercise. The normal local app binds to your own computer. The Compose ports also bind to localhost.

## Five experiments

Use **New key** before each new purchase. Keep the same key when testing a duplicate purchase.

| Payment scenario | Expected result |
| --- | --- |
| Accept the payment | PAID after one attempt |
| Decline the payment | DECLINED; reserved stock returned |
| Time out once, then work | PENDING, then PAID after a retry |
| Charge, but lose the reply | One receipt saved on attempt 1; attempt 2 finds it and completes the order without a second charge |
| Keep timing out | Three failed attempts, then DEAD; stock remains reserved |

For a DEAD order, sign in as **operator**. “Provider recovered → retry” changes the simulation to SUCCESS and queues another attempt. “Cancel order” checks the fake receipt ledger and returns stock if nothing was charged. DEAD is a review state, not a synonym for cancelled.

To test duplicate checkout: click “Place simulated order” again with unchanged details and key. You get the same order ID. If you change the quantity but keep the key, you get a conflict instead of silently placing another order.

## What is built

- Session login, CSRF protection, customer ownership checks, and an operator role.
- Server-side price calculation in integer cents.
- Stock reservation with one conditional SQL update.
- Customer-scoped idempotency keys and request comparison.
- An order, stock change, and outbox job saved in one transaction.
- A background outbox publisher with a local transport or RabbitMQ transport.
- Duplicate message protection using an order row lock and expected attempt number.
- Payment retries with increasing delays and a three-failure budget.
- Operator recovery, cancellation, and redelivery of pending jobs.
- A payment receipt ledger, order timeline, and simple operator metrics.
- Flyway schema migration, PostgreSQL configuration, Docker Compose, and CI.

This version supports **one product type per order**, with quantity 1–10. A multi-product cart is a useful next project change.

## Run the tests

```bash
mvn test
```

This runs the database/service and security tests using H2. Tests include real concurrent Java threads, duplicate messages, transaction rollback, payment recovery, and rejected unauthorized requests.

For PostgreSQL and RabbitMQ integration tests, start Docker Desktop, then:

```bash
mvn -Pinfra verify
```

Testcontainers starts temporary databases and a broker. These tests do not use your saved demo database. The `infra` **Maven profile** includes integration tests; the `infra` **Spring profile** configures the running app. They have the same name but do different jobs.

## Run the full stack

With Docker Desktop running, from the project folder:

```bash
docker compose up --build
```

Open http://localhost:8081. Use the same app login. You do not also run `mvn spring-boot:run`; the app is already inside Docker. Stop the Maven app first if it is using port 8081.

The broker dashboard is http://localhost:15672, with **orderflow** / **local-queue-only**. Its login is different from the app login.

```bash
docker compose stop
docker compose start
```

These commands keep your named database/broker volumes. Local H2 data and Docker PostgreSQL data are separate, so switching modes does not bring your old orders across.

## Where to read first

1. `docs/LEARNING_GUIDE.md` — small steps over several weeks.
2. `docs/ARCHITECTURE.md` — why each moving part exists and where the simplifications are.
3. `docs/EXPERIMENTS.md` — repeatable failure experiments.
4. `docs/API.md` — endpoints and example request bodies.
5. `docs/MAC_SETUP.md` — setup and troubleshooting.

Start the code with `CheckoutRequest`, then `ShopController`, `CheckoutService`, and `ShopRepository`. After that, read `PaymentWorker` and `OutboxPublisher`. The comments explain the reasons for the tricky lines.

## Scope

This is a local learning reference, not a real payment service. Its fake payment receipt ledger shares the app database and transaction. That lets you reproduce a lost-reply scenario, but it does not reproduce every crash boundary of a separate payment provider. A real integration needs provider-side idempotency, receipt lookup/reconciliation, refunds, and careful handling of unknown payment outcomes.

The queues and messages are durable, but one database and one broker are not high availability. There is no claim of end-to-end exactly-once delivery or a measured production throughput. Read the architecture notes before extending it.
