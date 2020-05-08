package pikaparser.clause;

import java.util.concurrent.PriorityBlockingQueue;

import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

public class RuleRef extends Clause {
    public String refdRuleName;

    public RuleRef(String refdRuleName) {
        super(new Clause[0]);
        this.refdRuleName = refdRuleName;
    }

    @Override
    public Match match(MatchDirection matchDirection, MemoTable memoTable, MemoKey memoKey, String input,
            PriorityBlockingQueue<MemoKey> priorityQueue) {
        throw new IllegalArgumentException(getClass().getSimpleName() + " node should not be in final grammar");
    }
    
    @Override
    public String toString() {
        if (toStringCached == null) {
            toStringCached = refdRuleName;
        }
        return toStringCached;
    }
}
