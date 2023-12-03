package benchmarking;

import com.sun.management.OperatingSystemMXBean;
import dynamicProgramming.outsourcing.SpaceManager;
import logging.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class RuntimeHeapWatcher {


    /** Die maximale Menge an Speicher in Megabyte, die für die Speicherverwaltung verwendet werden kann.
     */
    public static long maxHeapMemoryMB;

    /** Der Schwellenwert für die Java-Heap-Nutzung in Megabyte,
     *  ab dem der ParetoMST-Algorithmus seine Berechnungen auslagert
     *  oder die Ausführung abbricht.
     */
    public static int OUTSOURCE_THRESHOLD;

    /** Der Schwellenwert für die Java-Heap-Nutzung in Megabyte,
     *  ab dem der JVM garbage collector explizit aufgerufen wird.
     */
    public static int GC_THRESHOLD;
    private static int maximumNewTestHeapLoad;


    public static Result currentResult;
    public static Thread heapMonitorThread;
    public static boolean preventedOOME = false;

    public static double maxCpuDiff = 0;


    public static int getHeapLoadPercentage() {
        return Math.round((Runtime.getRuntime().totalMemory() / (float) Runtime.getRuntime().maxMemory()) * 100);
    }

    public static void init() {
        Logger.heap("Max Heap Space: " + maxHeapMemoryMB + " MB\n");
        Logger.heap("OUTSOURCE_THRESHOLD: " + OUTSOURCE_THRESHOLD + " MB\n");
        Logger.heap("GC_THRESHOLD: " + GC_THRESHOLD + " MB\n");
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        int load = getHeapLoadPercentage();
        maximumNewTestHeapLoad = Math.max(load, 1) * 3;
        Logger.heap("Anfangs heap Auslastung: " + load + "%\n");
        Logger.heap("setze maximale heap Auslastung für neue Tests auf: " + maximumNewTestHeapLoad + "%\n");

        heapMonitorThread = new Thread(new HeapMonitorTask());
        heapMonitorThread.start();
    }

    public static void forceGc() {
        Logger.status("Rufe gc auf...\n");
        long startTime = System.nanoTime();

        //Spacemanager zurücksetzen
        SpaceManager.init(null);

        System.gc();
        while (getHeapLoadPercentage() > maximumNewTestHeapLoad && (System.nanoTime()-startTime) / 1e9 <= 2 ) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        long endTime = System.nanoTime();
        Logger.heap((String.format(
                "Heap innerhalb von %.3f Sekunden auf eine Auslastung von %d%% gebracht\n",
                (endTime - startTime) / 1_000_000_000.0,
                getHeapLoadPercentage())));
    }

    public static double extractMaxCpuDiff() {
        double result = maxCpuDiff;
        maxCpuDiff = 0;
        return result;
    }

    public static void close() {
        if (heapMonitorThread != null && heapMonitorThread.isAlive()) {
            Logger.status("Schließe heapMonitorThread via hook\n");
            heapMonitorThread.interrupt();
        }
    }
}

class HeapMonitorTask implements Runnable {
    @Override
    public void run() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osMBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long lastTime = System.nanoTime();
        int oomeScore = 0;
        boolean DEBUG_request_reduction = false;
        boolean DEBUG_request_gc = false;
        int DEBUG_reduction_timer = -1;
        double secsSinceLastFlush = 0;

        while (true) {

            if (Thread.interrupted()) break;

            long currentTime = System.nanoTime();
            double elapsedSecs = (currentTime - lastTime) / 1_000_000_000.0;

            //  jede minute logs flushen
            secsSinceLastFlush += elapsedSecs;
            if (secsSinceLastFlush >= 60) {
                Logger.flushAllDataLogs();
                try {
                    Logger.logWriter.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                secsSinceLastFlush = 0;
            }

            //cpu monitor
            double systemCpuLoad = osMBean.getCpuLoad();
            double processCpuLoad = osMBean.getProcessCpuLoad();

            double currCpuDiff = (systemCpuLoad - processCpuLoad) * 100;
            RuntimeHeapWatcher.maxCpuDiff = Math.max(RuntimeHeapWatcher.maxCpuDiff,currCpuDiff);
//            Logger.cpu(String.format("total: %.2f, process: %.2f, diff: %.2f\n",systemCpuLoad*100,processCpuLoad*100,(systemCpuLoad-processCpuLoad)*100));

            if(currCpuDiff>=20) Logger.cpu(String.format("Verdächtige Cpu-Nutzung außerhalb dieses Prozesses: %.2f%%\n",currCpuDiff));

            //wir befinden uns in einer Berechnung
            if (RuntimeHeapWatcher.currentResult != null) {

                int heapUsedMB = (int) (memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));

                if (Logger.logHeapSpam) {
                    long[] outsourcedSize = SpaceManager.getFolderSize(SpaceManager.outsourceFolder.toPath());
                    outsourcedSize[0] /= (1024 * 1024);
                    double sizeCalculationSecs = outsourcedSize[2] / 1_000_0000_000.0;
                    Logger.spam(String.format("elapsed      %.2f     Mem:     %d MB    Out:  %d MB, %d Files, %.3f Secs\n",
                            elapsedSecs, heapUsedMB, outsourcedSize[0],outsourcedSize[1],sizeCalculationSecs));
                }

                if (heapUsedMB >= RuntimeHeapWatcher.OUTSOURCE_THRESHOLD || DEBUG_request_reduction || DEBUG_reduction_timer % 4 == 0) {
                    DEBUG_request_reduction = false;
                    if (!SpaceManager.allowOutsourcing || System.getProperty("user.dir").startsWith("/home/stud/stuckt0/IdeaProjects/ba")) {
                        //AUF DEM POOL RECHNER NICHT AUSLAGERN
                        RuntimeHeapWatcher.preventedOOME = true;
                        Logger.status("OOME erwartet; versuche rechnenden Thread zu stoppen...\n");
                        Logger.critical("Die folgenden Angaben, was die Zeit zum stoppen angeht stimmen ggf. nicht -> an den Timestamps richten\n");
                        Benchmark.shutDownExecutor();
                    } else {
                        SpaceManager.requestReduction();
                    }
                } else if (heapUsedMB >= RuntimeHeapWatcher.GC_THRESHOLD || DEBUG_request_gc) {
                    //GC, falls wir über dem Schwellwert sind
                    Logger.heap("RAM-Auslastung oberhalb vom Schwellwert, Rufe GC auf\n");
                    System.gc();
                    DEBUG_request_gc = false;
                }


                //ggf. OOME verhindern erkennen OOME an zu viel RAM & gc arbeitet & auslagerung arbeitet momentan nicht
                if (elapsedSecs > 3 && heapUsedMB >= 0.95 * (float) RuntimeHeapWatcher.maxHeapMemoryMB && !SpaceManager.reduceSpaceRequested && !SpaceManager.delayedReduceRequest ) {
                    oomeScore += 10;
                } else {
                    oomeScore--;
                }
                if (oomeScore >= 30) {
                    RuntimeHeapWatcher.preventedOOME = true;
                    Logger.status("OOME erwartet; versuche rechnenden Thread zu stoppen...\n");
                    Benchmark.shutDownExecutor();
                    oomeScore = 0;
                }

                RuntimeHeapWatcher.currentResult.pareto_mst_max_heap_usage = Math.max(RuntimeHeapWatcher.currentResult.pareto_mst_max_heap_usage, heapUsedMB);
//                if(Logger.logHeapSpam) Logger.writeToLog(    String.format("elapsed      %.2f     Mem:     %d MB   Forests: %d\n", elapsedSecs , heapUsedMB, Forest.forestCount),false);
            }
            lastTime = currentTime;

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Logger.status("Heap Monitor Thread gestoppt\n");
                break;
            }
        }
    }
}
