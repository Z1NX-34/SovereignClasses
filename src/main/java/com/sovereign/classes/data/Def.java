package com.sovereign.classes.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** One class or one race. Both share the same shape; a few fields only matter for one kind. */
public final class Def {
    public enum Kind {
        CLASS("classes"), RACE("races");
        public final String folder;
        Kind(String f) { folder = f; }
    }

    public static final class Attr {
        public String attribute = "";
        public double amount;
        /** addition | multiply_base | multiply_total */
        public String operation = "addition";
    }

    public static final class Kit {
        public String item = "";
        public int count = 1;
        public String nbt = "";
    }

    public static final class Ability {
        public String name = "";
        public String description = "";
        /** Reserved for scripted abilities (added in a later version); kept so nothing is lost on save. */
        public String type = "";
        public JsonObject params = new JsonObject();
    }

    public static final class Effect {
        public String effect = "";
        /** 0-based amplifier (level 1 = 0). */
        public int amplifier;
        /** 0 = permanent while the class/race is active. */
        public int seconds;
    }

    public Kind kind;
    public String id = "";
    public String name = "";
    public String description = "";
    public String icon = "";
    public String image = "";
    public int order;
    public List<String> tags = new ArrayList<>();
    public List<Attr> attributes = new ArrayList<>();
    public List<Kit> kit = new ArrayList<>();
    public List<String> commands = new ArrayList<>();
    public List<Ability> abilities = new ArrayList<>();
    public List<Effect> effects = new ArrayList<>();
    public List<String> allowedClasses = new ArrayList<>();

    public Def(Kind kind, String id) { this.kind = kind; this.id = id; }

    public String displayName() { return name == null || name.isBlank() ? id : name; }

    // ---------- json ----------
    private static String str(JsonObject o, String k, String d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : d;
    }

    private static List<String> strList(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        return out;
    }

    public static Def fromJson(Kind kind, String id, JsonObject o) {
        Def d = new Def(kind, id);
        d.name = str(o, "name", "");
        d.description = str(o, "description", "");
        d.icon = str(o, "icon", "");
        d.image = str(o, "image", "");
        try { d.order = o.has("order") ? o.get("order").getAsInt() : 0; } catch (Exception ignored) {}
        d.tags = strList(o, "tags");
        d.commands = strList(o, "commands");
        d.allowedClasses = strList(o, "allowed_classes");
        JsonElement a = o.get("attributes");
        if (a != null && a.isJsonArray()) for (JsonElement x : a.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Attr at = new Attr();
            at.attribute = str(j, "attribute", "");
            try { at.amount = j.get("amount").getAsDouble(); } catch (Exception ignored) {}
            at.operation = str(j, "operation", "addition");
            if (!at.attribute.isBlank()) d.attributes.add(at);
        }
        JsonElement k = o.get("kit");
        if (k != null && k.isJsonArray()) for (JsonElement x : k.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Kit kt = new Kit();
            kt.item = str(j, "item", "");
            try { kt.count = Math.max(1, j.get("count").getAsInt()); } catch (Exception ignored) {}
            kt.nbt = str(j, "nbt", "");
            if (!kt.item.isBlank()) d.kit.add(kt);
        }
        JsonElement ab = o.get("abilities");
        if (ab != null && ab.isJsonArray()) for (JsonElement x : ab.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Ability b = new Ability();
            b.name = str(j, "name", "");
            b.description = str(j, "description", "");
            b.type = str(j, "type", "");
            if (j.has("params") && j.get("params").isJsonObject()) b.params = j.getAsJsonObject("params");
            d.abilities.add(b);
        }
        JsonElement ef = o.get("effects");
        if (ef != null && ef.isJsonArray()) for (JsonElement x : ef.getAsJsonArray()) {
            if (!x.isJsonObject()) continue;
            JsonObject j = x.getAsJsonObject();
            Effect e = new Effect();
            e.effect = str(j, "effect", "");
            try { e.amplifier = Math.max(0, j.get("amplifier").getAsInt()); } catch (Exception ignored) {}
            try { e.seconds = Math.max(0, j.get("seconds").getAsInt()); } catch (Exception ignored) {}
            if (!e.effect.isBlank()) d.effects.add(e);
        }
        return d;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("order", order);
        if (kind == Kind.CLASS) {
            JsonArray t = new JsonArray();
            for (String s : tags) t.add(s);
            o.add("tags", t);
        }
        if (!icon.isBlank()) o.addProperty("icon", icon);
        if (!image.isBlank()) o.addProperty("image", image);
        if (kind == Kind.RACE) {
            JsonArray al = new JsonArray();
            for (String s : allowedClasses) al.add(s);
            o.add("allowed_classes", al);
        }
        JsonArray at = new JsonArray();
        for (Attr a : attributes) {
            JsonObject j = new JsonObject();
            j.addProperty("attribute", a.attribute);
            j.addProperty("amount", a.amount);
            j.addProperty("operation", a.operation);
            at.add(j);
        }
        o.add("attributes", at);
        JsonArray kt = new JsonArray();
        for (Kit k : kit) {
            JsonObject j = new JsonObject();
            j.addProperty("item", k.item);
            j.addProperty("count", k.count);
            if (!k.nbt.isBlank()) j.addProperty("nbt", k.nbt);
            kt.add(j);
        }
        o.add("kit", kt);
        JsonArray ab = new JsonArray();
        for (Ability b : abilities) {
            JsonObject j = new JsonObject();
            j.addProperty("name", b.name);
            j.addProperty("description", b.description);
            if (!b.type.isBlank()) {
                j.addProperty("type", b.type);
                j.add("params", b.params);
            }
            ab.add(j);
        }
        o.add("abilities", ab);
        JsonArray ef = new JsonArray();
        for (Effect e : effects) {
            JsonObject j = new JsonObject();
            j.addProperty("effect", e.effect);
            j.addProperty("amplifier", e.amplifier);
            j.addProperty("seconds", e.seconds);
            ef.add(j);
        }
        o.add("effects", ef);
        JsonArray cm = new JsonArray();
        for (String s : commands) cm.add(s);
        o.add("commands", cm);
        return o;
    }

    public static String cleanId(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char ch : (raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT)).toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') sb.append(ch);
            else if (ch == ' ') sb.append('_');
        }
        return sb.toString();
    }
}
