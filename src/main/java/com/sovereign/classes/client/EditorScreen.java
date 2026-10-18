package com.sovereign.classes.client;

import com.google.gson.JsonParser;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * In-game editor for classes, races and the theme (ops only; the server checks again when saving).
 * Everything is saved to config/sovereignclasses/ and synced to all players at once.
 */
public class EditorScreen extends Screen {
    private enum Tab { CLASSES, RACES, THEME }

    private static final String[] SEC = {"General", "Attributes", "Kit", "Abilities", "Commands"};
    private static final String[] SEC_THEME = {"Colours", "Texts & options"};
    private static final String[][] COLOURS = {
            {"accent", "Accent"}, {"accentDark", "Accent dark"}, {"heading", "Headings"}, {"text", "Text"}, {"dim", "Dim text"},
            {"line", "Lines"}, {"positive", "Positive"}, {"negative", "Negative"}, {"bgTop", "Background top"},
            {"bgBottom", "Background bottom"}, {"hue", "Hue shift (deg)"}, {"saturation", "Saturation x"}, {"brightness", "Brightness x"}};
    private static final String[][] TEXTS = {
            {"titleClass", "Class screen title"}, {"subtitleClass", "Class screen subtitle"}, {"confirmClass", "Class confirm button"},
            {"titleRace", "Race screen title"}, {"subtitleRace", "Race screen subtitle"}, {"confirmRace", "Race confirm button"}};
    private static final String[] HINTS = {
            "Description shown on the screen.",
            "One per line:  attribute amount [operation]   e.g.  generic.max_health 4   |   generic.attack_damage 15%   |   generic.movement_speed 0.1 multiply_total",
            "One per line:  item [count] [nbt]   e.g.  minecraft:iron_sword 1   (armour is equipped automatically)",
            "One per line:  Name | Description      (optional third part  | type:key=value;key=value  for scripted abilities)",
            "One per line, no leading slash, run once when picked.  @s is the player.   e.g.  effect give @s minecraft:speed 30 1"};

    private static final class Draft {
        String id = "", name = "", a = "", b = "", order = "0", desc = "", attrs = "", kit = "", abilities = "", commands = "";
    }

    private final Screen parent;
    private Tab tab = Tab.CLASSES;
    private int section;
    private Draft draft;
    private String selectedId = "";
    private boolean isNew;
    private int listScroll, seenVersion;
    private String status = "";
    private int statusColor;
    private String pendingSelect = "";

    private EditBox fId, fName, fA, fB, fOrder;
    private MultiLineEditBox big;
    private final List<EditBox> themeBoxes = new ArrayList<>();
    private Settings ts;   // theme/settings being edited

    private int pad = 12, bodyTop, bodyBottom, listW, fx, fw;

    public EditorScreen(Screen parent) {
        super(Component.literal("Sovereign Classes Editor"));
        this.parent = parent;
        seenVersion = Registry.version;
        ts = Settings.fromJson(Registry.settings.toJson());
    }

    private Def.Kind kind() { return tab == Tab.RACES ? Def.Kind.RACE : Def.Kind.CLASS; }

    // ---------- draft <-> def ----------
    private Draft toDraft(Def d) {
        Draft x = new Draft();
        x.id = d.id; x.name = d.name; x.order = Integer.toString(d.order); x.desc = d.description;
        if (d.kind == Def.Kind.CLASS) { x.a = Text.listToText(d.tags, ", "); x.b = d.icon; }
        else { x.a = d.image; x.b = Text.listToText(d.allowedClasses, ", "); }
        x.attrs = Text.attrsToText(d.attributes);
        x.kit = Text.kitToText(d.kit);
        x.abilities = Text.abilitiesToText(d.abilities);
        x.commands = String.join("\n", d.commands);
        return x;
    }

    private Def toDef(Draft x, Def.Kind k) {
        String id = Def.cleanId(x.id);
        if (id.isEmpty()) throw new IllegalArgumentException("ID is required (letters, numbers, _ and -)");
        Def d = new Def(k, id);
        d.name = x.name.trim();
        d.description = x.desc;
        try { d.order = Integer.parseInt(x.order.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Order must be a whole number"); }
        if (k == Def.Kind.CLASS) { d.tags = Text.parseList(x.a, ","); d.icon = x.b.trim(); }
        else { d.image = x.a.trim(); d.allowedClasses = Text.parseList(x.b, ","); }
        d.attributes = Text.parseAttrs(x.attrs);
        for (Def.Attr a : d.attributes)
            if (!ForgeRegistries.ATTRIBUTES.containsKey(new ResourceLocation(a.attribute)))
                throw new IllegalArgumentException("Unknown attribute: " + a.attribute);
        d.kit = Text.parseKit(x.kit);
        for (Def.Kit it : d.kit)
            if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(it.item)))
                throw new IllegalArgumentException("Unknown item: " + it.item);
        d.abilities = Text.parseAbilities(x.abilities);
        d.commands = Text.parseLines(x.commands);
        return d;
    }

    // ---------- widgets ----------
    private void commit() {
        if (tab == Tab.THEME) {
            for (EditBox b : themeBoxes) setTheme((String) keyOf(b), b.getValue());
            return;
        }
        if (draft == null) return;
        if (fId != null) draft.id = fId.getValue();
        if (fName != null) draft.name = fName.getValue();
        if (fA != null) draft.a = fA.getValue();
        if (fB != null) draft.b = fB.getValue();
        if (fOrder != null) draft.order = fOrder.getValue();
        if (big != null) switch (section) {
            case 0 -> draft.desc = big.getValue();
            case 1 -> draft.attrs = big.getValue();
            case 2 -> draft.kit = big.getValue();
            case 3 -> draft.abilities = big.getValue();
            default -> draft.commands = big.getValue();
        }
    }

    private final java.util.Map<EditBox, String> keys = new java.util.HashMap<>();
    private Object keyOf(EditBox b) { return keys.get(b); }

    private String getTheme(String k) {
        return switch (k) {
            case "accent" -> ts.accent; case "accentDark" -> ts.accentDark; case "heading" -> ts.heading; case "text" -> ts.text;
            case "dim" -> ts.dim; case "line" -> ts.line; case "positive" -> ts.positive; case "negative" -> ts.negative;
            case "bgTop" -> ts.bgTop; case "bgBottom" -> ts.bgBottom;
            case "hue" -> Text.fmt(ts.hue); case "saturation" -> Text.fmt(ts.saturation); case "brightness" -> Text.fmt(ts.brightness);
            case "titleClass" -> ts.titleClass; case "subtitleClass" -> ts.subtitleClass; case "confirmClass" -> ts.confirmClass;
            case "titleRace" -> ts.titleRace; case "subtitleRace" -> ts.subtitleRace; default -> ts.confirmRace;
        };
    }

    private void setTheme(String k, String v) {
        try {
            switch (k) {
                case "accent" -> ts.accent = v; case "accentDark" -> ts.accentDark = v; case "heading" -> ts.heading = v;
                case "text" -> ts.text = v; case "dim" -> ts.dim = v; case "line" -> ts.line = v;
                case "positive" -> ts.positive = v; case "negative" -> ts.negative = v;
                case "bgTop" -> ts.bgTop = v; case "bgBottom" -> ts.bgBottom = v;
                case "hue" -> ts.hue = Double.parseDouble(v.trim());
                case "saturation" -> ts.saturation = Double.parseDouble(v.trim());
                case "brightness" -> ts.brightness = Double.parseDouble(v.trim());
                case "titleClass" -> ts.titleClass = v; case "subtitleClass" -> ts.subtitleClass = v;
                case "confirmClass" -> ts.confirmClass = v; case "titleRace" -> ts.titleRace = v;
                case "subtitleRace" -> ts.subtitleRace = v; default -> ts.confirmRace = v;
            }
        } catch (NumberFormatException ignored) {}
    }

    private void restart() { commit(); rebuildWidgets(); }

    @Override
    protected void init() {
        Font f = Minecraft.getInstance().font;
        keys.clear();
        themeBoxes.clear();
        fId = fName = fA = fB = fOrder = null;
        big = null;
        pad = Skin.pad(width, height) > 16 ? 16 : 12;
        bodyTop = pad + 38;
        bodyBottom = height - pad - 26;
        listW = Math.max(100, Math.min(150, width / 5));
        fx = pad + listW + 8;
        fw = width - fx - pad;

        // tabs
        String[] names = {"Classes", "Races", "Theme"};
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            addRenderableWidget(new SkinButton(pad + i * 84, pad + 14, 80, 18, Component.literal(names[i]), b -> {
                commit(); tab = Tab.values()[ti]; section = 0; draft = null; selectedId = ""; isNew = false; listScroll = 0; status = ""; rebuildWidgets();
            }).highlight(tab.ordinal() == i));
        }

        // bottom buttons
        int by = height - pad - 20, bw = 86;
        addRenderableWidget(new SkinButton(width - pad - bw, by, bw, 20, Component.literal("Close"), b -> onClose()));
        if (tab == Tab.THEME) {
            addRenderableWidget(new SkinButton(width - pad - 2 * bw - 6, by, bw, 20, Component.literal("Save"), b -> saveTheme()));
            addRenderableWidget(new SkinButton(width - pad - 3 * bw - 12, by, bw, 20, Component.literal("Preview"), b -> preview()));
            initTheme(f);
            return;
        }
        addRenderableWidget(new SkinButton(width - pad - 2 * bw - 6, by, bw, 20, Component.literal("Save"), b -> save()));
        addRenderableWidget(new SkinButton(width - pad - 3 * bw - 12, by, bw, 20, Component.literal("Delete"), b -> delete()));

        // list
        addRenderableWidget(new SkinButton(pad, bodyTop + 4, listW, 18, Component.literal("+ New " + (tab == Tab.RACES ? "race" : "class")), b -> {
            commit();
            Draft d = new Draft();
            d.order = Integer.toString(Registry.of(kind()).size() + 1);
            draft = d; selectedId = ""; isNew = true; section = 0; status = "New entry - fill it in and press Save"; statusColor = Skin.s().cDim;
            rebuildWidgets();
        }));
        List<Def> defs = new ArrayList<>(Registry.of(kind()).values());
        int ly = bodyTop + 26, rows = Math.max(1, (bodyBottom - ly) / 18);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, defs.size() - rows)));
        for (int i = 0; i < rows && i + listScroll < defs.size(); i++) {
            Def d = defs.get(i + listScroll);
            String label = f.plainSubstrByWidth(d.displayName(), listW - 10);
            addRenderableWidget(new SkinButton(pad, ly + i * 18, listW, 16, Component.literal(label), b -> {
                commit(); draft = toDraft(d); selectedId = d.id; isNew = false; section = 0; status = ""; rebuildWidgets();
            }).highlight(d.id.equals(selectedId) && !isNew));
        }
        if (draft == null) return;

        // form
        boolean cls = tab == Tab.CLASSES;
        int y1 = bodyTop + 10;
        fId = box(f, fx, y1, 100, draft.id, 40);
        fId.setEditable(isNew);
        fName = box(f, fx + 106, y1, fw - 106 - 56, draft.name, 80);
        fOrder = box(f, fx + fw - 50, y1, 50, draft.order, 6);
        int y2 = y1 + 32, half = (fw - 6) / 2;
        fA = box(f, fx, y2, half, draft.a, 400);
        fB = box(f, fx + half + 6, y2, fw - half - 6, draft.b, 400);
        int sy = y2 + 24, n = SEC.length, sw = (fw - (n - 1) * 4) / n;
        for (int i = 0; i < n; i++) {
            final int si = i;
            addRenderableWidget(new SkinButton(fx + i * (sw + 4), sy, sw, 16, Component.literal(SEC[i]), b -> { section = si; restart(); }).highlight(section == i));
        }
        int by0 = sy + 20 + 22;
        int bh = Math.max(40, bodyBottom - 14 - by0);
        big = new MultiLineEditBox(f, fx, by0, fw, bh, Component.literal(""), Component.literal(""));
        big.setValue(switch (section) {
            case 0 -> draft.desc; case 1 -> draft.attrs; case 2 -> draft.kit; case 3 -> draft.abilities; default -> draft.commands;
        });
        addRenderableWidget(big);
    }

    private EditBox box(Font f, int x, int y, int w, String value, int max) {
        EditBox b = new EditBox(f, x, y, w, 18, Component.literal(""));
        b.setMaxLength(max);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    private void initTheme(Font f) {
        String[][] fields = section == 0 ? COLOURS : TEXTS;
        int n = SEC_THEME.length, sw = 120;
        for (int i = 0; i < n; i++) {
            final int si = i;
            addRenderableWidget(new SkinButton(pad + i * (sw + 4), bodyTop + 4, sw, 16, Component.literal(SEC_THEME[i]), b -> { section = si; restart(); }).highlight(section == i));
        }
        int cols = section == 0 ? 3 : 2;
        int gap = 10, cw = (width - 2 * pad - (cols - 1) * gap) / cols;
        int y0 = bodyTop + 38;
        for (int i = 0; i < fields.length; i++) {
            int c = i % cols, r = i / cols;
            EditBox b = new EditBox(f, pad + c * (cw + gap), y0 + r * 30, cw, 18, Component.literal(""));
            b.setMaxLength(120);
            b.setValue(getTheme(fields[i][0]));
            addRenderableWidget(b);
            themeBoxes.add(b);
            keys.put(b, fields[i][0]);
        }
        if (section == 1) {
            int ty = y0 + ((fields.length + 1) / 2) * 30 + 4;
            String[] lab = {"Require race", "Require class", "Race screen first", "Allow changing choice"};
            boolean[] val = {ts.requireRace, ts.requireClass, ts.raceFirst, ts.allowChange};
            int tw = (width - 2 * pad - 3 * 6) / 4;
            for (int i = 0; i < 4; i++) {
                final int ti = i;
                addRenderableWidget(new SkinButton(pad + i * (tw + 6), ty, tw, 18,
                        Component.literal(lab[i] + ": " + (val[i] ? "ON" : "OFF")), b -> {
                    commit();
                    switch (ti) {
                        case 0 -> ts.requireRace = !ts.requireRace;
                        case 1 -> ts.requireClass = !ts.requireClass;
                        case 2 -> ts.raceFirst = !ts.raceFirst;
                        default -> ts.allowChange = !ts.allowChange;
                    }
                    rebuildWidgets();
                }));
            }
        }
    }

    // ---------- actions ----------
    private void save() {
        commit();
        if (draft == null) { status = "Nothing to save"; statusColor = Skin.s().cNeg; return; }
        try {
            Def d = toDef(draft, kind());
            pendingSelect = d.id;
            Net.CH.sendToServer(new Net.SaveDef(kind() == Def.Kind.CLASS, false, d.id, Registry.toWire(d)));
            status = "Saving...";
            statusColor = Skin.s().cDim;
        } catch (IllegalArgumentException e) {
            status = e.getMessage();
            statusColor = Skin.s().cNeg;
        }
    }

    private void delete() {
        if (draft == null || isNew || selectedId.isEmpty()) { status = "Select an existing entry to delete"; statusColor = Skin.s().cNeg; return; }
        Net.CH.sendToServer(new Net.SaveDef(kind() == Def.Kind.CLASS, true, selectedId, ""));
        draft = null; selectedId = ""; status = "Deleted"; statusColor = Skin.s().cDim;
        rebuildWidgets();
    }

    private void preview() {
        commit();
        ts.compile();
        Registry.settings = ts;
        status = "Previewing (not saved yet)";
        statusColor = Skin.s().cDim;
        ts = Settings.fromJson(ts.toJson());
    }

    private void saveTheme() {
        commit();
        ts.compile();
        Net.CH.sendToServer(new Net.SaveSettings(ts.toJson().toString()));
        status = "Saved";
        statusColor = Skin.s().cPos;
    }

    @Override
    public void tick() {
        super.tick();
        if (seenVersion != Registry.version) {
            seenVersion = Registry.version;
            if (tab != Tab.THEME) {
                if (!pendingSelect.isEmpty()) {
                    Def d = Registry.of(kind()).get(pendingSelect);
                    if (d != null) { draft = toDraft(d); selectedId = d.id; isNew = false; status = "Saved"; statusColor = Skin.s().cPos; }
                    pendingSelect = "";
                } else if (!selectedId.isEmpty() && !Registry.of(kind()).containsKey(selectedId)) {
                    draft = null; selectedId = "";
                }
                rebuildWidgets();
            }
        }
    }

    // ---------- drawing ----------
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = Minecraft.getInstance().font;
        Settings st = Skin.s();
        Skin.background(g, width, height);
        g.drawCenteredString(f, Component.literal("Sovereign Classes Editor").withStyle(s -> s.withBold(true)), width / 2, pad + 2, st.cHeading);
        if (tab == Tab.THEME) {
            Skin.panel(g, pad - 4, bodyTop + 24, width - 2 * pad + 8, bodyBottom - bodyTop - 22);
            String[][] fields = section == 0 ? COLOURS : TEXTS;
            int cols = section == 0 ? 3 : 2, gap = 10, cw = (width - 2 * pad - (cols - 1) * gap) / cols, y0 = bodyTop + 38;
            for (int i = 0; i < fields.length; i++)
                g.drawString(f, fields[i][1], pad + (i % cols) * (cw + gap), y0 + (i / cols) * 30 - 10, st.cDim, false);
            if (section == 0) {
                g.drawString(f, "Colours are hex like #E8B028. Hue shift recolours the whole texture set; set the accent colours to match.", pad, bodyBottom - 12, st.cDim, false);
            }
        } else {
            Skin.panel(g, fx - 6, bodyTop, fw + 12, bodyBottom - bodyTop);
            if (draft == null) {
                g.drawCenteredString(f, Component.literal("Pick an entry on the left, or press + New."), fx + fw / 2, bodyTop + 40, st.cDim);
            } else {
                int y1 = bodyTop + 10;
                g.drawString(f, "ID", fx, y1 - 9, st.cDim, false);
                g.drawString(f, "Display name", fx + 106, y1 - 9, st.cDim, false);
                g.drawString(f, "Order", fx + fw - 50, y1 - 9, st.cDim, false);
                int y2 = y1 + 32, half = (fw - 6) / 2;
                boolean cls = tab == Tab.CLASSES;
                g.drawString(f, cls ? "Tags (comma separated)" : "Image (optional resource path)", fx, y2 - 9, st.cDim, false);
                g.drawString(f, cls ? "Icon item (optional)" : "Allowed classes (comma separated, empty = all)", fx + half + 6, y2 - 9, st.cDim, false);
                int hy = y2 + 24 + 20;
                var lines = f.split(Component.literal(HINTS[section]), fw);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(f, lines.get(i), fx, hy + i * 9, st.cDim, false);
            }
        }
        if (!status.isEmpty()) g.drawString(f, f.plainSubstrByWidth(status, width - 2 * pad - 280), pad, height - pad - 14, statusColor, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab != Tab.THEME && mx < pad + listW + 4 && my > bodyTop + 22) {
            commit();
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
