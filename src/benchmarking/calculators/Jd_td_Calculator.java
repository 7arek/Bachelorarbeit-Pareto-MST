package benchmarking.calculators;

import benchmarking.Result;
import datastructures.graphs.WeightedGraph;
import jdrasil.graph.TreeDecomposition;
import datastructures.ntd.NtdTransformer;

public class Jd_td_Calculator extends Calculator {
    WeightedGraph weightedGraph;
    public Jd_td_Calculator(WeightedGraph weightedGraph, Result result) {
        super(result,result.jd_td_time);
        this.weightedGraph = weightedGraph;
    }

    @Override
    TreeDecomposition<Integer> calculate() {
        return NtdTransformer.getJdTdFromWeightedGraph(weightedGraph);
    }
}
