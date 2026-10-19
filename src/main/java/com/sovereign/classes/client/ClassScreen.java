package com.sovereign.classes.client;

import com.sovereign.classes.Net;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import com.sovereign.classes.data.Text;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One class at a time: kit + abilities left, model and description in the middle, attributes right. */
public class ClassScreen extends Screen {
    private final List<Def> list = new ArrayList<>();
    private int sel;
    private int attrScroll, descScroll, passScroll, kitScroll;
    private SkinButton confirm;
    private Def cached;
    private List<ItemStack> kitStacks = new ArrayList<>();
    private List<String> kitNames = new ArrayList<>();
    private List<String[]> attrRows = new ArrayList<>();   // label, value, sign

    // layout
    private int pad, top, bottom, lx, lw, cx, cw, rx, rw, hh, navY, modelBottom, descTop, descBottom, half;

    public ClassScreen() { super(Component.literal("Choose Your Path")); }

    @Override
    public boolean shouldCloseOnEsc() { return !mustChoose(); }

    private boolean mustChoose() { return Registry.settings.requireClass && ClientState.cls.isEmpty(); }

    @Override
    protected void init() {
        list.clear();
        Def race = Registry.races.get(ClientState.race);
        for (Def d : Registry.classes.values())
            if (race == null || race.allowedClasses.isEmpty() || race.allowedClasses.contains(d.id)) list.add(d);
        sel = Math.max(0, Math.min(sel, list.size() - 1));
        cached = null;
        Settings st = Registry.settings;
        pad = Skin.pad(width, height);
        int bw = Math.min(260, width - 2 * pad), bh = 20;
        confirm = addRenderableWidget(new SkinButton((width - bw) / 2, height - pad - bh, bw, bh,
                Component.literal(st.confirmClass), b -> confirm()));
        confirm.active = !list.isEmpty() && (ClientState.cls.isEmpty() || st.allowChange);
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2))
            addRenderableWidget(new SkinButton(width - pad - 52, pad + 4, 48, 14, Component.literal("Editor"),
                    b -> minecraft.setScreen(new EditorScreen(this))));

        top = pad + 38 + 6;
        bottom = height - pad - bh - 6;
        hh = bottom - top;
        int gap = 8, inner = width - 2 * pad;
        lw = (inner - 2 * gap) * 27 / 100;
        cw = (inner - 2 * gap) * 32 / 100;
        rw = inner - 2 * gap - lw - cw;
        lx = pad; cx = lx + lw + gap; rx = cx + cw + gap;
        half = hh * 52 / 100;
        descBottom = bottom - 8;
        descTop = descBottom - Math.max(46, hh * 28 / 100);
        navY = descTop - 32;
        modelBottom = navY - 6;
    }

    private void confirm() {
        if (list.isEmpty()) return;
        Net.CH.sendToServer(new Net.Choose(true, list.get(sel).id));
    }

    private void go(int delta) {
        if (list.isEmpty()) return;
        sel = (sel + delta + list.size()) % list.size();
        descScroll = attrScroll = passScroll = kitScroll = 0;
    }

    // ---------- cache ----------
    private static String attrLabel(String id) {
        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id));
        return a == null ? id : Component.translatable(a.getDescriptionId()).getString();
    }

    private void cache(Def d) {
        if (d == cached) return;
        cached = d;
        attrRows = new ArrayList<>();
        for (Def.Attr a : d.attributes) {
            double amt = "addition".equals(a.operation) ? a.amount : a.amount * 100.0;
            String num = Text.fmt(Math.round(amt * 100.0) / 100.0);
            String sign = amt < 0 ? "-" : "+";
            String v = (amt < 0 ? "" : "+") + num + ("addition".equals(a.operation) ? "" : "%");
            attrRows.add(new String[] {attrLabel(a.attribute), v, sign});
        }
        kitStacks = new ArrayList<>();
        kitNames = new ArrayList<>();
        for (Def.Kit k : d.kit) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(k.item));
            ItemStack st = it == null ? ItemStack.EMPTY : new ItemStack(it, k.count);
            if (!st.isEmpty() && !k.nbt.isBlank()) {
                try { st.setTag(TagParser.parseTag(k.nbt)); } catch (Exception ignored) {}
            }
            kitStacks.add(st);
            kitNames.add(k.count + "x " + (st.isEmpty() ? k.item : st.getHoverName().getString()));
        }
    }

    // ---------- drawing ----------
    private void head(GuiGraphics g, Font f, Settings st, int x, int y, int w, int h, String title) {
        Skin.panel(g, x, y, w, h);
        g.drawString(f, title, x + 12, y + 8, st.cHeading, false);
        g.fill(x + 10, y + 19, x + w - 10, y + 20, Skin.alpha(st.cAccent, 0x88));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = Minecraft.getInstance().font;
        Settings st = Registry.settings;
        Skin.background(g, width, height);
        Skin.card(g, pad, pad, width - 2 * pad, 38);
        g.drawCenteredString(f, Component.literal(st.titleClass).withStyle(s -> s.withBold(true)), width / 2, pad + 9, st.cHeading);
        g.drawCenteredString(f, Component.literal(st.subtitleClass), width / 2, pad + 23, st.cText);

        if (list.isEmpty()) {
            g.drawCenteredString(f, Component.literal("No classes yet. Add some with the editor (/sovclasses editor)."), width / 2, height / 2, st.cDim);
        } else {
            Def d = list.get(sel);
            cache(d);
            left(g, f, st, d);
            centre(g, f, st, d, mx, my);
            right(g, f, st);
        }
        super.render(g, mx, my, pt);
    }

    private void left(GuiGraphics g, Font f, Settings st, Def d) {
        head(g, f, st, lx, top, lw, half, "Equipment");
        int y = top + 26, maxY = top + half - 8;
        int rows = Math.max(1, (maxY - y) / 18);
        kitScroll = Math.max(0, Math.min(kitScroll, kitNames.size() - rows));
        if (kitNames.isEmpty()) g.drawString(f, "-", lx + 14, y, st.cDim, false);
        for (int i = 0; i < rows && i + kitScroll < kitNames.size(); i++) {
            int yy = y + i * 18;
            g.fill(lx + 12, yy, lx + 30, yy + 18, 0x66000000);
            g.renderItem(kitStacks.get(i + kitScroll), lx + 13, yy + 1);
            g.drawString(f, f.plainSubstrByWidth(kitNames.get(i + kitScroll), lw - 54), lx + 34, yy + 5, st.cText, false);
        }
        Skin.scrollBar(g, lx + lw - 13, y - 1, rows * 18, rows, kitNames.size(), kitScroll);

        int py = top + half + 6, ph = bottom - py;
        head(g, f, st, lx, py, lw, ph, "Special abilities");
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (Def.Ability a : d.abilities) {
            if (!lines.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); cols.add(0); }
            for (FormattedCharSequence s : f.split(Component.literal(a.name).withStyle(x -> x.withBold(true)), lw - 34)) { lines.add(s); cols.add(st.cHeading); }
            for (FormattedCharSequence s : f.split(FormattedText.of(a.description), lw - 34)) { lines.add(s); cols.add(st.cText); }
        }
        if (!d.effects.isEmpty()) {
            if (!lines.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); cols.add(0); }
            for (FormattedCharSequence s : f.split(Component.literal("Effects").withStyle(x -> x.withBold(true)), lw - 34)) { lines.add(s); cols.add(st.cHeading); }
            for (Def.Effect e : d.effects) {
                String t = Skin.effectName(e.effect) + " " + Skin.roman(e.amplifier + 1) + (e.seconds > 0 ? " (" + e.seconds + "s)" : "");
                for (FormattedCharSequence s : f.split(FormattedText.of(t), lw - 34)) { lines.add(s); cols.add(st.cText); }
            }
        }
        if (lines.isEmpty()) { g.drawString(f, "-", lx + 14, py + 26, st.cDim, false); }
        int vis = Math.max(1, (ph - 32) / 10);
        passScroll = Math.max(0, Math.min(passScroll, lines.size() - vis));
        for (int i = 0; i < vis && i + passScroll < lines.size(); i++)
            g.drawString(f, lines.get(i + passScroll), lx + 14, py + 26 + i * 10, cols.get(i + passScroll), false);
        Skin.scrollBar(g, lx + lw - 13, py + 24, vis * 10, vis, lines.size(), passScroll);
    }

    private void centre(GuiGraphics g, Font f, Settings st, Def d, int mx, int my) {
        Skin.panel(g, cx, top, cw, hh);
        int pcx = cx + cw / 2;
        g.drawCenteredString(f, Component.literal(d.displayName()).withStyle(s -> s.withBold(true)), pcx, top + 9, st.cHeading);
        if (!d.icon.isBlank()) Skin.drawIcon(g, d.icon, Math.max(cx + 8, pcx - f.width(d.displayName()) / 2 - 24), top + 5);
        String tags = String.join("  ·  ", d.tags);
        if (!tags.isBlank()) g.drawCenteredString(f, Component.literal(f.plainSubstrByWidth(tags, cw - 20)), pcx, top + 20, st.cDim);
        g.fill(cx + 10, top + 29, cx + cw - 10, top + 30, Skin.alpha(st.cAccent, 0x88));

        int baseY = modelBottom - 6;
        int pw = Math.min(cw - 40, 90);
        g.fill(pcx - pw / 2, baseY, pcx + pw / 2, baseY + 3, st.cAccentDark);
        g.fill(pcx - pw / 2 + 4, baseY + 3, pcx + pw / 2 - 4, baseY + 5, st.cLine);
        g.fill(pcx - pw / 2, baseY, pcx + pw / 2, baseY + 1, st.cAccent);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int scale = Math.max(20, (baseY - (top + 30)) * 10 / 21);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, pcx, baseY, scale, (float) (pcx - mx), (float) (baseY - scale * 1.5f - my), player);
        }

        int ay = navY, n = list.size();
        arrow(g, f, st, cx + 14, ay, true, mx, my);
        arrow(g, f, st, cx + cw - 14 - 22, ay, false, mx, my);
        g.drawCenteredString(f, Component.literal((sel + 1) + " / " + n), pcx, ay + 3, st.cText);
        if (n <= 12) {
            int dx = pcx - n * 8 / 2 + 1;
            for (int i = 0; i < n; i++) g.fill(dx + i * 8, ay + 20, dx + i * 8 + 6, ay + 23, i == sel ? st.cAccent : st.cLine);
        }

        int dy = descTop;
        g.fill(cx + 10, dy - 3, cx + cw - 10, dy - 2, Skin.alpha(st.cAccent, 0x66));
        List<FormattedCharSequence> lines = f.split(FormattedText.of(d.description), cw - 36);
        int vis = Math.max(1, (descBottom - dy) / 10);
        descScroll = Math.max(0, Math.min(descScroll, lines.size() - vis));
        for (int i = 0; i < vis && i + descScroll < lines.size(); i++) g.drawString(f, lines.get(i + descScroll), cx + 14, dy + i * 10, st.cText, false);
        Skin.scrollBar(g, cx + cw - 13, dy, vis * 10, vis, lines.size(), descScroll);
    }

    private void arrow(GuiGraphics g, Font f, Settings st, int x, int y, boolean left, int mx, int my) {
        boolean over = mx >= x && mx < x + 22 && my >= y && my < y + 14;
        Skin.button(g, x, y, 22, 14, over, true);
        g.drawCenteredString(f, Component.literal(left ? "<" : ">"), x + 11, y + 3, over ? st.cHeading : st.cText);
    }

    private void right(GuiGraphics g, Font f, Settings st) {
        head(g, f, st, rx, top, rw, hh, "Attributes");
        int y0 = top + 28, rowH = 18;
        int vis = Math.max(1, (bottom - 8 - y0) / rowH);
        attrScroll = Math.max(0, Math.min(attrScroll, attrRows.size() - vis));
        if (attrRows.isEmpty()) g.drawString(f, "-", rx + 14, y0, st.cDim, false);
        int bx = rx + 12, bw = rw - 32;
        for (int i = 0; i < vis && i + attrScroll < attrRows.size(); i++) {
            String[] a = attrRows.get(i + attrScroll);
            int y = y0 + i * rowH;
            if (((i + attrScroll) & 1) == 0) g.fill(bx - 2, y - 3, bx + bw + 2, y + rowH - 3, 0x33000000);
            int col = a[2].equals("-") ? st.cNeg : st.cPos;
            g.drawString(f, f.plainSubstrByWidth(a[0], bw - f.width(a[1]) - 10), bx + 2, y + 1, st.cText, false);
            g.drawString(f, a[1], bx + bw - f.width(a[1]) - 2, y + 1, col, false);
        }
        Skin.scrollBar(g, rx + rw - 13, y0 - 3, vis * rowH, vis, attrRows.size(), attrScroll);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !list.isEmpty()) {
            int ay = navY, n = list.size();
            if (my >= ay && my < ay + 14) {
                if (mx >= cx + 14 && mx < cx + 36) { go(-1); return true; }
                if (mx >= cx + cw - 36 && mx < cx + cw - 14) { go(1); return true; }
            }
            if (n <= 12 && my >= ay + 17 && my < ay + 26) {
                int pcx = cx + cw / 2, dx = pcx - n * 8 / 2 + 1;
                int i = (int) Math.floor((mx - dx) / 8.0);
                if (i >= 0 && i < n && mx - dx - i * 8 < 7) { sel = i; descScroll = attrScroll = passScroll = kitScroll = 0; return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = -(int) Math.signum(delta);
        if (mx >= rx && mx < rx + rw && my >= top && my < bottom) { attrScroll = Math.max(0, attrScroll + d); return true; }
        if (mx >= cx && mx < cx + cw && my >= descTop - 4 && my < bottom) { descScroll = Math.max(0, descScroll + d); return true; }
        if (mx >= lx && mx < lx + lw && my >= top && my < bottom) {
            if (my < top + half) kitScroll = Math.max(0, kitScroll + d); else passScroll = Math.max(0, passScroll + d);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263 || key == 65) { go(-1); return true; }          // left, A
        if (key == 262 || key == 68) { go(1); return true; }           // right, D
        if ((key == 257 || key == 335) && confirm != null && confirm.active) { confirm(); return true; }   // enter
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
