package main;

import benchmarking.*;
import dynamicProgramming.outsourcing.SpaceManager;
import logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static boolean forceOutsource = false;


    public static void main(String[] args) throws Exception {

        //Alles Mögliche initialisieren
        init();
        Logger.status("Main init fertig\n\n");

        //  Custom Code ausführen und bei einer Exception die Threads, Logger, usw. entsprechend beenden
        try {
            Sandbox.run();
            //  Logger
            Logger.status("Code vollständig durchgelaufen\n\n");


            //Anderen Thread stoppen, damit der shutdownhook greift
            RuntimeHeapWatcher.close();


        } catch (Throwable throwable) {
            Logger.critical("!!!Irgendein Fehler ist aufgetreten!!!\n!!!Irgendein Fehler ist aufgetreten!!!\n!!!Irgendein Fehler ist aufgetreten!!!\n\n");

            //Anderen Thread stoppen, damit der shutdownhook greift
            RuntimeHeapWatcher.close();

            //StackTrace in den Logger schreiben
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            Logger.critical("\n\n\n" + sw+ "\n\n\n");

            throw throwable;
        }
    }


    private static void init() {
        //Ordner erstellen
        new File(Benchmark.currentTrialFolder).mkdirs();

        //Max heap bestimmen
        RuntimeHeapWatcher.maxHeapMemoryMB = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024);

        //Einstellungen laden
        Sandbox.settings();

        //  Logger initialisieren
        Logger.initLoggerNoSettings();

        //  Heap Thread initialisieren
        RuntimeHeapWatcher.init();

        //  shutdown hook initialisieren
        Thread hook = new Thread(() -> {
            RuntimeHeapWatcher.close();
            SpaceManager.deleteOutsourcedData();

            Logger.status("Schließe alle writer via hook\n");
            Logger.closeAllLogs();
//            ParameterGenerator.saveCurrParams();
        });
        Runtime.getRuntime().addShutdownHook(hook);

        //  Auslagerung initialisieren
        String userDir = System.getProperty("user.dir");
        if (userDir.startsWith("C:\\Users\\Tarek\\")) {
            SpaceManager.outsourceFolder = new File("C:\\Users\\Tarek\\BA_outsourcedSpace");
        } else if (userDir.startsWith("/home/tarek/ba")) { //Abteilungs Server
            SpaceManager.outsourceFolder = new File("/home/tarek/ba/BA_outsourcedSpace");
        } else if (userDir.startsWith("/home/stud/stuckt0/IdeaProjects/ba")) { //Pool Rechner
            SpaceManager.outsourceFolder = new File("/home/stud/stuckt0/IdeaProjects/ba/BA_outsourcedSpace");
        } else {
            if (SpaceManager.outsourceFolder == null)
                throw new RuntimeException("Der outsourceFolder kann nicht null sein");
            // else: es wurde durch settings() gesetzt
        }
        Logger.outsourcing("Auslagerungs Ordner: " + SpaceManager.outsourceFolder + "\n");

        //checken, ob der Ordner leer ist, ansonsten als Schutz abbrechen
        try {
            if (!SpaceManager.outsourceFolder.mkdirs()) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(SpaceManager.outsourceFolder.toPath());
                boolean isNotEmpty = ds.iterator().hasNext();
                ds.close();
                if (isNotEmpty) {
                    //Der Ordner ist nicht leer
                    Logger.critical("Der Auslagerungs-Ordner ist *nicht* leer. Abbruch, um nicht aus Versehen wichtige Daten zu löschen\n");
                    Logger.critical("Bitte gebe einen anderen Ordner an, oder lösche alle Dateien innerhalb des Auslagerungs-Ordners\n");
                    SpaceManager.outsourceFolder = null;
                    System.exit(3345);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}