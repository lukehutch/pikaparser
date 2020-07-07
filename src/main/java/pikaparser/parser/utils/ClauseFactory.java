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
package pikaparser.parser.utils;

import java.util.BitSet;
import java.util.Optional;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.nonterminal.FollowedBy;
import pikaparser.clause.nonterminal.NotFollowedBy;
import pikaparser.clause.nonterminal.OneOrMore;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.clause.terminal.CharSeq;
import pikaparser.clause.terminal.CharSet;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.terminal.Start;
import pikaparser.grammar.Rule;
import pikaparser.grammar.Rule.Associativity;

/** Clause factory, enabling the construction of clauses without "new", using static imports. */
public class ClauseFactory {
    /** Construct a {@link Rule}. */
    public static Rule rule(String ruleName, Clause clause) {
        // Use -1 as precedence if rule group has only one precedence
        return rule(ruleName, -1, /* associativity = */ null, clause);
    }

    /** Construct a {@link Rule} with the given precedence and associativity. */
    public static Rule rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        var rule = new Rule(ruleName, precedence, associativity, clause);
        return rule;
    }

    /** Construct a {@link Seq} clause. */
    public static Clause seq(Clause... subClauses) {
        return new Seq(subClauses);
    }

    /** Construct a {@link OneOrMore} clause. */
    public static Clause oneOrMore(Clause subClause) {
        // It doesn't make sense to wrap these clause types in OneOrMore, but the OneOrMore should have
        // no effect if this does occur in the grammar, so remove it
        if (subClause instanceof OneOrMore || subClause instanceof Nothing || subClause instanceof FollowedBy
                || subClause instanceof NotFollowedBy || subClause instanceof Start) {
            return subClause;
        }
        return new OneOrMore(subClause);
    }

    /** Construct an {@link Optional} clause. */
    public static Clause optional(Clause subClause) {
        // Optional(X) -> First(X, Nothing)
        return first(subClause, nothing());
    }

    /** Construct a {@link ZeroOrMore} clause. */
    public static Clause zeroOrMore(Clause subClause) {
        // ZeroOrMore(X) => Optional(OneOrMore(X)) => First(OneOrMore(X), Nothing)
        return optional(oneOrMore(subClause));
    }

    /** Construct a {@link First} clause. */
    public static Clause first(Clause... subClauses) {
        return new First(subClauses);
    }

    /** Construct a {@link FollowedBy} clause. */
    public static Clause followedBy(Clause subClause) {
        if (subClause instanceof Nothing) {
            // FollowedBy(Nothing) -> Nothing (since Nothing always matches)
            return subClause;
        } else if (subClause instanceof FollowedBy || subClause instanceof NotFollowedBy
                || subClause instanceof Start) {
            throw new IllegalArgumentException(FollowedBy.class.getSimpleName() + "("
                    + subClause.getClass().getSimpleName() + ") is nonsensical");
        }
        return new FollowedBy(subClause);
    }

    /** Construct a {@link NotFollowedBy} clause. */
    public static Clause notFollowedBy(Clause subClause) {
        if (subClause instanceof Nothing) {
            throw new IllegalArgumentException(NotFollowedBy.class.getSimpleName() + "("
                    + Nothing.class.getSimpleName() + ") will never match anything");
        } else if (subClause instanceof NotFollowedBy) {
            // Doubling NotFollowedBy yields FollowedBy.
            // N.B. this will not catch the case of "X <- !Y; Y <- !Z;", since RuleRefs are
            // not resolved yet
            return new FollowedBy(subClause.labeledSubClauses[0].clause);
        } else if (subClause instanceof FollowedBy || subClause instanceof Start) {
            throw new IllegalArgumentException(NotFollowedBy.class.getSimpleName() + "("
                    + subClause.getClass().getSimpleName() + ") is nonsensical");
        }
        return new NotFollowedBy(subClause);
    }

    /** Construct a {@link Start} terminal. */
    public static Clause start() {
        return new Start();
    }

    /** Construct a {@link Nothing} terminal. */
    public static Clause nothing() {
        return new Nothing();
    }

    /** Construct a terminal that matches a string token. */
    public static Clause str(String str) {
        if (str.length() == 1) {
            return c(str.charAt(0));
        } else {
            return new CharSeq(str, /* ignoreCase = */ false);
        }
    }

    /** Construct a terminal that matches one instance of any character given in the varargs param. */
    public static CharSet c(char... chrs) {
        return new CharSet(chrs);
    }

    /** Construct a terminal that matches one instance of any character in a given string. */
    public static CharSet cInStr(String str) {
        return new CharSet(str.toCharArray());
    }

    /** Construct a terminal that matches a character range. */
    public static CharSet cRange(char minChar, char maxChar) {
        if (maxChar < minChar) {
            throw new IllegalArgumentException("maxChar < minChar");
        }
        char[] chars = new char[maxChar - minChar + 1];
        for (char c = minChar; c <= maxChar; c++) {
            chars[c - minChar] = c;
        }
        return new CharSet(chars);
    }

    /**
     * Construct a terminal that matches a character range, specified using regexp notation without the square
     * brackets.
     */
    public static CharSet cRange(String charRangeStr) {
        boolean invert = charRangeStr.startsWith("^");
        var charList = StringUtils.getCharRangeChars(invert ? charRangeStr.substring(1) : charRangeStr);
        var chars = new BitSet(0xffff);
        for (int i = 0; i < charList.size(); i++) {
            var c = charList.get(i);
            if (c.length() == 2) {
                // Unescape \^, \-, \], \\
                c = c.substring(1);
            }
            var c0 = c.charAt(0);
            if (i <= charList.size() - 3 && charList.get(i + 1).equals("-")) {
                var cEnd = charList.get(i + 2);
                if (cEnd.length() == 2) {
                    // Unescape \^, \-, \], \\
                    cEnd = cEnd.substring(1);
                }
                var cEnd0 = cEnd.charAt(0);
                if (cEnd0 < c0) {
                    throw new IllegalArgumentException("Char range limits out of order: " + c0 + ", " + cEnd0);
                }
                chars.set(c0, cEnd0 + 1);
                i += 2;
            } else {
                chars.set(c0);
            }
        }
        return invert ? new CharSet(chars).invert() : new CharSet(chars);
    }

    /** Construct a character set as the union of other character sets. */
    public static CharSet c(CharSet... charSets) {
        return new CharSet(charSets);
    }

    /** Construct an {@link ASTNodeLabel}. */
    public static Clause ast(String astNodeLabel, Clause clause) {
        return new ASTNodeLabel(astNodeLabel, clause);
    }

    /** Construct a {@link RuleRef}. */
    public static Clause ruleRef(String ruleName) {
        return new RuleRef(ruleName);
    }
}
