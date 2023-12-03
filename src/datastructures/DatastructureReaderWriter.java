package datastructures;

import datastructures.graphs.WeightedGraph;
import datastructures.ntd.Ntd;
import datastructures.ntd.NtdNode;
import logging.Logger;

import java.io.*;
import java.util.*;

import static datastructures.ntd.Ntd.NodeType.*;

public class DatastructureReaderWriter {


    public static void saveNtd(Ntd ntd, String outputFileName) {
        try {
            File outputFile = getFreeFileName(outputFileName);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            //basic infos ausgeben

            writer.write(String.format("c #Nodes #JoinNodes tw\nntd %d %d %d\ngraph %d %d\nc #graphKnoten #graphKanten\n",
                    ntd.numberOfNodes, ntd.numberOfJoinNodes, ntd.tw,
                    ntd.numberOfVertices, ntd.numberOfEdges));


            //Nodes post-Order ausgeben, damit beim Erstellen direkt die Kinder gesetzt werden können
            int currentID = 1;
            HashMap<NtdNode, Integer> id = new HashMap<>();

            Set<NtdNode> visited = new HashSet<>();
            Stack<NtdNode> stack = new Stack<>();
            stack.push(ntd.getRoot());

            // post-order DFS
            while (!stack.isEmpty()) {
                NtdNode node = stack.peek();
                if (visited.contains(node)) {
                    stack.pop();
                    //id zuweisen
                    id.put(node, currentID);

                    //Node ausgeben
                    //-Bag inhalt schreiben
                    StringBuilder sb = new StringBuilder();
                    sb.append("b ").append(currentID);

                    for (Integer vertex : node.bag) {
                        sb.append(" ").append(vertex);
                    }
                    //-Node infos schreiben
                    switch (node.getNodeType()) {
                        case LEAF -> sb.append(" l");
                        case INTRODUCE -> sb.append(" i ").append(node.specialVertex).append(" ").append(id.get(node.firstChild));
                        case FORGET -> sb.append(" f ").append(node.specialVertex).append(" ").append(id.get(node.firstChild));
                        case EDGE -> sb.append(" e ").append(node.specialVertex).append(" ").append(node.secondSpecialVertex).append(" ").append(id.get(node.firstChild));
                        case JOIN -> sb.append(" j ").append(id.get(node.firstChild)).append(" ").append(id.get(node.secondChild));
                    }
                    sb.append("\n");
                    //-Alles zusammen ausgeben
                    writer.write(sb.toString());
                    currentID++;

                    continue;
                }
                visited.add(node);
                if (node.getFirstChild() != null) {
                    stack.push(node.getFirstChild());
                    if(node.getSecondChild() != null)
                        stack.push(node.getSecondChild());
                }
            }

            writer.close();

        } catch (IOException e) {
            Logger.critical("Ein Fehler ist aufgetreten\n");
            e.printStackTrace();
        }
    }

    public static Ntd readNtd(String inputFileName) {
        File inputFile = new File(inputFileName);

        Ntd ntd = new Ntd();
        ArrayList<NtdNode> nodes = new ArrayList<>();

        int checkSum = 0; //schauen, ob alle Nodes erstellt wurden (sollte eigentlich unnötig sein)

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("c")) {
                    // Kommentar-Linien ignorieren
                    continue;
                } else if (line.startsWith("ntd")) {

                    // #Nodes #JoinNodes tw auslesen
                    String[] args = line.split(" ");
                    ntd.numberOfNodes = Integer.parseInt(args[1]);
                    ntd.numberOfJoinNodes = Integer.parseInt(args[2]);
                    ntd.tw = Integer.parseInt(args[3]);

                    for (int i = 0; i < ntd.numberOfNodes; i++) {
                        nodes.add(new NtdNode());
                    }

                } else if (line.startsWith("graph")) {

                    //#graphKnoten #graphKanten auslesen
                    String[] args = line.split(" ");
                    ntd.numberOfVertices = Integer.parseInt(args[1]);
                    ntd.numberOfEdges = Integer.parseInt(args[2]);

                } else {

                    // Node auslesen und erstellen
                    String[] args = line.split(" ");
                    Set<Integer> bag = new HashSet<>();
                    int id = Integer.parseInt(args[1]);
                    int i = 2; //b und ID überspringen

                    //-Bag inhalt auslesen und erstellen
                    while (true) {
                        try {
                            bag.add(Integer.parseInt(args[i]));
                            i++;
                        } catch (NumberFormatException e) {
                            break;
                        }
                    }

                    //-Node Informationen auslesen und Node erstellen
                    switch (args[i]) {
                        case "l" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).nodeType = LEAF;
                            checkSum++;
                        }
                        case "i" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).specialVertex = Integer.parseInt(args[i + 1]);
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = INTRODUCE;
                            checkSum++;
                        }
                        case "f" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).specialVertex = Integer.parseInt(args[i + 1]);
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = FORGET;
                            if(bag.isEmpty())
                                ntd.root = nodes.get(id - 1);
                            checkSum++;
                        }
                        case "j" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 1]) - 1);
                            nodes.get(id - 1).secondChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = JOIN;
                            checkSum++;
                        }
                        case "e" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).specialVertex = Integer.parseInt(args[i + 1]);
                            nodes.get(id - 1).secondSpecialVertex = Integer.parseInt(args[i + 2]);
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 3]) - 1);
                            nodes.get(id - 1).nodeType = EDGE;
                            checkSum++;
                        }
                    }

                }
            }

        } catch (Exception e) {
            Logger.critical("ERROR: Die NTD Konnte nicht erstellt werden\n");
            e.printStackTrace();
            System.exit(1);
        }
        if (checkSum != ntd.numberOfNodes) {
            Logger.critical("ERROR: Die NTD wurde nicht korrekt ausgegeben\n");
            System.exit(1);
        }
        return ntd;
    }

    public static void saveWeightedGraph(WeightedGraph weightedGraph, String outputFileName) {
        try {
            File outputFile = getFreeFileName(outputFileName);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            //basic infos ausgeben
            writer.write(String.format("wg %d %d %d\n",
                    weightedGraph.jd_graph.getCopyOfVertices().size(), weightedGraph.weightDimension, weightedGraph.jd_graph.getNumberOfEdges()));

            for (Integer u : weightedGraph.jd_graph.getCopyOfVertices()) {
                for (Integer v : weightedGraph.jd_graph.getNeighborhood(u)) {
                    if (u.compareTo(v) > 0) continue;
                    writer.write(u + " " + v);
                    for (int weight : weightedGraph.getWeight(u, v)) {
                        writer.write(" " + weight);
                    }
                    writer.write("\n");
                }
            }

            writer.close();

        } catch (IOException e) {
            Logger.critical("Ein Fehler ist aufgetreten\n");
            e.printStackTrace();
        }
    }

    public static WeightedGraph readWeightedGraph(String inputFileName) {
        File inputFile = new File(inputFileName);
        WeightedGraph weightedGraph = null;

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            String line;
            int vertexCount;
            int weightDimension = 0;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("c")) {
                    // Kommentar-Linien Ignorieren
                    continue;
                } else if (line.startsWith("wg")) {

                    // vertexCount und weightDimension auslesen
                    String[] args = line.split(" ");
                    vertexCount = Integer.parseInt(args[1]);
                    weightDimension = Integer.parseInt(args[2]);

                    //weightedGraph initialisieren
                    weightedGraph = new WeightedGraph(weightDimension);
                    for (int i = 1; i <= vertexCount; i++) {
                        weightedGraph.addVertex(i);
                    }

                } else {

                    // Kante auslesen und erstellen
                    String[] args = line.split(" ");
                    int u = Integer.parseInt(args[0]);
                    int v = Integer.parseInt(args[1]);
                    int[] weight = new int[weightDimension];

                    for (int i = 0; i < weightDimension; i++) {
                        weight[i] = Integer.parseInt(args[i + 2]);
                    }
                    assert weightedGraph != null;
                    weightedGraph.addWeightedEdge(u,v,weight);

                }
            }

        } catch (Exception e) {
            Logger.critical("ERROR: weighted Graph Konnte nicht erstellt werden\n");
            e.printStackTrace();
            System.exit(1);
        }

        return weightedGraph;
    }

    public static File getFreeFileName(String filePath) {
        try {
            File file = new File(filePath);
            if (file.createNewFile()) {
                return file;
            } else {
                int i = 1;
                int lastDotIndex = filePath.lastIndexOf('.');
                String fileName = filePath.substring(0, lastDotIndex);
                String fileExtension = filePath.substring(lastDotIndex);
                while(true) {
                    file = new File(fileName + "(" + i + ")" + fileExtension);
                    if (file.createNewFile())
                        return file;
                    i++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
