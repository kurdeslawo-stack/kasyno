# CasinoCore

CasinoCore is a modular casino plugin foundation for **Minecraft Java / Paper 26.2**. The first game is Roulette; Blackjack is planned as the next game built on the same core.

This repository currently contains the plugin skeleton and production-ready Roulette visual assets. Full Roulette gameplay, GUI and animation are intentionally outside this stage.

## Requirements and build

- Java 25
- Maven 3.9+
- Paper 26.2

Build the plugin with:

```shell
./mvnw clean verify
```

On Windows:

```powershell
.\mvnw.cmd clean verify
```

The plugin JAR is written to `target/CasinoCore-0.1.0-SNAPSHOT.jar`.

## Plugin foundation

- `EconomyProvider` separates casino games from the server's future coin system.
- `LocalEconomyProvider` is an in-memory development provider. Its balances disappear after restart and it is not intended for production.
- Roulette accepts the configured stakes **1, 5 and 25 coins**.
- `/casino`, `/casino reload`, `/casino balance`, `/casino coins set <player> <amount>` and `/casino coins add <player> <amount>` provide initial administration and testing commands.

No Vault or external coin plugin integration is included yet.

## Roulette assets

Source assets are under `assets/roulette/`. The model is split into independently controllable **BASE**, **WHEEL** and **BALL** components so the plugin can animate them later. Their pivots, offsets, ball position and orbit radius are defined in `assets/roulette/integration.json`.

The ready Minecraft 26.2 resource pack is available at:

`dist/CasinoCore_Roulette_ResourcePack_26.2.zip`

Run `tools/build_roulette_assets.py` from the repository root to regenerate the asset output. The original `.bbmodel` sources are kept in `assets/roulette/blockbench/`.

## Roadmap

1. Connect the server's coin system through a production `EconomyProvider`.
2. Implement Roulette table placement, betting and animations.
3. Add Blackjack using the shared economy layer.
