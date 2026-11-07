package demo;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

public final class Server {
    public static void main(String[] args) throws Exception {
        PromiseService service = new PromiseService(Clock.systemUTC());
        if (args.length>0 && args[0].equals("matrix")) {
            List<Object> matrix = new ArrayList<>();
            for(boolean held : List.of(false,true)) {
                service.qualityHold(held);
                for(String cook : PromiseService.COOKS.keySet()) for(String slot : PromiseService.SLOTS.keySet())
                    matrix.add(Map.of("held",held,"selection",service.evaluate(cook,slot)));
            }
            System.out.println(Json.write(matrix)); return;
        }
        Path root = Path.of(args.length>0?args[0]:"dist").toAbsolutePath().normalize();
        int port = args.length>1?Integer.parseInt(args[1]):8088;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),32);
        ExecutorService pool = Executors.newFixedThreadPool(8); server.setExecutor(pool);
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath(); Map<String,String> params = query(ex.getRequestURI().getRawQuery());
                if(path.equals("/api/quote") && ex.getRequestMethod().equals("GET")) {
                    json(ex,200,service.quote(params.get("cart"),params.get("cook"),params.get("slot")));
                } else if(path.equals("/api/reserve") && ex.getRequestMethod().equals("POST")) {
                    json(ex,200,service.reserve(params.get("cart"),params.get("quote"),ex.getRequestHeaders().getFirst("Idempotency-Key")));
                } else if(path.equals("/api/quality") && ex.getRequestMethod().equals("POST")) {
                    if(!Set.of("true","false").contains(params.getOrDefault("held",""))) throw new PromiseService.Rejected("INVALID_SELECTION");
                    json(ex,200,service.qualityHold(Boolean.parseBoolean(params.get("held"))));
                } else if(path.equals("/api/reset") && ex.getRequestMethod().equals("POST")) {
                    service.reset(); json(ex,200,Map.of("reset",true));
                } else if(path.startsWith("/api/")) { json(ex,404,Map.of("error","ROUTE_NOT_FOUND"));
                } else if(ex.getRequestMethod().equals("GET")) {
                    Path file = root.resolve(path.equals("/")?"index.html":path.substring(1)).normalize();
                    if(!file.startsWith(root) || !Files.isRegularFile(file)) { json(ex,404,Map.of("error","NOT_FOUND")); return; }
                    byte[] bytes = Files.readAllBytes(file);
                    String type = file.toString().endsWith(".html")?"text/html; charset=utf-8":file.toString().endsWith(".css")?"text/css":file.toString().endsWith(".mjs")?"application/javascript":file.toString().endsWith(".svg")?"image/svg+xml":"application/octet-stream";
                    if(file.getFileName().toString().equals("index.html")) bytes = new String(bytes,StandardCharsets.UTF_8).replace("content=\"browser\"","content=\"java\"").getBytes(StandardCharsets.UTF_8);
                    send(ex,200,type,bytes);
                } else { json(ex,405,Map.of("error","METHOD_NOT_ALLOWED")); }
            } catch(PromiseService.Rejected rejected) { json(ex,409,Map.of("error",rejected.getMessage())); }
            catch(IllegalArgumentException invalid) { json(ex,400,Map.of("error","INVALID_REQUEST")); }
            catch(Exception error) { System.err.println(error.getClass().getSimpleName()); json(ex,500,Map.of("error","INTERNAL_ERROR")); }
            finally { ex.close(); }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(0);pool.shutdownNow();}));
        server.start(); System.out.println("Freshness prototype: http://127.0.0.1:"+port);
    }
    private static Map<String,String> query(String raw) {
        Map<String,String> params = new HashMap<>(); if(raw==null || raw.isEmpty()) return params;
        if(raw.length()>2048) throw new IllegalArgumentException();
        for(String pair:raw.split("&")) {
            String[] parts=pair.split("=",2); if(parts.length!=2) throw new IllegalArgumentException();
            if(params.putIfAbsent(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8))!=null) throw new IllegalArgumentException();
        }
        return params;
    }
    private static void json(HttpExchange ex,int code,Object value) throws IOException { send(ex,code,"application/json; charset=utf-8",Json.write(value).getBytes(StandardCharsets.UTF_8)); }
    private static void send(HttpExchange ex,int code,String type,byte[] value) throws IOException {
        ex.getResponseHeaders().set("Content-Type",type); ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.sendResponseHeaders(code,value.length); ex.getResponseBody().write(value);
    }
}
