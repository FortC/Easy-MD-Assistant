package com.aitools.mdassistant.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/序列化工具（无第三方依赖，避免跨版本类加载问题）。
 * 解析结果：对象 -> Map&lt;String,Object&gt;，数组 -> List&lt;Object&gt;，
 * 数字 -> Long/Double，字符串 -> String，另有 Boolean/null。
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text == null ? "" : text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.eof()) {
            throw new IllegalArgumentException("Unexpected trailing characters at " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String str(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof String ? (String) o : String.valueOf(o);
    }

    public static String getStr(Map<String, Object> m, String key) {
        return m == null ? null : str(m.get(key));
    }

    public static int getInt(Map<String, Object> m, String key, int def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException ignore) {
                return def;
            }
        }
        return def;
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeValue(v, sb);
        return sb.toString();
    }

    private static void writeValue(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String) {
            writeString((String) v, sb);
            return;
        }
        if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
            return;
        }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeValue(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(o, sb);
            }
            sb.append(']');
            return;
        }
        writeString(String.valueOf(v), sb);
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        char next() {
            return s.charAt(pos++);
        }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException(msg + " at " + pos);
        }

        void skipWs() {
            while (!eof()) {
                char c = peek();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (eof()) {
                throw err("Unexpected end of input");
            }
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, pos)) {
                throw err("Invalid literal");
            }
            pos += word.length();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWs();
            if (!eof() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (eof() || peek() != '"') {
                    throw err("Expected string key");
                }
                String key = parseString();
                skipWs();
                if (eof() || peek() != ':') {
                    throw err("Expected ':'");
                }
                pos++;
                map.put(key, parseValue());
                skipWs();
                if (eof()) {
                    throw err("Unterminated object");
                }
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw err("Expected ',' or '}'");
                }
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWs();
            if (!eof() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (eof()) {
                    throw err("Unterminated array");
                }
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw err("Expected ',' or ']'");
                }
            }
        }

        String parseString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw err("Unterminated string");
                }
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (eof()) {
                        throw err("Unterminated escape");
                    }
                    char e = next();
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw err("Invalid unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw err("Invalid escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object parseNumber() {
            int start = pos;
            while (!eof()) {
                char c = peek();
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) {
                throw err("Invalid number");
            }
            if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException ignore) {
                    // fall through to double
                }
            }
            return Double.parseDouble(num);
        }
    }
}
