package com.sovereign.classes.client;

import com.sovereign.classes.Net;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Entry points called from packets. Signatures use common types only, so a dedicated server never loads client classes. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void sync(String settings, List<String> classes, List<String> races, String race, String cls) {
        try {
            Settings s = Registry.settingsFromWire(settings);
            List<Def> c = new ArrayList<>(), r = new ArrayList<>();
            for (String j : classes) c.add(Registry.fromWire(Def.Kind.CLASS, j));
            for (String j : races) r.add(Registry.fromWire(Def.Kind.RACE, j));
            Registry.install(s, c, r);
        } catch (Exception e) {
            System.err.println("[sovereignclasses] bad sync packet: " + e);
        }
        ClientState.race = race;
        ClientState.cls = cls;
    }

    public static void open(int kind) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case Net.OPEN_RACE -> mc.setScreen(new RaceScreen());
            case Net.OPEN_CLASS -> mc.setScreen(new ClassScreen());
            case Net.OPEN_EDITOR -> mc.setScreen(new EditorScreen(null));
            default -> {
                if (mc.screen instanceof ClassScreen || mc.screen instanceof RaceScreen) mc.setScreen(null);
            }
        }
    }
}
