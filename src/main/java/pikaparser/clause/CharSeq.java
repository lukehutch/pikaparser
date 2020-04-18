package pikaparser.clause;

import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoEntry;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

public class CharSeq extends Terminal {

    public final String str;
    public final boolean ignoreCase;

    CharSeq(String str, boolean ignoreCase) {
        super();
        this.str = str;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            Set<MemoEntry> updatedEntries) {
        // Terminals always add matches to the memo table if they match
        if (memoKey.startPos < input.length() - str.length()
                && input.regionMatches(ignoreCase, memoKey.startPos, str, 0, str.length())) {
            return memoTable.addTerminalMatch(memoKey, /* terminalLen = */ str.length(), updatedEntries);
        }
        // Don't call MemoTable.addMatch for terminals that don't match, to limit size of memo table
        if (Parser.DEBUG) {
            System.out.println("Failed to match at position " + memoKey.startPos + ": " + memoKey);
        }
        return null;
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            buf.append('"');
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c >= 32 && c <= 126) {
                    buf.append(c);
                } else {
                    switch (c) {
                    case '\t':
                        buf.append("\\t");
                        break;
                    case '\n':
                        buf.append("\\n");
                        break;
                    case '\r':
                        buf.append("\\r");
                        break;
                    case '\b':
                        buf.append("\\b");
                        break;
                    case '\f':
                        buf.append("\\f");
                        break;
                    case '\'':
                        buf.append("\\'");
                        break;
                    case '\"':
                        buf.append("\\\"");
                        break;
                    case '\\':
                        buf.append("\\\\");
                        break;
                    default:
                        buf.append("\\u" + String.format("%04x", (int) c));
                    }
                }
            }
            buf.append('"');
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
