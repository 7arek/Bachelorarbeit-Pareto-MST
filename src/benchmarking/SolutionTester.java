package benchmarking;

import datastructures.graphs.WeightedGraph;
import logging.Logger;
import datastructures.ntd.TransformationFailureException;
import datastructures.DatastructureReaderWriter;
import datastructures.ntd.Ntd;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class SolutionTester {

    static boolean oneTestWasDifferent = false;

    public static void runAllTests(int setup, int repeat, int maxNtdTime, int maxParetoMstTime) throws TransformationFailureException {
        File file = new File("res/correctnessTesting");
        String[] directories = file.list((current, name) -> new File(current, name).isDirectory());
        Arrays.sort(directories);
        for (String test : directories) {
            runTest(test, setup, repeat, false, maxNtdTime, maxParetoMstTime);
        }
        if (oneTestWasDifferent) {
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!");
        }
    }

    private static double runTest(String testName, int setup, int repeat, boolean useGivenNtd, int maxNtdTime, int maxParetoMstTime) throws TransformationFailureException {
        String testDir = "res/correctnessTesting/" + testName;
        double[] testTimes = new double[repeat];

        for (int testNum = 1; testNum <= repeat+setup; testNum++) {

            //LOG
            if (testNum > setup)
                Logger.status(String.format("Running test \"%s\": [%d/%d]\n", testName, testNum-setup, repeat));
            else
                Logger.status(String.format("Running setup \"%s\": [%d/%d]\n", testName, testNum, setup));

            //Test
            Result result = new Result();
            result.safeSolution = true;

            WeightedGraph graph = DatastructureReaderWriter.readWeightedGraph(testDir + "/graph.txt");
            try {
                if (useGivenNtd) {
                    Ntd ntd = DatastructureReaderWriter.readNtd(testDir + "/ntd.txt");
                    Benchmark.benchmarkGivenNtd(graph, ntd, result, maxParetoMstTime);
                } else {
                    Benchmark.benchmark_graph(graph, result, maxNtdTime, maxParetoMstTime);
                }
            } catch (TimeoutException e) {
                if (testNum > setup) {
                    return -1 * 1000;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String untestedSolution = result.solution;
            if (testNum > setup) {
                testTimes[testNum - setup-1] = result.pareto_mst_time.get();
            }

            //Lösungen vergleichen
            boolean isDifferent = false;
            try {
                BufferedReader reader1 = new BufferedReader(new StringReader(untestedSolution));
                BufferedReader reader2 = Files.newBufferedReader(Path.of(testDir + "/solution.txt"));
                long lineNumber = 1;
                String line1 = "", line2 = "";
                while ((line1 = reader1.readLine()) != null) {
                    line2 = reader2.readLine();
                    if (!line1.equals(line2)) {
                        Logger.critical("Lösungen unterschiedlich, Zeile: " + lineNumber + "\n" +
                                line1 + "\n" + line2 + "\n");

                        isDifferent = true;
                    }
                    lineNumber++;
                }
                if (reader2.readLine() != null) {
                    Logger.critical("Lösungen unterschiedlich, 2. Datei hat mehr Zeilen");
                    isDifferent = true;
                }
                if (isDifferent) {
                    oneTestWasDifferent = true;
                    File outputFile = new File(testDir + "/differentSolution.txt");
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
                    writer.write(untestedSolution);
                    writer.close();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Arrays.stream(testTimes).average().orElse(-1);
    }

    /**
     * Führt einen bereits gespeicherten Test aus und vergleicht die Lösungen. <br>
     * Falls die Lösungen nicht übereinstimmen, wird dies im Log angegeben (d.h. in der Konsole, der Datei oder beidem (je nach Einstellung)). <br>
     * @param testName Der Name des gespeicherten Tests: "res/correctnessTesting/" + testName.
     * @param repeat Die Anzahl an gemessenen Ausführungen, aus denen die durchschnittliche Laufzeit berechnet wird.
     * @param useGivenNtd Gibt an, ob für den Test die bereits gespeicherte NTD, oder eine neue, benutzt werden soll.
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer NTD abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @return Die durchschnittliche Laufzeit
     */
    public static double runTest(String testName, int repeat, boolean useGivenNtd, int maxNtdTime, int maxParetoMstTime) throws TransformationFailureException {
        return runTest(testName, 0, repeat, useGivenNtd, maxNtdTime, maxParetoMstTime);
    }

    /**
     * Erstellt einen Test und speichert ihn. <br>
     * Ein Test beinhaltet einen gewichteten Graphen, eine Baumzerlegung und die Lösung (die Pareto-optimalen Spannbäume). <br>
     * Tests eignen sich, um zu überprüfen, ob der ParetoMST Algorithmus nach Verbesserungen immer noch die gleichen
     * Lösungen findet und ob er sie schneller / langsamer findet.
     * @param testName Der Ordnername, in dem der Test gespeichert werden soll: "res/correctnessTesting/" + testName
     * @param graph Der gewichtete Graph, dessen Pareto-optimale Spannbäume gefunden werden sollen.
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     */
    public static void createTest(String testName, WeightedGraph graph, int maxNtdTime, int maxParetoMstTime) throws Exception {
        File testFolder = getFreeFolderName("res/correctnessTesting/" + testName);

        //graph schreiben
        DatastructureReaderWriter.saveWeightedGraph(graph, testFolder + "/graph.txt");

        //Test
        Result result = new Result();
        result.safeSolution = true;

        //Baumzerlegung erstellen
        Ntd ntd = Benchmark.getNtdFromGraph(graph, result, maxNtdTime, true,true);

        //Baumzerlegung speichern
        DatastructureReaderWriter.saveNtd(ntd,testFolder + "/ntd.txt");

        //Lösung berechnen
        String solution = Benchmark.benchmarkGivenNtd(graph, ntd, result, maxParetoMstTime).solution;

        //Lösung schreiben
        File outputFile = new File(testFolder + "/solution.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        writer.write(solution);
        writer.close();
    }

    public static File getFreeFolderName(String folderPath) {
        File file = new File(folderPath);
        if (file.mkdir()) {
            return file;
        } else {
            int i = 1;
            while(true) {
                file = new File(folderPath + "(" + i + ")");
                if (file.mkdir())
                    return file;
                i++;
            }
        }
    }

    /**
     * Führt bereits gespeicherte Tests aus und vergleicht die Lösungen. <br>
     * Die durchschnittlichen Laufzeiten werden im Anschluss alle gesammelt ausgegeben. Dadurch eignet sich diese Methode dazu,
     * die Auswirkung einer Änderung im ParetoMST Algorithmus auf mehrere (Arten) von Graphen auf ein mal zu beobachten. <br>
     * Falls die Lösungen eines Tests nicht übereinstimmen, wird dies im Log angegeben (d.h. in der Konsole, oder der Datei, oder beidem (je nach Einstellung)). <br>
     * @param tests Die Namen der gespeicherten Tests: "res/correctnessTesting/" + testName.
     * @param repeat Die Anzahl an Ausführungen, aus denen die durchschnittliche Laufzeit berechnet wird.
     * @param useGivenNtd Gibt an, ob für die Tests die bereits gespeicherte Baumzerlegung, oder eine neue, benutzt werden soll.
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der Pareto-optimalen Spannbäume abgebrochen wird. 0 bedeutet kein Zeitlimit.
     */
    public static void runMultipleTests(ArrayList<String> tests, int repeat, boolean useGivenNtd, int maxNtdTime, int maxParetoMstTime) throws TransformationFailureException {
        int setup = 0;

        double[] avgTimes = new double[tests.size()];
        for (int i = 0; i < tests.size(); i++) {
            avgTimes[i] = runTest(tests.get(i), setup, repeat, useGivenNtd, maxNtdTime, maxParetoMstTime);
        }
        Logger.time("Durchschnittliche Laufzeiten [Sekunden] der durchgeführten Tests: \n");
        for (int i = 0; i < tests.size(); i++) {
            Logger.time(String.format("%s: %.1f\n", tests.get(i), avgTimes[i] / 1000));
        }
        if (oneTestWasDifferent) {
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!\n");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!\n");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!\n");
            Logger.critical("MIN. EINE LÖSUNG FALSCH!!!!\n");
        }
    }
}
