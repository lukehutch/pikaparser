//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion
//     and error recovery problems. Luke A. D. Hutchison, May 2020.
//     https://arxiv.org/abs/2005.06444
//
// This software is provided under the MIT license:
//
// Copyright 2020 Luke A. D. Hutchison
//  
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions
// of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//
package pikaparser.clause.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.StringUtils;

/** Terminal clause that matches a character or sequence of characters. */
public class CharSet extends Terminal {

    public final Set<Character> charSet = new HashSet<>();

    public final List<CharSet> subCharSets = new ArrayList<>();

    public boolean invertMatch = false;

    public CharSet(char... chars) {
        super();
        for (int i = 0; i < chars.length; i++) {
            this.charSet.add(chars[i]);
        }
    }

    public CharSet(CharSet... charSets) {
        super();
        for (CharSet charSet : charSets) {
            this.subCharSets.add(charSet);
        }
    }

    /** Invert in-place, and return this. */
    public CharSet invert() {
        invertMatch = !invertMatch;
        return this;
    }

    private boolean matchesInput(MemoKey memoKey, String input) {
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
                if (subCharSet.matchesInput(memoKey, input)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void determineWhetherCanMatchZeroChars() {
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        if (matchesInput(memoKey, input)) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            return new Match(memoKey, /* len = */ 1, Match.NO_SUBCLAUSE_MATCHES);
        }
        return null;
    }

    private void getCharSets(List<CharSet> charSets) {
        if (!charSet.isEmpty()) {
            charSets.add(this);
        }
        for (var subCharSet : subCharSets) {
            subCharSet.getCharSets(charSets);
        }
    }

    private void toString(StringBuilder buf) {
        var charsSorted = new ArrayList<>(charSet);
        Collections.sort(charsSorted);
        boolean isSingleChar = !invertMatch && charsSorted.size() == 1;
        if (isSingleChar) {
            char c = charsSorted.iterator().next();
            buf.append('\'');
            buf.append(StringUtils.escapeQuotedChar(c));
            buf.append('\'');
        } else {
            if (!charsSorted.isEmpty()) {
                buf.append('[');
                if (invertMatch) {
                    buf.append('^');
                }
                for (int i = 0; i < charsSorted.size(); i++) {
                    char c = charsSorted.get(i);
                    buf.append(StringUtils.escapeCharRangeChar(c));
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
