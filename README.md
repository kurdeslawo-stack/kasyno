# CasinoCore

CasinoCore is a modular casino plugin for **Minecraft Java / Paper 26.3**. It currently provides Roulette and classic Blackjack through the same economy layer.

Roulette uses a 54-slot inventory GUI, a server-side game engine and persistent `minecraft:interaction` hitboxes. The physical table model remains independent and is not spawned or animated by the plugin yet.

## Requirements and build

- Java 25
- Maven 3.9+
- Paper 26.3

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
- The GUI shows per-player totals and the five newest settled rounds. In Vault mode these are rebuilt from the durable round journal after restart; local development mode remains in memory.
- Blackjack uses a standard 52-card deck and supports hit, stand and double down. The dealer stands on soft 17 by default; split is intentionally outside the first Blackjack release.
- Blackjack pays a normal win at 1:1, refunds a push and pays a natural blackjack at 3:2. Coin balances are whole numbers, so a fractional blackjack profit is rounded down (for example, a stake of 1 returns 2 coins in total).
- Closing Blackjack after a wager has been funded counts as stand: the dealer finishes the hand and the round is settled exactly once. Closing before the deal starts creates no transaction or history entry.
- An explicitly rejected Blackjack payout freezes the hand in `SETTLING` and can safely retry. An unknown transaction outcome blocks the account's casino rounds until an administrator reconciles it against the economy provider's records.
- Blackjack statistics and the five newest settled hands survive restart in Vault mode through the round journal. Local development mode remains ephemeral.

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

- `/casino blackjack open` — open the Blackjack GUI

`/casino reload` and local coin administration require `casinocore.admin`. Roulette command access is split into `casinocore.roulette.open` and `casinocore.roulette.manage`. Blackjack access uses `casinocore.blackjack.open`; `casinocore.admin` grants all three permissions.

## Durable accounting and recovery

Production Vault mode writes round snapshots and transaction intents to
`plugins/CasinoCore/round-journal/` before calling the economy. Each round has a
UUID and stores its original rules, cards/deck or Roulette result. Files use
atomic replacement and forced writes; a process lock prevents two CasinoCore
instances from sharing the same journal. A corrupt/unavailable journal or a
different Vault provider identity blocks accounting instead of starting fresh.

At startup, confirmed funded rounds are completed using their saved state.
Blackjack recovery treats the last persisted hand as stand, including a funded
double down. Confirmed payouts are never sent again. Completed records rebuild
history and statistics. Closing normally still settles active rounds.

Vault cannot atomically commit a casino record and an external economy transfer.
If the provider throws or returns no response after a transaction is called, its
outcome is **unknown**, even if balances appear unchanged. Such transactions are
not automatically retried. Check the external economy's audit records first.

Administrator commands (permission `casinocore.admin`):

```text
/casino transactions list [page]
/casino transactions retry <round-id>
/casino transactions resolve <round-id> <stake|double|payout> <applied|not-applied> <evidence>
```

`retry` accepts only explicitly rejected payouts. `resolve` records the named
administrator and evidence, then resumes the saved round. `applied` confirms an
existing transfer without repeating it; `not-applied` permits the original
payout to be attempted, or cancels an uncharged starting stake. Never choose a
resolution by guessing. Reconciliation does not create a replacement round.

Back up the journal together with the external economy database. Do not delete
round files, edit them while running, or roll back one side independently.
Deploy by stopping the previous plugin normally before replacing its JAR.
Existing pre-journal history cannot be reconstructed retroactively.

LocalEconomyProvider remains development-only: its balances, journal and history
are intentionally ephemeral, so crash recovery is enabled for production Vault
mode. The same unknown-outcome protection still applies within a local session.

Run regression tests with Maven and the independent crash/transaction probes
with `py -3 tools/run_blackjack_failure_probes.py`. See
`docs/TEST_REPORT_26.3.md` for the verification scope.

## CasinoCore resource pack

Source assets are under `assets/roulette/`. The model is split into independently controllable **BASE**, **WHEEL** and **BALL** components so the plugin can animate them later. Their pivots, offsets, ball position and orbit radius are defined in `assets/roulette/integration.json`.

The Blackjack deck is generated from `tools/build_blackjack_assets.py`. It contains 52 card fronts and one CasinoCore card back under `assets/resource_pack/assets/casinocore/.../blackjack/cards/`. The GUI maps each `BlackjackCard` directly to its `casinocore:blackjack/cards/<rank>_<suit>` item model. Card names remain visible when a player has not loaded the pack.

The ready Minecraft 26.3 resource pack is available at:

`dist/CasinoCore_ResourcePack_26.3.zip`

The pack targets format **97.1**, as documented in the [Minecraft 26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3). The plugin builds against the pinned Paper API `26.3.build.200-stable`.

Run `tools/build_roulette_assets.py` from the repository root to regenerate Roulette and then assemble the unified pack, or run `tools/build_blackjack_assets.py` to regenerate the cards and assemble the current Roulette sources. The original `.bbmodel` sources are kept in `assets/roulette/blockbench/`.

## Roadmap

1. Connect and validate the production Vault economy on the target server.
2. Bind the separate 3D table renderer and wheel animation to persisted interaction hitboxes.
3. Add journal compaction/database storage for large installations.
