package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class SizeLimitedCache extends MediaCache {
    private final List<Path> _orderedListOfFiles = new ArrayList<>(); // sorting: oldest -> newest
    private int _maxMediaSize; // todo make this editable in the config options (the option already exists for clients but is currently broken)
    private long _currentCacheSize = 0;

    public SizeLimitedCache(Path cacheFolder, int maxMediaSize) {
        super(cacheFolder);
        this._maxMediaSize = maxMediaSize;

        try {
            // Populate _orderedListOfFiles with sorted files (oldest first)
            _orderedListOfFiles.addAll(
                Files.list(cacheFolder)
                    .filter(Files::isRegularFile) // Keep only regular files
                    .sorted(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            LOGGER.error("Error reading last modified time for file: {}", path, e);
                            return Long.MAX_VALUE; // assign the file the oldest possible time
                        }
                    }))
                    .toList()
            );
        } catch (IOException e) {
            LOGGER.error("Failed to initialize cache file list", e);
        }
    }

    private void enforceCacheSizeLimit(Path filePath) {
        try {
            _currentCacheSize += Files.size(filePath);
            _orderedListOfFiles.addLast(filePath);

            long maxCacheSize = (long) this._maxMediaSize * 1024 * 1024 * 8;
            if (_currentCacheSize > maxCacheSize) {
                Iterator<Path> iterator = _orderedListOfFiles.iterator();
                while (iterator.hasNext() && _currentCacheSize > maxCacheSize) {
                    Path currentPath = iterator.next();
                    _currentCacheSize -= Files.size(currentPath);
                    iterator.remove();
                    Files.deleteIfExists(currentPath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error enforcing cache size limit", e);
        }
    }

    @Override
    public Path saveMediaToCache(BufferedImage image, int hash, String formatName) throws IOException {
        Path filePath = super.saveMediaToCache(image, hash, formatName);
        enforceCacheSizeLimit(filePath);
        return filePath;
    }

    @Override
    public Path saveGifToCache(AnimatedGif gif, int hash) throws IOException {
        Path filePath = super.saveGifToCache(gif, hash);
        enforceCacheSizeLimit(filePath);
        return filePath;
    }

    @Override
    public boolean removeMediaFromCache(int hash) {
        try {
            Optional<Path> filePathOptional = getFilePath(hash);

            if (filePathOptional.isPresent()) {
                Path filePath = filePathOptional.get();

                long fileSize = Files.size(filePath);
                _currentCacheSize -= fileSize;

                Files.deleteIfExists(filePath);

                // Remove the file from the list of cached files
                Iterator<Path> iterator = _orderedListOfFiles.iterator();
                while (iterator.hasNext()) {
                    Path currentPath = iterator.next();
                    if (currentPath.equals(filePath)) {
                        iterator.remove();
                        break;
                    }
                }
                return true;
            } else {
                LOGGER.warn("File with hash {} not found in cache", hash);
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("Error removing file with hash {} from cache", hash, e);
        }
        return false;
    }

    public int getCachedElementCount() {
        return _orderedListOfFiles.size();
    }

    public long getCurrentCacheSize() {
        return _currentCacheSize;
    }
}
