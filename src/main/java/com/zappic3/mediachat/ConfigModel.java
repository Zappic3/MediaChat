package com.zappic3.mediachat;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Modmenu(modId = "media-chat")
@Config(name = "mediachat", wrapperName = "MediaChatConfig")
public class ConfigModel {
    @SectionHeader("generalOptions")
    @RangeConstraint(min = 200, max = 50000)
    public int maxRamUsage = 1000;

    @RangeConstraint(min = 3, max = 15)
    public int mediaChatHeight = 5;
    @RangeConstraint(min = 0.1F, max = 1.0F)
    public float maxMediaWidth = 1.0F;

    @PredicateConstraint("maxMediaSizePredicate")
    public int maxMediaSize = 30;

    public Boolean cacheMedia = true;

    public int maxCacheSize = 200;

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
    public serverMediaNetworkingMode serverNetworkingMode = serverMediaNetworkingMode.FILES_ONLY;

    public boolean confirmUploadPopup = true;

    public fileSharingServiceEnum hostingService = fileSharingServiceEnum.FILEBIN_NET;

    public boolean useWhitelist = false;
    //@RegexConstraint("^(?!.*\\.\\.)(?!.*(^\\.|\\.$)).+?\\..+$")
    @Hook
    public List<String> whitelistedWebsites = new ArrayList<>(List.of("tenor.com")); // todo add constraints

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
    // ALL: everything is downloaded by the server and distributed to the clients
    public enum serverMediaNetworkingMode {
        NONE, FILES_ONLY, ALL
    }

    public enum fileSharingServiceEnum {
        FILEBIN_NET
    }
}
