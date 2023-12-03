package datastructures.graphs;

import logging.Logger;

import java.util.Set;
import java.util.Random;

public class GraphCreator {

    public static WeightedGraph generateRandomWeightedGraph(int numberOfVertices, int numberOfEdges) {
        if(numberOfEdges < numberOfVertices -1 || numberOfEdges > ((long) numberOfVertices * (numberOfVertices-1))/2)
            throw new IllegalArgumentException("Illegale Anzahl an Ecken");

        Logger.status("Erstelle zufälligen WeightedGraph\n");
        double[] executionTimes = new double[3];
        double startTime, endTime;

        //überprüfen, ob numberOfVertices und numberOfEdges kompatibel sind
        if (numberOfEdges < numberOfVertices - 1)
            return null;
        numberOfEdges -= numberOfVertices - 1;

        //uniform zufälligen Spannbaum erstellen
        startTime = System.nanoTime();
        WeightedGraph weightedGraph = createRandomST(numberOfVertices);
        endTime = System.nanoTime();
        executionTimes[0] = endTime - startTime;

        //uniform zufälligen verbundenen Graphen aus dem Spannbaum erstellen
        startTime = System.nanoTime();
        addRandomEdges(weightedGraph, numberOfEdges);
        endTime = System.nanoTime();
        executionTimes[1] = endTime - startTime;

        //uniform zufällig gewichte zuweisen
        startTime = System.nanoTime();
        addRandomWeights(weightedGraph);
        endTime = System.nanoTime();
        executionTimes[2] = endTime - startTime;


        //LOG
        double timeSum = 0;
        for (int i = 0; i < executionTimes.length; i++) {
            executionTimes[i] /= 1000000.0;
            timeSum += executionTimes[i];
        }
        Logger.time("WG total_time, st_time, edges_time, weights_time\n");
        Logger.time(String.format("%f [%f %f %f]\n",
                timeSum, executionTimes[0], executionTimes[1], executionTimes[2]));

        return weightedGraph;
    }

    private static void addRandomWeights(WeightedGraph weightedGraph) {
        Random rnd = new Random();
        for (Integer u : weightedGraph.weights.keySet()) {
            for (Integer v : weightedGraph.weights.get(u).keySet()) {
                int[] weight = weightedGraph.getWeight(u,v);
                for (int i = 0; i < weightedGraph.weightDimension; i++) {
                    //zufallszahl zwischen 1 und 10 mit 2 nachkommastellen generieren
//                    weight[i] = Math.round(Math.random() * 100d) / 1d;
                    weight[i] = rnd.nextInt(1, 1000);
                }
            }
        }
    }

    private static WeightedGraph createRandomST(int numberOfVertices) {
        Random rng = new Random();
        WeightedGraph weightedGraph = new WeightedGraph(2);
        for (int i = 1; i <= numberOfVertices; i++) {
            weightedGraph.addVertex(i);
        }
        Set<Integer> isolated = weightedGraph.jd_graph.getCopyOfVertices();

        int currentVertex = rng.nextInt(1, numberOfVertices + 1);
        isolated.remove(currentVertex);

        while (!isolated.isEmpty()) {
            int randomVertex = rng.nextInt(1, numberOfVertices + 1);
            if (isolated.contains(randomVertex)) {
                weightedGraph.addWeightedEdge(currentVertex,randomVertex, new int[]{0, 0});
                isolated.remove(randomVertex);
            }
            currentVertex = randomVertex;
        }
        return weightedGraph;
    }

    private static void addRandomEdges(WeightedGraph weightedGraph, int numberOfEdges) {
        Random rng = new Random();
        int u, v;
        while (numberOfEdges != 0) {
            u = rng.nextInt(1, weightedGraph.jd_graph.getNumVertices() + 1);
            v = rng.nextInt(1, weightedGraph.jd_graph.getNumVertices() + 1);
            if (!weightedGraph.jd_graph.isAdjacent(u, v) && u != v) {
                weightedGraph.addWeightedEdge(u,v, new int[]{0, 0});
                numberOfEdges--;
            }
        }
    }

}
