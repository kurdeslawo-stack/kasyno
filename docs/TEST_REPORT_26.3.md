# CasinoCore fault testing — 2026-10-16

Branch: `feature/blackjack`. API: `26.3.build.200-stable`, Java 25.

## Post-fix verification

The baseline failures below have been fixed with write-ahead round snapshots,
stable transaction identifiers, explicit rejected/unknown outcomes and manual
reconciliation of unknown transfers. Production Vault mode uses the persistent
`round-journal` directory. Local development remains intentionally ephemeral.

Current regression suite: **93 passing tests**, including persistence/recovery,
Vault outcome classification, administrative permissions and existing GUI tests.
The independent probe now reports **0/4 failed safety checks**: no duplicate
credit/debit, a funded round recovers from a newly opened journal, and a separate
JVM terminated with Runtime.halt recovers without lifecycle cleanup. Repeated
recovery produces one payout and one history entry. This is not a live Paper test.

Additional review removed full-history scans from per-player round-lock checks
and added pagination to `/casino transactions list [page]`. Regression tests
verify that locks are released per owner and pending entries beyond 20 are reachable.

Additional tests cover crash windows around double down and confirmed payouts,
unconfirmed transaction intents surviving restart, evidence-controlled replay,
corrupt files, disk-write failure, provider identity mismatch, process locking
and history reconstruction without duplicate entries. Journal writes happen
before any external money transfer. Unknown transfers require provider-log
evidence; no atomicity across Vault and the journal is claimed.

The following sections preserve the **pre-fix baseline** for comparison.

## Regression suite

`mvn clean test`: **64 passed, 0 failures, 0 errors, 0 skipped**.
This includes 19 Roulette tests and 45 Blackjack tests. Twenty-one new
GUI/scheduler test cases use Mockito with real BlackjackManager state transitions.

Covered scenarios include shift-click, number-key/hotbar swapping, double-click,
offhand swapping, dropping, drag cancellation, another player's menu, 100 start
clicks with one withdrawal, repeated actions after settlement, close followed by
quit, close before funding, normal four-step dealing, rejected payout during a
scheduled deal callback, cancelling tasks at shutdown, offline players, other
inventories, queued callbacks after session deletion, and reveal/result/reset
transitions.

The test setup uses a deterministic deck, mocked Bukkit events and a controlled
queue of scheduled callbacks. It does not start Paper or a Minecraft client.
Actual rendering, packet handling, audible sounds, Bukkit event dispatch and the
production server's Vault provider remain live-server validation items.

## Standalone fault injection

Run after compiling:

```powershell
py -3 tools/run_blackjack_failure_probes.py
```

**Three safety checks fail on the current implementation.** These failures are
reported independently from JUnit, with exit code 1. Raw results are saved in
`target/failure-probe-results.txt`.

| Injected fault | Expected safe result | Observed result |
| --- | --- | --- |
| Provider credits a 10-coin payout, then throws; settlement retried | Account 105 after starting at 100 and staking 5 | Account 115; 20 credited, only one history record |
| Provider debits a 5-coin stake, then throws; start retried | 5 withdrawn once | 10 withdrawn; account 90 |
| Manager reconstructed after a funded round, skipping shutdown | Funded round recoverable | No session recovered; account remains 95 |

The first two probes use a deliberately faulty in-memory EconomyProvider; they
demonstrate the interface's ambiguous-transaction failure mode, not an observed
incident in the production Vault provider. The restart probe simulates memory
loss; it does not forcibly kill a running server.

## Implications

Normal gameplay and explicitly rejected transactions pass regression tests.
Exactly-once accounting cannot be claimed when the provider commits and then
throws, or when the process loses in-memory state. Before production hardening,
introduce explicit unknown/rejected outcomes, reconciliation for unknown
transactions and durable round/transaction records. A local database alone
cannot atomically commit the external economy's transaction.

This testing pass does not add persistence or change transaction semantics.
Its only runtime adjustment defers Sound lookup until sounds are enabled, so
muted animation tests do not require a running Paper sound registry.

Changed in this testing pass: `pom.xml` (test-only Mockito dependency),
`BlackjackAnimation.java` (lazy Sound lookup), `BlackjackGuiSafetyTest.java`,
`tools/probes/BlackjackFailureProbe.java`, `tools/probes/README.md`,
`tools/run_blackjack_failure_probes.py`, and this report.
