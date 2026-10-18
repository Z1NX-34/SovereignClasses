package com.sovereign.classes.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Line-based text forms used by the in-game editor.
 *   attributes  generic.max_health 4            (add)
 *               generic.attack_damage 15%       (+15 %, multiply_base)
 *               generic.movement_speed 0.1 multiply_total
 *   kit         minecraft:iron_sword 1 {Enchantments:[...]}    (count and nbt optional)
 *   abilities   Name | Description | type:key=value;key=value  (type part optional)
 *   commands    one command per line, no leading slash, @s is the player
 */
public final class Text {
    private Text() {}

    public static String normId(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : "minecraft:" + s;
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (String l : (s == null ? "" : s).split("\\R")) if (!l.isBlank()) out.add(l.trim());
        return out;
    }

    // ---------- attributes ----------
    public static String attrsToText(List<Def.Attr> l) {
        StringBuilder sb = new StringBuilder();
        for (Def.Attr a : l) {
            sb.append(a.attribute).append(' ').append(fmt(a.amount));
            if (!"addition".equals(a.operation)) sb.append(' ').append(a.operation);
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return Long.toString((long) d);
        String s = String.format(Locale.ROOT, "%.4f", d);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static List<Def.Attr> parseAttrs(String text) {
        List<Def.Attr> out = new ArrayList<>();
        int n = 0;
        for (String line : lines(text)) {
            n++;
            String[] p = line.split("\\s+");
            if (p.length < 2) throw new IllegalArgumentException("attributes line " + n + ": need  <attribute> <amount>");
            Def.Attr a = new Def.Attr();
            a.attribute = normId(p[0]);
            String v = p[1];
            boolean pct = v.endsWith("%");
            if (pct) v = v.substring(0, v.length() - 1);
            try {
                a.amount = Double.parseDouble(v.startsWith("+") ? v.substring(1) : v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("attributes line " + n + ": '" + p[1] + "' is not a number");
            }
            a.operation = pct ? "multiply_base" : "addition";
            if (pct) a.amount /= 100.0;
            if (p.length > 2) {
                String op = p[2].toLowerCase(Locale.ROOT);
                if (!op.equals("addition") && !op.equals("multiply_base") && !op.equals("multiply_total"))
                    throw new IllegalArgumentException("attributes line " + n + ": operation must be addition, multiply_base or multiply_total");
                a.operation = op;
            }
            out.add(a);
        }
        return out;
    }

    // ---------- kit ----------
    public static String kitToText(List<Def.Kit> l) {
        StringBuilder sb = new StringBuilder();
        for (Def.Kit k : l) {
            sb.append(k.item).append(' ').append(k.count);
            if (!k.nbt.isBlank()) sb.append(' ').append(k.nbt);
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static List<Def.Kit> parseKit(String text) {
        List<Def.Kit> out = new ArrayList<>();
        int n = 0;
        for (String line : lines(text)) {
            n++;
            String[] p = line.split("\\s+", 3);
            Def.Kit k = new Def.Kit();
            k.item = normId(p[0]);
            if (p.length > 1) {
                try { k.count = Math.max(1, Integer.parseInt(p[1])); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("kit line " + n + ": count '" + p[1] + "' is not a number"); }
            }
            if (p.length > 2) k.nbt = p[2];
            out.add(k);
        }
        return out;
    }

    // ---------- abilities ----------
    public static String abilitiesToText(List<Def.Ability> l) {
        StringBuilder sb = new StringBuilder();
        for (Def.Ability a : l) {
            sb.append(a.name).append(" | ").append(a.description);
            if (!a.type.isBlank()) {
                sb.append(" | ").append(a.type);
                String sep = ":";
                for (var e : a.params.entrySet()) {
                    sb.append(sep).append(e.getKey()).append('=').append(e.getValue().getAsString());
                    sep = ";";
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static List<Def.Ability> parseAbilities(String text) {
        List<Def.Ability> out = new ArrayList<>();
        for (String line : lines(text)) {
            String[] p = line.split("\\|", 3);
            Def.Ability a = new Def.Ability();
            a.name = p[0].trim();
            a.description = p.length > 1 ? p[1].trim() : "";
            if (p.length > 2) {
                String t = p[2].trim();
                int c = t.indexOf(':');
                a.type = (c < 0 ? t : t.substring(0, c)).trim();
                a.params = new JsonObject();
                if (c >= 0) for (String kv : t.substring(c + 1).split(";")) {
                    int e = kv.indexOf('=');
                    if (e > 0) a.params.addProperty(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
                }
            }
            out.add(a);
        }
        return out;
    }

    // ---------- simple lists ----------
    public static String listToText(List<String> l, String sep) { return String.join(sep, l); }

    public static List<String> parseList(String text, String sepRegex) {
        List<String> out = new ArrayList<>();
        for (String s : (text == null ? "" : text).split(sepRegex)) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    public static List<String> parseLines(String text) { return lines(text); }
}
