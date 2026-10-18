package com.sovereign.classes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Button drawn with the themed textures. */
public class SkinButton extends Button {
    private boolean highlighted;

    public SkinButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    public SkinButton highlight(boolean on) { this.highlighted = on; return this; }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean over = isHoveredOrFocused() || highlighted;
        Skin.button(g, getX(), getY(), getWidth(), getHeight(), over, active);
        var f = Minecraft.getInstance().font;
        int col = !active ? Skin.s().cDim : over ? Skin.s().cHeading : Skin.s().cText;
        g.drawCenteredString(f, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, col);
    }
}
