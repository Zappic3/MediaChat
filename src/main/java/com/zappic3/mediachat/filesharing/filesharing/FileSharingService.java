package com.zappic3.mediachat.filesharing.filesharing;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static com.zappic3.mediachat.MediaChat.CONFIG;

public abstract class FileSharingService {
    protected static HashMap<String, Class<? extends FileSharingService>> services = new HashMap<>();
    static {
        services.put("filebin.net", FilebinService.class);
    }

    public static FileSharingService getDownloadServiceFor(URL url) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String sanitizedUrl = sanitizeUrl(url.getHost());
        //return services.get(sanitizedUrl).getDeclaredConstructor().newInstance();
        Class<? extends FileSharingService> service = services.get(sanitizedUrl);
        if (service == null) {
            return new DefaultWebDownload();
        }
        return service.getDeclaredConstructor().newInstance();
    }

    public static FileSharingUpload getUploadService() {
        switch (CONFIG.hostingService()) {
            default -> {
                return new FilebinService();
            }
        }
    }

     public DownloadedMedia downloadWithChecks(URL url) {
        if (isUrlWhitelisted(url) || FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) { // bypass whitelist check if the server is downloading data
            return download(url);
        }
        return new DownloadedMedia(DownloadedMedia.DownloadError.WHITELIST);
    }

    abstract protected DownloadedMedia download(URL url);

    private boolean isUrlWhitelisted(URL url) {
        if (CONFIG.useWhitelist()) {
            String host = sanitizeUrl(url.getHost());

            for (String whitelistedDomain : CONFIG.whitelistedWebsites()) {
                whitelistedDomain = sanitizeUrl(whitelistedDomain);
                if (host.equals(whitelistedDomain) || host.endsWith("." + whitelistedDomain)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static String sanitizeUrl(String url) {
        String host =  url.toLowerCase().replaceFirst("\\.$", "");
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
        CompletableFuture<URL> upload(Path filePath);
    }
}
