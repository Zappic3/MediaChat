package com.zappic3.mediachat;

import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import com.zappic3.mediachat.mixin.client.ChatScreenAccessor;
import com.zappic3.mediachat.ui.ConfirmUploadScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.Utility.displayErrorMessage;

public class FileUploadHandler {
    public static void uploadFiles(
        List<Path> paths,
        String currentText,
        int cursorPos
    ) {
        Consumer<FileSharingService.FileHostingService> consumer = (selectedService) -> {
            StringBuilder newChatField = new StringBuilder();
            FileSharingService.FileSharingUpload service = FileSharingService.getUploadService(selectedService);
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

            int i = 0;
            for (Path path : paths) {
                i++;
                final String uploadPlaceholder = "@upload" + i;
                newChatField.append(CONFIG.startMediaUrl()).append(uploadPlaceholder).append(CONFIG.endMediaUrl());

                chain = chain.thenCompose(ignored -> {
                    CompletableFuture<URI> future = service.upload(path);

                    return future.thenAccept(uri -> {
                        if (uri != null && !service.hasError()) {
                            MinecraftClient.getInstance().execute(() -> {
                                Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                                if (currentScreen instanceof ChatScreen) {
                                    TextFieldWidget currentChatField = ((ChatScreenAccessor) currentScreen).getChatField();
                                    String oldChatFieldText = currentChatField.getText();
                                    String newChatFieldText = oldChatFieldText.replaceAll(uploadPlaceholder, uri.toString());
                                    int oldCursorPos = currentChatField.getCursor();
                                    currentChatField.setText(newChatFieldText);
                                    currentChatField.setCursor(oldCursorPos + (newChatFieldText.length() - oldChatFieldText.length()), false);
                                }
                            });
                        } else {
                            if (service.getErrorMessage() != null) {
                                LOGGER.error(service.getErrorMessage());
                                displayErrorMessage(service.getErrorMessage());
                            } else {
                                LOGGER.error("An error occurred while uploading file");
                                displayErrorMessage("An error occurred while uploading file"); // todo localize
                            }
                        }
                    }).exceptionally(actualException -> {
                        if (actualException instanceof FileSharingService.DetailedCancellationException) { //todo check why the instanceof doesnt work + why the error type get also shown when getMessage())is used
                            displayErrorMessage(actualException.getMessage());
                        } else {
                            displayErrorMessage("An error occurred during file upload: " + actualException.getMessage());
                        }
                        return null;
                    });
                });
            }


            MinecraftClient.getInstance().setScreen(new ChatScreen(currentText.substring(0, cursorPos) + newChatField + currentText.substring(cursorPos)));
            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
            if (currentScreen instanceof ChatScreen) {
                TextFieldWidget chatField = ((ChatScreenAccessor) currentScreen).getChatField();
                ((ChatScreenAccessor) currentScreen).getChatField().setCursor(chatField.getCursor() + newChatField.length(), false);
            }
        };

        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (currentScreen instanceof ChatScreen) {
            TextFieldWidget chatField = ((ChatScreenAccessor) currentScreen).getChatField();

            boolean noServiceConfigured = (CONFIG.defaultHostingService() == FileSharingService.FileHostingService.NONE)
                    || ((CONFIG.defaultHostingService() == FileSharingService.FileHostingService.MINECRAFT_SERVER)
                    && (CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.NONE
                    || CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.LINKS_ONLY));

            if (noServiceConfigured) {
                MinecraftClient.getInstance().setScreen(new ConfirmUploadScreen(chatField.getText(), consumer));
            } else {
                if (CONFIG.useLocalMinecraftServerForHostingIfPossible()
                        && (CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.ALL
                        || CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.FILES_ONLY)) {
                    consumer.accept(FileSharingService.FileHostingService.MINECRAFT_SERVER);
                } else {
                    consumer.accept(CONFIG.defaultHostingService());
                }
            }
        }
    }
}
