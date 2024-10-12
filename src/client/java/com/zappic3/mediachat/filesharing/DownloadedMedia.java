package com.zappic3.mediachat.filesharing;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class DownloadedMedia {
    protected final List<BufferedImage> _downloadedMedia;
    protected final DownloadError _downloadError;
    protected final String _formatName;

    private DownloadedMedia(List<BufferedImage> downloadedImage, String formatName, DownloadError downloadError) {
        _downloadedMedia = downloadedImage;
        _formatName = formatName;
        _downloadError = downloadError;
    }

    public DownloadedMedia(List<BufferedImage> downloadedImage, String formatName) {
        this(downloadedImage, formatName, null);
    }

    public DownloadedMedia(BufferedImage downloadedImage, String formatName) {
        this(new ArrayList<>(List.of(downloadedImage)), formatName, null);
    }

    public DownloadedMedia(DownloadError downloadError) {
        this(null, null, downloadError);
    }

    public boolean hasError() {
        return _downloadError != null;
    }

    public DownloadError getDownloadError() {
        return _downloadError;
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
     */
    public enum DownloadError {
        GENERIC, WHITELIST, SIZE, FORMAT
    }
}
