package dynamicProgramming.outsourcing;

import benchmarking.Benchmark;
import benchmarking.RuntimeHeapWatcher;
import dynamicProgramming.Comparators;
import dynamicProgramming.DynamicProgrammingOnNTD;
import dynamicProgramming.Forest;
import dynamicProgramming.State;
import dynamicProgramming.outsourcing.iterators.DefaultPartitionIterator;
import dynamicProgramming.outsourcing.iterators.OutsourcedPartitionIterator;
import forestHeaps.ForestForestBoxer;
import forestHeaps.PartitionForestForestBoxerBoxer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import logging.Logger;
import main.Main;
import datastructures.ntd.Edge;
import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static benchmarking.SolutionPrinter.originForestsRaf;

public class SpaceManager {

    /**
     * Gibt an, ob das Auslagern aktueller Berechnungen erlaubt wird. <br>
     * Ist das Auslagern nicht erlaubt und der Pareto-MST Algorithmus überschreitet den Auslagerungs-Schwellwert, so wird die
     * Ausführung des Pareto-MST Algorithmus mit einem künstlichen OOME unterbrochen. Dieser kann abgefangen werden, um
     * das Programm problemlos fortzusetzen.
     */
    public static boolean allowOutsourcing;

    /**In diesen Ordner werden die Berechnungen des ParetoMST-Algorithmus ausgelagert. <br>
     * Wenn der Computer z.B. mit einer Cloud synchronisiert wird, empfiehlt es sich diesen Ordner außerhalb des Synchronisierungsbereichs zu platzieren.<br>
     * Der angegebene Ordner *muss* zu Anfang leer, oder nicht vorhanden sein. Ansonsten bricht das Programm ab, um nicht ungewollt Daten zu löschen.
     */
    public static File outsourceFolder;
    public static final int SURFACE_FORESTS_BYTE_BUFFER_SIZE = Integer.BYTES * 2 + Long.BYTES * 1 + 1;
    public static final int BOXER_BYTE_BUFFER_SIZE = Short.BYTES * 2 + Long.BYTES * 2 + 1;


    public static File originForestFile;

    static final int ORIGIN_FORESTS_BYTE_BUFFER_SIZE = Integer.BYTES * 2 + Long.BYTES * 2 + 1;

    public static DynamicProgrammingOnNTD dynProg;

    public static NtdNode currentNode;
    public static NtdNode lastNode;


    public enum nodePhase {
        BUILDUP,
        MERGE
    }

    public static nodePhase currentNodePhase;
    static long firstFreeForestID;

    public static BufferedOutputStream originForestStream;


    private final static int OUTSOURCE_NOTHING = 0;
    private final static int OUTSOURCE_ORIGIN_FORESTS = 1;
    private final static int OUTSOURCE_STACK = 2;
    private final static int OUTSOURCE_CURRENT_CALCULATION = 3;

    public static int currentOutsourceStatus = OUTSOURCE_NOTHING; //public zum debuggen

    public static boolean reduceSpaceRequested;
    public static boolean delayedReduceRequest;

    public static void init(DynamicProgrammingOnNTD dynamicProgrammingOnNTD) {
        dynProg = dynamicProgrammingOnNTD;
        originForestFile = null;
        currentNode = null;
        currentNodePhase = null;
        firstFreeForestID = 0;
        originForestStream = null;
        reduceSpaceRequested = false;
        delayedReduceRequest = false;

    }

    /**
     * Diese Methode wird von dem HeapWatcher thread aufgerufen, der bring dann den rechnenden thread dazu anzuhalten
     * und reduceSpace aufzurufen.
     */
    public static void requestReduction() {
        Logger.outsourcing("Auslagerung ANGEFRAGT\n");


        if (isReduceIllegal()) {
            Logger.outsourcing("Auslagerung VERZÖGERT - Wir sind nicht in join, im Edge Aufbau, oder außerhalb einer Berechnung\n");
            delayedReduceRequest = true;
            return;
        }

        if (reduceSpaceRequested) {
            Logger.outsourcing("Auslagerung ABGELEHNT - Es wird gerade Ausgelagert\n");
//            delayedReduceRequest = true;
            return;
        }

        if (currentOutsourceStatus == OUTSOURCE_CURRENT_CALCULATION && dynProg.current_sv.outsourceCurrentStatus == MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            Logger.outsourcing("Auslagerung ABGELEHNT - Es wurde schon alles Ausgelagert (gc wird ab dem Schwellwert nicht mehr explizit aufgerufen)\n");
            return;
        }


        //Den rechnenden Thread sagen, dass er auslagern soll
        reduceSpaceRequested = true;
        Benchmark.currentExecutionThread.interrupt();
    }

    private static boolean isReduceIllegal() {
        return !(currentNode == null || currentNode.getNodeType() == Ntd.NodeType.JOIN || (currentNode.getNodeType() == Ntd.NodeType.EDGE && currentNodePhase == nodePhase.BUILDUP));
    }

    public static void reduceSpace() {
        Logger.outsourcing("Auslagerung AUFGERUFEN\n");

        if (isReduceIllegal()) {
            Logger.outsourcing("Auslagerung VERZÖGERT - Die Berechnung ist nach Auslagerungs-Anfrage in eine Phase übergegangen, in welcher nicht ausgelagert wird - Wir sind nicht in join, im Edge Aufbau, oder außerhalb einer Berechnung \n");
            delayedReduceRequest = true;
            return;
        }

        Logger.status("Auslagerung wird gestartet\n");

        RuntimeHeapWatcher.currentResult.outsourced = true;

        switch (currentOutsourceStatus) {
            case OUTSOURCE_NOTHING:
                //ggf. gibt es neue origin-forests -> auslagern
                reduceOriginForests();

                //  zur ersten Stufe (origin_forests) wechseln
                currentOutsourceStatus = OUTSOURCE_ORIGIN_FORESTS;
                break;
            case OUTSOURCE_ORIGIN_FORESTS:
                if (!dynProg.stateVectorStack.isEmpty()) {
                    //ggf. gibt es neue origin-forests -> auslagern
                    reduceOriginForests();
                    //momentan werden nur orign-forests ausgelagert, ggf. gibt es auch neue; trotzdem zur nächsten Stufe
                    reduceStack();
                    currentOutsourceStatus = OUTSOURCE_STACK;
                    break;
                } else {
                    Logger.outsourcing("Auslagerung - ÜBERSPRINGE reduceStack - Stack ist leer\n");
                    currentOutsourceStatus = OUTSOURCE_STACK;
                }
            case OUTSOURCE_STACK:
                //ggf. gibt es neue origin-forests -> auslagern
                reduceOriginForests();

                if (isStackFullyOutsourced()) {
                    //Der Stack ist komplett ausgelagert, jetzt muss die aktuelle Berechnung z.t. ausgelagert werden

                    if (currentNode != null && currentNode.getNodeType() == Ntd.NodeType.JOIN && currentNodePhase == nodePhase.MERGE) {//currentNode == null -> verzögerte Auslagerung
                        //Der erste auslagerungs-Modus bei Join-Merge
                        reduceNewSolutions();
                        dynProg.current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS;
                        dynProg.second_current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS;
                    } else {
                        //Der erste auslagerungs-Modus bei Edge, Forget, Join-Buildup
                        reduceCurrent();
                        dynProg.current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;
                        if (dynProg.second_current_sv != null)
                            dynProg.second_current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;

                    }

                    currentOutsourceStatus = OUTSOURCE_CURRENT_CALCULATION;
                } else {
                    //Es gibt (seit letzter Auslagerung) ein neues Element auf dem Stack, dass ausgelagert werden kann
                    //wir lagern erstmal nur das aus und erhöhen noch nicht den auslagerungs-Modus
                    //kann auch durch eine verzögerte Auslagerung kommen, da die Berechnung dann auf dem Stack ist
                    reduceStack();
                }
                break;
            case OUTSOURCE_CURRENT_CALCULATION:
                //ggf. gibt es neue origin-forests -> auslagern
                reduceOriginForests();
                switch (dynProg.current_sv.outsourceCurrentStatus) {
                    case MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS -> {
                        reduceCurrent();
                        dynProg.current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;
                    }
                    case MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS -> {
                        Logger.critical("WARNUNG: reduceSpace request, bei Status OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS sollte eigentlich abgefangen werden\n");
                    }
                }

                break;
        }

        reduceSpaceRequested = false;

        Logger.status("Auslagerung DURCHGELAUFEN\n");
        Logger.outsourcing("Auslagerung DURCHGELAUFEN\n");

    }

    public static void reduceOriginForests() {

        Logger.outsourcing("Auslagerung ORIGIN-FORESTS\n");

        //origin forest Datei, Stream init
        if (originForestStream == null) {
            outsourceFolder.mkdir();
            originForestFile = new File(outsourceFolder + "/origin_forests.txt");
            try {
                if (!originForestFile.createNewFile()) {
                    throw new RuntimeException("Origin Forest File existiert schon\n)");
                }
                originForestStream = new BufferedOutputStream(new FileOutputStream(originForestFile, true));
                if (originForestsRaf == null) {
                    originForestsRaf = new RandomAccessFile(SpaceManager.originForestFile, "r");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //  Eine Liste mit allen vectorProxies zum Iterieren erstellen
        ArrayList<MstStateVectorProxy> vectorProxies = new ArrayList<>(dynProg.stateVectorStack.size() + 2);
        vectorProxies.addAll(dynProg.stateVectorStack);
        if (dynProg.current_sv != null) vectorProxies.add(dynProg.current_sv);
        if (dynProg.second_current_sv != null && currentNode != null) vectorProxies.add(dynProg.second_current_sv);


        //  Über die Liste iterieren und je auslagern
        for (MstStateVectorProxy vectorProxy : vectorProxies) {


            //Der Vektor kann unmöglich origin-forests haben -> abbrechen
            if (currentNode != null && ((!vectorProxy.canContainOriginForests) && //currentNode != null wegen ver
                    !((vectorProxy == dynProg.current_sv || vectorProxy == dynProg.second_current_sv) &&
                            (currentNode.getNodeType() == Ntd.NodeType.JOIN || currentNode.getNodeType() == Ntd.NodeType.EDGE))))
                continue;
            vectorProxy.canContainOriginForests = false;

            //  Eine Liste mit allen Listen von States, welche ausgelagert werden sollen, erstellen
            ArrayList<ArrayList<State>> stateListsList = new ArrayList<>(2);
            //wenn bei forget old_states != null, so sind alle states aus states auch in old_states und wir sparen unnötiges iterieren
            if (currentNode == null || !(currentNode.getNodeType() == Ntd.NodeType.FORGET && vectorProxy.other_states != null))
                stateListsList.add(vectorProxy.states);

            //für forget, edge, join
            if (vectorProxy.other_states != null) stateListsList.add(vectorProxy.other_states);

            //  Über die Liste iterieren und je auslagern
            for (ArrayList<State> states : stateListsList) {
                if (states == null) continue;
                outsourceOriginForests(states);
            }
        }

        //  Logger
        Logger.outsourcing("Auslagerung ORIGIN-FORESTS FERTIG\n");
        try {
            originForestStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void outsourceOriginForests(ArrayList<State> stateList) {
        for (State state : stateList) {
            for (Forest root : state.compatibleForests) {
                //  DFS über alle Forests iterieren und auslagern (relativ speicher-effizient)
                Stack<Forest> stack = new Stack<>();
                Forest current = root;
                Forest lastForest = null;

                while (current != null || !stack.isEmpty()) {
                    if (current != null) {  //solutionOrigin versuchen
                        stack.push(current);
                        current = current.solutionOrigin;
                    } else {
                        Forest peekedForest = stack.peek();
                        if (peekedForest.secondSolutionOrigin != null && lastForest != peekedForest.secondSolutionOrigin) { //secondSolutionOrigin versuchen
                            current = peekedForest.secondSolutionOrigin;
                        } else { //Forest abarbeiten
                            lastForest = stack.pop();
                            //Wir testen, ob dieser Forest schon ausgelagert wurde; falls ja stehen alle wichtigen Daten
                            // in der Datei, die id wurde somit auch schon generiert und ist im ID feld
                            if (lastForest.isOutsourced) continue;

                            //Dieser Forest wurde noch nicht ausgelagert -> die Kinder sind also noch referenziert, falls
                            // dieser Forest welche hatte; außerdem wurden diese Kinder dann schon ausgelagert, und die ID
                            // steht im id-feld

                            //jetzt lagern wir diesen Forest aus
                            outsourceOriginForest(lastForest);
                        }
                    }
                }
            }
        }
    }


    public static void reduceStack() {
        //  Logger
        Logger.outsourcing("Auslagerung STACK\n");

        for (MstStateVectorProxy vectorProxy : dynProg.stateVectorStack) {
            if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS)
                continue;
            initVectorFolder(vectorProxy);
            //treeIndex erstellen
            createTreeIndexFile(vectorProxy);

            // Die Forests auslagern
            outsourceForests(vectorProxy.states, vectorProxy.statesFolder, false);

            // Die Partitionen auslagern
            vectorProxy.states = null;
            vectorProxy.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;
        }

        //  Logger
        Logger.outsourcing("Auslagerung STACK FERTIG\n");
    }

    /**
     * <li>betrifft nur join </li>
     * <li>die neuen Lösungen direkt auslagern</li>
     * <li>diese Methode lagert die Lösungen nicht nur aus, sondern ändert den Modus im Proxy so, dass ab diesem Zeitpunkt
     * auch alle weiteren neuen Lösungen direkt ausgelagert werden</li>
     */
    public static void reduceNewSolutions() {
        //  selbst, wenn es nichts zum auslagern gibt, erstellen wir den Ordner und die treeIndex datei, damit
        //  das während join merge nicht mehr initialisiert werden muss

        //  prüfen, ob die neuen Lösungen schon ausgelagert wurden
        if (dynProg.current_sv.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS)
            return;

        //  Logger
        Logger.outsourcing("Auslagerung JOIN-SOLUTIONS\n");

        //  Neuen Ordner erstellen
        initVectorFolder(dynProg.current_sv);
//        if (!initVectorFolder(dynProg.current_sv)) throw new RuntimeException("Der \"neue\" Ordner existiert schon\n");

        //  treeIndex Datei schreiben
        createTreeIndexFile(dynProg.current_sv);

        //prüfen, ob wir uns in der merge-phase befinden (ansonsten gibt es noch keine neuen Lösungen)
        if (currentNodePhase == nodePhase.MERGE) {
            //Die neuen Lösungen stehen bei join in der merge phase in states
            outsourceForests(dynProg.current_sv.states, dynProg.current_sv.statesFolder, false);
        }

        dynProg.current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS;
        dynProg.current_sv.states = null;

        //  Logger
        Logger.outsourcing("Auslagerung JOIN-SOLUTIONS FERTIG\n");
    }

    /**
     * <li>diese Methode lagert die Forests und Boxer nicht nur aus, sondern ändert den Modus im Proxy so,
     * dass ab diesem Zeitpunkt auch alle weiteren Forests und Boxer direkt ausgelagert werden</li>
     */
    public static void reduceCurrent() {
        if (dynProg.current_sv == null) return;

        //  Logger
        Logger.outsourcing("Auslagerung AKTUELLES\n");

        initVectorFolder(dynProg.current_sv);
        //treeIndex erstellen
        createTreeIndexFile(dynProg.current_sv);

        if (dynProg.second_current_sv != null) initVectorFolder(dynProg.second_current_sv);

        if (currentNode == null) {
            //verzögerte Auslagerung bzw. Auslagerung von außerhalb
            //  Die States auslagern
            outsourceForests(dynProg.current_sv.states, dynProg.current_sv.statesFolder, false);
            dynProg.current_sv.states = null;

        } else if (currentNode.getNodeType() == Ntd.NodeType.EDGE && currentNodePhase == nodePhase.BUILDUP) {
            //unnötige Referenzen löschen
            dynProg.current_sv.uvInSameClassStates = null;

            //  Die noch zu iterierenden states auslagern
            outsourceRemainingStates(dynProg.current_sv, dynProg.current_sv.unprocessedStatesFolder);

            //  Die schon iterierten States auslagern
            outsourceForests(dynProg.current_sv.states, dynProg.current_sv.statesFolder, false);
            dynProg.current_sv.states = null;

            // Die neuen States auslagern
            outsourceForests(dynProg.current_sv.other_states, dynProg.current_sv.statesFolder, true);
            dynProg.current_sv.other_states = null;

            // Den Iterator austauschen
            dynProg.current_sv.currentPartitionIterator = new OutsourcedPartitionIterator(dynProg.current_sv);

        } else if (currentNode.getNodeType() == Ntd.NodeType.JOIN && currentNodePhase == nodePhase.BUILDUP) {
            //Die Boxer auslagern
            outsourceBoxers();

            //den aktuellen aPartitionState merken
            State aState = dynProg.current_sv.currentPartitionIterator.getCurrentState();
            ArrayList<IntArrayList> aPartition = aState.partition;

            //  Die noch zu iterierenden states auslagern
            outsourceRemainingStates(dynProg.current_sv, dynProg.current_sv.unprocessedStatesFolder);
            outsourceRemainingStates(dynProg.second_current_sv, dynProg.second_current_sv.unprocessedStatesFolder);

            //  Die schon iterierten States auslagern
            outsourceForests(dynProg.current_sv.states, dynProg.current_sv.oldStatesFolder, false);
            outsourceForests(dynProg.second_current_sv.states, dynProg.second_current_sv.oldStatesFolder, false);
            dynProg.current_sv.states = null;
            dynProg.second_current_sv.states = null;

            // Die Iterator austauschen
            dynProg.current_sv.currentPartitionIterator = new OutsourcedPartitionIterator(dynProg.current_sv);
            aState.partition = aPartition;
            ((OutsourcedPartitionIterator) dynProg.current_sv.currentPartitionIterator).currentState = aState;
            ((OutsourcedPartitionIterator) dynProg.current_sv.currentPartitionIterator).currentFilePath =
                    new File(dynProg.current_sv.oldStatesFolder + "/" + SpaceManager.getHashCodeString(aState.partition) + ".txt").toPath();

            //der bPartition iterator wird erstmal auf den unprocessedStates laufen gelassen (übrige States dieser Iteration)
            //danach wird er automatich durch getPartitionIterator in join()
            // auf dem oldStatesFolder, also auf allen States, erstellt
            dynProg.second_current_sv.currentPartitionIterator = new OutsourcedPartitionIterator(dynProg.second_current_sv);


            //den Ordner für neue Lösungen erstellen
            dynProg.current_sv.statesFolder.mkdir();

        } else if (currentNode.getNodeType() == Ntd.NodeType.JOIN && currentNodePhase == nodePhase.MERGE) {
            //Die Boxer auslagern
            outsourceBoxers();

            // Die States (für die Boxer) auslagern
            outsourceForests(dynProg.current_sv.other_states, dynProg.current_sv.oldStatesFolder, false);
            outsourceForests(dynProg.second_current_sv.states, dynProg.second_current_sv.oldStatesFolder, false);
            dynProg.current_sv.other_states = null;
            dynProg.second_current_sv.states = null;


            //Die neuen Lösungen auslagern (falls dieser Schritt übersprungen wurde)
            reduceNewSolutions();

        } else {
            throw new RuntimeException("Das sollte verzögert werden");
        }

        //  Logger
        Logger.outsourcing("Auslagerung AKTUELLES FERTIG\n");
    }

    private static void reduceJoinedVektor() {
        Logger.outsourcing("Auslagerung JOINED-VEKTOR\n");
        reduceSpaceRequested = true; //Damit andere Anfragen erstmal abgelehnt werden
        initVectorFolder(dynProg.second_current_sv);
        outsourceOriginForests(dynProg.second_current_sv.states);
        outsourceForests(dynProg.second_current_sv.states, dynProg.second_current_sv.statesFolder, false);
        dynProg.second_current_sv.states = null;
        dynProg.second_current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;
        SpaceManager.currentOutsourceStatus = OUTSOURCE_CURRENT_CALCULATION;
        Logger.outsourcing("Auslagerung JOINED-VEKTOR fertig\n");
        reduceSpaceRequested = false;
    }

    private static void outsourceRemainingStates(MstStateVectorProxy vectorProxy, File saveFolder) {
        saveFolder.mkdirs();
        DefaultPartitionIterator iterator = (DefaultPartitionIterator) vectorProxy.currentPartitionIterator;

        while (iterator.hasNext()) {
            ArrayList<IntArrayList> partition = iterator.next();
            File forestsFile = new File(saveFolder + "/" + getHashCodeString(partition) + ".txt");
            outsourceForests(iterator.getCurrentState(), forestsFile);
            iterator.remove();
        }
    }

    /**
     * <li>Stand: Die origin-forests sind alle ausgelagert</li>
     * <li>Stand: Es wurden noch nie surface-Forests dieses Vektors ausgelagert</li>
     * <li>lagert bei join *nicht* die neuen Lösungen aus (passiert in outsourceNewSolutions, oder outsourceCurrentForestsAndBoxers</li>
     * <li>Falls wir ein mal die Forests eines Vektors auslagern, laden wir sie nie mehr für eine Berechnung
     * komplett in den Speicher</li>
     */

    private static void outsourceForests(ArrayList<State> stateList, File saveFolder, boolean canHaveDuplicates) {
        try {
            saveFolder.mkdir();
            for (State state : stateList) {
                //wurde ggf. schon ausgelagert (forget old_states die schon merged states haben keine forests mehr
                if (state.compatibleForests == null) continue;

                // hashCodeString generieren
                String hashCodeString = getHashCodeString(state.partition);


                File tmpStateFile = new File(saveFolder + "/unnamedState.txt");
                File wantedStateFile = new File(saveFolder + "/" + hashCodeString + ".txt");
                tmpStateFile.createNewFile();

                File resultedFile = moveStateFile(tmpStateFile, wantedStateFile, canHaveDuplicates);

                outsourceForests(state, resultedFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void outsourceForests(State state, File forestsFile) {
        try {
            //  I/O init
            forestsFile.createNewFile();
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(forestsFile, false));

            //  forests auslagern
            for (Forest forest : state.compatibleForests) {
                writeSurfaceForestToStream(outputStream, forest);
            }

            //  speicher freigeben
            state.compatibleForests = null;
            state.partition = null;

            // I/O cleanup
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void outsourceBoxers() {
        if (dynProg.current_sv.joinBoxerMap == null) return;

        Logger.outsourcing("auslagerung BOXER\n");

        //  die aStates und bStates für die HashCodes sortieren
        if (currentNodePhase == nodePhase.BUILDUP) {
            for (State aState : dynProg.current_sv.states)
                aState.partition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);
        } else {
            for(State aState : dynProg.current_sv.other_states)
                aState.partition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);
        }
        for (State bState : dynProg.second_current_sv.states)
            bState.partition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);

        // boxer Ordner erstellen
        dynProg.current_sv.boxerFolder.mkdir();
        for (Map.Entry<Short, HashMap<Long, PartitionForestForestBoxerBoxer>> outerEntry : dynProg.current_sv.joinBoxerMap.entrySet()) {
            for (Map.Entry<Long, PartitionForestForestBoxerBoxer> innerEntry : outerEntry.getValue().entrySet()) {

                File currentBoxerFile = new File(dynProg.current_sv.boxerFolder + "/" + outerEntry.getKey() + "," + innerEntry.getKey() + ".txt");
                try {
                    //  I/O init
                    currentBoxerFile.createNewFile();
                    BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(currentBoxerFile, false));

                    for (ForestForestBoxer forestForestBoxer : innerEntry.getValue().forestForestBoxers) {

                        // hashCodes generieren
                        short aBracketCode = State.getBracketCode(forestForestBoxer.aState.partition);
                        long aNumberCode = State.getNumberCode(forestForestBoxer.aState.partition, dynProg.ntd.treeIndex);
                        short bBracketCode = State.getBracketCode(forestForestBoxer.bState.partition);
                        long bNumberCode = State.getNumberCode(forestForestBoxer.bState.partition, dynProg.ntd.treeIndex);

                        //  schreiben
                        ByteBuffer byteBuffer = ByteBuffer.allocate(BOXER_BYTE_BUFFER_SIZE);
                        byteBuffer.putShort(aBracketCode);
                        byteBuffer.putLong(aNumberCode);
                        byteBuffer.putShort(bBracketCode);
                        byteBuffer.putLong(bNumberCode);
                        byteBuffer.put((byte) 10);//newLine in UTF-8
                        outputStream.write(byteBuffer.array());

                    }
                    // I/O cleanup
                    outputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        //speicher freigeben
        dynProg.current_sv.joinBoxerMap = null;
        //  Logger
        Logger.outsourcing("auslagerung BOXER FERTIG\n");
    }

    /**
     * <li>Muss beim erstmaligen Auslagern und nach jedem introduce und forget aufgerufen werden</li>
     */
    private static void createTreeIndexFile(MstStateVectorProxy vectorProxy) {
        // ggf. treeIndex Datei erstellen
        vectorProxy.stackFolder.mkdir();
        File treeIndexFile = new File(vectorProxy.stackFolder + "/treeIndex.txt");
        try {
            treeIndexFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //  Den aktuellen Bag des Vektors laden
        HashSet<Integer> currentBag = new HashSet<>();

        if (vectorProxy == dynProg.current_sv) {
            if (currentNode == null) { //auslagerung von außerhalb
                currentBag.addAll(lastNode.bag);
            } else {
                currentBag.addAll(currentNode.bag);
            }
        } else {
            //  irgendeine Partition laden, um über den Bag zu iterieren
            for (IntArrayList class_ : vectorProxy.getPartitionIterator(vectorProxy.statesFolder).next()) {
                currentBag.addAll(class_);
            }
        }

        // treeIndexInverse erstellen
        int[] treeIndexInverse = new int[dynProg.ntd.tw + 1];
        for (int v : currentBag)
            treeIndexInverse[dynProg.ntd.treeIndex.get(v)] = v;


        // treeIndexInverse String erstellen
        StringBuilder sb = new StringBuilder();
        for (int v : treeIndexInverse) {
            sb.append(v).append(",");
        }
        sb.delete(sb.length() - 1, sb.length());

        // treeIndexInverse in die Datei schreiben
        try {
            FileWriter fw = new FileWriter(vectorProxy.stackFolder + "/treeIndex.txt", false);
            fw.write(sb.toString());
            fw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void outsourceOriginForest(Forest forest) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ORIGIN_FORESTS_BYTE_BUFFER_SIZE);
        forest.id = firstFreeForestID++;
        byteBuffer.putInt(forest.u);
        byteBuffer.putInt(forest.v);
        byteBuffer.putLong(forest.solutionOrigin == null ?
                Long.MIN_VALUE : forest.solutionOrigin.id);
        byteBuffer.putLong(forest.secondSolutionOrigin == null ?
                Long.MIN_VALUE : forest.secondSolutionOrigin.id);
        byteBuffer.put((byte) 10); //newLine in UTF-8
        try {
            originForestStream.write(byteBuffer.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //wir merken an, dass dieser FOrest ausgelagert wurde und geben die Kinder frei
        // zumindest die Referenz, von diesem Forest aus
        forest.isOutsourced = true;
        forest.solutionOrigin = null;
        forest.secondSolutionOrigin = null;
    }

    public static void writeSurfaceForestToStream(BufferedOutputStream outputStream, Forest forest) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SURFACE_FORESTS_BYTE_BUFFER_SIZE);
        byteBuffer.putLong(forest.id);
        byteBuffer.putInt(forest.weight[0]);
        byteBuffer.putInt(forest.weight[1]);
        byteBuffer.put((byte) 10); //newLine in UTF-8
        try {
            outputStream.write(byteBuffer.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayList<IntArrayList> loadPartitionFromPath(Path stateFilePath, int[] treeIndexInverse) {
        int bagSize = 0;
        for (int i : treeIndexInverse) {
            if (i != 0) bagSize++;
        }

        String fileName = stateFilePath.getFileName().toString();
        int commaIndex = fileName.indexOf(",");
        int dotIndex = fileName.lastIndexOf(".");
        short bracketCode = Short.parseShort(fileName.substring(0, commaIndex));
        long numberCode = Long.parseLong(fileName.substring(commaIndex + 1, dotIndex));

        if (bracketCode == 0 && numberCode == 0)
            return new ArrayList<>();

        //  short;long offset ignorieren
        bracketCode = (short) (bracketCode << 16 - bagSize);
        numberCode = (long) (numberCode << (4 * (16 - bagSize)));

        ArrayList<IntArrayList> partition = new ArrayList<>();
        IntArrayList currentClass = null;
        for (int i = 0; i < bagSize; i++) {
            if ((bracketCode & (Short.MIN_VALUE)) != 0) {
                //  Die folgenden Knoten gehören zu einer neuen Klasse
                currentClass = new IntArrayList();
                partition.add(currentClass);
            }
            //  vertex auslesen & hinzufügen
            int currentTreeIdx = (int) ((numberCode >>> (4 * 15)) & (0b1111));
            int currentVertex = treeIndexInverse[currentTreeIdx];
            currentClass.add(currentVertex);

            //  shiften
            bracketCode = (short) (bracketCode << 1);
            numberCode = (long) (numberCode << 4);
        }
        return partition;
    }

    public static String getHashCodeString(ArrayList<IntArrayList> partition) {
        partition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);
        short bracketCode = State.getBracketCode(partition);
        long numberCode = State.getNumberCode(partition, dynProg.ntd.treeIndex);
        return bracketCode + "," + numberCode;
    }

    public static int[] getTreeIndexInverse(MstStateVectorProxy vectorProxy) {
        final int[] treeIndexInverse;
        try {
            File treeIndexFile = vectorProxy == dynProg.second_current_sv ?
                    new File(dynProg.current_sv.stackFolder + "/treeIndex.txt")
                    : new File(vectorProxy.stackFolder + "/treeIndex.txt");

            BufferedReader fileReader = new BufferedReader(new FileReader(treeIndexFile));
            String firstLine = fileReader.readLine();
            fileReader.close();
            String[] treeIndexStringArray = firstLine.split(",");
            treeIndexInverse = new int[treeIndexStringArray.length];
            for (int i = 0; i < treeIndexStringArray.length; i++) {
                treeIndexInverse[i] = Integer.parseInt(treeIndexStringArray[i]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return treeIndexInverse;
    }

    public static File moveStateFile(
            File currentFile,
            File wantedFile,
            boolean canHaveDuplicates) {

        try {
            currentFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(currentFile.toPath().equals(wantedFile.toPath())) return currentFile;

        String fileWithoutExtension = wantedFile.getPath().substring(0, wantedFile.getPath().lastIndexOf("."));

        // Forest Datei Namen finden
        File resultingFile;

        if (canHaveDuplicates && wantedFile.exists()) {
            //Die Partition kommt jetzt doppelt vor -> die vorhandene umbenennen, und diese mit suffix _2 benennen
            resultingFile = new File(fileWithoutExtension + "_2.txt");
            File forestsFile_1 = new File(fileWithoutExtension + "_1.txt");
            boolean didRename = wantedFile.renameTo(forestsFile_1);
            if (!didRename) {
                try {
                    throw new IOException("Rename hat nicht funktioniert");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        } else if (canHaveDuplicates && new File(fileWithoutExtension + "_1.txt").exists()) {
            //Die Partition kommt schon doppelt vor -> kleinsten freien suffix wählen
            int suffixNr = 2;
            do {
                resultingFile = new File(fileWithoutExtension + "_" + suffixNr++ + ".txt");
            }
            while (resultingFile.exists());
        } else {
            //Die Partition kommt nicht doppelt vor
            resultingFile = wantedFile;
        }
        currentFile.renameTo(resultingFile);
        return resultingFile;
    }

    private static boolean initVectorFolder(MstStateVectorProxy vectorProxy) {
        //Stand: Die aktuellen Vektoren sind *nicht* auf dem Stack
        String folderName;

        if (vectorProxy == dynProg.current_sv) {
            folderName = String.valueOf(dynProg.stateVectorStack.size());
        } else if (vectorProxy == dynProg.second_current_sv) {
            folderName = String.valueOf(dynProg.stateVectorStack.size() + 1);
        } else {
            int stack_search = dynProg.stateVectorStack.search(vectorProxy);
            if (stack_search == -1) {
                Logger.critical("Der Vektor wurde nicht auf dem Stack gefunden");
                throw new RuntimeException("Der Vektor wurde nicht auf dem Stack gefunden");
            }
            folderName = String.valueOf(dynProg.stateVectorStack.size() - stack_search);
        }

        vectorProxy.stackFolder = new File(outsourceFolder + "/stack/" + folderName);

        boolean folderIsNew = vectorProxy.stackFolder.mkdirs();

//        if (vectorProxy == dynProg.current_sv || vectorProxy == dynProg.second_current_sv) {
//            // wir sind in der Aktuellen Berechnung
        vectorProxy.statesFolder = new File(vectorProxy.stackFolder + "/states");
        vectorProxy.unprocessedStatesFolder = new File(vectorProxy.stackFolder + "/unprocessedStates");
        vectorProxy.oldStatesFolder = new File(vectorProxy.stackFolder + "/oldStates");
        vectorProxy.boxerFolder = new File(vectorProxy.stackFolder + "/boxers");
        if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            vectorProxy.statesFolder.mkdir();
            vectorProxy.unprocessedStatesFolder.mkdir();
            if (currentNode != null && currentNode.getNodeType() == Ntd.NodeType.JOIN) {
                vectorProxy.oldStatesFolder.mkdir();
                vectorProxy.boxerFolder.mkdir();
            }
        }
//        }


        return folderIsNew;
    }

    public static ArrayList<Forest> loadAllForests(File forestFile) {
        try {
            ArrayList<Forest> forests = new ArrayList<>((int) (Files.size(forestFile.toPath()) / SpaceManager.SURFACE_FORESTS_BYTE_BUFFER_SIZE));

            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(forestFile));

            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(SURFACE_FORESTS_BYTE_BUFFER_SIZE);
                if (inputStream.read(byteBuffer.array()) != SURFACE_FORESTS_BYTE_BUFFER_SIZE)
                    break;
                Forest newForest = new Forest(2);
                forests.add(newForest);
                newForest.id = byteBuffer.getLong();
                newForest.weight[0] = byteBuffer.getInt();
                newForest.weight[1] = byteBuffer.getInt();
            }
            inputStream.close();
            return forests;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static ArrayList<Edge> reloadEdgesDeep(Forest rootForest, RandomAccessFile raf) throws IOException {
        //ohne rekursion, da zb bei e1000 dann schon ein 1000er Aufruf (mehr wegen Join) nötig wäre
        ArrayList<Edge> edges = new ArrayList<>();
        Stack<Long> idStack = new Stack<>();
        idStack.push(rootForest.id);

        while (!idStack.isEmpty()) {
            long currentID = idStack.pop();

            //Die Daten des Forests auslesen
            ByteBuffer byteBuffer = ByteBuffer.allocate(ORIGIN_FORESTS_BYTE_BUFFER_SIZE);
            raf.seek(currentID * ORIGIN_FORESTS_BYTE_BUFFER_SIZE);
            raf.readFully(byteBuffer.array());
            int u = byteBuffer.getInt();
            int v = byteBuffer.getInt();
            long firstOriginID = byteBuffer.getLong();
            long secondOriginID = byteBuffer.getLong();

//            if (idCheck != currentID) {
//                Logger.critical("Origin Forest falsch gelesen (IDs matchen nicht)\n");
//                throw new RuntimeException("Origin Forest falsch gelesen (IDs matchen nicht)\n");
//            }

            //Falls der Forest eine Kante hatte
            if (u != -1) {
                edges.add(new Edge(u, v));
            }

            //die nächsten Forests pushen
            if (firstOriginID != Long.MIN_VALUE) {
                idStack.push(firstOriginID);
            }

            if (secondOriginID != Long.MIN_VALUE) {
                idStack.push(secondOriginID);
            }
        }
        return edges;
    }

    public static void aboutToHandleNode(NtdNode node) {
        //Stand: (second_)current_sv sind richtig gesetzt und nicht mehr auf dem Stack
        currentNode = node;

        if (currentNode.getNodeType() != Ntd.NodeType.LEAF && currentNode.getNodeType() != Ntd.NodeType.INTRODUCE)
            currentNodePhase = nodePhase.BUILDUP;

        if (currentNode.getNodeType() == Ntd.NodeType.JOIN &&
                dynProg.current_sv.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS &&
                dynProg.second_current_sv.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            //current_sv ist ausgelagert und second_current_sv nicht --> second_current_sv auch auslagern
            reduceJoinedVektor();
        }


        //  Status o.ä. richtig setzen
        if (currentNode.getNodeType() == Ntd.NodeType.LEAF) {
            if (currentOutsourceStatus == OUTSOURCE_CURRENT_CALCULATION) {
                //der OUTSOURCE_CURRENT_CALCULATION Status ist vom Vektor abhängig, da wir jetzt auf einem neuen
                // arbeiten, setzten wir den Status zurück
                currentOutsourceStatus = OUTSOURCE_STACK;
            }
        }

        //States Ordner umbenennen
        if (currentNode.getNodeType() != Ntd.NodeType.LEAF) {
            if (dynProg.current_sv.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
                dynProg.current_sv.statesFolder.renameTo(dynProg.current_sv.unprocessedStatesFolder);
                if (dynProg.second_current_sv != null) {
                    //oldStates folder, wegen des dynamischen wechsels mit dem bPartitionIterator
                    dynProg.second_current_sv.statesFolder.renameTo(dynProg.second_current_sv.oldStatesFolder);
                }

            }
        }

        // Folders init
        if (currentOutsourceStatus >= OUTSOURCE_STACK) {
            if (dynProg.current_sv != null) initVectorFolder(dynProg.current_sv);
            if (dynProg.second_current_sv != null) initVectorFolder(dynProg.second_current_sv);
        }
    }

    /**
     * <li>Stand: Node-Berechnung ist durch;
     * die vektoren sind wieder auf dem Stack;
     * die Vektoren sind noch durch (second_)current_sv referenziert</li>
     */
    public static void handledNode() throws Exception {
        //Stand: current_sv ist noch *nicht* auf dem Stack

        // temporäre Ordner löschen
        deleteDir(dynProg.current_sv.unprocessedStatesFolder);
        deleteDir(dynProg.current_sv.oldStatesFolder);
        deleteDir(dynProg.current_sv.boxerFolder);
        if (dynProg.second_current_sv != null)
            deleteDir(dynProg.second_current_sv.stackFolder);

        //temporäre Listen löschen
        dynProg.current_sv.other_states = null;
        dynProg.current_sv.joinBoxerMap = null;
        if (dynProg.second_current_sv != null)
            dynProg.second_current_sv.other_states = null;

        if (currentNode.getNodeType() == Ntd.NodeType.JOIN || currentNode.getNodeType() == Ntd.NodeType.EDGE) {
            //Es kann sein, das bei der Abarbeitung dieser Node neue origin-forests entstanden sind
            dynProg.current_sv.canContainOriginForests = true;
        }

        if (currentNode.getNodeType() == Ntd.NodeType.JOIN && currentOutsourceStatus == OUTSOURCE_CURRENT_CALCULATION) {
            //das Erste, was bei JOIN ausgelagert wird, sind die "neuen" Lösungen, also alles was wir an die neuen
            // Nodes weiter geben. Damit die nächste Node, die den Vektor läd das weiß, aktualisieren wir den Status
            dynProg.current_sv.outsourceCurrentStatus = MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS;
        }

        if (dynProg.current_sv.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS &&
                (currentNode.getNodeType() == Ntd.NodeType.INTRODUCE || currentNode.getNodeType() == Ntd.NodeType.FORGET)) {
            // Den treeIndexFile aktualisieren
            createTreeIndexFile(dynProg.current_sv);
        }

        lastNode = currentNode;
        currentNode = null;

        currentNodePhase = null;


        if (Main.forceOutsource && dynProg.current_sv.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            Logger.debug("Forciere auslagern\n");
            delayedReduceRequest = true;
        }

        //Auf verzögerte Auslagerung prüfen
        if (delayedReduceRequest) {
            delayedReduceRequest = false;
            reduceSpaceRequested = true;
            DynamicProgrammingOnNTD.handleInteruption();
        }
    }

    public static void handledNodeBuildup() {
        Logger.spam(currentNode.getNodeType() + " - merge\n");
        currentNodePhase = nodePhase.MERGE;
    }

    private static boolean isStackFullyOutsourced() {
        boolean isOutsourced = true;
        for (MstStateVectorProxy vectorProxy : dynProg.stateVectorStack) {
            if (vectorProxy.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS) {
                isOutsourced = false;
                break;
            }
        }
        return isOutsourced;
    }

    /**
     * Falls man die Größe der ausgelagerten Daten beschränken müsste.
     * Da das kein wirklich interessantes/wichtiges Problem ist, bei dem ich denke, dass ich es selbst
     * implementieren sollte, ist der Code stark von Stackoverflow "inspiriert".
     * <a href="https://stackoverflow.com/questions/2149785/get-size-of-folder-or-file">...</a>
     */
    public static long[] getFolderSize(Path path) {

        long startTime = System.nanoTime();
        final AtomicLong size = new AtomicLong(0);
        final AtomicLong fileCount = new AtomicLong(0);

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                    size.addAndGet(attrs.size());
                    fileCount.addAndGet(1);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {

                    Logger.outsourcing("getFolderSize: skipped: " + file + " (" + exc + ")\n");
                    size.set(-1);
                    fileCount.set(-1);
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

                    if (exc != null) {
                        Logger.outsourcing("getFolderSize: had trouble traversing: " + dir + " (" + exc + ")\n");
                        size.set(-1);
                        fileCount.set(-1);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
        }

        long endTime = System.nanoTime();

        return new long[]{size.get(), fileCount.get(),endTime-startTime};
    }

    public static void finishedCalculation() {
        try {
            if (originForestStream != null) originForestStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void deleteOutsourcedData() {
        if (SpaceManager.outsourceFolder == null) {
            Logger.status("Lösche ausgelagerte Daten nicht\n");
            return;
        }

        if (System.getProperty("user.dir").startsWith("/home/stud/stuckt0/IdeaProjects/ba")) {
            Logger.status("Lösche ausgelagerte Daten nicht, da POOL\n");
            return;
        }
        Logger.status("Lösche ausgelagerte Daten (falls vorhanden)...\n");

        long startTime = System.nanoTime();
        try {
            if (originForestsRaf != null) {
                originForestsRaf.close();
                originForestsRaf = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (File file : outsourceFolder.listFiles()) {
            deleteDir(file);
        }

        long endTime = System.nanoTime();

        Logger.status((String.format(
                "Ausgelagerte Daten innerhalb von %.3f Sekunden gelöscht\n",
                (endTime - startTime) / 1_000_000_000.0)));
    }

    private static void deleteDir(File file) {
        if (file == null) return;
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        //nur als extra Sicherung
        if (file.getAbsolutePath().startsWith(SpaceManager.outsourceFolder.getAbsolutePath())) {
            file.delete();
        } else {
            Logger.critical("Absicherung der Datenlöschung hat einen falschen Pfad erkannt und nichts gelöscht\n");
            System.exit(94);
        }
    }
}


