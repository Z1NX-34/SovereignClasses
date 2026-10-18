package com.sovereign.classes.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Holds the loaded data. The server fills it from config/sovereignclasses/, clients receive a copy through
 * the sync packet. Snapshots are immutable and swapped whole, so readers on other threads are safe.
 */
public final class Registry {
    private Registry() {}

    public static volatile Map<String, Def> classes = new LinkedHashMap<>();
    public static volatile Map<String, Def> races = new LinkedHashMap<>();
    public static volatile Settings settings = new Settings();
    public static volatile int version = 0;

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("sovereignclasses"); }

    public static Map<String, Def> of(Def.Kind k) { return k == Def.Kind.CLASS ? classes : races; }

    private static Map<String, Def> sorted(Collection<Def> in) {
        List<Def> l = new ArrayList<>(in);
        l.sort(Comparator.comparingInt((Def d) -> d.order).thenComparing(d -> d.id));
        Map<String, Def> m = new LinkedHashMap<>();
        for (Def d : l) m.put(d.id, d);
        return m;
    }

    public static void install(Settings s, Collection<Def> cl, Collection<Def> ra) {
        classes = sorted(cl);
        races = sorted(ra);
        settings = s;
        version++;
    }

    // ---------- disk (server side) ----------
    private static List<Def> readFolder(Def.Kind kind) {
        List<Def> out = new ArrayList<>();
        Path d = dir().resolve(kind.folder);
        try {
            Files.createDirectories(d);
            try (Stream<Path> st = Files.list(d)) {
                for (Path p : (Iterable<Path>) st.filter(x -> x.getFileName().toString().endsWith(".json")).sorted()::iterator) {
                    String fn = p.getFileName().toString();
                    String id = Def.cleanId(fn.substring(0, fn.length() - 5));
                    if (id.isEmpty()) continue;
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                        out.add(Def.fromJson(kind, id, o));
                    } catch (Exception e) {
                        System.err.println("[sovereignclasses] could not read " + p + ": " + e);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[sovereignclasses] " + e);
        }
        return out;
    }

    public static synchronized void loadDisk() {
        Settings s = new Settings();
        Path sp = dir().resolve("settings.json");
        try {
            Files.createDirectories(dir());
            if (Files.exists(sp)) s = Settings.fromJson(JsonParser.parseString(Files.readString(sp, StandardCharsets.UTF_8)).getAsJsonObject());
            else Files.writeString(sp, PRETTY.toJson(s.toJson()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[sovereignclasses] settings: " + e);
        }
        install(s, readFolder(Def.Kind.CLASS), readFolder(Def.Kind.RACE));
    }

    public static synchronized void writeDef(Def d) throws IOException {
        Path f = dir().resolve(d.kind.folder);
        Files.createDirectories(f);
        Files.writeString(f.resolve(d.id + ".json"), PRETTY.toJson(d.toJson()), StandardCharsets.UTF_8);
    }

    public static synchronized void deleteDef(Def.Kind kind, String id) throws IOException {
        Files.deleteIfExists(dir().resolve(kind.folder).resolve(Def.cleanId(id) + ".json"));
    }

    public static synchronized void writeSettings(Settings s) throws IOException {
        Files.createDirectories(dir());
        Files.writeString(dir().resolve("settings.json"), PRETTY.toJson(s.toJson()), StandardCharsets.UTF_8);
    }

    // ---------- wire format ----------
    public static String toWire(Def d) {
        JsonObject o = d.toJson();
        o.addProperty("id", d.id);
        return o.toString();
    }

    public static Def fromWire(Def.Kind kind, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String id = Def.cleanId(o.has("id") ? o.get("id").getAsString() : "");
        return Def.fromJson(kind, id, o);
    }

    public static Settings settingsFromWire(String json) {
        return Settings.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
}
