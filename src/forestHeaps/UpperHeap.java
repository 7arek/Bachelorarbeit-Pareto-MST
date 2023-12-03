package forestHeaps;

import dynamicProgramming.Forest;

import java.util.ArrayList;

public class UpperHeap extends AbstractHeap {
    private final ArrayList<ForestForestBoxer> forestForestBoxers;

    LowerHeap[] heapRef;

    public UpperHeap(ArrayList<ForestForestBoxer> forestForestBoxers) {
        this.size = forestForestBoxers.size();
        this.forestForestBoxers = forestForestBoxers;
        heap = new LowerHeap[size];
        heapRef = (LowerHeap[]) heap;
        initHeap();
    }

    @Override
    protected void initHeapIdx(int idx) {
        LowerHeap lowerHeap = new LowerHeap(
                forestForestBoxers.get(idx).aState.compatibleForests,
                forestForestBoxers.get(idx).bState.compatibleForests);
        heapRef[idx] = lowerHeap;
    }

    @Override
    protected void loadNextWeights(int idx) {
        heapRef[idx].removeMinAndAddNext();
    }

    @Override
    protected boolean hasNextForest(int idx) {
        return heapRef[idx].size >= 2 || heapRef[idx].hasNextForest(0);
    }

    @Override
    public int[] getMinWeight() {
        return heapRef[0].getMinWeight();
    }

    @Override
    public Forest getMinForest() {
        return heapRef[0].getMinForest();
    }

    @Override
    protected boolean weightsLess(int aIdx, int bIdx) {
        return Forest.weightsLess(heapRef[aIdx].getMinWeight(), heapRef[bIdx].getMinWeight());
    }


}
