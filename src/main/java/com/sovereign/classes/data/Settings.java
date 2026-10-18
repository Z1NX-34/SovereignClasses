package com.sovereign.classes.data;

import com.google.gson.JsonObject;

/** settings.json: behaviour switches, titles and the theme (colours + hue shift of the textures). */
public final class Settings {
    public boolean requireRace = true, requireClass = true, raceFirst = true, allowChange = false;

    public String titleClass = "Choose Your Path";
    public String subtitleClass = "A/D or arrows - choose     Enter - confirm";
    public String confirmClass = "Follow This Path";
    public String titleRace = "Choose Your Race";
    public String subtitleRace = "Select a race before continuing";
    public String confirmRace = "Confirm Race";

    public String accent = "#E8B028";
    public String accentDark = "#824F10";
    public String heading = "#FFD36A";
    public String text = "#EFE6D2";
    public String dim = "#B9A9D8";
    public String line = "#46386C";
    public String positive = "#9BE58F";
    public String negative = "#FF8A80";
    public String bgTop = "#0A0614";
    public String bgBottom = "#1B1133";
    /** Texture recolouring: hue in degrees, saturation and brightness as multipliers. */
    public double hue = 0, saturation = 1, brightness = 1;

    // compiled ARGB colours
    public transient int cAccent, cAccentDark, cHeading, cText, cDim, cLine, cPos, cNeg, cBgTop, cBgBottom;

    public Settings() { compile(); }

    public static int parse(String s, int fallback) {
        try {
            String h = s.trim();
            if (h.startsWith("#")) h = h.substring(1);
            long v = Long.parseLong(h, 16);
            if (h.length() == 6) return 0xFF000000 | (int) v;
            if (h.length() == 8) return (int) v;
        } catch (Exception ignored) {}
        return fallback;
    }

    public void compile() {
        cAccent = parse(accent, 0xFFE8B028);
        cAccentDark = parse(accentDark, 0xFF824F10);
        cHeading = parse(heading, 0xFFFFD36A);
        cText = parse(text, 0xFFEFE6D2);
        cDim = parse(dim, 0xFFB9A9D8);
        cLine = parse(line, 0xFF46386C);
        cPos = parse(positive, 0xFF9BE58F);
        cNeg = parse(negative, 0xFFFF8A80);
        cBgTop = parse(bgTop, 0xFF0A0614);
        cBgBottom = parse(bgBottom, 0xFF1B1133);
    }

    public String textureKey() { return hue + "/" + saturation + "/" + brightness; }

    private static String s(JsonObject o, String k, String d) {
        try { return o.has(k) ? o.get(k).getAsString() : d; } catch (Exception e) { return d; }
    }
    private static boolean b(JsonObject o, String k, boolean d) {
        try { return o.has(k) ? o.get(k).getAsBoolean() : d; } catch (Exception e) { return d; }
    }
    private static double n(JsonObject o, String k, double d) {
        try { return o.has(k) ? o.get(k).getAsDouble() : d; } catch (Exception e) { return d; }
    }

    public static Settings fromJson(JsonObject o) {
        Settings x = new Settings();
        x.requireRace = b(o, "require_race", x.requireRace);
        x.requireClass = b(o, "require_class", x.requireClass);
        x.raceFirst = b(o, "race_first", x.raceFirst);
        x.allowChange = b(o, "allow_change", x.allowChange);
        x.titleClass = s(o, "title_class", x.titleClass);
        x.subtitleClass = s(o, "subtitle_class", x.subtitleClass);
        x.confirmClass = s(o, "confirm_class", x.confirmClass);
        x.titleRace = s(o, "title_race", x.titleRace);
        x.subtitleRace = s(o, "subtitle_race", x.subtitleRace);
        x.confirmRace = s(o, "confirm_race", x.confirmRace);
        JsonObject t = o.has("theme") && o.get("theme").isJsonObject() ? o.getAsJsonObject("theme") : new JsonObject();
        x.accent = s(t, "accent", x.accent);
        x.accentDark = s(t, "accent_dark", x.accentDark);
        x.heading = s(t, "heading", x.heading);
        x.text = s(t, "text", x.text);
        x.dim = s(t, "dim", x.dim);
        x.line = s(t, "line", x.line);
        x.positive = s(t, "positive", x.positive);
        x.negative = s(t, "negative", x.negative);
        x.bgTop = s(t, "background_top", x.bgTop);
        x.bgBottom = s(t, "background_bottom", x.bgBottom);
        x.hue = n(t, "hue", x.hue);
        x.saturation = n(t, "saturation", x.saturation);
        x.brightness = n(t, "brightness", x.brightness);
        x.compile();
        return x;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("require_race", requireRace);
        o.addProperty("require_class", requireClass);
        o.addProperty("race_first", raceFirst);
        o.addProperty("allow_change", allowChange);
        o.addProperty("title_class", titleClass);
        o.addProperty("subtitle_class", subtitleClass);
        o.addProperty("confirm_class", confirmClass);
        o.addProperty("title_race", titleRace);
        o.addProperty("subtitle_race", subtitleRace);
        o.addProperty("confirm_race", confirmRace);
        JsonObject t = new JsonObject();
        t.addProperty("accent", accent);
        t.addProperty("accent_dark", accentDark);
        t.addProperty("heading", heading);
        t.addProperty("text", text);
        t.addProperty("dim", dim);
        t.addProperty("line", line);
        t.addProperty("positive", positive);
        t.addProperty("negative", negative);
        t.addProperty("background_top", bgTop);
        t.addProperty("background_bottom", bgBottom);
        t.addProperty("hue", hue);
        t.addProperty("saturation", saturation);
        t.addProperty("brightness", brightness);
        o.add("theme", t);
        return o;
    }
}
