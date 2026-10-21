package com.sovereign.classes;

import com.sovereign.classes.data.Def;
import com.sovereign.classes.data.Registry;
import com.sovereign.classes.data.Weapons;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Weapon proficiency, damage rules and class-locked items. All of it is server side. */
@Mod.EventBusSubscriber(modid = SovereignClasses.MODID)
public final class Combat {
    private Combat() {}

    // ---------- helpers ----------
    private static String itemId(ItemStack st) {
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        return k == null ? "" : k.toString();
    }

    private static boolean itemTag(ItemStack st, String t) {
        try { return st.is(TagKey.create(Registries.ITEM, new ResourceLocation(t))); } catch (Exception e) { return false; }
    }

    private static boolean entityTag(Entity e, String t) {
        try { return e.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(t))); } catch (Exception ex) { return false; }
    }

    private static Def[] defs(Player p) {
        return new Def[]{Registry.races.get(PlayerState.race(p)), Registry.classes.get(PlayerState.cls(p))};
    }

    private static String typePath(DamageSource s) {
        return s.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("");
    }

    private static String typeFull(DamageSource s) {
        return s.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    private static boolean isMagic(DamageSource s) {
        String p = typePath(s);
        return p.contains("magic") || p.contains("spell");
    }

    // ---------- groups and filters ----------
    public static boolean inGroup(Entity e, String group) {
        if (e == null) return false;
        switch (group) {
            case "undead": if (e instanceof LivingEntity l && l.getMobType() == MobType.UNDEAD) return true; break;
            case "boss": if (e.getType().is(Tags.EntityTypes.BOSSES)) return true; break;
            case "player": return e instanceof Player;
            default: break;
        }
        Weapons.Type g = Registry.weapons.group(group);
        if (g == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && Weapons.matchesAny(g.match, k.toString(), false, t -> entityTag(e, t));
    }

    public static boolean filterMatches(String filter, DamageSource src, Entity other) {
        String f = filter.trim().toLowerCase(Locale.ROOT);
        switch (f) {
            case "all": return true;
            case "melee": return src.getDirectEntity() != null && src.getDirectEntity() == src.getEntity() && !isMagic(src);
            case "projectile": return src.is(DamageTypeTags.IS_PROJECTILE);
            case "explosion": return src.is(DamageTypeTags.IS_EXPLOSION);
            case "aoe": return src.is(DamageTypeTags.IS_EXPLOSION) || typePath(src).equals("dragon_breath") || typePath(src).equals("lightning_bolt");
            case "magic": return isMagic(src);
            case "fire": return src.is(DamageTypeTags.IS_FIRE);
            case "fall": return src.is(DamageTypeTags.IS_FALL);
            default: break;
        }
        if (f.startsWith("spell:")) return typePath(src).equals(f.substring(6) + "_magic");
        if (f.startsWith("type:")) return typeFull(src).equals(f.substring(5));
        if (f.startsWith("vs:")) return inGroup(other, f.substring(3));
        return false;
    }

    // ---------- factors ----------
    private static double weaponPercent(Player p, ItemStack held) {
        boolean empty = held.isEmpty();
        Weapons.Type t = Registry.weapons.typeOf(itemId(held), empty, tg -> itemTag(held, tg));
        double sum = 0;
        for (Def d : defs(p)) {
            if (d == null) continue;
            double v = d.weaponOther;
            if (t != null) for (Def.WeaponRow r : d.weapons) if (r.type.equals(t.id)) { v = r.percent; break; }
            sum += v;
        }
        return sum;
    }

    private static double rulePercent(Player p, String when, DamageSource src, Entity other) {
        double sum = 0;
        for (Def d : defs(p)) {
            if (d == null) continue;
            for (Def.Rule r : d.rules) if (r.when.equals(when) && filterMatches(r.filter, src, other)) sum += r.percent;
        }
        return sum;
    }

    /** Damage multiplier for something this player deals. */
    public static double dealt(Player p, LivingEntity target, DamageSource src) {
        double w = typePath(src).equals("thorns") ? 0 : weaponPercent(p, p.getMainHandItem());
        double r = rulePercent(p, "dealt", src, target);
        return Math.max(0, 1 + w / 100.0) * Math.max(0, 1 + r / 100.0);
    }

    /** Damage multiplier for something this player takes. */
    public static double taken(Player p, DamageSource src) {
        return Math.max(0, 1 + rulePercent(p, "taken", src, src.getEntity()) / 100.0);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        DamageSource src = e.getSource();
        if (src.getEntity() instanceof Player a && !a.level().isClientSide) {
            if (!allowed(a, a.getMainHandItem())) {
                deny(a, a.getMainHandItem());
                e.setCanceled(true);
                return;
            }
            e.setAmount((float) (e.getAmount() * dealt(a, e.getEntity(), src)));
        }
        if (e.getEntity() instanceof Player v && !v.level().isClientSide)
            e.setAmount((float) (e.getAmount() * taken(v, src)));
    }

    // ---------- locks ----------
    public static boolean allowed(Player p, ItemStack st) {
        if (st.isEmpty() || p.isCreative()) return true;
        List<String> only = Registry.weapons.lockedTo(itemId(st), t -> itemTag(st, t));
        return only == null || only.contains(PlayerState.cls(p));
    }

    private static void deny(Player p, ItemStack st) {
        List<String> only = Registry.weapons.lockedTo(itemId(st), t -> itemTag(st, t));
        StringBuilder sb = new StringBuilder();
        if (only != null) for (String c : only) {
            Def d = Registry.classes.get(c);
            if (sb.length() > 0) sb.append(", ");
            sb.append(d == null ? c : d.displayName());
        }
        p.displayClientMessage(Component.literal("Only " + (sb.length() == 0 ? "another class" : sb) + " can use this."), true);
    }

    private static void check(Player p, ItemStack st, net.minecraftforge.eventbus.api.Event e) {
        if (p.level().isClientSide || allowed(p, st)) return;
        deny(p, st);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) { check(e.getEntity(), e.getEntity().getMainHandItem(), e); }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) { check(e.getEntity(), e.getItemStack(), e); }

    @SubscribeEvent
    public static void onMine(PlayerInteractEvent.LeftClickBlock e) { check(e.getEntity(), e.getItemStack(), e); }

    @SubscribeEvent
    public static void onStartUse(LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof Player p) check(p, e.getItem(), e);
    }
}
