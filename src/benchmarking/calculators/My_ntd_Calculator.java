package benchmarking.calculators;

import benchmarking.Result;
import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import datastructures.ntd.TransformationFailureException;
import datastructures.ntd.NtdTransformer;
import datastructures.ntd.Ntd;

public class My_ntd_Calculator extends Calculator {
    NiceTreeDecomposition<Integer> jd_ntd;
    public My_ntd_Calculator(NiceTreeDecomposition<Integer> jd_ntd, Result result) {
        super(result,result.my_ntd_time);
        this.jd_ntd = jd_ntd;
    }

    @Override
    Ntd calculate() throws TransformationFailureException {
        return NtdTransformer.getNtdFromJdNtd(jd_ntd);
    }
}
