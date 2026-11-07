# Buy for when you’ll cook

**A proposed Licious experiment: a freshness promise tied to the customer’s intended meal.**

Independent candidate concept · 7 November 2026

A customer planning tomorrow’s dinner needs to know whether the pack they receive will support that meal.

## What I observed

On the [public Licious wings page reviewed](https://www.licious.in/marinades/crispy-chicken-wings-7-pieces-pr_mc6l4zmw1so), the description gives a general four-day shelf-life instruction. I did not see a minimum remaining life at delivery or a promise covering a chosen cooking time. This is an observed gap in that public view—not evidence of unsafe handling. The signed-in app and checkout remain unverified.

## What changes

| Observed public view | Proposed experience |
| --- | --- |
| General shelf-life guidance, without a visible promise for the intended meal. | The cooking time determines eligible products, slots and batches. |
| Customer question: “Will the pack I receive cover tomorrow’s dinner?” | Proposed promise: “This delivery option can support your selected mealtime.” |

A delivery slot answers **when it arrives**. The proposed promise covers **the usable life remaining for the intended meal**.

1. Customer selects **tonight, tomorrow, or a cooking time**.
2. Licious shows products and delivery slots that can meet that requirement.
3. The backend allocates an eligible batch using actual expiry, release and quality information under approved storage rules.
4. It rechecks before dispatch and handles any change explicitly with the customer.

For example, if an early-delivery batch cannot cover tomorrow’s dinner, offer a later delivery with an eligible batch. Earlier-dated stock can still serve customers cooking sooner, where approved rules allow.

## Why this could create value

- **Conversion:** give planned-meal buyers confidence to purchase by answering their freshness question before checkout.
- **Inventory efficiency:** match earlier-dated eligible stock with customers cooking sooner, preserving later-dated stock for later meals.
- **Repeat purchasing:** make freshness a useful, verifiable part of the buying experience that customers can return for.

These are hypotheses, not measured outcomes. First confirm planned-meal demand, current app capabilities and reliable batch data. A bounded pilot should measure delivered-order conversion, repeat purchasing and contribution after extra picking constraints, fulfillment costs, waste, refunds and guarantee costs.

**Reject the idea** if customers mostly cook immediately, the capability already exists, or the added costs erase the benefit.

## What is already built

The browser prototype and Java 21 reference API demonstrate cooking-time eligibility, alternate delivery slots, earliest eligible batch selection, expiring holds and rejection after a batch’s quality status changes. The Java implementation also covers concurrent reservations, idempotent retries and quote/cart binding.

The hosted demo runs a browser reference model; the downloadable project runs the Java API locally. The prototype rechecks at acceptance. Production dispatch checks require fulfillment integration and approved operating rules. All inventory and policy parameters are simulated; no Licious systems are connected.

Existing validation covers 17 Java tests, 18 cross-language decision combinations, two browser-model checks and 10 HTTP checks. No revenue uplift, production readiness or industry-first claim is made.

**Question for the team:** Is this an unmet customer need worth a bounded experiment, or does it overlap with work already underway?

[Read the proposal and try the demo](https://agwlprince617.github.io/licious-freshness-prototype/proposal.html) · [Source](https://github.com/agwlprince617/licious-freshness-prototype)
