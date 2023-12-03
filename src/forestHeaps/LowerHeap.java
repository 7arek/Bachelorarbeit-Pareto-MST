package forestHeaps;

import dynamicProgramming.Forest;

import java.util.ArrayList;
import java.util.Arrays;

public class LowerHeap extends AbstractHeap{

    private final ArrayList<Forest> aForests;

    private final ArrayList<Forest> bForests;
    LowerHeapNode[] heapRef;


    public LowerHeap(ArrayList<Forest> aForests, ArrayList<Forest> bForests) {
        boolean aIsSmaller = aForests.size() <= bForests.size();
        if (aIsSmaller) {
            this.aForests = aForests;
            this.bForests = bForests;
        } else {
            this.aForests = bForests;
            this.bForests = aForests;
        }

        this.size = this.aForests.size();
        heap = new LowerHeapNode[size];
        heapRef = (LowerHeapNode[]) heap;
        initHeap();
    }

    @Override
    protected void initHeapIdx(int idx) {
        heapRef[idx] = new LowerHeapNode(aForests.get(idx), 0, Forest.getAddedWeight(
                aForests.get(idx).weight,
                bForests.get(0).weight));
    }

    @Override
    protected void loadNextWeights(int idx) {
        heapRef[idx].bForestIdx++;
        Forest.writeAddedWeight(
                heapRef[idx].aForest.weight,
                bForests.get(heapRef[idx].bForestIdx).weight,
                heapRef[idx].weight);
    }

    @Override
    protected boolean hasNextForest(int idx) {
        return bForests.size() > heapRef[idx].bForestIdx + 1;
    }

    @Override
    public int[] getMinWeight() {
        return heapRef[0].weight;
    }

    @Override
    public Forest getMinForest() {
        return new Forest(
                Arrays.copyOf(heapRef[0].weight, heapRef[0].weight.length),
                -1, -1,
                heapRef[0].aForest, bForests.get(heapRef[0].bForestIdx));
    }

    @Override
    protected boolean weightsLess(int aIdx, int bIdx) {
        return Forest.weightsLess(heapRef[aIdx].weight, heapRef[bIdx].weight);
    }

    private static class LowerHeapNode {
        public LowerHeapNode(Forest aForest, int bForestIdx, int[] weight) {
            this.aForest = aForest;
            this.bForestIdx = bForestIdx;
            this.weight = weight;
        }

        final Forest aForest;
        int bForestIdx;
        final int[] weight;
    }

}
