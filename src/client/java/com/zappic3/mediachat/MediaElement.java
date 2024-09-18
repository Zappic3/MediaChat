package com.zappic3.mediachat;

import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChatClient.CONFIG;
import static com.zappic3.mediachat.MediaChatClient.getModDataFolderPath;
import static com.zappic3.mediachat.Utility.registerTexture;

public class MediaElement {
    private static final Identifier MEDIA_LOADING =  Identifier.of("media-chat", "textures/media_loading.png");
    private static final Identifier MEDIA_UNSUPPORTED =  Identifier.of("media-chat", "textures/media_unsupported.png");
    private static final Identifier MEDIA_DOWNLOAD_FAILED =  Identifier.of("media-chat", "textures/media_download_failed.png");
    private static final Map<Integer, MediaElement> _mediaPool = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> loadFuture;
    private final String _source;
    private volatile Identifier _id;
    private int _width;
    private int _height;

    private MediaElement(String source, Identifier id) {
        this._source = source;
        this._id = MEDIA_LOADING;
        this._width = 64;
        this._height = 64;

        this.loadFuture = CompletableFuture.runAsync(() -> {
            MediaIdentifierInfo mii = downloadMedia(source);
            this.setId(mii.getId(), mii.getWidth(), mii.getHeight());
        });
    }

    public static MediaElement of(String source) {
        return _mediaPool.computeIfAbsent(source.hashCode(), s -> new MediaElement(source, MEDIA_LOADING));
    }

    private static MediaIdentifierInfo downloadMedia(String source) {
        try {
            URL url = new URI(source).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();

            String contentType = connection.getContentType();
            int contentLength = connection.getContentLength();
            if (contentType.startsWith("image") && contentLength > 0 && contentLength <= CONFIG.maxMediaSize() * 1024 * 1024) {  // convert mb to byte
                ImageInputStream imageStream = ImageIO.createImageInputStream(url.openStream());

                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
                if (!readers.hasNext()) {
                    imageStream.close();
                    return new MediaIdentifierInfo(MEDIA_UNSUPPORTED, 64, 64);
                }

                ImageReader reader = readers.next();
                String formatName = reader.getFormatName();
                reader.setInput(imageStream);
                BufferedImage image = reader.read(0);
                imageStream.close();
                // todo add option to save images to disk

                if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                    Identifier id = registerTexture(image, source.hashCode()+"");
                    return new MediaIdentifierInfo(id, image.getWidth(), image.getHeight());
                } else {
                    return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 64, 64);
                }

            } else {
                return new MediaIdentifierInfo(MEDIA_UNSUPPORTED, 64, 64);
            }



        } catch (IOException e) {
            // handle IOException
            LOGGER.error("Error Downloading Image: \n"+e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error while registering image: \n" + e.getMessage());
        }
        return new MediaIdentifierInfo(Identifier.of("media-chat", "textures/image.png"), 64, 64);
    }

    // this is intended for loading media that was saved to disk on startup.
    public static void add(Identifier id, int hashCode, int width, int height) {
        MediaElement e = new MediaElement(null, id);
        e.setId(id, width, height);
        _mediaPool.put(hashCode, e);
    }

    public Identifier currentFrame() {
        return _id;
    }

    public int width () {
        return _width;
    }

    public int height () {
        return _height;
    }

    private void setId(Identifier id, int width, int height) {
        this._id = id;
        this._width = width;
        this._height = height;
    }
}
