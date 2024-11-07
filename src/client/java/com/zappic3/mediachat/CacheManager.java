package com.zappic3.mediachat;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.*;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChatClient.getModDataFolderPath;
import static com.zappic3.mediachat.Utility.registerTexture;

public class CacheManager {
    private static long _currentCacheSize = 0;
    private static List<File> _orderedListOfFiles = new ArrayList<>(); // sorting: oldest -> newest

    private CacheManager() {}

    private static File getCacheFolder() {
        return new File(getModDataFolderPath("cache").toUri());
    }

    public static void registerCachedTextures() {
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

    public static void saveMediaToCache(BufferedImage image, String source, String formatName) throws IOException {
        File file = new File(getModDataFolderPath("cache") + File.separator + source.hashCode() + "." + formatName);
        ImageIO.write(image, formatName, file);
        enforceCacheSizeLimit(file);
    }

    //todo: the same gif gets saved with different names. something with the hash must be wrong
    public static void saveGifToCache(AnimatedGif gif, String source) throws IOException {
        String filePath = getModDataFolderPath("cache") + File.separator + source.hashCode() + ".gif";
        GifSequenceWriter writer = new GifSequenceWriter(gif.getDelay(0).toMillis(), true);
        ImmutableImage[] images = gif.getFrames().toArray(new ImmutableImage[0]);
        if (images != null) {
            writer.output(images, filePath);
        }
    }

    private static void enforceCacheSizeLimit(File file) {
        _currentCacheSize += file.length();
        _orderedListOfFiles.addLast(file);

        long maxCacheSize = (long) CONFIG.maxMediaSize() * 1024 * 1024;
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

    public static boolean isFileInCache(int hash) {
        File[] matchingFiles = getCacheFolder().listFiles(getFilter(hash+""));
        return matchingFiles != null && matchingFiles.length > 0;
    }

    public static OneOfTwo<BufferedImage, AnimatedGif> loadFileFromCache(int hash) {
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

    private static FilenameFilter getFilter(String filename) {
        return (dir, name) -> {
            int dotIndex = name.lastIndexOf('.');
            String baseName = (dotIndex == -1) ? name : name.substring(0, dotIndex);
            return baseName.equals(filename);
        };
    }


}
