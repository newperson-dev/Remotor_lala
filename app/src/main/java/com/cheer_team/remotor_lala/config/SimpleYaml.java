package com.cheer_team.remotor_lala.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 YAML 子集解析器（零第三方依赖，方便离线构建 / 单元测试）。
 *
 * <p>支持：
 * <ul>
 *   <li>整行注释与行尾注释（# 前必须是行首或空白）</li>
 *   <li>按缩进嵌套的 Map 与 List</li>
 *   <li>对象列表：{@code - key: value} 起头、后续行与 key 对齐</li>
 *   <li>双引号字符串（支持 \n、\t、\r、\"、\\ 以及 Unicode 转义）与单引号字符串</li>
 *   <li>int / double / boolean / null 自动类型推断</li>
 * </ul>
 *
 * <p>不支持：锚点、多文档、复杂流式语法（够本项目用，也避免引入依赖）。
 * 解析结果统一是 {@code LinkedHashMap} 与 {@code ArrayList}。
 */
public final class SimpleYaml {

    private SimpleYaml() {
    }

    /** YAML 语法错误。 */
    public static class YamlException extends RuntimeException {
        public YamlException(String message) {
            super(message);
        }
    }

    private static final class Line {
        int indent;
        String content;
        final int number;

        Line(int indent, String content, int number) {
            this.indent = indent;
            this.content = content;
            this.number = number;
        }
    }

    // ------------------------------------------------------------------ 入口

    public static Map<String, Object> parse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return parse(sb.toString());
    }

    public static Map<String, Object> parse(String text) {
        List<Line> lines = tokenize(text);
        if (lines.isEmpty()) {
            return new LinkedHashMap<>();
        }
        int[] pos = {0};
        Line first = lines.get(0);
        Object root = isSequenceItem(first.content)
                ? parseSequence(lines, pos, first.indent)
                : parseMapping(lines, pos, first.indent);
        if (pos[0] < lines.size()) {
            Line rest = lines.get(pos[0]);
            throw new YamlException("第 " + rest.number + " 行缩进异常: " + rest.content);
        }
        if (!(root instanceof Map)) {
            throw new YamlException("YAML 根节点必须是键值对映射");
        }
        return castMap(root);
    }

    // -------------------------------------------------------------- 分词

    private static List<Line> tokenize(String text) {
        List<Line> out = new ArrayList<>();
        String[] raw = text.split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i];
            if (i == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            line = stripComment(line);
            if (line.trim().isEmpty()) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            if (indent < line.length() && line.charAt(indent) == '\t') {
                throw new YamlException("第 " + (i + 1) + " 行使用了 Tab 缩进，请改用空格");
            }
            out.add(new Line(indent, line.substring(indent).trim(), i + 1));
        }
        return out;
    }

    private static String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') {
                    i++;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    // -------------------------------------------------------------- 递归下降

    private static Map<String, Object> parseMapping(List<Line> lines, int[] pos, int indent) {
        Map<String, Object> map = new LinkedHashMap<>();
        while (pos[0] < lines.size()) {
            Line line = lines.get(pos[0]);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw new YamlException("第 " + line.number + " 行缩进过深: " + line.content);
            }
            if (isSequenceItem(line.content)) {
                break;
            }
            int colon = keyColon(line.content);
            if (colon < 0) {
                throw new YamlException("第 " + line.number + " 行缺少 ':' -> " + line.content);
            }
            String key = unquote(line.content.substring(0, colon).trim());
            String rest = line.content.substring(colon + 1).trim();
            pos[0]++;

            if (!rest.isEmpty()) {
                map.put(key, parseScalar(rest, line.number));
                continue;
            }
            if (pos[0] >= lines.size()) {
                map.put(key, null);
                continue;
            }
            Line next = lines.get(pos[0]);
            boolean sameIndentList = next.indent == indent && isSequenceItem(next.content);
            if (next.indent > indent || sameIndentList) {
                map.put(key, isSequenceItem(next.content)
                        ? parseSequence(lines, pos, next.indent)
                        : parseMapping(lines, pos, next.indent));
            } else {
                map.put(key, null);
            }
        }
        return map;
    }

    private static List<Object> parseSequence(List<Line> lines, int[] pos, int indent) {
        List<Object> list = new ArrayList<>();
        while (pos[0] < lines.size()) {
            Line line = lines.get(pos[0]);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw new YamlException("第 " + line.number + " 行缩进异常: " + line.content);
            }
            if (!isSequenceItem(line.content)) {
                break;
            }
            String rest = line.content.equals("-") ? "" : line.content.substring(2).trim();

            if (rest.isEmpty()) {
                pos[0]++;
                if (pos[0] < lines.size() && lines.get(pos[0]).indent > indent) {
                    Line next = lines.get(pos[0]);
                    list.add(isSequenceItem(next.content)
                            ? parseSequence(lines, pos, next.indent)
                            : parseMapping(lines, pos, next.indent));
                } else {
                    list.add(null);
                }
                continue;
            }

            if (keyColon(rest) >= 0) {
                // "- id: xxx" 形式：把这一行改写成普通映射项，后续同列的行归入同一个 map
                line.content = rest;
                line.indent = indent + 2;
                list.add(parseMapping(lines, pos, line.indent));
            } else {
                pos[0]++;
                list.add(parseScalar(rest, line.number));
            }
        }
        return list;
    }

    private static boolean isSequenceItem(String content) {
        return content.equals("-") || content.startsWith("- ");
    }

    /** 找到作为键值分隔符的冒号（不在引号内、后面是空格或行尾）。 */
    private static int keyColon(String s) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') {
                    i++;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                if (i + 1 == s.length() || s.charAt(i + 1) == ' ') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Object parseScalar(String s, int lineNumber) {
        if (s.isEmpty()) {
            return null;
        }
        char first = s.charAt(0);
        if (first == '"' || first == '\'') {
            if (s.length() < 2 || s.charAt(s.length() - 1) != first) {
                throw new YamlException("第 " + lineNumber + " 行字符串引号不匹配: " + s);
            }
            String body = s.substring(1, s.length() - 1);
            return first == '"' ? unescape(body) : body.replace("''", "'");
        }
        if (s.equals("[]")) {
            return new ArrayList<>();
        }
        if (s.equals("{}")) {
            return new LinkedHashMap<String, Object>();
        }
        if (s.equals("~") || s.equalsIgnoreCase("null")) {
            return null;
        }
        if (s.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (s.matches("[-+]?\\d+")) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return Long.parseLong(s);
            }
        }
        if (s.matches("[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)[eE][-+]?\\d+")
                || s.matches("[-+]?(\\d+\\.\\d*|\\.\\d+)")) {
            return Double.parseDouble(s);
        }
        return s;
    }

    private static String unescape(String body) {
        StringBuilder sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\' || i + 1 >= body.length()) {
                sb.append(c);
                continue;
            }
            char e = body.charAt(++i);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case '"': sb.append('"'); break;
                case '\'': sb.append('\''); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case '0': sb.append('\0'); break;
                case 'u':
                    if (i + 4 < body.length()) {
                        sb.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    break;
                default:
                    sb.append('\\').append(e);
                    break;
            }
        }
        return sb.toString();
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.charAt(0) == '"' && s.endsWith("\"")) || (s.charAt(0) == '\'' && s.endsWith("'")))) {
            Object value = parseScalar(s, 0);
            return value == null ? "" : String.valueOf(value);
        }
        return s;
    }

    // -------------------------------------------------------------- 取值辅助

    /** 按 "a.b.c" 路径取值，任一层缺失返回 null。 */
    @SuppressWarnings("unchecked")
    public static Object get(Map<String, Object> root, String path) {
        if (root == null || path == null) {
            return null;
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public static String getString(Map<String, Object> root, String path, String fallback) {
        Object value = get(root, path);
        return value == null ? fallback : String.valueOf(value);
    }

    public static int getInt(Map<String, Object> root, String path, int fallback) {
        Object value = get(root, path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return (int) Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static float getFloat(Map<String, Object> root, String path, float fallback) {
        Object value = get(root, path);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean getBoolean(Map<String, Object> root, String path, boolean fallback) {
        Object value = get(root, path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) {
                return true;
            }
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) {
                return false;
            }
        }
        return fallback;
    }

    public static Map<String, Object> getMap(Map<String, Object> root, String path) {
        return castMap(get(root, path));
    }

    public static List<Object> getList(Map<String, Object> root, String path) {
        Object value = get(root, path);
        return value instanceof List ? castList(value) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> castList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return Collections.emptyList();
    }
}
