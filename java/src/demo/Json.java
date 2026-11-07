package demo;

import java.lang.reflect.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.*;

/** Output-only serializer for this dependency-free demo; no request JSON is parsed. */
public final class Json {
    private Json() {}
    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof TemporalAccessor || value instanceof Enum<?>)
            return quote(value.toString());
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map)
            return map.entrySet().stream().map(e -> quote(e.getKey().toString()) + ":" + write(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> list)
            return list.stream().map(Json::write).collect(Collectors.joining(",", "[", "]"));
        if (value.getClass().isRecord()) {
            List<String> fields = new ArrayList<>();
            try {
                for (RecordComponent component : value.getClass().getRecordComponents())
                    fields.add(quote(component.getName()) + ":" + write(component.getAccessor().invoke(value)));
            } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
            return String.join(",", fields).transform(s -> "{" + s + "}");
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
