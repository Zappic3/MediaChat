package com.zappic3.mediachat.ui;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

public class ConfirmUploadScreen extends BaseUIModelScreen<FlowLayout> {
    String _chatTextboxContent = "";
    Runnable _insertUrlsIntoChatbox;

    public ConfirmUploadScreen(String chatTextboxContent, Runnable runnable) {
        super(FlowLayout.class, DataSource.asset(Identifier.of(MOD_ID, "confirm_upload_screen")));
        _chatTextboxContent = chatTextboxContent;
        _insertUrlsIntoChatbox = runnable;
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.childById(ButtonComponent.class, "yes_button").onPress(button -> {
            _insertUrlsIntoChatbox.run();
        });
        rootComponent.childById(ButtonComponent.class, "no_button").onPress(button -> {
            close();
        });
    }


    @Override
    public void close() {
        super.close();
        MinecraftClient.getInstance().setScreen(new ChatScreen(_chatTextboxContent));
    }
}
