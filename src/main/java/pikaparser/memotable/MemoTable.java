//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: parsing in reverse solves the left recursion and error recovery problems
//     Luke A. D. Hutchison, May 2020
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import pikaparser.clause.Clause;
import pikaparser.grammar.Grammar;
import pikaparser.parser.utils.GrammarUtils;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoTable {
    /**
     * A map from clause to startPos to a {@link Match} for the memo entry. (Use concurrent data structures so that
     * terminals can be memoized in parallel during initialization.)
     */
    private Map<Clause, NavigableMap<Integer, Match>> memoTable = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------------------------------------------

    /** The number of {@link Match} instances created. */
    public final AtomicInteger numMatchObjectsCreated = new AtomicInteger();

    /**
     * The number of {@link Match} instances added to the memo table (some will be overwritten by later matches).
     */
    public final AtomicInteger numMatchObjectsMemoized = new AtomicInteger();

    // -------------------------------------------------------------------------------------------------------------

    public MemoTable(Grammar grammar) {
        for (var clause : grammar.allClauses) {
            // Use a concurrent data structure so that terminals can be memoized in parallel during initialization
            memoTable.put(clause, new ConcurrentSkipListMap<>());
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Look up the current best match for a given {@link MemoKey} in the memo table. */
    public Match lookUpBestMatch(MemoKey memoKey) {
        // Find current best match in memo table (null if there is no current best match)
        var bestMatch = memoTable.get(memoKey.clause).get(memoKey.startPos);
        if (bestMatch != null) {
            // If there is a current best match, return it
            return bestMatch;

        } else if (memoKey.clause.canMatchZeroChars) {
            // If there is no match in the memo table for this clause, but this clause can match zero characters,
            // then we need to return a new zero-length match to the parent clause. (This is part of the strategy
            // for minimizing the number of zero-length matches that are memoized.)
            // (N.B. this match will not have any subclause matches, which may be unexpected, so conversion of
            // parse tree to AST should be robust to this.)
            return new Match(memoKey);
        }
        // No match was found in the memo table
        return null;
    }

    /**
     * Add a new {@link Match} to the memo table, if the match is non-null. Schedule seed parent clauses for
     * matching if the match is non-null or if the parent clause can match zero characters.
     */
    public void addMatch(MemoKey memoKey, Match newMatch, PriorityBlockingQueue<MemoKey> priorityQueue) {
        var matchUpdated = false;
        if (newMatch != null) {
            // Track memoization
            numMatchObjectsCreated.incrementAndGet();

            // Get the memo entry for memoKey if already present; if not, create a new entry
            var clauseMatches = memoTable.get(memoKey.clause);
            var oldMatch = clauseMatches.get(memoKey.startPos);

            // If there is no old match, or the new match is better than the old match
            if ((oldMatch == null || newMatch.isBetterThan(oldMatch))) {
                // Store the new match in the memo entry
                clauseMatches.put(memoKey.startPos, newMatch);
                matchUpdated = true;

                // Track memoization
                numMatchObjectsMemoized.incrementAndGet();
                if (Grammar.DEBUG) {
                    System.out.println("Setting new best match: " + newMatch.toStringWithRuleNames());
                }
            }
        }
        for (var seedParentClause : memoKey.clause.seedParentClauses) {
            // If there was a valid match, or if there was no match but the parent clause can match
            // zero characters, schedule the parent clause for matching. (This is part of the strategy
            // for minimizing the number of zero-length matches that are memoized.)
            if (matchUpdated || seedParentClause.canMatchZeroChars) {
                var parentMemoKey = new MemoKey(seedParentClause, memoKey.startPos);
                priorityQueue.add(parentMemoKey);
                if (Grammar.DEBUG) {
                    System.out
                            .println("    Following seed parent clause: " + parentMemoKey.toStringWithRuleNames());
                }
            }
        }
        if (Grammar.DEBUG) {
            System.out.println(
                    (newMatch != null ? "Matched: " : "Failed to match: ") + memoKey.toStringWithRuleNames());
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get all {@link Match} entries for the given clause, indexed by start position. */
    public NavigableMap<Integer, Match> getNavigableMatches(Clause clause) {
        return memoTable.get(clause);
    }

    /** Get the {@link Match} entries for all matches of this clause. */
    public List<Match> getAllMatches(Clause clause) {
        return memoTable.get(clause).entrySet().stream().map(Entry::getValue).collect(Collectors.toList());
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of this clause, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getNonOverlappingMatches(Clause clause) {
        var clauseMatches = memoTable.get(clause);
        var firstEntry = clauseMatches.firstEntry();
        var nonoverlappingMatches = new ArrayList<Match>();
        if (firstEntry != null) {
            // If there was at least one memo entry
            for (var ent = firstEntry; ent != null;) {
                var startPos = ent.getKey();
                var match = ent.getValue();
                // Store match
                nonoverlappingMatches.add(match);
                // Start looking for a new match in the memo table after the end of the previous match.
                // Need to consume at least one character per match to avoid getting stuck in an infinite loop,
                // hence the Math.max(1, X) term. Have to subtract 1, because higherEntry() starts searching
                // at a position one greater than its parameter value.
                ent = clauseMatches.higherEntry(startPos + Math.max(1, match.len) - 1);
            }
        }
        return nonoverlappingMatches;
    }

    /**
     * Get any syntax errors in the parse, as a map from start position to a tuple, (end position, span of input
     * string between start position and end position).
     */
    public NavigableMap<Integer, Entry<Integer, String>> getSyntaxErrors(Grammar grammar, String input,
            String... syntaxCoverageRuleNames) {
        // Find the range of characters spanned by matches for each of the coverageRuleNames
        var parsedRanges = new TreeMap<Integer, Entry<Integer, String>>();
        for (var coverageRuleName : syntaxCoverageRuleNames) {
            var rule = grammar.ruleNameWithPrecedenceToRule.get(coverageRuleName);
            if (rule != null) {
                for (var match : getNonOverlappingMatches(rule.labeledClause.clause)) {
                    GrammarUtils.addRange(match.memoKey.startPos, match.memoKey.startPos + match.len, input,
                            parsedRanges);
                }
            }
        }
        // Find the inverse of the spanned ranges -- these are the syntax errors
        var syntaxErrorRanges = new TreeMap<Integer, Entry<Integer, String>>();
        int prevEndPos = 0;
        for (var ent : parsedRanges.entrySet()) {
            var currStartPos = ent.getKey();
            var currEndPos = ent.getValue().getKey();
            if (currStartPos > prevEndPos) {
                // At least one character was not matched by one of the listed rules
                syntaxErrorRanges.put(prevEndPos,
                        new SimpleEntry<>(currStartPos, input.substring(prevEndPos, currStartPos)));
            }
            prevEndPos = currEndPos;
        }
        if (!parsedRanges.isEmpty()) {
            var lastEnt = parsedRanges.lastEntry();
            var lastEntEndPos = lastEnt.getValue().getKey();
            if (lastEntEndPos < input.length()) {
                // There was at least one character before the end of the string that was not matched
                // by one of the listed rules
                syntaxErrorRanges.put(lastEntEndPos,
                        new SimpleEntry<>(input.length(), input.substring(lastEntEndPos, input.length())));
            }
        }
        return syntaxErrorRanges;
    }
}
