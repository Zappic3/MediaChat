package com.zappic3.mediachat.filesharing.filesharing;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.ImageSource;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

public class DefaultWebDownload extends FileSharingService{
    public DefaultWebDownload() {}

    protected DownloadedMedia download(URI uri) {
        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            connection.connect();

            String contentType = connection.getContentType();
            int contentLength = connection.getContentLength();

            if (contentType.startsWith("image") && contentLength > 0 && contentLength <= CONFIG.maxMediaSize() * 1024 * 1024) {  // convert mb to byte
                ImageInputStream imageStream = ImageIO.createImageInputStream(url.openStream());

                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
                if (!readers.hasNext()) {
                    imageStream.close();
                    return new DownloadedMedia(DownloadedMedia.DownloadError.FORMAT); //todo check if this translation actually works
                }

                ImageReader reader = readers.next();
                String formatName = reader.getFormatName().toLowerCase();

                List<BufferedImage> frames = new ArrayList<>();
                List<Long> delays = null;

                // save in case it needs to be saved to disk
                AnimatedGif gif = null;

                switch (formatName) {
                    case "gif":
                        delays = new ArrayList<>();
                        gif = AnimatedGifReader.read(ImageSource.of(imageInputStreamToInputStream(imageStream)));
                        for (int i = 0; i < gif.getFrameCount(); i++) {
                            ImmutableImage currentFrame = gif.getFrame(i);
                            frames.add(i, currentFrame.toNewBufferedImage(currentFrame.getType()));
                            delays.add(gif.getDelay(i).toMillis());
                        }
                        break;
                    default:
                        reader.setInput(imageStream);
                        BufferedImage image = reader.read(0);
                        frames.add(image);
                }
                imageStream.close();

                if (!frames.isEmpty() && frames.getFirst().getWidth() > 0 && frames.getFirst().getHeight() > 0) {
                    switch (formatName) {
                        case "gif":
                            return new DownloadedGif(frames, delays, gif);
                        default:
                            return new DownloadedMedia(frames, formatName);
                    }
                } else {
                    return new DownloadedMedia(DownloadedMedia.DownloadError.GENERIC);
                }

            } else {
                if (!contentType.startsWith("image")) {
                    return new DownloadedMedia(DownloadedMedia.DownloadError.FORMAT, new String[]{contentType});
                }
                return new DownloadedMedia(DownloadedMedia.DownloadError.SIZE, new String[] {((contentLength / 1024) / 1024) + "mb"});
            }
        } catch (SocketTimeoutException e) {
            LOGGER.error("Error while downloading media element.\n" +
                    "Make sure you are connected to the internet");
            return new DownloadedMedia(DownloadedMedia.DownloadError.INTERNET);
        } catch (UnknownHostException e) {
            LOGGER.error("Unknown host exception for {}", e.getMessage());
            return new DownloadedMedia(DownloadedMedia.DownloadError.INTERNET,new String[]{e.getMessage()});
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
            return new DownloadedMedia(DownloadedMedia.DownloadError.GENERIC, new String[]{e.getMessage()});
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
