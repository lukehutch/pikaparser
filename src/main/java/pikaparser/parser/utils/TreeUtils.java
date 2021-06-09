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
package pikaparser.parser.utils;

import pikaparser.ast.ASTNode;
import pikaparser.grammar.MetaGrammar;
import pikaparser.memotable.Match;

/** Tree utilities. */
public class TreeUtils {
    /** Render the AST rooted at an {@link ASTNode} into a StringBuffer. */
    public static void renderTreeView(ASTNode astNode, String input, String indentStr, boolean isLastChild,
            StringBuilder buf) {
        int inpLen = 80;
        String inp = input.substring(astNode.startPos,
                Math.min(input.length(), astNode.startPos + Math.min(astNode.len, inpLen)));
        if (inp.length() == inpLen) {
            inp += "...";
        }
        inp = StringUtils.escapeString(inp);

        // Uncomment for double-spaced rows
        // buf.append(indentStr + "│\n");

        buf.append(indentStr + (isLastChild ? "└─" : "├─") + astNode.label + " : " + astNode.startPos + "+"
                + astNode.len + " : \"" + inp + "\"\n");
        if (astNode.children != null) {
            for (int i = 0; i < astNode.children.size(); i++) {
                renderTreeView(astNode.children.get(i), input, indentStr + (isLastChild ? "  " : "│ "),
                        i == astNode.children.size() - 1, buf);
            }
        }
    }

    /** Render a parse tree rooted at a {@link Match} node into a StringBuffer. */
    public static void renderTreeView(Match match, String astNodeLabel, String input, String indentStr,
            boolean isLastChild, StringBuilder buf) {
        int inpLen = 80;
        String inp = input.substring(match.memoKey.startPos,
                Math.min(input.length(), match.memoKey.startPos + Math.min(match.len, inpLen)));
        if (inp.length() == inpLen) {
            inp += "...";
        }
        inp = StringUtils.escapeString(inp);

        // Uncomment for double-spaced rows
        // buf.append(indentStr + "│\n");

        var astNodeLabelNeedsParens = MetaGrammar.needToAddParensAroundASTNodeLabel(match.memoKey.clause);
        buf.append(indentStr);
        buf.append(isLastChild ? "└─" : "├─");
        var ruleNames = match.memoKey.clause.getRuleNames();
        if (!ruleNames.isEmpty()) {
            buf.append(ruleNames + " <- ");
        }
        if (astNodeLabel != null) {
            buf.append(astNodeLabel);
            buf.append(':');
            if (astNodeLabelNeedsParens) {
                buf.append('(');
            }
        }
        var toStr = match.memoKey.clause.toString();
        buf.append(toStr);
        if (astNodeLabel != null && astNodeLabelNeedsParens) {
            buf.append(')');
        }
        buf.append(" : ");
        buf.append(match.memoKey.startPos);
        buf.append('+');
        buf.append(match.len);
        buf.append(" : \"");
        buf.append(inp);
        buf.append("\"\n");

        // Recurse to descendants
        var subClauseMatchesToUse = match.getSubClauseMatches();
        for (int subClauseMatchIdx = 0; subClauseMatchIdx < subClauseMatchesToUse.size(); subClauseMatchIdx++) {
            var subClauseMatchEnt = subClauseMatchesToUse.get(subClauseMatchIdx);
            var subClauseASTNodeLabel = subClauseMatchEnt.getKey();
            var subClauseMatch = subClauseMatchEnt.getValue();
            renderTreeView(subClauseMatch, subClauseASTNodeLabel, input, indentStr + (isLastChild ? "  " : "│ "),
                    subClauseMatchIdx == subClauseMatchesToUse.size() - 1, buf);
        }
    }

    /** Print the parse tree rooted at a {@link Match} node to stdout. */
    public static void printTreeView(Match match, String input) {
        var buf = new StringBuilder();
        renderTreeView(match, null, input, "", true, buf);
        System.out.println(buf.toString());
    }
}
