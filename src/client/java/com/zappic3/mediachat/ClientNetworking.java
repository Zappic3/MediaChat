package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.internal.GifSequenceReader;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.filesharing.filesharing.ServerFileSharingService;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.MEDIA_CHANNEL;
import static com.zappic3.mediachat.MediaChatClient.CLIENT_CACHE;
import static com.zappic3.mediachat.Utility.registerTexture;

public class ClientNetworking {
    private final static Map<Integer, DataAssembler> _uncompleteDataAssemblers = new ConcurrentHashMap<>();
    private final static Map<Integer, BiConsumer<ByteArrayInputStream, String>> _registeredReceiver = new ConcurrentHashMap<>();

    private ClientNetworking() {}

    public static void registerReceiver(int packageId, BiConsumer<ByteArrayInputStream, String> receiver) {
        if (_registeredReceiver.containsKey(packageId)) {
            LOGGER.warn("Registering Packet Receiver Failed (An Receiver is already registered)");
        } else {
            _registeredReceiver.put(packageId, receiver);
        }
    }


    public static void registerPackets() {
        MEDIA_CHANNEL.registerClientbound(NetworkManager.ClientboundMediaSyncUploadResponsePacket.class, ((message, access) -> {
            LOGGER.info("received media sync upload success packet");
            ServerFileSharingService.receiveServerResponsePacket(message.mediaId(), message);
        }));

        MEDIA_CHANNEL.registerClientbound(NetworkManager.ClientboundMediaSyncUploadErrorPacket.class, ((message, access) -> {
            LOGGER.info("received media sync upload error packet");
            ServerFileSharingService.receiveServerResponsePacket(message.mediaId(), message);
        }));

        MEDIA_CHANNEL.registerClientbound(NetworkManager.ClientboundMediaSyncDownloadImagePacket.class, ((message, access) -> {
            LOGGER.info("received media sync download packet: \n" + message);
            CompletableFuture.runAsync(() -> {
                DataAssembler assembler = _uncompleteDataAssemblers.computeIfAbsent(message.mediaId(), k -> new DataAssembler(message.totalChunks()));
                assembler.addChunk(message.currentChunk(), message.imageData());

                if (assembler.isComplete()) {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(assembler.assemble())) {
                        if (_registeredReceiver.containsKey(message.mediaId())) {
                            _registeredReceiver.get(message.mediaId()).accept(bais, "image");
                            return;
                        }

                        // default behavior if no receiver is registered
                        BufferedImage image = ImageIO.read(bais);
                        Utility.IdentifierAndSize ias = registerTexture(image, message.mediaId() + "_0");
                        MediaElement.update(message.mediaId(), ias.identifier(), image.getWidth(), image.getHeight(), ias.size());
                        CLIENT_CACHE.saveMediaToCache(image, message.mediaId(), "png");
                        LOGGER.info("updated media element successfully");

                    } catch (IOException e) {
                        LOGGER.error("Could not not read image data send from server", e);
                        MediaElement.update(message.mediaId(), DownloadedMedia.DownloadError.GENERIC);
                    }
                }
            });
        }));

        MEDIA_CHANNEL.registerClientbound(NetworkManager.ClientboundMediaSyncDownloadGifPacket.class, ((message, access) -> {
            LOGGER.info("received media sync download gif packet (" + message.currentChunk() +"/" + message.totalChunks() + "), " + message.gifData().length + " bytes");
            CompletableFuture.runAsync(() -> {
            DataAssembler assembler = _uncompleteDataAssemblers.computeIfAbsent(message.mediaId(), k -> new DataAssembler(message.totalChunks()));
            assembler.addChunk(message.currentChunk(), message.gifData());
                if (assembler.isComplete()) {
                    LOGGER.info("all chunks received");
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(assembler.assemble())) {
                        if (_registeredReceiver.containsKey(message.mediaId())) {
                            _registeredReceiver.get(message.mediaId()).accept(bais, "gif");
                            return;
                        }

                        // default behavior if no receiver is registered
                        GifSequenceReader reader = new GifSequenceReader();
                        int status = reader.read(bais);
                        if (status == 0) {
                            // todo check if this code can be reused together with similar code from the defaultWebDownload class
                            List<Identifier> identifiers = new ArrayList<>();
                            List<Long> delays = new ArrayList<>();
                            long size = 0;
                            int width = -1;
                            int height = -1;
                            for (int i = 0; i < reader.getFrameCount(); i++) {
                                BufferedImage image = reader.getFrame(i);
                                if (width == -1 || height == -1) {
                                    width = image.getWidth();
                                    height = image.getHeight();
                                }

                                Utility.IdentifierAndSize ias = registerTexture(image, message.mediaId() + "_" + i);
                                identifiers.add(ias.identifier());
                                size += ias.size();
                                delays.add((long) reader.getDelay(i));
                            }
                            MediaElement.update(message.mediaId(), identifiers, delays, width, height, size);
                            LOGGER.info("updated media element successfully (gif)");

                        } else {
                            // todo error handling
                            LOGGER.error("Error convert data send from server to gif, error code: {}", status);
                        }


                    } catch (IOException e) {
                        LOGGER.error("Could not not read gif data send from server", e);
                    }
                }
            });
        }));

        MEDIA_CHANNEL.registerClientbound(NetworkManager.ClientboundMediaSyncDownloadErrorPacket.class, ((message, access) -> {
            LOGGER.info("received media sync download error packet");
            MediaElement.update(message.mediaId(), message.error());
        }));
    }
}
