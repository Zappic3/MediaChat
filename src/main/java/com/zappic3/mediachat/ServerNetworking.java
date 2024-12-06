package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.mojang.text2speech.Narrator.LOGGER;
import static com.zappic3.mediachat.MediaChat.MEDIA_CHANNEL;
import static com.zappic3.mediachat.MediaChat.SERVER_CACHE;
import static com.zappic3.mediachat.NetworkManager.serializeAnimatedGif;
import static com.zappic3.mediachat.NetworkManager.serializeBufferedImage;
import static com.zappic3.mediachat.ServerDownloadManager.requestDownload;
import static com.zappic3.mediachat.ServerDownloadManager.sendImageChunksToPlayers;

public class ServerNetworking {
    private final static Map<UUID, DataAssembler> _uncompleteDataAssemblers = new HashMap<>();

    private ServerNetworking(){}

    public static void registerPackets() {
        MEDIA_CHANNEL.registerServerbound(NetworkManager.ServerboundMediaSyncUploadImagePacket.class, (message, access) -> {
            LOGGER.info("received upload message: "+message);
            DataAssembler assembler = _uncompleteDataAssemblers.computeIfAbsent(message.mediaId(), k -> new DataAssembler(message.totalChunks()));
            assembler.addChunk(message.currentChunk(), message.data());
            // todo check conditions & disrupt upload if needed (e.g. package size)
            if (assembler.isComplete()) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(assembler.assemble())) {
                    BufferedImage image = ImageIO.read(bais);
                    String mediaLocator = "server:" + message.mediaId();
                    SERVER_CACHE.saveMediaToCache(image, mediaLocator.hashCode(), "png");
                    MEDIA_CHANNEL.serverHandle(access.player()).send(new NetworkManager.ClientboundMediaSyncUploadResponsePacket(message.mediaId(), mediaLocator));
                } catch (IOException e) {
                    //todo error handling
                }
            }


        });

        MEDIA_CHANNEL.registerServerbound(NetworkManager.ServerboundMediaSyncRequestDownloadPacket.class, (message, access) -> {
            LOGGER.info("received download message from "+access.player().getName().getString());
            if (SERVER_CACHE.isFileInCache(message.mediaLocation().hashCode())) {
                OneOfTwo<BufferedImage, AnimatedGif> data =  SERVER_CACHE.loadFileFromCache(message.mediaLocation().hashCode());
                if (data.getFirst() != null) {
                    List<DataChunker.Chunk> chunks = new DataChunker(serializeBufferedImage(data.getFirst())).splitIntoChunks();
                    sendImageChunksToPlayers(message.mediaLocation(), chunks, Collections.singletonList(access.player()));
                } else if (data.getSecond() != null) {
                    List<DataChunker.Chunk> chunks = new DataChunker(serializeAnimatedGif(data.getSecond())).splitIntoChunks();
                    sendImageChunksToPlayers(message.mediaLocation(), chunks, Collections.singletonList(access.player()));
                }
            } else {
                CompletableFuture.runAsync(() -> requestDownload(message.mediaLocation(), access.player()));
            }
        });

        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncUploadResponsePacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadImagePacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadGifPacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadErrorPacket.class);
    }
}
