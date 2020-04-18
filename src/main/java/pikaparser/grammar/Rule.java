package pikaparser.grammar;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pikaparser.clause.ASTNodeLabel;
import pikaparser.clause.Clause;
import pikaparser.clause.RuleRef;

public class Rule {
    public String ruleName;
    public final int precedence;
    public final Associativity associativity;
    public Clause clause;
    public String astNodeLabel;

    /** Associativity (or null implies no associativity). */
    public static enum Associativity {
        LEFT, RIGHT;
    }

    public Rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        this.ruleName = ruleName;
        this.precedence = precedence;
        this.associativity = associativity;

        // Lift AST node label from clause into astNodeLabel field in rule
        if (clause instanceof ASTNodeLabel) {
            this.astNodeLabel = ((ASTNodeLabel) clause).astNodeLabel;
            this.clause = clause.subClauses[0];
        } else {
            this.clause = clause;
        }

        // Lift AST node labels from subclauses into subClauseASTNodeLabels array in parent
        liftASTNodeLabels(this.clause);

        // Register rule in clause, for toString()
        this.clause.registerRule(this);
    }

    public Rule(String ruleName, Clause clause) {
        // Use precedence of -1 for rules that only have one precedence
        // (this causes the precedence number not to be shown in the output of toStringWithRuleNames())
        this(ruleName, -1, /* associativity = */ null, clause);
    }

    /**
     * Label subclause positions with the AST node label from any {@link CreateASTNode} nodes in each subclause
     * position.
     */
    private static void liftASTNodeLabels(Clause clause) {
        for (int subClauseIdx = 0; subClauseIdx < clause.subClauses.length; subClauseIdx++) {
            Clause subClause = clause.subClauses[subClauseIdx];
            if (subClause instanceof ASTNodeLabel) {
                // Copy any AST node labels from subclause node to subClauseASTNodeLabels array within the parent
                var subClauseASTNodeLabel = ((ASTNodeLabel) subClause).astNodeLabel;
                if (subClauseASTNodeLabel != null) {
                    if (clause.subClauseASTNodeLabels == null) {
                        // Alloc array for subclause node labels, if not already done
                        clause.subClauseASTNodeLabels = new String[clause.subClauses.length];
                    }
                    if (clause.subClauseASTNodeLabels[subClauseIdx] == null) {
                        // Update subclause label, if it hasn't already been labeled
                        clause.subClauseASTNodeLabels[subClauseIdx] = subClauseASTNodeLabel;
                    }
                } else {
                    throw new IllegalArgumentException(ASTNodeLabel.class.getSimpleName() + " is null");
                }
                // Remove the ASTNodeLabel node 
                clause.subClauses[subClauseIdx] = subClause.subClauses[0];
            }
            // Recurse
            liftASTNodeLabels(subClause);
        }
    }

    /**
     * Recursively call toString() on clause tree, so that toString() values are cached before {@link RuleRef}
     * objects are replaced with direct references, and so that shared subclauses are only matched once.
     */
    private static Clause intern(Clause clause, Map<String, Clause> toStringToClause, Set<Clause> visited) {
        if (visited.add(clause)) {
            // Call toString() on (and intern) subclauses, bottom-up
            for (int i = 0; i < clause.subClauses.length; i++) {
                clause.subClauses[i] = intern(clause.subClauses[i], toStringToClause, visited);
            }
            // Call toString after recursing to child nodes
            var toStr = clause.toString();

            // Intern the clause based on the toString value
            var prevInternedClause = toStringToClause.putIfAbsent(toStr, clause);
            return prevInternedClause != null ? prevInternedClause : clause;
        } else {
            // Avoid infinite loop
            var internedClause = toStringToClause.get(clause.toString());
            return internedClause != null ? internedClause : clause;
        }
    }

    /**
     * Recursively call toString() on the clause tree for this {@link Rule}, so that toString() values are cached
     * before {@link RuleRef} objects are replaced with direct references, and so that shared subclauses are only
     * matched once.
     */
    void intern(Map<String, Clause> toStringToClause, Set<Clause> visited) {
        var internedClause = intern(clause, toStringToClause, visited);
        if (internedClause != clause) {
            // Toplevel clause was already interned as a subclause of another rule
            clause = internedClause;
        }
    }

    /** Resolve {@link RuleRef} clauses to a reference to the named rule. */
    private void resolveRuleRefs(Rule rule, Clause clause, Map<String, Rule> ruleNameToRule, Set<Clause> visited) {
        if (visited.add(clause)) {
            for (int subClauseIdx = 0; subClauseIdx < clause.subClauses.length; subClauseIdx++) {
                Clause subClause = clause.subClauses[subClauseIdx];
                if (subClause instanceof RuleRef) {
                    // Look up rule from name in RuleRef
                    String refdRuleName = ((RuleRef) subClause).refdRuleName;

                    // Set current clause to a direct reference to the referenced rule
                    var refdRule = ruleNameToRule.get(refdRuleName);
                    if (refdRule == null) {
                        throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
                    }
                    clause.subClauses[subClauseIdx] = refdRule.clause;

                    // Copy across AST node label, if any
                    if (refdRule.astNodeLabel != null) {
                        if (clause.subClauseASTNodeLabels == null) {
                            // Alloc array for subclause node labels, if not already done
                            clause.subClauseASTNodeLabels = new String[clause.subClauses.length];
                        }
                        if (clause.subClauseASTNodeLabels[subClauseIdx] == null) {
                            // Update subclause label, if it hasn't already been labeled
                            clause.subClauseASTNodeLabels[subClauseIdx] = refdRule.astNodeLabel;
                        }
                    }
                    // Stop recursing at RuleRef
                } else {
                    // Recurse through subclause tree if subclause was not a RuleRef 
                    resolveRuleRefs(rule, subClause, ruleNameToRule, visited);
                }
            }
        }
    }

    /** Resolve {@link RuleRef} clauses to a reference to the named rule. */
    public void resolveRuleRefs(Map<String, Rule> ruleNameToRule, Set<Clause> visited) {
        if (clause instanceof RuleRef) {
            // Follow a chain of toplevel RuleRef instances
            Set<Clause> chainVisited = new HashSet<>();
            var currClause = clause;
            while (currClause instanceof RuleRef) {
                if (!chainVisited.add(currClause)) {
                    throw new IllegalArgumentException(
                            "Cycle in " + RuleRef.class.getSimpleName() + " references for rule " + ruleName);
                }
                // Look up rule clause from name in RuleRef
                String refdRuleName = ((RuleRef) currClause).refdRuleName;

                // Referenced rule group is the same as the current rule group, and there is only one level
                // of precedence for rule group: R <- R
                if (refdRuleName.equals(ruleName)) {
                    throw new IllegalArgumentException("Rule references only itself: " + ruleName);
                }

                var refdRule = ruleNameToRule.get(refdRuleName);
                if (refdRule == null) {
                    throw new IllegalArgumentException("Unknown rule name: " + refdRuleName);
                }

                // Else Set current clause to the base clause of the referenced rule
                currClause = refdRule.clause;

                // If the referenced rule creates an AST node, add the AST node label to the rule
                if (astNodeLabel == null) {
                    astNodeLabel = refdRule.astNodeLabel;
                }

                // Record rule name in the rule's toplevel clause, for toString
                currClause.registerRule(this);
            }

            // Overwrite RuleRef with direct reference to the named rule 
            clause = currClause;

        } else {
            // Recurse through subclause tree if toplevel clause was not a RuleRef 
            resolveRuleRefs(this, clause, ruleNameToRule, visited);
        }
    }

    /** Check a {@link Clause} tree does not contain any cycles (needed for top-down lexing). */
    private static void checkNoCycles(Clause clause, Set<Clause> discovered, Set<Clause> finished) {
        if (clause instanceof RuleRef) {
            throw new IllegalArgumentException(
                    "There should not be any " + RuleRef.class.getSimpleName() + " nodes left in grammar");
        }
        discovered.add(clause);
        for (var subClause : clause.subClauses) {
            if (discovered.contains(subClause)) {
                throw new IllegalArgumentException("Lex rule's clause tree contains a cycle at " + subClause);
            }
            if (!finished.contains(subClause)) {
                checkNoCycles(subClause, discovered, finished);
            }
        }
        discovered.remove(clause);
        finished.add(clause);
    }

    /** Check a {@link Clause} tree does not contain any cycles (needed for top-down lex). */
    public void checkNoCycles() {
        var discovered = new HashSet<Clause>();
        var finished = new HashSet<Clause>();
        for (var subClause : clause.subClauses) {
            if (!discovered.contains(subClause) && !finished.contains(subClause)) {
                checkNoCycles(subClause, discovered, finished);
            }
        }
    }

    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position. */
    private static void findReachableClauses(Clause clause, Set<Clause> visited, List<Clause> revTopoOrderOut) {
        if (visited.add(clause)) {
            for (var subClause : clause.subClauses) {
                findReachableClauses(subClause, visited, revTopoOrderOut);
            }
            revTopoOrderOut.add(clause);
        }
    }

    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position. */
    public void findReachableClauses(Set<Clause> visited, List<Clause> revTopoOrderOut) {
        findReachableClauses(clause, visited, revTopoOrderOut);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        buf.append(ruleName);
        buf.append(" = ");
        buf.append(clause.toString());
        buf.append(')');
        return buf.toString();
    }
}
