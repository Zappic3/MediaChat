package com.zappic3.mediachat;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.*;

@Modmenu(modId = "media-chat")
@Config(name = "mediachat", wrapperName = "MediaChatConfig")
public class ConfigModel {
    @RangeConstraint(min = 3, max = 15)
    public int mediaChatHeight = 5;
    @RangeConstraint(min = 0.1F, max = 1.0F)
    public float maxMediaWidth = 1.0F;

    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String startMediaUrl = "[";
    @Sync(Option.SyncMode.OVERRIDE_CLIENT)
    @RegexConstraint("\\S{1,10}")
    public String endMediaUrl = "]";

    @Nest
    public DebugOptions debugOptions = new DebugOptions();

    public static class DebugOptions {
        public boolean renderImages = true;
        public boolean renderHiddenChatMessages = false;
        public boolean displayScissorArea = false;
        @RestartRequired
        public boolean useNameInsteadOfID = false;
    }
}
