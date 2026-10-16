# Blackjack fault injection

Run after compiling the plugin:

```powershell
py -3 tools/run_blackjack_failure_probes.py
```

The script uses temporary copies of compiled classes and fake accounts. It never
connects to a live server or economy. Results are written to
`target/failure-probe-results.txt`.

These are safety probes, separate from the JUnit regression suite. A nonzero
exit code means at least one desired guarantee is not implemented. Do not treat
a passing regression suite as proof of safety against these failures.

Fixed scenarios now checked by the probes:

1. Provider credits a payout, then throws. Blind retry is blocked. After evidence
   confirms the credit was applied, the balance stays 105 and one history entry is created.
2. Provider debits a stake, then throws. Starting again cannot withdraw another
   stake. Evidence confirming the debit permits the saved round to finish.
3. A new manager and a newly opened file journal recover a confirmed funded round
   without calling lifecycle settlement on the old manager. It is paid once.
4. A separate JVM funds a round and terminates with Runtime.halt, skipping all
   cleanup hooks. A new process recovers the journal and a file-backed fake wallet;
   repeated recovery pays once and creates one history entry.

The third case reconstructs the manager; the fourth actually halts a standalone
JVM. Neither terminates a real Paper server. GUI/scheduler JUnit tests use mocked
Bukkit events and scheduling; an actual client/server test remains necessary.

Production Vault mode supplies the durable file journal automatically. The probe
uses a temporary directory. Unknown external transfers deliberately require
administrator reconciliation; the probe knows the injected operation was applied
and supplies that evidence. It does not infer outcomes from a balance difference.
