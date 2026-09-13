# CasinoCore

CasinoCore is a modular casino plugin for **Minecraft Java / Paper 26.2**. The first implemented game is Roulette; Blackjack is planned as the next game built on the same core.

Roulette uses a 54-slot inventory GUI, a server-side game engine and persistent `minecraft:interaction` hitboxes. The physical table model remains independent and is not spawned or animated by the plugin yet.

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

The plugin JAR is written to `target/CasinoCore-1.0.0.jar`.

## Plugin foundation

- `EconomyProvider` separates casino games from the server economy.
- `LocalEconomyProvider` is an in-memory development provider. Its balances disappear after restart and it is not intended for production.
- `VaultEconomyProvider` delegates balances and transactions to the Economy service registered in Vault. Vault API is a provided dependency and is not bundled in the plugin JAR.
- Roulette uses numbers **0-12**, configured stakes **1, 5 and 25 coins**, outside bets with total return x2 and exact-number bets with total return x12.
- The result is drawn once before the slowing GUI animation starts. Closing the inventory or leaving the server settles an already-funded round exactly once.
- When a player closes Roulette during a spin, a successfully settled result reopens on the next server tick only if the player is still online and has not opened another container.
- The GUI shows per-player totals and the five newest settled rounds. This data is kept in memory and resets when the plugin or server restarts.

The release configuration defaults to `economy.provider: vault`. Vault mode disables CasinoCore with a clear console error when Vault or its Economy service is unavailable; it never falls back to the local provider. Set the provider to `local` only on a development server.

## Commands

- `/casino` — plugin status and configured stakes
- `/casino balance` — own coin balance
- `/casino reload` — reload stakes, payouts and hitbox dimensions
- `/casino coins set <player> <amount>` — set development balance in local mode
- `/casino coins add <player> <amount>` — add development coins in local mode
- `/casino roulette open` — open the Roulette GUI for development
- `/casino roulette create` — create and persist an invisible Roulette interaction hitbox
- `/casino roulette remove` — remove the nearest Roulette hitbox

`/casino reload` and local coin administration require `casinocore.admin`. Roulette command access is split into `casinocore.roulette.open` and `casinocore.roulette.manage`; `casinocore.admin` grants both.

## Roulette assets

Source assets are under `assets/roulette/`. The model is split into independently controllable **BASE**, **WHEEL** and **BALL** components so the plugin can animate them later. Their pivots, offsets, ball position and orbit radius are defined in `assets/roulette/integration.json`.

The ready Minecraft 26.2 resource pack is available at:

`dist/CasinoCore_Roulette_ResourcePack_26.2.zip`

Run `tools/build_roulette_assets.py` from the repository root to regenerate the asset output. The original `.bbmodel` sources are kept in `assets/roulette/blockbench/`.

## Roadmap

1. Connect and validate the production Vault economy on the target server.
2. Bind the separate 3D table renderer and wheel animation to persisted interaction hitboxes.
3. Add Blackjack using the shared economy layer.
