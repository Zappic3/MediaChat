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

    public static FileSharingService getDownloadServiceFor(URI uri) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String sanitizedUri = sanitizeUri(uri.getHost());
        //return services.get(sanitizedUrl).getDeclaredConstructor().newInstance();

        if (uri.getScheme().equalsIgnoreCase("server")) {
            return new ServerFileSharingService();
        } else {
            Class<? extends FileSharingService> service = services.get(sanitizedUri);
            if (service == null) {
                return new DefaultWebDownload();
            }
            return service.getDeclaredConstructor().newInstance();
        }
    }

    public static FileSharingUpload getUploadService() {
        if (CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.ALL || CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.FILES_ONLY) { // todo add config option for client to otp out of server file uploading
            return new ServerFileSharingService();
        }

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
            String host = sanitizeUri(url.getHost());

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
    }
}
