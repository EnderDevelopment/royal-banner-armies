package com.enderdevelopment.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private NamespacedKey bannerKey;
    private NamespacedKey factionKey;
    private NamespacedKey ownerKey;
    private BukkitTask aiTask;
    private final List<Unit> units = new ArrayList<>();
    private static final String[][] DATA = {
        {"HUMANS", "Swordsman", "Spearman", "Archer", "Light Cavalry", "Heavy Cavalry"},
        {"ELVES", "Swordsman", "Spearman", "Archer", "Ranger", "Cavalry"},
        {"DWARVES", "Axeman", "Hammer Guard", "Crossbowman", "Shield Guard", "Boar Rider"},
        {"ORCS", "Brute", "Warrior", "Spearman", "Archer", "Wolf Rider"}
    };
    private static final Material[] WEAPONS = {
        Material.IRON_SWORD, Material.IRON_SWORD, Material.BOW, Material.IRON_SWORD, Material.IRON_SWORD,
        Material.IRON_SWORD, Material.IRON_SWORD, Material.BOW, Material.IRON_SWORD, Material.IRON_SWORD,
        Material.IRON_AXE, Material.IRON_AXE, Material.CROSSBOW, Material.SHIELD, Material.IRON_AXE,
        Material.IRON_AXE, Material.IRON_SWORD, Material.IRON_SWORD, Material.BOW, Material.IRON_SWORD
    };
    private static final double[] HEALTH = {24,26,18,28,36,22,24,18,20,30,30,34,20,36,32,38,30,28,18,30};
    private static final double[] DAMAGE = {6,5,4,7,9,6,5,4,6,8,8,7,5,6,8,10,7,6,4,8};

    @Override
    public void onEnable() {
        bannerKey = new NamespacedKey(this, "royal_banner");
        factionKey = new NamespacedKey(this, "army_faction");
        ownerKey = new NamespacedKey(this, "army_owner");
        Bukkit.getPluginManager().registerEvents(this, this);
        aiTask = Bukkit.getScheduler().runTaskTimer(this, this::tickArmy, 20L, 10L);
    }

    @Override
    public void onDisable() {
        if (aiTask != null) aiTask.cancel();
        units.removeIf(unit -> { Entity entity = Bukkit.getEntity(unit.id()); if (entity != null) entity.remove(); return true; });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command is for players."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("banner")) {
            player.getInventory().addItem(createBanner());
            player.sendMessage(Component.text("Royal Banner issued.", NamedTextColor.GOLD));
        } else openMenu(player);
        return true;
    }

    private ItemStack createBanner() {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Royal Banner", NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(bannerKey, PersistentDataType.STRING, "recruit");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onBannerUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !"recruit".equals(item.getItemMeta().getPersistentDataContainer().get(bannerKey, PersistentDataType.STRING))) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    private void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(), 27, Component.text("Royal Army Recruitment", NamedTextColor.GOLD));
        for (int f = 0; f < DATA.length; f++) {
            for (int u = 1; u <= 5; u++) {
                int index = f * 5 + u - 1;
                ItemStack entry = new ItemStack(icon(index));
                ItemMeta meta = entry.getItemMeta();
                meta.displayName(Component.text(DATA[f][u], factionColor(f)));
                meta.lore(List.of(Component.text(DATA[f][0] + " | Health " + (int) HEALTH[index] + " | Damage " + (int) DAMAGE[index], NamedTextColor.GRAY), Component.text("Tap to recruit", NamedTextColor.YELLOW)));
                entry.setItemMeta(meta);
                inventory.setItem(f * 5 + u - 1, entry);
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 20) return;
        recruit(player, slot);
    }

    private void recruit(Player owner, int index) {
        int faction = index / 5;
        Location spawn = owner.getLocation().add(owner.getLocation().getDirection().multiply(2));
        EntityType type = index == 2 || index == 7 || index == 12 || index == 18 ? EntityType.SKELETON : EntityType.VINDICATOR;
        Entity entity = owner.getWorld().spawnEntity(spawn, type);
        if (!(entity instanceof Mob mob)) { entity.remove(); return; }
        mob.customName(Component.text(DATA[faction][index % 5] + " - " + DATA[faction][0], factionColor(faction)));
        mob.setCustomNameVisible(true);
        mob.setCanPickupItems(false);
        mob.setRemoveWhenFarAway(false);
        if (mob.getAttribute(Attribute.MAX_HEALTH) != null) mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(HEALTH[index]);
        mob.setHealth(HEALTH[index]);
        if (mob.getAttribute(Attribute.ATTACK_DAMAGE) != null) mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(DAMAGE[index]);
        mob.getPersistentDataContainer().set(factionKey, PersistentDataType.INTEGER, faction);
        mob.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        if (mob.getEquipment() != null) {
            mob.getEquipment().setItemInMainHand(new ItemStack(WEAPONS[index]));
            mob.getEquipment().setItemInMainHandDropChance(0);
            mob.getEquipment().setHelmet(new ItemStack(new Material[]{Material.IRON_HELMET,Material.LEATHER_HELMET,Material.CHAINMAIL_HELMET,Material.IRON_HELMET}[faction]));
            mob.getEquipment().setChestplate(new ItemStack(new Material[]{Material.IRON_CHESTPLATE,Material.LEATHER_CHESTPLATE,Material.IRON_CHESTPLATE,Material.CHAINMAIL_CHESTPLATE}[faction]));
            mob.getEquipment().setArmorDropChance(0,0,0,0);
        }
        units.add(new Unit(mob.getUniqueId(), faction, owner.getUniqueId(), index));
        owner.sendMessage(Component.text(DATA[faction][index % 5] + " recruited.", NamedTextColor.GREEN));
        owner.closeInventory();
    }

    private void tickArmy() {
        units.removeIf(unit -> {
            Entity entity = Bukkit.getEntity(unit.id());
            if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) return true;
            Player owner = Bukkit.getPlayer(unit.owner());
            if (owner == null || !owner.isOnline() || !owner.getWorld().equals(mob.getWorld())) { mob.setTarget(null); return false; }
            LivingEntity enemy = null;
            double nearest = 12.0;
            for (Entity nearby : mob.getNearbyEntities(10, 5, 10)) {
                if (!(nearby instanceof LivingEntity living) || living.equals(mob) || living.equals(owner) || living.isDead()) continue;
                Integer otherFaction = living.getPersistentDataContainer().get(factionKey, PersistentDataType.INTEGER);
                if (otherFaction == null || otherFaction == unit.faction()) {
                    if (living instanceof Player player && player.equals(owner)) continue;
                    if (!(living instanceof Player) && otherFaction == null) continue;
                }
                if (living instanceof Player player && player.equals(owner)) continue;
                double distance = living.getLocation().distanceSquared(mob.getLocation());
                if (distance < nearest * nearest) { nearest = Math.sqrt(distance); enemy = living; }
            }
            if (enemy != null) mob.setTarget(enemy);
            else if (mob.getTarget() != null && (!mob.getTarget().isValid() || !mob.getTarget().getWorld().equals(mob.getWorld()))) mob.setTarget(null);
            if (mob.getTarget() == null && mob.getLocation().distanceSquared(owner.getLocation()) > 25) mob.getPathfinder().moveTo(owner.getLocation(), 1.15);
            return false;
        });
    }

    @EventHandler
    public void onAllyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker) || !(event.getEntity() instanceof LivingEntity victim)) return;
        Integer a = attacker.getPersistentDataContainer().get(factionKey, PersistentDataType.INTEGER);
        Integer v = victim.getPersistentDataContainer().get(factionKey, PersistentDataType.INTEGER);
        if (a != null && a.equals(v)) event.setCancelled(true);
    }

    private Material icon(int index) { return WEAPONS[index]; }
    private NamedTextColor factionColor(int f) { return new NamedTextColor[]{NamedTextColor.GOLD,NamedTextColor.GREEN,NamedTextColor.GRAY,NamedTextColor.RED}[f]; }
    private record Unit(UUID id, int faction, UUID owner, int type) {}
    private static final class MenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
