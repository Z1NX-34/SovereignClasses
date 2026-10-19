package com.sovereign.classes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Settings;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SovereignClasses.MODID)
public final class Server {
    private Server() {}

    private static final String MOD_PREFIX = "sovereignclasses:";

    // ---------- events ----------
    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) { Registry.loadDisk(); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            applyAll(sp, false);
            Net.sendSync(sp);
            prompt(sp, false);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            applyAll(sp, false);
            Net.sendSync(sp);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(Player.PERSISTED_NBT_TAG))
            e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, old.getCompound(Player.PERSISTED_NBT_TAG).copy());
    }

    // ---------- selection ----------
    /** Opens whatever screen the player still has to go through; closes the screens when nothing is left. */
    public static void prompt(ServerPlayer sp, boolean closeWhenDone) {
        Settings s = Registry.settings;
        boolean needRace = s.requireRace && !Registry.races.isEmpty() && PlayerState.race(sp).isEmpty();
        boolean needClass = s.requireClass && !Registry.classes.isEmpty() && PlayerState.cls(sp).isEmpty();
        if (needRace && (s.raceFirst || !needClass)) Net.open(sp, Net.OPEN_RACE);
        else if (needClass) Net.open(sp, Net.OPEN_CLASS);
        else if (closeWhenDone) Net.open(sp, Net.CLOSE);
    }

    public static void choose(ServerPlayer sp, boolean isClass, String id) {
        Settings s = Registry.settings;
        String have = isClass ? PlayerState.cls(sp) : PlayerState.race(sp);
        if (!have.isEmpty() && !s.allowChange) return;
        Def d = (isClass ? Registry.classes : Registry.races).get(id);
        if (d == null) return;
        if (isClass) {
            Def r = Registry.races.get(PlayerState.race(sp));
            if (r != null && !r.allowedClasses.isEmpty() && !r.allowedClasses.contains(id)) return;
            PlayerState.setCls(sp, id);
        } else {
            PlayerState.setRace(sp, id);
            // a class that this race may not use is dropped
            Def c = Registry.classes.get(PlayerState.cls(sp));
            if (c != null && !d.allowedClasses.isEmpty() && !d.allowedClasses.contains(c.id)) {
                removePermanent(sp, c);
                PlayerState.setCls(sp, "");
            }
        }
        boolean fresh = !id.equals(have);
        if (fresh && !have.isEmpty()) removePermanent(sp, (isClass ? Registry.classes : Registry.races).get(have));
        applyAll(sp, fresh);
        if (fresh) {
            giveKit(sp, d);
            giveTimedEffects(sp, d);
            runCommands(sp, d);
        }
        Net.sendSync(sp);
        prompt(sp, true);
    }

    // ---------- attributes ----------
    public static void applyAll(ServerPlayer sp, boolean heal) {
        for (Attribute a : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance inst = sp.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers()))
                if (m.getName().startsWith(MOD_PREFIX)) inst.removeModifier(m);
        }
        add(sp, Registry.races.get(PlayerState.race(sp)), "race");
        add(sp, Registry.classes.get(PlayerState.cls(sp)), "class");
        ensurePermanent(sp, Registry.races.get(PlayerState.race(sp)));
        ensurePermanent(sp, Registry.classes.get(PlayerState.cls(sp)));
        if (heal) sp.setHealth(sp.getMaxHealth());
        else if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
    }

    private static void add(ServerPlayer sp, Def d, String source) {
        if (d == null) return;
        int n = 0;
        for (Def.Attr a : d.attributes) {
            n++;
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(a.attribute));
            if (attr == null) continue;
            AttributeInstance inst = sp.getAttribute(attr);
            if (inst == null) continue;
            AttributeModifier.Operation op = switch (a.operation) {
                case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
                case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
                default -> AttributeModifier.Operation.ADDITION;
            };
            UUID uuid = UUID.nameUUIDFromBytes((MOD_PREFIX + source + ":" + d.id + ":" + a.attribute + ":" + n).getBytes());
            inst.addTransientModifier(new AttributeModifier(uuid, MOD_PREFIX + source + ":" + d.id, a.amount, op));
        }
    }

    // ---------- effects ----------
    private static MobEffect effectOf(Def.Effect e) {
        return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(e.effect));
    }

    /** Effects with 0 seconds last as long as the class/race does; they are restored after death and relog. */
    private static void ensurePermanent(ServerPlayer sp, Def d) {
        if (d == null) return;
        for (Def.Effect e : d.effects) {
            if (e.seconds > 0) continue;
            MobEffect me = effectOf(e);
            if (me == null) continue;
            MobEffectInstance cur = sp.getEffect(me);
            if (cur == null || cur.getAmplifier() != e.amplifier || cur.getDuration() != MobEffectInstance.INFINITE_DURATION)
                sp.addEffect(new MobEffectInstance(me, MobEffectInstance.INFINITE_DURATION, e.amplifier, false, false, true));
        }
    }

    public static void removePermanent(ServerPlayer sp, Def d) {
        if (d == null) return;
        for (Def.Effect e : d.effects) {
            if (e.seconds > 0) continue;
            MobEffect me = effectOf(e);
            if (me == null) continue;
            MobEffectInstance cur = sp.getEffect(me);
            if (cur != null && cur.getDuration() == MobEffectInstance.INFINITE_DURATION) sp.removeEffect(me);
        }
    }

    private static void giveTimedEffects(ServerPlayer sp, Def d) {
        for (Def.Effect e : d.effects) {
            if (e.seconds <= 0) continue;
            MobEffect me = effectOf(e);
            if (me != null) sp.addEffect(new MobEffectInstance(me, e.seconds * 20, e.amplifier, false, true, true));
        }
    }

    // ---------- kit and commands ----------
    private static void giveKit(ServerPlayer sp, Def d) {
        for (Def.Kit k : d.kit) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(k.item));
            if (item == null) continue;
            ItemStack st = new ItemStack(item, k.count);
            if (!k.nbt.isBlank()) {
                try { st.setTag(TagParser.parseTag(k.nbt)); } catch (CommandSyntaxException ignored) {}
            }
            if (item instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                if (sp.getItemBySlot(slot).isEmpty()) {
                    sp.setItemSlot(slot, st);
                    continue;
                }
            }
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
    }

    private static void runCommands(ServerPlayer sp, Def d) {
        if (sp.getServer() == null) return;
        for (String cmd : d.commands) {
            String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            if (c.isBlank()) continue;
            sp.getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
        }
    }

    // ---------- editor saves (op only) ----------
    public static void saveDef(ServerPlayer sp, boolean isClass, boolean delete, String id, String json) {
        if (!sp.hasPermissions(2)) return;
        Def.Kind kind = isClass ? Def.Kind.CLASS : Def.Kind.RACE;
        try {
            if (delete) {
                Registry.deleteDef(kind, id);
            } else {
                Def d = Registry.fromWire(kind, json);
                if (d.id.isEmpty()) return;
                Registry.writeDef(d);
            }
        } catch (Exception e) {
            sp.sendSystemMessage(Component.literal("[Sovereign Classes] save failed: " + e));
            return;
        }
        Registry.loadDisk();
        reapplyAll(sp);
    }

    public static void saveSettings(ServerPlayer sp, String json) {
        if (!sp.hasPermissions(2)) return;
        try {
            Registry.writeSettings(Registry.settingsFromWire(json));
        } catch (Exception e) {
            sp.sendSystemMessage(Component.literal("[Sovereign Classes] save failed: " + e));
            return;
        }
        Registry.loadDisk();
        reapplyAll(sp);
    }

    private static void reapplyAll(ServerPlayer from) {
        if (from.getServer() == null) return;
        for (ServerPlayer p : from.getServer().getPlayerList().getPlayers()) applyAll(p, false);
        Net.sendSyncAll(from.getServer());
    }

    // ---------- commands ----------
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("sovclasses").requires(s -> s.hasPermission(2))
                .then(Commands.literal("editor").executes(c -> {
                    Net.open(c.getSource().getPlayerOrException(), Net.OPEN_EDITOR);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    Registry.loadDisk();
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) applyAll(p, false);
                    Net.sendSyncAll(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Sovereign Classes reloaded: " + Registry.classes.size()
                            + " classes, " + Registry.races.size() + " races"), false);
                    return 1;
                }))
                .then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
                        removePermanent(p, Registry.races.get(PlayerState.race(p)));
                        removePermanent(p, Registry.classes.get(PlayerState.cls(p)));
                        PlayerState.clear(p);
                        applyAll(p, false);
                        Net.sendSync(p);
                        prompt(p, false);
                        n++;
                    }
                    return n;
                }))));
    }
}
