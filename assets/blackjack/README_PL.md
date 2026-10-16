# CasinoCore Blackjack cards

Deterministycznie generowana talia 52 kart oraz rewers dla Minecraft Java 26.3.
Karty mają jasny front, złotą ramkę, czytelną rangę i duży symbol koloru.
Rewers używa ciemnej zieleni, złotej ramki i monogramu CasinoCore.

Uruchom z katalogu repozytorium:

```powershell
py -3 tools/build_blackjack_assets.py
```

Generator tworzy źródłowe PNG i JSON-y w `assets/blackjack/generated`, podgląd
w `assets/blackjack/preview`, wspólną paczkę w `assets/resource_pack` oraz ZIP
`dist/CasinoCore_ResourcePack_26.3.zip`. Paczka zawiera jednocześnie Roulette
i Blackjack. Nie wymaga OptiFine.

Identyfikatory mają postać `casinocore:blackjack/cards/<ranga>_<kolor>`, np.
`casinocore:blackjack/cards/ace_spades` i `casinocore:blackjack/cards/10_hearts`.
Rewers: `casinocore:blackjack/cards/card_back`.

```mcfunction
/give @s minecraft:paper[minecraft:item_model="casinocore:blackjack/cards/ace_spades"]
/give @s minecraft:paper[minecraft:item_model="casinocore:blackjack/cards/card_back"]
```
