package com.zappic3.mediachat.mixin.client;

import com.zappic3.mediachat.IMediaChatPaste;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import com.zappic3.mediachat.ui.ConfirmUploadScreen;
import com.zappic3.mediachat.ui.GifBrowserUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.zappic3.mediachat.MediaChat.*;
import static net.minecraft.client.util.InputUtil.GLFW_KEY_ESCAPE;

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
            FileSharingService.FileSharingUpload service = FileSharingService.getUploadService();

            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

            int i = 0;
            for (Path path : paths) {
                i++;
                final String uploadPlaceholder = "@upload"+i;
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
                    }).exceptionally(ex -> {
                        Throwable actualException = ex;
                        if (actualException instanceof FileSharingService.DetailedCancellationException) { //todo check why the instanceof doesnt work + why the error type get also shown when getMessage())is used
                            displayErrorMessage(actualException.getMessage());
                        } else {
                            displayErrorMessage("An error occurred during file upload: " + actualException.getMessage());
                        }
                        return null;
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

    @Unique
    private void displayErrorMessage(String message) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.empty().formatted(Formatting.RED).append(message), false);
        }
    }

    // only close the GIF browser on esc, instead of the entire screen
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW_KEY_ESCAPE) {
            GifBrowserUI gif = GifBrowserUI.getInstance();
            if (gif != null && gif.isGifBrowserOpen()) {
                gif.closeGifBrowser();
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    public void onInit(CallbackInfo ci) {
        if (chatField instanceof IMediaChatPaste) {
            ((IMediaChatPaste) chatField).mediaChat$enableMediaChatPaste(true);
        }
    }

}
