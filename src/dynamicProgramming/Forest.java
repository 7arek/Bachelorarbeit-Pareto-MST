package dynamicProgramming;

import static benchmarking.SolutionPrinter.appendForest;

public class Forest {



    public Forest(int weightDimension) {
        this.weight = new int[weightDimension];
    }

    public Forest(int[] weight, int u, int v, Forest solutionOrigin, Forest secondSolutionOrigin) {
        this.weight = weight;
        this.u = u;
        this.v = v;
        this.solutionOrigin = solutionOrigin;
        this.secondSolutionOrigin = secondSolutionOrigin;
    }

    public boolean isOutsourced = false;

    public final int[] weight;
    // Kanten-pointer zur rekonstruktion der Lösung
    public int u = -1;

    public int v = -1;

    public Forest solutionOrigin;

    public Forest secondSolutionOrigin; //nur für join nodes

    public long id = Long.MIN_VALUE;

    public static int[] getAddedWeight(int[] aWeight, int[] bWeight) {
        return writeAddedWeight(aWeight, bWeight, new int[aWeight.length]);
    }

    public static int[] writeAddedWeight(int[] aWeight, int[] bWeight, int[] output) {
        for (int i = 0; i < output.length; i++) {
            output[i] = aWeight[i] + bWeight[i];
        }
        return output;
    }

    public static boolean weightsLess(int[] aWeight, int[] bWeight) {
        return (aWeight[0] < bWeight[0] || aWeight[0] == bWeight[0] && aWeight[1] < bWeight[1]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendForest(this, sb);
        return sb.toString();
    }

}
