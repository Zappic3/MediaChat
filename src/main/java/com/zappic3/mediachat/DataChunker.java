package com.zappic3.mediachat;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to break up large blocks of data into smaller chunks
 * that are below the Minecraft networking packet size limit
 */
public class DataChunker {
    private final byte[] data;
    private final int chunkSize;

    public DataChunker(byte[] data, int chunkSize) {
        this.data = data;
        this.chunkSize = chunkSize;
    }

    public DataChunker(byte[] data) {
        this.data = data;
        this.chunkSize = 30000; //a little bit smaller than Minecraft max packet size of 2^15.
    }

    public List<Chunk> splitIntoChunks() {
        List<Chunk> chunks = new ArrayList<>();
        int totalChunks = (int) Math.ceil((double) data.length / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(data.length, start + chunkSize);
            byte[] chunkData = new byte[end - start];
            System.arraycopy(data, start, chunkData, 0, chunkData.length);

            chunks.add(new Chunk(totalChunks, i, chunkData));
        }

        return chunks;
    }

    public static class Chunk {
        private final int totalChunks;
        private final int chunkIndex;
        private final byte[] data;

        public Chunk(int totalChunks, int chunkIndex, byte[] data) {
            this.totalChunks = totalChunks;
            this.chunkIndex = chunkIndex;
            this.data = data;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public byte[] getData() {
            return data;
        }
    }
}
