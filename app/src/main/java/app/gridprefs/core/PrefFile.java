package app.gridprefs.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-preserving editor for the Feral "registry XML" preferences file.
 *
 * The file is never re-serialised from a DOM. Every line is kept as-is and only the
 * single line of a changed value is rewritten, so untouched settings (including the big
 * binary blobs) are guaranteed to come back byte-for-byte identical.
 */
public final class PrefFile {

    public static final String SETUP = "HKEY_CURRENT_USER/Software/Feral Interactive/GRID Autosport/Setup";
    public static final String DRS = SETUP + "/DynamicResolutionScaling";

    public static final class Entry {
        public final String path;
        public final String name;
        public final String type;
        /** Value text exactly as it appears in the XML (still XML-escaped). */
        public final String raw;
        final int line;

        Entry(String path, String name, String type, String raw, int line) {
            this.path = path;
            this.name = name;
            this.type = type;
            this.raw = raw;
            this.line = line;
        }

        public String text() {
            return unescape(raw);
        }

        public boolean isInteger() {
            return "integer".equals(type);
        }

        public boolean isString() {
            return "string".equals(type);
        }

        /** 8-byte binary values are IEEE doubles (little endian) in this format. */
        public boolean isDouble() {
            return "binary".equals(type) && raw.length() == 16;
        }
    }

    private static final Pattern VALUE =
            Pattern.compile("^(\\s*)<value name=\"([^\"]*)\" type=\"([^\"]*)\">(.*)</value>\\s*$");
    private static final Pattern KEY_OPEN = Pattern.compile("^\\s*<key name=\"([^\"]*)\">\\s*$");
    private static final Pattern KEY_CLOSE = Pattern.compile("^\\s*</key>\\s*$");

    private final List<String> lines = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> index = new HashMap<>();
    private final Map<String, Integer> keyClose = new HashMap<>();

    public PrefFile(String text) {
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl + 1;
            lines.add(text.substring(start, end));
            start = end;
        }
        reindex();
    }

    // ---------------------------------------------------------------- reading

    public List<Entry> entries() {
        return entries;
    }

    public Entry find(String path, String name) {
        return index.get(path + "\u0000" + name);
    }

    public boolean has(String path, String name) {
        return find(path, name) != null;
    }

    public String getString(String path, String name, String def) {
        Entry e = find(path, name);
        return e == null ? def : e.text();
    }

    public int getInt(String path, String name, int def) {
        Entry e = find(path, name);
        if (e == null || !e.isInteger()) return def;
        try {
            return Integer.parseInt(e.text().trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public double getDouble(String path, String name, double def) {
        Entry e = find(path, name);
        if (e == null || !e.isDouble()) return def;
        return hexToDouble(e.raw);
    }

    // ---------------------------------------------------------------- writing

    public void setInt(String path, String name, int value) {
        put(path, name, "integer", Integer.toString(value));
    }

    public void setString(String path, String name, String value) {
        put(path, name, "string", escape(value));
    }

    public void setDouble(String path, String name, double value) {
        put(path, name, "binary", doubleToHex(value));
    }

    /** Writes a value coming from a free-text editor, validated against the entry's existing type. */
    public void setFromText(Entry e, String text) {
        if (e.isInteger()) {
            setInt(e.path, e.name, Integer.parseInt(text.trim()));
        } else if (e.isDouble()) {
            setDouble(e.path, e.name, Double.parseDouble(text.trim()));
        } else if (e.isString()) {
            setString(e.path, e.name, text);
        } else {
            throw new IllegalArgumentException("This value type cannot be edited as text");
        }
    }

    private void put(String path, String name, String type, String raw) {
        Entry old = find(path, name);
        if (old != null) {
            String ln = lines.get(old.line);
            String indent = ln.substring(0, ln.indexOf('<'));
            lines.set(old.line, indent + valueLine(name, old.type, raw) + eolOf(ln));
        } else {
            Integer close = keyClose.get(path);
            if (close == null) {
                throw new IllegalStateException("Missing key in file: " + path);
            }
            String closeLine = lines.get(close);
            String indent = closeLine.substring(0, closeLine.indexOf('<')) + "    ";
            lines.add(close, indent + valueLine(name, type, raw) + eolOf(closeLine));
        }
        reindex();
    }

    private static String valueLine(String name, String type, String raw) {
        return "<value name=\"" + escape(name) + "\" type=\"" + type + "\">" + raw + "</value>";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l);
        return sb.toString();
    }

    // ---------------------------------------------------------------- internals

    private void reindex() {
        entries.clear();
        index.clear();
        keyClose.clear();
        List<String> stack = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String body = stripEol(lines.get(i));
            Matcher m = VALUE.matcher(body);
            if (m.matches()) {
                String path = join(stack);
                Entry e = new Entry(path, unescape(m.group(2)), m.group(3), m.group(4), i);
                entries.add(e);
                index.put(path + "\u0000" + e.name, e);
                continue;
            }
            m = KEY_OPEN.matcher(body);
            if (m.matches()) {
                stack.add(unescape(m.group(1)));
                continue;
            }
            if (KEY_CLOSE.matcher(body).matches() && !stack.isEmpty()) {
                keyClose.put(join(stack), i);
                stack.remove(stack.size() - 1);
            }
        }
    }

    private static String join(List<String> stack) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    private static String stripEol(String s) {
        int end = s.length();
        if (end > 0 && s.charAt(end - 1) == '\n') end--;
        if (end > 0 && s.charAt(end - 1) == '\r') end--;
        return s.substring(0, end);
    }

    private static String eolOf(String s) {
        if (s.endsWith("\r\n")) return "\r\n";
        if (s.endsWith("\n")) return "\n";
        return "\n";
    }

    // ---------------------------------------------------------------- encoding helpers

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    public static String doubleToHex(double d) {
        long bits = Double.doubleToLongBits(d);
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            int b = (int) ((bits >>> (8 * i)) & 0xff);
            sb.append(Character.forDigit(b >> 4, 16)).append(Character.forDigit(b & 15, 16));
        }
        return sb.toString();
    }

    public static double hexToDouble(String hex) {
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            int b = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            bits |= ((long) b) << (8 * i);
        }
        return Double.longBitsToDouble(bits);
    }
}
