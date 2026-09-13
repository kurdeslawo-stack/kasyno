# CasinoCore

Minecraft casino plugin project for Paper 26.2.

## Status

Initial repository scaffold with the Roulette asset pack. The first game module will be Roulette, followed by Blackjack.

## Planned architecture

- `CasinoCore` plugin core
- pluggable `EconomyProvider`
- local test economy for development
- Roulette module with 1 / 5 / 25 coin stakes
- separate roulette table / wheel / ball assets for animation
- later Blackjack module using the same economy layer

## Assets

Roulette source assets live under `assets/roulette/`. The ready-to-use Minecraft 26.2 resource pack is kept in `dist/`.

## Development

The Java plugin skeleton is intentionally minimal so Codex can build the gameplay layer on top of a clean structure.
