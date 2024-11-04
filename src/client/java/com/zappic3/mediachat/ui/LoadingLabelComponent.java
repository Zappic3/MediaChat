package com.zappic3.mediachat.ui;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.text.Text;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class LoadingLabelComponent extends LabelComponent {
    private ExecuteAndCheck _action;
    private boolean executedAction;

    protected LoadingLabelComponent(Text text, ExecuteAndCheck action) {
        super(text);
        _action = action;
        executedAction = false;
    }

    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(context, mouseX, mouseY, partialTicks, delta);
        if (!executedAction) {
            executedAction = _action.execute();
        }

    }

    public LoadingLabelComponent onLoad(ExecuteAndCheck action) {
        _action = action;
        executedAction = false;
        return this;
    }

    public LoadingLabelComponent disableAction() {
        executedAction = true;
        return this;
    }

    public LoadingLabelComponent enableAction() {
        executedAction = true;
        return this;
    }

    /**
     * Everytime the  {@link LoadingLabelComponent#draw} is called, this gets executed
     * (if executedAction is false) until {@link ExecuteAndCheck#execute()} returns true
     */
    @FunctionalInterface
    public interface ExecuteAndCheck {
        boolean execute();
    }

}
