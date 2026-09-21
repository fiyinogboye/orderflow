# Experiments you can repeat

## 1. Double-click without buying twice

Sign in as student. Choose a product, quantity 1, and Accept the payment. Place the order, then place it again without changing the key or details.

Expected: same order ID, one stock deduction, one receipt. Now change the quantity and submit with the same key. Expected: a conflict message. Click New key when you want a genuinely new purchase.

## 2. A payment reply disappears

Choose Charge, but lose the reply and a new key. Open the order timeline.

Expected: attempt 1 saves a receipt and schedules a retry. Attempt 2 finds that receipt and sets PAID. Sign in as operator to compare the charges counter; it increases by one, not two.

## 3. Permanent timeout and operator review

Choose Keep timing out with a new key. Wait roughly 10–15 seconds in the default configuration. The order becomes DEAD after three attempts. Stock remains reserved.

Log out and sign in as operator. Select Provider recovered → retry. Expected: the next attempt succeeds, the total attempt count becomes 4, and stock is not reserved again.

Repeat with a fresh order but choose Cancel order instead. Expected: CANCELLED and stock returned once. Do not click Recover for that cancelled order; terminal orders do not offer that action.

## 4. Concurrency without relying on fast clicking

```bash
mvn -Dtest=ReliabilityTest#concurrentCustomersCannotBuyMoreThanTheStock test
mvn -Dtest=ReliabilityTest#concurrentRetriesOfOneCheckoutCreateOneOrder test
mvn -Dtest=ReliabilityTest#concurrentDuplicateMessagesChargeOnlyOnce test
```

Read the assertions before changing the code. They describe the rules the app must keep true. These tests use temporary data, not your running app's file database.

## 5. Broker goes offline — Docker mode

Start the full stack with Compose, then in another terminal in the same project folder:

```bash
docker compose stop rabbit
```

Place a successful order in the app. Expected: it stays PENDING; the operator's unsent jobs count rises. The app logs publish failures. The health endpoint may report DOWN because RabbitMQ is unavailable; checkout can still save durable work in PostgreSQL.

```bash
docker compose start rabbit
```

Wait for broker startup and reconnection. Expected: the saved job is eventually delivered and the order becomes PAID. No second checkout is needed.

## 6. App restart with data kept

In Docker mode, stop RabbitMQ, place an order, and restart just the app:

```bash
docker compose restart app
docker compose start rabbit
```

Expected: the database still contains the pending order and outbox job; it can finish after the broker returns. Named volumes keep data across ordinary restarts.

## 7. Customer privacy

Create an order as student. Log in as friend in a private browser window. Friend's order list should not contain student's order. The security test also tries reading another user's timeline directly and expects 404.

## 8. Poison message — infrastructure test

With Docker Desktop running:

```bash
mvn -Pinfra -Dit.test=RabbitDeliveryIT verify
```

The test publishes an unknown event ID and expects it in the dead-letter queue after listener retries. The temporary test broker is separate from your Compose broker. This is a technical processing failure, not the simulated payment timeout used for DEAD orders.
