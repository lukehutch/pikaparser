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
package pikaparser.grammar;

import pikaparser.ast.LabeledClause;
import pikaparser.clause.Clause;
import pikaparser.clause.aux.ASTNodeLabel;

/** A grammar rule. */
public class Rule {
    /** The name of the rule. */
    public String ruleName;

    /** The precedence of the rule, or -1 for no specified precedence. */
    public final int precedence;

    /** The associativity of the rule, or null for no specified associativity. */
    public final Associativity associativity;

    /** The toplevel clause of the rule, and any associated AST node label. */
    public LabeledClause labeledClause;

    /** Associativity (null implies no specified associativity). */
    public static enum Associativity {
        LEFT, RIGHT;
    }

    /** Construct a rule with specified precedence and associativity. */
    public Rule(String ruleName, int precedence, Associativity associativity, Clause clause) {
        this.ruleName = ruleName;
        this.precedence = precedence;
        this.associativity = associativity;

        String astNodeLabel = null;
        var clauseToUse = clause;
        if (clause instanceof ASTNodeLabel) {
            // Transfer ASTNodeLabel.astNodeLabel to astNodeLabel
            astNodeLabel = ((ASTNodeLabel) clause).astNodeLabel;
            // skip over ASTNodeLabel node when adding subClause to subClauses array
            clauseToUse = clause.labeledSubClauses[0].clause;
        }
        this.labeledClause = new LabeledClause(clauseToUse, astNodeLabel);
    }

    /** Construct a rule with no specified precedence or associativity. */
    public Rule(String ruleName, Clause clause) {
        // Use precedence of -1 for rules that only have one precedence
        // (this causes the precedence number not to be shown in the output of toStringWithRuleNames())
        this(ruleName, -1, /* associativity = */ null, clause);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(ruleName);
        buf.append(" <- ");
        buf.append(labeledClause.toString());
        return buf.toString();
    }
}
