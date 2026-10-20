# CasinoCore 1.1.0 — Laina

**Aktualny plugin:** [CasinoCore-1.1.0.jar](dist/CasinoCore-1.1.0.jar?raw=true)

**Paczka instalacyjna z instrukcją i configiem:** [Pobierz ZIP](dist/CasinoCore_Laina_1.1.0_26.3.zip?raw=true)

Plugin dla **Paper 26.3 / Java 25**. Ruletka i Blackjack korzystają z ekonomii
serwera przez Vault. Wersja Laina nie wysyła resource packa, nie ustawia własnych
`item_model`, nie tworzy mebli/Interaction ani nie uruchamia ich animacji.
Ikony pochodzą z materiałów i CustomModelData (CMD) skonfigurowanych przez właściciela.

## Instalacja i aktualizacja z 1.0.0

1. Wyłącz serwer i zrób kopię `plugins/CasinoCore/` oraz starego JAR-a.
2. Usuń stary JAR CasinoCore z `plugins/` oraz ewentualnego `plugins/update/`.
   Wgraj wyłącznie `CasinoCore-1.1.0.jar`; nie uruchamiaj dwóch wersji równocześnie.
3. Wymagane są Vault i zarejestrowany dostawca Economy obsługujący wypłaty ułamkowe.
   Domyślny provider to `vault`; brak integracji wyłącza plugin, bez przejścia na local.
4. Uruchom serwer. `/version CasinoCore` musi pokazać **1.1.0**.
   Stary config zostaje automatycznie skopiowany do `config-before-laina-*.yml`.
   Ekonomia i ustawienia Blackjacka są zachowane. Ruletka przechodzi na 0–10,
   równe szanse, wypłaty x1.8/x8 i nowe GUI; stare wagi przestają obowiązywać.
5. Ustaw CMD z własnej paczki i wykonaj `/casinocore reload`.
   Nie wgrywaj dawnej paczki CasinoCore do dystrybucji Lainy.

Dziennik nowych transakcji używa kwot w setnych częściach waluty i katalogu
`round-journal-cents-v2/`. Stary `round-journal/` pozostaje nietknięty.
Jeżeli zawiera niedokończone rundy, aktualizacja jest blokowana: rozlicz je na
wersji 1.0.0 przed przejściem na 1.1.0. **Nie usuwaj dziennika, aby obejść blokadę.**
Zakończona historia starej wersji nie jest importowana do nowego zakresu 0–10.

## Otwieranie przez mebel

Mebel/komenda konsolowa uruchamia:

```text
ruletka <nick>
```

Przykład w grze: `/ruletka Gracz`. Nick musi należeć do gracza online.
Uprawnienie wykonawcy: **`casinocore.roulette.open`** (domyślnie OP).
Gracz, któremu konsola otwiera menu, nie potrzebuje tej permisji.
Podstaw placeholder nicku zgodnie z systemem mebli używanym na serwerze.
Nie ma już `/casino`, komend create/remove, własnych hitboxów ani modeli mebli.

Blackjack: `/blackjack <nick>`, analogicznie `casinocore.blackjack.open`.
Administracja: `casinocore.admin` (domyślnie OP).

## Ruletka

- Zakres **0–10**, każdy numer z szansą **1/11**. Szans nie można zmieniać wagami.
- Zero: zielone. Czerwone: 1, 3, 5, 7, 9. Czarne: 2, 4, 6, 8, 10.
- Niskie: **1–5**, wysokie: **6–10**. Zero przegrywa wszystkie zakłady zewnętrzne.
- Numer: zwrot **x8**. Czerwone/czarne/niskie/wysokie: zwrot **x1.8**.
  Są to wypłaty **łącznie ze stawką**: za 5₭ wygrane zewnętrzne oddaje 9₭,
  więc bilans wynosi +4₭. Za 1₭ wypłata wynosi dokładnie 1.80₭.
- Wybierz zakład, następnie kliknij **1 / 5 / 25₭ po lewej**: runda startuje od razu.
  Osobny przycisk zakręcania i zakłady parzyste/nieparzyste zostały usunięte.
- Wynik jest losowany raz przed animacją. Zmiana configu nie zmienia aktywnej rundy.
- Zamknięcie menu/quit/disable rozlicza rozpoczętą rundę dokładnie raz.
  Nieznany wynik transakcji blokuje ponowny przelew i wymaga sprawdzenia logów ekonomii.
- Historia pokazuje ostatnich pięć rund z czerwonym/ zielonym bilansem kwot.

Układ (wiersze 1–6, kolumny 1–9):

```text
.      .    .    .    .    .    .    .    SALDO
1₭     .    0    1    2    3    4    .    HISTORIA
5₭     .    0    .    .    .    5    .    STATUS
25₭    .   10    9    8    7    6    .    .
.      .    .    .    .    .    .    .    .
.      .   RED BLACK LOW HIGH  .    .    ZAMKNIJ
```

Oba zielone kafelki wybierają ten sam zakład na 0 i pokazują ten sam stan CMD.
Nie tworzą dodatkowego wyniku: zero nadal ma szansę 1/11. Numery biegną
zgodnie z ruchem wskazówek zegara, wraz z animacją piłki.

## Ikony i teksty

Wszystkie ikony ruletki są w `gui.icons`. `cmd: 0` oznacza zwykły przedmiot.
Dla każdego numeru `gui.icons.numbers.'0'` ... `'10'` istnieją cztery stany:
`normal`, `selected`, `ball`, `selected-ball`.
Zakłady `gui.icons.outside.red/black/low/high` mają `normal` i `selected`.
Stawki to `gui.icons.stakes.'0'/'1'/'2'` (indeksy pozycji, nie kwoty).
Konfigurowalne są również tło, historia, saldo, zamknięcie i wszystkie statusy.

Przykład **własnych, przykładowych** CMD dla numeru 1 — trzeba podstawić identyfikatory
faktycznie zdefiniowane w paczce Lainy:

```yaml
gui:
  icons:
    numbers:
      '1':
        normal: {material: PAPER, cmd: 6101}
        selected: {material: PAPER, cmd: 6102}
        ball: {material: PAPER, cmd: 6103}
        selected-ball: {material: PAPER, cmd: 6104}
```

Wpisy dodaj do istniejącej sekcji, bez powtarzania klucza `gui`.
Każde CMD jest liczbą całkowitą 0–16777216. Stan bez wpisu dziedziczy `normal`.
Plugin nie generuje tekstur dla CMD: ich wygląd określa paczka właściciela.
Bez CMD stan piłki ma oddzielny materiał; nie opiera się wyłącznie na glincie.
Puste kafelki mają ukryty tooltip.

Tytuł domyślny: **`&5Ruletka`**. Wszystkie teksty menu ruletki, ich kolory i pogrubienie
ustawia `roulette.text` (`&e`, `&c`, `&l`, itd.). Animacja i dźwięki pozostają w
`roulette.animation` / `roulette.sounds`. Po edycji: `/casinocore reload`.
Nieprawidłowy reload jest odrzucany bez zastąpienia działającej konfiguracji.
Ikony Blackjacka można opcjonalnie nadpisać w `gui.icons.blackjack`.

## Testowa ekonomia i diagnostyka

`economy.provider: local` służy tylko do developmentu; saldo znika po restarcie.
Testowe kwoty: `/casinocore coins set <nick> <kwota>` lub `coins add` (tylko local).
Kwoty w configu i komendach są w walucie serwera, maksymalnie dwa miejsca po przecinku.
Wewnętrzny interfejs EconomyProvider i dziennik używają setnych: **100 = 1₭**.

Nierozliczone transakcje: `/casinocore transactions list`.
`retry <round-id>` ponawia wyłącznie jednoznacznie odrzuconą wypłatę.
`resolve <round-id> <stake|double|payout> <applied|not-applied> <dowód z logów ekonomii>`
służy do ręcznego uzgodnienia nieznanej transakcji. Nie ponawiaj jej w ciemno.

## Budowanie

Java 25, Maven 3.9+ (lub dołączony wrapper):

```text
./mvnw clean package
python tools/build_distribution.py
```

Windows: `.\mvnw.cmd clean package`.
Wynik: `dist/CasinoCore-1.1.0.jar` oraz `dist/CasinoCore_Laina_1.1.0_26.3.zip`.
ZIP zawiera JAR, wzorcowy config, instrukcję i sumy kontrolne. **Nie zawiera resource packa.**
