package benchmarking;

import logging.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ParameterGenerator {
    protected int[] parameters;
    protected static int[] fileParameters;
    protected static String currFilename;

    protected boolean lastTimeAborted = false;

    /**
     *  Erstellt einen Parametergenerator, welcher mit den Parametern Anzahl Knoten = vStart, Anzahl Kanten = (vStart + eOffset) anfängt. <br>
     *  Läuft der Benchmark innerhalb des gegebenen Zeitlimits durch, so wird für den nächsten Durchlauf die Anzahl an
     *  Kanten um eStepSize erhöht. <br>
     *  Läuft der Benchmark innerhalb des gegebenen Zeitlimits nicht durch, so wird die Anzahl an Knoten für den nächsten
     *  durchlauf um vStepSize erhöht und die Anzahl an Kanten auf die momentane Anzahl an Knoten + eOffset "zurückgesetzt" <br>
     *  Falls durch das Erhöhen der Kantenanzahl eine unmögliche Anzahl an Kanten entsteht, so wird die Anzahl an Knoten für den nächsten
     *  durchlauf um vStepSize erhöht, und die Anzahl an Kanten auf die momentane Anzahl an Knoten + eOffset "zurückgesetzt" <br>
     * @param vStart Die Anzahl an Knoten, mit denen wir die erste Testreihe anfangen
     * @param eOffset Den Kanten-Überschuss, mit dem wir jede neue Testreihe (d.h. neue Anzahl an Knoten) anfangen.
     * @param vStepSize Die Anzahl an Knoten, um die wir nach jedem fehlgeschlagenen Durchlauf erhöhen.
     * @param eStepSize Die Anzahl an Kanten, um die wir nach jedem gelungenen Durchlauf erhöhen.
     * @return Der erstellte Parametergenerator.
     */
    public static ParameterGenerator getSuccessiveParameterGenerator(int vStart, int eOffset, int vStepSize, int eStepSize) {
        return new ParameterGenerator() {
            @Override
            public void generateInitialParameters() {
                parameters = new int[]{vStart, vStart + eOffset};
            }

            @Override
            public void generateNextParameters(boolean allRepeatsAborted) throws NoNextParameterException {
                if (allRepeatsAborted) {
//                    if (lastTimeAborted) throw new NoNextParameterException();
                    lastTimeAborted = true;
                    parameters[0] += vStepSize;
                    parameters[1] = parameters[0] + eOffset;

                } else {
                    lastTimeAborted = false;
                    parameters[1] += eStepSize;

                    if (parameters[1] > (((long) parameters[0]) * (parameters[0] - 1)) / 2) {
                        //unmögliche Anzahl an Kanten
                        parameters[0] += vStepSize;
                        parameters[1] = parameters[0] + eOffset;
                    }
                }
            }
        };
    }

    public static ParameterGenerator getOffsetParameterGenerator(int vStart, int vStepSize, int[] eOffsets) {
        return new ParameterGenerator() {
            int eOffsetIndex = 0;

            @Override
            public void generateInitialParameters() {
                parameters = new int[]{vStart, vStart + eOffsets[eOffsetIndex++]};
            }

            @Override
            public void generateNextParameters(boolean allRepeatsAborted) throws NoNextParameterException {
                if (allRepeatsAborted) {
//                    if (lastTimeAborted) throw new NoNextParameterException();
                    lastTimeAborted = true;
                    parameters[0] += vStepSize;
                    eOffsetIndex = 0;
                    parameters[1] = parameters[0] + eOffsets[eOffsetIndex++];

                } else {
                    lastTimeAborted = false;
                    if (eOffsetIndex == eOffsets.length) {
                        eOffsetIndex = 0;
                        parameters[0] += vStepSize;
                    }
                    parameters[1] = parameters[0] + eOffsets[eOffsetIndex++];

                    if (parameters[1] > (((long) parameters[0]) * (parameters[0] - 1)) / 2) {
                        //unmögliche Anzahl an Kanten
                        parameters[0] += vStepSize;
                        eOffsetIndex = 0;
                        parameters[1] = parameters[0] + eOffsets[eOffsetIndex++];
                    }
                }
            }
        };
    }

    /**
     *  Erstellt einen Parametergenerator. <br>
     *  Der Parametergenerator liest die Parameter Zeile für Zeile aus der angegebenen Datei (filename). <br>
     *  Nachdem ein Parameter gelesen wurde, wird dieser aus der Datei gelöscht.<br>
     *  Dadurch, dass während des Manipulierens der Textdatei ein Lock erstellt wird, eignet sich diese Methode auch,
     *  wenn parallel mit mehreren Rechnern gearbeitet wird, welche auf die gleiche Datei zugreifen können.
     * @param filename Der name der Textdatei mit den Parametern.
     * @param selfIncrease Ist selfIncrease = false, so werden nur die Parameter generiert, welche auch in der Datei stehen. <br>
     *                     Ist selfIncrease = true, so wird, je angefangen mit eingelesenen Parametern, immer weiter die
     *                     Anzahl der Kanten um eins erhöht, bis ein Durchlauf fehlschlägt (d.h. er läuft nicht innerhalb der
     *                     angegebenen Zeit durch, oder wirft einen (künstlichen) OOME).
     * @return Der erstellte Parametergenerator.
     */
    public static ParameterGenerator getFileParameterGenerator(String filename, boolean selfIncrease) {
        currFilename = filename;
        return new ParameterGenerator() {
            boolean selfInc = selfIncrease;
            private int[] getParamsFromFile() throws NoNextParameterException {
                File file = new File(filename);
                File lockFile = new File(filename.substring(0,filename.lastIndexOf(".")).concat(".lock"));

                try {
                    List<int[]> arrayList = readParams(file);

                    //abbrechen, falls es keine Parameter mehr gibt
                    if (arrayList.isEmpty()) {
                        fileParameters = null;
                        //lock entfernen
                        long startTime = System.nanoTime();
                        while (!lockFile.delete()) {
                            TimeUnit.SECONDS.sleep(1);
                        }
                        long endTime = System.nanoTime();
                        long elapsedMS = (endTime - startTime)/ 1_000_000;
                        Logger.debug("file brauchte " + elapsedMS + " MS zum löschen\n");
                        throw new NoNextParameterException();
                    }

                    int[] newParams = arrayList.remove(0);

                    ParameterGenerator.writeParamsBack(file,arrayList);

                    return newParams;
                } catch (IOException  e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException("keine neuen Parameter oder I/O Probleme");
            }


            @Override
            public void generateInitialParameters() {
                try {
                    parameters = getParamsFromFile();
                    fileParameters = Arrays.copyOf(parameters, parameters.length);
                } catch (NoNextParameterException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void generateNextParameters(boolean allRepeatsAborted) throws NoNextParameterException {
                if (!selfInc) {
                    parameters = getParamsFromFile();
                    fileParameters = Arrays.copyOf(parameters, parameters.length);
                    return;
                }

                if (allRepeatsAborted) {
                    parameters = getParamsFromFile();
                    fileParameters = Arrays.copyOf(parameters, parameters.length);
                } else {
                    parameters[1]++;
                    if (parameters[1] > (((long) parameters[0]) * (parameters[0] - 1)) / 2) {
                        //unmögliche Anzahl an Kanten
                        parameters = getParamsFromFile();
                        fileParameters = Arrays.copyOf(parameters, parameters.length);
                    }
                }
            }
        };
    }

    public static void saveCurrParams() {
        if (fileParameters == null) {
            return;
        }
        System.out.println("Versuche laufende Parameter abzuspeichern\n");

        File file = new File(currFilename);

        try {

            List<int[]> arrayList = readParams(file);

            arrayList.add(0,fileParameters);

            writeParamsBack(file, arrayList);
            System.out.println("Parameter wurden abgespeichert\n");

        } catch (IOException  e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("keine neuen Parameter oder I/O Probleme");
    }

    private static List<int[]> readParams(File file) throws IOException, InterruptedException {
        File lockFile = new File(file.getName().substring(0,file.getName().lastIndexOf(".")).concat(".lock"));

        long startTime = System.nanoTime();
        while (!lockFile.createNewFile()) {
            TimeUnit.SECONDS.sleep(1);
        }
        long endTime = System.nanoTime();
        long elapsedMS = (endTime - startTime)/ 1_000_000;
        Logger.debug("file war gesperrt für: " + elapsedMS + " MS \n");

        //reader init
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedFileReader = new BufferedReader(fileReader);

        //das array lesen
        List<int[]> arrayList = new ArrayList<>();
        String line;
        while ((line = bufferedFileReader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length == 2) {
                int[] element = new int[2];
                element[0] = Integer.parseInt(parts[0]);
                element[1] = Integer.parseInt(parts[1]);
                arrayList.add(element);
            }
        }
        bufferedFileReader.close();
        fileReader.close();
        return arrayList;
    }

    private static void writeParamsBack(File file, List<int[]> arrayList) throws IOException, InterruptedException {
        int[][] array = new int[arrayList.size()][2];
        for (int i = 0; i < arrayList.size(); i++) {
            array[i] = arrayList.get(i);
        }

        File lockFile = new File(file.getName().substring(0,file.getName().lastIndexOf(".")).concat(".lock"));
        //zurück schreiben
        FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        BufferedWriter bufferedFileWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
        for (int[] element : array) {
            bufferedFileWriter.write(element[0] + "," + element[1] + "\n");
        }

        //lock entfernen
        long startTime = System.nanoTime();
        while (!lockFile.delete()) {
            TimeUnit.SECONDS.sleep(1);
        }
        long endTime = System.nanoTime();
        long elapsedMS = (endTime - startTime)/ 1_000_000;
        Logger.debug("file brauchte " + elapsedMS + " MS zum löschen\n");

        //io close
        bufferedFileWriter.close();
        fileOutputStream.close();
    }

    protected abstract void generateNextParameters(boolean allRepeatsAborted) throws NoNextParameterException;

    protected abstract void generateInitialParameters();

    int[] nextParameters(boolean allRepeatsAborted) throws NoNextParameterException {
        if (parameters == null) {
            generateInitialParameters();
        } else {
            generateNextParameters(allRepeatsAborted);
        }
        return parameters;
    }


    public static class NoNextParameterException extends Exception {
    }
}
