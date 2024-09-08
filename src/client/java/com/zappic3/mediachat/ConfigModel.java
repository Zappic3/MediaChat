package com.zappic3.mediachat;

import io.wispforest.owo.config.annotation.*;

@Modmenu(modId = "media-chat")
@Config(name = "media-chat-config", wrapperName = "MediaChatConfig")
public class ConfigModel {
    @RangeConstraint(min = 3, max = 15)
    public int mediaChatHeight = 5;
    @RangeConstraint(min = 0.1F, max = 1.0F)
    public float maxMediaWidth = 1.0F;
}
