# A 60–90 second walkthrough

Use the hosted review demo for a product discussion, or run `bash run-java.sh` from the source package to demonstrate the actual Java API behind the same UI.

**0–15 seconds — the customer question**

“This is an independent prototype using simulated inventory. The question is whether a freshness promise for when a customer will cook could help planned purchases.”

Start with **Tonight** and the express delivery. Open **Inspect the decision** to show that batch A qualifies.

**15–35 seconds — tomorrow’s meal**

Select **Tomorrow**. The current delivery cannot support the cooking time from the available fixture stock. Choose the suggested later delivery: batch B qualifies. Reserve the demo pack, then use **Replay the same request** to show that the reservation ID is unchanged.

**35–65 seconds — a promise must survive change**

Choose **A quality status changes**. The scenario resets inventory, quotes batch B, then places it on hold. Click **Reserve a demo pack**: acceptance is blocked. Refresh the options and accept the suggested next-day delivery: batch C can support the same meal.

**65–90 seconds — value and the ask**

“The business hypothesis is better planned-order completion and inventory matching. I would measure contribution after waste, fulfillment and promise costs. First I’d verify the current app and batch-data capabilities with the team. I’d value your feedback on whether this is a useful problem.”

Be clear that the hosted web version is a browser reference model. The accompanying Java API, tests and source can be run locally. The demo is not a live Licious checkout and does not establish revenue uplift.
