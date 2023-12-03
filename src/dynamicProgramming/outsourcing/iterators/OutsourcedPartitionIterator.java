package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.Forest;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.outsourcing.SpaceManager;
import dynamicProgramming.State;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import datastructures.ntd.Ntd;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;

import static dynamicProgramming.outsourcing.SpaceManager.SURFACE_FORESTS_BYTE_BUFFER_SIZE;

public class OutsourcedPartitionIterator implements PartitionIterator {

    private final MstStateVectorProxy vectorProxy;

    public Path currentFilePath;

    BufferedOutputStream lastAddedStateFileStream;

    public State currentState = new State();

    final int[] treeIndexInverse;

    private final DirectoryStream<Path> directoryStream;
    private final Iterator<Path> iterator;

    private final boolean checkForDuplicates;

    public OutsourcedPartitionIterator(MstStateVectorProxy vectorProxy, File statesFolder) {
        this.vectorProxy = vectorProxy;

        //bei introduce entstehen keine dopplungen
        this.checkForDuplicates = (SpaceManager.currentNode != null && SpaceManager.currentNode.getNodeType() != Ntd.NodeType.INTRODUCE);

        //  den File Stream erstellen
        Path folderPath = statesFolder.toPath();
        try {
            directoryStream = Files.newDirectoryStream(folderPath);
            iterator = directoryStream.iterator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //  die TreeIndex Datei einlesen
        treeIndexInverse = SpaceManager.getTreeIndexInverse(vectorProxy);
    }


    public OutsourcedPartitionIterator(MstStateVectorProxy vectorProxy) {
        this(vectorProxy, vectorProxy.unprocessedStatesFolder);
    }

    @Override
    public boolean hasNext() {
        if (iterator.hasNext()) return true;

        //  Falls hasNext false zurück gibt, wird automatisch der DirectoryStream geschlossen
        close();
        return false;
    }

    @Override
    public ArrayList<IntArrayList> next() {
        currentFilePath = iterator.next();

        // Aus dem Dateinamen die Partition wiederherstellen
        currentState.partition = SpaceManager.loadPartitionFromPath(currentFilePath, treeIndexInverse);

        return currentState.partition;
    }


    @Override
    public void remove() {
        //Forget: aus dem notIterated Ordner löschen, bzw. nicht in den standard Ordner verschieben
    }

    /**
     * Für Edge: den State zum standard Ordner hinzufügen.
     * Außerdem werden alle folgenden forests durch saveForest-Aufrufe in dieser Datei gespeichert
     */
    @Override
    public void addState(State state) {
        //Edge: den State zum standard Ordner hinzufügen
        try {
            if (lastAddedStateFileStream != null)
                lastAddedStateFileStream.close();


            //  im standard Ordner den State unter einem nicht vergebenen Namen anlegen
            File tmpStateFile = new File(vectorProxy.statesFolder + "/unnamedState.txt");

            //  die State Datei entsprechend umbenennen
            File newStateFile = new File(vectorProxy.statesFolder + "/" + SpaceManager.getHashCodeString(state.partition) + ".txt");
            File renamedNewStateFile = SpaceManager.moveStateFile(tmpStateFile, newStateFile, true);

            //Für saveForest einen Writer erstellen
            lastAddedStateFileStream = new BufferedOutputStream((new FileOutputStream(renamedNewStateFile, true)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveForest(Forest forest) {
        //Origin-Forest Eintrag machen
        SpaceManager.outsourceOriginForest(forest);

        //Surface Forest in lastAddedStateFile schreiben
        SpaceManager.writeSurfaceForestToStream(lastAddedStateFileStream, forest);
    }

    @Override
    public int getCompatibleForestsSize() {
        try {
            return (int) (Files.size(currentFilePath) / SURFACE_FORESTS_BYTE_BUFFER_SIZE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ForestIterator getForestIterator() {
        return new OutsourcedForestIterator(currentFilePath.toFile());
    }

    @Override
    public State getCurrentState() {
        return currentState;
    }

    @Override
    public void processedPartition(String hashCodeString) {
        //Ausgelagert
        //  -Edge: Datei verschieben
        //  -Forget,Introduce: Datei umbenennen&verschieben
        //  -Join current_sv: Datei zu oldStates verschieben
        //  -Join second_current_sv: Datei verschieben, falls noch nicht in oldStates (das falls passiert in moveState, da currentPath = wantedPath)

        if (lastAddedStateFileStream != null) {
            try {
                lastAddedStateFileStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //  alter File
        File oldStateFile = currentFilePath.toFile();

        //  neuer File
        File newStateFile;
        if (SpaceManager.currentNode.getNodeType() == Ntd.NodeType.JOIN) {
            //bei join kommen die danach in oldStates, da in states die neuen Lösungen rein kommen
            newStateFile = new File(vectorProxy.oldStatesFolder + "/" + hashCodeString + ".txt");
        } else {
            newStateFile = new File(vectorProxy.statesFolder + "/" + hashCodeString + ".txt");
        }

        //verschieben (und umbenennen)
        SpaceManager.moveStateFile(oldStateFile, newStateFile, checkForDuplicates);
    }

    @Override
    public void close() {
        try {
            directoryStream.close();
            if (lastAddedStateFileStream != null) lastAddedStateFileStream.close();
            currentState = null;
            vectorProxy.currentPartitionIterator = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processedPartition() {
        processedPartition(SpaceManager.getHashCodeString(currentState.partition));
    }

    public short getUnmodifiedBracketCode() {
        String fileName = String.valueOf(currentFilePath.getFileName());
        return Short.parseShort(fileName.substring(0, fileName.indexOf(",")));
    }

    public long getUnmodifiedNumberCode() {
        String fileName = String.valueOf(currentFilePath.getFileName());
        return Long.parseLong(fileName.substring(fileName.indexOf(",") + 1, fileName.lastIndexOf(".")));
    }

}
