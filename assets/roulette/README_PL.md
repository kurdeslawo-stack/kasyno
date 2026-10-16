# CasinoCore — roulette_table

Gotowy asset stołu ruletki w stylu Minecraft: ciemny orzech, zielone sukno,
złote obramowanie, koło z 37 polami w europejskiej kolejności, plansza 1–36,
trzy tabliczki: **1 coin**, **5 coins**, **25 coins**. Napisy są częścią PNG.
Stół ma około 2,75 × 1,625 bloku; blat leży na wysokości 0,98 bloku,
a najwyższy punkt wrzeciona na wysokości 1,17 bloku. Przód stołu: +Z.

## Pliki

- resource_pack/assets/casinocore/textures/entity/roulette_table.png — wspólny atlas 512×512 RGBA.
- resource_pack/assets/casinocore/models/entity/roulette_table.json — cały stół.
- resource_pack/assets/casinocore/models/entity/roulette_table_base.json — stół z nieruchomą misą, bez wirnika i kulki.
- resource_pack/assets/casinocore/models/entity/roulette_wheel.json — sam wirnik, pivot [8,8,8].
- resource_pack/assets/casinocore/models/entity/roulette_ball.json — sama kulka, pivot [8,8,8].
- resource_pack/assets/casinocore/items/*.json — definicje item_model dla Java 26.3.
- blockbench/*.bbmodel — wersja złożona i trzy osobne modele, z osadzonym PNG.
- preview/*.png — trzy rendery rzeczywistej geometrii oraz powiększony atlas.
- integration.json — punkty odniesienia i przesunięcia części.
- build_roulette_assets.py — odtwarzalny generator, Python + Pillow.

## Resource pack / szybki podgląd

Paczka jest przygotowana pod **Minecraft Java 26.3** (resource pack format 97.1).
`pack.mcmeta` używa aktualnych pól `min_format` i `max_format`, obu ustawionych
na `[97, 1]`. Dla tego nowego formatu nie używa `pack_format` ani
`supported_formats`.
Na innej wersji połącz katalog assets z docelowym resource packiem i zachowaj
pack.mcmeta odpowiednie dla wersji klienta. Nie deklarujemy testów w grze.
Geometria używa standardowych elementów Java; nie wymaga OptiFine ani moda.
Nazwa katalogu models/entity jest tylko lokalizacją pliku: to model item/block,
nie format Bedrock ani automatyczny zamiennik geometrii istniejącej encji.

Wgraj wspólną paczkę `dist/CasinoCore_ResourcePack_26.3.zip`, zawierającą
zasoby Roulette i Blackjack, do folderu resourcepacks i aktywuj paczkę.
Dla Java 26.3:

```mcfunction
/give @s minecraft:paper[minecraft:item_model="casinocore:roulette_table"]
```

Podgląd stołu jako nieruchomego mebla (wykonaj stojąc na docelowej podłodze;
przy standardowym item_display środek geometrii wypada w [8,8,8], więc dodajemy 0,5 bloku Y):

```mcfunction
/summon minecraft:item_display ~ ~0.5 ~ {Tags:["casinocore_roulette_preview"],billboard:"fixed",item_display:"none",item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"casinocore:roulette_table"}},shadow_radius:1.0f,shadow_strength:0.5f,view_range:1.0f,width:3.5f,height:3.0f}
```

Nie ma funkcji kasyna, kolizji ani hitboxa interakcji. CasinoCore później powinien
utworzyć hitbox interaction i obsługiwać zakłady. Model sam nie dodaje bloku.
Starsze wydania bez item model definitions potrzebują osobnej konfiguracji
CustomModelData/overrides.

## Osobna animacja

Wybierz **albo cały roulette_table**, **albo trzy części** — nie wyświetlaj obu
wariantów jednocześnie, bo powielisz powierzchnie.

Każdy item_display używa kontekstu none i skali 1. Bazę umieść w B=(x,y+0.5,z).
Wirnik umieść względem B: **(-0.6875, +0.50875, -0.0625)** bloku.
Kulkę w pozycji pokazanej w modelu: **(-0.6875, +0.539375, -0.35)** bloku.
Obracaj wirnik wokół jego lokalnej osi Y (pivot jest w środku modelu).
Pozycja kulki na orbicie względem B: W + (r*sin(a), h, -r*cos(a)),
gdzie W to przesunięcie wirnika, r=0.2875 bloku i h=0.030625 bloku.
Przy obrocie całego stołu obróć także wektory przesunięć jego części.
To wskazówki integracyjne; nie dołączono kodu animacji ani pluginu.

## Edycja i wygląd

Otwórz blockbench/roulette_table.bbmodel w Blockbench. Grupy base, wheel i ball
mają osobne punkty odniesienia. Osobne .bbmodel odpowiadają eksportom JSON.
Tekstura jest osadzona w plikach, a także dostarczona jako PNG.
Eksport: Java Block/Item. Koło jest celowo schodkowe/low-poly.
Blat i tabliczki mają 8 px na jednostkę modelu (128 px na blok), koło 10 px
na jednostkę. Wirnik jest pomniejszony względem misy, by mógł się obracać
o pełne 360 stopni bez kolizji wizualnej z jej krawędzią.
Materiały powtarzalne współdzielą fragmenty atlasu; szerokie jednolite listwy
mogą używać niższej gęstości. Filtrowanie podglądu: nearest / pixelated.
Szczegóły cyfr na kole służą do oglądania z bliska; wynik i wybór stawki powinny
później być podawane przez UI pluginu. Plansza jest grafiką, nie hitboxem.

Źródło formatu paczki 97.1: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
Wszystkie tekstury i geometria zostały stworzone dla tego assetu, bez zewnętrznych materiałów.
