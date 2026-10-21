package com.sovereign.classes;

import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private Net() {}

    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SovereignClasses.MODID, "main"), () -> V, V::equals, V::equals);
    private static final int MAX = 1 << 20;

    public static final int OPEN_RACE = 0, OPEN_CLASS = 1, OPEN_EDITOR = 2, CLOSE = -1;

    public static void init() {
        int id = 0;
        CH.registerMessage(id++, Sync.class, Sync::enc, Sync::dec, Sync::handle);
        CH.registerMessage(id++, Open.class, Open::enc, Open::dec, Open::handle);
        CH.registerMessage(id++, Choose.class, Choose::enc, Choose::dec, Choose::handle);
        CH.registerMessage(id++, SaveDef.class, SaveDef::enc, SaveDef::dec, SaveDef::handle);
        CH.registerMessage(id++, SaveSettings.class, SaveSettings::enc, SaveSettings::dec, SaveSettings::handle);
        CH.registerMessage(id++, SaveWeapons.class, SaveWeapons::enc, SaveWeapons::dec, SaveWeapons::handle);
    }

    // ---------- helpers ----------
    public static void sendSync(ServerPlayer sp) {
        CH.send(PacketDistributor.PLAYER.with(() -> sp), buildSync(sp));
    }

    public static void sendSyncAll(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) sendSync(sp);
    }

    public static void open(ServerPlayer sp, int kind) {
        CH.send(PacketDistributor.PLAYER.with(() -> sp), new Open(kind));
    }

    private static Sync buildSync(ServerPlayer sp) {
        Sync s = new Sync();
        s.settings = Registry.settings.toJson().toString();
        s.weapons = Registry.weapons.toJson().toString();
        for (Def d : Registry.classes.values()) s.classes.add(Registry.toWire(d));
        for (Def d : Registry.races.values()) s.races.add(Registry.toWire(d));
        s.race = PlayerState.race(sp);
        s.cls = PlayerState.cls(sp);
        return s;
    }

    private static void client(Supplier<NetworkEvent.Context> c, Runnable r) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> r));
        c.get().setPacketHandled(true);
    }

    private static void server(Supplier<NetworkEvent.Context> c, java.util.function.Consumer<ServerPlayer> r) {
        c.get().enqueueWork(() -> {
            ServerPlayer sp = c.get().getSender();
            if (sp != null) r.accept(sp);
        });
        c.get().setPacketHandled(true);
    }

    // ---------- server -> client ----------
    public static final class Sync {
        String settings = "{}", weapons = "{}";
        List<String> classes = new ArrayList<>(), races = new ArrayList<>();
        String race = "", cls = "";

        static void enc(Sync m, FriendlyByteBuf b) {
            b.writeUtf(m.settings, MAX);
            b.writeUtf(m.weapons, MAX);
            b.writeVarInt(m.classes.size());
            for (String s : m.classes) b.writeUtf(s, MAX);
            b.writeVarInt(m.races.size());
            for (String s : m.races) b.writeUtf(s, MAX);
            b.writeUtf(m.race, 256);
            b.writeUtf(m.cls, 256);
        }

        static Sync dec(FriendlyByteBuf b) {
            Sync m = new Sync();
            m.settings = b.readUtf(MAX);
            m.weapons = b.readUtf(MAX);
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) m.classes.add(b.readUtf(MAX));
            n = b.readVarInt();
            for (int i = 0; i < n; i++) m.races.add(b.readUtf(MAX));
            m.race = b.readUtf(256);
            m.cls = b.readUtf(256);
            return m;
        }

        static void handle(Sync m, Supplier<NetworkEvent.Context> c) {
            client(c, () -> com.sovereign.classes.client.ClientHooks.sync(m.settings, m.weapons, m.classes, m.races, m.race, m.cls));
        }
    }

    public static final class Open {
        final int kind;
        public Open(int k) { kind = k; }
        static void enc(Open m, FriendlyByteBuf b) { b.writeVarInt(m.kind + 1); }
        static Open dec(FriendlyByteBuf b) { return new Open(b.readVarInt() - 1); }
        static void handle(Open m, Supplier<NetworkEvent.Context> c) {
            client(c, () -> com.sovereign.classes.client.ClientHooks.open(m.kind));
        }
    }

    // ---------- client -> server ----------
    public static final class Choose {
        final boolean isClass;
        final String id;
        public Choose(boolean isClass, String id) { this.isClass = isClass; this.id = id; }
        static void enc(Choose m, FriendlyByteBuf b) { b.writeBoolean(m.isClass); b.writeUtf(m.id, 256); }
        static Choose dec(FriendlyByteBuf b) { return new Choose(b.readBoolean(), b.readUtf(256)); }
        static void handle(Choose m, Supplier<NetworkEvent.Context> c) {
            server(c, sp -> Server.choose(sp, m.isClass, m.id));
        }
    }

    public static final class SaveDef {
        final boolean isClass, delete;
        final String id, json;
        public SaveDef(boolean isClass, boolean delete, String id, String json) {
            this.isClass = isClass; this.delete = delete; this.id = id; this.json = json;
        }
        static void enc(SaveDef m, FriendlyByteBuf b) {
            b.writeBoolean(m.isClass); b.writeBoolean(m.delete); b.writeUtf(m.id, 256); b.writeUtf(m.json, MAX);
        }
        static SaveDef dec(FriendlyByteBuf b) { return new SaveDef(b.readBoolean(), b.readBoolean(), b.readUtf(256), b.readUtf(MAX)); }
        static void handle(SaveDef m, Supplier<NetworkEvent.Context> c) {
            server(c, sp -> Server.saveDef(sp, m.isClass, m.delete, m.id, m.json));
        }
    }

    public static final class SaveSettings {
        final String json;
        public SaveSettings(String json) { this.json = json; }
        static void enc(SaveSettings m, FriendlyByteBuf b) { b.writeUtf(m.json, MAX); }
        static SaveSettings dec(FriendlyByteBuf b) { return new SaveSettings(b.readUtf(MAX)); }
        static void handle(SaveSettings m, Supplier<NetworkEvent.Context> c) {
            server(c, sp -> Server.saveSettings(sp, m.json));
        }
    }

    public static final class SaveWeapons {
        final String json;
        public SaveWeapons(String json) { this.json = json; }
        static void enc(SaveWeapons m, FriendlyByteBuf b) { b.writeUtf(m.json, MAX); }
        static SaveWeapons dec(FriendlyByteBuf b) { return new SaveWeapons(b.readUtf(MAX)); }
        static void handle(SaveWeapons m, Supplier<NetworkEvent.Context> c) {
            server(c, sp -> Server.saveWeapons(sp, m.json));
        }
    }

    /** Used by the client to validate before sending. */
    public static Settings parseSettings(String json) { return Registry.settingsFromWire(json); }
}
