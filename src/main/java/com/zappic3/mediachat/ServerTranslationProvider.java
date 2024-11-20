package com.zappic3.mediachat;

public class ServerTranslationProvider implements TranslationProvider {
    @Override
    public String translate(String key, Object... args) {
        return "[Server] " + key;
    }
}
