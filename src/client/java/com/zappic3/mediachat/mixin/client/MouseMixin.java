package com.zappic3.mediachat.mixin.client;// Import necessary classes
import com.zappic3.mediachat.MediaElement;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        // Check if the button is the left mouse button (button index 0) and if the action is press (action 1)
        if (button == 0 && action == 1) {
            MediaElement.reactToMouseClick();
        }
    }

}