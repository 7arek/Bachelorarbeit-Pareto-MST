package forestHeaps;

import dynamicProgramming.Forest;

public abstract class AbstractHeap {
    Object[] heap;

    protected int size;

    protected void initHeap() {
        for (int i = 0; i < this.size; i++) {
            initHeapIdx(i);
        }
        for (int i = (size - 2) / 2; i >= 0; i--) {
            minHeapify(i);
        }
    }

    public void removeMinAndAddNext() {
        if (hasNextForest(0)) {
            loadNextWeights(0);
            minHeapify(0);
        } else {
            size--;
            heap[0] = heap[size];
            minHeapify(0);
        }
    }

    private void minHeapify(int i) {
        int l = 2 * i + 1;
        int r = l + 1;
        int smallest = i;
        if(l < size && weightsLess(l,smallest))
            smallest = l;
        if(r < size && weightsLess(r,smallest))
            smallest = r;
        if (smallest != i) {
            Object tmp = heap[i];
            heap[i] = heap[smallest];
            heap[smallest] = tmp;

            minHeapify(smallest);
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    protected abstract void initHeapIdx(int idx);

    protected abstract void loadNextWeights(int idx);

    protected abstract boolean hasNextForest(int idx);

    public abstract int[] getMinWeight();

    public abstract Forest getMinForest();

    protected abstract boolean weightsLess(int aIdx, int bIdx);

}
