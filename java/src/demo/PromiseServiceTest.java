package demo;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PromiseServiceTest {
    private static int passed;
    private static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-07T04:30:00Z");
        public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;} void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    private record Fixture(PromiseService service,TestClock clock){}
    private static Fixture fresh(){var c=new TestClock();return new Fixture(new PromiseService(c),c);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void test(String name,Runnable action){action.run();passed++;System.out.println("PASS "+name);}
    private static void error(String expected,Runnable action){try{action.run();throw new AssertionError("Expected "+expected);}catch(PromiseService.Rejected e){check(e.getMessage().equals(expected),"Got "+e.getMessage()+", expected "+expected);}}
    public static void main(String[] args)throws Exception {
        test("Tonight's express option selects batch A",()->check("A".equals(fresh().service().evaluate("tonight","express").batch()),"Wrong batch"));
        test("Tomorrow's meal cannot use the current express stock",()->{
            var q=fresh().service().quote("cart-00001","tomorrow","express");check(q.batch()==null,"Invalid promise");
            check(q.alternatives().size()==2&&q.alternatives().getFirst().slotKey().equals("evening"),"No useful alternative");
        });
        test("Later delivery covers tomorrow's meal with batch B",()->check("B".equals(fresh().service().evaluate("tomorrow","evening").batch()),"Wrong batch"));
        test("Earliest eligible batch is chosen before a later-dated batch",()->{
            var s=fresh().service();check("A".equals(s.evaluate("tonight","evening").batch()),"Wasted earlier eligibility");
            check("B".equals(s.evaluate("tomorrow","nextday").batch()),"Should preserve C");
        });
        test("Monday's cooking time requires batch C",()->check("C".equals(fresh().service().evaluate("monday","nextday").batch()),"Wrong batch"));
        test("Delivery after the meal produces no promise",()->check(fresh().service().evaluate("tonight","nextday").diagnostics().stream().allMatch(d->d.reason().equals("DELIVERY_AFTER_COOK")),"Late delivery allowed"));
        test("A quality hold blocks acceptance of an earlier quote",()->{
            var s=fresh().service();var q=s.quote("cart-00001","tomorrow","evening");s.qualityHold(true);
            error("QUALITY_HOLD",()->s.reserve("cart-00001",q.quoteId(),"key-000001"));
            check(s.available("B")==2,"Blocked request consumed stock");
        });
        test("Requoting after a hold offers next-day batch C",()->{
            var s=fresh().service();s.qualityHold(true);var q=s.quote("cart-00001","tomorrow","evening");
            check(q.batch()==null&&q.alternatives().size()==1&&q.alternatives().getFirst().batch().equals("C"),"Wrong fallback");
        });
        test("An expired quote cannot reserve stock",()->{
            var f=fresh();var q=f.service().quote("cart-00001","tonight","express");f.clock().advance(120);
            error("QUOTE_EXPIRED",()->f.service().reserve("cart-00001",q.quoteId(),"key-000001"));
        });
        test("Quote ownership is bound to its demo cart",()->{
            var s=fresh().service();var q=s.quote("cart-00001","tonight","express");
            error("QUOTE_CART_MISMATCH",()->s.reserve("cart-00002",q.quoteId(),"key-000001"));
        });
        test("A repeat request returns the same reservation",()->{
            var s=fresh().service();var q=s.quote("cart-00001","tonight","express");
            var r=s.reserve("cart-00001",q.quoteId(),"key-000001");
            check(r.equals(s.reserve("cart-00001",q.quoteId(),"key-000001"))&&s.available("A")==2,"Duplicated hold");
        });
        test("Reusing an idempotency key for another quote is rejected",()->{
            var s=fresh().service();var first=s.quote("cart-00001","tonight","express");var second=s.quote("cart-00001","tonight","evening");
            s.reserve("cart-00001",first.quoteId(),"key-000001");
            error("IDEMPOTENCY_KEY_REUSED",()->s.reserve("cart-00001",second.quoteId(),"key-000001"));
        });
        test("A cart cannot hold a second pack with another key",()->{
            var s=fresh().service();var q=s.quote("cart-00001","tonight","express");s.reserve("cart-00001",q.quoteId(),"key-000001");
            error("DEMO_PACK_ALREADY_RESERVED",()->s.reserve("cart-00001",q.quoteId(),"key-000002"));
        });
        test("Hold expiry releases once and an expired retry does not reserve again",()->{
            var f=fresh();var s=f.service();var q=s.quote("cart-00001","tonight","express");s.reserve("cart-00001",q.quoteId(),"key-000001");f.clock().advance(90);
            check(s.available("A")==3&&s.available("A")==3,"Wrong stock after expiry");
            check(s.reserve("cart-00001",q.quoteId(),"key-000001").status().equals("EXPIRED")&&s.available("A")==3,"Retry recreated hold");
        });
        test("Invalid choices and keys fail without changing stock",()->{
            var s=fresh().service();error("INVALID_SELECTION",()->s.evaluate("invalid","express"));
            error("INVALID_REQUEST_KEY",()->s.quote("x","tonight","express"));check(s.available("A")==3,"Stock changed");
        });
        var racing=fresh().service();var qs=new ArrayList<PromiseService.Quote>();
        for(int i=0;i<32;i++)qs.add(racing.quote("racing-cart-"+i,"tonight","express"));
        AtomicInteger success=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(32)){
            var fs=new ArrayList<Future<?>>();for(var q:qs)fs.add(pool.submit(()->{
                start.await();try{racing.reserve(q.cartId(),q.quoteId(),"race-key-001");success.incrementAndGet();}
                catch(PromiseService.Rejected e){check(e.getMessage().equals("OUT_OF_STOCK"),"Unexpected race failure: "+e.getMessage());}return null;
            }));start.countDown();for(var f:fs)f.get(10,TimeUnit.SECONDS);
        }
        test("32 customers compete for three packs: exactly three reservations",()->check(success.get()==3&&racing.available("A")==0,"Oversold or underallocated"));
        var retrying=fresh().service();var q=retrying.quote("cart-00001","tonight","express");Set<String> ids=ConcurrentHashMap.newKeySet();
        try(var pool=Executors.newFixedThreadPool(16)){
            var fs=new ArrayList<Future<?>>();for(int i=0;i<32;i++)fs.add(pool.submit(()->ids.add(retrying.reserve("cart-00001",q.quoteId(),"retry-key-001").reservationId())));
            for(var f:fs)f.get(10,TimeUnit.SECONDS);
        }
        test("32 concurrent retries create one reservation",()->check(ids.size()==1&&retrying.available("A")==2,"Duplicate reservation"));
        System.out.println("All "+passed+" Java tests passed.");
    }
}
