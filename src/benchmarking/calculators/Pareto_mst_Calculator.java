package benchmarking.calculators;

import benchmarking.Result;
import dynamicProgramming.DynamicProgrammingOnNTD;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import datastructures.graphs.WeightedGraph;
import datastructures.ntd.Ntd;

public class Pareto_mst_Calculator extends Calculator{
    WeightedGraph weightedGraph;
    Ntd ntd;

    public Pareto_mst_Calculator(Result result, WeightedGraph weightedGraph, Ntd ntd) {
        super(result,result.pareto_mst_time);
        this.weightedGraph = weightedGraph;
        this.ntd = ntd;
    }

    @Override
    MstStateVectorProxy calculate() throws Exception {
        return new DynamicProgrammingOnNTD(weightedGraph, ntd).run();
    }
}
