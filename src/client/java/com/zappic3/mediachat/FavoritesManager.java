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
import java.io.*;
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
    private static final Path favoritesLocation = MediaCache.getOSRoot().resolve("Favorites");
    private static final MediaCache FAVORITE_CACHE = new MediaCache(favoritesLocation);
    private static final Path propFile = favoritesLocation.resolve("media.properties");
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

    public List<Path> getFavoritesList() {
        return _favoritesList;
    }

    public boolean isFavorite(int hash) {
        return FAVORITE_CACHE.isFileInCache(hash);
    }

    public String getMediaSourceURL(String hash) {
        Properties props = getFavProps();
        return props.getProperty(hash);
    }

    public static Optional<Path> getFilePath(int hash) {
        return FAVORITE_CACHE.getFilePath(hash);
    }

    public OneOfTwo<BufferedImage, AnimatedGif> loadFavoriteFromCache(int hash) {
        return FAVORITE_CACHE.loadFileFromCache(hash);
    }


    /**
     * Replaces the filename of the old file with the hash of the newUrl
     *
     * @param prevHash hash (filename) of the file to replace
     * @param newUrl the url that leads to the new file location
     */
    public void replaceFavoriteUrl(String prevHash, String newUrl) {
        String newHash = newUrl.hashCode()+"";

        // update filename
        ListIterator<Path> iterator = _favoritesList.listIterator();
        while (iterator.hasNext()) {
            Path path = iterator.next();
            if (path.getFileName().toString().contains(prevHash)) {

                String oldName = path.getFileName().toString();
                String newName = oldName.replace(prevHash, newHash);
                Path newPath = path.resolveSibling(newName);

                try {
                    Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);
                    iterator.set(newPath);
                } catch (IOException e) {
                    LOGGER.error("failed to update filename", e);
                }
                break;
            }
        }

        // update name in the propFile
        removeFavProps(prevHash);
        addFavProp(newHash, newUrl);
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
            removeFavProps(hash + "");
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
                    addFavProp(url.hashCode() + "", url);
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
                        addFavProp(url.hashCode() + "", url);
                    } catch (IOException e) {
                        LOGGER.error("Failed to save gif", e);
                    }
                } else {
                    try {
                        BufferedImage image = ImageIO.read(bais);
                        FAVORITE_CACHE.saveMediaToCache(image, url.hashCode(), "png");
                        addFavProp(url.hashCode() + "", url);
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
                    addFavProp(url.hashCode() + "", url);
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
            return paths
                    .filter(Files::isRegularFile) // Only include files, not directories
                    .filter(path -> !path.equals(propFile)) // Exclude the prop file
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties getFavProps() {
        File f = propFile.toFile();
        try {
            f.createNewFile(); // creates new file if it doesn't exist already
        } catch (IOException e) {
            LOGGER.warn("IOException while trying to create prop file", e);
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (FileNotFoundException e) {
            LOGGER.error("File wasn't found, even though it should've been created moments ago", e);
        } catch (IOException e) {
            LOGGER.warn("IOException while trying to load prop file", e);
        }

        return props;
    }

    private void setFavProps(Properties props) {
        File f = propFile.toFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            props.store(out, null); // null = no comment header
        } catch (FileNotFoundException e) {
            LOGGER.error("Prop file is directory or cant be opened", e);
        } catch (IOException e) {
            LOGGER.error("Error writing to prop file", e);
        }
    }

    private void addFavProp(String hash, String url) {
        // don't save locations that are saved on the server
        if (!url.startsWith("server:")) {
            Properties props = getFavProps();
            props.setProperty(hash, url);
            setFavProps(props);
        }
    }

    private void removeFavProps(String hash) {
        Properties props = getFavProps();
        props.remove(hash);
        setFavProps(props);
    }
}
