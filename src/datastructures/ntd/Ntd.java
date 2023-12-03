package datastructures.ntd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import static datastructures.ntd.Ntd.NodeType.FORGET;
import static datastructures.ntd.Ntd.NodeType.INTRODUCE;

public class Ntd{

    public NtdNode root;

    public Ntd() {
    }

    //public zum debuggen
    public int tw;
    public int numberOfNodes;
    public int numberOfJoinNodes;

    public int numberOfVertices; //Knoten Anzahl des ursprünglichen Graphs
    public int numberOfEdges; //Kanten Anzahl des ursprünglichen Graphs

    public HashMap<Integer, Integer> treeIndex;

    public enum NodeType {
        LEAF,
        INTRODUCE,
        FORGET,
        JOIN,
        EDGE
    }

    public NtdNode getRoot() {
        return root;
    }

    public void computeTreeIndex() {
        treeIndex = new HashMap<>(tw+1);

        // verfügbare indices
        Stack<Integer> indices = new Stack<>();
        int i = tw+1; while (i --> 0) indices.push(i);

        Stack<NtdNode> stack = new Stack<>();
        Set<NtdNode> finished = new HashSet<>(numberOfNodes);
        stack.push(root);

        while (!stack.isEmpty()) {
            NtdNode v = stack.peek();
            if (finished.contains(v)) {
                stack.pop();
                if (v.nodeType == FORGET) {
                    indices.push(treeIndex.get(v.specialVertex));
                } else if (v.nodeType == INTRODUCE) {
                    indices.pop();
                }
                continue;
            }
            finished.add(v);

            if (v.nodeType == FORGET) {
                treeIndex.put(v.specialVertex, indices.pop());
            } else if (v.nodeType == INTRODUCE) {
                indices.push(treeIndex.get(v.specialVertex));
            }

            if (v.getFirstChild() != null) {
                stack.push(v.getFirstChild());
                if (v.getSecondChild() != null)
                    stack.push(v.getSecondChild());
            }
        }
    }
}
