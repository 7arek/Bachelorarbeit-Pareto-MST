package dynamicProgramming;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.*;

public class State {

    public ArrayList<IntArrayList> partition;

    public ArrayList<Forest> compatibleForests;

    /**
     * Erzeugt einen neuen State und initialisiert partition und compatibleForests (leer).
     */
    public State() {
        partition = new ArrayList<>();
        compatibleForests = new ArrayList<>();
    }

    public State(ArrayList<IntArrayList> partition, ArrayList<Forest> compatibleForests) {
        this.partition = partition;
        this.compatibleForests = compatibleForests;
    }

    /**
     * Erzeugt einen neuen State, welcher eine deep-copy der Partition oPartition erhält.
     * @param oPartition die Partition, welche kopiert werden soll.
     */
    public State(ArrayList<IntArrayList> oPartition) {
        partition = new ArrayList<>();
        for (IntArrayList class_ : oPartition) {
            partition.add(new IntArrayList(class_));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int i = 0; i < partition.size(); i++) {
            sb.append("{");
            Iterator<Integer> classIterator = partition.get(i).iterator();
            while (classIterator.hasNext()) {
                sb.append(classIterator.next());
                if (classIterator.hasNext()) {
                    sb.append(", ");
                }
            }
            if (i < partition.size() - 1)
                sb.append("}, ");
            else
                sb.append("}");
        }
        sb.append("}:  ");
        sb.append(compatibleForests.size());
        return sb.toString();
    }

    public static short getBracketCode(ArrayList<IntArrayList> partition) {
        short bracketCode = 0;
        for (int i = 0; i < partition.size();i++) {
            bracketCode += 1;
            if (i == partition.size() - 1) {
                bracketCode = (short) (bracketCode << (partition.get(i).size() - 1));
            } else {
                bracketCode = (short) (bracketCode << (partition.get(i).size()));
            }
        }
        return bracketCode;
    }

    public static long getNumberCode(ArrayList<IntArrayList> partition, HashMap<Integer, Integer> treeIndex) {
        long numberCode = 0;
        for (IntArrayList class_ : partition) {
            for (int v : class_) {
                numberCode = numberCode << 4; //4 bit, für tw <=15
                numberCode += treeIndex.get(v);
            }
        }
        return numberCode;
    }
}
