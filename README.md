# Fresh for your plans

[Open the public demo](https://agwlprince617.github.io/licious-freshness-prototype/) · [Read the proposal](docs/PROPOSAL.md) · [View deployment checks](https://github.com/agwlprince617/licious-freshness-prototype/actions/workflows/pages.yml)

An independent prototype for discussing a cooking-time freshness promise with the Licious team. All products, prices, batch dates, inventory and policy parameters are simulated. No customer data, payments or Licious systems are connected.

## Run the actual Java API and UI

Requires JDK 21+ with `java` and `javac`. No dependencies, Maven, credentials or API keys are needed.

```bash
bash run-java.sh
```

Open `http://127.0.0.1:8088`. The same UI now calls the Java service. “Inspect the decision” identifies the runtime. Stop with Ctrl-C. State is in memory and resets at startup. The service binds to loopback only and is a demonstration, not a deployable commerce backend.

## Hosted demo versus Java

The hosted static site runs `dist/engine.mjs`, a JavaScript reference model in the browser. It is not a hosted Java backend. The Java implementation lives in `java/src/demo/PromiseService.java`; `Server.java` exposes it over HTTP and serves the same UI. Tests compare the two implementations across 18 cooking-time, delivery-slot and quality-state combinations.

GitHub Pages serves `dist/` after the Java, browser-model and HTTP checks pass in GitHub Actions. Pushes to `main` update the public demo automatically. `python3 tests/package-source.py` rebuilds the downloadable source archive; the deployment workflow runs it too.

The hosted model is isolated to each browser tab. The local Java service shares its synthetic inventory among its clients. Both demonstrate one sample product and one-pack reservations. The clocks used for cooking and batch dates are fixed to 7–10 November 2026; quote/hold TTLs use the runtime clock. The demo six-hour product-life buffer is invented and is not food-storage advice or a Licious policy.

## Tests

```bash
node tests/check.mjs
python3 tests/http-check.py
```

The first command needs Node.js 22+ and a JDK. It runs 17 Java tests, 18 cross-language decision combinations and two browser-model checks. The second uses Python's standard library, compiles Java, starts a temporary loopback server, runs 10 HTTP checks and stops it.

The Java tests cover cooking/slot eligibility, choosing the earliest eligible batch, quality changes, quote ownership, expiry, idempotency and races. A 32-customer race for three packs creates exactly three reservations. Thirty-two concurrent retries create one reservation.

## Present it in 60–90 seconds

1. Start with Tonight and the express delivery. Inspect the decision: batch A qualifies.
2. Select Tomorrow. The same delivery cannot support the meal, so the UI suggests a later slot.
3. Accept the suggested slot. Batch B now qualifies. Reserve the demo pack and replay the request: the reservation is unchanged.
4. Choose “A quality status changes.” This resets the fixture, quotes batch B and then places it on hold. Attempt the reservation: it is blocked.
5. Refresh and choose the suggested next-day delivery. Batch C can support the same meal.
6. Open the short pitch and discuss the actual business test: contribution after allocation, waste, fulfillment and promise costs.

No order is placed. Demo reservations expire after 90 seconds; release occurs on the next state operation. Reset deliberately clears all state. Use the synthetic scenario buttons to restart the presentation.

## Scope and production work

- Eligibility uses deterministic release/use-by data and quality status. A real rollout needs approved batch-level quality data, storage rules and reliable available-to-promise inventory.
- Acceptance rechecks the originally selected batch. Stock decrement, hold and idempotency state are atomic within one JVM; expiry is lazy. This provides no distributed consistency or crash recovery.
- Quotes are bound to a demo cart, but cart IDs are not authentication. The loopback demo has no user authentication, payment or order system. A production version requires customer authorization, durable transactions, bounded idempotency retention and audit controls.
- The demo has a fixed product, price, quantity and fulfillment node. It does not recompute a full cart, variable packed weight, fees, promotions or delivery capacity.
- An accepted hold is not a dispatch guarantee. Production order confirmation, allocation/picking checks, change consent, cancellation, compensation and durable expiry jobs remain to be implemented.
- Existing scheduling and inventory algorithms are supporting capabilities. The proposed product increment is an optional customer cooking time and an enforceable purchase-time promise. Whether Licious already supports this in the signed-in app is unverified.
- No revenue uplift, load target or business outcome is claimed. Validate need, current capability and full contribution economics before a pilot.

## Files

`dist/` contains the static website; `java/` contains the API and domain implementation; `tests/` contains parity and HTTP checks. `dist/source.zip` is a portable source download. `docs/` contains the proposal, walkthrough and validation record. Private outreach drafts and hosting credentials are excluded from this repository.
