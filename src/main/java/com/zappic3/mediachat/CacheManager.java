package com.zappic3.mediachat;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.*;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class CacheManager {
    private long _currentCacheSize = 0;
    private List<File> _orderedListOfFiles = new ArrayList<>(); // sorting: oldest -> newest

    private int _maxMediaSize; // todo make this editable in the config options (the option already exists for clients but is currently broken)
    private final Path _cacheFolder;

    public CacheManager(Path cacheFolder, int maxMediaSize) {
        this._maxMediaSize = maxMediaSize;
        this._cacheFolder = cacheFolder;
    }

    private File getCacheFolder() {
        Path folderPath = FabricLoader.getInstance().getGameDir().resolve("MediaChat").resolve(_cacheFolder);
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return folderPath.toFile();
    }

    public void registerCachedTextures() {
        LOGGER.info("Registering textures from cache...");
        File folder = getCacheFolder();
        File[] filesArray = folder.listFiles();

        if (filesArray != null) {
            _orderedListOfFiles = new ArrayList<>(List.of(filesArray));
            _orderedListOfFiles.sort(Comparator.comparingLong(File::lastModified));

            for (File file : _orderedListOfFiles) {
                if (file.isFile()) {
                    _currentCacheSize += file.length();
                }
            }
        }
    }

    public void saveMediaToCache(BufferedImage image, int hash, String formatName) throws IOException {
        File file = new File(getCacheFolder() + File.separator + hash + "." + formatName);
        ImageIO.write(image, formatName, file);
        enforceCacheSizeLimit(file);
    }

    public void saveGifToCache(AnimatedGif gif, int hash) throws IOException {
        String filePath = getCacheFolder() + File.separator + hash + ".gif";
        GifSequenceWriter writer = new GifSequenceWriter(gif.getDelay(0).toMillis(), true);
        ImmutableImage[] images = gif.getFrames().toArray(new ImmutableImage[0]);
        writer.output(images, filePath);
        enforceCacheSizeLimit(new File(filePath));
    }

    private void enforceCacheSizeLimit(File file) {
        _currentCacheSize += file.length();
        _orderedListOfFiles.addLast(file);

        long maxCacheSize = (long) this._maxMediaSize * 1024 * 1024;
        if (_currentCacheSize > maxCacheSize) {
            Iterator<File> iterator = _orderedListOfFiles.iterator();
            while (iterator.hasNext() && _currentCacheSize > maxCacheSize) {
                File currentFile = iterator.next();
                _currentCacheSize -= currentFile.length();
                iterator.remove();
                currentFile.delete();
            }
        }
    }

    public boolean isFileInCache(int hash) {
        File[] matchingFiles = getCacheFolder().listFiles(getFilter(hash+""));
        return matchingFiles != null && matchingFiles.length > 0;
    }

    public OneOfTwo<BufferedImage, AnimatedGif> loadFileFromCache(int hash) {
        File[] matchingFiles = getCacheFolder().listFiles(getFilter(hash+""));

        if (matchingFiles != null && matchingFiles.length > 0) {
            File currentFile = matchingFiles[0];
            if (currentFile.exists() && currentFile.isFile()) {
                try {
                    String nameWithoutExtension = currentFile.getName().substring(0, currentFile.getName().lastIndexOf("."));
                    String extension = currentFile.getName().substring(nameWithoutExtension.length()+1);
                    switch (extension) {
                        case "gif":
                            AnimatedGif gif = AnimatedGifReader.read(ImageSource.of(currentFile));
                            return OneOfTwo.ofSecond(gif);
                        default:
                            BufferedImage image = ImageIO.read(currentFile);
                            return OneOfTwo.ofFirst(image);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to register texture '{}' from cache", currentFile.getName(), e);
                }
            }
        }
        return null;
    }

    private FilenameFilter getFilter(String filename) {
        return (dir, name) -> {
            int dotIndex = name.lastIndexOf('.');
            String baseName = (dotIndex == -1) ? name : name.substring(0, dotIndex);
            return baseName.equals(filename);
        };
    }

    public int getCachedElementCount() {
        return _orderedListOfFiles.size();
    }

    public long getCurrentCacheSize() {
        return _currentCacheSize;
    }

}
