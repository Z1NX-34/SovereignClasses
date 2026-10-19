package com.sovereign.classes.client;

import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/** Full-width search list used for attributes, items, effects, icons and textures. */
public class PickerScreen extends Screen {
    public static final class Entry {
        public final String id, label;
        public final ItemStack stack;
        public final ResourceLocation tex;
        public Entry(String id, String label, ItemStack stack, ResourceLocation tex) {
            this.id = id; this.label = label; this.stack = stack; this.tex = tex;
        }
    }

    public static final class Source {
        public final String name;
        public final Supplier<List<Entry>> loader;
        private List<Entry> cache;
        public Source(String name, Supplier<List<Entry>> loader) { this.name = name; this.loader = loader; }
        List<Entry> get() { if (cache == null) cache = loader.get(); return cache; }
    }

    // ---------- sources ----------
    private static <T> List<Entry> fromRegistry(IForgeRegistry<T> reg, java.util.function.Function<T, String> label,
                                                java.util.function.Function<T, ItemStack> stack) {
        List<Entry> out = new ArrayList<>();
        for (ResourceLocation k : reg.getKeys()) {
            T v = reg.getValue(k);
            if (v == null || k.toString().equals("minecraft:air")) continue;
            out.add(new Entry(k.toString(), label.apply(v), stack == null ? null : stack.apply(v), null));
        }
        out.sort(Comparator.comparing((Entry e) -> e.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    public static Source attributes() {
        return new Source("Attributes", () -> fromRegistry(ForgeRegistries.ATTRIBUTES,
                a -> Component.translatable(a.getDescriptionId()).getString(), null));
    }

    public static Source effects() {
        return new Source("Effects", () -> fromRegistry(ForgeRegistries.MOB_EFFECTS,
                e -> Component.translatable(e.getDescriptionId()).getString(), null));
    }

    public static Source items() {
        return new Source("Items", () -> fromRegistry(ForgeRegistries.ITEMS,
                i -> new ItemStack(i, 1).getHoverName().getString(), i -> new ItemStack(i, 1)));
    }

    public static Source textures() {
        return new Source("Textures", () -> {
            List<Entry> out = new ArrayList<>();
            Map<ResourceLocation, Resource> found = Minecraft.getInstance().getResourceManager()
                    .listResources("textures", rl -> rl.getPath().endsWith(".png"));
            for (ResourceLocation rl : found.keySet()) {
                String p = rl.getPath();
                out.add(new Entry(rl.toString(), p.substring("textures/".length(), p.length() - 4), null, rl));
            }
            out.sort(Comparator.comparing((Entry e) -> e.id));
            return out;
        });
    }

    // ---------- screen ----------
    private final Screen parent;
    private final String title;
    private final List<Source> sources;
    private final Consumer<String> onPick;
    private int mode, scroll;
    private EditBox search;
    private List<Entry> shown = new ArrayList<>();
    private static final int ROW = 20, MAX_SHOWN = 1500;
    private int pad, listTop, listBottom;

    public PickerScreen(Screen parent, String title, List<Source> sources, Consumer<String> onPick) {
        super(Component.literal(title));
        this.parent = parent; this.title = title; this.sources = sources; this.onPick = onPick;
    }

    @Override
    protected void init() {
        pad = Skin.pad(width, height) > 16 ? 16 : 12;
        String old = search == null ? "" : search.getValue();
        int y = pad + 18;
        for (int i = 0; i < sources.size() && sources.size() > 1; i++) {
            final int mi = i;
            addRenderableWidget(new SkinButton(pad + i * 84, y, 80, 16, Component.literal(sources.get(i).name), b -> {
                mode = mi; scroll = 0; refilter();
            }).highlight(mode == i));
        }
        if (sources.size() > 1) y += 20;
        search = new EditBox(Minecraft.getInstance().font, pad, y, width - 2 * pad, 18, Component.literal(""));
        search.setMaxLength(80);
        search.setValue(old);
        search.setResponder(s -> { scroll = 0; refilter(); });
        addRenderableWidget(search);
        setFocused(search);
        search.setFocused(true);
        addRenderableWidget(new SkinButton(width / 2 - 50, height - pad - 20, 100, 20, Component.literal("Cancel"), b -> onClose()));
        listTop = y + 26;
        listBottom = height - pad - 28;
        refilter();
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : sources.get(mode).get()) {
            if (q.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(q) || e.id.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
                if (out.size() >= MAX_SHOWN) break;
            }
        }
        shown = out;
    }

    private int rows() { return Math.max(1, (listBottom - listTop) / ROW); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = Minecraft.getInstance().font;
        Settings st = Registry.settings;
        Skin.background(g, width, height);
        g.drawCenteredString(f, Component.literal(title).withStyle(s -> s.withBold(true)), width / 2, pad + 2, st.cHeading);
        Skin.panel(g, pad - 4, listTop - 4, width - 2 * pad + 8, listBottom - listTop + 8);
        int rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - rows)));
        if (shown.isEmpty()) g.drawCenteredString(f, Component.literal("Nothing matches"), width / 2, listTop + 12, st.cDim);
        for (int i = 0; i < rows && i + scroll < shown.size(); i++) {
            Entry e = shown.get(i + scroll);
            int y = listTop + i * ROW, x0 = pad + 2, x1 = width - pad - 12;
            boolean over = mx >= x0 && mx < x1 && my >= y && my < y + ROW;
            if (over) g.fill(x0, y, x1, y + ROW - 1, Skin.alpha(st.cAccent, 0x40));
            else if (((i + scroll) & 1) == 0) g.fill(x0, y, x1, y + ROW - 1, 0x22000000);
            int tx = x0 + 4;
            if (e.stack != null) { g.renderItem(e.stack, x0 + 3, y + 2); tx = x0 + 24; }
            else if (e.tex != null) { Skin.drawTexture(g, e.tex, x0 + 3, y + 2, 16); tx = x0 + 24; }
            String idShown = f.plainSubstrByWidth(e.id, (x1 - x0) / 2 - 8);
            int idW = f.width(idShown);
            g.drawString(f, f.plainSubstrByWidth(e.label, x1 - tx - idW - 16), tx, y + 6, over ? st.cHeading : st.cText, false);
            g.drawString(f, idShown, x1 - idW - 4, y + 6, st.cDim, false);
        }
        Skin.scrollBar(g, width - pad - 6, listTop, rows * ROW, rows, shown.size(), scroll);
        String info = shown.size() >= MAX_SHOWN ? MAX_SHOWN + "+ results (type to narrow)" : shown.size() + " results";
        g.drawString(f, info, pad, height - pad - 14, st.cDim, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= pad + 2 && mx < width - pad - 12 && my >= listTop && my < listTop + rows() * ROW) {
            int i = (int) ((my - listTop) / ROW) + scroll;
            if (i >= 0 && i < shown.size()) {
                onPick.accept(shown.get(i).id);
                if (minecraft != null) minecraft.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * 3);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
