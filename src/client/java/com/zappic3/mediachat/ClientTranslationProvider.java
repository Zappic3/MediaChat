package com.zappic3.mediachat;

import net.minecraft.client.resource.language.I18n;

public class ClientTranslationProvider implements TranslationProvider {
    @Override
    public String translate(String key, Object... args) {
        return I18n.translate(key, args);

    }
}
