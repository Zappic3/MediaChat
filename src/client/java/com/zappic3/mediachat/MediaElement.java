package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import com.zappic3.mediachat.ui.MediaViewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.sksamuel.scrimage.ImmutableImage;

import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.MediaChatClient.CLIENT_CACHE;
import static com.zappic3.mediachat.Utility.*;

public class MediaElement {
    private static final Identifier MEDIA_LOADING =  Identifier.of(MOD_ID, "textures/media_loading.png");
    private static final Identifier MEDIA_UNSUPPORTED =  Identifier.of(MOD_ID, "textures/media_unsupported.png");
    private static final Identifier MEDIA_DOWNLOAD_FAILED =  Identifier.of(MOD_ID, "textures/media_download_failed.png");
    private static final Identifier MEDIA_NOT_WHITELISTED =  Identifier.of(MOD_ID, "textures/media_not_whitelisted.png");
    private static final Identifier MEDIA_TOO_BIG =  Identifier.of(MOD_ID, "textures/media_too_big.png");
    private static final Identifier MEDIA_NO_INTERNET =  Identifier.of(MOD_ID, "textures/media_no_internet.png");
    private static final Identifier MEDIA_DOWNLOADING_FROM_SERVER = Identifier.of(MOD_ID, "textures/media_downloading_from_server.png");

    private static final Map<Integer, MediaElement> _mediaPool = new ConcurrentHashMap<>();
    private static MediaElement _hoveredMediaElement = null;


    private  CompletableFuture<Void> loadFuture ;
    private String _source;
    private volatile List<Identifier> _identifier = new ArrayList<>();
    private int _width;
    private int _height;
    private final UUID _elementId;
    private String _errorMessage;

    // this is used to determine which textures to unload to free up ram
    private long _lastTimeUsed;
    private final Importance _importance;
    private long _sizeInBit; // important: a value of -1 means that this MediaElement uses an internal texture that should never be unloaded
    public enum Importance {
        HIGH(Duration.ofMinutes(30)),
        NORMAL(Duration.ofMinutes(10)),
        LOW(Duration.ofSeconds(30));

        public final Duration duration;
        private Importance(Duration duration) {
            this.duration = duration;
        }
    }

    // animated element support
    private int _currentFrame = 0;
    private volatile List<Long> _frameDelays = new ArrayList<>();
    private boolean isAnimPlaying = false;
    private long animPlayingStartTime = -1;

    private MediaElement(String source, Identifier id, long sizeInBits) {
        this(source, new ArrayList<>(List.of(id)), true, Importance.NORMAL, sizeInBits);
    }

    private MediaElement(String source, Identifier id, boolean saveToCache, long sizeInBits) {
        this(source, new ArrayList<>(List.of(id)), saveToCache, Importance.NORMAL, sizeInBits);
    }

    private MediaElement(String source, Identifier id, Importance importance, long sizeInBits) {
        this(source, new ArrayList<>(List.of(id)), true, importance, sizeInBits);
    }

    private MediaElement(String source, Identifier id, boolean saveToCache, Importance importance, long sizeInBits) {
        this(source, new ArrayList<>(List.of(id)), saveToCache, importance, sizeInBits);
    }

    private MediaElement(String source, List<Identifier> ids, long sizeInBits) {
        this(source, ids, true, Importance.NORMAL, sizeInBits);
    }

    private MediaElement(String source, List<Identifier> ids, boolean saveToCache, Importance importance, long sizeInBit) {
        this._source = source;
        this._identifier = ids;
        this._elementId = UUID.randomUUID();
        this._errorMessage = null;
        this._lastTimeUsed = System.currentTimeMillis();
        this._importance = importance;
        this._sizeInBit = sizeInBit;

        if (source != null) {
            this.setIdentifier(MEDIA_LOADING, 64, 64, -1);
            this.loadFuture = CompletableFuture.runAsync(() -> {
                try {
                    MediaIdentifierInfo mii = downloadMedia(source, saveToCache);
                    this.setIdentifier(mii.id(), mii.delays(), mii.width(), mii.height(), mii.sizeInBit());
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

    public record MediaIdentifierInfo(List<Identifier> id, List<Long> delays, int width, int height, long sizeInBit) {
        public MediaIdentifierInfo(Identifier id, int width, int height, long sizeInBit) {
            this(List.of(id), null, width, height, sizeInBit);
        }
    }

    public static MediaElement of(String source, boolean saveToCache, Importance importance) {
        MediaElement element =  _mediaPool.computeIfAbsent(source.hashCode(), s -> new MediaElement(source, MEDIA_LOADING, saveToCache, importance, -1));
        if (element._source == null) { // this is useful to add a source to images loaded from local cache
            element._source = source;
        }
        element._lastTimeUsed = System.currentTimeMillis();
        return element;
    }

    public static MediaElement of(String source, boolean saveToCache) {
        return of(source, saveToCache, Importance.NORMAL);
    }

    public static MediaElement of(String source) {
        return of(source, true, Importance.NORMAL);
    }

    /**
     * Changes the {@link Identifier} of an already existing {@link MediaElement}
     * and all its associated information
     *
     * @param hash hash of the {@link MediaElement} that should be changed
     * @param id the new {@link Identifier}
     * @param width width of the image in px
     * @param height height of the image in px
     * @param sizeInBit filesize of the image in bits
     */
    public static void update(int hash, Identifier id, int width, int height, long sizeInBit) {
        MediaElement element = _mediaPool.get(hash);
        if (element != null) {
            element.setIdentifier(id, width, height, sizeInBit);
        }
    }

    public static void update(int hash, List<Identifier> ids, List<Long> delays, int width, int height, long sizeInBit) {
        MediaElement element = _mediaPool.get(hash);
        if (element != null) {
            element.setIdentifier(ids, delays, width, height, sizeInBit);
        }
    }

    public static void update(int hash, DownloadedMedia.DownloadError error) {
        MediaElement element = _mediaPool.get(hash);
        if (element != null) {
            MediaIdentifierInfo mii = getErrorImageIdentifier(error);
            element.setIdentifier(mii.id(), mii.delays(), mii.width(), mii.height(), mii.sizeInBit());
            DownloadedMedia media = new DownloadedMedia(error); // little trick to get translated error messages
            element.setErrorMessage(media.getErrorMessage());
        }
    }

    // todo dieses gif funktioniert nicht, warum? https://s1882.pcdn.co/wp-content/uploads/VoaBStransp.gif
    private MediaIdentifierInfo downloadMedia(String source, boolean saveToCache) {
        DownloadedMedia downloadedMedia;

        try {
            if (CLIENT_CACHE.isFileInCache(source.hashCode())) {
                OneOfTwo<BufferedImage, AnimatedGif> result =  CLIENT_CACHE.loadFileFromCache(source.hashCode());
                if (result != null) {
                    if (result.getFirst() != null) {
                        Utility.IdentifierAndSize ias = registerTexture(result.getFirst(), source.hashCode()+"_"+0);
                        return new MediaIdentifierInfo(ias.identifier(), result.getFirst().getWidth(), result.getFirst().getHeight(), calculateTextureMemoryUsage(result.getFirst()));

                    } else if (result.getSecond() != null) {
                        AnimatedGif gif = result.getSecond();
                        List<Long> delays = new ArrayList<>();
                        List<Identifier> identifiers = new ArrayList<>();
                        long totalSize = 0;
                        Utility.IdentifierAndSize ias;
                        for (int i = 0; i < gif.getFrameCount(); i++) {
                            ImmutableImage currentFrame = gif.getFrame(i);
                            BufferedImage image = currentFrame.toNewBufferedImage(currentFrame.getType());
                            ias = registerTexture(image, source.hashCode()+"_"+i);
                            identifiers.add(ias.identifier());
                            totalSize += ias.size();
                            delays.add(gif.getDelay(i).toMillis());
                        }
                        return new MediaIdentifierInfo(identifiers, delays, gif.getFrame(0).width, gif.getFrame(0).height, totalSize);
                    }
                }
            }

            // check if the downloading should be handled server-side
            if (!MinecraftClient.getInstance().isInSingleplayer() && (CONFIG.serverNetworkingMode().equals(ConfigModel.serverMediaNetworkingMode.ALL) || CONFIG.serverNetworkingMode().equals(ConfigModel.serverMediaNetworkingMode.LINKS_ONLY))) {
                LOGGER.info("IMAGE SHOULD BE DOWNLOADED BY SERVER");
                MEDIA_CHANNEL.clientHandle().send(new NetworkManager.ServerboundMediaSyncRequestDownloadPacket(source));
                return new MediaIdentifierInfo(MEDIA_DOWNLOADING_FROM_SERVER, 64, 64, -1);
            }

            URI uri = new URI(source);
            FileSharingService service = FileSharingService.getDownloadServiceFor(uri);
            downloadedMedia = service.downloadWithChecks(uri.toURL());

            if (downloadedMedia != null && !downloadedMedia.hasError()) {
                List<BufferedImage> frames = downloadedMedia.getDownloadedMedia();
                if (!frames.isEmpty() && frames.getFirst().getWidth() > 0 && frames.getFirst().getHeight() > 0) {
                    if (CONFIG.cacheMedia() && saveToCache) {
                        switch (downloadedMedia) {
                            case DownloadedGif g:
                                CLIENT_CACHE.saveGifToCache(g.getOriginalGif(), source.hashCode());
                                break;
                            default:
                                CLIENT_CACHE.saveMediaToCache(frames.getFirst(), source.hashCode(), downloadedMedia.getFormatName());
                                break;
                        }
                    }

                    //################################################
                    List<Identifier> identifiers = new ArrayList<>();
                    int totalSize = 0;
                    for (int i = 0; i < frames.size(); i++) {
                        BufferedImage image = frames.get(i);
                        Utility.IdentifierAndSize ias = registerTexture(image, source.hashCode()+"_"+i);
                        totalSize += ias.size();
                        identifiers.add(ias.identifier());
                    }

                    List<Long> delays = null;
                    if (downloadedMedia instanceof DownloadedGif) { delays = ((DownloadedGif) downloadedMedia).getDelays(); }

                    return new MediaIdentifierInfo(identifiers, delays, frames.getFirst().getWidth(), frames.getFirst().getHeight(), totalSize);
                } else {
                    return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512, -1);
                }
            } else if (downloadedMedia != null) {
                this.setErrorMessage(downloadedMedia.getErrorMessage());
                return getErrorImageIdentifier(downloadedMedia.getDownloadError());

            } else {
                this.setErrorMessage(I18n.translate("text.mediachat.media.tooltip.genericError"));
                return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512, -1);
            }

        } catch (IOException e) {
            // handle IOException //todo replace strings with translation keys
            LOGGER.error("Error Downloading Image: \n"+e.getMessage());
            setErrorMessage("Error Downloading Image: \n"+e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error while registering image: \n" + e.getMessage());
            setErrorMessage("Error while registering image: \n"+e.getMessage());
        }
        return new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512, -1);
    }

    // this is intended for loading media that was saved to disk on startup. //todo check if the url may be needed (e. g. whitelist checking)
    public static void add(Identifier id, int hashCode, int width, int height, long sizeInBit) {
        MediaElement e = new MediaElement(null, id, sizeInBit);
        e.setIdentifier(id, width, height, sizeInBit);
        _mediaPool.put(hashCode, e);
    }

    /**
     * Removes the texture(s) associated with the {@link MediaElement} from the {@link TextureManager}
     * and then removes all internal references to the {@link MediaElement}, so everything can be garbage collected.
     */
    public void remove() {
        for (Identifier id : _identifier) {
            unregisterTexture(id);
        }
        if (this.source() != null) {
            _mediaPool.remove(this.source().hashCode());
        }
    }

    /**
     * Iterates over all currently existing  {@link MediaElement} Objects
     * and determines if they should be unloaded, based on the last time they were referenced and
     * their {@link Importance}.
     */
    public static void removeUnusedElements() {
        long currentTime = System.currentTimeMillis();
        Duration minNotUsed = Duration.ofSeconds(5);

        List<MediaElement> elementsToRemove = new ArrayList<>(_mediaPool.values().stream()
                .filter(media -> media.timeUntilRemoval(currentTime) <= 0 && media._sizeInBit != -1)
                .toList());
        int removedElementsCount = elementsToRemove.size();
        for (MediaElement media : elementsToRemove) {
            media.remove();
        }

        long totalSize = totalMediaElementSize();

        // if we are above the ram limit, remove more elements until we aren't
        long ramLimit = (long) CONFIG.maxRamUsage() * 1_048_576 * 8; // convert mb to bit
        if (totalSize > ramLimit) {
            elementsToRemove.clear();

            List<MediaElement> sortedByRemovalTime = _mediaPool.values().stream()
                    .sorted(Comparator.comparingLong(media -> media.timeUntilRemoval(currentTime)))
                    .toList();

            Iterator<MediaElement> iterator = sortedByRemovalTime.iterator();
            while (totalSize > ramLimit && iterator.hasNext()) {
                MediaElement media = iterator.next();
                if (media._lastTimeUsed + minNotUsed.toMillis() < currentTime) {
                    elementsToRemove.add(media);
                    totalSize -= media.sizeInBit();
                }
            }
            removedElementsCount += elementsToRemove.size();
            for (MediaElement media : elementsToRemove) {
                media.remove();
            }

            LOGGER.info("Removed {} unused media element(s) from memory (RAM limit reached)", removedElementsCount);
        } else {
            if (removedElementsCount > 0) {
                LOGGER.info("Removed {} unused media element(s) from memory", removedElementsCount);
            }
        }
    }

    public static long totalMediaElementSize() {
        return _mediaPool.values().stream()
                .filter(element -> element.sizeInBit() != -1)
                .mapToLong(MediaElement::sizeInBit)
                .sum();
    }

    public static int loadedMediaElementCount() {
        return _mediaPool.size();
    }

    private long timeUntilRemoval(long currentTime) {
        return (_lastTimeUsed + _importance.duration.toMillis()) - currentTime;
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

    public long sizeInBit() {
        return _sizeInBit;
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

    private void setIdentifier(Identifier id, int width, int height, long sizeInBit) {
        List<Identifier> ids = new ArrayList<>();
        ids.add(id);
        this.setIdentifier(new ArrayList<>(List.of(id)), null, width, height, sizeInBit);
        this.isAnimPlaying = false;
    }

    private void setIdentifier(List<Identifier> ids, List<Long> delays, int width, int height, long sizeInBit) {
        if (delays != null && ids.size() != delays.size()) {throw new IllegalArgumentException("Error setting MediaElement Identifier:\nThe 'ids' and 'delays' list must have the same size (or null)");}
        this._identifier = ids;
        this._width = width;
        this._height = height;
        this._frameDelays = delays;
        this._sizeInBit = sizeInBit;
        this.isAnimPlaying = true;
    }

    private static MediaIdentifierInfo getErrorImageIdentifier(DownloadedMedia.DownloadError error) {
        return switch (error) {
            case FORMAT -> new MediaIdentifierInfo(MEDIA_UNSUPPORTED, 512, 512, -1);
            case SIZE -> new MediaIdentifierInfo(MEDIA_TOO_BIG, 512, 512, -1);
            case WHITELIST -> new MediaIdentifierInfo(MEDIA_NOT_WHITELISTED, 512, 512, -1);
            case INTERNET -> new MediaIdentifierInfo(MEDIA_NO_INTERNET, 512, 512, -1);
            default -> new MediaIdentifierInfo(MEDIA_DOWNLOAD_FAILED, 512, 512, -1);
        };
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

        if (_sizeInBit != -1) {
            tooltip.append("§eSize:§b ").append(formatBits(this.sizeInBit())).append("\n");
        }

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
