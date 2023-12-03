package main;

import benchmarking.Benchmark;
import benchmarking.ParameterGenerator;
import benchmarking.RuntimeHeapWatcher;
import benchmarking.SolutionTester;
import dynamicProgramming.outsourcing.SpaceManager;
import datastructures.graphs.GraphCreator;
import datastructures.graphs.WeightedGraph;
import logging.Logger;
import datastructures.DatastructureReaderWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Sandbox {
    //----------------------------------------------------------------------------------------------------------
    //In dieser Klasse werden die wichtigsten Anwendungsfälle der Implementierung exemplarisch dargestellt.
    //  -settings() wird am Anfang des Programms aufgerufen und legt Einstellungen fest.
    //  -Anschließend wird run() aufgerufen. Hier sollen Benchmarks gestartet werden usw..
    //  -Die Methoden example_* stellen exemplarisch Anwendungsfälle dar.
    //----------------------------------------------------------------------------------------------------------


    public static void settings(){
        //Diese Methode wird zu Beginn der Ausführung aufgerufen und legt Einstellungen fest.
        //Detaillierte Informationen befinden sich in den Dokumentationen der jeweiligen Variablen.

        //Die Log-Einstellung sind für die Konsolenausgabe (bzw. dessen Speicherung) wichtig.

        //Die DataLog-Einstellungen sind für das Sammeln von Daten zur Analyse des Algorithmus wichtig
        //  (mit den DataLogs wurden die Daten zu den Anzahlen an Zuständen, Lösungen, usw. gesammelt)

        //Die Auslagerungs-Einstellungen sind wichtig für die Auslagerung.
        //  Hier wird z.B. eingestellt wo die Daten gespeichert werden und ab wann ausgelagert werden soll.



        //Log Einstellungen.

        Logger.logging = true;
        Logger.loggingOutputMode = Logger.CONSOLE_AND_FILE_LOGGING;
        Logger.logTimes = true;
        Logger.logHeap = true;
        Logger.logCpu = false;
        Logger.logHeapSpam = false;
        Logger.logStatus = true;
        Logger.logSimulating = false;
        Logger.logOutsourcing = true;
        Logger.logNtdInfos = true;
        Logger.logGraphInfos = true;
        Logger.logTimeStamps = true;

        Logger.logDebug = false;

        //DataLog Einstellungen.
        //Die DataLogs werden in "output/dataLogs" gespeichert.
        //In der Implementierungs-Phase wurden immer wieder unterschiedliche Daten gemessen, wodurch die Dateinamen
        //der DataLogs nicht aussagekräftig sind (first.csv, second.csv, third.csv).
        //Bei jeder erneuten Ausführung des Programms werden neue Dateien erstellt.
        //Damit die alten Dateien nicht überschrieben werden, erhalten neue Dateien einen suffix, z.B. (1), (2),...

        //Die Informationen der verschiedenen DataLogs lassen sich über die Primär- bzw. Fremdschlüssel "trial_nr" und
        //"graph_nr" in Verbindung setzen.
        //Es sei zu beachten, dass die DataLogs hauptsächlich für die Nutzung eines
        //"Benchmark.benchmark_ParameterGenerator()" Aufrufs ausgelegt sind. Bei Benutzung mehrerer, oder anderer methoden
        //kann es sein, dass "trial_nr" und / oder "graph_nr" nicht richtig gesetzt werden.

        Logger.datalogParetoMst = true;
        Logger.datalogGraphs = true;
        Logger.datalogNodes = true;


        // Auslagerungs-Einstellungen.
        SpaceManager.outsourceFolder = new File("C:\\BA_ParetoMST_outsourcedSpace");
        SpaceManager.allowOutsourcing = true;

        RuntimeHeapWatcher.OUTSOURCE_THRESHOLD = (int) ((RuntimeHeapWatcher.maxHeapMemoryMB) * 0.8);
        RuntimeHeapWatcher.GC_THRESHOLD = (int) (RuntimeHeapWatcher.OUTSOURCE_THRESHOLD * 0.8);

        // Sonstige Einstellungen.
        Benchmark.saveGraphAndNtd = false;
    }

    public static void run() throws Exception {
        //Diese Methode wird nach Ausführung von settings() aufgerufen. Hier sollten die Benchmarks gestartet werden.
        //Unterhalb dieser Methode werden die wichtigsten Funktionen exemplarisch dargestellt und dokumentiert.

        example_benchmark();
//        example_multipleBenchmarks();
//        example_test();
//        example_multipleTests();


    }

    private static void example_benchmark() {
        //Beispielcode: Wir erstellen / laden einen gewichteten Graphen

        WeightedGraph wg;

        //Variante: Gespeicherten Graphen laden
        wg = DatastructureReaderWriter.readWeightedGraph("res/correctnessTesting/v10_e20_sameWeight/graph.txt");

        //Variante: Graph neu erstellen
        wg = GraphCreator.generateRandomWeightedGraph(20, 25);

        //In den folgenden drei Aufrufen werden verschiedene Daten gemessen

        //Erklärung: Siehe Dokumentation der Methode
        Benchmark.benchmark_graph(wg,0,0);

        //Erklärung: Siehe Dokumentation der Methode
        Benchmark.benchmark_ntd_AllRoots(wg,null,0,0);

        //Erklärung: Siehe Dokumentation der Methode
        Benchmark.benchmark_ntd_Multiple(wg, 10, 0, 0);
    }

    private static void example_multipleBenchmarks() throws IOException {
        //Beispielcode:Wir erstellen *mehrere* gewichtete Graphen,

        //Erklärung:
        //Siehe "Benchmark.benchmark_ParameterGenerator" Dokumentation
        //Siehe ParameterGenerator.getSuccessiveParameterGenerator() Dokumentation
        //Siehe ParameterGenerator.getFileParameterGenerator() Dokumentation


        // Ein ParameterGenerator liefert dem Benchmark die "Parameter" für neue Graphen.
        // Mit Parametern sind jeweils die Anzahl an Knoten und Kanten der Graphen gemeint.
        ParameterGenerator parameterGenerator;

        //Variante successiveParameterGenerator.
        //Erklärung: Siehe Dokumentation der Methode
        parameterGenerator = ParameterGenerator.getSuccessiveParameterGenerator(15,-1,5,2);

        //Variante fileParameterGenerator.
        //Erklärung: Siehe Dokumentation der Methode
        parameterGenerator = ParameterGenerator.getFileParameterGenerator("beispielParameters.txt",true);


        //Die verschiedenen Laufzeiten können wir ähnlich wie in "example_benchmark()" messen.
        //Erklärung: Siehe Dokumentation der Methode
        Benchmark.benchmark_ParameterGenerator("beispielsBenchmark",
                0, 5*60,
                Benchmark.MODE.DEFAULT, 10,
                parameterGenerator);


        //Weiteres:
        //Damit der getFileParameterGenerator von mehreren Prozessen gleichzeitig verwendet werden kann, wird
        //während der Bearbeitung der Datei mit den Parametern eine .lock Datei erstellt
        // (durch die .lock Datei wissen andere Prozesse, dass die Datei mit den Parametern momentan bearbeitet wird).
        //
        //Falls das Programm beim Einlesen eines neuen Parameters (mittels getFileParameterGenerator) abgebrochen wird,
        //so kann es sein, dass die .lock Datei nicht gelöscht wird und das Programm bei nächster Ausführung
        //auf das Löschen dieser Datei wartet. In diesem Fall muss die entsprechende .lock datei von Hand gelöscht werden.
        //
        //Die Datei befindet sich im working-directory und heißt FILENAME.lock, wobei FILENAME dem angegebenen
        //Dateinamen entspricht. In diesem Beispiel also "beispielParameters.lock".
    }

    private static void example_test() throws Exception {
        //Beispielcode: Wir erstellen einen Test und führen diesen anschließend durch.

        //Wichtiges:
        // Falls ein Testname schon vergeben ist, wird der Test mit einem suffix gespeichert,
        // z.B. "beispielsTests(1)" oder "beispielsTests(2)",....

        //Wir erstellen einen Test. Erklärung: Siehe Dokumentation der Methode
        SolutionTester.createTest("beispielsTest", GraphCreator.generateRandomWeightedGraph(20, 72), 0, 0);

        //Wir führen diesen Test durch. Erklärung: Siehe Dokumentation der Methode
        SolutionTester.runTest("beispielsTest", 3, true, 0, 0);
    }

    private static void example_multipleTests() throws Exception {
        //Beispielcode: Wir erstellen *mehrere* Tests und führen diese anschließend durch.

        //Weiteres:
        //Ähnlich zu example_tests(), jedoch werden die durchschnittlichen Laufzeiten im Anschluss alle gesammelt ausgegeben.
        // Dadurch eignet sich diese Methode dazu, die Auswirkung einer Änderung im Pareto-MST Algorithmus auf
        // mehrere (Arten) von Graphen auf einmal zu beobachten.
        //
        // Falls ein Testname schon vergeben ist, wird der Test unter einem leicht anderen Namen gespeichert,
        // z.B. "v15_e60(1)" oder "v15_e60(2)",....


        //Wir erstellen je einen Test. Erklärung: Siehe Dokumentation der Methode
        SolutionTester.createTest("beispiel_v15_e60", GraphCreator.generateRandomWeightedGraph(15, 60), 0, 0);
        SolutionTester.createTest("beispiel_v15_e63", GraphCreator.generateRandomWeightedGraph(15, 63), 0, 0);
        SolutionTester.createTest("beispiel_v15_e65", GraphCreator.generateRandomWeightedGraph(15, 65), 0, 0);


        //Wir führen diese Tests durch. Erklärung: Siehe Dokumentation der Methode
        SolutionTester.runMultipleTests(new ArrayList<>(Arrays.asList("beispiel_v15_e60","beispiel_v15_e63","beispiel_v15_e65")), 3, true, 0, 0);
    }

}
