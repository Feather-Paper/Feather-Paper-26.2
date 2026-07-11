# Contributing to Feather Spigot

Feather Spigot welcomes community contributions! Whether you are submitting optimizations, bug fixes, or documentation updates, please read through these guidelines to ensure a smooth contribution process.

---

## 1. Core Principles

- **Gameplay Integrity**: Feather Spigot prioritizes performance without compromising vanilla Minecraft mechanics. Do not submit changes that alter redstone, mob spawning, tracking ranges, or vanilla entity behavior unless explicitly gated behind an opt-in configuration option.
- **Licensing**: Feather Spigot is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. By submitting code, you agree to license your contributions under GPL-3.0.

---

## 2. Setting Up Your Environment

Feather Spigot uses Gradle and the Paperweight patch system.

### Prerequisites
- **Java JDK 21 / 25**
- **Git**

### Initializing the Project
Clone the repository and apply patches:
```bash
./gradlew applyAllPatches
```

This will set up `feather-api` and `feather-server` directories.

---

## 3. Working with Patches

Modifications are made inside `feather-server` and `feather-api`. Once you finish editing files:

1. Commit your changes inside `feather-server` or `feather-api`:
   ```bash
   git add .
   git commit -m "Brief description of optimization or fix"
   ```
2. Rebuild patches from the workspace root:
   ```bash
   ./gradlew rebuildPatches
   ```

---

## 4. Code Style & Comments

- Keep code clean, readable, and well-structured.
- When adding inline comments for modifications, use descriptive summaries explaining *why* the change was made rather than boilerplate tags. Keep comments simple and concise.

---

## 5. Submitting Pull Requests

1. Rebase your branch against the latest `master` branch.
2. Ensure your changes compile cleanly via `./gradlew createPaperclipJar`.
3. Open a Pull Request detailing the performance impact and verifying that vanilla gameplay is unchanged.
