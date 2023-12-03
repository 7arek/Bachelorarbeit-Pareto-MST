package dynamicProgramming;

import benchmarking.Benchmark;
import benchmarking.RuntimeHeapWatcher;
import dynamicProgramming.outsourcing.iterators.PartitionIterator;
import datastructures.graphs.WeightedGraph;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.outsourcing.SpaceManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import logging.Logger;
import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class DynamicProgrammingOnNTD {

    public final Ntd ntd; //public zum debuggen

    final WeightedGraph weightedGraph;

    public final Stack<MstStateVectorProxy> stateVectorStack;

    public MstStateVectorProxy current_sv = null;
    public MstStateVectorProxy second_current_sv = null;

    //infoTree
    int[] bellNrs = new int[]{1, 1, 2, 5, 15, 52, 203, 877, 4140, 21147, 115975, 678570, 4213597, 27644437, 190899322, 1382958545};

    public DynamicProgrammingOnNTD(WeightedGraph weightedGraph, Ntd ntd) {
        this.ntd = ntd;
        stateVectorStack = new Stack<>();
        this.weightedGraph = weightedGraph;
        SpaceManager.init(this);
    }

    public MstStateVectorProxy run() throws Exception {
        //InfoTree
//        infoTree = new InfoTree(datastructuctures.ntd);


        Set<NtdNode> visited = new HashSet<>();
        Stack<NtdNode> stack = new Stack<>();
        stack.push(ntd.getRoot());

        // post-order DFS
        while (!stack.isEmpty()) {
            NtdNode v = stack.peek();
            if (visited.contains(v)) {
                stack.pop();
                handleBag(v);
                continue;
            }
            visited.add(v);
            if (v.getFirstChild() != null) {
                stack.push(v.getFirstChild());
                if (v.getSecondChild() != null)
                    stack.push(v.getSecondChild());
            }
        }

        SpaceManager.finishedCalculation();

//        Logger.writeToLog(infoTree.toTikz(),false);

        MstStateVectorProxy solutionVectorProxy = stateVectorStack.pop();
        PartitionIterator partitionIterator = solutionVectorProxy.getPartitionIterator(solutionVectorProxy.statesFolder);

        ArrayList<IntArrayList> partition = partitionIterator.next();
        if (solutionVectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            partition.add(new IntArrayList()); //dieser randfall wird anders behandelt
        }
        if (!(stateVectorStack.isEmpty() && //Es gibt nur einen State Vector
                solutionVectorProxy.getStatesSize() == 1 && // Nur eine mögliche Partition
                partition.size() == 1 && //Die Partition hat nur eine Klasse
                partition.get(0).isEmpty() // Die Klasse ist leer
        )) {
            Logger.critical("ERROR: Ungültiger Lösungsvektor\n");
            Logger.critical(solutionVectorProxy.states + "\n");
            throw new RuntimeException("ERROR: Ungültiger Lösungsvektor");

        }
        return solutionVectorProxy;
    }

    private void handleBag(NtdNode node) throws Exception {

        //  dataLog
//        System.out.println(node);
        int firstPartCount = 0;
        int secondPartCount = 0;
        int firstForestCount = 0;
        int secondForestCount = 0;
        String fNodeType = "none";
        String sNodeType = "none";

        //  infos fürs dataLog und SpaceManager
        if (node.getNodeType() == Ntd.NodeType.JOIN) { //second_current_sv als den oberen wählen, um Randfälle zu umgehen
            second_current_sv = stateVectorStack.pop();
            secondPartCount = second_current_sv.getStatesSize();
            secondForestCount = second_current_sv.mstStateVector.forestCount;
            sNodeType = node.getSecondChild().getNodeType().toString();
        }

        if (node.getNodeType() != Ntd.NodeType.LEAF) {
            current_sv = stateVectorStack.pop();
            firstPartCount = current_sv.getStatesSize();
            firstForestCount = current_sv.mstStateVector.forestCount;
            fNodeType = node.getFirstChild().getNodeType().toString();

        } else {
            firstPartCount = -1;
            firstForestCount = -1;
            fNodeType = "none";
        }

        //  spaceManager
        SpaceManager.aboutToHandleNode(node);


        //Logger (heapSpam)
        if (Logger.logHeapSpam) {
            long stackObergrenze = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
            Logger.spam(node + " ---" + stackObergrenze + "\n");
            if (RuntimeHeapWatcher.currentResult != null) {
                RuntimeHeapWatcher.currentResult.pareto_mst_max_heap_usage = Math.max(RuntimeHeapWatcher.currentResult.pareto_mst_max_heap_usage, (int) stackObergrenze);
            }
        }


        long start = System.nanoTime();

        switch (node.getNodeType()) {
            case LEAF -> current_sv = new MstStateVectorProxy();
            case INTRODUCE -> current_sv.mstStateVector.introduce(node.getSpecialVertex());
            case FORGET -> current_sv.mstStateVector.forget(node.getSpecialVertex(), ntd.treeIndex);
            case EDGE ->
                    current_sv.mstStateVector.edge(node.getSpecialVertex(), node.getSecondSpecialVertex(), weightedGraph.weights, ntd.treeIndex);
            case JOIN -> current_sv.mstStateVector.join(second_current_sv, ntd.treeIndex, node.bag.size());
        }


        //Dem space manager sagen, dass wir mit der momentanen Node fertig sind
        SpaceManager.handledNode();

        stateVectorStack.push(current_sv);

        current_sv = null;
        second_current_sv = null;

        //data Log infos schreiben, falls die Daten gelogged werden sollen
        long end = System.nanoTime();
        long elapsedMs = Math.round((end - start) / 1_000_000f);

        MstStateVectorProxy top_sv = stateVectorStack.peek();

        if (node.getNodeType() != Ntd.NodeType.LEAF && node.getNodeType() != Ntd.NodeType.INTRODUCE) {
            Logger.writeToDataLog("third", String.format("%d,%d,%d,%d,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d\n",
                    Benchmark.trial_nr, Benchmark.graph_nr,
                    node.bag.size(), bellNrs[node.bag.size()], top_sv.mstStateVector.introducedVerticesCount, top_sv.mstStateVector.introducedEdgesCount, top_sv.mstStateVector.forgottenVerticesCount,
                    fNodeType, sNodeType, node.getNodeType().toString(),
                    firstPartCount, secondPartCount, top_sv.getStatesSize(),
                    firstForestCount, secondForestCount, top_sv.mstStateVector.forestCount,
                    elapsedMs
            ));
        }
//        checkPartitionVertexSum(stateVectorStack.peek(),node);
    }

    public static void handleInteruption() throws Exception {
        if (SpaceManager.reduceSpaceRequested || SpaceManager.delayedReduceRequest) {
            SpaceManager.reduceSpace();
        } else {
            throw new TimeoutException();
        }
    }


    private void checkPartitionVertexSum(MstStateVectorProxy vectorProxy, NtdNode node) { //NUR ZUM DEBUGGEN
        int checkSum = node.bag.size();
        PartitionIterator partitionIterator = vectorProxy.getPartitionIterator();
        while (partitionIterator.hasNext()) {
            ArrayList<IntArrayList> partition = partitionIterator.next();
            int tmpSum = 0;
            for (IntArrayList class_ : partition) {
                tmpSum += class_.size();
            }
            if (tmpSum != checkSum) {
                Logger.critical("Partitions Vertex Summe ist falsch\n");
            }
        }
    }
}
