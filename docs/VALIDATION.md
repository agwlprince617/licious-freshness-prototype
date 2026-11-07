# Validation record

Performed on 7 November 2026 with JDK 21 and Node.js 24.

- 17 Java domain/concurrency tests passed, including 32 customers competing for three packs and 32 concurrent retries producing one reservation.
- 18 Java/browser-reference combinations matched for cooking time, delivery slot and quality state.
- Two browser-model checks passed for idempotent replay and quality-hold rejection/recovery.
- 10 HTTP checks passed, including UI runtime selection, empty-query reset, delivery eligibility, quality revalidation, reservation, retry behavior, stock decrement and cart binding.
- Browser verification covered tomorrow’s meal requiring another slot, reservation and replay, quality hold after quoting, rejection, refresh and a feasible next-day alternative.
- The pitch page and navigation were checked. Responsive page width matched the 390 px mobile viewport without horizontal page overflow. Desktop and mobile screenshots were saved.

The browser check exposed an empty-query reset bug; it was fixed before the HTTP checks passed. The Java API uses one JVM and in-memory state. No distributed database, production load, payment/order/dispatch integration or real customer experiment was tested. The hosted demo uses a browser model, and all operational data is synthetic.
