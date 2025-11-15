package com.example.sorterhopper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class SorterHopperPlugin extends JavaPlugin {

    private NamespacedKey sorterHopperKey;
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        this.sorterHopperKey = new NamespacedKey(this, "sorter_hopper");
        this.recipeKey = new NamespacedKey(this, "sorter_hopper_recipe");

        registerRecipe();
        SorterHopperListener listener = new SorterHopperListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);
        listener.startItemCheckTask();
    }

    @Override
    public void onDisable() {
        Bukkit.removeRecipe(recipeKey);
    }

    private void registerRecipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createSorterHopperItem());
        recipe.addIngredient(Material.HOPPER);
        recipe.addIngredient(Material.REDSTONE);
        Bukkit.addRecipe(recipe);
    }

    public ItemStack createSorterHopperItem() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Sorter Hopper");
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(sorterHopperKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSorterHopper(ItemStack item) {
        if (item == null || item.getType() != Material.HOPPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte value = data.get(sorterHopperKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public boolean isSorterHopper(Block block) {
        if (block == null || block.getType() != Material.HOPPER) {
            return false;
        }
        if (!(block.getState() instanceof TileState state)) {
            return false;
        }
        PersistentDataContainer data = state.getPersistentDataContainer();
        Byte value = data.get(sorterHopperKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public void markBlockAsSorterHopper(Block block) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        PersistentDataContainer data = state.getPersistentDataContainer();
        data.set(sorterHopperKey, PersistentDataType.BYTE, (byte) 1);
        state.update(true, false);
    }

    public void unmarkBlock(Block block) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        PersistentDataContainer data = state.getPersistentDataContainer();
        data.remove(sorterHopperKey);
        state.update(true, false);
    }
}
