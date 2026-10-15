package com.aggregator.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只覆盖本 SDK 的请求和配置，不是通用 JSON 库。 */
public final class Json {
    private Json() {}

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skip();
        if (parser.i != text.length()) {
            throw new IllegalArgumentException("JSON 末尾有多余内容");
        }
        return value;
    }

    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("需要 JSON 对象");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    public static String text(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    static long number(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    static boolean flag(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equals(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> stringMap(Object value) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return out;
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"').append(escape(text)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(out, list.get(i));
            }
            out.append(']');
        } else {
            write(out, String.valueOf(value));
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static final class Parser {
        private final String s;
        private int i;

        private Parser(String s) {
            this.s = s;
        }

        private Object parseValue() {
            skip();
            if (i >= s.length()) {
                throw new IllegalArgumentException("JSON 为空");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                i += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            i++;
            Map<String, Object> map = new LinkedHashMap<>();
            skip();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skip();
                String key = parseString();
                skip();
                expect(':');
                map.put(key, parseValue());
                skip();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            i++;
            List<Object> list = new ArrayList<>();
            skip();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skip();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char n = s.charAt(i++);
                    out.append(n == 'n' ? '\n' : n);
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException("字符串没有结束");
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("布尔值无效");
        }

        private Number parseNumber() {
            int start = i;
            while (i < s.length() && "-0123456789.".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String raw = s.substring(start, i);
            if (raw.contains(".")) {
                return Double.valueOf(raw);
            }
            return Long.valueOf(raw);
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("缺少字符 " + c);
            }
            i++;
        }
    }
}
