package com.zappic3.mediachat;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.*;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
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

    public static void registerCachedTextures() {
        File folder = new File(getModDataFolderPath("cache").toUri());
        File[] filesArray = folder.listFiles();

        if (filesArray != null) {
            _orderedListOfFiles = new ArrayList<>(List.of(filesArray));
            _orderedListOfFiles.sort(Comparator.comparingLong(File::lastModified));

            for (File file : _orderedListOfFiles) {
                if (file.isFile()) {
                    _currentCacheSize += file.length();
                    try {
                        String nameWithoutExtension = file.getName().substring(0, file.getName().lastIndexOf("."));
                        String extension = file.getName().substring(nameWithoutExtension.length()+1);
                        switch (extension) {
                            case "gif":
                                AnimatedGif gif = AnimatedGifReader.read(ImageSource.of(file));
                                MediaElement.add(gif, Integer.parseInt(nameWithoutExtension));
                                break;
                            default:
                                BufferedImage image = ImageIO.read(file);
                                Identifier id = registerTexture(image, nameWithoutExtension);
                                MediaElement.add(id, Integer.parseInt(nameWithoutExtension), image.getWidth(), image.getHeight());
                                break;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to register texture '{}' from cache", file.getName(), e);
                    }
                }
            }
        }
    }

    public static void saveMediaToCache(BufferedImage image, String source, String formatName) throws IOException {
        File file = new File(getModDataFolderPath("cache") + File.separator + source.hashCode() + "." + formatName);
        ImageIO.write(image, formatName, file);
        enforceCacheSizeLimit(file);
    }

    // this method might not be necessary anymore
    public static void saveAnimatedMediaToCache(List<BufferedImage> images, List<Long> delays, String source, String formatName) throws IOException {
        Path folder = getModDataFolderPath("cache" + File.separator + source.hashCode());
        for (int i = 0; i < images.size(); i++) {
            File file = new File(folder.resolve(i + "." + formatName).toString());
            ImageIO.write(images.get(i), formatName, file);
        }
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


}
