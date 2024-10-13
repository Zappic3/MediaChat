package com.zappic3.mediachat.filesharing;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class DownloadedMedia {
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

    public DownloadedMedia(DownloadError downloadError, String errorMessage) {
        this(null, null, downloadError, errorMessage);
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

    /**
     * Generic - unknown cause of Error
     * WHITELIST - requested URL is not whitelisted
     * SIZE - requested media exceeds the size limit
     * FORMAT - media is not a supported format
     * API - an API returned an error code
     */
    public enum DownloadError {
        GENERIC, WHITELIST, SIZE, FORMAT, API
    }
}
