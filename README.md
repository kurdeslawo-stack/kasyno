# CasinoCore

Ruletka i Blackjack dla **Paper 26.3 / Java 25**.
Gotowy plugin i resource pack: `dist/CasinoCore_TestServer_26.3.zip`.

## Instalacja

1. Wgraj `CasinoCore-1.0.0.jar` do `plugins/`.
2. Plugin domyślnie wymaga **Vault oraz zarejestrowanego dostawcy ekonomii**.
   Do testów bez Vault ustaw `economy.provider: local` w `plugins/CasinoCore/config.yml`.
   Salda i historia w trybie local znikają po restarcie.
3. Udostępnij graczom `CasinoCore_ResourcePack_26.3.zip` jako resource pack
   (ręcznie w kliencie lub przez adres URL w konfiguracji serwera).
4. Uruchom serwer. Uprawnienia administracyjne: `casinocore.admin` (domyślnie OP).

Otwieranie gier: `/casino roulette open` i `/casino blackjack open`.
Testowe coiny w trybie local: `/casino coins add <gracz> <kwota>`.

## Zmiana szans

`/casino chances` pokazuje aktualne ustawienia. Zmiany poniżej zapisują się
w `config.yml` i obowiązują od następnej rundy, bez restartu. Admin i gracze
z otwartym GUI kasyna usłyszą dzwon oraz zobaczą krótki komunikat.

### Ruletka

```text
/casino chances roulette <numer 0-12> <waga 0-1000000>
```

Waga określa częstość numeru, a nie procent wygranych gracza.
Domyślnie każdy numer ma wagę 1: szansa każdego wynosi 1/13, czyli 7,69%.
Przykład: `/casino chances roulette 0 2` daje zeru 2/14 = 14,29%,
a pozostałym numerom po 1/14 = 7,14%, jeśli wszystkie inne wagi wynoszą 1.
Waga 0 wyłącza numer. Co najmniej jedna waga musi pozostać dodatnia.

Zwiększenie wagi zera zwiększa również szansę wygrania zakładu dokładnie na 0.
Zmieniają się szanse wszystkich zakładów; GUI pokazuje aktualne procenty.
Wagi w pliku: `roulette.odds.weights`. Payouty: `roulette.payouts.outside`
i `roulette.payouts.exact` — są mnożnikami zwrotu łącznie ze stawką.

### Blackjack

```text
/casino chances blackjack standard
/casino chances blackjack h17
```

`standard`: krupier stoi na miękkim 17 (as + 6).
`h17`: krupier dobiera na miękkim 17.
Blackjack nie ma stałego procentu wygranych: wynik zależy też od kart i decyzji gracza.
Zasadę ustawia `blackjack.rules.dealer-hits-soft-17`.

Zysk za naturalnego blackjacka ustawia para
`blackjack.payouts.blackjack-profit-numerator` / `blackjack-profit-denominator`:
3/2 daje +150% stawki, 1/1 daje +100%. Ułamki zaokrąglane są w dół.
Zmiana wypłaty zmienia kwotę wygranej, nie prawdopodobieństwo otrzymania ręki.

Po ręcznej edycji configu: `/casino reload`.
Rundy już rozpoczęte zachowują swoje wyniki, karty i ustalone wypłaty.
