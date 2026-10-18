package com.sovereign.classes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** What a player picked, stored in the persistent part of the player's data (survives death and restarts). */
public final class PlayerState {
    private PlayerState() {}

    private static CompoundTag tag(Player p, boolean create) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            if (!create) return new CompoundTag();
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static String cls(Player p) { return tag(p, false).getString("sovereign_class"); }
    public static String race(Player p) { return tag(p, false).getString("sovereign_race"); }

    public static void setCls(Player p, String id) { tag(p, true).putString("sovereign_class", id); }
    public static void setRace(Player p, String id) { tag(p, true).putString("sovereign_race", id); }

    public static void clear(Player p) {
        CompoundTag t = tag(p, true);
        t.remove("sovereign_class");
        t.remove("sovereign_race");
    }
}
