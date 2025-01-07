package com.zappic3.mediachat.ui;

import com.zappic3.mediachat.ConfigModel;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

public class ConfirmUploadScreen extends BaseUIModelScreen<FlowLayout> {
    FlowLayout _rootComponent;
    String _chatTextboxContent = "";
    Consumer<FileSharingService.FileHostingService> _upload;
    FileSharingService.FileHostingService _selectedService = FileSharingService.FileHostingService.NONE;

    public ConfirmUploadScreen(String chatTextboxContent, Consumer<FileSharingService.FileHostingService> consumer) {
        super(FlowLayout.class, DataSource.asset(Identifier.of(MOD_ID, "confirm_upload_screen")));
        _chatTextboxContent = chatTextboxContent;
        _upload = consumer;
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        _rootComponent = rootComponent;
        rootComponent.childById(ButtonComponent.class, "yes_button").onPress(button -> {
            CheckboxComponent checkbox = rootComponent.childById(CheckboxComponent.class, "remember_checkbox");
            if (checkbox.isChecked()) {
                CONFIG.defaultHostingService(this._selectedService);
            }
            _upload.accept(_selectedService);
        }).active(false);
        rootComponent.childById(ButtonComponent.class, "no_button").onPress(button -> close());

        rootComponent.childById(CheckboxComponent.class, "remember_checkbox").tooltip(Text.translatable("text.mediachat.confirmUploadScreen.checkbox.tooltip", currentHostingService()));

        // add hosting services buttons
        FlowLayout scrollContainer = rootComponent.childById(FlowLayout.class, "scrolling_selection");
        for (FileSharingService.FileHostingService service :FileSharingService.FileHostingService.values()) {
            if (service.equals(FileSharingService.FileHostingService.NONE)) {continue;}
            if (service.equals(FileSharingService.FileHostingService.MINECRAFT_SERVER)
                    && (CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.LINKS_ONLY
                    || CONFIG.serverNetworkingMode() == ConfigModel.serverMediaNetworkingMode.NONE)) {continue;}

            ButtonComponent button = Components.button(Text.translatable("text.config.mediachat.enum.fileHostingService."+service.name().toLowerCase()), component -> updateSelectedService(service));
            button.horizontalSizing(Sizing.fill());
            button.id(service.name());
            scrollContainer.child(button);
        }

        LabelComponent label = _rootComponent.childById(LabelComponent.class, "currently_selected_text");
        label.text(Text.translatable("text.mediachat.confirmUploadScreen.currentlySelected", " "));

        // add reason this screen is shown
        if (CONFIG.defaultHostingService() != FileSharingService.FileHostingService.NONE) {
            LabelComponent reasonLabel = _rootComponent.childById(LabelComponent.class, "popup_reason");
            if (reasonLabel != null) {
                if (CONFIG.defaultHostingService() == FileSharingService.FileHostingService.MINECRAFT_SERVER) {
                    reasonLabel.text(Text.translatable("text.mediachat.confirmUploadScreen.popupReason.server"));
                } else {
                    reasonLabel.text(Text.translatable("text.mediachat.confirmUploadScreen.popupReason.generic", currentHostingService()));
                }
            }
        }
    }

    public void updateSelectedService(FileSharingService.FileHostingService service) {
        LabelComponent label = _rootComponent.childById(LabelComponent.class, "currently_selected_text");
        label.text(Text.translatable("text.mediachat.confirmUploadScreen.currentlySelected", Text.translatable("text.config.mediachat.enum.fileHostingService."+service.name().toLowerCase())));

        FlowLayout scrollContainer = _rootComponent.childById(FlowLayout.class, "scrolling_selection");
        ButtonComponent oldButton = scrollContainer.childById(ButtonComponent.class, _selectedService.name());

        if (oldButton != null) {
            oldButton.active(true);
        }

        ButtonComponent newButton  = scrollContainer.childById(ButtonComponent.class, service.name());
        if (newButton != null) {
            newButton.active(false);
        }
        _selectedService = service;

        if (_selectedService != FileSharingService.FileHostingService.NONE) {
            _rootComponent.childById(ButtonComponent.class, "yes_button").active(true);
        }
    }


    private Text currentHostingService() {
        return Text.translatable("text.config.mediachat.enum.fileHostingService."+ CONFIG.defaultHostingService().name().toLowerCase());
    }

    @Override
    public void close() {
        super.close();
        MinecraftClient.getInstance().setScreen(new ChatScreen(_chatTextboxContent));
    }
}
