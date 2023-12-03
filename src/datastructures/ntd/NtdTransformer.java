package datastructures.ntd;

import benchmarking.calculators.IntBoxer;
import datastructures.graphs.WeightedGraph;
import jdrasil.algorithms.SmartDecomposer;
import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import jdrasil.graph.Bag;
import jdrasil.graph.TreeDecomposition;
import logging.Logger;

import java.util.*;

import static datastructures.ntd.Ntd.NodeType.*;

public class NtdTransformer {
    public static Ntd getNtdFromWeightedGraph(WeightedGraph weightedGraph) throws TransformationFailureException {

        //jd_td erstellen
        TreeDecomposition<Integer> jd_td = getJdTdFromWeightedGraph(weightedGraph);

        //jd_ntd erstellen
        NiceTreeDecomposition<Integer> jd_ntd = getJdNtdFromJdTd(jd_td);

        return getNtdFromJdNtd(jd_ntd);
    }

    public static TreeDecomposition<Integer> getJdTdFromWeightedGraph(WeightedGraph weightedGraph) {
        try {
            return new SmartDecomposer<>(weightedGraph.jd_graph).call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static NiceTreeDecomposition<Integer> getJdNtdFromJdTd(TreeDecomposition<Integer> jd_td) {
        NiceTreeDecomposition<Integer> niceTreeDecomposition = new NiceTreeDecomposition<>(jd_td, true);
        niceTreeDecomposition.getProcessedTreeDecomposition();
        return niceTreeDecomposition;
    }

    public static Ntd getNtdFromJdNtd(NiceTreeDecomposition<Integer> jd_ntd) throws TransformationFailureException {
        return (getNtdFromJdNtd(jd_ntd, new Ntd()));
    }

    private static Ntd getNtdFromJdNtd(NiceTreeDecomposition<Integer> jd_ntd, Ntd ntd) throws TransformationFailureException {

        TreeDecomposition<Integer> jd_td = jd_ntd.getProcessedTreeDecomposition();

        //Daten der Jdrasil td auslesen
        int numberOfBags = jd_td.getNumberOfBags();
        int numberOfVertices = jd_td.n;

        ntd.tw = jd_td.getWidth();
        ntd.numberOfNodes = numberOfBags;
        ntd.numberOfVertices = numberOfVertices;
        ntd.numberOfEdges = jd_td.getGraph().getNumberOfEdges();
        ntd.numberOfJoinNodes = 0; //beim Einlesen der Bags/Nodes erhöhen

        //Knoten erstellen (damit wir nicht die gleiche Referenz wie jd haben)

        //leere Nodes erstellen
        ArrayList<NtdNode> nodes = new ArrayList<>();
        for (int i = 0; i < numberOfBags; i++) {
            nodes.add(new NtdNode());
        }

        //Bags zuweisen
        for (Bag<Integer> bag : jd_td.getTree()) {
            nodes.get(bag.id - 1).bag = new HashSet<>(bag.vertices);
        }

        //root zuweisen
        ntd.root = nodes.get(jd_ntd.getRoot().id - 1);

        //Nodes Kinder, specialvertex, nodetype zuweisen
        Stack<Bag<Integer>> jd_bag_stack = new Stack<>();
        Set<Bag<Integer>> jd_bags_visited = new HashSet<>();
        jd_bag_stack.push(jd_ntd.getRoot());

        int checkSum = 0; //überprüfen, ob die Anzahl der Nodes in meiner Implementierung die gleiche wie in JDrasil ist

        while (!jd_bag_stack.empty()) {
            Bag<Integer> jd_currentBag = jd_bag_stack.pop();
            jd_bags_visited.add(jd_currentBag);
            ArrayList<Bag<Integer>> jd_currentNeighbourhood = new ArrayList<>(jd_td.getNeighborhood(jd_currentBag));
            jd_currentNeighbourhood.removeAll(jd_bags_visited);

            NtdNode currentNode = nodes.get(jd_currentBag.id - 1);

            switch (jd_ntd.bagType.get(jd_currentBag)) {
                case LEAF:
                    currentNode.nodeType = LEAF;
                    checkSum++;
                    break;
                case INTRODUCE:
                    currentNode.nodeType = INTRODUCE;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    currentNode.specialVertex = jd_ntd.specialVertex.get(jd_currentBag);
                    checkSum++;
                    break;
                case FORGET:
                    currentNode.nodeType = Ntd.NodeType.FORGET;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    currentNode.specialVertex = jd_ntd.specialVertex.get(jd_currentBag);
                    checkSum++;
                    break;
                case JOIN:
                    currentNode.nodeType = Ntd.NodeType.JOIN;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    currentNode.secondChild = nodes.get(jd_currentNeighbourhood.get(1).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    jd_bag_stack.push(jd_currentNeighbourhood.get(1));
                    ntd.numberOfJoinNodes++;
                    checkSum++;
                    break;
                case EDGE:
                    currentNode.nodeType = Ntd.NodeType.EDGE;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    currentNode.specialVertex = jd_ntd.specialVertex.get(jd_currentBag);
                    currentNode.secondSpecialVertex = jd_ntd.secondSpecialVertex.get(jd_currentBag);
                    checkSum++;
                    break;
            }
        }
        if (checkSum != numberOfBags) {
            Logger.critical("ERROR: Die NTD wurde nicht korrekt übertragen\n");
            throw new TransformationFailureException("ERROR: Die NTD wurde nicht korrekt übertragen");
        }
        return ntd;
    }

    /**
     * Gibt eine *neue* Baumzerlegung mit newRoot als Wurzel zurück. Die originale Baumzerlegung bleibt unverändert.
     */
    public static Ntd copyNtd(Ntd originalNtd, NtdNode newRoot) throws TransformationFailureException {
        //falls die newRoot weder die originale Wurzel, noch ein blatt ist -> abbruch
        if (!((newRoot.nodeType == FORGET && newRoot.bag.size() == 0) || (newRoot.nodeType == LEAF))) {
            Logger.critical("changeRoot: ungültige Wurzel");
            return null;
        }

        //die ganzen Werte übernehmen wie in getNtdFromJdNtd
        Ntd newNtd = new Ntd();

        //Daten der Jdrasil td auslesen

        newNtd.tw = originalNtd.tw;
        newNtd.numberOfNodes = originalNtd.numberOfNodes;
        newNtd.numberOfVertices = originalNtd.numberOfVertices;
        newNtd.numberOfEdges = originalNtd.numberOfEdges;
        newNtd.numberOfJoinNodes = originalNtd.numberOfJoinNodes;

        //parent Beziehungen setzen und nach der newRoot suchen
        boolean foundNewRoot = originalNtd.root == newRoot;

        HashMap<NtdNode, NtdNode> parentNodeMap = new HashMap<>();
        Stack<NtdNode> nodeStack = new Stack<>();
        nodeStack.push(originalNtd.getRoot());
        parentNodeMap.put(originalNtd.getRoot().getFirstChild(), originalNtd.getRoot());

        while (!nodeStack.isEmpty()) {
            NtdNode currentNode = nodeStack.pop();

            if (currentNode.getNodeType() == Ntd.NodeType.LEAF) {
                if(currentNode == newRoot) foundNewRoot = true;
            } else if (currentNode.getNodeType() == Ntd.NodeType.JOIN) {
                nodeStack.push(currentNode.getFirstChild());
                nodeStack.push(currentNode.getSecondChild());
                parentNodeMap.put(currentNode.getFirstChild(), currentNode);
                parentNodeMap.put(currentNode.getSecondChild(), currentNode);
            } else {
                nodeStack.push(currentNode.getFirstChild());
                parentNodeMap.put(currentNode.getFirstChild(), currentNode);
            }
        }
        if(!foundNewRoot) throw new IllegalArgumentException("Die neue Wurzel newRoot muss in der Ntd originalNtd vorkommen");

        ArrayList<NtdNode> edgeBuffer = new ArrayList<>();

        IntBoxer checkSum = new IntBoxer(); //debug: überprüfen, ob die Anzahl der Nodes gleich ist
        checkSum.set(0);

        if (newRoot.nodeType == LEAF) {
            newNtd.root = recursiveUpwardsGenerator(newRoot, parentNodeMap, edgeBuffer, checkSum);
            if(!edgeBuffer.isEmpty()) throw new RuntimeException("recursiveUpwardsGenerator: edgeBuffer ist nicht leer");

        } else { // newRoot ist die originale Wurzel
            newNtd.root = recursiveDownwardsGenerator(newRoot, checkSum);
        }

        if (checkSum.get() != newNtd.numberOfNodes) {
            Logger.critical("ERROR: Die NTD wurde nicht korrekt übertragen\n");
            throw new TransformationFailureException("ERROR: Die NTD wurde nicht korrekt übertragen");
        }

        //falls newRoot nicht in der NTD ist (wird im Wurzel wechsel Algo festgestellt)

        //upwards copy wie im simulator
        //downwards einfach 1:1 kopieren
        return newNtd;
    }

    private static NtdNode recursiveDownwardsGenerator(NtdNode originalCurrentNode, IntBoxer checksum) {
        NtdNode newCurrentNode = new NtdNode();
        checksum.increase();

        //bag erstellen & zuweisen
        newCurrentNode.bag = new HashSet<>(originalCurrentNode.bag);
        //nodetype
        newCurrentNode.nodeType = originalCurrentNode.nodeType;

        //kinder, specialVertex
        switch (originalCurrentNode.nodeType) {
            case LEAF:
                break;
            case JOIN:
                newCurrentNode.firstChild = recursiveDownwardsGenerator(originalCurrentNode.firstChild, checksum);
                newCurrentNode.secondChild = recursiveDownwardsGenerator(originalCurrentNode.secondChild, checksum);
                break;
            case EDGE:
                newCurrentNode.firstChild = recursiveDownwardsGenerator(originalCurrentNode.firstChild, checksum);
                newCurrentNode.specialVertex = originalCurrentNode.getSpecialVertex();
                newCurrentNode.secondSpecialVertex = originalCurrentNode.getSecondSpecialVertex();
                break;
            case FORGET,INTRODUCE:
                newCurrentNode.firstChild = recursiveDownwardsGenerator(originalCurrentNode.firstChild, checksum);
                newCurrentNode.specialVertex = originalCurrentNode.getSpecialVertex();
                break;
        }
        return newCurrentNode;
    }

    private static NtdNode recursiveUpwardsGenerator(NtdNode originalCurrentNode, HashMap<NtdNode, NtdNode> parentNodeMap, ArrayList<NtdNode> edgeBuffer, IntBoxer checksum) {
        NtdNode newCurrentNode = new NtdNode();
        checksum.increase();

        //bag erstellen & zuweisen
        newCurrentNode.bag = new HashSet<>(originalCurrentNode.bag);

        //kinder, specialVertex
        NtdNode parentNode = parentNodeMap.get(originalCurrentNode);
        if (parentNode == null) { //originale Wurzel --> LEAF}
            newCurrentNode.nodeType = LEAF;
            return newCurrentNode;
        }
            switch (parentNode.nodeType) {
                case JOIN:
                    newCurrentNode.nodeType = JOIN;
                    newCurrentNode.firstChild = parentNode.firstChild == originalCurrentNode ?
                            recursiveDownwardsGenerator(parentNode.secondChild, checksum) :
                            recursiveDownwardsGenerator(parentNode.firstChild, checksum);
                    newCurrentNode.secondChild = recursiveUpwardsGenerator(parentNode, parentNodeMap, edgeBuffer, checksum);
                    break;
                case EDGE:
                    newCurrentNode.nodeType = INTRODUCE;
                    NtdNode tmpParentNode = parentNode;
                    while (tmpParentNode.nodeType == EDGE) {
                        edgeBuffer.add(tmpParentNode);
                        tmpParentNode = parentNodeMap.get(tmpParentNode);
                    }
                    //tmpParentNode ist jetzt forget
                    //parentNode ist immer noch edge

                    newCurrentNode.firstChild = recursiveUpwardsGenerator(tmpParentNode, parentNodeMap, edgeBuffer, checksum);
                    newCurrentNode.specialVertex = tmpParentNode.specialVertex;
                    break;
                case FORGET:
                    newCurrentNode.nodeType = INTRODUCE;
                    newCurrentNode.firstChild = recursiveUpwardsGenerator(parentNode, parentNodeMap, edgeBuffer, checksum);
                    newCurrentNode.specialVertex = parentNode.specialVertex;
                    break;
                case INTRODUCE:
                    newCurrentNode.nodeType = FORGET;
                    newCurrentNode.specialVertex = parentNode.specialVertex;
                    NtdNode childAfterEdges = recursiveUpwardsGenerator(parentNode, parentNodeMap, edgeBuffer, checksum);

                    NtdNode tmpCurrentNode = newCurrentNode;
                    for (int i = 0; i < edgeBuffer.size(); i++) {
                        if (edgeBuffer.get(i).specialVertex.equals(newCurrentNode.specialVertex) ||
                                edgeBuffer.get(i).secondSpecialVertex.equals(newCurrentNode.specialVertex)) {
                            NtdNode newEdge = new NtdNode();
                            checksum.increase();
                            newEdge.nodeType = EDGE;
                            newEdge.bag = new HashSet<>(parentNode.bag);
                            newEdge.specialVertex = edgeBuffer.get(i).specialVertex;
                            newEdge.secondSpecialVertex = edgeBuffer.get(i).secondSpecialVertex;
                            tmpCurrentNode.firstChild = newEdge;
                            tmpCurrentNode = newEdge;
                            edgeBuffer.remove(i--);
                        }
                    }
                    tmpCurrentNode.firstChild = childAfterEdges;
                    break;
            }
            return newCurrentNode;
        }

    private static Set<Integer> getBagCopy(Set<Integer> bag) {
        return new HashSet<>(bag);
    }


}
