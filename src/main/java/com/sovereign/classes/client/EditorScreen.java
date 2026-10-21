package com.sovereign.classes.client;

import com.sovereign.classes.Net;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import com.sovereign.classes.data.Text;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
 * Everything is written to config/sovereignclasses/ and synced to all players at once.
 * Every field is bound to the draft as you type, so switching tabs or pickers never loses text.
 */
public class EditorScreen extends Screen {
    private enum Tab { CLASSES, RACES, WEAPONS, THEME }

    private static final String[] SEC_C = {"General", "Attributes", "Kit", "Abilities", "Effects", "Weapons", "Rules", "Commands"};
    private static final String[] SEC_R = {"General", "Attributes", "Kit", "Abilities", "Effects", "Weapons", "Rules", "Commands", "Classes"};
    private static final String[] SEC_W = {"Weapon types", "Entity groups", "Class locks"};
    private static final String[] SEC_THEME = {"Colours", "Texts & options"};
    private static final String[][] COLOURS = {
            {"accent", "Accent"}, {"accentDark", "Accent dark"}, {"heading", "Headings"}, {"text", "Text"}, {"dim", "Dim text"},
            {"line", "Lines"}, {"positive", "Positive"}, {"negative", "Negative"}, {"bgTop", "Background top"},
            {"bgBottom", "Background bottom"}, {"hue", "Hue shift (deg)"}, {"saturation", "Saturation x"}, {"brightness", "Brightness x"}};
    private static final String[][] TEXTS = {
            {"titleClass", "Class screen title"}, {"subtitleClass", "Class screen subtitle"}, {"confirmClass", "Class confirm button"},
            {"titleRace", "Race screen title"}, {"subtitleRace", "Race screen subtitle"}, {"confirmRace", "Race confirm button"}};

    private static String hint(String sec) {
        return switch (sec) {
            case "General" -> "Description shown on the screen.";
            case "Attributes" -> "What this changes. Flat adds a number; % of base / % of total change by a percentage.";
            case "Kit" -> "Items given when picked. Armour is equipped for you. NBT is optional (enchantments etc.).";
            case "Abilities" -> "Text under Special abilities. Scripted abilities come later.";
            case "Effects" -> "Potion effects. Level starts at 1. Seconds 0 = permanent while this is active.";
            case "Weapons" -> "Damage change per weapon type, in %. 0 = full damage, -60 = 60% less, +20 = 20% more. 'Other weapons' covers every type not listed.";
            case "Rules" -> "Extra damage rules. Dealt = damage this player does, Taken = damage they receive. Percents of the same kind add up.";
            case "Commands" -> "Advanced: one command per line, no leading slash, @s = the player. Runs once when picked.";
            default -> "Click to allow or block classes for this race. None ticked = every class is allowed.";
        };
    }

    // ---------- draft ----------
    private static final class AttrRow { String attr; String amount = "1"; String op = "addition"; AttrRow(String a) { attr = a; } }
    private static final class KitRow { String item; String count = "1"; String nbt = ""; KitRow(String i) { item = i; } }
    private static final class AbRow { String name = "", desc = "", type = ""; JsonObject params = new JsonObject(); }
    private static final class EffRow { String effect; String level = "1"; String seconds = "0"; EffRow(String e) { effect = e; } }

    private static final class WpRow { String type; String percent = "0"; WpRow(String t) { type = t; } }
    private static final class RuRow { String when = "dealt", filter; String percent = "10"; RuRow(String f) { filter = f; } }
    private static final class WtRow { String id = "", name = "", match = ""; }
    private static final class LkRow { String match = "", classes = ""; }

    private static final class Draft {
        String id = "", name = "", a = "", b = "", order = "0", desc = "", commands = "", other = "0";
        List<WpRow> wp = new ArrayList<>();
        List<RuRow> ru = new ArrayList<>();
        List<AttrRow> attrs = new ArrayList<>();
        List<KitRow> kit = new ArrayList<>();
        List<AbRow> abilities = new ArrayList<>();
        List<EffRow> effects = new ArrayList<>();
        Set<String> allowed = new LinkedHashSet<>();
    }

    private record Lbl(int x, int y, String text, int color, String icon) {}

    private final Screen parent;
    private Tab tab = Tab.CLASSES;
    private int section;
    private Draft draft;
    private String selectedId = "";
    private boolean isNew;
    private int listScroll, rowScroll, seenVersion;
    private String status = "";
    private int statusColor;
    private String pendingSelect = "";
    private Settings ts;

    private final List<Lbl> labels = new ArrayList<>();
    private int rowsY0, rowsVis, rowsTotal;
    private int pad = 14, bodyTop, bodyBottom, listW, fx, fw;
    private static final int ROW = 22;

    public EditorScreen(Screen parent) {
        super(Component.literal("Sovereign Classes Editor"));
        this.parent = parent;
        seenVersion = Registry.version;
        ts = Settings.fromJson(Registry.settings.toJson());
    }

    private Def.Kind kind() { return tab == Tab.RACES ? Def.Kind.RACE : Def.Kind.CLASS; }
    private String[] secs() { return tab == Tab.RACES ? SEC_R : SEC_C; }
    private boolean defTab() { return tab == Tab.CLASSES || tab == Tab.RACES; }
    private String secName() { return secs()[Math.min(section, secs().length - 1)]; }

    // ---------- draft <-> def ----------
    private Draft toDraft(Def d) {
        Draft x = new Draft();
        x.id = d.id; x.name = d.name; x.order = Integer.toString(d.order); x.desc = d.description;
        if (d.kind == Def.Kind.CLASS) { x.a = Text.listToText(d.tags, ", "); x.b = d.icon; } else { x.a = d.image; }
        for (Def.Attr a : d.attributes) {
            AttrRow r = new AttrRow(a.attribute);
            r.op = a.operation;
            r.amount = Text.fmt("addition".equals(a.operation) ? a.amount : Math.round(a.amount * 10000.0) / 100.0);
            x.attrs.add(r);
        }
        for (Def.Kit k : d.kit) { KitRow r = new KitRow(k.item); r.count = Integer.toString(k.count); r.nbt = k.nbt; x.kit.add(r); }
        for (Def.Ability a : d.abilities) { AbRow r = new AbRow(); r.name = a.name; r.desc = a.description; r.type = a.type; r.params = a.params; x.abilities.add(r); }
        for (Def.Effect e : d.effects) { EffRow r = new EffRow(e.effect); r.level = Integer.toString(e.amplifier + 1); r.seconds = Integer.toString(e.seconds); x.effects.add(r); }
        x.commands = String.join("\n", d.commands);
        x.other = Text.fmt(d.weaponOther);
        for (Def.WeaponRow w : d.weapons) { WpRow r = new WpRow(w.type); r.percent = Text.fmt(w.percent); x.wp.add(r); }
        for (Def.Rule u : d.rules) { RuRow r = new RuRow(u.filter); r.when = u.when; r.percent = Text.fmt(u.percent); x.ru.add(r); }
        x.allowed.addAll(d.allowedClasses);
        return x;
    }

    private static double num(String s, String what) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(what + ": '" + s + "' is not a number"); }
    }

    private Def toDef(Draft x, Def.Kind k) {
        String id = Def.cleanId(x.id);
        if (id.isEmpty()) throw new IllegalArgumentException("ID is required (letters, numbers, _ and -)");
        Def d = new Def(k, id);
        d.name = x.name.trim();
        d.description = x.desc;
        try { d.order = Integer.parseInt(x.order.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Order must be a whole number"); }
        if (k == Def.Kind.CLASS) { d.tags = Text.parseList(x.a, ","); d.icon = x.b.trim(); } else { d.image = x.a.trim(); }
        for (AttrRow r : x.attrs) {
            Def.Attr a = new Def.Attr();
            a.attribute = r.attr; a.operation = r.op;
            a.amount = num(r.amount, Skin.attrName(r.attr));
            if (!"addition".equals(r.op)) a.amount /= 100.0;
            d.attributes.add(a);
        }
        for (KitRow r : x.kit) {
            Def.Kit t = new Def.Kit();
            t.item = r.item; t.nbt = r.nbt.trim();
            t.count = Math.max(1, (int) num(r.count, Skin.itemName(r.item)));
            d.kit.add(t);
        }
        for (AbRow r : x.abilities) {
            if (r.name.isBlank() && r.desc.isBlank()) continue;
            Def.Ability a = new Def.Ability();
            a.name = r.name.trim(); a.description = r.desc.trim(); a.type = r.type; a.params = r.params;
            d.abilities.add(a);
        }
        for (EffRow r : x.effects) {
            Def.Effect e = new Def.Effect();
            e.effect = r.effect;
            e.amplifier = Math.max(0, (int) num(r.level, Skin.effectName(r.effect)) - 1);
            e.seconds = Math.max(0, (int) num(r.seconds, Skin.effectName(r.effect)));
            d.effects.add(e);
        }
        d.commands = Text.parseLines(x.commands);
        d.weaponOther = num(x.other, "Other weapons");
        for (WpRow r : x.wp) { Def.WeaponRow w = new Def.WeaponRow(); w.type = r.type; w.percent = num(r.percent, r.type); d.weapons.add(w); }
        for (RuRow r : x.ru) { Def.Rule u = new Def.Rule(); u.when = r.when; u.filter = r.filter; u.percent = num(r.percent, PickerScreen.filterLabel(r.filter)); d.rules.add(u); }
        if (k == Def.Kind.RACE) for (String c : Registry.classes.keySet()) if (x.allowed.contains(c)) d.allowedClasses.add(c);
        if (k == Def.Kind.RACE) for (String c : x.allowed) if (!d.allowedClasses.contains(c) && Registry.classes.containsKey(c)) d.allowedClasses.add(c);
        return d;
    }

    // ---------- theme access ----------
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

    // ---------- widget helpers ----------
    private EditBox box(int x, int y, int w, String value, int max, Consumer<String> setter) {
        EditBox b = new EditBox(Minecraft.getInstance().font, x, y, w, 18, Component.literal(""));
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(setter);
        addRenderableWidget(b);
        return b;
    }

    private void pick(String title, List<PickerScreen.Source> src, Consumer<String> onPick) {
        Minecraft.getInstance().setScreen(new PickerScreen(this, title, src, onPick));
    }

    // ---------- build ----------
    @Override
    protected void init() {
        Font f = Minecraft.getInstance().font;
        labels.clear();
        rowsVis = rowsTotal = 0;
        pad = Skin.pad(width, height) > 16 ? 16 : 12;
        bodyTop = pad + 38;
        bodyBottom = height - pad - 26;
        listW = Math.max(100, Math.min(150, width / 5));
        fx = pad + listW + 8;
        fw = width - fx - pad;

        String[] names = {"Classes", "Races", "Weapons", "Theme"};
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            addRenderableWidget(new SkinButton(pad + i * 84, pad + 14, 80, 18, Component.literal(names[i]), b -> {
                tab = Tab.values()[ti]; section = 0; rowScroll = 0; draft = null; selectedId = ""; isNew = false; listScroll = 0; status = "";
                rebuildWidgets();
            }).highlight(tab.ordinal() == i));
        }

        int by = height - pad - 20, bw = 86;
        addRenderableWidget(new SkinButton(width - pad - bw, by, bw, 20, Component.literal("Close"), b -> onClose()));
        if (tab == Tab.THEME) {
            addRenderableWidget(new SkinButton(width - pad - 2 * bw - 6, by, bw, 20, Component.literal("Save"), b -> saveTheme()));
            addRenderableWidget(new SkinButton(width - pad - 3 * bw - 12, by, bw, 20, Component.literal("Preview"), b -> preview()));
            initTheme();
            return;
        }
        if (tab == Tab.WEAPONS) {
            addRenderableWidget(new SkinButton(width - pad - 2 * bw - 6, by, bw, 20, Component.literal("Save"), b -> saveWeapons()));
            initWeapons();
            return;
        }
        addRenderableWidget(new SkinButton(width - pad - 2 * bw - 6, by, bw, 20, Component.literal("Save"), b -> save()));
        addRenderableWidget(new SkinButton(width - pad - 3 * bw - 12, by, bw, 20, Component.literal("Delete"), b -> delete()));

        addRenderableWidget(new SkinButton(pad, bodyTop + 4, listW, 18, Component.literal("+ New " + (tab == Tab.RACES ? "race" : "class")), b -> {
            Draft d = new Draft();
            d.order = Integer.toString(Registry.of(kind()).size() + 1);
            draft = d; selectedId = ""; isNew = true; section = 0; rowScroll = 0;
            status = "New entry - fill it in and press Save"; statusColor = Skin.s().cDim;
            rebuildWidgets();
        }));
        List<Def> defs = new ArrayList<>(Registry.of(kind()).values());
        int ly = bodyTop + 26, rows = Math.max(1, (bodyBottom - ly) / 18);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, defs.size() - rows)));
        for (int i = 0; i < rows && i + listScroll < defs.size(); i++) {
            Def d = defs.get(i + listScroll);
            addRenderableWidget(new SkinButton(pad, ly + i * 18, listW, 16, Component.literal(d.displayName()), b -> {
                draft = toDraft(d); selectedId = d.id; isNew = false; section = 0; rowScroll = 0; status = ""; rebuildWidgets();
            }).highlight(d.id.equals(selectedId) && !isNew));
        }
        if (draft == null) return;
        final Draft dr = draft;

        // fixed fields
        boolean cls = tab == Tab.CLASSES;
        int y1 = bodyTop + 10;
        EditBox idBox = box(fx, y1, 100, dr.id, 40, v -> dr.id = v);
        idBox.setEditable(isNew);
        box(fx + 106, y1, fw - 106 - 56, dr.name, 80, v -> dr.name = v);
        box(fx + fw - 50, y1, 50, dr.order, 6, v -> dr.order = v);
        int y2 = y1 + 32, half = (fw - 6) / 2;
        if (cls) {
            box(fx, y2, half, dr.a, 400, v -> dr.a = v);
            box(fx + half + 6, y2, fw - half - 6 - 46, dr.b, 400, v -> dr.b = v);
            addRenderableWidget(new SkinButton(fx + fw - 44, y2, 44, 18, Component.literal("Pick"), b ->
                    pick("Pick an icon", List.of(PickerScreen.items(), PickerScreen.textures()), id -> dr.b = id)));
        } else {
            box(fx, y2, fw - 46, dr.a, 400, v -> dr.a = v);
            addRenderableWidget(new SkinButton(fx + fw - 44, y2, 44, 18, Component.literal("Pick"), b ->
                    pick("Pick a picture", List.of(PickerScreen.textures()), id -> dr.a = id)));
        }

        // sections
        String[] sn = secs();
        int sy = y2 + 24, n = sn.length, sw = (fw - (n - 1) * 4) / n;
        for (int i = 0; i < n; i++) {
            final int si = i;
            addRenderableWidget(new SkinButton(fx + i * (sw + 4), sy, sw, 16, Component.literal(sn[i]), b -> { section = si; rowScroll = 0; rebuildWidgets(); }).highlight(section == i));
        }
        int ay0 = sy + 20 + 22, ay1 = bodyBottom - 14;
        String sec = secName();
        switch (sec) {
            case "General" -> bigBox(f, ay0, ay1, dr.desc, v -> dr.desc = v);
            case "Commands" -> bigBox(f, ay0, ay1, dr.commands, v -> dr.commands = v);
            case "Attributes" -> attributeRows(f, dr, ay0, ay1);
            case "Kit" -> kitRows(f, dr, ay0, ay1);
            case "Abilities" -> abilityRows(f, dr, ay0, ay1);
            case "Effects" -> effectRows(f, dr, ay0, ay1);
            case "Weapons" -> weaponRows(f, dr, ay0, ay1);
            case "Rules" -> ruleRows(f, dr, ay0, ay1);
            default -> classRows(dr, ay0, ay1);
        }
    }

    private void bigBox(Font f, int y0, int y1, String value, Consumer<String> setter) {
        MultiLineEditBox big = new MultiLineEditBox(f, fx, y0, fw, Math.max(40, y1 - y0), Component.literal(""), Component.literal(""));
        big.setValue(value);
        big.setValueListener(setter);
        addRenderableWidget(big);
    }

    /** Common part of the row sections: Add button, scrolling window. Returns the first row y. */
    private int rowsHead(String addText, Runnable onAdd, int ay0, int ay1, int total) {
        addRenderableWidget(new SkinButton(fx, ay0 - 2, 130, 16, Component.literal(addText), b -> onAdd.run()));
        rowsY0 = ay0 + 18;
        rowsVis = Math.max(1, (ay1 - rowsY0) / ROW);
        rowsTotal = total;
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, total - rowsVis)));
        return rowsY0;
    }

    private SkinButton removeBtn(int x, int y, Runnable r) {
        return addRenderableWidget(new SkinButton(x, y, 18, 18, Component.literal("X"), b -> { r.run(); rebuildWidgets(); }));
    }

    private void attributeRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add attribute", () -> pick("Add attribute", List.of(PickerScreen.attributes()), id -> {
            dr.attrs.add(new AttrRow(id));
            rowScroll = Integer.MAX_VALUE;
        }), ay0, ay1, dr.attrs.size());
        int xr = fx + fw - 12, opW = 70, amtW = 54;
        int amtX = xr - 22 - 4 - opW - 4 - amtW;
        labels.add(new Lbl(amtX, ay0 + 2, "Amount", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < dr.attrs.size(); i++) {
            AttrRow r = dr.attrs.get(i + rowScroll);
            int y = y0 + i * ROW;
            labels.add(new Lbl(fx + 4, y + 5, f.plainSubstrByWidth(Skin.attrName(r.attr), amtX - fx - 12), Skin.s().cText, null));
            box(amtX, y, amtW, r.amount, 10, v -> r.amount = v);
            addRenderableWidget(new SkinButton(amtX + amtW + 4, y, opW, 18,
                    Component.literal(switch (r.op) { case "multiply_base" -> "% of base"; case "multiply_total" -> "% of total"; default -> "Flat"; }), b -> {
                r.op = switch (r.op) { case "addition" -> "multiply_base"; case "multiply_base" -> "multiply_total"; default -> "addition"; };
                rebuildWidgets();
            }));
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.attrs.remove(idx));
        }
    }

    private void kitRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add item", () -> pick("Add item", List.of(PickerScreen.items()), id -> {
            dr.kit.add(new KitRow(id));
            rowScroll = Integer.MAX_VALUE;
        }), ay0, ay1, dr.kit.size());
        int xr = fx + fw - 12, nbtW = Math.max(60, fw / 4), cntW = 34;
        int nbtX = xr - 22 - nbtW, cntX = nbtX - 4 - cntW;
        labels.add(new Lbl(cntX, ay0 + 2, "Count", Skin.s().cDim, null));
        labels.add(new Lbl(nbtX, ay0 + 2, "NBT (optional)", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < dr.kit.size(); i++) {
            KitRow r = dr.kit.get(i + rowScroll);
            int y = y0 + i * ROW;
            labels.add(new Lbl(fx + 4, y + 1, "", 0, r.item));
            labels.add(new Lbl(fx + 24, y + 5, f.plainSubstrByWidth(Skin.itemName(r.item), cntX - fx - 32), Skin.s().cText, null));
            box(cntX, y, cntW, r.count, 3, v -> r.count = v);
            box(nbtX, y, nbtW, r.nbt, 2000, v -> r.nbt = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.kit.remove(idx));
        }
    }

    private void abilityRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add ability", () -> {
            dr.abilities.add(new AbRow());
            rowScroll = Integer.MAX_VALUE;
            rebuildWidgets();
        }, ay0, ay1, dr.abilities.size());
        int xr = fx + fw - 12, nameW = Math.max(80, fw * 3 / 10);
        int descX = fx + nameW + 4, descW = xr - 22 - descX;
        labels.add(new Lbl(fx + 2, ay0 + 2, "Name", Skin.s().cDim, null));
        labels.add(new Lbl(descX, ay0 + 2, "Description", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < dr.abilities.size(); i++) {
            AbRow r = dr.abilities.get(i + rowScroll);
            int y = y0 + i * ROW;
            box(fx, y, nameW, r.name, 60, v -> r.name = v);
            box(descX, y, descW, r.desc, 400, v -> r.desc = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.abilities.remove(idx));
        }
    }

    private void effectRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add effect", () -> pick("Add effect", List.of(PickerScreen.effects()), id -> {
            dr.effects.add(new EffRow(id));
            rowScroll = Integer.MAX_VALUE;
        }), ay0, ay1, dr.effects.size());
        int xr = fx + fw - 12, secW = 54, lvlW = 40;
        int secX = xr - 22 - secW, lvlX = secX - 4 - lvlW;
        labels.add(new Lbl(lvlX, ay0 + 2, "Level", Skin.s().cDim, null));
        labels.add(new Lbl(secX, ay0 + 2, "Seconds", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < dr.effects.size(); i++) {
            EffRow r = dr.effects.get(i + rowScroll);
            int y = y0 + i * ROW;
            labels.add(new Lbl(fx + 4, y + 5, f.plainSubstrByWidth(Skin.effectName(r.effect), lvlX - fx - 12), Skin.s().cText, null));
            box(lvlX, y, lvlW, r.level, 3, v -> r.level = v);
            box(secX, y, secW, r.seconds, 6, v -> r.seconds = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.effects.remove(idx));
        }
    }

    private void weaponRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add weapon type", () -> pick("Add weapon type", List.of(PickerScreen.weaponTypes()), id -> {
            if (dr.wp.stream().noneMatch(r -> r.type.equals(id))) dr.wp.add(new WpRow(id));
            rowScroll = Integer.MAX_VALUE;
        }), ay0, ay1, dr.wp.size());
        int xr = fx + fw - 12, pctW = 54, pctX = xr - 22 - pctW;
        labels.add(new Lbl(pctX - 112, ay0 + 2, "Other weapons %", Skin.s().cDim, null));
        box(pctX, ay0 - 2, pctW, dr.other, 8, v -> dr.other = v);
        for (int i = 0; i < rowsVis && i + rowScroll < dr.wp.size(); i++) {
            WpRow r = dr.wp.get(i + rowScroll);
            int y = y0 + i * ROW;
            com.sovereign.classes.data.Weapons.Type t = Registry.weapons.types.stream().filter(x -> x.id.equals(r.type)).findFirst().orElse(null);
            labels.add(new Lbl(fx + 4, y + 5, f.plainSubstrByWidth(t == null ? r.type + " (unknown)" : t.label(), pctX - fx - 12), Skin.s().cText, null));
            box(pctX, y, pctW, r.percent, 8, v -> r.percent = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.wp.remove(idx));
        }
    }

    private void ruleRows(Font f, Draft dr, int ay0, int ay1) {
        int y0 = rowsHead("+ Add rule", () -> pick("Add rule - what kind of damage?", List.of(PickerScreen.filters(), PickerScreen.damageTypes()), id -> {
            dr.ru.add(new RuRow(id));
            rowScroll = Integer.MAX_VALUE;
        }), ay0, ay1, dr.ru.size());
        int xr = fx + fw - 12, pctW = 54, pctX = xr - 22 - pctW, whenW = 56;
        int fX = fx + whenW + 4;
        labels.add(new Lbl(pctX, ay0 + 2, "Change %", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < dr.ru.size(); i++) {
            RuRow r = dr.ru.get(i + rowScroll);
            int y = y0 + i * ROW;
            addRenderableWidget(new SkinButton(fx, y, whenW, 18, Component.literal("dealt".equals(r.when) ? "Dealt" : "Taken"), b -> {
                r.when = "dealt".equals(r.when) ? "taken" : "dealt";
                rebuildWidgets();
            }));
            addRenderableWidget(new SkinButton(fX, y, pctX - fX - 4, 18, Component.literal(PickerScreen.filterLabel(r.filter)), b ->
                    pick("Change damage kind", List.of(PickerScreen.filters(), PickerScreen.damageTypes()), id -> r.filter = id)));
            box(pctX, y, pctW, r.percent, 8, v -> r.percent = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> dr.ru.remove(idx));
        }
    }

    // ---------- weapons tab ----------
    private List<WtRow> wTypes, wGroups;
    private List<LkRow> wLocks;
    private int wsec;

    private void loadWeapons() {
        if (wTypes != null) return;
        wTypes = new ArrayList<>(); wGroups = new ArrayList<>(); wLocks = new ArrayList<>();
        for (var t : Registry.weapons.types) { WtRow r = new WtRow(); r.id = t.id; r.name = t.name; r.match = Text.listToText(t.match, ", "); wTypes.add(r); }
        for (var t : Registry.weapons.groups) { WtRow r = new WtRow(); r.id = t.id; r.name = t.name; r.match = Text.listToText(t.match, ", "); wGroups.add(r); }
        for (var l : Registry.weapons.locks) { LkRow r = new LkRow(); r.match = Text.listToText(l.match, ", "); r.classes = Text.listToText(l.classes, ", "); wLocks.add(r); }
    }

    private static String append(String list, String id) {
        return list.isBlank() ? id : list.trim() + ", " + id;
    }

    private void initWeapons() {
        loadWeapons();
        fx = pad; fw = width - 2 * pad;
        for (int i = 0; i < SEC_W.length; i++) {
            final int si = i;
            addRenderableWidget(new SkinButton(pad + i * 124, bodyTop + 4, 120, 16, Component.literal(SEC_W[i]), b -> { wsec = si; rowScroll = 0; rebuildWidgets(); }).highlight(wsec == i));
        }
        int ay0 = bodyTop + 52, ay1 = bodyBottom - 6;
        int xr = fx + fw - 12;
        if (wsec == 2) {
            int y0 = rowsHead("+ Add lock", () -> { wLocks.add(new LkRow()); rowScroll = Integer.MAX_VALUE; rebuildWidgets(); }, ay0, ay1, wLocks.size());
            int clsW = Math.max(100, fw / 4), clsX = xr - 22 - clsW, matchX = fx + 44, matchW = clsX - 44 - 4 - matchX;
            labels.add(new Lbl(matchX, ay0 + 2, "Items (id, #tag or *word*)", Skin.s().cDim, null));
            labels.add(new Lbl(clsX, ay0 + 2, "Only these classes (ids)", Skin.s().cDim, null));
            for (int i = 0; i < rowsVis && i + rowScroll < wLocks.size(); i++) {
                LkRow r = wLocks.get(i + rowScroll);
                int y = y0 + i * ROW;
                addRenderableWidget(new SkinButton(fx, y, 40, 18, Component.literal("Item"), b ->
                        pick("Lock which item?", List.of(PickerScreen.items()), id -> r.match = append(r.match, id))));
                box(matchX, y, matchW, r.match, 600, v -> r.match = v);
                addRenderableWidget(new SkinButton(clsX - 44, y, 40, 18, Component.literal("Class"), b ->
                        pick("Allow which class?", List.of(PickerScreen.classesSource()), id -> r.classes = append(r.classes, id))));
                box(clsX, y, clsW, r.classes, 300, v -> r.classes = v);
                final int idx = i + rowScroll;
                removeBtn(xr - 18, y, () -> wLocks.remove(idx));
            }
            return;
        }
        List<WtRow> rows = wsec == 0 ? wTypes : wGroups;
        int y0 = rowsHead(wsec == 0 ? "+ Add weapon type" : "+ Add group", () -> { rows.add(new WtRow()); rowScroll = Integer.MAX_VALUE; rebuildWidgets(); }, ay0, ay1, rows.size());
        int idW = 80, nameW = 120, idX = fx + 44, nameX = idX + idW + 4, matchX = nameX + nameW + 4, matchW = xr - 22 - matchX;
        labels.add(new Lbl(idX, ay0 + 2, "ID", Skin.s().cDim, null));
        labels.add(new Lbl(nameX, ay0 + 2, "Name", Skin.s().cDim, null));
        labels.add(new Lbl(matchX, ay0 + 2, wsec == 0 ? "Matches (checked top to bottom, first wins)" : "Matches (entity ids, #tags, *words*)", Skin.s().cDim, null));
        for (int i = 0; i < rowsVis && i + rowScroll < rows.size(); i++) {
            WtRow r = rows.get(i + rowScroll);
            int y = y0 + i * ROW;
            addRenderableWidget(new SkinButton(fx, y, 40, 18, Component.literal(wsec == 0 ? "Item" : "Mob"), b ->
                    pick(wsec == 0 ? "Add an item" : "Add a mob", List.of(wsec == 0 ? PickerScreen.items() : PickerScreen.entities()), id -> r.match = append(r.match, id))));
            box(idX, y, idW, r.id, 30, v -> r.id = v);
            box(nameX, y, nameW, r.name, 40, v -> r.name = v);
            box(matchX, y, matchW, r.match, 800, v -> r.match = v);
            final int idx = i + rowScroll;
            removeBtn(xr - 18, y, () -> rows.remove(idx));
        }
    }

    private void saveWeapons() {
        com.sovereign.classes.data.Weapons w = new com.sovereign.classes.data.Weapons();
        for (int g = 0; g < 2; g++) {
            for (WtRow r : g == 0 ? wTypes : wGroups) {
                var t = new com.sovereign.classes.data.Weapons.Type();
                t.id = Def.cleanId(r.id);
                if (t.id.isEmpty()) continue;
                t.name = r.name.trim(); t.match = Text.parseList(r.match, ",");
                (g == 0 ? w.types : w.groups).add(t);
            }
        }
        for (LkRow r : wLocks) {
            var l = new com.sovereign.classes.data.Weapons.Lock();
            l.match = Text.parseList(r.match, ","); l.classes = Text.parseList(r.classes, ",");
            if (!l.match.isEmpty()) w.locks.add(l);
        }
        Net.CH.sendToServer(new Net.SaveWeapons(w.toJson().toString()));
        wTypes = null;
        status = "Saved"; statusColor = Skin.s().cPos;
    }

    private void classRows(Draft dr, int ay0, int ay1) {
        List<String> ids = new ArrayList<>(Registry.classes.keySet());
        int cols = 2, gap = 6, cw = (fw - 12 - gap) / cols;
        rowsY0 = ay0 + 2;
        int vis = Math.max(1, (ay1 - rowsY0) / ROW);
        rowsVis = vis;
        rowsTotal = (ids.size() + cols - 1) / cols;
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, rowsTotal - vis)));
        if (ids.isEmpty()) labels.add(new Lbl(fx + 4, rowsY0 + 4, "No classes exist yet.", Skin.s().cDim, null));
        for (int r = 0; r < vis && r + rowScroll < rowsTotal; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (r + rowScroll) * cols + c;
                if (i >= ids.size()) break;
                String id = ids.get(i);
                Def d = Registry.classes.get(id);
                boolean on = dr.allowed.contains(id);
                addRenderableWidget(new SkinButton(fx + c * (cw + gap), rowsY0 + r * ROW, cw, 18,
                        Component.literal((on ? "[x] " : "[ ] ") + d.displayName()), b -> {
                    if (!dr.allowed.remove(id)) dr.allowed.add(id);
                    rebuildWidgets();
                }).highlight(on));
            }
        }
    }

    private void initTheme() {
        String[][] fields = section == 0 ? COLOURS : TEXTS;
        int sw = 120;
        for (int i = 0; i < SEC_THEME.length; i++) {
            final int si = i;
            addRenderableWidget(new SkinButton(pad + i * (sw + 4), bodyTop + 4, sw, 16, Component.literal(SEC_THEME[i]), b -> { section = si; rebuildWidgets(); }).highlight(section == i));
        }
        int cols = section == 0 ? 3 : 2;
        int gap = 10, cw = (width - 2 * pad - (cols - 1) * gap) / cols;
        int y0 = bodyTop + 38;
        for (int i = 0; i < fields.length; i++) {
            int c = i % cols, r = i / cols;
            final String key = fields[i][0];
            box(pad + c * (cw + gap), y0 + r * 30, cw, getTheme(key), 120, v -> setTheme(key, v));
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
        ts.compile();
        Registry.settings = ts;
        ts = Settings.fromJson(ts.toJson());
        status = "Previewing (not saved yet)";
        statusColor = Skin.s().cDim;
    }

    private void saveTheme() {
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
            if (defTab()) {
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
            if (section == 0)
                g.drawString(f, "Colours are hex like #E8B028. Hue shift recolours the whole texture set; set the accent colours to match.", pad, bodyBottom - 12, st.cDim, false);
        } else if (tab == Tab.WEAPONS) {
            Skin.panel(g, pad - 6, bodyTop, width - 2 * pad + 12, bodyBottom - bodyTop);
            String[] hints = {
                    "Which items count as which weapon type. Classes then get a damage % per type. Use an item id, #tag, or *word* (matches any id containing it).",
                    "Groups of mobs for rules like 'Against demons'. Undead, boss and player already exist built in.",
                    "Items only the listed classes may use (attack, use, mine). Others get a message and nothing happens."};
            var lines = f.split(Component.literal(hints[wsec]), width - 2 * pad - 8);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(f, lines.get(i), pad, bodyTop + 26 + i * 9, st.cDim, false);
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
                if (tab == Tab.CLASSES) {
                    g.drawString(f, "Tags (comma separated)", fx, y2 - 9, st.cDim, false);
                    g.drawString(f, "Icon (item or texture) - Pick", fx + half + 6, y2 - 9, st.cDim, false);
                } else {
                    g.drawString(f, "Picture (texture path, optional) - Pick", fx, y2 - 9, st.cDim, false);
                }
                int hy = y2 + 24 + 20;
                var lines = f.split(Component.literal(hint(secName())), fw);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(f, lines.get(i), fx, hy + i * 9, st.cDim, false);
            }
        }
        for (Lbl l : labels) {
            if (l.icon() != null) Skin.drawIcon(g, l.icon(), l.x(), l.y());
            else g.drawString(f, l.text(), l.x(), l.y(), l.color(), false);
        }
        if (rowsTotal > 0 && ((defTab() && draft != null) || tab == Tab.WEAPONS))
            Skin.scrollBar(g, fx + fw - 6, rowsY0, rowsVis * ROW, rowsVis, rowsTotal, rowScroll);
        if (!status.isEmpty()) g.drawString(f, f.plainSubstrByWidth(status, width - 2 * pad - 280), pad, height - pad - 14, statusColor, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = -(int) Math.signum(delta);
        if (defTab() && mx < fx - 8 && my > bodyTop + 22) {
            listScroll = Math.max(0, listScroll + d);
            rebuildWidgets();
            return true;
        }
        if (((defTab() && draft != null) || tab == Tab.WEAPONS) && rowsTotal > 0 && mx >= fx && my >= rowsY0) {
            rowScroll = Math.max(0, Math.min(rowScroll + d, Math.max(0, rowsTotal - rowsVis)));
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
