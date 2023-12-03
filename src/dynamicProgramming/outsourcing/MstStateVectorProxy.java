package dynamicProgramming.outsourcing;

import dynamicProgramming.MstStateVector;
import dynamicProgramming.State;
import dynamicProgramming.outsourcing.iterators.DefaultPartitionIterator;
import dynamicProgramming.outsourcing.iterators.OutsourcedPartitionIterator;
import dynamicProgramming.outsourcing.iterators.PartitionIterator;
import forestHeaps.PartitionForestForestBoxerBoxer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class MstStateVectorProxy {

    public final MstStateVector mstStateVector;

    /** <li>Die states sind ggf. (teilweise) ausgelagert.</li>
     *  <li>Ggf. gilt states=null</li>
     */
    public ArrayList<State> states;

    //während forget merge & join merge != null; damit outsourceOriginForests schneller geht
    //für edge im aufbau teil (new_states)
    public ArrayList<State> other_states;
    public File stackFolder;

    public File statesFolder;
    public File boxerFolder;
    public File unprocessedStatesFolder;
    public File oldStatesFolder;

    public PartitionIterator currentPartitionIterator;

    public final static int OUTSOURCE_CURRENT_NOTHING = 0;
    public final static int OUTSOURCE_CURRENT_NEW_SOLUTIONS = 10;
    public final static int OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS = 11;
//    public final static int OUTSOURCE_CURRENT_HEAP = 13;
    public int outsourceCurrentStatus = OUTSOURCE_CURRENT_NOTHING;
    public HashMap<Short, HashMap<Long, PartitionForestForestBoxerBoxer>> joinBoxerMap;
    public ArrayList<State> uvInSameClassStates; //für edge

    /**falls seit der letzten auslagerung keine edge- oder join-node von diesem Vektor besucht wurde,
     * so kann es keine neuen origin-forests geben
     */
    boolean canContainOriginForests = false;

    public MstStateVectorProxy() {
        mstStateVector = new MstStateVector(this,2);
    }

    public PartitionIterator getPartitionIterator(File statesFolder) {
        if (outsourceCurrentStatus < OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            currentPartitionIterator = new DefaultPartitionIterator(this);
        } else {
            currentPartitionIterator = new OutsourcedPartitionIterator(this,statesFolder);
        }
        return currentPartitionIterator;
    }

    public PartitionIterator getPartitionIterator() {
        return getPartitionIterator(unprocessedStatesFolder);
    }

    /**
     * <li>Gibt die größe von States zurück, oder die Anz. an hashCode Dateien im stackFolder</li>
     * <li>Ist während einer Berechnung im Allgemeinen *nicht* korrekt</li>
     */
    public int getStatesSize() {
        if (outsourceCurrentStatus < OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            return states.size();
        } else {
            return statesFolder.listFiles().length;
        }
    }

}
