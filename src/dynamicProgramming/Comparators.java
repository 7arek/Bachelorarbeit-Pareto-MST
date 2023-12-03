package dynamicProgramming;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Comparator;

public class Comparators {
    //Singleton Klasse
    public static final Comparator<IntArrayList> INT_ARRAY_LIST_COMPARATOR = Comparator.comparingInt(o -> o.getInt(0));
}
