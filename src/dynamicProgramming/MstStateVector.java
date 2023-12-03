package dynamicProgramming;

import dynamicProgramming.outsourcing.iterators.ForestIterator;
import dynamicProgramming.outsourcing.iterators.OutsourcedPartitionIterator;
import dynamicProgramming.outsourcing.iterators.PartitionIterator;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.outsourcing.SpaceManager;
import forestHeaps.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MstStateVector {

    private final MstStateVectorProxy vectorProxy;

    //infoTree
    public int introducedVerticesCount;
    public int introducedEdgesCount;
    public int forgottenVerticesCount;

    public int forestCount;

    public MstStateVector(MstStateVectorProxy vectorProxy, int weightDimension) {
        this.vectorProxy = vectorProxy;
        vectorProxy.states = new ArrayList<>();

        //Neuen State hinzufügen. Partition = {}, Forests = {{}}
        State newState = new State();
        vectorProxy.states.add(newState);
        newState.compatibleForests.add(new Forest(weightDimension)); //D[t,{}] = {{}}

        //infoTree
        introducedVerticesCount = 0;
        introducedEdgesCount = 0;
        forestCount = 1;

    }

    public void introduce(Integer v) {
        //infoTree
        introducedVerticesCount++;

        //Zu allen Partitionen die Klasse {v} hinzufügen.
        //Forests wird von der ursprünglichen Partition übernommen.
        PartitionIterator partitionIterator = vectorProxy.getPartitionIterator();

        while (partitionIterator.hasNext()) {
            //Neue Klasse {v}
            IntArrayList newClass = new IntArrayList();
            newClass.add((int) v);
            partitionIterator.next().add(newClass);

            // Normal: nichts machen
            // Ausgelagert: den State zum standard Ordner hinzufügen
            partitionIterator.processedPartition();
        }
    }

    public void forget(Integer v, HashMap<Integer, Integer> treeIndex) throws Exception {
        //infoTree
        forgottenVerticesCount++;


        //1. v aus allen Partitionen löschen.
        PartitionIterator partitionIterator = vectorProxy.getPartitionIterator();
        while (partitionIterator.hasNext()) {
            if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();
            ArrayList<IntArrayList> currentPartition = partitionIterator.next();
            for (IntArrayList class_ : currentPartition) {
                if (class_.rem(v)) {
                    if (class_.size() == 0 && currentPartition.size() != 1) {
                        //die Klasse ist nur noch die leere Menge
                        //da v nicht der letzte Knoten war, wir uns also nicht in der Wurzel befinden,
                        //ist v nicht mit dem Graphen verbunden und die Partition ist ungültig

                        //infoTree
                        forestCount -= partitionIterator.getCompatibleForestsSize();

                        //Normal: aus der states Liste entfernen
                        //Ausgelagert: aus dem notIterated Ordner löschen bzw. nicht in den standard Ordner verschieben
                        partitionIterator.remove();
                    } else {
                        //Normal: nichts machen
                        //Ausgelagert: State Datei umbenennen und in den standard Ordner verschieben
                        partitionIterator.processedPartition();
                    }
                    break; //v wurde aus der Partition entfernt -> mit der nächsten Partition weiter machen
                }
            }
        }

        //2. Haben zwei States nach dem Löschen die gleiche Partition, so muss die Menge der kompatiblen Wälder
        // vereinigt werden und anschließend dominierte entfernt werden

        //Dem space Manager sagen, dass wir jetzt mit dem mergen anfangen
        SpaceManager.handledNodeBuildup();


        if (vectorProxy.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            //Alle States mit gleichen Partitionen aus states entfernen und in equalStatesList einfügen
            vectorProxy.other_states = vectorProxy.states; //referenz im Proxy setzen, zum origin-forest Auslagern
            ArrayList<State> newStates = vectorProxy.states;
            vectorProxy.states = new ArrayList<>();


            mergeStates(newStates, treeIndex);

        }

        //hier unter neuem if, statt else, falls wir mergeStates abbrechen durch auslagerungs-Status Wechsel
        if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            mergeOutsourcedStates();
        }

        //die old_states wurden nur fürs Iterieren beim Auslagern benötigt
        vectorProxy.other_states = null;
    }

    public void edge(Integer u, Integer v, HashMap<Integer, HashMap<Integer, int[]>> graphWeights, HashMap<Integer, Integer> treeIndex) throws Exception {
        //infoTree
        introducedEdgesCount++;

        //u < v sicherstellen
        if (u > v) {
            Integer tmp = u;
            u = v;
            v = tmp;
        }

        //Für alle Partitionen, in denen u und v alleine vorkommen eine Partition hinzufügen, welche zusätzlich die neue Edge benutzt

        vectorProxy.uvInSameClassStates = new ArrayList<>();
        if (vectorProxy.outsourceCurrentStatus <= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            vectorProxy.other_states = new ArrayList<>();
        }

        vectorProxy.currentPartitionIterator = vectorProxy.getPartitionIterator();
        stateIteration:
        while (vectorProxy.currentPartitionIterator.hasNext()) {
            if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();
            ArrayList<IntArrayList> currentPartition = vectorProxy.currentPartitionIterator.next();

            //1. den index von u und v finden
            int uClassIndex = -1;
            int vClassIndex = -1;
            for (int i = 0; i < currentPartition.size(); i++) {
                IntArrayList class_ = currentPartition.get(i);
                if (class_.contains((int) u)) {
                    if (class_.contains((int) v)) {  //u,v in gleicher Klasse -> mit nächster Partition weiter machen
                        if (vectorProxy.outsourceCurrentStatus == MstStateVectorProxy.OUTSOURCE_CURRENT_NOTHING)
                            vectorProxy.uvInSameClassStates.add(vectorProxy.currentPartitionIterator.getCurrentState());
                        vectorProxy.currentPartitionIterator.processedPartition(); //fürs auslagern
                        continue stateIteration;
                    }
                    uClassIndex = i;
                } else if (class_.contains((int) v)) {
                    vClassIndex = i;
                }

                if (uClassIndex != -1 && vClassIndex != -1) // u,v index wurden gefunden
                    break;
            }

            //2. Den State kopieren
            State newState = new State(currentPartition);

            //3. Partition anpassen: Die Klassen von u und v vereinigen
            newState.partition.get(uClassIndex).addAll(newState.partition.get(vClassIndex));
            newState.partition.get(uClassIndex).unstableSort(Integer::compare);
            newState.partition.remove(vClassIndex);

            //4. compatibleForests anpassen: Zu allen Forests die neue Kante hinzufügen (d.h. Edge, weight und Solution Pointer setzen)
            int[] edgeWeight = graphWeights.get(u).get(v);

            //wenn die Forests nicht ausgelagert sind, speichern wir die neuen Forests in der entsprechenden Liste
            //andernfalls werden die Forests direkt in die entsprechende Datei geschrieben
            if (vectorProxy.outsourceCurrentStatus == MstStateVectorProxy.OUTSOURCE_CURRENT_NOTHING)
                newState.compatibleForests = new ArrayList<>();

            //über state iterieren, da newState.compatibleForests leer initialisiert wurde
            ForestIterator forestIterator = vectorProxy.currentPartitionIterator.getForestIterator();

            // Normal: den neuen State zu other_states (aka. newStates) hinzufügen und für saveForests merken
            // Ausgelagert: den State zum standard Ordner hinzufügen; und für saveForests merken
            vectorProxy.currentPartitionIterator.addState(newState);

            while (forestIterator.hasNext()) {
                Forest originForest = forestIterator.next();
                vectorProxy.currentPartitionIterator.saveForest(new Forest(
                        Forest.getAddedWeight(originForest.weight, edgeWeight),
                        u, v,
                        originForest, null));
            }

            //infoTree
            forestCount += vectorProxy.currentPartitionIterator.getCompatibleForestsSize(); //brauchen eig. die newState compForest size, aber das ist hier das gleiche

            // Normal: nichts machen
            // Ausgelagert: den State zum standard Ordner hinzufügen
            vectorProxy.currentPartitionIterator.processedPartition();

        }


        //Dem space Manager sagen, dass wir jetzt mit dem mergen anfangen
        SpaceManager.handledNodeBuildup();

        //5. bei gleicher Partition die dominierten Lösungen entfernen
        //5.1 pro states partition: alle gleichen newState partitionen finden, kombinieren, aus newStates löschen. Am Ende pareto filtern

        //Alle Partitionen aus states, welche ein Duplikat haben könnten, von states in newStates verschieben
        if (vectorProxy.outsourceCurrentStatus == MstStateVectorProxy.OUTSOURCE_CURRENT_NOTHING) {
            vectorProxy.other_states.addAll(vectorProxy.uvInSameClassStates);
            vectorProxy.states.removeAll(vectorProxy.uvInSameClassStates);
            vectorProxy.uvInSameClassStates = null;


            //Alle States mit gleichen Partitionen aus newStates entfernen und in equalStatesList einfügen
            mergeStates(vectorProxy.other_states, treeIndex);
        }

        //hier unter neuem if, statt else, falls wir mergeStates abbrechen durch auslagerungs-Status Wechsel
        if (vectorProxy.outsourceCurrentStatus != MstStateVectorProxy.OUTSOURCE_CURRENT_NOTHING) {
            mergeOutsourcedStates();
        }

    }

    public void join(MstStateVectorProxy joinedVectorProxy, HashMap<Integer, Integer> treeIndex, int bagSize) throws Exception {
        //infoTree
        introducedVerticesCount += joinedVectorProxy.mstStateVector.introducedVerticesCount - bagSize;
        introducedEdgesCount += joinedVectorProxy.mstStateVector.introducedEdgesCount;
        forgottenVerticesCount += joinedVectorProxy.mstStateVector.forgottenVerticesCount;
        forestCount = 0;
        int numberOfNewStates = 0;

        //Alle Paare von linkem und rechtem State kombinieren, dabei kreise vermeiden; danach pareto filtern

        //Map initialisieren
        vectorProxy.joinBoxerMap = new HashMap<>();

        //Für alle Paare von States
        vectorProxy.currentPartitionIterator = vectorProxy.getPartitionIterator();

        while (vectorProxy.currentPartitionIterator.hasNext()) {
            ArrayList<IntArrayList> aPartition = vectorProxy.currentPartitionIterator.next();

            joinedVectorProxy.currentPartitionIterator = joinedVectorProxy.getPartitionIterator(joinedVectorProxy.oldStatesFolder);

            while (joinedVectorProxy.currentPartitionIterator.hasNext()) {

                if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();

                ArrayList<IntArrayList> bPartition = joinedVectorProxy.currentPartitionIterator.next();

                ArrayList<IntArrayList> newPartition = combine(aPartition, bPartition);
                if (newPartition == null) {
                    //Es gab einen Kreis
                    joinedVectorProxy.currentPartitionIterator.processedPartition();
                    continue;
                }

                //infoTree (die Forest-Kombinationen, welche nicht genommen werden, werden in Merge wieder abgezogen)
                forestCount += vectorProxy.currentPartitionIterator.getCompatibleForestsSize() *
                        joinedVectorProxy.currentPartitionIterator.getCompatibleForestsSize();

                //die Partition sortieren (Voraussetzung der HashCodes)
                for (IntArrayList class_ : newPartition) {
                    class_.unstableSort(Integer::compare);
                }
                newPartition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);

                //den hashCode generieren
                short bracketCode = State.getBracketCode(newPartition);
                Long numberCode = State.getNumberCode(newPartition, treeIndex);

                //  Den Boxer speichern
                if (vectorProxy.outsourceCurrentStatus <= MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS) {

                    ForestForestBoxer newAbForestBoxer = new ForestForestBoxer(
                            vectorProxy.currentPartitionIterator.getCurrentState(),
                            joinedVectorProxy.currentPartitionIterator.getCurrentState());

                    //zugehörige Partitions-Liste finden (bzw. eine neue erstellen, fall noch keine existiert)
                    HashMap<Long, PartitionForestForestBoxerBoxer> innerMap = vectorProxy.joinBoxerMap.get(bracketCode);
                    if (innerMap == null) {
                        innerMap = new HashMap<>();
                        PartitionForestForestBoxerBoxer partitionBoxer = new PartitionForestForestBoxerBoxer(newPartition);
                        numberOfNewStates++;
                        partitionBoxer.forestForestBoxers.add(newAbForestBoxer);
                        innerMap.put(numberCode, partitionBoxer);
                        vectorProxy.joinBoxerMap.put(bracketCode, innerMap);
                    } else {
                        PartitionForestForestBoxerBoxer partitionBoxer = innerMap.get(numberCode);
                        if (partitionBoxer == null) {
                            partitionBoxer = new PartitionForestForestBoxerBoxer(newPartition);
                            numberOfNewStates++;
                            partitionBoxer.forestForestBoxers.add(newAbForestBoxer);
                            innerMap.put(numberCode, partitionBoxer);
                        } else {
                            partitionBoxer.forestForestBoxers.add(newAbForestBoxer);
                        }
                    }
                } else { //Auslagerung

                    //zugehörige Boxer-Datei finden (bzw. eine neue erstellen, fall noch keine existiert)
                    vectorProxy.boxerFolder.mkdir();
                    File currentBoxerFile = new File(vectorProxy.boxerFolder + "/" + bracketCode + "," + numberCode + ".txt");
                    currentBoxerFile.createNewFile(); //ggf. neue Datei erstellen

                    BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(currentBoxerFile, true));

                    // hashCodes der Iterator auslesen
                    short aBracketCode = ((OutsourcedPartitionIterator) vectorProxy.currentPartitionIterator).getUnmodifiedBracketCode();
                    long aNumberCode = ((OutsourcedPartitionIterator) vectorProxy.currentPartitionIterator).getUnmodifiedNumberCode();
                    short bBracketCode = ((OutsourcedPartitionIterator) joinedVectorProxy.currentPartitionIterator).getUnmodifiedBracketCode();
                    long bNumberCode = ((OutsourcedPartitionIterator) joinedVectorProxy.currentPartitionIterator).getUnmodifiedNumberCode();

                    //  schreiben
                    ByteBuffer byteBuffer = ByteBuffer.allocate(SpaceManager.BOXER_BYTE_BUFFER_SIZE);
                    byteBuffer.putShort(aBracketCode);
                    byteBuffer.putLong(aNumberCode);
                    byteBuffer.putShort(bBracketCode);
                    byteBuffer.putLong(bNumberCode);
                    byteBuffer.put((byte) 10);//newLine in UTF-8
                    outputStream.write(byteBuffer.array());

                    // I/O cleanup
                    outputStream.close();
                }
                joinedVectorProxy.currentPartitionIterator.processedPartition();
            }
            vectorProxy.currentPartitionIterator.processedPartition();

        }

        //gleiche Partitionen zusammenführen (deren Wälder)

        //Dem space Manager sagen, dass wir jetzt mit dem mergen anfangen
        SpaceManager.handledNodeBuildup();

        if (vectorProxy.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            vectorProxy.other_states = vectorProxy.states;
            vectorProxy.states = new ArrayList<>(numberOfNewStates);
        }


        if (vectorProxy.outsourceCurrentStatus < MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            joinDefaultMerge(vectorProxy.joinBoxerMap);
        }
        //hier unter neuem if, statt else, falls wir mergeStates abbrechen durch auslagerungs-Status Wechsel
        if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
            joinOutsourcedMerge(joinedVectorProxy);
        }

        //die other_states wurden nur fürs Iterieren beim Auslagern benötigt
        vectorProxy.other_states = null;
        vectorProxy.joinBoxerMap = null;
    }

    private void joinDefaultMerge(HashMap<Short, HashMap<Long, PartitionForestForestBoxerBoxer>> outerMap) throws Exception {
        for (Iterator<Map.Entry<Short, HashMap<Long, PartitionForestForestBoxerBoxer>>> outerIterator = outerMap.entrySet().iterator(); outerIterator.hasNext(); ) {

            Map.Entry<Short, HashMap<Long, PartitionForestForestBoxerBoxer>> outerEntry = outerIterator.next();
            HashMap<Long, PartitionForestForestBoxerBoxer> innerMap = outerEntry.getValue();

            for (Iterator<Map.Entry<Long, PartitionForestForestBoxerBoxer>> iterator = innerMap.entrySet().iterator(); iterator.hasNext(); ) {

                Map.Entry<Long, PartitionForestForestBoxerBoxer> innerEntry = iterator.next();
                PartitionForestForestBoxerBoxer equalPartitionBoxer = innerEntry.getValue();

                iterator.remove(); // löschen, damit das beim Auslagern nicht mit ausgelagert wird


                joinMerge(equalPartitionBoxer, outerEntry.getKey() + "," + innerEntry.getKey());

                if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_FORESTS_AND_PARTITIONS) {
                    //während des merge-Vorgangs wurde eine Speicher-reduzierung beantragt; es wurde schon alles
                    //ausgelagert, aber wir müssen auch die Merge-Methode wechseln
                    return;
                }
            }
        }
    }

    private void joinOutsourcedMerge(MstStateVectorProxy joinedVectorProxy) throws Exception {
        // File stream erstellen
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(vectorProxy.boxerFolder.toPath());
        Iterator<Path> iterator = directoryStream.iterator();

        int[] treeIndexInverse = SpaceManager.getTreeIndexInverse(vectorProxy);

        //Boxer Iteration
        while (iterator.hasNext()) {

            //  boxer laden init
            Path currentBoxerPath = iterator.next();
            PartitionForestForestBoxerBoxer boxerBoxer = new PartitionForestForestBoxerBoxer(SpaceManager.loadPartitionFromPath(currentBoxerPath, treeIndexInverse));

            BufferedInputStream boxerInputStream = new BufferedInputStream(new FileInputStream(currentBoxerPath.toFile()));

            HashMap<Short, HashMap<Long, State>> thisOuterMap = new HashMap<>();
            HashMap<Short, HashMap<Long, State>> joinedOuterMap = new HashMap<>();

            //  boxer laden
            int i = 0;
            ForestForestBoxer boxer = null;
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(Short.BYTES + Long.BYTES);
                if (boxerInputStream.read(byteBuffer.array()) != Short.BYTES + Long.BYTES)
                    break;
                Short bracketCode = byteBuffer.getShort();
                Long numberCode = byteBuffer.getLong();

                HashMap<Short, HashMap<Long, State>> currentOuterMap;
                State currentState;
                File currentForestFile;


                //  linken / rechten hashCode(+1) lesen
                if (i % 2 == 0) {
                    //wir lesen einen State von currentSV ein
                    currentOuterMap = thisOuterMap;
                    currentForestFile = new File(vectorProxy.oldStatesFolder + "/" + bracketCode + "," + numberCode + ".txt");
                    //  neuen boxer erstellen
                    boxer = new ForestForestBoxer();
                    boxerBoxer.forestForestBoxers.add(boxer);
                } else {
                    //wir lesen einen State vom joinedSV ein
                    currentOuterMap = joinedOuterMap;
                    currentForestFile = new File(joinedVectorProxy.oldStatesFolder + "/" + bracketCode + "," + numberCode + ".txt");
                    boxerInputStream.skip(1); //newLine überspringen
                }

                //  schauen, ob die je Dateien schon links / rechts geladen wurden -> ansonsten laden
                HashMap<Long, State> innerMap = currentOuterMap.get(bracketCode);
                if (innerMap == null) {
                    innerMap = new HashMap<>();
                    //   State laden
                    currentState = new State(null, SpaceManager.loadAllForests(currentForestFile));
                    innerMap.put(numberCode, currentState);
                    currentOuterMap.put(bracketCode, innerMap);
                } else {
                    currentState = innerMap.get(numberCode);
                    if (currentState == null) {
                        //   State laden
                        currentState = new State(null, SpaceManager.loadAllForests(currentForestFile));
                        innerMap.put(numberCode, currentState);
                    }
                }

                //  State an richtiger Stelle speichern
                if (i % 2 == 0) {
                    //wir lesen einen State von currentSV ein
                    boxer.aState = currentState;
                } else {
                    //wir lesen einen State vom joinedSV ein
                    boxer.bState = currentState;
                }
                i++;
            }
            boxerInputStream.close();

            //  den Boxer mergen
            String fileName = currentBoxerPath.getFileName().toString();
            joinMerge(boxerBoxer, fileName.substring(0, fileName.lastIndexOf(".")));

        }
        directoryStream.close();
    }

    private void joinMerge(PartitionForestForestBoxerBoxer boxerBoxer, String hashCodeString) throws Exception {
        State newState = new State(boxerBoxer.partition, new ArrayList<>());
        AbstractHeap heap;

        //Kommt die Partition nicht mehrfach vor, reicht es mit einem LowerHeap zu mergen.
        //Ansonsten wird mit einem UpperHeap merged
        heap = boxerBoxer.forestForestBoxers.size() == 1 ?
                new LowerHeap(boxerBoxer.forestForestBoxers.get(0).aState.compatibleForests,
                        boxerBoxer.forestForestBoxers.get(0).bState.compatibleForests) :
                new UpperHeap(boxerBoxer.forestForestBoxers);
        addParetoForestsToList(heap, newState.compatibleForests);

        if (vectorProxy.outsourceCurrentStatus >= MstStateVectorProxy.OUTSOURCE_CURRENT_NEW_SOLUTIONS) {
            //Den neuen State auslagern
            for (Forest forest : newState.compatibleForests) {
                SpaceManager.outsourceOriginForest(forest);
            }
            SpaceManager.outsourceForests(newState,
                    new File(vectorProxy.statesFolder + "/" + hashCodeString + ".txt"));
        } else {
            vectorProxy.states.add(newState);
        }
    }


    private void mergeOutsourcedStates() throws Exception {
        // File stream erstellen
        try {
            Path folderPath = vectorProxy.statesFolder.toPath();
            DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folderPath);
            Iterator<Path> iterator = directoryStream.iterator();

            while (iterator.hasNext()) {
                Path currentFilePath = iterator.next();
                if (!currentFilePath.toFile().exists()) continue; //Der Stream bekommt das Löschen nicht immer mit
                String fileName = currentFilePath.getFileName().toString();
                if (fileName.contains("_")) {
                    //  Es gibt diese Partition mehrmals -> alle Dateien zu dieser Partition finden
                    String hashCodeString = fileName.substring(0, fileName.lastIndexOf("_"));
                    ArrayList<File> samePartitionFiles = new ArrayList<>();
                    for (int i = 1; true; i++) {
                        File forestFile = new File(vectorProxy.statesFolder + "/" + hashCodeString + "_" + i + ".txt");
                        if (!forestFile.exists()) break;
                        samePartitionFiles.add(forestFile);
                    }

                    //(vielleicht) hier auf extreme Auslagerung erweitern

                    //  alle samePartitionFiles mergen
                    File paretoForestFile = new File(vectorProxy.statesFolder + "/" + hashCodeString + ".txt");
                    ArrayList<State> equalStates = new ArrayList<>(samePartitionFiles.size());
                    for (File forestFile : samePartitionFiles) {
                        State loadedState = new State();
                        loadedState.compatibleForests = SpaceManager.loadAllForests(forestFile);
                        equalStates.add(loadedState);
                        forestFile.delete();
                    }
                    State resultState = mergeAndFilterPartitions(equalStates);
                    SpaceManager.outsourceForests(resultState, paretoForestFile);
                }
                if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeStates(ArrayList<State> states, HashMap<Integer, Integer> treeIndex) throws Exception {
        for (State state : states) {
            state.partition.sort(Comparators.INT_ARRAY_LIST_COMPARATOR);
        }

        int numberOfNewStates = 0;

        HashMap<Short, HashMap<Long, ArrayList<State>>> outerMap = new HashMap<>();
        for (State state : states) {
            short bracketCode = State.getBracketCode(state.partition);
            Long numberCode = State.getNumberCode(state.partition, treeIndex);
            HashMap<Long, ArrayList<State>> innerMap = outerMap.get(bracketCode);
            if (innerMap == null) {
                innerMap = new HashMap<>();
                ArrayList<State> stateList = new ArrayList<>();
                numberOfNewStates++;
                stateList.add(state);
                innerMap.put(numberCode, stateList);
                outerMap.put(bracketCode, innerMap);
            } else {
                ArrayList<State> stateList = innerMap.get(numberCode);
                if (stateList == null) {
                    stateList = new ArrayList<>();
                    numberOfNewStates++;
                    stateList.add(state);
                    innerMap.put(numberCode, stateList);
                } else {
                    stateList.add(state);
                }
            }
            if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();
        }

        vectorProxy.states.ensureCapacity(vectorProxy.states.size() + numberOfNewStates);

        for (HashMap<Long, ArrayList<State>> innerMap : outerMap.values()) {
            for (ArrayList<State> equalStates : innerMap.values()) {
                if (equalStates.size() == 1) {
                    vectorProxy.states.add(equalStates.get(0));
                } else {
                    vectorProxy.states.add(mergeAndFilterPartitions(equalStates));
                }
            }
        }
    }

    private State mergeAndFilterPartitions(ArrayList<State> equalStates) throws Exception {
        ArrayList<Forest> newCompatibleForests = new ArrayList<>();
        ForestHeap forestHeap = new ForestHeap(equalStates);
        addParetoForestsToList(forestHeap, newCompatibleForests);
        equalStates.get(0).compatibleForests = newCompatibleForests;
        //Diese States werden nicht mehr benötigt -> speicher frei
        //isbesondere wichtig, damit im Falle einer auslagerung die States nicht mehrfach ausgelagert werden
        for (int i = 1; i < equalStates.size(); i++) {
            equalStates.get(i).compatibleForests = null;
            equalStates.get(i).partition = null;
        }
        return equalStates.get(0);
    }

    private void addParetoForestsToList(AbstractHeap heap, ArrayList<Forest> forestOutputList) throws Exception {
        int[] lastWeight = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        while (!heap.isEmpty()) {
            int[] weight = heap.getMinWeight();
            if (!(weight[1] > lastWeight[1] ||
                    weight[1] == lastWeight[1] && weight[0] > lastWeight[0])) {
                //Der nächste Forest aus dem Heap wird *nicht* dominiert -> hinzufügen
                Forest forest = heap.getMinForest();
                forestOutputList.add(forest);
                lastWeight = forest.weight; //darf nicht auf weight gesetzt werden, da sich die inhalte davon ändern
            } else {
                forestCount--;
            }
            heap.removeMinAndAddNext();
            if (Thread.interrupted()) DynamicProgrammingOnNTD.handleInteruption();
        }
    }

    private ArrayList<IntArrayList> combine(ArrayList<IntArrayList> aPartition, ArrayList<IntArrayList> bPartition) {
        //partitionen tauschen, falls bPartition < aPartition
        if (bPartition.size() < aPartition.size()) {
            ArrayList<IntArrayList> tmpPartition = aPartition;
            aPartition = bPartition;
            bPartition = tmpPartition;
        }

        //clpMap initialisieren:
        HashMap<Integer, ClassListPointer> clpMap = new HashMap<>();
        ArrayList<ClassListPointer> classListPointers = new ArrayList<>(aPartition.size());
        for (IntArrayList aClass : aPartition) {
            ClassListPointer clp = new ClassListPointer();
            classListPointers.add(clp);
            clp.originClass = aClass;
            for (int v : aClass) {
                clpMap.put(v, clp);
            }
        }

        for (IntArrayList bClass : bPartition) {
            if (bClass.size() == 1)
                continue; // Die Klasse verbindet nichts und kann zu keinem Kreis führen -> überspringen
            HashSet<Integer> currentClassPointersHash = new HashSet<>();
            ArrayList<IntArrayList> biggestClassList = null;
            for (int v : bClass) {
                ClassListPointer clp = clpMap.get(v);
                if (clp.classList == null) {
                    if (!currentClassPointersHash.add(clp.originClass.getInt(0))) return null;
                    continue;
                }
                if (!currentClassPointersHash.add(clp.classList.get(0).getInt(0))) return null;
                //Über diese Klassen-Iteration die größte Sammlung an verbundenen Klassen finden
                if (biggestClassList == null) {
                    biggestClassList = clp.classList;
                } else {
                    if (clp.classList.size() > biggestClassList.size()) biggestClassList = clp.classList;
                }
            }

            //Falls keine der Klassen dieser Iteration schonmal verbunden wurde, wird eine neue Sammlung an Klassen erstellt
            if (biggestClassList == null) {
                biggestClassList = new ArrayList<>(bClass.size());
            }

            //Setzen der Pointer und classList
            for (int v : bClass) {
                ClassListPointer clp = clpMap.get(v);
                if (clp.classList == null) {
                    biggestClassList.add(clp.originClass);
                    clp.classList = biggestClassList;
                } else {
                    if (clp.classList != biggestClassList) {
                        for (IntArrayList class_ : clp.classList) {
                            biggestClassList.add(class_);
                            clpMap.get(class_.getInt(0)).classList = biggestClassList;
                        }
                    }
                }

            }
        }

        //Die entstandene kreisfreie Partition erstellen
        ArrayList<IntArrayList> newPartition = new ArrayList<>();
        HashSet<Integer> addedFusedClassesFirstInt = new HashSet<>();
        for (ClassListPointer clp : classListPointers) {
            if (clp.classList == null) {
                //Die Klasse wurde nirgends geschnitten (abgesehen von 1er-Klassen),
                // -> also wird sie so hinzugefügt
                newPartition.add(new IntArrayList(clp.originClass));
            } else {
                if (!addedFusedClassesFirstInt.add(clp.classList.get(0).getInt(0))) continue;
                //Die Klasse gehört zu einer Sammlung, welche noch nicht hinzugefügt wurde
                // -> also wird eine vereinigung all dieser Klassen hinzugefügt
                IntArrayList newClass = new IntArrayList();
                for (IntArrayList class_ : clp.classList) {
                    newClass.addAll(class_);
                }
                newPartition.add(newClass);
            }
        }
        return newPartition;
    }
}
