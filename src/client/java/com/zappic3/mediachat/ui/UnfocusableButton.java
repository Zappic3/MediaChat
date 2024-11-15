package com.zappic3.mediachat.ui;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class UnfocusableButton extends ButtonComponent {
    protected UnfocusableButton(Text message, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
    }

    @SuppressWarnings("RedundantMethodOverride")
    @Override
    public boolean canFocus(FocusSource source) {
        return false;
    }

}
