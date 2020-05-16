package pikaparser.clause.util;

import java.util.ArrayList;
import java.util.List;

import pikaparser.clause.Clause;
import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.aux.RuleRef;
import pikaparser.clause.nonterminal.First;
import pikaparser.clause.nonterminal.FollowedBy;
import pikaparser.clause.nonterminal.NotFollowedBy;
import pikaparser.clause.nonterminal.OneOrMore;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.clause.terminal.CharSeq;
import pikaparser.clause.terminal.CharSet;
import pikaparser.clause.terminal.Nothing;
import pikaparser.clause.terminal.Start;
import pikaparser.grammar.Rule;
import pikaparser.grammar.Rule.Associativity;

// Clause factories, for clause optimization and sanity checking
public class ClauseFactory {
    public static Rule rule(String ruleName, Clause clause) {
        // Use -1 as precedence if rule group has only one precedence
        return rule(ruleName, -1, /* associativity = */ null, clause);
    }

    public static Rule rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        var rule = new Rule(ruleName, precedence, associativity, clause);
        return rule;
    }

    public static Clause ast(String astNodeLabel, Clause clause) {
        return new ASTNodeLabel(astNodeLabel, clause);
    }

    public static Clause oneOrMore(Clause subClause) {
        // It doesn't make sense to wrap these clause types in OneOrMore, but the OneOrMore should have
        // no effect if this does occur in the grammar, so remove it
        if (subClause instanceof OneOrMore || subClause instanceof Nothing || subClause instanceof FollowedBy
                || subClause instanceof NotFollowedBy || subClause instanceof Start) {
            return subClause;
        }
        return new OneOrMore(subClause);
    }

    public static Clause zeroOrMore(Clause subClause) {
        // ZeroOrMore(X) -> FirstMatch(OneOrMore(X), Nothing)
        return optional(oneOrMore(subClause));
    }

    public static Clause optional(Clause subClause) {
        // Optional(X) -> FirstMatch(X, Nothing)
        return first(subClause, nothing());
    }

    public static Clause first(Clause... subClauses) {
        for (int i = 0; i < subClauses.length; i++) {
            if (subClauses[i] instanceof Nothing && i < subClauses.length - 1) {
                throw new IllegalArgumentException("Subclauses of " + First.class.getSimpleName() + " after "
                        + Nothing.class.getSimpleName() + " will not be matched");
            }
        }
        return new First(subClauses);
    }

    public static Clause first(List<Clause> subClauses) {
        return first(subClauses.toArray(new Clause[0]));
    }

    public static Clause followedBy(Clause subClause) {
        if (subClause instanceof Nothing) {
            // FollowedBy(Nothing) -> Nothing (since Nothing always matches)
            return subClause;
        } else if (subClause instanceof FollowedBy || subClause instanceof NotFollowedBy
                || subClause instanceof Start) {
            throw new IllegalArgumentException(FollowedBy.class.getSimpleName() + "("
                    + subClause.getClass().getSimpleName() + "(X)) is nonsensical");
        }
        return new FollowedBy(subClause);
    }

    public static Clause notFollowedBy(Clause subClause) {
        if (subClause instanceof Nothing) {
            throw new IllegalArgumentException(NotFollowedBy.class.getSimpleName() + "("
                    + Nothing.class.getSimpleName() + ") will never match anything");
        } else if (subClause instanceof FollowedBy || subClause instanceof NotFollowedBy
                || subClause instanceof Start) {
            throw new IllegalArgumentException(NotFollowedBy.class.getSimpleName() + "("
                    + subClause.getClass().getSimpleName() + "(X)) is nonsensical");
        }
        return new NotFollowedBy(subClause);
    }

    public static Clause start() {
        return new Start();
    }

    public static Clause nothing() {
        return new Nothing();
    }

    public static Clause seq(Clause... subClauses) {
        return new Seq(subClauses);
    }

    public static Clause seq(List<Clause> subClauses) {
        return new Seq(subClauses);
    }

    public static Clause r(String ruleName) {
        return new RuleRef(ruleName);
    }

    public static CharSet c(char chr) {
        return new CharSet(chr);
    }

    public static CharSet c(char chrStart, char chrEnd) {
        return new CharSet(chrStart, chrEnd);
    }

    public static CharSet c(String chrs) {
        return new CharSet(chrs);
    }

    public static CharSet c(CharSet... charSets) {
        return new CharSet(charSets);
    }

    public static CharSet c(char chr, boolean invert) {
        var cs = new CharSet(chr);
        if (invert) {
            cs.invert();
        }
        return cs;
    }

    public static CharSet cRange(String charRanges) {
        boolean invert = charRanges.startsWith("^");
        List<CharSet> charSets = new ArrayList<>();
        for (int i = invert ? 1 : 0; i < charRanges.length(); i++) {
            char c = charRanges.charAt(i);
            if (i <= charRanges.length() - 3 && charRanges.charAt(i + 1) == '-') {
                char cEnd = charRanges.charAt(i + 2);
                if (cEnd < c) {
                    throw new IllegalArgumentException("Char range limits out of order: " + c + ", " + cEnd);
                }
                charSets.add(new CharSet(c, cEnd));
                i += 2;
            } else {
                charSets.add(new CharSet(c));
            }
        }
        return charSets.size() == 1 ? charSets.get(0) : new CharSet(charSets);
    }

    public static Clause str(String str) {
        return new CharSeq(str, /* ignoreCase = */ false);
    }
}
