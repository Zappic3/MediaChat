package com.zappic3.mediachat.filesharing.filesharing;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.ImageSource;
import com.zappic3.mediachat.RandomString;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

public class FilebinService extends FileSharingService implements FileSharingService.FileSharingUpload {
    private final RandomString randomString;
    private final String bucketName;
    private final String userId;
    private boolean _hasError = false;
    private String _errorMessage = null;

    public FilebinService() {
        randomString = new RandomString(20);
        bucketName = randomString.nextString();
        userId = randomString.nextString();
    }

    @Override
    public CompletableFuture<URI> upload(Path filePath) {
        //todo investigate 500 error when uploading files simultaneously
        String fileName = randomString.nextString();
        String predictedUrl = "https://filebin.net/"+bucketName+"/"+fileName;
        return CompletableFuture.supplyAsync(() -> {
            try (HttpClient client = HttpClient.newHttpClient()){
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(predictedUrl))
                        .header("accept", "application/json")
                        .header("cid", userId)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofFile(filePath))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201) {
                    LOGGER.info(response.body());
                    return URI.create("https://filebin.net/"+bucketName+"/"+fileName);
                } else {
                    LOGGER.warn("Error uploading file to Filebin.net:\n{}", response.statusCode());
                    _hasError = true;
                    _errorMessage = "Error uploading file to Filebin.net: " + response.statusCode();
                    // todo: retry upload on fail
                    return null;
                }

            }  catch (Exception e) {
                LOGGER.error("Error uploading file to Filebin.net:\n{}", e.getMessage());
                _hasError = true;
                _errorMessage = "Error uploading file to Filebin.net: " + e.getMessage();
                LOGGER.error(e.getMessage(), e);
            }
            _hasError = true;
            _errorMessage = "An error occurred while uploading file to Filebin.net";
            return null;
        });
    }


    protected DownloadedMedia download(URI uri) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpRequest filebinRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("accept", "*/*")
                .header("User-Agent", "Java HttpClient")
                .header("Cookie", "verified=2024-05-24") // todo automatically update this
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<InputStream> filebinResponse = client.send(filebinRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (filebinResponse.statusCode() != 302) {
                return new DownloadedMedia(DownloadedMedia.DownloadError.API, new String[] {"filebin.net", filebinResponse.statusCode()+""});
            }

            // download the actual file
            URI fileLocation = new URI(filebinResponse.headers().firstValue("location").orElse(""));

            LOGGER.info("Location: {}", fileLocation);

            HttpRequest mediaRequest = HttpRequest.newBuilder()
                .uri(fileLocation)
                .header("accept", "*/*")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<InputStream> mediaResponse = client.send(mediaRequest, HttpResponse.BodyHandlers.ofInputStream());


            LOGGER.info("Media Header: {}", mediaResponse.headers().toString());

            // todo: check for http codes (403, 404)
            String contentType = mediaResponse.headers().firstValue("Content-Type").orElse("");
            if (!contentType.startsWith("image/")) {
                client.shutdown();
                return new DownloadedMedia(DownloadedMedia.DownloadError.FORMAT, new String[contentType.length()]);
            }

            long contentLength = mediaResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength == -1L || contentLength > (long) CONFIG.maxMediaSize() * 1024 * 1024) {
                return new DownloadedMedia(DownloadedMedia.DownloadError.SIZE, new String[]{((contentLength/1024)/1024) + "mb"});
            }

            try (InputStream inputStream = mediaResponse.body();
                 ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {

                Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
                if (!readers.hasNext()) {
                    return new DownloadedMedia(DownloadedMedia.DownloadError.FORMAT);
                }
                ImageReader reader = readers.next();
                String format = reader.getFormatName().toLowerCase();

                if (format.equals("gif")) {
                    List<BufferedImage> frames = new ArrayList<>();
                    List<Long> delays = new ArrayList<>();

                    AnimatedGif gif = AnimatedGifReader.read(ImageSource.of(imageInputStreamToInputStream(imageInputStream)));

                    for (int i = 0; i < gif.getFrameCount(); i++) {
                        ImmutableImage currentFrame = gif.getFrame(i);
                        frames.add(i, currentFrame.toNewBufferedImage(currentFrame.getType()));
                        delays.add(gif.getDelay(i).toMillis());
                    }
                    return new DownloadedGif(frames, delays, gif);
                }

                reader.setInput(imageInputStream);
                BufferedImage image = reader.read(0);

                return new DownloadedMedia(image, reader.getFormatName().toLowerCase());

            }
        } catch (HttpTimeoutException | ConnectException e) {
            LOGGER.error("Error while downloading media element from Filebin.\n" +
                    "Make sure you are connected to the internet");
            return new DownloadedMedia(DownloadedMedia.DownloadError.INTERNET);
        } catch (UnknownHostException e) {
            LOGGER.error("Unknown host exception for {}", e.getMessage());
            return new DownloadedMedia(DownloadedMedia.DownloadError.INTERNET, new String[]{e.getMessage()});
        } catch (Exception e) {
            return new DownloadedMedia(DownloadedMedia.DownloadError.GENERIC, new String[]{e.getMessage()});
        } finally {
            client.shutdown();
        }
    }

    public boolean hasError() {
        return _hasError;
    }

    public String getErrorMessage() {
        return _errorMessage;
    }
}
