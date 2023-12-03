package benchmarking;

import dynamicProgramming.Forest;
import dynamicProgramming.outsourcing.iterators.ForestIterator;
import dynamicProgramming.outsourcing.iterators.PartitionIterator;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.outsourcing.SpaceManager;
import datastructures.ntd.Edge;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Stack;

public class SolutionPrinter {

    public static RandomAccessFile originForestsRaf;

    public static String solutionToString(MstStateVectorProxy vectorProxy) {
        // Anzahl MSTs anfügen
        PartitionIterator partitionIterator = vectorProxy.getPartitionIterator(vectorProxy.statesFolder);
        partitionIterator.next(); //die Lösung reinladen für compatibleForests-size

        String firstLine = partitionIterator.getCompatibleForestsSize() + "\n";

        StringBuilder sb = new StringBuilder();

        //MSTs anfügen
        ForestIterator forestIterator = partitionIterator.getForestIterator();
        while (forestIterator.hasNext()) {
            Forest mst = forestIterator.next();
            appendForest(mst, sb);
            sb.append("\n");
        }
        try {
            if (originForestsRaf != null) {
                originForestsRaf.close();
                originForestsRaf = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //nach kanten sortieren, falls das gewicht gleich ist
        String solution = sb.toString();
        String[] lines = solution.split("\n");
        Arrays.sort(lines, Comparator.
                comparingInt((String line) -> Integer.parseInt(line.split(" ")[0])).
                thenComparingInt((String line) -> Integer.parseInt(line.split(" ")[1])).
                thenComparing(String::compareTo));
        String sortedString = String.join("\n", lines);

        return firstLine.concat(sortedString);
    }

    public static void appendForest(Forest forest, StringBuilder sb) {
        //neuer reader, falls die Datei manipuliert wurde
        if (forest.isOutsourced) {
            try {
                if (SpaceManager.originForestStream != null) SpaceManager.originForestStream.flush();
                if (originForestsRaf != null) originForestsRaf.close();
                originForestsRaf = new RandomAccessFile(SpaceManager.originForestFile, "r");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //Kosten anfügen
        sb.append(forest.weight[0]).append(" ").append(forest.weight[1]);

        //Kanten ausgeben
        //-Kanten liste erstellen
        ArrayList<Edge> edges = new ArrayList<>();
        Stack<Forest> forestStack = new Stack<>();
        forestStack.push(forest);
        while (!forestStack.empty()) {
            Forest currentForest = forestStack.pop();

            if (currentForest.isOutsourced) {
                //der Forest (und somit auch alle Kinder) wurde(n) ausgelagert -> über die originForest den nächsten auslesen
                try {
                    edges.addAll(SpaceManager.reloadEdgesDeep(currentForest, originForestsRaf));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


            } else {
                //der Forest wurde nicht ausgelagert, wir können ganz normal die Kinder in den Stack einfügen
                if (currentForest.u != -1)
                    edges.add(new Edge(currentForest.u, currentForest.v));
                if (currentForest.solutionOrigin != null)
                    forestStack.push(currentForest.solutionOrigin);
                if (currentForest.secondSolutionOrigin != null)
                    forestStack.push(currentForest.secondSolutionOrigin);
            }

        }

        //-Kanten sortieren
        edges.sort(Comparator.comparingInt(Edge::getU).thenComparingInt(Edge::getV));

        //-Kanten anfügen
        for (Edge edge : edges) {
            sb.append(" ").append(edge.getU()).append("-").append(edge.getV());
        }
    }
}


