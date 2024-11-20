package com.zappic3.mediachat.filesharing.filesharing;

import com.sksamuel.scrimage.nio.AnimatedGif;

import java.awt.image.BufferedImage;
import java.util.List;

public class DownloadedGif extends DownloadedMedia {
    private final List<Long> _delays;
    private final AnimatedGif _originalGif;

    public DownloadedGif(List<BufferedImage> frames, List<Long> delays, AnimatedGif originalGif) {
        super(frames, "gif");
        _delays = delays;
        _originalGif = originalGif;
    }

    public DownloadedGif(DownloadedMedia.DownloadError error, String[] errorMessageVals) {
        super(error, errorMessageVals);
        _delays = null;
        _originalGif = null;
    }

    public List<Long> getDelays() {
        return _delays;
    }

    public AnimatedGif getOriginalGif() {
        return _originalGif;
    }
}
