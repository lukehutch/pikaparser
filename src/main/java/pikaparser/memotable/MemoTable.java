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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import pikaparser.clause.Clause;
import pikaparser.clause.nonterminal.NotFollowedBy;
import pikaparser.grammar.Grammar;
import pikaparser.parser.utils.IntervalUnion;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoTable {
    /**
     * A map from clause to startPos to a {@link Match} for the memo entry. (Use concurrent data structures so that
     * terminals can be memoized in parallel during initialization.)
     */
    private Map<MemoKey, Match> memoTable = new HashMap<>();

    /** The grammar. */
    public Grammar grammar;

    /** The input string. */
    public String input;

    /** The priority queue. */
    private PriorityQueue<Clause> priorityQueue;

    // -------------------------------------------------------------------------------------------------------------

    /** The number of {@link Match} instances created. */
    public final AtomicInteger numMatchObjectsCreated = new AtomicInteger();

    /**
     * The number of {@link Match} instances added to the memo table (some will be overwritten by later matches).
     */
    public final AtomicInteger numMatchObjectsMemoized = new AtomicInteger();

    // -------------------------------------------------------------------------------------------------------------

    public MemoTable(Grammar grammar, String input, PriorityQueue<Clause> priorityQueue) {
        this.grammar = grammar;
        this.input = input;
        this.priorityQueue = priorityQueue;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Look up the current best match for a given {@link MemoKey} in the memo table. */
    public Match lookUpBestMatch(MemoKey memoKey) {
        // Find current best match in memo table (null if there is no current best match)
        var bestMatch = memoTable.get(memoKey);
        if (bestMatch != null) {
            // If there is a current best match, return it
            return bestMatch;

        } else if (memoKey.clause instanceof NotFollowedBy) {
            // Need to match NotFollowedBy top-down
            return memoKey.clause.match(this, memoKey, input);

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
    public void addMatch(MemoKey memoKey, Match newMatch) {
        var matchUpdated = false;
        if (newMatch != null) {
            // Track memoization
            numMatchObjectsCreated.incrementAndGet();

            // Get the memo entry for memoKey if already present; if not, create a new entry
            var oldMatch = memoTable.get(memoKey);

            // If there is no old match, or the new match is better than the old match
            if ((oldMatch == null || newMatch.isBetterThan(oldMatch))) {
                // Store the new match in the memo entry
                memoTable.put(memoKey, newMatch);
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
                priorityQueue.add(seedParentClause);
                if (Grammar.DEBUG) {
                    System.out.println(
                            "    Following seed parent clause: " + seedParentClause.toStringWithRuleNames());
                }
            }
        }
        if (Grammar.DEBUG) {
            System.out.println(
                    (newMatch != null ? "Matched: " : "Failed to match: ") + memoKey.toStringWithRuleNames());
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get all {@link Match} entries, indexed by clause then start position. */
    public Map<Clause, NavigableMap<Integer, Match>> getAllNavigableMatches() {
        var clauseMap = new HashMap<Clause, NavigableMap<Integer, Match>>();
        memoTable.values().stream().forEach(match -> {
            var startPosMap = clauseMap.get(match.memoKey.clause);
            if (startPosMap == null) {
                startPosMap = new TreeMap<>();
                clauseMap.put(match.memoKey.clause, startPosMap);
            }
            startPosMap.put(match.memoKey.startPos, match);
        });
        return clauseMap;
    }

    /** Get all nonoverlapping {@link Match} entries, indexed by clause then start position. */
    public Map<Clause, NavigableMap<Integer, Match>> getAllNonOverlappingMatches() {
        var nonOverlappingClauseMap = new HashMap<Clause, NavigableMap<Integer, Match>>();
        for (var ent : getAllNavigableMatches().entrySet()) {
            var clause = ent.getKey();
            var startPosMap = ent.getValue();
            var prevEndPos = 0;
            var nonOverlappingStartPosMap = new TreeMap<Integer, Match>();
            for (var startPosEnt : startPosMap.entrySet()) {
                var startPos = startPosEnt.getKey();
                if (startPos >= prevEndPos) {
                    var match = startPosEnt.getValue();
                    nonOverlappingStartPosMap.put(startPos, match);
                    var endPos = startPos + match.len;
                    prevEndPos = endPos;
                }
            }
            nonOverlappingClauseMap.put(clause, nonOverlappingStartPosMap);
        }
        return nonOverlappingClauseMap;
    }

    /** Get all {@link Match} entries for the given clause, indexed by start position. */
    public NavigableMap<Integer, Match> getNavigableMatches(Clause clause) {
        var treeMap = new TreeMap<Integer, Match>();
        memoTable.entrySet().stream().forEach(ent -> {
            if (ent.getKey().clause == clause) {
                treeMap.put(ent.getKey().startPos, ent.getValue());
            }
        });
        return treeMap;
    }

    /** Get the {@link Match} entries for all matches of this clause. */
    public List<Match> getAllMatches(Clause clause) {
        var matches = new ArrayList<Match>();
        getNavigableMatches(clause).entrySet().stream().forEach(ent -> matches.add(ent.getValue()));
        return matches;
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of this clause, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getNonOverlappingMatches(Clause clause) {
        var matches = getAllMatches(clause);
        var nonoverlappingMatches = new ArrayList<Match>();
        for (int i = 0; i < matches.size(); i++) {
            var match = matches.get(i);
            var startPos = match.memoKey.startPos;
            var endPos = startPos + match.len;
            nonoverlappingMatches.add(match);
            while (i < matches.size() - 1 && matches.get(i + 1).memoKey.startPos < endPos) {
                i++;
            }
        }
        return nonoverlappingMatches;
    }

    /**
     * Get any syntax errors in the parse, as a map from start position to a tuple, (end position, span of input
     * string between start position and end position).
     */
    public NavigableMap<Integer, Entry<Integer, String>> getSyntaxErrors(String... syntaxCoverageRuleNames) {
        // Find the range of characters spanned by matches for each of the coverageRuleNames
        var parsedRanges = new IntervalUnion();
        for (var coverageRuleName : syntaxCoverageRuleNames) {
            var rule = grammar.getRule(coverageRuleName);
            for (var match : getNonOverlappingMatches(rule.labeledClause.clause)) {
                parsedRanges.addRange(match.memoKey.startPos, match.memoKey.startPos + match.len);
            }
        }
        // Find the inverse of the parsed ranges -- these are the syntax errors
        var unparsedRanges = parsedRanges.invert(0, input.length()).getNonOverlappingRanges();
        // Extract the input string span for each unparsed range
        var syntaxErrorSpans = new TreeMap<Integer, Entry<Integer, String>>();
        unparsedRanges.entrySet().stream().forEach(ent -> syntaxErrorSpans.put(ent.getKey(),
                new SimpleEntry<>(ent.getValue(), input.substring(ent.getKey(), ent.getValue()))));
        return syntaxErrorSpans;
    }
}
