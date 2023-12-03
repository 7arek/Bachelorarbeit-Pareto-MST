package benchmarking;

import benchmarking.calculators.*;
import datastructures.DatastructureReaderWriter;
import dynamicProgramming.outsourcing.iterators.PartitionIterator;
import dynamicProgramming.outsourcing.MstStateVectorProxy;
import dynamicProgramming.outsourcing.SpaceManager;
import datastructures.graphs.GraphCreator;
import datastructures.graphs.WeightedGraph;
import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import jdrasil.graph.TreeDecomposition;
import logging.Logger;
import datastructures.ntd.*;
import rootChoosing.Simulator;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;


public class Benchmark {

    /** Gibt an, ob die Graphen und Baumzerlegungen, welche innerhalb der Benchmark Methoden automatisch erstellt werden,
     * gespeichert werden sollen. <br>
     * Das Speichern kann nützlich sein, um z.B. Ausreißer im Nachhinein zu untersuchen. <br>
     * Die Graphen und Baumzerlegungen werden standardmäßig in "output/benchmarks/defaultBenchmarkFolder" gespeichert. <br>
     * Während der Ausführung von {@link #benchmark_ParameterGenerator} werden die Graphen und Baumzerlegungen in einem gesonderten Ordner gespeichert.
     *
     */
    public static boolean saveGraphAndNtd = false;

    public static int trial_nr;
    public static int graph_nr;

    public static ExecutorService executor;
    public static Future<Object> futureObject;

    public static Thread currentExecutionThread;

    //Benchmark einstellungen
    public static String currentTrialFolder = "output/benchmarks/defaultBenchmarkFolder";

    public enum MODE {
        DEFAULT,
        ALL_ROOTS,
        MULTIPLE_NTD
    }

    /** Führt den ParetoMST Algorithmus auf mehreren zufälligen Graphen aus.
     *  Die Ergebnisse werden (solange in den Einstellungen aktiviert) in den dataLogs in "output/dataLogs" und "output/logs" gespeichert.
     *
     * @param benchmarkName Der Ordnername, in dem die getesteten Graphen und Baumzerlegungen gespeichert werden, also "output/benchmarks/ANGEGEBENER_NAME" (falls in den Einstellungen aktiviert).
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param mode Benchmark "Modus": <br>
     *             DEFAULT generiert eine Baumzerlegung, versucht anhand des Schätzers die beste Wurzel dieser Baumzerlegung zu finden und berechnet anschließend die Pareto-optimalen Spannbäume mit dieser Baumzerlegung, gewurzelt in der bestimmten Wurzel.<br>
     *             ALL_ROOTS generiert eine Baumzerlegung, bestimmt für alle Wurzeln dieser Baumzerlegung die ParetoMST-Laufzeit-Schätzwerte und berechnet anschließend die Pareto-optimalen Spannbäume mit dieser Baumzerlegung, ausgehend von allen möglichen Wurzeln.<br>
     *             MULTIPLE_NTD generiert ntdCount viele Baumzerlegungen, versucht je anhand des Schätzers die beste Wurzel der jeweiligen Baumzerlegung zu finden und berechnet anschließend für jede Baumzerlegung, gewurzelt in der jeweils bestimmten Wurzel, die Pareto-optimalen Spannbäume.
     * @param ntdCount Die Anzahl an zu generierenden Baumzerlegungen im Modus MULTIPLE_NTD
     * @param parameterGenerator Der Parameter Generator, welcher benutzt werden soll um die Anzahl an Knoten und Kanten der zufälligen Graphen zu bestimmen
     */
    public static void benchmark_ParameterGenerator(String benchmarkName, int maxNtdTime, int maxParetoMstTime,MODE mode,int ntdCount, ParameterGenerator parameterGenerator) throws IOException {
        Logger.status("Starte benchmark_ParameterGenerator\n");
        int repeat = 1;

        if(mode!=MODE.DEFAULT)
            repeat = 1;

        currentTrialFolder = "output/benchmarks/" + benchmarkName;
        new File(currentTrialFolder).mkdirs();

        trial_nr = 0;
        graph_nr = 0;

        boolean allRepeatsAborted = false;

        while (true) {

            //Parameter generieren
            int[] parameters;
            try {
                parameters = parameterGenerator.nextParameters(allRepeatsAborted);
            } catch (ParameterGenerator.NoNextParameterException e) {
                //Test hört auf
                Logger.status("--Alle Parameter durch--\n");
                break;
            }

            //Logging
            if (Logger.logging) {
                if(Logger.loggingOutputMode == Logger.FILE_LOGGING || Logger.loggingOutputMode == Logger.CONSOLE_AND_FILE_LOGGING)
                    Logger.logWriter.flush();
                Logger.status("--------TRIAL NUMBER: " + (trial_nr+1) +"----- Parameters: "+ Arrays.toString(parameters) +"\n");
            }


            //Zufälligen Graphen erstellen
            WeightedGraph randomWeightedGraph = GraphCreator.generateRandomWeightedGraph(parameters[0], parameters[1]);

            //Graph ggf. speichern
            if (Benchmark.saveGraphAndNtd) {
                DatastructureReaderWriter.saveWeightedGraph(randomWeightedGraph,
                        String.format("%s/graph_n%d_m%d.txt",
                                currentTrialFolder,parameters[0],parameters[1]));
            }

            allRepeatsAborted = true;

            for (int i = 1; i <= repeat; i++) {
                Result result = new Result();
                try {

                    result.graph_num_vertices = parameters[0];
                    result.graph_num_edges = parameters[1];




                    //benchmark starten
                    switch (mode) {
                        case DEFAULT -> {
                            Benchmark.benchmark_graph(randomWeightedGraph, result, maxNtdTime, maxParetoMstTime);
                            allRepeatsAborted = false;
                        }
                        case ALL_ROOTS -> {
                            allRepeatsAborted = Benchmark.benchmark_ntd_AllRoots(randomWeightedGraph,null,result,maxNtdTime,maxParetoMstTime);
                        }
                        case MULTIPLE_NTD -> {
                            allRepeatsAborted = Benchmark.benchmark_ntd_Multiple(randomWeightedGraph,ntdCount,result,maxNtdTime,maxParetoMstTime);
                        }
                    }
                    Logger.flushAllDataLogs();

                } catch (Exception | OutOfMemoryError e) {
                    Logger.flushAllDataLogs();
                    if (handleExceptions(e, result)) {
                        i--;
                        continue;
                    }
                }
            }
        }
        currentTrialFolder = "output/benchmarks/defaultBenchmarkFolder";
    }


    /** Führt den ParetoMST Algorithmus folgendermaßen auf dem angegebenen Graphen aus:
     *  Bestimmt für alle Wurzeln einer Baumzerlegung die ParetoMST-Laufzeit-Schätzwerte und berechnet anschließend die
     *  Pareto-optimalen Spannbäume mit dieser Baumzerlegung, ausgehend von allen möglichen Wurzeln.<br>
     *  Die Ergebnisse werden (solange in den Einstellungen aktiviert) in den (data)Logs in "output/dataLogs" und "output/logs" gespeichert.<br>
     * @param weightedGraph Der Graph dessen Pareto-optimalen Spannbäume berechnet werden sollen
     * @param ntd Eine zugehörige Baumzerlegung (Falls ntd == null, wird eine Baumzerlegung berechnet. Ansonsten wird mit der angegebenen Baumzerlegung gearbeitet.)
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     */
    public static void benchmark_ntd_AllRoots(WeightedGraph weightedGraph, Ntd ntd, int maxNtdTime, int maxParetoMstTime)  {
        Logger.status("Starte benchmark_ntd_AllRoots\n");
        Result result = new Result();
        try {
            benchmark_ntd_AllRoots(weightedGraph, ntd, result, maxNtdTime, maxParetoMstTime);
        } catch (Throwable e) {
            handleExceptions(e, result);
        }
    }

    static boolean benchmark_ntd_AllRoots(WeightedGraph weightedGraph, Ntd ntd, Result result, int maxNtdTime, int maxParetoMstTime) throws Exception {
        graph_nr++;
        if(ntd == null) ntd = getNtdFromGraph(weightedGraph, result, maxNtdTime, false,false);


        Simulator simulator = new Simulator(ntd);

        long startTime = System.nanoTime();
        HashMap<NtdNode, BigInteger> estimatedTimeMap = simulator.estimateAllRoots();
        long endTime = System.nanoTime();
        long elapsedMS = (endTime - startTime)/ 1_000_000;
        result.better_root_time.set((int) elapsedMS);
        result.root_count = estimatedTimeMap.size();

        //dataLog
        Logger.writeToDataLog("second",String.format(Locale.US,"%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f\n",
                trial_nr,graph_nr,
                ntd.numberOfVertices, ntd.numberOfEdges, ntd.tw, ntd.numberOfNodes, ntd.numberOfJoinNodes,
                result.jd_total_time,result.better_root_time.get(),result.root_count,
                RuntimeHeapWatcher.extractMaxCpuDiff()));

        boolean minOneRootTimeout = false;

        ArrayList<Map.Entry<NtdNode, BigInteger>> roots = new ArrayList<>(estimatedTimeMap.entrySet());
        roots.sort(Map.Entry.comparingByValue());

        ArrayList<BigInteger> ranTimes = new ArrayList<>();

        for (Map.Entry<NtdNode, BigInteger> entry : roots) {
            if (!ranTimes.isEmpty() && (ranTimes.get(ranTimes.size()-1).subtract(entry.getValue()).abs().compareTo(ranTimes.get(ranTimes.size()-1).divide(BigInteger.valueOf(20))) <= 0)) { //Abweichung <= 5 %
//                    Logger.writeToLog("BenchmarkAllRoots: skipping, because of\n " + entry.getValue() + "\n"+ ranTimes.get(ranTimes.size()-1) + "\n" ,false);
                continue;
            }
            ranTimes.add(entry.getValue());
            Logger.status("BenchmarkAllRoots: wähle NTD mit Schätzwert: " + entry.getValue() + "\n");
            NtdNode root = entry.getKey();
            trial_nr++;
            Ntd ntdCopy = NtdTransformer.copyNtd(ntd, root);

            Result tmpResult = new Result(result);
            tmpResult.estimated_time = entry.getValue();


            //ParetoMST berechnen
            try {
                benchmarkGivenNtd(weightedGraph, ntdCopy, tmpResult, maxParetoMstTime);
            } catch (Exception | OutOfMemoryError e) {
                if (e instanceof TimeoutException) {
                    minOneRootTimeout = true;
                }
                Logger.flushAllDataLogs();
                handleExceptions(e, tmpResult);
            }
        }
        return minOneRootTimeout;
    }

    /** Führt den ParetoMST Algorithmus folgendermaßen auf dem angegebenen Graphen aus:
     *  Generiert ntdCount viele Baumzerlegungen, versucht jeweils anhand des Schätzers die beste Wurzel der jeweiligen Baumzerlegung zu finden und berechnet anschließend für jede Baumzerlegung, gewurzelt in der jeweils bestimmten Wurzel, die Pareto-optimalen Spannbäume.<br>
     *  Die Ergebnisse werden (solange in den Einstellungen aktiviert) in den (data)Logs in "output/dataLogs" und "output/logs" gespeichert.<br>
     * @param weightedGraph Der Graph dessen Pareto-optimalen Spannbäume berechnet werden sollen
     * @param ntdCount Die Anzahl an zu generierenden Baumzerlegungen
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     */
    public static void benchmark_ntd_Multiple(WeightedGraph weightedGraph, int ntdCount, int maxNtdTime, int maxParetoMstTime) {
        Logger.status("Starte benchmark_ntd_Multiple\n");
        Result result = new Result();
        try {
            benchmark_ntd_Multiple(weightedGraph, ntdCount, result, maxNtdTime, maxParetoMstTime);
        } catch (Throwable e) {
            handleExceptions(e, result);
        }
    }

    static boolean benchmark_ntd_Multiple(WeightedGraph weightedGraph, int ntdCount, Result result, int maxNtdTime, int maxParetoMstTime) throws Exception {
        graph_nr++;
        if(result == null) result = new Result();
        boolean minOneNtdTimeout = false;

        for (int i = 0; i < ntdCount;i++) {
            trial_nr++;

            Result trialResult = new Result(result);

            Ntd ntd = getNtdFromGraph(weightedGraph, trialResult, maxNtdTime, true,true);

            //ParetoMST berechnen
            try {
                benchmarkGivenNtd(weightedGraph, ntd, trialResult, maxParetoMstTime);
                trialResult.abortReason = "none";
            } catch (Exception | OutOfMemoryError e) {
                if (e instanceof  TimeoutException)
                    minOneNtdTimeout = true;
                Logger.flushAllDataLogs();
                handleExceptions(e, trialResult);
            }

        }
        return minOneNtdTimeout;
    }

    /** Führt den ParetoMST Algorithmus auf dem angegebenen Graphen aus:
     *  Generiert eine Baumzerlegung, versucht anhand des Schätzers die beste Wurzel dieser Baumzerlegung zu finden und berechnet anschließend die Pareto-optimalen
     *  Spannbäume mit dieser Baumzerlegung, gewurzelt in der bestimmten Wurzel.<br>
     *  Die Ergebnisse werden (solange in den Einstellungen aktiviert) in den (data)Logs in "output/dataLogs" und "output/logs" gespeichert.<br>
     * @param weightedGraph Der Graph dessen Pareto-optimalen Spannbäume berechnet werden sollen
     * @param maxNtdTime Zeit in Sekunden, bis die Erstellung einer Baumzerlegung abgebrochen wird. 0 bedeutet kein Zeitlimit.
     * @param maxParetoMstTime Zeit in Sekunden, bis die Berechnung der ParetoMST abgebrochen wird. 0 bedeutet kein Zeitlimit.
     */
    public static void benchmark_graph(WeightedGraph weightedGraph, int maxNtdTime, int maxParetoMstTime) {
        Logger.status("Starte benchmark_graph\n");
        Result result = new Result();
        try {
            benchmark_graph(weightedGraph,result, maxNtdTime, maxParetoMstTime);
        } catch (Throwable e) {
            handleExceptions(e, result);
        }
    }

    static Result benchmark_graph(WeightedGraph weightedGraph, Result result, int maxNtdTime, int maxParetoMstTime) throws Exception {
        graph_nr++;
        trial_nr++;
        //Ntd Erstellung und Benchmark
        Ntd ntd = getNtdFromGraph(weightedGraph, result, maxNtdTime, true,true);

        //ParetoMST berechnen
        benchmarkGivenNtd(weightedGraph, ntd, result, maxParetoMstTime);
        return result;
    }

    public static Ntd getNtdFromGraph(WeightedGraph weightedGraph, Result result, int maxNtdTime, boolean simulate, boolean writeSecond) throws Exception {
        Logger.graph("#Knoten #Kanten\n");
        Logger.graph(String.format("wg %d %d\n",
                weightedGraph.jd_graph.getCopyOfVertices().size(), weightedGraph.jd_graph.getNumberOfEdges()));

        Logger.heap("Heap auslastung:" + RuntimeHeapWatcher.getHeapLoadPercentage()+"%\n");
        Logger.status("Berechne NTD\n");

        //max cpu Diff zurücksetzten
        RuntimeHeapWatcher.extractMaxCpuDiff();

        //jd_td erstellen
        TreeDecomposition<Integer> jd_td = (TreeDecomposition<Integer>) runCalculator(
                new Jd_td_Calculator(weightedGraph, result),
                maxNtdTime,
                "(JD) tree decomposition berechnet\n");

        //jd_ntd erstellen
        NiceTreeDecomposition<Integer> jd_ntd = (NiceTreeDecomposition<Integer>) runCalculator(
                new Jd_ntd_Calculator(jd_td, result),
                maxNtdTime - result.jd_td_time.get(),
                "(JD) nice tree decomposition berechnet\n"
        );


        //jd_ntd zu meiner ntd
        Ntd ntd = (Ntd) Objects.requireNonNull(runCalculator(
                new My_ntd_Calculator(jd_ntd, result),
                -1,
                "JD ntd in meine ntd überführt\n"));


        //meine ntd: Die beste Wurzel schätzen
        Ntd betterNtd = ntd;
        if (simulate) {
            Logger.status("Schätze Laufzeiten...\n");

            betterNtd = (Ntd) runCalculator(new Better_root_Calculator(ntd, result),
                    0,
                    null);

            Logger.status("Beste geschätzte Laufzeit: " + result.estimated_time + "\n");
        }

        //result
        result.jd_total_time = result.jd_td_time.get() + result.jd_ntd_time.get();


        //LOG
        Logger.time("jd_total_time, jd_td_time, jd_ntd_time, (my_ntd_time)\n");
        Logger.time(String.format("%d [%d %d (%d)]\n",
                result.jd_total_time, result.jd_td_time.get(), result.jd_ntd_time.get(), result.my_ntd_time.get()));

        Logger.ntd("#Nodes #JoinNodes width\n");
        Logger.ntd(String.format("ntd %d %d %d\n",
                betterNtd.numberOfNodes, betterNtd.numberOfJoinNodes, betterNtd.tw));


        //NTD speichern
        if (Benchmark.saveGraphAndNtd) {
            DatastructureReaderWriter.saveNtd(betterNtd,
                    String.format("%s/ntd_n%d_m%d_tw%d_n%d_j%d.txt",
                            currentTrialFolder,
                            weightedGraph.jd_graph.getNumVertices(),weightedGraph.jd_graph.getNumberOfEdges(),
                            betterNtd.tw,betterNtd.numberOfNodes,betterNtd.numberOfJoinNodes));
        }


        //result
        result.ntd_tw = betterNtd.tw;
        result.ntd_num_nodes = betterNtd.numberOfNodes;
        result.ntd_num_join_nodes = betterNtd.numberOfJoinNodes;

        result.graph_num_edges = betterNtd.numberOfEdges;
        result.graph_num_vertices = betterNtd.numberOfVertices;

        //dataLog
        if (writeSecond) {
            Logger.writeToDataLog("second",String.format(Locale.US,"%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f\n",
                    trial_nr,graph_nr,
                    betterNtd.numberOfVertices, betterNtd.numberOfEdges, betterNtd.tw, betterNtd.numberOfNodes, betterNtd.numberOfJoinNodes,
                    result.jd_total_time,result.better_root_time.get(),result.root_count,
                    RuntimeHeapWatcher.extractMaxCpuDiff()));
        }

        return betterNtd;
    }

    public static Result benchmarkGivenNtd(WeightedGraph weightedGraph, Ntd ntd, Result result, int maxParetoMstTime) throws Exception {
        RuntimeHeapWatcher.currentResult = result;

        Logger.heap("Heap auslastung:" + RuntimeHeapWatcher.getHeapLoadPercentage()+"%\n");

        //max cpu Diff zurücksetzten
        RuntimeHeapWatcher.extractMaxCpuDiff();

        Logger.status("Berechne ParetoMST\n");

        ntd.computeTreeIndex();

        MstStateVectorProxy vectorProxy = (MstStateVectorProxy) runCalculator(
                new Pareto_mst_Calculator(result, weightedGraph, ntd),
                maxParetoMstTime,
                Logger.logTimes ? null : "ParetoMST berechnet\n"
        );

        RuntimeHeapWatcher.currentResult = null;

        PartitionIterator partitionIterator = vectorProxy.getPartitionIterator(vectorProxy.statesFolder);
        partitionIterator.next();
        int solutionCount = partitionIterator.getCompatibleForestsSize();

        int runtimeMs = result.pareto_mst_time.get();
        String timeString = "";
        long hours = TimeUnit.MILLISECONDS.toHours(runtimeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(runtimeMs) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(runtimeMs));
        long milliseconds = runtimeMs -
                TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(runtimeMs));
        double seconds = milliseconds / 1000f;
        if (hours != 0) timeString += hours + "h ";
        if (minutes != 0) timeString += minutes + "m ";
        if (seconds != 0) timeString += String.format("%.1fs",seconds);



        Logger.time("ParetoMST: Zeit | Anzahl Lösungen | Max. Heap Nutzung (MB)\n");
        Logger.time(String.format("%s   | %d | %d\n", timeString, solutionCount, result.pareto_mst_max_heap_usage));

        if (result.safeSolution)
            result.solution = SolutionPrinter.solutionToString(vectorProxy);
        result.solutionCount = solutionCount;

        //Heap für den nächsten aufruf aufräumen
        RuntimeHeapWatcher.forceGc();
        //Speicher für den nächsten Aufruf aufräumen
        SpaceManager.deleteOutsourcedData();

        //dataLog
        result.abortReason = "none";
        Logger.writeToDataLog("first",String.format(Locale.US,"%d,%d,%d,%d,%d,%d,%s,%s,%.2f\n",
                trial_nr, graph_nr,
                result.estimated_time,result.pareto_mst_time.get(),
                result.solutionCount,
                result.pareto_mst_max_heap_usage,
                result.abortReason,
                result.outsourced ? "YES" : "NO",
                RuntimeHeapWatcher.extractMaxCpuDiff()));

        return result;
    }

    private static Object runCalculator(Calculator calculator, int maxTime, String logStatus) throws Exception {

        executor = Executors.newSingleThreadExecutor();

        Callable<Object> callable = () -> {
            currentExecutionThread = Thread.currentThread();

            Object object = calculator.timeCalculation();

            if(logStatus != null)
                Logger.status(logStatus);

            return object;
        };

        futureObject = executor.submit(callable);

        try {
            Object object = (maxTime <= 0) ? futureObject.get() : futureObject.get(maxTime, TimeUnit.SECONDS);
            executor.shutdownNow();
            futureObject = null;
            if (object == null) throw new Exception();
            return object;
        } catch (TimeoutException e) {
            Logger.status("Die Berechnung hat die maximale Laufzeit von " + maxTime + " Sekunden überschritten, stoppe Thread...\n");
            shutDownExecutor();
            throw e;
        } catch (Exception e) {
            //Abfragen, ob wegen Speicher die interrupted Flag gesetzt wurde
            if (RuntimeHeapWatcher.preventedOOME) {
                RuntimeHeapWatcher.preventedOOME = false;
                shutDownExecutor();
                Logger.critical("OOME wurde verhindert\n");
                throw new OutOfMemoryError("artificial");
            }
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Logger.critical("\n\n\n" + sw + "\n\n\n");
            if (!executor.isShutdown()) {
                shutDownExecutor();
            } else {
                RuntimeHeapWatcher.forceGc();
                SpaceManager.deleteOutsourcedData();
            }
            throw e;
        }
    }

    public static boolean handleExceptions(Object e, Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            Throwable t = (Throwable) e;
            t.printStackTrace(pw);

        } catch (Exception ignored) {
            Logger.critical("HandleException: Konnte nicht casten\n");
            Logger.critical("\n\n" + sw);
        }

        if (e instanceof OutOfMemoryError oome) {
            Logger.critical("\n\n\nERROR: (artificial) OOME\n\n\n");
            Logger.critical("\n\n" + sw);
            //Heap voll während Berechnung
            if (((OutOfMemoryError) e).getMessage().equals("artificial")) {
                result.abortReason = "ARTIFICIAL_OOME";
            } else {
                result.abortReason = "OOME";
            }

            Logger.critical(Arrays.toString(oome.getStackTrace()));
            oome.printStackTrace();



        } else if (e instanceof TransformationFailureException) {
            //Ntd falsch übertragen (bzw. JDrasil hat eine falsche NTD erzeugt)

//                    result.abortReason = "FalscheUebertragung";
            Logger.critical("\n\n\nERROR: FALSCHE UEBERTRAGUNG\n\n\n");
            Logger.critical("\n\n" + sw);
            //bei Falscher übertragung wird der Test nicht gezählt
            return true;
        } else if (e instanceof TimeoutException) {
            //Eine Berechnung hat zu lange gebraucht
            result.abortReason = "TimeOut";
        } else {
            Logger.critical("\n\n\nERROR: Unbekannter Fehler\n\n\n");
            Logger.critical("\n\n" + sw);
            result.abortReason = "some_Reason";
        }
        Logger.writeToDataLog("first",String.format(Locale.US,"%d,%d,%d,%d,%d,%d,%s,%s,%.2f\n",
                trial_nr, graph_nr,
                result.estimated_time,result.pareto_mst_time.get(),
                result.solutionCount,
                result.pareto_mst_max_heap_usage,
                result.abortReason,
                result.outsourced ? "YES" : "NO",
                RuntimeHeapWatcher.extractMaxCpuDiff()));
        return false;
    }

    public static void shutDownExecutor() {
        futureObject.cancel(true);
        executor.shutdownNow();
        try {
            long startTime = System.nanoTime();
            executor.awaitTermination(1, TimeUnit.DAYS);
            long endTime = System.nanoTime();
            Logger.status(String.format("Thread innerhalb von %.3f Sekunden gestoppt\n", (endTime-startTime) / 1_000_000_000.0));
            RuntimeHeapWatcher.forceGc();
            SpaceManager.deleteOutsourcedData();
            futureObject = null;
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }


}
