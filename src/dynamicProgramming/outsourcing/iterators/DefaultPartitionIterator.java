package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.Forest;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.State;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Iterator;

public class DefaultPartitionIterator implements PartitionIterator{
    private final MstStateVectorProxy vectorProxy;

    Iterator<State> iterator;

    State lastAddedState;

    State currentState;

    public DefaultPartitionIterator(MstStateVectorProxy vectorProxy) {
        this.vectorProxy = vectorProxy;
        iterator = vectorProxy.states.iterator();
    }

    @Override
    public boolean hasNext() {
        if(iterator.hasNext()) return true;
        vectorProxy.currentPartitionIterator = null; //TEMPORÄR auskommentiert
        return false;
    }

    @Override
    public ArrayList<IntArrayList> next() {
        currentState = iterator.next();
        return currentState.partition;
    }

    @Override
    public void remove() {
        iterator.remove();
    }

    @Override
    public void addState(State state) {
        //Für forget: nichts (inplace) --> also ruft forget das hier nicht auf
        //Für introduce: nichts (inplace) -> also ruft introduce das hier nicht auf
        //Für edge: newstates.add; und den state für addForests merken
        lastAddedState = state;
        vectorProxy.other_states.add(state);
    }

    @Override
    public ForestIterator getForestIterator() {
        //Die Forests sind nicht ausgelagert, also Default iterator
        return new DefaultForestIterator(currentState);
    }

    @Override
    public void processedPartition() {
        //Normal: Nichts machen
    }

    @Override
    public void close() {
        //Nix machen
    }

    @Override
    public void saveForest(Forest forest) {
        //Für Edge, erzeugen neuer Forests
        lastAddedState.compatibleForests.add(forest);
    }

    @Override
    public int getCompatibleForestsSize() {
        return currentState.compatibleForests.size();
    }

    public State getCurrentState() {
        return currentState;
    }
}
