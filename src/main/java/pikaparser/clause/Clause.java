//
// This file is part of the pika parser reference implementation:
//
//     https://github.com/lukehutch/pikaparser
//
// The pika parsing algorithm is described in the following paper: 
//
//     Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion
//     and error recovery problems. Luke A. D. Hutchison, May 2020.
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
package pikaparser.clause;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import pikaparser.ast.LabeledClause;
import pikaparser.clause.aux.ASTNodeLabel;
import pikaparser.clause.nonterminal.Seq;
import pikaparser.clause.terminal.Nothing;
import pikaparser.grammar.MetaGrammar;
import pikaparser.grammar.Rule;
import pikaparser.memotable.Match;
import pikaparser.memotable.MemoKey;
import pikaparser.memotable.MemoTable;

/** Abstract superclass of all PEG operators and terminals. */
public abstract class Clause {
    /** Subclauses, paired with their AST node label (if there is one). */
    public LabeledClause[] labeledSubClauses;

    /** Rules this clause is a toplevel clause of (used by {@link #toStringWithRuleNames(}) method). */
    public List<Rule> rules;

    /** The parent clauses of this clause that should be matched in the same start position. */
    public List<Clause> seedParentClauses = new ArrayList<>();

    /** If true, the clause can match while consuming zero characters. */
    public boolean canMatchZeroChars;

    /** Index in the topological sort order of clauses, bottom-up. */
    public int clauseIdx;

    /** The cached result of the {@link #toString()} method. */
    public String toStringCached;

    /** The cached result of the {@link #toStringWithRuleNames()} method. */
    private String toStringWithRuleNameCached;

    // -------------------------------------------------------------------------------------------------------------

    /** Clause constructor. */
    protected Clause(Clause... subClauses) {
        if (subClauses.length > 0 && subClauses[0] instanceof Nothing) {
            // Nothing can't be the first subclause, since we don't trigger upwards expansion of the DP wavefront
            // by seeding the memo table by matching Nothing at every input position, to keep the memo table small
            throw new IllegalArgumentException(
                    Nothing.class.getSimpleName() + " cannot be the first subclause of any clause");
        }
        this.labeledSubClauses = new LabeledClause[subClauses.length];
        for (int i = 0; i < subClauses.length; i++) {
            var subClause = subClauses[i];
            String astNodeLabel = null;
            if (subClause instanceof ASTNodeLabel) {
                // Transfer ASTNodeLabel.astNodeLabel to LabeledClause.astNodeLabel field
                astNodeLabel = ((ASTNodeLabel) subClause).astNodeLabel;
                // skip over ASTNodeLabel node when adding subClause to subClauses array
                subClause = subClause.labeledSubClauses[0].clause;
            }
            this.labeledSubClauses[i] = new LabeledClause(subClause, astNodeLabel);
        }
    }

    /** Register this clause with a rule (used by {@link #toStringWithRuleNames()}). */
    public void registerRule(Rule rule) {
        if (rules == null) {
            rules = new ArrayList<>();
        }
        rules.add(rule);
    }

    /** Unregister this clause from a rule. */
    public void unregisterRule(Rule rule) {
        if (rules != null) {
            rules.remove(rule);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Find which subclauses need to add this clause as a "seed parent clause". Overridden in {@link Seq}. */
    public void addAsSeedParentClause() {
        // Default implementation: all subclauses will seed this parent clause.
        var added = new HashSet<>();
        for (var labeledSubClause : labeledSubClauses) {
            // Don't duplicate seed parent clauses in the subclause
            if (added.add(labeledSubClause.clause)) {
                labeledSubClause.clause.seedParentClauses.add(this);
            }
        }
    }

    /**
     * Sets {@link #canMatchZeroChars} to true if this clause can match zero characters, i.e. always matches at any
     * input position. Called bottom-up. Implemented in subclasses. Returns `true` if {@link #canMatchZeroChars} is
     * flipped from false to true. (Return false for terminals like {@link Nothing} that always return true.)
     */
    public abstract boolean determineWhetherCanMatchZeroChars();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Match a clause by looking up its subclauses in the memotable (in the case of nonterminals), or by looking at
     * the input string (in the case of terminals). Implemented in subclasses.
     */
    public abstract Match match(MemoTable memoTable, MemoKey memoKey, String input);

    // -------------------------------------------------------------------------------------------------------------

    /** Get the names of rules that this clause is the root clause of. */
    public String getRuleNames() {
        return rules == null ? ""
                : String.join(", ",
                        rules.stream().map(rule -> rule.ruleName).sorted().collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        throw new IllegalArgumentException("toString() needs to be overridden in subclasses");
    }

    /** Get the clause as a string, with rule names prepended if the clause is the toplevel clause of a rule. */
    public String toStringWithRuleNames() {
        if (toStringWithRuleNameCached == null) {
            if (rules != null) {
                StringBuilder buf = new StringBuilder();
                // Add rule names
                buf.append(getRuleNames());
                buf.append(" <- ");
                // Add any AST node labels
                var addedASTNodeLabels = false;
                for (int i = 0; i < rules.size(); i++) {
                    var rule = rules.get(i);
                    if (rule.labeledClause.astNodeLabel != null) {
                        buf.append(rule.labeledClause.astNodeLabel + ":");
                        addedASTNodeLabels = true;
                    }
                }
                var addParens = addedASTNodeLabels && MetaGrammar.needToAddParensAroundASTNodeLabel(this);
                if (addParens) {
                    buf.append('(');
                }
                buf.append(toString());
                if (addParens) {
                    buf.append(')');
                }
                toStringWithRuleNameCached = buf.toString();
            } else {
                toStringWithRuleNameCached = toString();
            }
        }
        return toStringWithRuleNameCached;
    }
}
