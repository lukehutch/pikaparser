package pikaparser.memotable;

import pikaparser.clause.Clause;

/** A memo entry for a specific {@link Clause} at a specific start position. */
public class MemoKey implements Comparable<MemoKey> {
    /** The {@link Clause}. */
    public final Clause clause;

    /** The start position. */
    public final int startPos;

    public MemoKey(Clause clause, int startPos) {
        this.clause = clause;
        this.startPos = startPos;
    }

    /** Sort order for {@link MemoKey} instances. */
    @Override
    public int compareTo(MemoKey other) {
        // Sort MemoKeys in reverse order of startPos 
        int diff = -(this.startPos - other.startPos);
        if (diff != 0) {
            return diff;
        }
        // Break ties using clause topological sort index, sorting from bottom-up 
        return this.clause.clauseIdx - other.clause.clauseIdx;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() ^ startPos;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof MemoKey)) {
            return false;
        }
        MemoKey other = (MemoKey) obj;
        return this.clause == other.clause && this.startPos == other.startPos;
    }

    public String toStringWithRuleNames() {
        return clause.toStringWithRuleNames() + " : " + startPos;
    }

    @Override
    public String toString() {
        return clause + " : " + startPos;
    }
}
