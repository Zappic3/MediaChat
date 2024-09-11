package com.zappic3.mediachat;

import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChatClient.getModDataFolderPath;
import static com.zappic3.mediachat.Utility.registerTexture;

public class MediaElement {
    private static final Identifier MEDIA_LOADING =  Identifier.of("media-chat", "textures/media_loading.png");
    private static Map<String, MediaElement> _mediaPool = new ConcurrentHashMap<>();
    private final String _source;
    private volatile Identifier _id;
    private final CompletableFuture<Void> loadFuture;
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
        return _mediaPool.computeIfAbsent(source, s -> new MediaElement(s, MEDIA_LOADING));
    }

    private static MediaIdentifierInfo downloadMedia(String source) {
        try {
            URL url = new URL(source);
            BufferedImage image = ImageIO.read(url);

            //String filePath = getModDataFolderPath("temp").resolve(source.hashCode() + ".png").toString();
            //LOGGER.info(filePath);
            //File file = new File(filePath);
            //ImageIO.write(image, "png", file); // todo add option to save images to disk
            Identifier id = registerTexture(image, source.hashCode()+"");
            return new MediaIdentifierInfo(id, image.getWidth(), image.getHeight());
        } catch (IOException e) {
            // handle IOException
            LOGGER.error("Error Downloading Image: \n"+e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error while registering image: \n" + e.getMessage());
        }
        return new MediaIdentifierInfo(Identifier.of("media-chat", "textures/image.png"), 64, 64);
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
