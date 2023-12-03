package rootChoosing;

import logging.Logger;
import datastructures.ntd.Edge;
import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;

import java.math.BigInteger;
import java.util.*;

public class Simulator {

    private Ntd ntd;

    private ArrayList<Edge> edgeBuffer;

    public static final int[] bellNrs = new int[]{1, 1, 2, 5, 15, 52, 203, 877, 4140, 21147, 115975, 678570, 4213597, 27644437, 190899322, 1382958545};

    private BigInteger edge_timeSum;
    private BigInteger forget_timeSum;
    private BigInteger join_timeSum;

    private ArrayList<BigInteger[]> timeSumsArray;

    //debug
    private int join_checksum;
    private int node_checksum;


    HashMap<NtdNode, NtdNode> parentNode = new HashMap<>();

    public Simulator(Ntd ntd) {
        this.ntd = ntd;
    }

    public static Map.Entry<NtdNode, BigInteger> findMin(HashMap<NtdNode, BigInteger> map) {
        Map.Entry<NtdNode, BigInteger> min = new AbstractMap.SimpleEntry<>(null, null);
        for (Map.Entry<NtdNode, BigInteger> entry : map.entrySet()) {
            if(min.getValue() == null || entry.getValue().compareTo(min.getValue()) < 0){
                min = entry;
            }
        }
        return min;
    }

    public Map.Entry<NtdNode,BigInteger> estimateBestRoot() {
        HashMap<NtdNode, BigInteger> potentialRootMap = estimateAllRoots();
        return findMin(potentialRootMap);
    }

    public HashMap<NtdNode, BigInteger> estimateAllRoots() {
        timeSumsArray = new ArrayList<>();
        ArrayList<NtdNode> potentialRoots = findPotentialRoots();

        HashMap<NtdNode, BigInteger> potentialRootMap = new HashMap<>();

        for (NtdNode potentialRoot : potentialRoots) {
            BigInteger currentTime = getEstimatedTime(potentialRoot);
            if (join_checksum != 0 || node_checksum != 0) {
                Logger.critical("join_checksum: " + join_checksum + ", node_checksum: " + node_checksum);
                throw new RuntimeException("join checksum != 0 || node_checksum != 0");
            }
            potentialRootMap.put(potentialRoot, currentTime);
        }

        //timeSumsArrayAusgabe
        if (Logger.logSimulating) {
            timeSumsArray.sort(Comparator.comparing(o -> o[0]));
            Logger.simulate("Geschätzte LZ je Wurzel: \n");
            Logger.simulate("total_timeSum, join_timeSum, forget_timeSum, edge_timeSum\n");
            for (BigInteger[] timeSums : timeSumsArray) {
                Logger.simulate(String.format("%d, %d, %d, %d\n", timeSums[0], timeSums[1], timeSums[2], timeSums[3]));
            }
            Logger.simulate("Geschätzte LZ je Wurzel (GEKÜRZT nach der 5% Regel): \n");
            Logger.simulate("total_timeSum, join_timeSum, forget_timeSum, edge_timeSum\n");
            BigInteger currentMax = BigInteger.valueOf(-99999999);
            for (BigInteger[] timeSums : timeSumsArray) {
                if(currentMax.subtract(timeSums[0]).abs().compareTo(currentMax.divide(BigInteger.valueOf(20))) <= 0)
                    continue;
                currentMax = timeSums[0];
                Logger.simulate(String.format("%d, %d, %d, %d\n", timeSums[0], timeSums[1], timeSums[2], timeSums[3]));
            }
        }

        return potentialRootMap;
    }

    public BigInteger getEstimatedTime(NtdNode potentialRoot) {
//        Logger.status("estimatedTime [total, join, forget, edge]\n");
        BigInteger total_timeSum;
        edge_timeSum =BigInteger.valueOf(0);
        forget_timeSum = BigInteger.valueOf(0);
        join_timeSum = BigInteger.valueOf(0);
        join_checksum = ntd.numberOfJoinNodes;
        node_checksum = ntd.numberOfNodes;
        edgeBuffer = new ArrayList<>();

        if (potentialRoot.getNodeType() == Ntd.NodeType.LEAF) {
            recursiveUpwardsSimulator(potentialRoot, null);
            if (!edgeBuffer.isEmpty())
                throw new RuntimeException("recursiveUpwardsSimulator: edgeBuffer ist nicht leer");
        } else if (potentialRoot.getNodeType() == Ntd.NodeType.FORGET && potentialRoot.bag.size() == 0) {
            recursiveDownwardsSimulator(potentialRoot);
        } else {
            //das sollte nicht passieren können
            throw new RuntimeException("getEstimatedTime: potential Root ist weder LEAF noch originale ROOT");
        }
        total_timeSum = edge_timeSum.add(forget_timeSum).add(join_timeSum);
//        if (Logger.logTimes)
        timeSumsArray.add(new BigInteger[]{total_timeSum, join_timeSum, forget_timeSum, edge_timeSum});
        return total_timeSum;
    }

    private Tupel recursiveUpwardsSimulator(NtdNode currentNode, NtdNode predecessorNode) {
        node_checksum--;
        switch (currentNode.getNodeType()) {
            case INTRODUCE -> {
                Tupel first = recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);

                //EDGE pro kante im speicher mit dem knoten;
                boolean alreadySimulatedAnEdge = false;
                Ntd.NodeType child_node_type = null;
                for (int i = 0; i < edgeBuffer.size(); i++) {
                    if (currentNode.getSpecialVertex() == edgeBuffer.get(i).getU() || currentNode.getSpecialVertex() == edgeBuffer.get(i).getV()) {

                        //vorherigen Node Typ bestimmen
                        if (alreadySimulatedAnEdge) {
                            child_node_type = Ntd.NodeType.EDGE;
                        } else {
                            alreadySimulatedAnEdge = true;

                            switch (parentNode.get(currentNode).getNodeType()) {
                                case JOIN -> child_node_type = Ntd.NodeType.JOIN;
                                case FORGET, EDGE -> child_node_type = Ntd.NodeType.INTRODUCE;
                                case INTRODUCE -> child_node_type = Ntd.NodeType.FORGET;
                            }
                        }
                        handleEdge(first, child_node_type, currentNode.bag.size());
                        edgeBuffer.remove(i--);
                    }
                }

                //FORGET mit bag size -1
                handleForget(first, currentNode.bag.size() - 1);
                return first;
            }
            case FORGET -> {
                if (currentNode.bag.size() == 0) {
                    //LEAF
                    //INTRODUCE mit bag size +1 (momentan also nix)
                    return handleLeaf();
                } else {
                    //INTRODUCE mit bag size +1 (momentan also nix)
                    return recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
                }
            }
            case JOIN -> {
                //andere seite downwards
                NtdNode otherChild = currentNode.getFirstChild() == predecessorNode ? currentNode.getSecondChild() : currentNode.getFirstChild();
                Tupel first = recursiveDownwardsSimulator(otherChild);
                //nach oben weiterhin upwards
                Tupel second = recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);

                //mit den Ergebnissen JOIN

                handleJoin(first, second, currentNode.bag.size());
                return first;
            }
            case EDGE -> {
                //EDGE speichern
                edgeBuffer.add(new Edge(currentNode.getSpecialVertex(), currentNode.getSecondSpecialVertex()));
                return recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
            }
            case LEAF -> {
                //hier kommen wir nur hin, wenn in der LEAF node gestartet wird
                //Zeit ist unter Simulationsannahmen so gering, dass hier nicht gerechnet werden muss

                return recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
            }

        }
        throw new RuntimeException("recursiveUpwardsSimulator: Node art unbekannt"); //das hier sollte nicht passieren können
    }

    private Tupel recursiveDownwardsSimulator(NtdNode currentNode) {
        node_checksum--;
        switch (currentNode.getNodeType()) {
            case LEAF -> {
                return handleLeaf();
            }
            case INTRODUCE -> {
                return recursiveDownwardsSimulator(currentNode.getFirstChild());
            }
            case FORGET -> {
                Tupel first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                handleForget(first, currentNode.bag.size());
                return first;
            }
            case JOIN -> {
                Tupel first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                Tupel second = recursiveDownwardsSimulator(currentNode.getSecondChild());
                handleJoin(first, second, currentNode.bag.size());
                return first;
            }
            case EDGE -> {
                Tupel first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                handleEdge(first, currentNode.getFirstChild().getNodeType(), currentNode.bag.size());
                return first;
            }
        }
        throw new RuntimeException("recursiveDownwardsSimulator: Node art unbekannt"); //das hier sollte nicht passieren können
    }

    private double func(double x, double exponent, double coefficient, double intercept, Double MIN, Double MAX) {
        double value = Math.pow(x, exponent) * coefficient + intercept;
        if (MIN != null) {
            value = Math.max(value, MIN);
        }
        if (MAX != null) {
            value = Math.min(value, MAX);
        }
        return value;
    }

    private BigInteger bigFunc(double x,double exponent, double coefficient, double intercept, Double MIN, Double MAX) {
        return  BigInteger.valueOf((long) func(x, exponent,  coefficient,  intercept, MIN, MAX));
    }

    private Tupel handleLeaf() {
        return new Tupel(1,1);
    }

    private void handleEdge(Tupel first, Ntd.NodeType child_node_type, int bag_size) {
        if (first.partition_count * 2 > bellNrs[bag_size]) { //hitCap = true
            //timeSum
            edge_timeSum = edge_timeSum.add(bigFunc(first.partition_count, 1.6708186839914323,5.397959173199246e-06,0,null,null));
            //partition count !!bei edge arbeiten wir mit dem multiplikator!!
            first.partition_count *= func(bag_size, 1, 0.0005060129129482801, 1.0135640678047544, (double) 1, (double) 2);

        } else { //hitCap = false
            //timeSum
            edge_timeSum = edge_timeSum.add(bigFunc(first.partition_count, 1.1530948035397222,0.00696350877433863,0,null,null));
            //partition count !!bei edge arbeiten wir mit dem multiplikator!!
            first.partition_count *= func(bag_size, 1, -0.06210307137158804, 2.186107493659719, (double) 1, (double) 2);
        }
        //cap
        first.partition_count = Math.min(bellNrs[bag_size], first.partition_count);
    }

    private void handleForget(Tupel first, int bag_size) {
        //timeSum
        forget_timeSum = forget_timeSum.add(bigFunc(first.partition_count,1.5708004606663157,6.294629337629676e-06,0,null,null));

        //partitionCount  !!wir können das hier auch ohne normierung machen, weil die funktion linear ist und sich somit die normierungen aufheben
        first.partition_count = func(first.partition_count, 1, 0.5173298942960608, 0, (double) 1, (double) bellNrs[bag_size]);

    }

    private void handleJoin(Tupel first, Tupel second, int bag_size) {
        join_checksum--;

        double multiplied_partition_count = first.partition_count * second.partition_count;
        double normed_multiplied_partition_count = multiplied_partition_count / bellNrs[bag_size];


        if (multiplied_partition_count > bellNrs[bag_size]) { //hitCap

             //timeSum
            join_timeSum = join_timeSum.add(bigFunc(multiplied_partition_count,1,0.2689853334730426,0,null,null));

            //partition Count !!bei join arbeiten wir mit der normierung, da die funktion nicht linear ist!!
            first.partition_count = bellNrs[bag_size] * func(normed_multiplied_partition_count, 0.06180614754764031, 0.6739753642766005, 0, null, (double) 1);

        } else { //not hitCap

             //timeSum
            join_timeSum = join_timeSum.add(bigFunc(multiplied_partition_count,1,0.0038980009892205614,0,null,null));

            //hier können wir die normierung rauslassen, da die Funktion linear ist
            first.partition_count = func(multiplied_partition_count, 1, 0.9983610626349286, 0, (double) 1, null);

        }

    }


    private ArrayList<NtdNode> findPotentialRoots() {
        ArrayList<NtdNode> potentialRoots = new ArrayList<>();

        Stack<NtdNode> nodeStack = new Stack<>();

        potentialRoots.add(ntd.getRoot());
        nodeStack.push(ntd.getRoot());
        parentNode.put(ntd.getRoot().getFirstChild(), ntd.getRoot());

        while (!nodeStack.isEmpty()) {
            NtdNode currentNode = nodeStack.pop();

            if (currentNode.getNodeType() == Ntd.NodeType.LEAF) {
                potentialRoots.add(currentNode);
            } else if (currentNode.getNodeType() == Ntd.NodeType.JOIN) {
                nodeStack.push(currentNode.getFirstChild());
                nodeStack.push(currentNode.getSecondChild());
                parentNode.put(currentNode.getFirstChild(), currentNode);
                parentNode.put(currentNode.getSecondChild(), currentNode);
            } else {
                nodeStack.push(currentNode.getFirstChild());
                parentNode.put(currentNode.getFirstChild(), currentNode);
            }
        }
        return potentialRoots;
    }
}

class Tupel { //könnte durch int[] ersetzt werden, ist so aber intuitiver für mich
    double partition_count, forest_count;

    public Tupel(double partition_count, double forest_count) {
        this.partition_count = partition_count;
        this.forest_count = forest_count;
    }
}
