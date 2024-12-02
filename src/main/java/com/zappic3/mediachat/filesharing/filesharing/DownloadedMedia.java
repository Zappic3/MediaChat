package com.zappic3.mediachat.filesharing.filesharing;

import com.zappic3.mediachat.TranslationProvider;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static com.zappic3.mediachat.MediaChat.CONFIG;

public class DownloadedMedia {
    private static TranslationProvider provider;

    protected final List<BufferedImage> _downloadedMedia;
    protected final DownloadError _downloadError;
    protected final String _errorMessage;
    protected final String _formatName;

    private DownloadedMedia(List<BufferedImage> downloadedImage, String formatName, DownloadError downloadError, String errorMessage) {
        _downloadedMedia = downloadedImage;
        _formatName = formatName;
        _downloadError = downloadError;
        _errorMessage = errorMessage;
    }

    public DownloadedMedia(List<BufferedImage> downloadedImage, String formatName) {
        this(downloadedImage, formatName, null, null);
    }

    public DownloadedMedia(BufferedImage downloadedImage, String formatName) {
        this(new ArrayList<>(List.of(downloadedImage)), formatName, null, null);
    }

    public DownloadedMedia(DownloadError downloadError) {
        this(downloadError, new String[]{});
    }

    public DownloadedMedia(DownloadError downloadError, String[] errorMessageVals) {
        this(null, null, downloadError, determineErrorMessage(downloadError, errorMessageVals));
    }

    public static void setTranslationProvider(TranslationProvider translationProvider) {
        provider = translationProvider;
    }

    public boolean hasError() {
        return _downloadError != null;
    }

    public DownloadError getDownloadError() {
        return _downloadError;
    }

    public String getErrorMessage() {
        return _errorMessage;
    }

    public List<BufferedImage> getDownloadedMedia() {
        return _downloadedMedia;
    }

    public String getFormatName() {
        return _formatName;
    }

    private static @NotNull String determineErrorMessage(DownloadError error, String[] values) {
        String noInfo = "'no info provided'";
        return switch (error) {
            case WHITELIST -> provider.translate("text.mediachat.media.tooltip.whitelistError");
            case SIZE ->     values.length == 0
                    ? provider.translate("text.mediachat.media.tooltip.sizeError", CONFIG.maxMediaSize() + "mb", noInfo)
                    : provider.translate("text.mediachat.media.tooltip.sizeError", CONFIG.maxMediaSize() + "mb", values[0]);
            case FORMAT ->   values.length == 0
                    ? provider.translate("text.mediachat.media.tooltip.formatError")
                    : provider.translate("text.mediachat.media.tooltip.formatError") + " (" + values[0] + ")" ;
            case API ->      values.length <= 1
                    ? provider.translate("text.mediachat.media.tooltip.apiError", noInfo, noInfo)
                    : provider.translate("text.mediachat.media.tooltip.apiError", values[0], values[1]);
            case INTERNET -> values.length == 0
                    ? provider.translate("text.mediachat.media.tooltip.internetError")
                    : provider.translate("text.mediachat.media.tooltip.unknownHostError", values[0]);
            default ->       values.length == 0
                    ? provider.translate("text.mediachat.media.tooltip.genericError")
                    : values[0];
        };
    }

    /**
     * <ul>
     *     <li>GENERIC - unknown cause of Error (s)</li>
     *     <li>WHITELIST - requested URL is not whitelisted</li>
     *     <li>SIZE - requested media exceeds the size limit</li>
     *     <li>FORMAT - media is not a supported format (s)</li>
     *     <li>API - an API returned an error code (s)</li>
     *     <li>INTERNET - no internet connection</li>
     * </ul>
     * (s mark the errors that need to be handled on the dedicated server side)
     */
    public enum DownloadError {
        GENERIC, WHITELIST, SIZE, FORMAT, API, INTERNET
    }
}
