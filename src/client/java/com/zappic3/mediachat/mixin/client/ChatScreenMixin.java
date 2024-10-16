package com.zappic3.mediachat.mixin.client;

import com.zappic3.mediachat.filesharing.FileSharingService;
import com.zappic3.mediachat.filesharing.FilebinService;
import com.zappic3.mediachat.ui.ConfirmUploadScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    // this is necessary because of "extends Screen"
    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        Runnable runnable = () -> {
            StringBuilder newChatField = new StringBuilder();
            FileSharingService service = FileSharingService.getUploadService();

            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

            int i = 0;
            for (Path path : paths) {
                i++;
                final String uploadPlaceholder = "@upload"+i;
                newChatField.append(CONFIG.startMediaUrl()).append(uploadPlaceholder).append(CONFIG.endMediaUrl());

                chain = chain.thenCompose(ignored -> {
                    CompletableFuture<URL> future = service.upload(path);

                    return future.thenAccept(url -> {
                        MinecraftClient.getInstance().execute(() -> {
                            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                            if (currentScreen instanceof ChatScreen) {
                                TextFieldWidget currentChatField = ((ChatScreenAccessor) currentScreen).getChatField();
                                String oldChatFieldText = currentChatField.getText();
                                String newChatFieldText = oldChatFieldText.replaceAll(uploadPlaceholder, url.toString());
                                int oldCursorPos = currentChatField.getCursor();
                                currentChatField.setText(newChatFieldText);
                                LOGGER.info(currentChatField.getCursor()+" :: " + oldCursorPos);
                                currentChatField.setCursor(oldCursorPos + (newChatFieldText.length() - oldChatFieldText.length()), false);
                                LOGGER.info(currentChatField.getCursor()+"");
                            }
                        });
                    });
                });
            }

            String currentText = chatField.getText();
            MinecraftClient.getInstance().setScreen(new ChatScreen(currentText.substring(0, chatField.getCursor()) + newChatField + currentText.substring(chatField.getCursor())));
            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
            if (currentScreen instanceof ChatScreen) {
                ((ChatScreenAccessor) currentScreen).getChatField().setCursor(chatField.getCursor() + newChatField.length(), false);
            }
        };

        if (CONFIG.confirmUploadPopup()) {
            MinecraftClient.getInstance().setScreen(new ConfirmUploadScreen(chatField.getText(), runnable));
        } else {
            runnable.run();
        }
    }
}
