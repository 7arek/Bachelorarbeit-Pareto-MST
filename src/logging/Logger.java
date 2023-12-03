package logging;

import datastructures.DatastructureReaderWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class Logger {

    /**
     * Gibt an, ob der Logger aktiviert ist.
     */
    public static boolean logging;

    /**
     * Gibt an, ob Informationen zu den Zeitmessungen ausgegeben werden sollen.
     */
    public static boolean logTimes;

    /**
     * Gibt an, ob Informationen zum Java Heap ausgegeben werden sollen.
     */
    public static boolean logHeap;

    /**
     * Gibt an, ob Informationen zur CPU-Auslastung *anderer* Programme ausgegeben werden sollen, um sicherzustellen, dass Ausreißer nicht durch spontane Updates oder ähnliche Ereignisse verursacht wurden.
     */
    public static boolean logCpu;

    /**
     * Gibt an, ob Informationen zum aktuellen Status des Programms ausgegeben werden sollen.
     */
    public static boolean logStatus;

    /**
     * Gibt an, ob Informationen zur Vorbehandlung der Baumzerlegung ausgegeben werden sollen.
     */
    public static boolean logSimulating;

    /**
     * Gibt an, ob *viele* Informationen zum Java Heap ausgegeben werden sollen.
     */
    public static boolean logHeapSpam;

    /**
     * Gibt an, ob Informationen zur Auslagerung des ParetoMST-Algorithmus ausgegeben werden sollen.
     */
    public static boolean logOutsourcing;

    /**
     * Gibt an, ob Informationen zu von Benchmark erstellten Baumzerlegungen ausgegeben werden sollen.
     */
    public static boolean logNtdInfos;

    /**
     * Gibt an, ob Informationen zu von Benchmarks erstellten Graphen ausgegeben werden sollen.
     */
    public static boolean logGraphInfos;

    /**
     * Gibt an, ob Informationen zum aktuellen Debug-Problem ausgegeben werden sollen (wird normalerweise nicht benötigt).
     */
    public static boolean logDebug;

    /**
     * Gibt an, ob zu allen Log-Nachrichten die momentane Zeit angegeben werden soll.
     */
    public static boolean logTimeStamps;

    /**
     * Legt den Ausgabe-Modus für den Logger fest (Konsole und / oder in einer Datei). <br>
     * Möglich sind:
     * <li> Nichts Ausgeben:  NO_LOGGING = 0 </li>
     * <li> Auf der Konsole Ausgeben:  CONSOLE_LOGGING = 1 </li>
     * <li> In eine Datei ausgeben: FILE_LOGGING = 2</li>
     * <li> In eine Datei und auf der Konsole ausgeben:  CONSOLE_AND_FILE_LOGGING = 3 </li>
     */
    public static int loggingOutputMode;
    public static BufferedWriter logWriter;

    /**Gibt an, ob die "groben" Daten des ParetoMST-Algorithmus in der Datei "first.txt" gespeichert werden sollen. <br>
     * Das umfasst z.B. Die Laufzeit, die maximale RAM nutzung, die Anzahl der Lösungen
     */
    public static boolean datalogParetoMst;

    /**
     * Gibt an, ob die Daten zu den Graphen und Baumzerlegungen in der Datei "second.txt" gespeichert werden sollen. <br>
     * Das umfasst z.B. Die Laufzeit der Baumzerlegungs-Erstellung, die Baumweite der Baumzerlegung, die Anzahl an Knoten des Graphen
     */
    public static boolean datalogGraphs;

    /** Gibt an, ob detaillierte Daten des ParetoMST-Algorithmus in der Datei "third.txt" gespeichert werden sollen. <br>
     * Das umfasst Informationen zum Stand des Algorithmus während des durchlaufens der Baumzerlegung,
     * z.B. die Anzahl an Zuständen, die Anzahl an Lösungen, der momentane Node-Typ.
     *
     */
    public static boolean datalogNodes;

    public static final int NO_LOGGING = 0;
    public static final int CONSOLE_LOGGING = 1;
    public static final int FILE_LOGGING = 2;
    public static final int CONSOLE_AND_FILE_LOGGING = 3;

    public static boolean logIsOnNewLine = true;

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");


    public static HashMap<String, BufferedWriter> dataLogWriters = new HashMap<>();

    public static void status(String message) {
        if(logStatus) writeToLog("STATUS", message, false);
    }
    public static void time(String message) {
        if(logTimes) writeToLog("TIME  ", message, false);
    }

    public static void graph(String message) {
        if(logGraphInfos) writeToLog("GRAPH", message, false);
    }

    public static void spam(String message) {
        if(logHeapSpam) writeToLog("SPAM  ", message, false);
    }

    public static void outsourcing(String message) {
        if(logOutsourcing) writeToLog("OUTSRC", message, false);
    }
    public static void heap(String message) {
        if(logHeap) writeToLog("HEAP  ", message, false);
    }
    public static void cpu(String message) {
        if(logCpu) writeToLog("CPU   ", message, false);
    }
    public static void ntd(String message) {
        if(logNtdInfos) writeToLog("NTD   ", message, false);
    }
    public static void simulate(String message) {
        if(logSimulating) writeToLog("SIMLTR", message, false);
    }
    public static void debug(String message) {
        if(logDebug) writeToLog("DEBUG ", message, true);
    }

    public static void critical(String message) {
        writeToLog("CRITCL", message, true);
    }

    private static void writeToLog(String prefix, String message, boolean isCritical) {
        if(!Logger.logging) return;
        message = prefix.concat("\t").concat(message);
        if (logTimeStamps && logIsOnNewLine) {
            message = dateTimeFormatter.format(LocalDateTime.now()).concat("\t").concat(message);
        }
        logIsOnNewLine = message.endsWith("\n");
        try {
            if(loggingOutputMode == FILE_LOGGING || loggingOutputMode == CONSOLE_AND_FILE_LOGGING)
                logWriter.write(message);
            if(loggingOutputMode == CONSOLE_LOGGING || loggingOutputMode == CONSOLE_AND_FILE_LOGGING)
                if (isCritical) {
                    System.out.flush();
                    System.err.print(message);
                    System.err.flush();
                    Thread.sleep(50); //damit die err message vor den folgenden out messages kommt
                } else {
                    System.out.print(message);
                }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void writeToDataLog(String dataLogName,String message) {
        BufferedWriter dataLog = dataLogWriters.get(dataLogName);
        if(dataLog == null) return;
        try {
            dataLog.write(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static BufferedWriter createNewLogWriter(String fileName) {
        try {
            File outputFile = DatastructureReaderWriter.getFreeFileName(fileName);

            return new BufferedWriter(new FileWriter(outputFile));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initLogger(int loggingOutputMode, boolean logTimes, boolean logHeap, boolean logCpu,boolean logStatus, boolean logSimulating, boolean logHeapSpam, boolean logOutsourcing, boolean logNtdInfos, boolean logGraphInfos, boolean logTimeStamps, boolean logDebug) {
        Logger.logging = (loggingOutputMode != NO_LOGGING);
        Logger.loggingOutputMode = loggingOutputMode;
        Logger.logTimes = logTimes;
        Logger.logHeap = logHeap;
        Logger.logCpu = logCpu;
        Logger.logHeapSpam = logHeapSpam;
        Logger.logStatus = logStatus;
        Logger.logSimulating = logSimulating;
        Logger.logOutsourcing = logOutsourcing;
        Logger.logNtdInfos = logNtdInfos;
        Logger.logGraphInfos = logGraphInfos;
        Logger.logTimeStamps = logTimeStamps;
        Logger.logDebug = logDebug;

        initLoggerNoSettings();
    }

    public static void initLoggerNoSettings() {
        if (logging) {
            if (loggingOutputMode == FILE_LOGGING || loggingOutputMode == CONSOLE_AND_FILE_LOGGING) {
                String logFolder = "output/logs";
                new File(logFolder).mkdirs();
                logWriter = createNewLogWriter(logFolder + "/log.txt");
            }
        }

        if(datalogParetoMst)
            Logger.initDataLogger("first");
        if(datalogGraphs)
            Logger.initDataLogger("second");
        if(datalogNodes)
            Logger.initDataLogger("third");

        Logger.writeToDataLog("first", "trial_nr,graph_nr,estimated_time,pareto_mst_time,solution_count,pareto_mst_max_heap_usage,abort_reason,outsourced,maxCpuDiff\n");
        Logger.writeToDataLog("second", "trial_nr,graph_nr,graph_num_vertices,graph_num_edges,ntd_tw,ntd_num_nodes,ntd_num_join_nodes,jd_total_time,better_root_time,root_count,maxCpuDiff\n");
        Logger.writeToDataLog("third", "trial_nr,graph_nr," +
                "bag_size,bell_nr,vi,ei,fi," +
                "f_node_type,s_node_type,i_node_type," +
                "first_partition_count,second_partition_count,i_partition_count," +
                "first_forest_count,second_forest_count,i_forest_count," +
                "node_time\n");
    }

    public static void initDataLogger(String name) {
        String logFolder = "output/dataLogs";
        new File(logFolder).mkdirs();
        dataLogWriters.put(name, createNewLogWriter(logFolder + "/" + name + ".txt"));
    }

    public static void closeAllLogs() {
        //DataLogs
        for (BufferedWriter writer : Logger.dataLogWriters.values()) {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ignored) {}
        }

        //Log
        try {
            if (Logger.logWriter != null) {
                Logger.logWriter.close();
            }
        } catch (IOException ignored) {}
    }

    public static void flushAllDataLogs() {
        for (BufferedWriter writer : Logger.dataLogWriters.values()) {
            try {
                if (writer != null) {
                    writer.flush();
                }
            } catch (IOException ignored) {}
        }
    }
}
