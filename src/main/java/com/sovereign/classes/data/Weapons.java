package com.sovereign.classes.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * weapons.json: weapon types (which items count as a dagger, a katana ...), entity groups (demon, boss ...)
 * and class locks (items only some classes may use). Matchers are written the same way everywhere:
 *   minecraft:iron_sword   exact id
 *   #minecraft:swords      tag
 *   *katana*               any id containing the word
 *   @empty                 bare hands (types only)
 * Types are checked in list order and the first match wins, so put specific types before generic ones.
 */
public final class Weapons {
    public static final class Type {
        public String id = "", name = "";
        public List<String> match = new ArrayList<>();
        public String label() { return name == null || name.isBlank() ? id : name; }
    }

    public static final class Lock {
        public String name = "";
        public List<String> match = new ArrayList<>();
        public List<String> classes = new ArrayList<>();
    }

    public List<Type> types = new ArrayList<>();
    public List<Type> groups = new ArrayList<>();
    public List<Lock> locks = new ArrayList<>();

    // ---------- matching ----------
    /** @param tag answers whether the thing being tested has the given tag id (without the #). */
    public static boolean matches(String pattern, String id, boolean empty, Predicate<String> tag) {
        String p = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return false;
        if (p.equals("@empty")) return empty;
        if (empty) return false;
        if (p.startsWith("#")) return tag != null && tag.test(p.substring(1));
        if (p.startsWith("*") && p.endsWith("*") && p.length() > 2) return id.contains(p.substring(1, p.length() - 1));
        if (p.startsWith("*")) return id.endsWith(p.substring(1));
        if (p.endsWith("*")) return id.startsWith(p.substring(0, p.length() - 1));
        return id.equals(p.contains(":") ? p : "minecraft:" + p);
    }

    public static boolean matchesAny(List<String> l, String id, boolean empty, Predicate<String> tag) {
        for (String s : l) if (matches(s, id, empty, tag)) return true;
        return false;
    }

    /** First type that matches, or null. */
    public Type typeOf(String id, boolean empty, Predicate<String> tag) {
        for (Type t : types) if (matchesAny(t.match, id, empty, tag)) return t;
        return null;
    }

    public Type group(String id) {
        for (Type g : groups) if (g.id.equals(id)) return g;
        return null;
    }

    /** Classes allowed to use the item, or null when it is not locked. */
    public List<String> lockedTo(String id, Predicate<String> tag) {
        List<String> out = null;
        for (Lock l : locks) {
            if (!matchesAny(l.match, id, false, tag)) continue;
            if (out == null) out = new ArrayList<>();
            out.addAll(l.classes);
        }
        return out;
    }

    // ---------- json ----------
    private static List<String> strs(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static List<Type> readTypes(JsonObject o, String key) {
        List<Type> out = new ArrayList<>();
        JsonElement a = o.get(key);
        if (a != null && a.isJsonArray()) for (JsonElement x : a.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Type t = new Type();
            t.id = Def.cleanId(str(j, "id"));
            t.name = str(j, "name");
            t.match = strs(j, "match");
            if (!t.id.isEmpty()) out.add(t);
        }
        return out;
    }

    private static JsonArray writeTypes(List<Type> l) {
        JsonArray a = new JsonArray();
        for (Type t : l) {
            JsonObject j = new JsonObject();
            j.addProperty("id", t.id);
            j.addProperty("name", t.name);
            JsonArray m = new JsonArray();
            for (String s : t.match) m.add(s);
            j.add("match", m);
            a.add(j);
        }
        return a;
    }

    public static Weapons fromJson(JsonObject o) {
        Weapons w = new Weapons();
        w.types = readTypes(o, "types");
        w.groups = readTypes(o, "groups");
        JsonElement a = o.get("locks");
        if (a != null && a.isJsonArray()) for (JsonElement x : a.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Lock l = new Lock();
            l.name = str(j, "name");
            l.match = strs(j, "match");
            l.classes = strs(j, "classes");
            if (!l.match.isEmpty()) w.locks.add(l);
        }
        return w;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("types", writeTypes(types));
        o.add("groups", writeTypes(groups));
        JsonArray a = new JsonArray();
        for (Lock l : locks) {
            JsonObject j = new JsonObject();
            j.addProperty("name", l.name);
            JsonArray m = new JsonArray();
            for (String s : l.match) m.add(s);
            j.add("match", m);
            JsonArray c = new JsonArray();
            for (String s : l.classes) c.add(s);
            j.add("classes", c);
            a.add(j);
        }
        o.add("locks", a);
        return o;
    }

    // ---------- starter file ----------
    private static Type t(String id, String name, String... m) {
        Type x = new Type();
        x.id = id; x.name = name;
        for (String s : m) x.match.add(s);
        return x;
    }

    public static Weapons defaults() {
        Weapons w = new Weapons();
        w.types.add(t("unarmed", "Bare hands", "@empty"));
        w.types.add(t("fist", "Knuckles & gauntlets", "*knuckle*", "*gauntlet*", "*fist*", "*claw*"));
        w.types.add(t("dagger", "Daggers", "*dagger*", "*knife*", "*kunai*", "*dirk*"));
        w.types.add(t("katana", "Katanas", "*katana*", "*tachi*", "*nodachi*"));
        w.types.add(t("greatsword", "Greatswords", "*greatsword*", "*claymore*", "*zweihander*", "*longsword*"));
        w.types.add(t("greataxe", "Greataxes", "*greataxe*", "*battleaxe*", "*battle_axe*"));
        w.types.add(t("scythe", "Scythes", "*scythe*", "*sickle*"));
        w.types.add(t("spear", "Spears & lances", "*spear*", "*lance*", "*pike*", "*halberd*", "*glaive*", "minecraft:trident"));
        w.types.add(t("hammer", "Hammers & maces", "*hammer*", "*mace*", "*flail*"));
        w.types.add(t("crossbow", "Crossbows", "*crossbow*"));
        w.types.add(t("bow", "Bows", "minecraft:bow", "*longbow*", "*shortbow*"));
        w.types.add(t("staff", "Staves & wands", "*staff*", "*wand*", "*scepter*", "*rod*"));
        w.types.add(t("sword", "Swords", "#minecraft:swords", "*sword*"));
        w.types.add(t("axe", "Axes", "#minecraft:axes", "*_axe"));
        w.groups.add(t("demon", "Demons", "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube", "minecraft:zoglin",
                "minecraft:vex", "minecraft:evoker", "minecraft:ravager", "*demon*", "*devil*", "*fiend*"));
        return w;
    }
}
