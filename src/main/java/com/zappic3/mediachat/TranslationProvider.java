package com.zappic3.mediachat;

@FunctionalInterface
public interface TranslationProvider {
    String translate(String key, Object... args);
}
