package com.zappic3.mediachat.mixin.client;

import com.zappic3.mediachat.ConfigModel;
import com.zappic3.mediachat.FileUploadHandler;
import com.zappic3.mediachat.IMediaChatPaste;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import com.zappic3.mediachat.ui.ConfirmUploadScreen;
import com.zappic3.mediachat.ui.GifBrowserUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
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
import java.util.function.Consumer;

import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.Utility.displayErrorMessage;
import static net.minecraft.client.util.InputUtil.GLFW_KEY_ESCAPE;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow private ChatInputSuggestor chatInputSuggestor;

    // this is necessary because of "extends Screen"
    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        FileUploadHandler.uploadFiles(paths, chatField.getText(), chatField.getCursor());
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
