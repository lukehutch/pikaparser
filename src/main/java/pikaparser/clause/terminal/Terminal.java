package pikaparser.clause.terminal;

import pikaparser.clause.Clause;

public abstract class Terminal extends Clause {
    protected Terminal() {
        super(new Clause[0]);
    }
}
