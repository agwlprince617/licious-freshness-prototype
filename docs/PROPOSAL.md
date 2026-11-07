# Fresh for your plans

**A proposed experiment for Licious: a freshness promise tied to when the customer will cook.**

Independent candidate prototype · 7 November 2026

**Customer question:** Can I confidently buy this product for the time I intend to use it?

**Customer need to validate:** A customer planning a meal may benefit from a clear promise about usable product life for the intended cooking time. This prototype explores that question through batch eligibility and delivery choices. Whether the signed-in Licious app already supports the flow remains unverified.

**Proposed experience:** The customer optionally chooses a cooking time. Product and delivery options are checked against approved batch-release, expiry, storage and quality rules. An eligible batch is reserved, and the promise is revalidated when accepted. Production fulfillment would need checks at allocation and dispatch too.

**Working demonstration:** An interactive customer flow and Java 21 reference API show how tomorrow’s meal can need a different delivery slot, how the earliest eligible batch is selected, and how a quality hold blocks an earlier quote. The implementation also covers expiring holds, quote/cart binding, concurrent reservations and idempotent retries.

**Validation:** 17 Java tests, 18 cross-language decision combinations, two browser-model checks and 10 HTTP checks passed. The local UI was checked for the planned-meal, reservation/retry and quality-change flows, including mobile sizing. The hosted version runs a browser reference model; the downloadable project runs the actual Java API locally.

**Potential business value:** Increase completion of planned purchases, match eligible earlier-dated inventory to nearer-term meals, and improve repeat purchasing through a clearer promise. These remain hypotheses. A promise can also increase picking constraints, waste, delivery costs and customer credits.

**Business test:** Establish meaningful planned-meal demand, verify current capabilities and reliable batch data, then compare delivered-order contribution and repeat behavior alongside waste and promise costs. Stop if the need is already met or costs outweigh benefits.

**Request:** Feedback from a relevant product or engineering owner on whether this is a useful problem for a bounded experiment.

All inventory, prices, dates and policy parameters are simulated. No customer data, payments or Licious systems are connected. No revenue uplift, production readiness or industry-first claim is made.
