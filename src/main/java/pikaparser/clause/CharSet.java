package pikaparser.clause;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.Parser;

public class CharSet extends Terminal {

    public final Set<Character> charSet = new HashSet<>();

    public final List<CharSet> subCharSets = new ArrayList<>();

    public boolean invertMatch = false;

    public CharSet(char c) {
        super();
        this.charSet.add(c);
    }

    public CharSet(String chars) {
        super();
        for (int i = 0; i < chars.length(); i++) {
            this.charSet.add(chars.charAt(i));
        }
    }

    public CharSet(char[] chars) {
        super();
        for (int i = 0; i < chars.length; i++) {
            this.charSet.add(chars[i]);
        }
    }

    public CharSet(char minChar, char maxChar) {
        super();
        for (char c = minChar; c <= maxChar; c++) {
            this.charSet.add(c);
        }
    }

    public CharSet(CharSet... charSets) {
        super();
        for (CharSet charSet : charSets) {
            this.subCharSets.add(charSet);
        }
    }

    public CharSet(Collection<CharSet> charSets) {
        super();
        for (CharSet charSet : charSets) {
            if (charSet.invertMatch) {

            } else {
                this.charSet.addAll(charSet.charSet);
            }
        }
    }

    /** Invert in-place, and return this. */
    public CharSet invert() {
        invertMatch = !invertMatch;
        return this;
    }

    private boolean inputMatches(MemoKey memoKey, String input) {
        if (memoKey.startPos >= input.length()) {
            return false;
        }
        boolean matches = !charSet.isEmpty() //
                && (invertMatch ^ charSet.contains(input.charAt(memoKey.startPos)));
        if (matches) {
            return true;
        }
        if (!subCharSets.isEmpty()) {
            // SubCharSets may be inverted, so need to test each individually for efficiency,
            // rather than producing a large Set<Character> for all chars of an inverted CharSet
            for (CharSet subCharSet : subCharSets) {
                if (subCharSet.inputMatches(memoKey, input)) {
                    return true;
                }
            }
        }
        if (Parser.DEBUG) {
            System.out.println(
                    "Failed to match at position " + memoKey.startPos + ": " + memoKey.toStringWithRuleNames());
        }
        return false;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input) {
        if (inputMatches(memoKey, input)) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            return new Match(memoKey, /* firstMatchingSubClauseIdx = */ 0, /* len = */ 1,
                    Match.NO_SUBCLAUSE_MATCHES);
        }
        return null;
    }

    private void toString(StringBuilder buf) {
        var charsSorted = new ArrayList<>(charSet);
        Collections.sort(charsSorted);
        boolean isSingleChar = !invertMatch && charsSorted.size() == 1;
        if (isSingleChar) {
            char c = charsSorted.iterator().next();
            if (c == '\'') {
                buf.append("'\\''");
            } else if (c == '\\') {
                buf.append("'\\\\'");
            } else if (c >= 32 && c <= 126) {
                buf.append("'" + c + "'");
            } else if (c == '\n') {
                buf.append("'\\n'");
            } else if (c == '\r') {
                buf.append("'\\r'");
            } else if (c == '\t') {
                buf.append("'\\t'");
            } else if (c == '\f') {
                buf.append("'\\f'");
            } else {
                buf.append("'\\u" + String.format("%04x", (int) c) + "'");
            }
        } else {
            if (!charsSorted.isEmpty()) {
                buf.append('[');
                if (invertMatch) {
                    buf.append('^');
                }
                for (int i = 0; i < charsSorted.size(); i++) {
                    char c = charsSorted.get(i);
                    if (c == '\\') {
                        buf.append("\\\\");
                    } else if (c == ']') {
                        buf.append("\\]");
                    } else if (c == '[') {
                        buf.append("\\[");
                    } else if (c == '^' && i == 0) {
                        buf.append("\\^");
                    } else if (c >= 32 && c <= 126) {
                        buf.append(c);
                    } else if (c == '\n') {
                        buf.append("\\n");
                    } else if (c == '\r') {
                        buf.append("\\r");
                    } else if (c == '\t') {
                        buf.append("\\t");
                    } else {
                        buf.append("\\u" + String.format("%04x", (int) c));
                    }
                    int j = i + 1;
                    while (j < charsSorted.size() && charsSorted.get(j).charValue() == c + (j - i)) {
                        j++;
                    }
                    if (j > i + 2) {
                        buf.append("-");
                        i = j - 1;
                        buf.append(charsSorted.get(i));
                    }
                }
                buf.append(']');
            }
        }
    }

    private void getCharSets(List<CharSet> charSets) {
        if (!charSet.isEmpty()) {
            charSets.add(this);
        }
        for (var subCharSet : subCharSets) {
            subCharSet.getCharSets(charSets);
        }
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            List<CharSet> charSets = new ArrayList<>();
            getCharSets(charSets);
            var buf = new StringBuilder();
            if (charSets.size() > 1) {
                buf.append('(');
            }
            int startLen = buf.length();
            for (var charSet : charSets) {
                if (buf.length() > startLen) {
                    buf.append(" | ");
                }
                charSet.toString(buf);
            }
            if (charSets.size() > 1) {
                buf.append(')');
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
