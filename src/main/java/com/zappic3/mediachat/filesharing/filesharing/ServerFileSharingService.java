package com.zappic3.mediachat.filesharing.filesharing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.zappic3.mediachat.DataChunker;
import com.zappic3.mediachat.NetworkManager;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.MEDIA_CHANNEL;

public class ServerFileSharingService extends FileSharingService implements FileSharingService.FileSharingUpload {
    private static final Map<UUID, CompletableFuture<URI>> _runningUploads = new ConcurrentHashMap<>(); // makes it possible to cancel running uploads
    private static final Map<UUID, CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket>> _waitingUploads = new ConcurrentHashMap<>();

    @Override
    protected DownloadedMedia download(URL url) {
        //todo
        return null;
    }

    @Override
    public CompletableFuture<URI> upload(Path filePath) {
        UUID uploadId = UUID.randomUUID();
        CompletableFuture<URI> future =  CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(filePath);
                List<DataChunker.Chunk> chunks = new DataChunker(data).splitIntoChunks();

                CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket> serverResponseFuture = new CompletableFuture<>();
                _waitingUploads.put(uploadId, serverResponseFuture);
                LOGGER.info("Uploading");
                chunks.forEach(chunk -> MEDIA_CHANNEL.clientHandle().send(new NetworkManager.ServerboundMediaSyncUploadImagePacket(uploadId, chunk.getData(), chunk.getChunkIndex(), chunk.getTotalChunks())));
                LOGGER.info("waiting for response");
                NetworkManager.ClientboundMediaSyncUploadResponsePacket serverResponse = serverResponseFuture.get(); // wait for answer from server
                _runningUploads.remove(uploadId);
                LOGGER.info("Upload completed");
                // todo handle possibly received errors

                return new URI(serverResponse.mediaLocator());
            } catch (IOException e) {
                LOGGER.error("error while reading file for server upload", e);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e); // todo error handling
            } catch (URISyntaxException e) {
                LOGGER.error("URI from server response is invalid", e);
            }
            return null;
        });
        _runningUploads.put(uploadId, future);
        return future;
    }

    public static void receiveServerResponsePacket(UUID uploadId, NetworkManager.ClientboundMediaSyncUploadResponsePacket access) {
        CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket> future = _waitingUploads.get(uploadId);
        if (future != null) {
            future.complete(access);
            _waitingUploads.remove(uploadId);
        }

    }
}
