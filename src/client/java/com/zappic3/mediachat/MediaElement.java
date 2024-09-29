package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.ImageSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.sksamuel.scrimage.ImmutableImage;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChatClient.CONFIG;
import static com.zappic3.mediachat.Utility.registerTexture;

public class MediaElement {
    private static final Identifier MEDIA_LOADING =  Identifier.of("media-chat", "textures/media_loading.png");
    private static final Identifier MEDIA_UNSUPPORTED =  Identifier.of("media-chat", "textures/media_unsupported.png");
    private static final Identifier MEDIA_DOWNLOAD_FAILED =  Identifier.of("media-chat", "textures/media_download_failed.png");
    private static final Identifier MEDIA_NOT_WHITELISTED =  Identifier.of("media-chat", "textures/media_not_whitelisted.png");

    private static final Map<Integer, MediaElement> _mediaPool = new ConcurrentHashMap<>();
    private static MediaElement _hoveredMediaElement = null;

    private  CompletableFuture<Void> loadFuture ;
    private final String _source;
    private volatile List<Identifier> _ids = new ArrayList<>();
    private int _width;
    private int _height;
    private String messageId;

    // animated element support
    private int _currentFrame = 0;
    private volatile List<Long> _frameDelays = new ArrayList<>();
    private boolean isAnimPlaying = false;
    private long animPlayingStartTime = -1;

    private MediaElement(String source, Identifier id) {
        this(source, new ArrayList<>(List.of(id)));
    }

    private MediaElement(String source, List<Identifier> ids) {
        this._source = source;
        this._ids = ids;
        this.messageId = null;

        if (source != null) {
            this.setIdentifier(MEDIA_LOADING, 64, 64);
            this.loadFuture = CompletableFuture.runAsync(() -> {
                MediaIdentifierInfo mii = downloadMedia(source);
                this.setIdentifier(mii.id(), mii.delays(), mii.width(), mii.height());
                if (mii.delays != null) {
                    this.isAnimPlaying = true;
                }
            });
        }
    }

    public record MediaIdentifierInfo(List<Identifier> id, List<Long> delays, int width, int height) {
        public MediaIdentifierInfo(Identifier id, int width, int height) {
            this(List.of(id), null, width, height);
        }
    }

    public static MediaElement of(String source) {
        return _mediaPool.computeIfAbsent(source.hashCode(), s -> new MediaElement(source, MEDIA_LOADING));
    }

    // todo dieses gif funktioniert nicht, warum? https://s1882.pcdn.co/wp-content/uploads/VoaBStransp.gif
    private static MediaIdentifierInfo downloadMedia(String source) {
        try {
            URL url = new URI(source).toURL();

            if (CONFIG.useWhitelist()) {
                boolean isWhitelisted = false;
                String host = url.getHost();
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }

                for (String whitelistedDomain : CONFIG.whitelistedWebsites()) {
                    if (whitelistedDomain.startsWith("www.")) {
                        whitelistedDomain = whitelistedDomain.substring(4);
                    }

                    if (host.equals(whitelistedDomain) || host.endsWith("." + whitelistedDomain)) {
                        isWhitelisted = true;
                        break;
                    }
                }
                if (!isWhitelisted) {
                    return new MediaIdentifierInfo(MEDIA_NOT_WHITELISTED, 64, 64);
                }
            }

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
                String formatName = reader.getFormatName().toLowerCase();

                List<BufferedImage> frames  = new ArrayList<>();
                List<Long> delays = null;

                // save in case it needs to be safed to disk
                AnimatedGif gif = null;

                switch (formatName) {
                    case "gif":
                        delays = new ArrayList<>();
                        gif = AnimatedGifReader.read(ImageSource.of(imageInputStreamToInputStream(imageStream)));
                        for (int i = 0; i < gif.getFrameCount(); i++) {
                            ImmutableImage currentFrame = gif.getFrame(i);
                            frames.add(i, currentFrame.toNewBufferedImage(currentFrame.getType()));
                            delays.add(gif.getDelay(i).toMillis());
                        }
                        break;
                    default:
                        reader.setInput(imageStream);
                        BufferedImage image = reader.read(0);
                        frames.add(image);
                }
                imageStream.close();

                if (!frames.isEmpty() && frames.getFirst().getWidth() > 0 && frames.getFirst().getHeight() > 0) {
                    if (CONFIG.cacheMedia()) {
                        switch (formatName) {
                            case "gif":
                                CacheManager.saveGifToCache(gif, source);
                                break;
                            default:
                                CacheManager.saveMediaToCache(frames.getFirst(), source, formatName);
                                break;
                        }
                    }
                    List<Identifier> identifiers = new ArrayList<>();
                    for (int i = 0; i < frames.size(); i++) {
                        BufferedImage image = frames.get(i);
                        Identifier id = registerTexture(image, source.hashCode()+"_"+i);
                        identifiers.add(id);
                    }

                    return new MediaIdentifierInfo(identifiers, delays, frames.getFirst().getWidth(), frames.getFirst().getHeight());
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

    private static InputStream imageInputStreamToInputStream(ImageInputStream imageStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = imageStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    // this is intended for loading media that was saved to disk on startup.
    public static void add(Identifier id, int hashCode, int width, int height) {
        MediaElement e = new MediaElement(null, id);
        e.setIdentifier(id, width, height);
        _mediaPool.put(hashCode, e);
    }

    public static void add(AnimatedGif gif, int hashCode) throws Exception {
        List<Long> delays = new ArrayList<>();
        List<Identifier> identifiers = new ArrayList<>();
        for (int i = 0; i < gif.getFrameCount(); i++) {
            ImmutableImage currentFrame = gif.getFrame(i);
            BufferedImage image = currentFrame.toNewBufferedImage(currentFrame.getType());
            Identifier id = registerTexture(image, hashCode+"_"+i);
            identifiers.add(id);
            delays.add(gif.getDelay(i).toMillis());
        }

        MediaElement e = new MediaElement(null, identifiers);
        e.setIdentifier(identifiers, delays, gif.getFrame(0).width, gif.getFrame(0).height);
        _mediaPool.put(hashCode, e);

    }

    public Identifier currentFrame() {
        if (isAnimPlaying && _frameDelays != null) { determineCurrentFrame(); }
        return _ids.get(_currentFrame);
    }

    private void determineCurrentFrame() {
        long totalTime = animPlayingStartTime;
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < _frameDelays.size(); i++) {
            totalTime += _frameDelays.get(i);
            if (totalTime > currentTime) {
                _currentFrame = i;
                return;
            }
        }
        animPlayingStartTime = currentTime;
        _currentFrame = 0;
    }

    public int width() {
        return _width;
    }

    public int height() {
        return _height;
    }

    public String messageId() {
        return this.messageId;
    }

    public void messageId(String id) {
        this.messageId = id;
    }

    public static void hovered(MediaElement element) {
        _hoveredMediaElement = element;
    }

    public static MediaElement hovered() {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) {
            _hoveredMediaElement = null;
        }
        return _hoveredMediaElement;
    }

    private void setIdentifier(Identifier id, int width, int height) {
        List<Identifier> ids = new ArrayList<>();
        ids.add(id);
        this.setIdentifier(new ArrayList<>(List.of(id)), null, width, height);
        this.isAnimPlaying = false;
    }

    private void setIdentifier(List<Identifier> ids, List<Long> delays, int width, int height) {
        if (delays != null && ids.size() != delays.size()) {throw new IllegalArgumentException("Error setting MediaElement Identifier:\nThe 'ids' and 'delays' list must have the same size (or null)");}
        this._ids = ids;
        this._width = width;
        this._height = height;
        this._frameDelays = delays;
        this.isAnimPlaying = true;
    }

    public static void reactToMouseClick() {
        if (hovered() != null) {
            LOGGER.info("MediaElement pressed: " + hovered().messageId());
        }
    }
}
