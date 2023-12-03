package benchmarking.calculators;

import benchmarking.Result;
import datastructures.ntd.TransformationFailureException;
import datastructures.ntd.NtdTransformer;
import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;
import rootChoosing.Simulator;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class Better_root_Calculator extends Calculator{
    Ntd ntd;

    public Better_root_Calculator(Ntd ntd, Result result) {
        super(result, result.better_root_time);
        this.ntd = ntd;
    }

    @Override
    Ntd calculate() throws TransformationFailureException {
        Simulator simulator = new Simulator(ntd);
        HashMap<NtdNode, BigInteger> estimatedTimeMap = simulator.estimateAllRoots();

        Map.Entry<NtdNode, BigInteger> minEntry = Simulator.findMin(estimatedTimeMap);

        NtdNode betterRoot = minEntry.getKey();
        result.estimated_time = minEntry.getValue();
        result.root_count = estimatedTimeMap.size();
        return NtdTransformer.copyNtd(ntd, betterRoot);
    }
}
