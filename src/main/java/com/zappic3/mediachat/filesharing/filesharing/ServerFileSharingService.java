package com.zappic3.mediachat.filesharing.filesharing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

import static com.zappic3.mediachat.MediaChat.*;

public class ServerFileSharingService extends FileSharingService implements FileSharingService.FileSharingUpload {
    private static final Map<UUID, CompletableFuture<URI>> _runningUploads = new ConcurrentHashMap<>(); // makes it possible to cancel running uploads
    private static final Map<UUID, CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket>> _waitingUploads = new ConcurrentHashMap<>();
    private boolean _hasError = false;
    private String _errorMessage = null;

    @Override
    protected DownloadedMedia download(URI uri) {
        LOGGER.error("ServerFileSharingDownload.download() called. This should not happen. Please submit a bug report on github");
        return new DownloadedMedia(DownloadedMedia.DownloadError.GENERIC, new String[]{"ServerFileSharingDownload.download called. This should never happen. Please submit a bug report on github"});
    }

    @Override
    public CompletableFuture<URI> upload(Path filePath) {
        UUID uploadId = UUID.randomUUID();
        CompletableFuture<URI> future =  CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(filePath);
                long sizeLimit = (long) CONFIG.serverMaxFileSize() * 1024 * 1024 * 8; //convert mb to bit

                if (data.length > sizeLimit) {
                    _hasError = true;
                    _errorMessage = "File too big";
                    return null;
                }

                List<DataChunker.Chunk> chunks = new DataChunker(data).splitIntoChunks();
                CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket> serverResponseFuture = new CompletableFuture<>();
                _waitingUploads.put(uploadId, serverResponseFuture);

                String mimeType = Files.probeContentType(filePath);
                if (mimeType != null) {
                    if (mimeType.equals("image/gif")) {
                        chunks.forEach(chunk -> MEDIA_CHANNEL.clientHandle().send(new NetworkManager.ServerboundMediaSyncUploadGifPacket(uploadId, chunk.getData(), chunk.getChunkIndex(), chunk.getTotalChunks())));
                    } else if (mimeType.startsWith("image/")) {
                        chunks.forEach(chunk -> MEDIA_CHANNEL.clientHandle().send(new NetworkManager.ServerboundMediaSyncUploadImagePacket(uploadId, chunk.getData(), chunk.getChunkIndex(), chunk.getTotalChunks())));
                    } else {
                        _hasError = true;
                        _errorMessage = "The File you want to upload has an invalid filetype \"" + mimeType + "\"";
                        _runningUploads.remove(uploadId);
                        return null;

                    }
                }
                // this waits for an answer from the server
                NetworkManager.ClientboundMediaSyncUploadResponsePacket serverResponse = serverResponseFuture.get();
                _runningUploads.remove(uploadId);

                // todo handle possibly received errors

                return new URI(serverResponse.mediaLocator());
            } catch (IOException e) {
                LOGGER.error("error while reading file for server upload", e); //todo translate this
                _hasError = true;
                _errorMessage = "error while reading file for server upload: " + e.getMessage(); //todo translate this
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error("An error occurred: {}", e.getMessage());
                _hasError = true;
                _errorMessage = "An error occurred: " + e.getMessage(); //todo translate this
            } catch (URISyntaxException e) {
                LOGGER.error("URI from server response is invalid", e);
                _hasError = true;
                _errorMessage = "URI from server response is invalid"; //todo translate this
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

    public static void receiveServerResponsePacket(UUID uploadId, NetworkManager.ClientboundMediaSyncUploadErrorPacket access) {
        CompletableFuture<NetworkManager.ClientboundMediaSyncUploadResponsePacket> waitingFuture = _waitingUploads.get(uploadId);
        CompletableFuture<URI> runningFuture = _runningUploads.get(uploadId);

        if (waitingFuture != null) {
            waitingFuture.cancel(true);
            _waitingUploads.remove(uploadId);
        }
        if (runningFuture != null) {
            _runningUploads.remove(uploadId);
            runningFuture.completeExceptionally(new DetailedCancellationException(access.errorMessage()));
        }
    }

    public boolean hasError() {
        return _hasError;
    }

    public String getErrorMessage() {
        return _errorMessage;
    }
}
