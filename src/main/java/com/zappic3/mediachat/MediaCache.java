package com.zappic3.mediachat;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.GifSequenceWriter;
import com.sksamuel.scrimage.nio.ImageSource;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class MediaCache {
    protected final Path _cachePath;

    public MediaCache(Path cachePath) {
        this._cachePath = cachePath;
        try {
            Files.createDirectories(_cachePath);
        } catch (IOException e) {
            LOGGER.error("Failed to create cache directory", e);
            throw new RuntimeException(e);
        }


    }

    public static Path getOSRoot() {
        String home = System.getProperty("user.home");
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return Paths.get(System.getenv("APPDATA"), "MediaChatMod");
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "MediaChatMod");
        } else {
            return Paths.get(home, ".MediaChatMod"); // linux / unix
        }
    }

    public static Path getModRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("MediaChat");
    }

    public Path saveMediaToCache(BufferedImage image, int hash, String formatName) throws IOException {
        Path filePath = _cachePath.resolve(hash + "." + formatName);
        ImageIO.write(image, formatName, filePath.toFile());
        return filePath;
    }

    public Path saveGifToCache(AnimatedGif gif, int hash) throws IOException {
        Path filePath = _cachePath.resolve(hash + ".gif");
        GifSequenceWriter writer = new GifSequenceWriter(gif.getDelay(0).toMillis(), true);
        ImmutableImage[] images = gif.getFrames().toArray(new ImmutableImage[0]);
        writer.output(images, filePath);
        return filePath;
    }

    public boolean removeMediaFromCache(int hash) {
        Optional<Path> filePathOptional = getFilePath(hash);
        try {
            if (filePathOptional.isPresent()) {
                Path filePath = filePathOptional.get();
                Files.deleteIfExists(filePath);
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

    public boolean isFileInCache(int hash) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(_cachePath, path ->
                path.getFileName().toString().contains(hash+""))) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            throw new RuntimeException("Error checking cache files", e);
        }
    }

    public OneOfTwo<BufferedImage, AnimatedGif> loadFileFromCache(int hash) {
        try (Stream<Path> files = Files.list(_cachePath)) {
            Path matchingFile = files
                    .filter(path -> path.getFileName().toString().contains(hash+""))
                    .findFirst()
                    .orElse(null);

            if (matchingFile != null && Files.isRegularFile(matchingFile)) {
                String fileName = matchingFile.getFileName().toString();
                int dotIndex = fileName.lastIndexOf(".");
                if (dotIndex == -1) return null; // No extension found

                String extension = fileName.substring(dotIndex + 1);

                switch (extension.toLowerCase()) {
                    case "gif":
                        AnimatedGif gif = AnimatedGifReader.read(ImageSource.of(matchingFile.toFile()));
                        return OneOfTwo.ofSecond(gif);
                    default:
                        BufferedImage image = ImageIO.read(matchingFile.toFile());
                        return OneOfTwo.ofFirst(image);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load texture '{}' from cache", hash, e);
        }

        return null;
    }

    public Optional<Path> getFilePath(int hash) {
        try (Stream<Path> files = Files.list(_cachePath)) {
            return files
                    .filter(path -> path.getFileName().toString().contains(hash+""))
                    .findFirst(); // Returns Optional<Path>
        } catch (IOException e) {
            throw new RuntimeException("Error searching for file in cache", e);
        }
    }

    public Path getCachePath() {
        return _cachePath;
    }
}
