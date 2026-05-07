package com.zappic3.mediachat;

import blue.endless.jankson.Comment;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Modmenu(modId = "media-chat")
@Config(name = "mediachat", wrapperName = "MediaChatConfig")
public class ConfigModel {
    // server side config options
    @ExcludeFromScreen
    @Comment("Server Config Options:")
    public int serverMaxCacheSize = 200; // value in mb

    @ExcludeFromScreen
    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    public int serverMaxFileSize = 30; // value in mb

    @ExcludeFromScreen
    public List<PlayerListEntry> serverWhitelist = new ArrayList<>();

    @ExcludeFromScreen
    public List<PlayerListEntry> serverBlacklist = new ArrayList<>();

    @ExcludeFromScreen
    public ServerMediaPermissionMode serverMediaPermissionMode = ServerMediaPermissionMode.BLACKLIST;

    // client & server:
    @Comment("Client Config Options:")
    @SectionHeader("generalOptions")
    @RangeConstraint(min = 200, max = 50000)
    public int maxRamUsage = 1000; // value in mb

    @RangeConstraint(min = 3, max = 15)
    public int mediaChatHeight = 5;
    @RangeConstraint(min = 0.1F, max = 1.0F)
    public float maxMediaWidth = 1.0F;

    @PredicateConstraint("maxMediaSizePredicate")
    public int maxMediaSize = 30;

    public Boolean cacheMedia = true;

    public int maxCacheSize = 200; // value in mb

    public boolean displayDebugScreenInfos = true;

    public boolean autoWrapUrlsOnPasteShortcut = true;

    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String startMediaUrl = "[";

    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String endMediaUrl = "]";

    @SectionHeader("privacyOptions")
    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    public serverMediaNetworkingMode serverNetworkingMode = serverMediaNetworkingMode.ALL;
    //TODO: add the following options: WarnIfIpIsNotProtected, A an option to disable media from this server? an option to add the server to trust the server

    public FileSharingService.FileHostingService defaultHostingService = FileSharingService.FileHostingService.NONE;
    public boolean useLocalMinecraftServerForHostingIfPossible = true;

    public boolean useWhitelist = false;
    //@RegexConstraint("^(?!.*\\.\\.)(?!.*(^\\.|\\.$)).+?\\..+$")
    @Hook
    public List<String> whitelistedWebsites = new ArrayList<>(List.of("tenor.com")); // todo add constraints

    @SectionHeader("gifBrowserOptions")
    @Hook
    public TenorSupportedLanguages tenorLanguage = TenorSupportedLanguages.MINECRAFT;
    public String tenorApiKey = "";

    @SectionHeader("debugOptions")
    @Nest
    public DebugOptions debugOptions = new DebugOptions();

    public static class DebugOptions {
        public boolean renderImages = true;
        public boolean renderHiddenChatMessages = false;
        public boolean displayScissorArea = false;
        @RestartRequired
        public boolean useNameInsteadOfID = false;
    }

    public static boolean maxMediaSizePredicate(int maxMediaSize) {
        return maxMediaSize >0;
    }

    // NONE: clients download everything, files cant be directly shared through the server
    // FILES_ONLY: clients download media shared via url but files get uploaded to the server and distributed among clients
    // LINKS_ONLY: server downloads and distribute media shared via link but files cant be uploaded directly
    // ALL: everything is downloaded by the server and distributed to the clients
    public enum serverMediaNetworkingMode {
        NONE, FILES_ONLY, LINKS_ONLY, ALL
    }

    public enum ServerMediaPermissionMode {
        OFF, WHITELIST, BLACKLIST
    }

    /**
     * List of all languages supported by tenor.
     * <a href="https://developers.google.com/tenor/guides/localization">https://developers.google.com/tenor/guides/localization</a>
     */
    public enum TenorSupportedLanguages {
        MINECRAFT("minecraft"),
        ALBANIAN("sq"),
        ARABIC("ar"),
        BELARUSIAN("be"),
        BENGALI("bn"),
        BOSNIAN("bs"),
        CANTONESE("zh_HK"),
        CATALAN("ca"),
        CHINESE_SIMPLIFIED("zh_CN"),
        CHINESE_TRADITIONAL("zh_TW"),
        CROATIAN("hr"),
        CZECH("cd"),
        DANISH("da"),
        DUTCH("nl"),
        ENGLISH("en"),
        FARSI("fa"),
        FILIPINO("fil"), // Tenor Search isn't supported
        FINNISH("fi"),
        FRENCH("fr"),
        GERMAN("de"),
        GREEK("el"),
        HEBREW("he"),
        HINDI("hi"),
        HUNGARIAN("hu"),
        INDONESIAN("id"),
        ITALIAN("it"),
        JAPANESE("ja"),
        KOREAN("ko"),
        LAOS("lo"),
        MALAY("ms"),
        MONGOLIAN("mn"),
        NORWEGIAN_BOKMAL("no"),
        //NORWEGIAN_NYNORSK("nn"), // category translation isn't supported
        POLISH("pl"),
        PORTUGUESE("pt"),
        ROMANIAN("ro"),
        RUSSIAN("ru"),
        SERBIAN("sr"),
        SLOVAK("sk"),
        SPANISH("es"),
        SWEDISH("sv"),
        //TAGALOG("tl"), // category translation isn't supported
        THAI("th"),
        TURKISH("tr"),
        UKRAINIAN("uk"),
        URDU("ur"),
        VIETNAMESE("vi");

        public final String lang_code;

        TenorSupportedLanguages(String lang) {
            this.lang_code = lang;
        }
    }
}
