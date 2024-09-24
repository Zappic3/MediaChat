package com.zappic3.mediachat;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Modmenu(modId = "media-chat")
@Config(name = "mediachat", wrapperName = "MediaChatConfig")
public class ConfigModel {
    @SectionHeader("generalOptions")
    @RangeConstraint(min = 3, max = 15)
    public int mediaChatHeight = 5;
    @RangeConstraint(min = 0.1F, max = 1.0F)
    public float maxMediaWidth = 1.0F;

    @PredicateConstraint("maxMediaSizePredicate")
    public int maxMediaSize = 30;

    public Boolean cacheMedia = false;

    public int maxCacheSize = 200;


    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String startMediaUrl = "[";

    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String endMediaUrl = "]";

    @SectionHeader("privacyOptions")
    public boolean useWhitelist = false;

    //@RegexConstraint("^(?!.*\\.\\.)(?!.*(^\\.|\\.$)).+?\\..+$")
    @Hook
    public List<String> whitelistedWebsites = new ArrayList<>(List.of("imgur.com", "i.redd.it")); // todo add constraints

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
}
