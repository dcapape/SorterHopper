# SorterHopper Plugin

Spigot plugin for Minecraft 1.21.10 that adds an intelligent filtering hopper with improved absorption system.

## 🎯 Main Features

- **Intelligent Filtering**: Only collects items that exactly match the filter items
- **High Efficiency**: Continuous tracking system and attraction of accepted items for maximum capture
- **Multiple Interception Points**: Detects and filters items from spawn to pickup
- **Works in All Scenarios**: 
  - Loose items (entities)
  - Items in flowing water
  - Items from chests and hoppers
  - Fast items on blue ice

## 📦 Installation

1. Download the JAR from [Releases](https://github.com/dcapape/SorterHopper/releases)
2. Place `SorterHopper.jar` in the `plugins/` folder of your Spigot server
3. Restart the server

## 🎮 Usage

### Crafting the Sorter Hopper

Place in the crafting table:
- 1x Hopper
- 1x Redstone

You will get a **Sorter Hopper**.

### Configuring the Filter

1. Place the Sorter Hopper where you need to filter items
2. Right-click to open its inventory
3. Place the items you want it to collect (one or more types)
4. The hopper will only collect items that exactly match the filter

### Behavior

- **Empty filter**: Collects all items (normal hopper behavior)
- **Filter with items**: Only collects matching items (type, metadata, enchantments, etc.)
- **Rejected items**: Are ignored and continue their natural course

## 🔧 Compilation

### Requirements

- Java 17 or higher
- Maven 3.9.6 (included in `tools/`)

### Compile

```powershell
cd devplugins
.\tools\apache-maven-3.9.6\bin\mvn.cmd clean package
```

The JAR will be generated in: `devplugins/target/sorterhopper-1.0.0-SNAPSHOT.jar`

## 🏗️ Technical Architecture

### Multi-Layer Filtering System

The plugin implements a multi-layer filtering system for maximum efficiency:

#### 1. Early Interception (`ItemSpawnEvent`)
- Detects items when they spawn in the world
- Detection radius: 5.0 blocks
- Verification area: 11x11x11 cube
- Marks items for continuous tracking if near a sorterHopper

#### 2. Continuous Tracking (`checkNearbyItems`)
- Runs every tick (0.05 seconds)
- Verifies all items near sorterHoppers
- Dynamic radius: 5.0 blocks for fast items, 4.0 for slow ones
- Future position prediction up to 6 ticks ahead for fast items
- Attracts accepted items towards the hopper

#### 3. Pickup Filtering (`InventoryPickupItemEvent`)
- Last line of defense before item enters the hopper
- Priority: `HIGHEST`
- Cancels event if item doesn't match the filter

#### 4. Attraction System

Accepted items are attracted towards the hopper:
- **< 0.5 blocks**: Direct teleportation to hopper
- **0.5-1.5 blocks**: Attraction with force 0.3
- **> 3.0 blocks**: Removed from tracking

### Main Classes

- **`SorterHopperPlugin`**: Main class
  - Registers the crafting recipe
  - Manages Sorter Hopper identification using `PersistentDataContainer`
  - Starts the continuous tracking system

- **`SorterHopperListener`**: Event handler
  - `onItemSpawn`: Intercepts items when they spawn
  - `onInventoryPickupItemEvent`: Filters items when being picked up
  - `onInventoryMoveItemEvent`: Filters items moving between inventories
  - `checkNearbyItems`: Continuous tracking and item attraction
  - `onBlockPlace/Break`: Handles placement and destruction

### Events Used

| Event | Priority | Purpose |
|-------|----------|---------|
| `ItemSpawnEvent` | HIGH | Early item interception |
| `InventoryPickupItemEvent` | HIGHEST | Final filtering before pickup |
| `InventoryMoveItemEvent` | NORMAL | Filtering transfers between inventories |
| `BlockPlaceEvent` | NORMAL | Mark blocks as sorterHopper |
| `BlockBreakEvent` | NORMAL | Restore special item |

## 📊 Performance

- **Continuous tracking**: Every tick (20 times per second)
- **Detection area**: Up to 5 blocks radius
- **Prediction**: Up to 6 ticks ahead for fast items
- **Efficiency**: >90% capture rate under normal conditions

## 🔍 Filtering System

Filtering uses `ItemStack.isSimilar()` which compares:
- Material type
- Metadata (name, lore, enchantments)
- Persistent data
- Amount (not compared)

## 📝 Technical Notes

- Uses `PersistentDataContainer` to mark blocks as Sorter Hoppers
- Continuous tracking runs every tick for maximum responsiveness
- Rejected items are simply ignored (not pushed)
- System optimized for fast items and multiple simultaneous items

## 🐛 Troubleshooting

### Hopper doesn't collect items

1. Verify the hopper is a Sorter Hopper (correctly crafted)
2. Verify the filter has configured items
3. Verify items match exactly (same type, metadata, etc.)

### Items pass by

- The attraction system should capture them automatically
- If it persists, verify the hopper has space in its inventory

## 📄 License

This project is private code. All rights reserved.

## 👨‍💻 Development

### Project Structure

```
devplugins/
├── src/main/java/com/example/sorterhopper/
│   ├── SorterHopperPlugin.java    # Main class
│   └── SorterHopperListener.java  # Event handler
├── src/main/resources/
│   └── plugin.yml                 # Plugin configuration
├── pom.xml                        # Maven configuration
└── tools/                         # Included Maven
```

### Contributing

This is a private project. For changes or improvements, contact the maintainer.
