package pikaparser.memotable;

import java.util.concurrent.atomic.AtomicInteger;

import pikaparser.clause.Clause;
import pikaparser.grammar.Grammar;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoEntry {
    /**
     * The current best {@link Match} for this {@link Clause} at this start position, or null if the clause didn't
     * match at this start position.
     */
    public Match bestMatch;

    /**
     * Add a new match to this {@link MemoEntry}, if it is better than the previous best match.
     * 
     * @return true if the match was updated.
     */
    public boolean setBestMatch(Match newMatch, AtomicInteger numMatchObjectsMemoized) {
        // If the new match is better than the current best match from the previous iteration
        if ((bestMatch == null || newMatch.isBetterThan(bestMatch))) {
            // Set the new best match (this should only be done once for each memo entry in each
            // parsing iteration, since activeSet is a set, and addNewBestMatch is called at most 
            // once per activeSet element).
            bestMatch = newMatch;
            
            numMatchObjectsMemoized.incrementAndGet();

            // Since there was a new best match at this memo entry, any parent clauses that have this clause
            // in the first position (that must match one or more characters) needs to be added to the active set
            if (Grammar.DEBUG) {
                System.out.println("Setting new best match: " + newMatch.toStringWithRuleNames());
            }
            return true;
        } else {
            return false;
        }
    }
}
