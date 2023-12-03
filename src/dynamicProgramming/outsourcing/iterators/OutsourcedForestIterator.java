package dynamicProgramming.outsourcing.iterators;

import dynamicProgramming.*;
import dynamicProgramming.outsourcing.SpaceManager;

import java.io.*;
import java.nio.ByteBuffer;

public class OutsourcedForestIterator implements ForestIterator{

    private final BufferedInputStream inputStream;

    ByteBuffer byteBuffer = ByteBuffer.allocate(SpaceManager.SURFACE_FORESTS_BYTE_BUFFER_SIZE);

    private boolean hasNextIsOutdated = true;
    private boolean hasNext = false;

    public OutsourcedForestIterator(File forestFile) {
        // Forest Datei
        try {
            inputStream = new BufferedInputStream(new FileInputStream(forestFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        if (hasNextIsOutdated) {
            try {
                byteBuffer = ByteBuffer.allocate(SpaceManager.SURFACE_FORESTS_BYTE_BUFFER_SIZE);
                int readByteCount = inputStream.read(byteBuffer.array());
                hasNext = readByteCount == SpaceManager.SURFACE_FORESTS_BYTE_BUFFER_SIZE;
                hasNextIsOutdated = false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if(!hasNext) {
            close();
        }
        return hasNext;
    }

    @Override
    public Forest next() {
        hasNextIsOutdated = true;
        Forest newForest = new Forest(2);
        newForest.id = byteBuffer.getLong();
        newForest.weight[0] = byteBuffer.getInt();
        newForest.weight[1] = byteBuffer.getInt();
        newForest.isOutsourced = true;
        return newForest;
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
