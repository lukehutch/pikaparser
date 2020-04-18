package pikaparser.parser;

import java.util.ArrayList;
import java.util.List;

import pikaparser.clause.Clause;

public class ASTNode {

    public final String label;
    public final int startPos;
    public final int len;
    public final List<ASTNode> children;
    public final Clause nodeType;
    public final String input;

    public ASTNode(String nodeName, Clause nodeType, int startPos, int len, String input) {
        this.label = nodeName;
        this.nodeType = nodeType;
        this.startPos = startPos;
        this.len = len;
        this.children = new ArrayList<>();
        this.input = input;
    }

    public void addChild(ASTNode child) {
        children.add(child);
    }

    private void getAllDescendantsNamed(String name, List<ASTNode> termsOut) {
        if (label.equals(name)) {
            termsOut.add(this);
        } else {
            for (ASTNode child : children) {
                child.getAllDescendantsNamed(name, termsOut);
            }
        }
    }

    public List<ASTNode> getAllDescendantsNamed(String name) {
        List<ASTNode> terms = new ArrayList<>();
        getAllDescendantsNamed(name, terms);
        return terms;
    }

    public ASTNode getFirstDescendantNamed(String name) {
        if (label.equals(name)) {
            return this;
        } else {
            for (ASTNode child : children) {
                return child.getFirstDescendantNamed(name);
            }
        }
        throw new IllegalArgumentException("Node not found: \"" + name + "\"");
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

    private void renderTreeView(String indentStr, boolean isLastChild, StringBuilder buf) {
        int inpLen = 80;
        String inp = input.substring(startPos, Math.min(input.length(), startPos + Math.min(len, inpLen)));
        if (inp.length() == inpLen) {
            inp += "...";
        }
        inp = inp.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
        buf.append(indentStr + "│  \n");
        buf.append(indentStr + (isLastChild ? "└─ " : "├─ ") + label + " " + startPos + "+" + len + " : \"" + inp
                + "\"\n");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                var subClauseMatch = children.get(i);
                subClauseMatch.renderTreeView(indentStr + (isLastChild ? "   " : "│  "), i == children.size() - 1,
                        buf);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        renderTreeView("", true, buf);
        return buf.toString();
    }
}
