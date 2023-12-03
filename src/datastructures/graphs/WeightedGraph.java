package datastructures.graphs;

import jdrasil.graph.Graph;
import jdrasil.graph.GraphFactory;

import java.util.HashMap;

public class WeightedGraph {

    public final Graph<Integer> jd_graph;

    public final HashMap<Integer, HashMap<Integer,int[]>> weights;
    public final int weightDimension;

    public WeightedGraph(int weightDim) {
        jd_graph = GraphFactory.emptyGraph();
        weights = new HashMap<>();
        weightDimension = weightDim;
    }

    public void addWeightedEdge(int u, int v, int[] weight) {
        jd_graph.addEdge(u, v);

        // u <= v sicherstellen

        if (u > v) {
            int w = u;
            u = v;
            v = w;
        }

        HashMap<Integer, int[]> map;
        if (!weights.containsKey(u)) {
            map = new HashMap<>();
            weights.put(u, map);
        } else {
            map = weights.get(u);
        }
        map.put(v, weight);
    }


    /**
     * Gibt das Gewicht der Kante {u, v} zurück. u muss nicht kleiner als v sein
     */
    public int[] getWeight(int u, int v) {
        //u <= v sicherstellen
        if (u > v) {
            int w = u;
            u = v;
            v = w;
        }
        return weights.get(u).get(v);
        //der min & max vergleich ist für edge() nicht notwendig, da direkt am anfang u < v sichergestellt wird
    }

    public void addVertex(int i) {
        jd_graph.addVertex(i);
    }
}
