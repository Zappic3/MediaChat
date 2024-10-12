package com.zappic3.mediachat;

import com.zappic3.mediachat.MediaChatConfig;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

public class ConfigControls {
    private ConfigControls() {}
    public static void registerConfigObserver() {
        CONFIG.subscribeToWhitelistedWebsites(ConfigControls::formatWhitelistObserver);
    }

    public static void formatWhitelistObserver(List<String> whitelist) {
        for (int i = 0; i < whitelist.size(); i++) {
            String urlString = whitelist.get(i);
            try {
                urlString = urlString.replaceFirst("^(http://|https://)?(www\\.)?", "");

                int slashIndex = urlString.indexOf('/');
                if (slashIndex != -1) {
                    urlString = urlString.substring(0, slashIndex);
                }

                if (urlString.contains(".") && !urlString.endsWith(".") && !urlString.startsWith(".") && !urlString.contains("..")) {
                    whitelist.set(i, urlString);
                } else {
                    whitelist.set(i, null);
                }

            } catch (Exception e) {
                whitelist.set(i, null);
            }
        }
        whitelist.removeIf(Objects::isNull);
        CONFIG.whitelistedWebsites(whitelist);
    }
}
