package pikaparser.memotable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import pikaparser.clause.Clause;
import pikaparser.parser.Parser;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoEntry {
    /** The {@link MemoKey} for this entry. */
    public final MemoKey memoKey;

    /** The current best {@link Match} for this {@link Clause} at this start position. */
    public Match bestMatch;

    /**
     * The match for this MemoEntry added in the current iteration -- this will replace {@link bestMatch} if it is a
     * better match.
     */
    public PriorityBlockingQueue<Match> newMatches = new PriorityBlockingQueue<>();

    public Set<MemoKey> backRefs = Collections.newSetFromMap(new ConcurrentHashMap<MemoKey, Boolean>());

    public MemoEntry(MemoKey memoKey) {
        this.memoKey = memoKey;
    }

    /**
     * Add a new match to this {@link MemoEntry}, if it is better than the previous best match.
     *
     * <p>
     * This method is potentially run in a multiple threads for a single {@link MemoEntry}, in the first stage of
     * the iteration.
     */
    public void addNewBestMatch(Match newMatch, Set<MemoEntry> updatedEntries) {
        // If the new match is better than the current best match from the previous iteration
        if (bestMatch == null || newMatch.compareTo(bestMatch) < 0) {
            // Add the new match. There may be more than one match added in a given iteration -- a zero-width
            // placeholder match (if canMatchZeroChars is true for the clause), and one or more (equal) "real"
            // matches with length greater than zero.
            newMatches.add(newMatch);

            // Mark entry as changed
            updatedEntries.add(this);

            if (Parser.DEBUG) {
                System.out.println("Found better match: " + newMatch.toStringWithRuleNames());
            }
        }
    }

    /**
     * If there was a new best match for this {@link MemoEntry}, copy the new best match to bestMatch, add any
     * parent clauses to the active set (following the grammar structure and backrefs), and clear the backrefs.
     * 
     * <p>
     * This method is run in a single thread per {@link MemoEntry}, in the second stage of the iteration.
     */
    public void updateBestMatch(String input, Set<MemoKey> activeSetOut, AtomicInteger numMatchObjectsMemoized) {
        // Get the best new updated match for this MemoEntry, if there is one
        var newBestMatch = newMatches.peek();
        if (newBestMatch != null) {
            // Clear newMatches for the next iteration
            newMatches.clear();

            // Replace bestMatch with newMatch
            bestMatch = newBestMatch;
            numMatchObjectsMemoized.incrementAndGet();

            StringBuilder debug = null;
            if (Parser.DEBUG) {
                debug = new StringBuilder();
                debug.append("Setting new best match: " + bestMatch.toStringWithRuleNames() + "\n");
            }

            // Since there was a new best match at this memo entry, any parent clauses that have this clause
            // in the first position (that must match one or more characters) needs to be added to the active set
            for (var seedParentClause : memoKey.clause.seedParentClauses) {
                MemoKey parentMemoKey = new MemoKey(seedParentClause, bestMatch.memoKey.startPos);
                activeSetOut.add(parentMemoKey);

                if (Parser.DEBUG) {
                    debug.append(
                            "    Following seed parent clause: " + parentMemoKey.toStringWithRuleNames() + "\n");
                }
            }

            // Any parent clause that depended upon the previous match also needs to be added to the active set
            for (var backref : backRefs) {
                activeSetOut.add(backref);

                if (Parser.DEBUG) {
                    debug.append("    Following backref: " + backref.toStringWithRuleNames() + "\n");
                }
            }

            if (Parser.DEBUG) {
                System.out.print(debug);
            }

            // Clear backrefs for the next iteration
            backRefs.clear();
        }
    }

    public String toStringWithRuleNames() {
        return memoKey.toStringWithRuleNames();
    }

    @Override
    public String toString() {
        return memoKey.toString();
    }
}
