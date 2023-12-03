package logging;

import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;

import java.util.HashMap;

public class InfoTree {

    public HashMap<NtdNode, InfoTreeNode> infoTreeMap;

    public InfoTreeNode infoTreeRoot;

    private int maxId = 1;

    public InfoTree(Ntd ntd) {
        infoTreeMap = new HashMap<>();
        infoTreeRoot = recursiveInfoTreeGeneration(ntd.getRoot());
    }

    private InfoTreeNode recursiveInfoTreeGeneration(NtdNode currentNtdNode) {
        if (currentNtdNode.getNodeType() == Ntd.NodeType.JOIN) {
            InfoTreeNode currentInfoNode = new InfoTreeNode(maxId++);
            infoTreeMap.put(currentNtdNode, currentInfoNode);
            currentInfoNode.firstChild = recursiveInfoTreeGeneration(currentNtdNode.getFirstChild());
            currentInfoNode.secondChild = recursiveInfoTreeGeneration(currentNtdNode.getSecondChild());
            return currentInfoNode;
        }

        if (currentNtdNode.getFirstChild() != null) {
            return recursiveInfoTreeGeneration(currentNtdNode.getFirstChild());
        } else {
            return null;
        }
    }

    public String toTikz() {
        StringBuilder nodeString = new StringBuilder();
        StringBuilder edgeString = new StringBuilder();
        nodeString.append("\\tikz\\graph[binary tree layout] {\n");
        recursiveTikzGenerator(nodeString, edgeString, infoTreeRoot);
        return nodeString.toString() + edgeString + "\n};";
    }

    private void recursiveTikzGenerator(StringBuilder nodeString, StringBuilder edgeString, InfoTreeNode infoNode) {
        nodeString.append(String.format("%d/ %s;\n", infoNode.id, infoNode.information));
        if (infoNode.firstChild != null) {
            edgeString.append(infoNode.id).append(" -- ").append(infoNode.firstChild.id).append(";\n");
            recursiveTikzGenerator(nodeString,edgeString,infoNode.firstChild);
        }
        if (infoNode.secondChild != null) {
            edgeString.append(infoNode.id).append(" -- ").append(infoNode.secondChild.id).append(";\n");
            recursiveTikzGenerator(nodeString,edgeString,infoNode.secondChild);
        }
    }

    public static class InfoTreeNode {
        public String information;
        public InfoTreeNode firstChild;
        public InfoTreeNode secondChild;
        public int id;
        public long elapsedMS;
        public int bagSize;

        public int introducedVerticesCount;
        public int introducedEdgesCount;
        public int forgottenVerticesCount;

        public InfoTreeNode(int id) {
            this.id = id;
            introducedEdgesCount = 0;
            introducedVerticesCount = 0;
            forgottenVerticesCount = 0;
        }
    }
}
