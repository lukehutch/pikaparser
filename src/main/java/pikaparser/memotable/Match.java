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
package pikaparser.memotable;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import pikaparser.clause.Clause;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.nonterminal.OneOrMore;

/** A complete match of a {@link Clause} at a given start position. */
public class Match {
    /** The {@link MemoKey}. */
    public final MemoKey memoKey;

    /** The length of the match. */
    public final int len;

    /**
     * The subclause index of the first matching subclause (will be 0 unless {@link #labeledClause} is a
     * {@link First}, and the matching clause was not the first subclause).
     */
    private int firstMatchingSubClauseIdx;

    /** The subclause matches. */
    private final Match[] subClauseMatches;

    /** There are no subclause matches for terminals. */
    public static final Match[] NO_SUBCLAUSE_MATCHES = new Match[0];

    /** Construct a new match. */
    public Match(MemoKey memoKey, int len, int firstMatchingSubClauseIdx, Match[] subClauseMatches) {
        this.memoKey = memoKey;
        this.len = len;
        this.firstMatchingSubClauseIdx = firstMatchingSubClauseIdx;
        this.subClauseMatches = subClauseMatches;
    }

    /** Construct a new match of a nonterminal clause other than {@link First}. */
    public Match(MemoKey memoKey, int len, Match[] subClauseMatches) {
        this(memoKey, len, /* firstMatchingSubClauseIdx = */ 0, subClauseMatches);
    }

    /** Construct a new terminal match. */
    public Match(MemoKey memoKey, int len) {
        this(memoKey, len, /* firstMatchingSubClauseIdx = */ 0, /* subClauseMatches = */ NO_SUBCLAUSE_MATCHES);
    }

    /** Construct a new zero-length match without subclauses. */
    public Match(MemoKey memoKey) {
        this(memoKey, /* len = */ 0);
    }

    /**
     * Get subclause matches. Automatically flattens the right-recursive structure of {@link OneOrMore} nodes,
     * collecting the subclause matches into a single array of (AST node label, subclause match) tuples.
     * 
     * @return A list of tuples: (AST node label, subclause match).
     */
    public List<Entry<String, Match>> getSubClauseMatches() {
        if (subClauseMatches.length == 0) {
            // This is a terminals, or an empty placeholder match returned by MemoTable.lookUpBestMatch
            return Collections.emptyList();
        }
        if (memoKey.clause instanceof OneOrMore) {
            // Flatten right-recursive structure of OneOrMore parse subtree
            var subClauseMatchesToUse = new ArrayList<Entry<String, Match>>();
            for (var curr = this; curr.subClauseMatches.length > 0;) {
                // Add head of right-recursive list to arraylist, paired with its AST node label, if present
                subClauseMatchesToUse.add(new SimpleEntry<>(curr.memoKey.clause.labeledSubClauses[0].astNodeLabel,
                        curr.subClauseMatches[0]));
                if (curr.subClauseMatches.length == 1) {
                    // The last element of the right-recursive list will have a single element, i.e. (head),
                    // rather than two elements, i.e. (head, tail) -- see the OneOrMore.match method
                    break;
                }
                // Move to tail of list
                curr = curr.subClauseMatches[1];
            }
            return subClauseMatchesToUse;
        } else if (memoKey.clause instanceof First) {
            // For First, pair the match with the AST node label from the subclause of idx firstMatchingSubclauseIdx
            return Arrays.asList(new SimpleEntry<>(
                    memoKey.clause.labeledSubClauses[firstMatchingSubClauseIdx].astNodeLabel, subClauseMatches[0]));
        } else {
            // For other clause types, return labeled subclause matches
            var numSubClauses = memoKey.clause.labeledSubClauses.length;
            var subClauseMatchesToUse = new ArrayList<Entry<String, Match>>(numSubClauses);
            for (int i = 0; i < numSubClauses; i++) {
                subClauseMatchesToUse.add(
                        new SimpleEntry<>(memoKey.clause.labeledSubClauses[i].astNodeLabel, subClauseMatches[i]));
            }
            return subClauseMatchesToUse;
        }
    }

    /**
     * Compare this {@link Match} to another {@link Match} of the same {@link Clause} type and start position.
     * 
     * @return true if this {@link Match} is a better match than the other {@link Match}.
     */
    public boolean isBetterThan(Match other) {
        if (other == this) {
            return false;
        }
        // An earlier subclause match in a First clause is better than a later subclause match
        if (memoKey.clause instanceof First) {
            if (this.firstMatchingSubClauseIdx < other.firstMatchingSubClauseIdx) {
                return true;
            } else if (this.firstMatchingSubClauseIdx > other.firstMatchingSubClauseIdx) {
                return false;
            }
        }
        // A longer match (i.e. a match that spans more characters in the input) is better than a shorter match
        return this.len > other.len;
    }

    public String toStringWithRuleNames() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey.toStringWithRuleNames() + "+" + len);
        //        buf.append(memoKey.toStringWithRuleNames() + "+" + len + " => [ ");
        //        var subClauseMatchesToUse = getSubClauseMatches();
        //        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
        //            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
        //            var subClauseMatch = subClauseMatchEnt.getValue();
        //            if (subClauseMatchIdx > 0) {
        //                buf.append(" ; ");
        //            }
        //            buf.append(subClauseMatch.toStringWithRuleNames());
        //        }
        //        buf.append(" ]");
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(memoKey + "+" + len);
        //        buf.append(" => [ ");
        //        var subClauseMatchesToUse = getSubClauseMatches();
        //        for (int i = 0; i < subClauseMatchesToUse.length; i++) {
        //            var s = subClauseMatchesToUse[i];
        //            if (i > 0) {
        //                buf.append(" ; ");
        //            }
        //            buf.append(s.toString());
        //        }
        //        buf.append(" ]");
        return buf.toString();
    }
}
