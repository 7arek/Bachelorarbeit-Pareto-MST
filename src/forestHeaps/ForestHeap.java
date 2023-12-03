package forestHeaps;

import dynamicProgramming.Forest;
import dynamicProgramming.State;

import java.util.ArrayList;

public class ForestHeap extends AbstractHeap {
    private final ArrayList<State> stateList;
    int[][] heapRef;
    public ForestHeap(ArrayList<State> stateList) {
        this.size = stateList.size();
        this.stateList = stateList;
        heap = new int[size][2];
        heapRef = (int[][]) heap;
        initHeap();
    }

    @Override
    protected void initHeapIdx(int idx) {
        heap[idx] = new int[]{idx, 0};
    }

    @Override
    protected void loadNextWeights(int idx) {
        heapRef[idx][1]++;
    }

    @Override
    protected boolean hasNextForest(int idx) {
        return stateList.get(heapRef[idx][0]).compatibleForests.size() > heapRef[idx][1] + 1;
    }

    @Override
    public int[] getMinWeight() {
        return stateList.get(heapRef[0][0]).compatibleForests.get(heapRef[0][1]).weight;
    }

    @Override
    public Forest getMinForest() {
        return stateList.get(heapRef[0][0]).compatibleForests.get(heapRef[0][1]);
    }

    @Override
    protected boolean weightsLess(int aIdx, int bIdx) {
        return Forest.weightsLess(stateList.get(heapRef[aIdx][0]).compatibleForests.get(heapRef[aIdx][1]).weight,stateList.get(heapRef[bIdx][0]).compatibleForests.get(heapRef[bIdx][1]).weight);
    }
}
