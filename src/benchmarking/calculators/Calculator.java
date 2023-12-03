package benchmarking.calculators;

import benchmarking.Result;

public abstract class Calculator {
    IntBoxer intBoxer;
    Result result;

    public Object timeCalculation() throws Exception {
        long startTime = System.nanoTime();
        Object object = calculate();
        long endTime = System.nanoTime();
        intBoxer.set((int) ((endTime - startTime) / 1_000_000));
        return object;
    }
    abstract Object calculate() throws Exception;

    public Calculator(Result result,IntBoxer intBoxer) {
        this.result = result;
        this.intBoxer = intBoxer;
    }
}
