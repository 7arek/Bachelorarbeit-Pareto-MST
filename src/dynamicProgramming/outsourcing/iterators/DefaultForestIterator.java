package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.Forest;
import dynamicProgramming.State;

import java.util.Iterator;

public class DefaultForestIterator implements ForestIterator{
    final Iterator<Forest> iterator;

    public DefaultForestIterator(State currentState) {
        iterator = currentState.compatibleForests.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Forest next() {
        return iterator.next();
    }

    @Override
    public void close() {
        //muss nix machen
    }
}
