# CasinoCore

Plugin kasyna z **ruletką i Blackjackiem** dla **Minecraft Java / Paper 26.3**.
Wymaga **Javy 25**.

**[Pobierz gotową paczkę: plugin + resource pack + config](https://github.com/kurdeslawo-stack/kasyno/blob/feature/blackjack/dist/CasinoCore_TestServer_26.3.zip)**

Aktualne źródła i pełny polski opis znajdują się na gałęzi
[feature/blackjack](https://github.com/kurdeslawo-stack/kasyno/tree/feature/blackjack).
Poniższe komendy dotyczą aktualnej paczki z tego odnośnika.

## Instalacja

Wgraj JAR do folderu `plugins/` i udostępnij graczom resource pack z paczki.
Domyślnie wymagany jest Vault z dostawcą ekonomii. Do testów bez Vault ustaw
`economy.provider: local` w configu; salda w tym trybie znikają po restarcie.
Administracja wymaga uprawnienia `casinocore.admin` (domyślnie OP).

Gry otworzysz przez `/casino roulette open` i `/casino blackjack open`.

## Zmiana szans

- `/casino chances` — pokazuje aktualne ustawienia.
- `/casino chances roulette <numer 0-12> <waga>` — zmienia częstość danego numeru.
  Większa waga oznacza częstszy wynik. Wagi nie są procentami; domyślnie wszystkie
  wynoszą 1, czyli każdy numer ma 1/13 szans. Waga 0 wyłącza numer.
- `/casino chances blackjack standard` — krupier stoi na miękkim 17.
- `/casino chances blackjack h17` — krupier dobiera na miękkim 17.

Zmiany zapisują się automatycznie i obowiązują od następnej rundy.
Blackjack nie ma stałego procentu wygranych: wynik zależy od kart i decyzji gracza.
Mnożniki wypłat można zmieniać w `config.yml`, a potem użyj `/casino reload`.
