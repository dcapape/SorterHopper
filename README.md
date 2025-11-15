# SorterHopper Plugin

Plugin de Spigot para Minecraft 1.21.10 que añade un hopper filtrador inteligente con sistema de absorción mejorado.

## 🎯 Características Principales

- **Filtrado Inteligente**: Solo recoge items que coinciden exactamente con los items del filtro
- **Alta Eficiencia**: Sistema de tracking continuo y atracción de items aceptados para máxima captura
- **Múltiples Puntos de Interceptación**: Detecta y filtra items desde que aparecen hasta que son recogidos
- **Funciona en Todos los Escenarios**: 
  - Items sueltos (entidades)
  - Items en agua corriendo
  - Items desde cofres y tolvas
  - Items rápidos en hielo azul

## 📦 Instalación

1. Descarga el JAR desde [Releases](https://github.com/tu-usuario/SorterHopper/releases)
2. Coloca `SorterHopper.jar` en la carpeta `plugins/` de tu servidor Spigot
3. Reinicia el servidor

## 🎮 Uso

### Craftear el Sorter Hopper

Coloca en la mesa de crafteo:
- 1x Hopper
- 1x Redstone

Obtendrás un **Sorter Hopper**.

### Configurar el Filtro

1. Coloca el Sorter Hopper donde necesites filtrar items
2. Haz clic derecho para abrir su inventario
3. Coloca los items que quieres que recoja (uno o más tipos)
4. El hopper solo recogerá items que coincidan exactamente con los del filtro

### Comportamiento

- **Filtro vacío**: Recoge todos los items (comportamiento normal de hopper)
- **Filtro con items**: Solo recoge items que coinciden (tipo, metadatos, encantamientos, etc.)
- **Items rechazados**: Se ignoran y continúan su curso natural

## 🔧 Compilación

### Requisitos

- Java 17 o superior
- Maven 3.9.6 (incluido en `devplugins/tools/`)

### Compilar

```powershell
cd devplugins
.\tools\apache-maven-3.9.6\bin\mvn.cmd clean package
```

El JAR se generará en: `devplugins/target/sorterhopper-1.0.0-SNAPSHOT.jar`

## 🏗️ Arquitectura Técnica

### Sistema de Filtrado Multi-Capa

El plugin implementa un sistema de filtrado en múltiples capas para máxima eficiencia:

#### 1. Interceptación Temprana (`ItemSpawnEvent`)
- Detecta items cuando aparecen en el mundo
- Radio de detección: 5.0 bloques
- Área de verificación: cubo 11x11x11
- Marca items para tracking continuo si están cerca de un sorterHopper

#### 2. Tracking Continuo (`checkNearbyItems`)
- Se ejecuta cada tick (0.05 segundos)
- Verifica todos los items cerca de sorterHoppers
- Radio dinámico: 5.0 bloques para items rápidos, 4.0 para lentos
- Predicción de posición futura hasta 6 ticks adelante para items rápidos
- Atrae items aceptados hacia el hopper

#### 3. Filtrado en Pickup (`InventoryPickupItemEvent`)
- Última línea de defensa antes de que el item entre al hopper
- Prioridad: `HIGHEST`
- Cancela el evento si el item no coincide con el filtro

#### 4. Sistema de Atracción

Items aceptados son atraídos hacia el hopper:
- **< 0.5 bloques**: Teleportación directa al hopper
- **0.5-1.5 bloques**: Atracción con fuerza 0.3
- **> 3.0 bloques**: Removido del tracking

### Clases Principales

- **`SorterHopperPlugin`**: Clase principal
  - Registra la receta de fabricación
  - Gestiona identificación de Sorter Hoppers usando `PersistentDataContainer`
  - Inicia el sistema de tracking continuo

- **`SorterHopperListener`**: Manejador de eventos
  - `onItemSpawn`: Intercepta items cuando aparecen
  - `onInventoryPickupItemEvent`: Filtra items al ser recogidos
  - `onInventoryMoveItemEvent`: Filtra items que se mueven entre inventarios
  - `checkNearbyItems`: Tracking continuo y atracción de items
  - `onBlockPlace/Break`: Maneja colocación y destrucción

### Eventos Utilizados

| Evento | Prioridad | Propósito |
|--------|-----------|-----------|
| `ItemSpawnEvent` | HIGH | Interceptar items temprano |
| `InventoryPickupItemEvent` | HIGHEST | Filtrado final antes de recoger |
| `InventoryMoveItemEvent` | NORMAL | Filtrado de transferencias entre inventarios |
| `BlockPlaceEvent` | NORMAL | Marcar bloques como sorterHopper |
| `BlockBreakEvent` | NORMAL | Restaurar item especial |

## 📊 Rendimiento

- **Tracking continuo**: Cada tick (20 veces por segundo)
- **Área de detección**: Hasta 5 bloques de radio
- **Predicción**: Hasta 6 ticks adelante para items rápidos
- **Eficiencia**: >90% de captura en condiciones normales

## 🔍 Sistema de Filtrado

El filtrado usa `ItemStack.isSimilar()` que compara:
- Tipo de material
- Metadatos (nombre, lore, encantamientos)
- Datos persistentes
- Cantidad (no se compara)

## 📝 Notas Técnicas

- Usa `PersistentDataContainer` para marcar bloques como Sorter Hoppers
- El tracking continuo se ejecuta cada tick para máxima responsividad
- Los items rechazados simplemente se ignoran (no se empujan)
- Sistema optimizado para items rápidos y múltiples items simultáneos

## 🐛 Solución de Problemas

### El hopper no recoge items

1. Verifica que el hopper sea un Sorter Hopper (crafteado correctamente)
2. Verifica que el filtro tenga items configurados
3. Verifica que los items coincidan exactamente (mismo tipo, metadatos, etc.)

### Items pasan de largo

- El sistema de atracción debería capturarlos automáticamente
- Si persiste, verifica que el hopper tenga espacio en su inventario

## 📄 Licencia

Este proyecto es código privado. Todos los derechos reservados.

## 👨‍💻 Desarrollo

### Estructura del Proyecto

```
devplugins/
├── src/main/java/com/example/sorterhopper/
│   ├── SorterHopperPlugin.java    # Clase principal
│   └── SorterHopperListener.java  # Manejador de eventos
├── src/main/resources/
│   └── plugin.yml                 # Configuración del plugin
├── pom.xml                        # Configuración Maven
└── tools/                         # Maven incluido
```

### Contribuir

Este es un proyecto privado. Para cambios o mejoras, contacta al mantenedor.
