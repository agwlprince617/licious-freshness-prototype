// Synthetic fixtures and a browser reference model. Java is the local API implementation.
export const COOKS = {
  tonight: { label: 'Tonight', detail: 'Sat 7 Nov · 7 pm', at: '2026-11-07T19:00:00+05:30' },
  tomorrow: { label: 'Tomorrow', detail: 'Sun 8 Nov · 7 pm', at: '2026-11-08T19:00:00+05:30' },
  monday: { label: 'Monday', detail: 'Mon 9 Nov · 7 pm', at: '2026-11-09T19:00:00+05:30' }
};
export const SLOTS = {
  express: { label: 'Today, 10:30 am', detail: 'In 30 minutes', at: '2026-11-07T10:30:00+05:30' },
  evening: { label: 'Today, 5 pm', detail: 'An evening delivery', at: '2026-11-07T17:00:00+05:30' },
  nextday: { label: 'Tomorrow, 10 am', detail: 'A morning delivery', at: '2026-11-08T10:00:00+05:30' }
};
export const BATCHES = [
  { id: 'A', label: 'Batch A', release: '2026-11-07T08:00:00+05:30', useBy: '2026-11-08T15:00:00+05:30', useByLabel: 'Sun 8 Nov · 3 pm', quantity: 3 },
  { id: 'B', label: 'Batch B', release: '2026-11-07T14:00:00+05:30', useBy: '2026-11-09T23:00:00+05:30', useByLabel: 'Mon 9 Nov · 11 pm', quantity: 2 },
  { id: 'C', label: 'Batch C', release: '2026-11-08T09:00:00+05:30', useBy: '2026-11-10T23:00:00+05:30', useByLabel: 'Tue 10 Nov · 11 pm', quantity: 4 }
];
const hours = n => n * 3600000;
const requireKey = (record, key) => { if (!record[key]) throw new Error('INVALID_SELECTION'); return record[key]; };
export function evaluate(cookKey, slotKey, held = false, counts = {}) {
  const cook = requireKey(COOKS, cookKey), slot = requireKey(SLOTS, slotKey);
  const diagnostics = BATCHES.map(b => {
    let reason = 'ELIGIBLE';
    if (Date.parse(slot.at) > Date.parse(cook.at)) reason = 'DELIVERY_AFTER_COOK';
    else if (held && b.id === 'B') reason = 'QUALITY_HOLD';
    else if ((counts[b.id] ?? b.quantity) < 1) reason = 'OUT_OF_STOCK';
    else if (Date.parse(b.release) > Date.parse(slot.at)) reason = 'NOT_RELEASED_FOR_SLOT';
    else if (Date.parse(b.useBy) < Date.parse(cook.at) + hours(6)) reason = 'COOK_TIME_NOT_COVERED';
    return { ...b, available: counts[b.id] ?? b.quantity, reason };
  });
  const eligible = diagnostics.filter(b => b.reason === 'ELIGIBLE').sort((a,b) => Date.parse(a.useBy)-Date.parse(b.useBy));
  return { cookKey, slotKey, batch: eligible[0]?.id ?? null, diagnostics };
}
export class BrowserDemo {
  constructor() { this.reset(); }
  reset() { this.counts = Object.fromEntries(BATCHES.map(b => [b.id, b.quantity])); this.held = false; this.version = 1; this.quotes = new Map(); this.receipts = new Map(); this.active = null; }
  expire() {
    if (this.active && Date.now() >= this.active.expiresAt) {
      this.counts[this.active.batch]++; this.active.status = 'EXPIRED'; this.active = null; this.version++;
    }
  }
  quote(cookKey, slotKey) {
    this.expire();
    const selection = evaluate(cookKey, slotKey, this.held, this.counts);
    const alternatives = Object.keys(SLOTS).map(k => evaluate(cookKey, k, this.held, this.counts)).filter(q => q.batch);
    const quote = { ...selection, alternatives, version: this.version, quoteId: crypto.randomUUID(), quotedAt: Date.now() };
    this.quotes.set(quote.quoteId, quote); return quote;
  }
  qualityHold(held) { if (held !== this.held) { this.held = held; this.version++; } return { held, version: this.version }; }
  reserve(quoteId, key) {
    this.expire();
    if (this.receipts.has(key)) {
      const previous = this.receipts.get(key);
      if (previous.quoteId !== quoteId) throw new Error('IDEMPOTENCY_KEY_REUSED');
      return previous;
    }
    const q = this.quotes.get(quoteId);
    if (!q || !q.batch) throw new Error('NO_VALID_PROMISE');
    if (Date.now()-q.quotedAt >= 120000) throw new Error('QUOTE_EXPIRED');
    const current = evaluate(q.cookKey, q.slotKey, this.held, this.counts);
    const chosen = current.diagnostics.find(b => b.id === q.batch);
    if (chosen.reason !== 'ELIGIBLE') throw new Error(chosen.reason);
    if (this.active) throw new Error('DEMO_PACK_ALREADY_RESERVED');
    this.counts[q.batch]--; this.version++;
    const receipt = { reservationId: crypto.randomUUID(), quoteId, batch: q.batch, status: 'ACTIVE', expiresAt: Date.now()+90000 };
    this.receipts.set(key, receipt); this.active = receipt; return receipt;
  }
}
