package com.example.sorterhopper;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import java.util.HashSet;
import java.util.Set;

public class SorterHopperListener implements Listener {

    private final SorterHopperPlugin plugin;
    private final Set<Location> processing = new HashSet<>();

    public SorterHopperListener(SorterHopperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.isSorterHopper(item)) {
            plugin.markBlockAsSorterHopper(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.isSorterHopper(block)) {
            return;
        }

        event.setDropItems(false);

        if (block.getState() instanceof Hopper hopperState) {
            Inventory inventory = hopperState.getInventory();
            for (ItemStack content : inventory.getContents()) {
                if (content != null && content.getType() != Material.AIR) {
                    block.getWorld().dropItemNaturally(block.getLocation(), content);
                }
            }
            inventory.clear();
        }

        block.getWorld().dropItemNaturally(block.getLocation(), plugin.createSorterHopperItem());
        plugin.unmarkBlock(block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        Inventory source = event.getSource();

        if (isSorterHopperInventory(destination)) {
            if (!matchesFilter(destination, event.getItem())) {
                event.setCancelled(true);
            }
            return;
        }

        if (isSorterHopperInventory(source)) {
            InventoryHolder holder = source.getHolder();
            if (!(holder instanceof Hopper hopper)) {
                return;
            }

            Location location = hopper.getBlock().getLocation();
            if (processing.contains(location)) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            processing.add(location);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    attemptCustomTransfer(hopper.getInventory(), destination);
                } finally {
                    processing.remove(location);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (!isSorterHopperInventory(inventory)) {
            return;
        }

        Item itemEntity = event.getItem();
        if (itemEntity == null) {
            return;
        }

        ItemStack itemStack = itemEntity.getItemStack();
        if (!matchesFilter(inventory, itemStack)) {
            event.setCancelled(true);
        }
    }

    private boolean isSorterHopperInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof Hopper hopper)) {
            return false;
        }
        Block block = hopper.getBlock();
        return plugin.isSorterHopper(block);
    }

    private boolean matchesFilter(Inventory hopperInventory, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemStack[] contents = hopperInventory.getContents();
        for (ItemStack content : contents) {
            if (content == null || content.getType() == Material.AIR) {
                continue;
            }
            if (content.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    private void attemptCustomTransfer(Inventory hopperInventory, Inventory destination) {
        if (destination == null) {
            return;
        }

        int slot = findMovableSlot(hopperInventory);
        if (slot == -1) {
            return;
        }

        ItemStack stack = hopperInventory.getItem(slot);
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        ItemStack single = stack.clone();
        single.setAmount(1);

        var leftovers = destination.addItem(single);
        if (!leftovers.isEmpty()) {
            return;
        }

        int remaining = stack.getAmount() - 1;
        if (remaining <= 0) {
            hopperInventory.setItem(slot, null);
        } else {
            stack.setAmount(remaining);
            hopperInventory.setItem(slot, stack);
        }
    }

    private int findMovableSlot(Inventory hopperInventory) {
        ItemStack[] contents = hopperInventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int filterIndex = findFilterSlotIndex(hopperInventory, item);
            if (filterIndex != i && countTotal(hopperInventory, item) > 1) {
                return i;
            }
        }

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int filterIndex = findFilterSlotIndex(hopperInventory, item);
            if (filterIndex == i && item.getAmount() > 1 && countTotal(hopperInventory, item) > 1) {
                return i;
            }
        }

        return -1;
    }

    private int findFilterSlotIndex(Inventory hopperInventory, ItemStack reference) {
        ItemStack[] contents = hopperInventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.isSimilar(reference)) {
                return i;
            }
        }
        return -1;
    }

    private int countTotal(Inventory hopperInventory, ItemStack reference) {
        int total = 0;
        for (ItemStack content : hopperInventory.getContents()) {
            if (content == null || content.getType() == Material.AIR) {
                continue;
            }
            if (content.isSimilar(reference)) {
                total += content.getAmount();
            }
        }
        return total;
    }
}

