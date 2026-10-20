# CasinoCore — komendy

| Komenda | Działanie | Uprawnienie |
| --- | --- | --- |
| `/ruletka <nick>` | Otwiera ruletkę wskazanemu graczowi online. | `casinocore.roulette.open` |
| `/blackjack <nick>` | Otwiera Blackjacka wskazanemu graczowi online. | `casinocore.blackjack.open` |
| `/casinocore reload` | Przeładowuje konfigurację. | `casinocore.admin` |
| `/casinocore coins set <nick> <kwota>` | Ustawia saldo w ekonomii `local`. | `casinocore.admin` |
| `/casinocore coins add <nick> <kwota>` | Dodaje środki w ekonomii `local`. | `casinocore.admin` |
| `/casinocore transactions list [strona]` | Pokazuje nierozliczone rundy. | `casinocore.admin` |
| `/casinocore transactions retry <id-rundy>` | Ponawia jednoznacznie odrzuconą wypłatę. | `casinocore.admin` |
| `/casinocore transactions resolve <id-rundy> <operacja> <stan> <dowód>` | Uzgadnia transakcję na podstawie logów ekonomii. | `casinocore.admin` |

W tabeli składni `resolve` wybierz jedną operację i jeden stan: `stake`, `double` lub `payout` oraz `applied` lub `not-applied`.

Komendy otwierania GUI mogą być wykonywane przez konsolę lub mebel; w konsoli pomiń `/`.
Permisję musi mieć wykonawca komendy. Gracz, któremu konsola otwiera GUI, nie potrzebuje jej.
`casinocore.admin` obejmuje obie permisje otwierania. Wszystkie domyślnie przysługują OP.
