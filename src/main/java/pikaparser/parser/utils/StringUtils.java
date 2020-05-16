package pikaparser.parser.utils;

public class StringUtils {
    public static int hexDigitToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a';
        } else if (c >= 'A' && c <= 'F') {
            return c - 'F';
        }
        throw new IllegalArgumentException("Illegal Unicode hex char: " + c);
    }

    public static char unescapeChar(String escapedChar) {
        if (escapedChar.length() == 0) {
            throw new IllegalArgumentException("Empty char string");
        } else if (escapedChar.length() == 1) {
            return escapedChar.charAt(0);
        }
        switch (escapedChar) {
        case "\\t":
            return '\t';
        case "\\b":
            return '\b';
        case "\\n":
            return '\n';
        case "\\r":
            return '\r';
        case "\\f":
            return '\f';
        case "\\'":
            return '\'';
        case "\\\"":
            return '"';
        case "\\\\":
            return '\\';
        default:
            if (escapedChar.startsWith("\\u") && escapedChar.length() == 6) {
                int c0 = hexDigitToInt(escapedChar.charAt(2));
                int c1 = hexDigitToInt(escapedChar.charAt(3));
                int c2 = hexDigitToInt(escapedChar.charAt(4));
                int c3 = hexDigitToInt(escapedChar.charAt(5));
                return (char) ((c0 << 24) | (c1 << 16) | (c2 << 8) | c3);
            } else {
                throw new IllegalArgumentException("Invalid character: " + escapedChar);
            }
        }
    }

    public static String unescapeString(String str) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                if (i == str.length() - 1) {
                    // Should not happen
                    throw new IllegalArgumentException("Got backslash at end of quoted string");
                }
                buf.append(unescapeChar(str.substring(i, i + 2)));
                i++; // Consume escaped character
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }

    public static String escapeCharRangeChar(char c) {
        if (c == '[') {
            return "\\[";
        } else if (c == ']') {
            return "\\]";
        } else if (c == '^') {
            return "\\^";
        } else  {
            return escapeChar(c);
        }
    }

    public static String escapeChar(char c) {
        if (c == '\'') {
            return "\\'";
        } else if (c == '\\') {
            return "\\\\";
        } else if (c >= 32 && c <= 126) {
            return Character.toString(c);
        } else if (c == '\n') {
            return "\\n";
        } else if (c == '\r') {
            return "\\r";
        } else if (c == '\t') {
            return "\\t";
        } else if (c == '\f') {
            return "\\f";
        } else if (c == '\b') {
            return "\\b";
        } else {
            return "\\u" + String.format("%04x", (int) c);
        }
    }

    public static String escapeString(String str) {
        var buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            buf.append(c == '"' ? "\\\"" : escapeChar(c));
        }
        return buf.toString();
    }
}
