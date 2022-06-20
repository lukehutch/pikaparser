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

import java.util.BitSet;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;
import pikaparser.parser.utils.StringUtils;

/** Terminal clause that matches a character or sequence of characters. */
public class CharSet extends Terminal {

    private BitSet chars;
    private BitSet invertedChars;

    public CharSet(char... chars) {
        super();
        this.chars = new BitSet(0xffff);
        for (int i = 0; i < chars.length; i++) {
            this.chars.set(chars[i]);
        }
    }

    public CharSet(CharSet... charSets) {
        super();
        if (charSets.length == 0) {
            throw new IllegalArgumentException("Must provide at least one CharSet");
        }
        this.chars = new BitSet(0xffff);
        for (CharSet charSet : charSets) {
            if (charSet.chars != null) {
                for (int i = charSet.chars.nextSetBit(0); i >= 0; i = charSet.chars.nextSetBit(i + 1)) {
                    this.chars.set(i);
                }
            }
            if (charSet.invertedChars != null) {
                if (this.invertedChars == null) {
                    this.invertedChars = new BitSet(0xffff);
                }
                for (int i = charSet.invertedChars.nextSetBit(0); i >= 0; i = charSet.invertedChars
                        .nextSetBit(i + 1)) {
                    this.invertedChars.set(i);
                }
            }
        }
    }

    public CharSet(BitSet chars) {
        super();
        if (chars.cardinality() == 0) {
            throw new IllegalArgumentException("Must provide at least one char in a CharSet");
        }
        this.chars = chars;
    }

    /** Invert in-place, and return this. */
    public CharSet invert() {
        var tmp = chars;
        chars = invertedChars;
        invertedChars = tmp;
        toStringCached = null;
        return this;
    }

    @Override
    public boolean determineWhetherCanMatchZeroChars() {
        return false;
    }

    @Override
    public Match match(MemoTable memoTable, MemoKey memoKey, String input) {
        if (memoKey.startPos < input.length()) {
            char c = input.charAt(memoKey.startPos);
            if ((chars != null && chars.get(c)) || (invertedChars != null && !invertedChars.get(c))) {
                // Terminals are not memoized (i.e. don't look in the memo table)
                return new Match(memoKey, /* len = */ 1, Match.NO_SUBCLAUSE_MATCHES);
            }
        }
        return null;
    }

    private static void toString(BitSet chars, int cardinality, boolean inverted, StringBuilder buf) {
        boolean isSingleChar = !inverted && cardinality == 1;
        if (isSingleChar) {
            char c = (char) chars.nextSetBit(0);
            buf.append('\'');
            buf.append(StringUtils.escapeQuotedChar(c));
            buf.append('\'');
        } else {
            buf.append('[');
            if (inverted) {
                buf.append('^');
            }
            for (int i = chars.nextSetBit(0); i >= 0; i = chars.nextSetBit(i + 1)) {
                buf.append(StringUtils.escapeCharRangeChar((char) i));
                if (i < chars.size() - 1 && chars.get(i + 1)) {
                    // Contiguous char range
                    int end = i + 2;
                    while (end < chars.size() && chars.get(end)) {
                        end++;
                    }
                    int numCharsSpanned = end - i;
                    if (numCharsSpanned > 2) {
                        buf.append('-');
                    }
                    buf.append(StringUtils.escapeCharRangeChar((char) (end - 1)));
                    i = end - 1;
                }
            }
            buf.append(']');
        }
    }

    @Override
    public String toString() {
        if (toStringCached == null) {
            var buf = new StringBuilder();
            var charsCardinality = chars == null ? 0 : chars.cardinality();
            var invertedCharsCardinality = invertedChars == null ? 0 : invertedChars.cardinality();
            var invertedAndNot = charsCardinality > 0 && invertedCharsCardinality > 0;
            if (invertedAndNot) {
                buf.append('(');
            }
            if (charsCardinality > 0) {
                toString(chars, charsCardinality, false, buf);
            }
            if (invertedAndNot) {
                buf.append(" | ");
            }
            if (invertedCharsCardinality > 0) {
                toString(invertedChars, invertedCharsCardinality, true, buf);
            }
            if (invertedAndNot) {
                buf.append(')');
            }
            toStringCached = buf.toString();
        }
        return toStringCached;
    }
}
