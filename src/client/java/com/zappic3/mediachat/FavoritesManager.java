package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.internal.GifSequenceReader;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.MediaChatClient.CLIENT_CACHE;

public class FavoritesManager {
    private static final MediaCache FAVORITE_CACHE = new MediaCache(MediaCache.getOSRoot().resolve("Favorites"));
    private static FavoritesManager instance;
    private final List<Path> _favoritesList = getAllFavoriteFiles(FAVORITE_CACHE.getCachePath());
    private final Set<Integer> _activeOperations = new HashSet<>();

    private FavoritesManager() {}

    public static FavoritesManager getInstance() {
        if (instance == null) {
            instance = new FavoritesManager();
        }
        return instance;
    }

    public boolean isFavorite(int hash) {
        return FAVORITE_CACHE.isFileInCache(hash);
    }

    private void removeFavorite(int hash) {
        if (FAVORITE_CACHE.removeMediaFromCache(hash)) {
            Iterator<Path> iterator = _favoritesList.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.getFileName().toString().contains(Integer.toString(hash))) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private void addFavorite(String url) {
        if (CLIENT_CACHE.isFileInCache(url.hashCode())) {
            Optional<Path> filePathOptional = CLIENT_CACHE.getFilePath(url.hashCode());
            if (filePathOptional.isPresent()) {
                Path sourceFile = filePathOptional.get();
                Path targetFile = FAVORITE_CACHE.getCachePath().resolve(sourceFile.getFileName());

                try {
                    if (!Files.exists(FAVORITE_CACHE.getCachePath())) {
                        Files.createDirectories(FAVORITE_CACHE.getCachePath());
                    }
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    _favoritesList.add(targetFile);
                    return;
                } catch (IOException e) {
                    LOGGER.error("Failed to add favorite file (error trying to copy file)", e);
                }
            }
        }

        // if the above doesn't work, download the file from the internet
        if ((!MinecraftClient.getInstance().isInSingleplayer()
                && (CONFIG.serverNetworkingMode().equals(ConfigModel.serverMediaNetworkingMode.ALL)
                || CONFIG.serverNetworkingMode().equals(ConfigModel.serverMediaNetworkingMode.LINKS_ONLY))
                && (!url.startsWith("https://media.tenor.com/")) // don't download tenor GIFs via the server to save bandwidth and performance
        ) || url.startsWith("server:")) {

            ClientNetworking.registerReceiver(url.hashCode(), (bais, type) -> {
                if (type.equals("gif")) {
                    GifSequenceReader reader = new GifSequenceReader();
                    int status = reader.read(bais);

                    if (status != GifSequenceReader.STATUS_OK) {
                        LOGGER.error("Failed to read downloaded gif (status: {})", status);
                        return;
                    }
                    AnimatedGif gif = new AnimatedGif(reader);
                    try {
                        FAVORITE_CACHE.saveGifToCache(gif, url.hashCode());
                    } catch (IOException e) {
                        LOGGER.error("Failed to save gif", e);
                    }
                } else {
                    try {
                        BufferedImage image = ImageIO.read(bais);
                        FAVORITE_CACHE.saveMediaToCache(image, url.hashCode(), "png");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            MEDIA_CHANNEL.clientHandle().send(new NetworkManager.ServerboundMediaSyncRequestDownloadPacket(url));

        } else {
            try {
                URI uri = new URI(url);
                FileSharingService service = FileSharingService.getDownloadServiceFor(uri);
                DownloadedMedia downloadedMedia = service.downloadWithChecks(uri);

                if (downloadedMedia != null && !downloadedMedia.hasError()) {
                    switch (downloadedMedia) {
                        case DownloadedGif g:
                            FAVORITE_CACHE.saveGifToCache(g.getOriginalGif(), url.hashCode());
                            break;
                        default:
                            FAVORITE_CACHE.saveMediaToCache(downloadedMedia.getDownloadedMedia().getFirst(), url.hashCode(), downloadedMedia.getFormatName());
                            break;
                    }

                    Optional<Path> newFilePathOption = FAVORITE_CACHE.getFilePath(url.hashCode());
                    newFilePathOption.ifPresent(_favoritesList::add);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to add favorite file (error trying to download file)", e);
            }
        }
    }

    public void toggleFavorite(String url) {
        synchronized (_activeOperations) {
            if (_activeOperations.contains(url.hashCode())) {
                LOGGER.warn("This Favorite element is currently already being processed. Try again in a few moments");
                return;
            }
            _activeOperations.add(url.hashCode());
        }

        new Thread(() -> {
            try {
                if (isFavorite(url.hashCode())) {
                    removeFavorite(url.hashCode());
                } else {
                    addFavorite(url);
                }
            } finally {
                synchronized (_activeOperations) {
                    _activeOperations.remove(url.hashCode());
                }
            }
        }).start();
    }

    private List<Path> getAllFavoriteFiles(Path folder) {
        try (Stream<Path> paths = Files.walk(folder, 10)) {
            return paths.filter(Files::isRegularFile) // Only include files, not directories
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
