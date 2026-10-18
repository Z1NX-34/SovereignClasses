package com.sovereign.classes.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sovereign.classes.SovereignClasses;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * All drawing helpers. Textures live in assets/sovereignclasses/textures/gui/ and can be replaced by any resource
 * pack (or kubejs/assets). Hue, saturation and brightness from settings.json recolour them on the fly.
 */
public final class Skin {
    private Skin() {}

    public static Settings s() { return Registry.settings; }

    // ---------- textures ----------
    private static final Map<String, ResourceLocation> DYN = new HashMap<>();
    private static String dynKey = "";

    public static ResourceLocation tex(String name) {
        ResourceLocation base = new ResourceLocation(SovereignClasses.MODID, "textures/gui/" + name + ".png");
        Settings st = s();
        if (st.hue == 0 && st.saturation == 1 && st.brightness == 1) return base;
        String key = st.textureKey();
        if (!key.equals(dynKey)) {
            for (ResourceLocation r : DYN.values()) Minecraft.getInstance().getTextureManager().release(r);
            DYN.clear();
            dynKey = key;
        }
        ResourceLocation have = DYN.get(name);
        if (have != null) return have;
        ResourceLocation made = recolour(base, name, st, key);
        DYN.put(name, made);
        return made;
    }

    private static ResourceLocation recolour(ResourceLocation base, String name, Settings st, String key) {
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(base);
            if (r.isEmpty()) return base;
            try (InputStream in = r.get().open()) {
                NativeImage img = NativeImage.read(in);
                for (int y = 0; y < img.getHeight(); y++)
                    for (int x = 0; x < img.getWidth(); x++)
                        img.setPixelRGBA(x, y, shift(img.getPixelRGBA(x, y), st));
                ResourceLocation rl = new ResourceLocation(SovereignClasses.MODID, "dyn/" + name + "_" + Math.abs(key.hashCode()));
                Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                return rl;
            }
        } catch (Exception e) {
            return base;
        }
    }

    /** Pixels are ABGR. */
    private static int shift(int p, Settings st) {
        int a = p >>> 24;
        if (a == 0) return p;
        float r = (p & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = ((p >> 16) & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        float sat = max <= 0 ? 0 : d / max, v = max;
        h = (float) ((h + st.hue) % 360.0);
        if (h < 0) h += 360f;
        sat = (float) Math.max(0, Math.min(1, sat * st.saturation));
        v = (float) Math.max(0, Math.min(1, v * st.brightness));
        float c = v * sat, hh = h / 60f, x = c * (1 - Math.abs(hh % 2f - 1)), m = v - c;
        float rr, gg, bb;
        if (hh < 1) { rr = c; gg = x; bb = 0; }
        else if (hh < 2) { rr = x; gg = c; bb = 0; }
        else if (hh < 3) { rr = 0; gg = c; bb = x; }
        else if (hh < 4) { rr = 0; gg = x; bb = c; }
        else if (hh < 5) { rr = x; gg = 0; bb = c; }
        else { rr = c; gg = 0; bb = x; }
        int R = Math.round((rr + m) * 255), G = Math.round((gg + m) * 255), B = Math.round((bb + m) * 255);
        return (a << 24) | (B << 16) | (G << 8) | R;
    }

    // ---------- race images ----------
    public static final class Img {
        public ResourceLocation loc;
        public int w = 256, h = 256;
        public boolean custom;
    }
    private static final Map<String, Img> IMGS = new HashMap<>();
    private static int imgVersion = -1;

    public static Img raceImage(Def d) {
        if (imgVersion != Registry.version) { IMGS.clear(); imgVersion = Registry.version; }
        Img have = IMGS.get(d.id);
        if (have != null) return have;
        Img t = new Img();
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            ResourceLocation loc = d.image.isBlank() ? null : ResourceLocation.tryParse(d.image.trim());
            if (loc == null) loc = new ResourceLocation(SovereignClasses.MODID, "textures/gui/race/" + d.id + ".png");
            Optional<Resource> r = rm.getResource(loc);
            if (r.isPresent()) t.custom = true;
            else {
                loc = new ResourceLocation(SovereignClasses.MODID, "textures/gui/race/_default.png");
                r = rm.getResource(loc);
            }
            if (r.isPresent()) {
                try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                    t.w = img.getWidth();
                    t.h = img.getHeight();
                }
                t.loc = loc;
            }
        } catch (Exception ignored) {
            t.loc = null;
        }
        IMGS.put(d.id, t);
        return t;
    }

    // ---------- drawing ----------
    public static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int alpha(int argb, int a) { return (a << 24) | (argb & 0xFFFFFF); }

    public static void nine(GuiGraphics g, String texName, int x, int y, int w, int h, int b, int ts) {
        if (w <= 0 || h <= 0) return;
        ResourceLocation tex = tex(texName);
        b = Math.min(b, Math.min(w, h) / 2);
        if (b <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int mw = w - 2 * b, mh = h - 2 * b, tm = ts - 2 * b;
        g.blit(tex, x, y, b, b, 0, 0, b, b, ts, ts);
        g.blit(tex, x + w - b, y, b, b, ts - b, 0, b, b, ts, ts);
        g.blit(tex, x, y + h - b, b, b, 0, ts - b, b, b, ts, ts);
        g.blit(tex, x + w - b, y + h - b, b, b, ts - b, ts - b, b, b, ts, ts);
        if (mw > 0) {
            g.blit(tex, x + b, y, mw, b, b, 0, tm, b, ts, ts);
            g.blit(tex, x + b, y + h - b, mw, b, b, ts - b, tm, b, ts, ts);
        }
        if (mh > 0) {
            g.blit(tex, x, y + b, b, mh, 0, b, b, tm, ts, ts);
            g.blit(tex, x + w - b, y + b, b, mh, ts - b, b, b, tm, ts, ts);
        }
        if (mw > 0 && mh > 0) g.blit(tex, x + b, y + b, mw, mh, b, b, tm, tm, ts, ts);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) { nine(g, "panel", x, y, w, h, 8, 32); }
    public static void card(GuiGraphics g, int x, int y, int w, int h) { nine(g, "card", x, y, w, h, 6, 32); }

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        nine(g, !active ? "button_disabled" : hovered ? "button_hover" : "button_normal", x, y, w, h, 6, 24);
    }

    public static void background(GuiGraphics g, int w, int h) {
        Settings st = s();
        g.fillGradient(0, 0, w, h, st.cBgTop, st.cBgBottom);
        g.fillGradient(0, 0, w, h / 5, 0xAA000000, 0x00000000);
        g.fillGradient(0, h - h / 5, w, h, 0x00000000, 0xAA000000);
        int m = 6;
        rect(g, m, m, w - m, h - m, st.cAccent);
        rect(g, m + 2, m + 2, w - m - 2, h - m - 2, st.cLine);
        g.fill(m + 1, m + 1, w - m - 1, m + 3, st.cAccent);
        g.fill(m + 3, m + 3, w - m - 3, m + 4, st.cAccentDark);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int c = 34;
        g.blit(tex("corner_tl"), m - 1, m - 1, c, c, 0, 0, 40, 40, 40, 40);
        g.blit(tex("corner_tr"), w - m + 1 - c, m - 1, c, c, 0, 0, 40, 40, 40, 40);
        g.blit(tex("corner_bl"), m - 1, h - m + 1 - c, c, c, 0, 0, 40, 40, 40, 40);
        g.blit(tex("corner_br"), w - m + 1 - c, h - m + 1 - c, c, c, 0, 0, 40, 40, 40, 40);
    }

    public static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    /** Thin scroll bar; always drawn so players can see whether there is more. */
    public static void scrollBar(GuiGraphics g, int x, int y, int h, int vis, int total, int pos) {
        Settings st = s();
        g.fill(x - 1, y - 1, x + 4, y + h + 1, 0xAA0A0614);
        g.fill(x, y, x + 3, y + h, blend(st.cLine, 0xFF000000, 0.3f));
        boolean more = total > vis;
        int th = more ? Math.max(12, h * vis / total) : h;
        int ty = more ? y + (h - th) * pos / Math.max(1, total - vis) : y;
        g.fill(x, ty, x + 3, ty + th, more ? st.cAccent : blend(st.cAccentDark, 0xFF000000, 0.2f));
        if (more) g.fill(x, ty, x + 3, ty + 1, blend(st.cAccent, 0xFFFFFFFF, 0.5f));
    }

    public static int pad(int w, int h) { return Math.max(12, Math.min(26, Math.min(w, h) / 16)); }
}
