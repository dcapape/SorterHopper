package com.example.sorterhopper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class SorterHopperListener implements Listener {

    private final SorterHopperPlugin plugin;
    private final Set<Location> processing = new HashSet<>();
    private BukkitTask itemCheckTask;
    // Tracking de items cerca de hoppers sorter para verificación proactiva
    private final Set<UUID> trackedItems = ConcurrentHashMap.newKeySet();
    // Tracking de items aceptados que deben ser atraídos hacia el hopper
    private final Map<UUID, Location> acceptedItems = new ConcurrentHashMap<>();

    public SorterHopperListener(SorterHopperPlugin plugin) {
        this.plugin = plugin;
    }

    public void startItemCheckTask() {
        // Verificar items cerca de tolvas cada tick (0.05 segundos) para máxima respuesta con múltiples items
        itemCheckTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkNearbyItems, 1L, 1L);
    }

    public void stopItemCheckTask() {
        if (itemCheckTask != null) {
            itemCheckTask.cancel();
            itemCheckTask = null;
        }
    }

    private void checkNearbyItems() {
        // ESTRATEGIA 2: Tracking continuo - verificar items tracked y todos los items cerca de hoppers
        // Este es el sistema proactivo que reemplaza la reactividad a eventos
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            // Limpiar items muertos del tracking
            trackedItems.removeIf(uuid -> {
                org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(uuid);
                return entity == null || !(entity instanceof org.bukkit.entity.Item) || entity.isDead();
            });
            
            // Limpiar items aceptados muertos
            acceptedItems.entrySet().removeIf(entry -> {
                org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(entry.getKey());
                return entity == null || !(entity instanceof org.bukkit.entity.Item) || entity.isDead();
            });
            
            // Atraer y forzar recogida de items aceptados hacia sus hoppers SORTER
            for (Map.Entry<UUID, Location> entry : acceptedItems.entrySet()) {
                org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(entry.getKey());
                if (entity instanceof org.bukkit.entity.Item itemEntity && !itemEntity.isDead()) {
                    Location hopperLoc = entry.getValue();
                    
                    // VERIFICAR que el hopper sigue siendo un sorterHopper con filtro
                    Block hopperBlock = hopperLoc.getBlock();
                    if (hopperBlock.getType() != Material.HOPPER || !plugin.isSorterHopper(hopperBlock)) {
                        // El hopper ya no es un sorterHopper, remover del tracking
                        acceptedItems.remove(entry.getKey());
                        continue;
                    }
                    
                    if (!(hopperBlock.getState() instanceof Hopper hopper)) {
                        acceptedItems.remove(entry.getKey());
                        continue;
                    }
                    
                    Inventory hopperInventory = hopper.getInventory();
                    if (!isSorterHopperInventory(hopperInventory) || !hasFilterItems(hopperInventory)) {
                        // El hopper ya no tiene filtro, remover del tracking
                        acceptedItems.remove(entry.getKey());
                        continue;
                    }
                    
                    // Verificar que el item sigue coincidiendo con el filtro
                    ItemStack itemStack = itemEntity.getItemStack();
                    if (!matchesFilter(hopperInventory, itemStack)) {
                        // El item ya no coincide, remover del tracking
                        acceptedItems.remove(entry.getKey());
                        continue;
                    }
                    
                    Location itemLoc = itemEntity.getLocation();
                    double distance = itemLoc.distance(hopperLoc);
                    
                    // Si está muy cerca (dentro del área de recogida del hopper), teleportarlo directamente
                    if (distance < 0.5) {
                        // Teleportar directamente al centro del hopper para forzar recogida
                        Location targetLoc = hopperLoc.clone();
                        targetLoc.setY(targetLoc.getY() - 0.1); // Justo encima del hopper
                        itemEntity.teleport(targetLoc);
                        itemEntity.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); // Detener movimiento
                    } else if (distance < 1.5) {
                        // Atraer hacia el hopper con fuerza más agresiva
                        org.bukkit.util.Vector pull = hopperLoc.toVector().subtract(itemLoc.toVector());
                        if (pull.length() > 0) {
                            double pullForce = 0.3; // Fuerza más agresiva
                            pull.normalize().multiply(pullForce);
                            org.bukkit.util.Vector currentVel = itemEntity.getVelocity();
                            itemEntity.setVelocity(currentVel.add(pull));
                        }
                    } else if (distance > 3.0) {
                        // Si se alejó demasiado, remover del tracking
                        acceptedItems.remove(entry.getKey());
                    }
                }
            }
            
            // Buscar items en el mundo
            for (org.bukkit.entity.Item itemEntity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                if (itemEntity.isDead()) {
                    trackedItems.remove(itemEntity.getUniqueId());
                    continue;
                }
                
                ItemStack itemStack = itemEntity.getItemStack();
                if (itemStack == null || itemStack.getType() == Material.AIR) {
                    continue;
                }

                Location itemLoc = itemEntity.getLocation();
                org.bukkit.util.Vector velocity = itemEntity.getVelocity();
                double speed = velocity.length();
                
                // ESTRATEGIA 3: Área de detección grande (4-5 bloques) para items rápidos
                double checkRadius = speed > 0.1 ? 5.0 : 4.0; // Radio grande: 5 bloques para rápidos, 4 para lentos
                
                Block centerBlock = itemLoc.getBlock();
                
                // Verificar bloques en un cubo muy grande para interceptar antes
                int range = speed > 0.1 ? 5 : 4; // Cubo 11x11x11 para items rápidos, 9x9x9 para lentos
                boolean processed = false;
                
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                            Block block = centerBlock.getRelative(x, y, z);
                            if (block.getType() == Material.HOPPER && plugin.isSorterHopper(block)) {
                                if (block.getState() instanceof Hopper hopper) {
                                    Inventory inventory = hopper.getInventory();
                                    if (isSorterHopperInventory(inventory)) {
                                        Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);
                                        double distance = itemLoc.distance(blockLoc);
                                        
                                        // Para items rápidos, verificar posición futura hasta 6 ticks adelante
                                        // Esto permite interceptar items ANTES de que lleguen
                                        if (speed > 0.1) {
                                            for (int ticks = 1; ticks <= 6; ticks++) {
                                                Location futureLoc = itemLoc.clone().add(velocity.clone().multiply(ticks));
                                                double futureDistance = futureLoc.distance(blockLoc);
                                                distance = Math.min(distance, futureDistance);
                                            }
                                        }
                                        
                                        if (distance < checkRadius) {
                                            // ESTRATEGIA 2: Agregar a tracking continuo
                                            trackedItems.add(itemEntity.getUniqueId());
                                            
                                            if (hasFilterItems(inventory)) {
                                                // ESTRATEGIA 4: Aplicar filtro ANTES de que llegue al hopper
                                                boolean matches = matchesFilter(inventory, itemStack);
                                                
                                                if (!matches) {
                                                    // No hacer nada, simplemente ignorar el item
                                                    processed = true;
                                                    break;
                                                } else {
                                                    // Marcar item como aceptado y atraerlo hacia el hopper
                                                    if (!acceptedItems.containsKey(itemEntity.getUniqueId())) {
                                                        acceptedItems.put(itemEntity.getUniqueId(), blockLoc);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (processed) break;
                    }
                    if (processed) break;
                }
            }
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (!isSorterHopperInventory(inventory)) {
            return;
        }

        Item itemEntity = event.getItem();
        if (itemEntity.isDead()) {
            return;
        }

        ItemStack itemStack = itemEntity.getItemStack();
        if (itemStack.getType() == Material.AIR) {
            return;
        }

        // Si el filtro está vacío (no hay items de filtro), permitir todo
        if (!hasFilterItems(inventory)) {
            return;
        }

        // Si el item no coincide con el filtro, cancelar el evento
        if (!matchesFilter(inventory, itemStack)) {
            event.setCancelled(true);
        } else {
            // Remover del tracking de items aceptados cuando es recogido
            acceptedItems.remove(itemEntity.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // ESTRATEGIA 1: Interceptar items cuando aparecen - punto de entrada más temprano
        // Esta es la estrategia clave: interceptar ANTES de que lleguen al hopper
        Item itemEntity = event.getEntity();
        Location itemLoc = itemEntity.getLocation();
        ItemStack itemStack = itemEntity.getItemStack();
        
        // ESTRATEGIA 3: Área de detección grande (4-5 bloques) para interceptar temprano
        double detectionRadius = 5.0; // Radio grande para interceptar antes de que lleguen
        
        // Verificar un área muy amplia alrededor del item spawnado (cubo 11x11x11)
        Block centerBlock = itemLoc.getBlock();
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block block = centerBlock.getRelative(x, y, z);
                    if (block.getType() == Material.HOPPER && plugin.isSorterHopper(block)) {
                        if (block.getState() instanceof Hopper hopper) {
                            Inventory inventory = hopper.getInventory();
                            if (isSorterHopperInventory(inventory) && hasFilterItems(inventory)) {
                                Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);
                                double distance = itemLoc.distance(blockLoc);
                                
                                // ESTRATEGIA 4: Aplicar filtro ANTES de que llegue al hopper
                                if (distance < detectionRadius) {
                                    // ESTRATEGIA 2: Marcar para tracking continuo
                                    trackedItems.add(itemEntity.getUniqueId());
                                    
                                    // Aplicar filtro inmediatamente
                                    if (!matchesFilter(inventory, itemStack)) {
                                        // No hacer nada, simplemente ignorar el item
                                        return; // Solo procesar una vez
                                    } else {
                                        // Marcar item como aceptado y atraerlo hacia el hopper
                                        if (!acceptedItems.containsKey(itemEntity.getUniqueId())) {
                                            acceptedItems.put(itemEntity.getUniqueId(), blockLoc);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

    private boolean hasFilterItems(Inventory hopperInventory) {
        ItemStack[] contents = hopperInventory.getContents();
        for (ItemStack content : contents) {
            if (content != null && content.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
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
            // Comparar por tipo y datos (metadatos, encantamientos, etc.)
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

