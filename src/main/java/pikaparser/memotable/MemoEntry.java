package pikaparser.memotable;

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
    public void addNewBestMatch(Match newMatch, PriorityBlockingQueue<MemoKey> priorityQueue,
            AtomicInteger numMatchObjectsMemoized) {
        // If the new match is better than the current best match from the previous iteration
        if ((bestMatch == null || newMatch.compareTo(bestMatch) < 0)) {
            // Set the new best match (this should only be done once for each memo entry in each
            // parsing iteration, since activeSet is a set, and addNewBestMatch is called at most 
            // once per activeSet element).
            bestMatch = newMatch;
            numMatchObjectsMemoized.incrementAndGet();

            // Since there was a new best match at this memo entry, any parent clauses that have this clause
            // in the first position (that must match one or more characters) needs to be added to the active set
            StringBuilder debug = null;
            if (Parser.DEBUG) {
                debug = new StringBuilder();
                debug.append("Setting new best match: " + newMatch.toStringWithRuleNames() + "\n");
            }
            for (var seedParentClause : memoKey.clause.seedParentClauses) {
                MemoKey parentMemoKey = new MemoKey(seedParentClause, memoKey.startPos);
                priorityQueue.add(parentMemoKey);

                if (Parser.DEBUG) {
                    debug.append(
                            "    Following seed parent clause: " + parentMemoKey.toStringWithRuleNames() + "\n");
                }
            }
            if (Parser.DEBUG) {
                System.out.print(debug);
            }

            if (Parser.DEBUG) {
                System.out.println("Found better match: " + newMatch.toStringWithRuleNames());
            }
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
