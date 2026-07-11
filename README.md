# Feather Spigot

<div align="center">

**Feather Spigot** is a high-performance, modern Minecraft server software based on Leaf and Paper.
Designed to maximize server efficiency while strictly preserving vanilla gameplay mechanics.

</div>

---

## Overview

Feather Spigot merges cutting-edge performance optimizations with uncompromising gameplay accuracy. Unlike aggressive optimization forks that alter simulation distances, mob AI, or vanilla behavior to save CPU cycles, Feather optimizes data structures, collections, RNG, and threading while keeping standard Minecraft mechanics intact.

### Key Highlights
- **GPL-3.0 Licensed**: Open-source, transparent, and built for community collaboration.
- **Vanilla Gameplay Preservation**: No compromised redstone, mob spawning, or tracking ranges.
- **High-Speed RNG & Collections**: Uses accelerated random generation and streamlined collections (`FeatherFastCollections`) for minimal overhead.
- **Paper & Spigot Compatible**: Full support for all standard Bukkit, Spigot, and Paper plugins.

---

## Getting Started

### Requirements
- **Java**: Java 21 or Java 25 (Recommended)
- **OS**: Linux, Windows, or macOS (64-bit)

### Running the Server
1. Download or build `server.jar`.
2. Launch with standard flags:
   ```bash
   java -Xms4G -Xmx4G -XX:+UseG1GC -jar server.jar --nogui
   ```
3. Accept the EULA in `eula.txt` and start your server.

---

## Configuration

Feather provides a dedicated configuration file located in `config/feather.yml` upon first boot.

### Key Configuration Modules
- **Performance & Randomness**: Toggle ultra-fast PRNG sources (`FasterRandomSource`).
- **Profile Cache**: High-efficiency profile resolution (`FeatherProfileCache`).
- **Collection Optimizations**: Optimized map and set lookups (`FeatherFastCollections`).

---

## Building from Source

To build Feather Spigot locally:

```bash
./gradlew applyAllPatches
./gradlew createPaperclipJar
```

The compiled standalone JAR will be output to `feather-server/build/libs/` or directly as `server.jar`.

---

## License

Feather Spigot is distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE.md](LICENSE.md) for full license terms.
