package benchmarking.calculators;

import benchmarking.Result;
import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import jdrasil.graph.TreeDecomposition;
import datastructures.ntd.NtdTransformer;

public class Jd_ntd_Calculator extends Calculator {
    TreeDecomposition<Integer> jd_td;
    public Jd_ntd_Calculator(TreeDecomposition<Integer> jd_td, Result result) {
        super(result,result.jd_ntd_time);
        this.jd_td = jd_td;
    }

    @Override
    NiceTreeDecomposition<Integer> calculate() {
        return NtdTransformer.getJdNtdFromJdTd(jd_td);
    }
}
