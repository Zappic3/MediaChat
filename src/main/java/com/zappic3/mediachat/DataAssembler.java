package com.zappic3.mediachat;

import java.util.HashMap;
import java.util.Map;

/**
 * Assembles Data Chunks from the {@link DataChunker}
 * into their original form
 */
public class DataAssembler {
    private final Map<Integer, byte[]> chunks = new HashMap<>();
    private final int expectedChunks;
    private int totalSize = 0;

    public DataAssembler(int expectedChunks) {
        this.expectedChunks = expectedChunks;
    }

    public void addChunk(int chunkIndex, byte[] data) {
        if (!chunks.containsKey(chunkIndex)) {
            chunks.put(chunkIndex, data);
            totalSize += data.length;
        }
    }

    public boolean isComplete() {
        return chunks.size() == expectedChunks;
    }

    public byte[] assemble() {
        if (!isComplete()) {
            throw new IllegalStateException("Data is incomplete. Not all chunks have been received.");
        }

        byte[] completeData = new byte[totalSize];
        int offset = 0;

        for (int i = 0; i < expectedChunks; i++) {
            byte[] chunk = chunks.get(i);
            System.arraycopy(chunk, 0, completeData, offset, chunk.length);
            offset += chunk.length;
        }

        return completeData;
    }
}
