package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.Forest;

public interface ForestIterator {

    boolean hasNext();

    Forest next();

    void close();
}
