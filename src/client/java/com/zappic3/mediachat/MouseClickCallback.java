package com.zappic3.mediachat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.ActionResult;

public interface MouseClickCallback {
    Event<MouseClickCallback> EVENT = EventFactory.createArrayBacked(MouseClickCallback.class,
            listeners -> (window, button, action, mods) -> {
                for (MouseClickCallback listener : listeners) {
                    ActionResult result = listener.reactToMouseClick(window, button, action, mods);

                    if (result != ActionResult.PASS) {
                        return result;
                    }
                }
                return ActionResult.PASS;
            });
    ActionResult reactToMouseClick(long window, int button, int action, int mods);
}
