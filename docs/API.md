# API notes

The browser already calls these routes. Use its Network tab to follow a real request before trying to write your own client.

| Method | Route | Who | Purpose |
| --- | --- | --- | --- |
| GET | `/api/session` | Signed-in user | Username, role flag, transport, CSRF header/token |
| GET | `/api/products` | Signed-in user | Product prices and available stock |
| GET | `/api/orders` | Signed-in user | Own latest 100 orders; operator sees all latest 100 |
| POST | `/api/orders` | Signed-in user | Idempotent checkout |
| GET | `/api/orders/{id}/events` | Owner or operator | Timeline |
| GET | `/api/admin/metrics` | Operator | Pending, paid, dead, unsent, and receipt counts |
| POST | `/api/admin/orders/{id}/recover` | Operator | Restore a DEAD order's fake provider and retry |
| POST | `/api/admin/orders/{id}/cancel` | Operator | Cancel a DEAD uncharged order and release stock |
| POST | `/api/admin/orders/{id}/redeliver` | Operator | Resend outstanding work for a PENDING order |
| GET | `/actuator/health` | Anyone locally | Basic health status, no component details |

## Checkout body

```json
{
  "productId": 1,
  "quantity": 1,
  "scenario": "CHARGE_THEN_TIMEOUT"
}
```

Required headers: `Content-Type: application/json`, `Idempotency-Key: <8–80 safe characters>`, and the CSRF header/token returned by `/api/session`. Keep the session cookie from login. CSRF tokens are not passwords; fetch a fresh one after login.

Scenario values: `SUCCESS`, `DECLINE`, `TIMEOUT_ONCE`, `ALWAYS_TIMEOUT`, `CHARGE_THEN_TIMEOUT`.

Successful initial and repeated checkout both return HTTP 200 with the current order. The client does not supply username, total price, payment status, or attempt count. The server controls those fields. A repeat can return a later status, such as PAID, because the same order may have progressed since the first response.

Common errors: 400 for malformed input, 401 if signed out, 403 for missing CSRF or insufficient role, 404 for unavailable/private order timelines, 409 for insufficient stock, key/body mismatch, or invalid recovery state.

## Try a request from your signed-in browser console

```javascript
const session = await fetch('/api/session').then(r => r.json());
const key = crypto.randomUUID();
const response = await fetch('/api/orders', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Idempotency-Key': key,
    [session.csrfHeader]: session.csrfToken
  },
  body: JSON.stringify({productId: 1, quantity: 1, scenario: 'SUCCESS'})
});
console.log(response.status, await response.json());
```

For a retry, reuse `key` and the same body. Do not paste browser session cookies into a public issue or commit them.
