package com.zappic3.mediachat.ui;

import com.zappic3.mediachat.TenorService;
import com.zappic3.mediachat.Utility;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.layers.Layers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.zappic3.mediachat.MediaChat.*;

public class GifBrowserUI extends BaseOwoScreen<FlowLayout> {
    private static final Identifier NO_INTERNET =  Identifier.of(MOD_ID, "textures/media_no_internet.png");
    private static final Identifier INVALID_API_KEY =  Identifier.of(MOD_ID, "textures/media_too_big.png");

    private static final Text searchBarDefaultText = Text.translatable("text.mediachat.gifBrowser.searchbarPlaceholder");
    private boolean searchbarIsDefault;
    private static GifBrowserUI _currentInstance;
    private FlowLayout _root;
    private final FlowLayout _gifBrowser;
    private final ButtonComponent _gifButton;
    private final LoadingLabelComponent _loadingLabel;
    private boolean _gifBrowserOpen;

    private String currentSearchTerm;
    private CompletableFuture<TenorService.TenorQueryResponse<TenorService.SearchResponse>> currentSearchResponse;
    private CompletableFuture<Void> isDisplayingSearchDone; // this is used to check if everything chained to currentSearchResponse is also done

    private boolean _searchEnabled;

    public GifBrowserUI() {
        currentSearchTerm = "";
        currentSearchResponse = null;
        isDisplayingSearchDone = null;
        searchbarIsDefault = true;
        _currentInstance = this;
        _gifBrowserOpen = false;
        _gifBrowser = buildGifBrowser();
        _gifButton = buildGifButton();
        _loadingLabel = buildLoadingLabel();
        _searchEnabled = true;
    }

    public static GifBrowserUI getInstance() {
        return _currentInstance;
    }

    public static void addGifUIToChatScreen() {
        Layers.add(Containers::verticalFlow, instance -> new GifBrowserUI().build(instance.adapter.rootComponent), ChatScreen.class);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        _root = rootComponent;
        _root.child(_gifButton);
        _gifBrowserOpen = false;

    }

    private LoadingLabelComponent buildLoadingLabel() {
        LoadingLabelComponent label = new LoadingLabelComponent(Text.of("Loading..."), () -> true);
        label.horizontalTextAlignment(HorizontalAlignment.CENTER)
                .verticalTextAlignment(VerticalAlignment.CENTER)
                .sizing(Sizing.fill(), Sizing.fixed(100))
                .id("load-more");
        return label;
    }

    private FlowLayout buildGifBrowser() {
        FlowLayout layout = Containers.verticalFlow(Sizing.fill(30), Sizing.fill(70));
        TextBoxComponent searchbar = Components.textBox(Sizing.fill()).text(getSearchBarDefaultString());
        searchbar.id("searchbar");
        searchbar.onChanged().subscribe((newText) -> {
                    if (_searchEnabled) {
                        String cleanedText = TenorService.sanitizeQuery(newText);
                        if (!cleanedText.equals(getSearchBarDefaultString()) && !cleanedText.isEmpty() && !cleanedText.equals(currentSearchTerm)) {
                            if (currentSearchResponse != null && !currentSearchResponse.isDone()) {
                                currentSearchResponse.cancel(true);
                            }
                            currentSearchTerm = cleanedText;
                            currentSearchResponse = TenorService.getSearchResults(cleanedText);
                            showGifSearchResults(currentSearchResponse, true);
                        } else if (!cleanedText.equals(currentSearchTerm)) {
                            showGifCategories(TenorService.getFeatured(), true);
                        }
                    }
        });

        layout.child(searchbar)
        .child(
                Containers.verticalScroll(Sizing.fill(), Sizing.expand(),
                        Containers.verticalFlow(Sizing.content(), Sizing.content()).child(
                            Containers.horizontalFlow(Sizing.fill(), Sizing.content())
                                    .child(Containers.verticalFlow(Sizing.expand(50), Sizing.content()).id("gif-container-left"))
                                    .child(Containers.verticalFlow(Sizing.expand(50), Sizing.content()).id("gif-container-right"))
                        ).sizing(Sizing.content(), Sizing.content()).id("gif-container-container")
                )
        )
        .surface(Surface.VANILLA_TRANSLUCENT)
        .positioning(Positioning.relative(100, 100))
        .margins(Insets.of(0,15, 0, 2))
        .id("browser-background");

        return layout;
    }

    private ButtonComponent buildGifButton() {
        ButtonComponent button = new UnfocusableButton(Text.of("Browse Gifs"), buttonComponent -> {
            openGifBrowser();
        });
        button.positioning(Positioning.relative(100, 100))
                .margins(Insets.bottom(14));
        button.renderer(ButtonComponent.Renderer.flat(0x606060, 0x303030, 0x111111));

        return button;
    }

    private void addLoadingLabel(LoadingLabelComponent.ExecuteAndCheck action) {
        FlowLayout layout = _root.childById(FlowLayout.class, "gif-container-container");
        if (layout.childById(LoadingLabelComponent.class, "load-more") == null) {
            layout.child(_loadingLabel);
        }
        _loadingLabel.onLoad(action);
    }

    private void removeLoadingLabel() {
        FlowLayout layout = _root.childById(FlowLayout.class, "gif-container-container");
        if (layout.childById(LoadingLabelComponent.class, "load-more") != null) {
            layout.removeChild(_loadingLabel);
        }
    }

    public void openGifBrowser() {
        _root.clearChildren();
        _root.child(_gifBrowser);
        _gifBrowserOpen = true;
        showGifCategories(TenorService.getFeatured(), true);
    }

    public void closeGifBrowser() {
        // clear searchbar (this is needed when the GIF browser is opened again without reopening the chat interface)
        _gifBrowser.childById(TextBoxComponent.class, "searchbar").text(getSearchBarDefaultString());
        _root.clearChildren();
        _root.child(_gifButton);
        _gifBrowserOpen = false;
    }

    /**
     * Clears the gif browser of all current gifs and displays gif categories provided by {@link TenorService.Category}.
     * The gif elements utilize {@link PressableGifComponent} to insert the category name into the gif searchbar when clicked.
     *
     * @param elements A {@link CompletableFuture} that provides an {@link Iterator<TenorService.Category>}. Every Category element will be added to the gif browser
     * @param addGifTitle whether to display the name of the category above over the gif.
     */
    private void showGifCategories (CompletableFuture<TenorService.TenorQueryResponse<Iterator<TenorService.Category>>> elements, boolean addGifTitle) {
        isDisplayingSearchDone = elements.thenAccept(queryResponse -> {
            clearGifWidgets();

            PreviewGifContainer preview = new PreviewGifContainer(0);
            FlowLayout column = _root.childById(FlowLayout.class, "gif-container-left");

            if (queryResponse.isConnected()) {
                Iterator<TenorService.Category> categories = queryResponse.getResponseValue();
                categories.forEachRemaining(category -> {
                    int columnWidth = column != null ? column.width() : 50;
                    preview.setColumnWidth(columnWidth);

                    String gifTitle;
                    if (addGifTitle) {
                        gifTitle = category.searchterm();
                    } else {
                        gifTitle = "";
                    }
                    List<Integer> sizes = new ArrayList<>();
                    sizes.add(50);
                    sizes.add(30);
                    preview.addGifWidget(category.image(), sizes, null, gifTitle, null);
                });

                // add ui elements on the main thread
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        _root.childById(FlowLayout.class, "gif-container-left").children(preview.getLeftGifs());
                        _root.childById(FlowLayout.class, "gif-container-right").children(preview.getRightGifs());
                        removeLoadingLabel();
                    } catch (Exception e) {
                        LOGGER.error("Failed to display gif categories", e);
                    }
                });
            } else {
                displayError(queryResponse.getConnectionStatus());
            }
        });
    }

    /**
     * A class that makes it possible to add gifs asynchronously to a list of sorts.
     * This is necessary to avoid the risk of a {@link java.util.ConcurrentModificationException} when modifying the UI
     * in CompletableFuture's
     */
    class PreviewGifContainer {
        private final List<Component> _leftGifs;
        private final List<Component> _rightGifs;
        private int _leftColumnHeight;
        private int _rightColumnHeight;
        private int _columnWidth;

        public PreviewGifContainer(int columnWidth) {
            _leftGifs = new ArrayList<>();
            _rightGifs = new ArrayList<>();
            _leftColumnHeight = _root.childById(FlowLayout.class,  "gif-container-left").height();
            _rightColumnHeight = _root.childById(FlowLayout.class,  "gif-container-right").height();
            _columnWidth = columnWidth;
        }

        public void addGifWidget(String sourceURL, List<Integer> dims, String bigGifUrl, String gifTitle, String gifId) {
            if (gifTitle == null) {gifTitle = "";}
            PressableGifComponent gifComponent = createGifWidget(sourceURL, bigGifUrl, gifTitle, gifId);
            int addedHeight = ((dims.get(1) * _columnWidth) / dims.get(0));
            gifComponent.verticalSizing(Sizing.fixed(addedHeight));

            if (_leftColumnHeight <= _rightColumnHeight) {
                _leftGifs.add(gifComponent);

                _leftColumnHeight += addedHeight;
            } else {
                _rightGifs.add(gifComponent);
                _rightColumnHeight += addedHeight;
            }
        }
        public void setColumnWidth(int columnWidth) {
            _columnWidth = columnWidth;
        }

        public List<Component> getRightGifs() {
            return _rightGifs;
        }
        public List<Component> getLeftGifs() {
            return _leftGifs;
        }
    }

    private void showGifSearchResults(CompletableFuture<TenorService.TenorQueryResponse<TenorService.SearchResponse>> response, boolean clearGifWidgets) {
        if (clearGifWidgets) {clearGifWidgets();}
        isDisplayingSearchDone = response.thenAccept((queryResponse) -> {
            PreviewGifContainer preview = new PreviewGifContainer(0);
            FlowLayout column = _root.childById(FlowLayout.class, "gif-container-left");

            if (queryResponse.isConnected()) {
                TenorService.SearchResponse results = queryResponse.getResponseValue();
                results.results().iterator().forEachRemaining((result) -> {
                    int columnWidth = column != null ? column.width() : 50;
                    preview.setColumnWidth(columnWidth);
                    TenorService.MediaFormat gifData = result.media_formats().tinygif();
                    preview.addGifWidget(gifData.url(), gifData.dims(), result.media_formats().mediumgif().url(), null, result.id());
                });
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        _root.childById(FlowLayout.class, "gif-container-left").children(preview.getLeftGifs());
                        _root.childById(FlowLayout.class, "gif-container-right").children(preview.getRightGifs());
                        addLoadingLabel(() -> {
                            if (isDisplayingSearchDone != null && isDisplayingSearchDone.isDone()) {
                                try {
                                    CompletableFuture<TenorService.TenorQueryResponse<TenorService.SearchResponse>> newResponse = TenorService.getSearchResults(currentSearchTerm, currentSearchResponse.get().getResponseValue().next());
                                    currentSearchResponse = newResponse;
                                    showGifSearchResults(newResponse, false);
                                } catch (Exception e) {
                                    LOGGER.error("Failed to load more gifs", e);
                                }
                            }
                            return true;
                        }); // setup loadingLabel for infinite scroll
                    } catch (Exception e) {
                        LOGGER.error("Failed to display gif search results", e);
                    }
                });
            } else {
                displayError(queryResponse.getConnectionStatus());
            }
        });

    }

    private void displayError(TenorService.connectionStatus connection) {
        _searchEnabled = false;
        Text rawMsg;
        Identifier errorImageIdentifier;

        if (connection.equals(TenorService.connectionStatus.INVALID_API_KEY)) {
            rawMsg = Text.translatable("text.mediachat.gifBrowser.apiKeyError");
            errorImageIdentifier = INVALID_API_KEY;
        } else if (connection.equals(TenorService.connectionStatus.NO_INTERNET)) {
            rawMsg = Text.translatable("text.mediachat.gifBrowser.connectionError");
            errorImageIdentifier = NO_INTERNET;
        } else {
            rawMsg = Text.of("??? - If you're seeing this, you found a new and exiting bug!");
            errorImageIdentifier = NO_INTERNET;
        }

        FlowLayout layout = Containers.verticalFlow(Sizing.fill(30), Sizing.fill(70));
        layout.surface(Surface.VANILLA_TRANSLUCENT)
                .positioning(Positioning.relative(100, 100));

        _root.clearChildren();
        _root.child(layout);

        int textMargin = 5;
        TextureComponent errorImage = Components.texture(errorImageIdentifier, 0, 0, 512, 512);
        errorImage.positioning(Positioning.relative(50, 0)).margins(Insets.of(textMargin));
        layout.child(errorImage);

        LabelComponent label = Components.label(rawMsg);
        label.maxWidth(layout.width()-(textMargin*2))
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .positioning(Positioning.relative(50, 25))
                .margins(Insets.of(textMargin));

        ButtonComponent reloadButton = Components.button(Text.of("Retry"), (button) -> {});
        reloadButton.positioning(Positioning.relative(50, 70));

        layout.child(label);
        layout.child(reloadButton);

        _gifBrowserOpen = false; // todo this is a quick-fix, because when the warning is closed, but the chat screen not exited, the gif category screen wont refresh for some reason

    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);

    }

    private void clearGifWidgets() {
        _root.childById(FlowLayout.class, "gif-container-left").clearChildren();
        _root.childById(FlowLayout.class, "gif-container-right").clearChildren();
    }

    public boolean isGifBrowserOpen() {
        return _gifBrowserOpen;
    }

    private PressableGifComponent createGifWidget(String sourceURL, String bigGifUrl, String title, String gifId) {
        PressableGifComponent component = new PressableGifComponent(sourceURL, (gifComponent) -> {
            if (title != null && !title.isEmpty()) {
                _root.childById(TextBoxComponent.class, "searchbar").text(title);
            } else {
                if (gifId != null && !gifId.isEmpty()) {
                    TenorService.registerShare(gifId);
                }

                String insertedUrl;
                if (bigGifUrl == null || bigGifUrl.isEmpty()) {
                    insertedUrl = sourceURL;
                } else {
                    insertedUrl = bigGifUrl;
                }

                Utility.insertStringAtCursorPos(CONFIG.startMediaUrl() + insertedUrl + CONFIG.endMediaUrl());
                closeGifBrowser();
            }
        }, bigGifUrl);
        component.margins(Insets.of(1));
        component.horizontalSizing(Sizing.expand());
        if (title != null && !title.isEmpty()) {
            component.setMessage(Text.literal(title).formatted(Formatting.BOLD));
            component.setAutoHeight(false);
        }

        return component;
    }

    private static String getSearchBarDefaultString() {
        return I18n.translate(searchBarDefaultText.getString());
    }

        private void updateSearchbar() {
        TextBoxComponent searchbar = _root.childById(TextBoxComponent.class, "searchbar");
        if (searchbar != null) {
            if (searchbar.isFocused() && searchbarIsDefault && searchbar.getText().equals(getSearchBarDefaultString())) {
                searchbarIsDefault = false;
                searchbar.text("");
            } else if (!searchbar.isFocused() && !searchbarIsDefault && searchbar.getText().isEmpty()) {
                searchbarIsDefault = true;
                searchbar.text(getSearchBarDefaultString());
            } else if (!searchbar.isFocused() && searchbarIsDefault && !searchbar.getText().equals(getSearchBarDefaultString())) {
                searchbarIsDefault = false;
            }
        }
    }

    public static void update() {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatScreen screen) {
            GifBrowserUI instance = GifBrowserUI.getInstance();
            if (instance != null && GifBrowserUI.getInstance()._gifBrowserOpen) {
                instance.updateSearchbar();
            }
        }
    }
}
