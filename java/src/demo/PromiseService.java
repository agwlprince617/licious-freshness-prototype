package demo;

import java.time.*;
import java.util.*;

/** A single-product synthetic demonstration. No Licious data or integrations. */
public final class PromiseService {
    public record Choice(String key, Instant at) {}
    public record Batch(String id, Instant release, Instant useBy, int initialQuantity) {}
    public record Diagnostic(String id, Instant release, Instant useBy, int available, String reason) {}
    public record Selection(String cookKey, String slotKey, String batch, List<Diagnostic> diagnostics) {}
    public record Quote(String quoteId, String cartId, long version, Instant quotedAt, String cookKey, String slotKey,
                        String batch, List<Diagnostic> diagnostics, List<Selection> alternatives) {}
    public record Receipt(String reservationId, String quoteId, String batch, String status, Instant expiresAt) {}
    private record Saved(String quoteId, Receipt receipt) {}
    public static final class Rejected extends RuntimeException {
        public Rejected(String code) { super(code); }
    }
    // Six hours is an invented fixture policy, NOT storage advice or a Licious standard.
    public static final Duration DEMO_BUFFER = Duration.ofHours(6);
    public static final Map<String, Choice> COOKS = choices(
            "tonight", "2026-11-07T19:00:00+05:30", "tomorrow", "2026-11-08T19:00:00+05:30", "monday", "2026-11-09T19:00:00+05:30");
    public static final Map<String, Choice> SLOTS = choices(
            "express", "2026-11-07T10:30:00+05:30", "evening", "2026-11-07T17:00:00+05:30", "nextday", "2026-11-08T10:00:00+05:30");
    public static final List<Batch> BATCHES = List.of(
            batch("A", "2026-11-07T08:00:00+05:30", "2026-11-08T15:00:00+05:30", 3),
            batch("B", "2026-11-07T14:00:00+05:30", "2026-11-09T23:00:00+05:30", 2),
            batch("C", "2026-11-08T09:00:00+05:30", "2026-11-10T23:00:00+05:30", 4));
    private final Clock clock;
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Quote> quotes = new HashMap<>();
    private final Map<String, Saved> idempotency = new HashMap<>();
    private final Map<String, Receipt> active = new HashMap<>();
    private boolean held;
    private long version = 1;
    public PromiseService(Clock clock) { this.clock = clock; reset(); }

    public synchronized void reset() {
        counts.clear(); BATCHES.forEach(b -> counts.put(b.id(), b.initialQuantity()));
        quotes.clear(); idempotency.clear(); active.clear(); held = false; version++;
    }
    public synchronized Map<String, Object> qualityHold(boolean value) {
        if (held != value) { held = value; version++; }
        return Map.of("held", held, "version", version);
    }
    public synchronized Selection evaluate(String cookKey, String slotKey) {
        expire();
        Choice cook = COOKS.get(cookKey), slot = SLOTS.get(slotKey);
        if (cook == null || slot == null) throw new Rejected("INVALID_SELECTION");
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Batch b : BATCHES) {
            String reason = "ELIGIBLE";
            if (slot.at().isAfter(cook.at())) reason = "DELIVERY_AFTER_COOK";
            else if (held && b.id().equals("B")) reason = "QUALITY_HOLD";
            else if (counts.get(b.id()) < 1) reason = "OUT_OF_STOCK";
            else if (b.release().isAfter(slot.at())) reason = "NOT_RELEASED_FOR_SLOT";
            else if (b.useBy().isBefore(cook.at().plus(DEMO_BUFFER))) reason = "COOK_TIME_NOT_COVERED";
            diagnostics.add(new Diagnostic(b.id(), b.release(), b.useBy(), counts.get(b.id()), reason));
        }
        String chosen = diagnostics.stream().filter(b -> b.reason().equals("ELIGIBLE"))
                .min(Comparator.comparing(Diagnostic::useBy)).map(Diagnostic::id).orElse(null);
        return new Selection(cookKey, slotKey, chosen, List.copyOf(diagnostics));
    }
    public synchronized Quote quote(String cart, String cook, String slot) {
        if (cart == null || !cart.matches("[A-Za-z0-9_-]{8,80}")) throw new Rejected("INVALID_REQUEST_KEY");
        Selection selected = evaluate(cook, slot);
        List<Selection> alternatives = SLOTS.keySet().stream().map(k -> evaluate(cook,k)).filter(s -> s.batch()!=null).toList();
        Quote q = new Quote(UUID.randomUUID().toString(), cart, version, clock.instant(), cook, slot,
                selected.batch(), selected.diagnostics(), alternatives);
        quotes.put(q.quoteId(), q); return q;
    }
    public synchronized Receipt reserve(String cart, String quoteId, String key) {
        expire();
        if (cart == null || !cart.matches("[A-Za-z0-9_-]{8,80}") || key == null || !key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new Rejected("INVALID_REQUEST_KEY");
        String scope = cart + ":" + key;
        Saved saved = idempotency.get(scope);
        if (saved != null) {
            if (!saved.quoteId().equals(quoteId)) throw new Rejected("IDEMPOTENCY_KEY_REUSED");
            Receipt r = saved.receipt();
            return clock.instant().isBefore(r.expiresAt()) ? r : new Receipt(r.reservationId(), r.quoteId(), r.batch(), "EXPIRED", r.expiresAt());
        }
        Quote q = quotes.get(quoteId);
        if (q == null || q.batch()==null) throw new Rejected("NO_VALID_PROMISE");
        if (!q.cartId().equals(cart)) throw new Rejected("QUOTE_CART_MISMATCH");
        if (!clock.instant().isBefore(q.quotedAt().plusSeconds(120))) throw new Rejected("QUOTE_EXPIRED");
        Selection current = evaluate(q.cookKey(), q.slotKey());
        Diagnostic chosen = current.diagnostics().stream().filter(b -> b.id().equals(q.batch())).findFirst().orElseThrow();
        if (!chosen.reason().equals("ELIGIBLE")) throw new Rejected(chosen.reason());
        if (active.containsKey(cart)) throw new Rejected("DEMO_PACK_ALREADY_RESERVED");
        // Check, decrement, reservation and idempotency write share one JVM lock.
        counts.compute(q.batch(), (k,v) -> v-1); version++;
        Receipt result = new Receipt(UUID.randomUUID().toString(), quoteId, q.batch(), "ACTIVE", clock.instant().plusSeconds(90));
        active.put(cart,result); idempotency.put(scope,new Saved(quoteId,result)); return result;
    }
    private void expire() {
        var it = active.entrySet().iterator();
        while (it.hasNext()) {
            Receipt r = it.next().getValue();
            if (!clock.instant().isBefore(r.expiresAt())) { counts.compute(r.batch(),(k,v)->v+1); version++; it.remove(); }
        }
    }
    public synchronized int available(String batch) { expire(); return counts.get(batch); }
    private static Batch batch(String id,String release,String useBy,int quantity) {
        return new Batch(id,OffsetDateTime.parse(release).toInstant(),OffsetDateTime.parse(useBy).toInstant(),quantity);
    }
    private static Map<String,Choice> choices(String... pairs) {
        Map<String,Choice> result = new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put(pairs[i], new Choice(pairs[i], OffsetDateTime.parse(pairs[i+1]).toInstant()));
        return Collections.unmodifiableMap(result);
    }
}
