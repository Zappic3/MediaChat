package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.zappic3.mediachat.filesharing.DownloadedGif;
import com.zappic3.mediachat.filesharing.DownloadedMedia;
import com.zappic3.mediachat.filesharing.FileSharingService;
import com.zappic3.mediachat.ui.MediaViewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.sksamuel.scrimage.ImmutableImage;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.MOD_ID;
import static com.zappic3.mediachat.Utility.registerTexture;

public class MediaElement {
    private static final Identifier MEDIA_LOADING =  Identifier.of(MOD_ID, "textures/media_loading.png");
    private static final Identifier MEDIA_UNSUPPORTED =  Identifier.of(MOD_ID, "textures/media_unsupported.png");
    private static final Identifier MEDIA_DOWNLOAD_FAILED =  Identifier.of(MOD_ID, "textures/media_download_failed.png");
    private static final Identifier MEDIA_NOT_WHITELISTED =  Identifier.of(MOD_ID, "textures/media_not_whitelisted.png");

    private static final Map<Integer, MediaElement> _mediaPool = new ConcurrentHashMap<>();
    private static MediaElement _hoveredMediaElement = null;

    private  CompletableFuture<Void> loadFuture ;
    private String _source;
    private volatile List<Identifier> _identifier = new ArrayList<>();
    private int _width;
    private int _height;
    private final UUID _elementId;
    private String _errorMessage;

    // animated element support
    private int _currentFrame = 0;
    private volatile List<Long> _frameDelays = new ArrayList<>();
    private boolean isAnimPlaying = false;
    private long animPlayingStartTime = -1;

    private MediaElement(String source, Identifier id) {
        this(source, new ArrayList<>(List.of(id)), true);
    }

    private MediaElement(String source, Identifier id, boolean saveToCache) {
        this(source, new ArrayList<>(List.of(id)), saveToCache);
    }

    private MediaElement(String source, List<Identifier> ids) {
        this(source, ids, true);
    }

    private MediaElement(String source, List<Identifier> ids, boolean saveToCache) {
        this._source = source;
        this._identifier = ids;
        this._elementId = UUID.randomUUID();
        this._errorMessage = null;

        if (source != null) {
            this.setIdentifier(MEDIA_LOADING, 64, 64);
            this.loadFuture = CompletableFuture.runAsync(() -> {
                try {
                    MediaIdentifierInfo mii = downloadMedia(source, saveToCache);
                    this.setIdentifier(mii.id(), mii.delays(), mii.width(), mii.height());
                    if (mii.delays != null) {
                        this.isAnimPlaying = true;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load media", e);
                }
            }).exceptionally(ex -> {
                LOGGER.error("Error in async download task: {}", ex.getMessage());
                return null;
            });
        }
    }

    public record MediaIdentifierInfo(List<Identifier> id, List<Long> delays, int width, int height) {
        public MediaIdentifierInfo(Identifier id, int width, int height) {
            this(List.of(id), null, width, height);
        }
    }

    public static MediaElement of(String source, boolean saveToCache) {
        MediaElement element =  _mediaPool.computeIfAbsent(source.hashCode(), s -> new MediaElement(source, MEDIA_LOADING, saveToCache));
        if (element._source == null) { // this is useful to add a source to images loaded from local cache
            element._source = source;
        }
        return element;
    }

    public static MediaElement of(String source) {
        return of(source, true);
    }

    // todo dieses gif funktioniert nicht, warum? https://s1882.pcdn.co/wp-content/uploads/VoaBStransp.gif
    private MediaIdentifierInfo downloadMedia(String source, boolean saveToCache) {
        try {
            URL url = new URI(source).toURL();

            FileSharingService service = FileSharingService.getDownloadServiceFor(url);
            DownloadedMedia downloadedMedia = service.downloadWithChecks(url);

            if (downloadedMedia != null && !downloadedMedia.hasError()) {
                List<BufferedImage> frames = downloadedMedia.getDownloadedMedia();
                if (!frames.isEmpty() && frames.getFirst().getWidth() > 0 && frames.getFirst().getHeight() > 0) {
                    if (CONFIG.cacheMedia() && saveToCache) {
                        switch (downloadedMedia) {
                            case DownloadedGif g:
                                CacheManager.saveGifToCache(g.getOriginalGif(), source);
                                break;
                            default:
                                CacheManager.saveMediaToCache(frames.getFirst(), source, downloadedMedia.getFormatName());
                                break;
                        }
                    }

                    //################################################
                    List<Identifier> identifiers = new ArrayList<>();
                    for (int i = 0; i < frames.size(); i++) {
                        BufferedImage image = frames.get(i);
                        Identifier id = registerTexture(image, source.hashCode()+"_"+i);
                        identifiers.add(id);
                    }

                    List<Long> delays = null;
                    if (downloadedMedia instanceof DownloadedGif) { delays = ((DownloadedGif) downloadedMedia).getDelays(); }

                    return new MediaIdentifierInfo(identifiers, delays, frames.getFirst().getWidth(), frames.getFirst().getHeight());
                } else {
                    return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512);
                }
            } else if (downloadedMedia != null) {
                _errorMessage = downloadedMedia.getErrorMessage();
                switch (downloadedMedia.getDownloadError()) {
                    case FORMAT -> {
                        return new MediaIdentifierInfo(MEDIA_UNSUPPORTED, 512, 512);
                    }
                    case SIZE -> {
                        return new MediaIdentifierInfo(MEDIA_UNSUPPORTED, 512, 512); // todo add error image
                    }
                    case WHITELIST -> {
                        return new MediaIdentifierInfo(MEDIA_NOT_WHITELISTED, 512, 512);
                    }
                    default -> {
                        return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512);
                    }
                }
            } else {
                _errorMessage = I18n.translate("text.mediachat.media.tooltip.genericError");
                return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512);
            }

        } catch (IOException e) {
            // handle IOException
            LOGGER.error("Error Downloading Image: \n"+e.getMessage());
            setErrorMessage("Error Downloading Image: \n"+e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error while registering image: \n" + e.getMessage());
            setErrorMessage("Error while registering image: \n"+e.getMessage());
        }
        return new MediaIdentifierInfo(Identifier.of(MOD_ID, "textures/image.png"), 64, 64);
    }



    // this is intended for loading media that was saved to disk on startup. //todo check if the url may be needed (e. g. whitelist checking)
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

    public void setErrorMessage(String errorMessage) {
        _errorMessage = errorMessage;
    }

    public Identifier currentFrame() {
        if (isAnimPlaying && _frameDelays != null) { determineCurrentFrame(); }
        return _identifier.get(_currentFrame);
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

    public String source() {
        return _source;
    }

    public UUID elementId() {
        return _elementId;
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

    public boolean isLoading() {
        return this.currentFrame().equals(MEDIA_LOADING);
    }

    private void setIdentifier(Identifier id, int width, int height) {
        List<Identifier> ids = new ArrayList<>();
        ids.add(id);
        this.setIdentifier(new ArrayList<>(List.of(id)), null, width, height);
        this.isAnimPlaying = false;
    }

    private void setIdentifier(List<Identifier> ids, List<Long> delays, int width, int height) {
        if (delays != null && ids.size() != delays.size()) {throw new IllegalArgumentException("Error setting MediaElement Identifier:\nThe 'ids' and 'delays' list must have the same size (or null)");}
        this._identifier = ids;
        this._width = width;
        this._height = height;
        this._frameDelays = delays;
        this.isAnimPlaying = true;
    }

    public static void reactToMouseClick(int button, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        MediaElement hovered = hovered();
        if (hovered != null) {
            if (button == 0 && action == 1) {
                client.setScreen(new MediaViewScreen(hovered()));
            } else if (button == 1 && action == 1) {
                if (Screen.hasShiftDown()) {
                    Utility.insertStringAtCursorPos(CONFIG.startMediaUrl() + hovered.source() + CONFIG.endMediaUrl());
                } else {
                    MinecraftClient.getInstance().keyboard.setClipboard(hovered.source());
                }
            }
        }
    }

    public Text getTooltip() {
        StringBuilder tooltip = new StringBuilder();
        if (_errorMessage != null) {
            tooltip.append("§4Error:§c ").append(_errorMessage).append("\n");
        }
        tooltip.append("§eSource:§b ").append((_source!=null ? _source : "Local Cache")).append("\n");

        // todo actually implement these keybinds
        String keybinds = "§7<Leftclick>§8 open image\n" +
                "§7<Rightclick>§8 copy URL\n" +
                "§7<Shift+Leftclick>§8 reload Image\n" +
                "§7<Shift+Rightclick>§8 Insert URL";
        tooltip.append(keybinds);
        return Text.of(tooltip.toString());
    }


    public static void renderTooltip() {
        if (hovered() != null) {
            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
            if (currentScreen instanceof ChatScreen chatScreen) {
                chatScreen.setTooltip(hovered().getTooltip());
            }
        }
    }
}
