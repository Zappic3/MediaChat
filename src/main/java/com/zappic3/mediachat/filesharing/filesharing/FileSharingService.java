package com.zappic3.mediachat.filesharing.filesharing;

import com.zappic3.mediachat.ConfigModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

public abstract class FileSharingService {
    protected static HashMap<String, Class<? extends FileSharingService>> services = new HashMap<>();

    static {
        services.put("filebin.net", FilebinService.class);
    }

    public static FileSharingService getDownloadServiceFor(URI uri) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String sanitizedUri = sanitizeUri(uri.getHost());
        //return services.get(sanitizedUrl).getDeclaredConstructor().newInstance();

        if (uri.getScheme().equalsIgnoreCase("server")) {
            return new ServerFileSharingService(); // this service should never be used for downloading.
                                                   // This is just here to make it easier to find potential bugs.
                                                   // Actual server downloading logic is inside the MediaElement class
        } else {
            Class<? extends FileSharingService> service = services.get(sanitizedUri);
            if (service == null) {
                return new DefaultWebDownload();
            }
            return service.getDeclaredConstructor().newInstance();
        }
    }

    public static FileSharingUpload getUploadService(FileHostingService service) {
        if (service == null || service == FileHostingService.NONE) {
            LOGGER.error("FileHostingService requested for invalid target: {}", service);
        }

        return switch (service) {
            case FILEBIN_NET -> new FilebinService();
            case MINECRAFT_SERVER -> new ServerFileSharingService();
            case null, default -> new FilebinService();
        };
    }

     public DownloadedMedia downloadWithChecks(URI uri) {
        if (isUrlWhitelisted(uri) || FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) { // bypass whitelist check if the server is downloading data
            return download(uri);
        }
        return new DownloadedMedia(DownloadedMedia.DownloadError.WHITELIST);
    }

    abstract protected DownloadedMedia download(URI uri);

    private boolean isUrlWhitelisted(URI uri) {
        if (CONFIG.useWhitelist()) {
            if (uri.getScheme().equalsIgnoreCase("server")) {return true;} // cant exclude the server

            String host = sanitizeUri(uri.getHost());

            for (String whitelistedDomain : CONFIG.whitelistedWebsites()) {
                whitelistedDomain = sanitizeUri(whitelistedDomain);
                if (host.equals(whitelistedDomain) || host.endsWith("." + whitelistedDomain)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }


    private static String sanitizeUri(String uri) {
        String host =  uri.toLowerCase().replaceFirst("\\.$", "");
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        return host;
    }

    protected static InputStream imageInputStreamToInputStream(ImageInputStream imageStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = imageStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    public interface FileSharingUpload {
        CompletableFuture<URI> upload(Path filePath);
        boolean hasError();
        String getErrorMessage();
    }

    public static class DetailedCancellationException extends CancellationException {
        public DetailedCancellationException(String message) {
            super(message);
        }

        @Override
        public String getMessage() {
            return super.getMessage();
        }
    }

    public enum FileHostingService {
        NONE,
        MINECRAFT_SERVER,
        FILEBIN_NET
    }
}
