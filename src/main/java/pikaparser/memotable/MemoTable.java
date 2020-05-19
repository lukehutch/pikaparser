package pikaparser.memotable;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import pikaparser.clause.Clause;
import pikaparser.grammar.Grammar;
import pikaparser.parser.utils.GrammarUtils;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoTable {
    /**
     * A map from clause to startPos to MemoEntry. (This uses concurrent data structures so that terminals can be
     * memoized in parallel.)
     */
    private Map<Clause, NavigableMap<Integer, MemoEntry>> memoTable = new ConcurrentHashMap<>();

    /** The number of {@link Match} instances created. */
    public final AtomicInteger numMatchObjectsCreated = new AtomicInteger();

    /**
     * The number of {@link Match} instances added to the memo table (some will be overwritten by later matches).
     */
    public final AtomicInteger numMatchObjectsMemoized = new AtomicInteger();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * If matchDirection == BOTTOM_UP, get the current best match in the memo table without recursing to child
     * clauses, or create a new memo entry as a placeholder to use even if there is no current match at the
     * requested {@link MemoKey}.
     * 
     * <p>
     * If matchDirection == TOP_DOWN, recurse down through child clauses (standard recursive descent parsing,
     * unmemoized).
     */
    public Match lookUpBestMatch(MemoKey memoKey) {
        // Create a new memo entry for non-terminals
        // (Have to add memo entry if terminal does match, since a new match needs to trigger parent clauses.)
        // Get MemoEntry for the MemoKey
        var clauseEntries = memoTable.get(memoKey.clause);
        var memoEntry = clauseEntries == null ? null : clauseEntries.get(memoKey.startPos);

        if (memoEntry != null && memoEntry.bestMatch != null) {
            // If there is already a memoized best match in the MemoEntry, return it
            return memoEntry.bestMatch;

        } else if (memoKey.clause.canMatchZeroChars) {
            // Special case -- if there is no current best match for the memo, but its clause always matches
            // zero or more characters, return a zero-width match.
            int firstMatchingSubClauseIdx = 0;
            for (int i = 0; i < memoKey.clause.labeledSubClauses.length; i++) {
                // The matching subclause is the first subclause that can match zero characters
                // (this works for all PEG operator types)
                if (memoKey.clause.labeledSubClauses[i].clause.canMatchZeroChars) {
                    firstMatchingSubClauseIdx = i;
                    break;
                }
            }
            // Don't need to memoize this match, since it is just a placeholder until the real match state is known
            return new Match(memoKey, firstMatchingSubClauseIdx, /* len = */ 0, Match.NO_SUBCLAUSE_MATCHES);
        }

        // No match was found in the memo table
        return null;
    }

    /**
     * Add a new {@link Match} to the memo table, if the match is non-null. Schedule seed parent clauses for
     * matching if the match is non-null or if the parent clause can match zero characters.
     */
    public void addMatch(MemoKey memoKey, Match match, PriorityBlockingQueue<MemoKey> priorityQueue) {
        if (match != null) {
            numMatchObjectsCreated.incrementAndGet();

            // Get the memo entry for memoKey if already present; if not, create a new entry
            var clauseEntries = memoTable.computeIfAbsent(memoKey.clause, c -> new ConcurrentSkipListMap<>());
            var memoEntry = clauseEntries.computeIfAbsent(memoKey.startPos, s -> new MemoEntry());

            // Record the new match in the memo entry, and schedule the memo entry to be updated  
            memoEntry.setNewMatch(match, numMatchObjectsMemoized);
        }

        for (var seedParentClause : memoKey.clause.seedParentClauses) {
            // If there was a valid match, or if there was no match but the parent clause can match
            // zero characters regardless, schedule the parent clause for matching
            if (match != null || seedParentClause.canMatchZeroChars) {
                var parentMemoKey = new MemoKey(seedParentClause, memoKey.startPos);
                priorityQueue.add(parentMemoKey);

                if (Grammar.DEBUG) {
                    System.out
                            .println("    Following seed parent clause: " + parentMemoKey.toStringWithRuleNames());
                }
            }
        }

        if (Grammar.DEBUG) {
            System.out
                    .println((match != null ? "Matched: " : "Failed to match: ") + memoKey.toStringWithRuleNames());
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get all {@link MemoEntry} entries for the given clause, indexed by start position. */
    public NavigableMap<Integer, MemoEntry> getNavigableMatches(Clause clause) {
        return memoTable.get(clause);
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of this clause, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getNonOverlappingMatches(Clause clause) {
        var skipList = memoTable.get(clause);
        if (skipList == null) {
            return Collections.emptyList();
        }
        var firstEntry = skipList.firstEntry();
        var nonoverlappingMatches = new ArrayList<Match>();
        if (firstEntry != null) {
            // If there was at least one memo entry
            for (var ent = firstEntry; ent != null;) {
                var startPos = ent.getKey();
                var memoEntry = ent.getValue();
                if (memoEntry.bestMatch != null) {
                    // Only store matches
                    nonoverlappingMatches.add(memoEntry.bestMatch);
                    // Start looking for a new match in the memo table after the end of the previous match.
                    // Need to consume at least one character per match to avoid getting stuck in an infinite loop,
                    // hence the Math.max(1, X) term. Have to subtract 1, because higherEntry() starts searching
                    // at a position one greater than its parameter value.
                    ent = skipList.higherEntry(startPos + Math.max(1, memoEntry.bestMatch.len) - 1);
                } else {
                    // Move to next MemoEntry
                    ent = skipList.higherEntry(startPos);
                }
            }
        }
        return nonoverlappingMatches;
    }

    /**
     * Get the {@link Match} entries for all nonoverlapping matches of this clause, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    public List<Match> getAllMatches(Clause clause) {
        var skipList = memoTable.get(clause);
        if (skipList == null) {
            return Collections.emptyList();
        }
        var firstEntry = skipList.firstEntry();
        var allMatches = new ArrayList<Match>();
        if (firstEntry != null) {
            // If there was at least one memo entry
            for (var ent = firstEntry; ent != null;) {
                var startPos = ent.getKey();
                var memoEntry = ent.getValue();
                if (memoEntry.bestMatch != null) {
                    // Only store matches
                    allMatches.add(memoEntry.bestMatch);
                }
                // Move to next MemoEntry
                ent = skipList.higherEntry(startPos);
            }
        }
        return allMatches;
    }

    /**
     * Get the {@link Match} entries for all postions where a match was queried, but there was no match.
     */
    public List<Integer> getNonMatchPositions(Clause clause) {
        var skipList = memoTable.get(clause);
        if (skipList == null) {
            return Collections.emptyList();
        }
        var firstEntry = skipList.firstEntry();
        var nonMatches = new ArrayList<Integer>();
        if (firstEntry != null) {
            // If there was at least one memo entry
            for (var ent = firstEntry; ent != null;) {
                var startPos = ent.getKey();
                var memoEntry = ent.getValue();
                if (memoEntry.bestMatch == null) {
                    nonMatches.add(startPos);
                }
                // Move to next MemoEntry
                ent = skipList.higherEntry(startPos);
            }
        }
        return nonMatches;
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
                syntaxErrorRanges.put(prevEndPos,
                        new SimpleEntry<>(currStartPos, input.substring(prevEndPos, currStartPos)));
            }
            prevEndPos = currEndPos;
        }
        if (!parsedRanges.isEmpty()) {
            var lastEnt = parsedRanges.lastEntry();
            var lastEntEndPos = lastEnt.getValue().getKey();
            if (lastEntEndPos < input.length()) {
                // Add final syntax error range, if there is one
                syntaxErrorRanges.put(lastEntEndPos,
                        new SimpleEntry<>(input.length(), input.substring(lastEntEndPos, input.length())));
            }
        }
        return syntaxErrorRanges;
    }
}
