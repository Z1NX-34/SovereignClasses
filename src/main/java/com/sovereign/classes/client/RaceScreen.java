package com.sovereign.classes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sovereign.classes.Net;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Big image cards side by side; the picked card opens up and tells you about the race. */
public class RaceScreen extends Screen {
    private final List<Def> list = new ArrayList<>();
    private int sel = -1, descScroll;
    private SkinButton confirm;
    private int pad, cardsY, size, gap, x0, ch;

    public RaceScreen() { super(Component.literal("Choose Your Race")); }

    @Override
    public boolean shouldCloseOnEsc() { return !(Registry.settings.requireRace && ClientState.race.isEmpty()); }

    @Override
    protected void init() {
        list.clear();
        list.addAll(Registry.races.values());
        if (sel >= list.size()) sel = -1;
        Settings st = Registry.settings;
        pad = Skin.pad(width, height);
        int bw = Math.min(220, width - 2 * pad), bh = 20, btnY = height - pad - bh;
        confirm = addRenderableWidget(new SkinButton((width - bw) / 2, btnY, bw, bh, Component.literal(st.confirmRace), b -> confirm()));
        confirm.active = sel >= 0 && (ClientState.race.isEmpty() || st.allowChange);
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2))
            addRenderableWidget(new SkinButton(width - pad - 52, pad + 4, 48, 14, Component.literal("Editor"),
                    b -> minecraft.setScreen(new EditorScreen(this))));

        int n = Math.max(1, list.size());
        int topY = pad + 38 + 10, block = btnY - 10 - topY;
        gap = Math.max(12, Math.min(28, width / 50));
        int availW = width - 2 * pad - 16;
        size = Math.max(80, Math.min(300, (availW - (n - 1) * gap) / n));
        ch = Math.max(120, Math.min(420, block));
        int total = n * size + (n - 1) * gap;
        x0 = (width - total) / 2;
        cardsY = topY + Math.max(0, (block - ch) / 2);
    }

    private void confirm() {
        if (sel < 0 || sel >= list.size()) return;
        Net.CH.sendToServer(new Net.Choose(false, list.get(sel).id));
    }

    private int cardX(int i) { return x0 + i * (size + gap); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = Minecraft.getInstance().font;
        Settings st = Registry.settings;
        Skin.background(g, width, height);
        Skin.card(g, pad, pad, width - 2 * pad, 38);
        g.drawCenteredString(f, Component.literal(st.titleRace).withStyle(s -> s.withBold(true)), width / 2, pad + 8, st.cHeading);
        g.drawCenteredString(f, Component.literal(st.subtitleRace), width / 2, pad + 22, st.cText);
        if (list.isEmpty())
            g.drawCenteredString(f, Component.literal("No races yet. Add some with the editor (/sovclasses editor)."), width / 2, height / 2, st.cDim);
        for (int i = 0; i < list.size(); i++) {
            int x = cardX(i);
            boolean over = mx >= x && mx < x + size && my >= cardsY && my < cardsY + ch;
            card(g, f, st, list.get(i), x, cardsY, over, i == sel);
        }
        super.render(g, mx, my, pt);
    }

    private void card(GuiGraphics g, Font f, Settings st, Def d, int x, int y, boolean over, boolean sel) {
        int w = size, h = ch;
        if (sel) {
            g.fill(x - 4, y - 4, x + w + 4, y + h + 4, Skin.alpha(st.cAccent, 0x33));
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, Skin.alpha(st.cAccent, 0x66));
        }
        Skin.card(g, x, y, w, h);
        int iw = w - 16;
        int side = sel ? Math.max(48, Math.min(iw, h * 42 / 100)) : Math.max(48, Math.min(iw, h - 58));
        int ix = x + (w - side) / 2, iy = y + 8;
        g.fill(ix, iy, ix + side, iy + side, st.cBgTop);
        Skin.Img t = Skin.raceImage(d);
        if (t.loc != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(t.loc, ix, iy, side, side, 0, 0, t.w, t.h, t.w, t.h);
        }
        int fc = sel ? st.cAccent : over ? Skin.blend(st.cAccent, 0xFFFFFFFF, 0.25f) : st.cAccentDark;
        Skin.rect(g, ix, iy, ix + side, iy + side, fc);
        if (over && !sel) g.fill(ix + 1, iy + 1, ix + side - 1, iy + side - 1, 0x22FFFFFF);
        if (this.sel >= 0 && !sel && !over) g.fill(ix + 1, iy + 1, ix + side - 1, iy + side - 1, 0x66000000);

        int ny = iy + side + 8;
        g.drawCenteredString(f, Component.literal(d.displayName()).withStyle(s -> s.withBold(true)), x + w / 2, ny, sel ? st.cHeading : st.cText);
        if (!sel) {
            g.drawCenteredString(f, Component.literal("Click to learn more"), x + w / 2, ny + 13, st.cDim);
            return;
        }
        g.fill(x + 12, ny + 13, x + w - 12, ny + 14, Skin.alpha(st.cAccent, 0xAA));
        int top = ny + 20, bottom = y + h - 10;
        int vis = Math.max(1, (bottom - top) / 11);
        List<FormattedCharSequence> all = f.split(FormattedText.of(d.description), Math.max(40, w - 40));
        descScroll = Math.max(0, Math.min(descScroll, all.size() - vis));
        for (int i = 0; i < vis && i + descScroll < all.size(); i++) g.drawString(f, all.get(i + descScroll), x + 16, top + i * 11, st.cText, false);
        Skin.scrollBar(g, x + w - 14, top, vis * 11, vis, all.size(), descScroll);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < list.size(); i++) {
                int x = cardX(i);
                if (mx >= x && mx < x + size && my >= cardsY && my < cardsY + ch) {
                    if (sel != i) { sel = i; descScroll = 0; confirm.active = ClientState.race.isEmpty() || Registry.settings.allowChange; }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (sel >= 0) { descScroll = Math.max(0, descScroll - (int) Math.signum(delta)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && confirm != null && confirm.active) { confirm(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
