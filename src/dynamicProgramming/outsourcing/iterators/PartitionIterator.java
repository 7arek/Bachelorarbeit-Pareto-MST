package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.Forest;
import dynamicProgramming.State;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;

public interface PartitionIterator {

    boolean hasNext();

    ArrayList<IntArrayList> next();

    void remove();

    void addState(State state);

    /**
     * Fügt den Forest zum Letzten state, welcher in addState hinzugefügt wurde, hinzu
     */
    void saveForest(Forest forest);

    int getCompatibleForestsSize();

    /**
     * Wird für edge und für das Erstellen der Lösungen benötigt.
     */
    ForestIterator getForestIterator();

    State getCurrentState();
    /**
     * Falls mindestens die Forests ausgelagert sind, existiert eine Datei mit dem hashCode dieser Partition.
     * Da wir die Partition manipulieren, muss auch der Name der Datei aktualisiert werden.
     */
    void processedPartition();
    default void processedPartition(String hashCodeString){
        processedPartition();}

    void close();
}
