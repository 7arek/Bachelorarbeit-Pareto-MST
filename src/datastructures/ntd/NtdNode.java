package datastructures.ntd;

import java.util.Set;

public class NtdNode {

    public NtdNode(NtdNode firstChild, NtdNode secondChild, Set<Integer> bag, Integer specialVertex, Integer secondSpecialVertex, Ntd.NodeType nodeType) {
        this.firstChild = firstChild;
        this.secondChild = secondChild;
        this.bag = bag;
        this.specialVertex = specialVertex;
        this.secondSpecialVertex = secondSpecialVertex;
        this.nodeType = nodeType;
    }

    public NtdNode() {
    }

    public NtdNode firstChild;
    public NtdNode secondChild; //nur für join Nodes

    public Set<Integer> bag; //wird eigentlich nur zum Printen und Debuggen benötigt

    public Integer specialVertex; //für introduce, forget, edge Nodes

    public Integer secondSpecialVertex; // für edge Nodes

    public Ntd.NodeType nodeType;

    @Override
    public String toString() { //fürs debuggen
        StringBuilder sb = new StringBuilder();
        for (int v : bag) {
            sb.append(" ").append(v);
        }
        sb.append(" ").append(nodeType);
        if(specialVertex != null) sb.append(" ").append(specialVertex);
        if(secondSpecialVertex != null) sb.append(" ").append(secondSpecialVertex);
        return sb.toString();
    }

    public NtdNode getFirstChild() {
        return firstChild;
    }

    public NtdNode getSecondChild() {
        return secondChild;
    }

    public Ntd.NodeType getNodeType() {
        return nodeType;
    }

    public int getSpecialVertex() {
        return specialVertex;
    }

    public int getSecondSpecialVertex() {
        return secondSpecialVertex;
    }
}
