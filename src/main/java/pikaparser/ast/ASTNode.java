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
package pikaparser.ast;

import java.util.ArrayList;
import java.util.List;

import pikaparser.clause.Clause;
import pikaparser.memotable.Match;
import pikaparser.parser.utils.TreeUtils;

/** A node in the Abstract Syntax Tree (AST). */
public class ASTNode {
    public final String label;
    public final Clause nodeType;
    public final int startPos;
    public final int len;
    public final String input;
    public final List<ASTNode> children = new ArrayList<>();

    private ASTNode(String label, Clause nodeType, int startPos, int len, String input) {
        this.label = label;
        this.nodeType = nodeType;
        this.startPos = startPos;
        this.len = len;
        this.input = input;
    }

    /** Recursively create an AST from a parse tree. */
    public ASTNode(String label, Match match, String input) {
        this(label, match.memoKey.clause, match.memoKey.startPos, match.len, input);
        addNodesWithASTNodeLabelsRecursive(this, match, input);
    }

    /** Recursively convert a match node to an AST node. */
    private static void addNodesWithASTNodeLabelsRecursive(ASTNode parentASTNode, Match parentMatch, String input) {
        // Recurse to descendants
        var subClauseMatchesToUse = parentMatch.getSubClauseMatches();
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
            var subClauseASTNodeLabel = subClauseMatchEnt.getKey();
            var subClauseMatch = subClauseMatchEnt.getValue();
            if (subClauseASTNodeLabel != null) {
                // Create an AST node for any labeled sub-clauses
                parentASTNode.children.add(new ASTNode(subClauseASTNodeLabel, subClauseMatch, input));
            } else {
                // Do not add an AST node for parse tree nodes that are not labeled; however, still need
                // to recurse to their subclause matches
                addNodesWithASTNodeLabelsRecursive(parentASTNode, subClauseMatch, input);
            }
        }
    }

    public ASTNode getOnlyChild() {
        if (children.size() != 1) {
            throw new IllegalArgumentException("Expected one child, got " + children.size());
        }
        return children.get(0);
    }

    public ASTNode getFirstChild() {
        if (children.size() < 1) {
            throw new IllegalArgumentException("No first child");
        }
        return children.get(0);
    }

    public ASTNode getSecondChild() {
        if (children.size() < 2) {
            throw new IllegalArgumentException("No second child");
        }
        return children.get(1);
    }

    public ASTNode getThirdChild() {
        if (children.size() < 3) {
            throw new IllegalArgumentException("No third child");
        }
        return children.get(2);
    }

    public ASTNode getChild(int i) {
        return children.get(i);
    }

    public String getText() {
        return input.substring(startPos, startPos + len);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        TreeUtils.renderTreeView(this, input, "", true, buf);
        return buf.toString();
    }
}
