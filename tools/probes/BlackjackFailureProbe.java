package pl.kurdeslawo.casinocore.blackjack;

import java.util.List;
import java.util.UUID;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone fault injection. A nonzero exit reports unmet crash/economy guarantees. */
public final class BlackjackFailureProbe {
    private static final UUID PLAYER = UUID.fromString("1c2456e5-3231-4149-8ec3-6eb63056473d");

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("halt-child")) {
            Path directory = Path.of(args[1]);
            FaultyEconomy economy = new FaultyEconomy(directory.resolve("wallet.txt"));
            RoundJournal journal = new RoundJournal(directory);
            manager(economy, journal).start(PLAYER);
            Runtime.getRuntime().halt(0); // No shutdown hooks, journal.close or plugin lifecycle.
        }
        int failures = 0;
        failures += creditThenThrow();
        failures += debitThenThrow();
        failures += abruptRestart();
        failures += haltedProcess();
        System.out.println("Fault-injection safety checks failed: " + failures + "/4");
        if (failures != 0) System.exit(1);
    }

    private static int creditThenThrow() {
        FaultyEconomy economy = new FaultyEconomy();
        RoundJournal journal = new RoundJournal();
        BlackjackManager manager = manager(economy, journal);
        manager.start(PLAYER);
        manager.finishDeal(PLAYER);
        economy.creditThenThrow = true;
        try { manager.stand(PLAYER); } catch (IllegalStateException expected) { /* ambiguous response */ }
        economy.creditThenThrow = false;
        boolean blocked = false;
        try { manager.stand(PLAYER); } catch (IllegalStateException expected) { blocked = true; }
        UUID round = manager.session(PLAYER).orElseThrow().roundId();
        journal.resolve(round, "payout", true, "probe: external credit known applied");
        manager.recoverRound(round);
        boolean safe = blocked && economy.balance == 105 && economy.credited == 10;
        System.out.println((safe ? "PASS" : "FAIL")
            + " credit-then-exception + retry: expected balance=105, actual=" + economy.balance
            + ", credited=" + economy.credited + ", history=" + manager.recent(PLAYER).size());
        return safe ? 0 : 1;
    }

    private static int debitThenThrow() {
        FaultyEconomy economy = new FaultyEconomy();
        RoundJournal journal = new RoundJournal();
        BlackjackManager manager = manager(economy, journal);
        economy.debitThenThrow = true;
        try { manager.start(PLAYER); } catch (IllegalStateException expected) { /* ambiguous response */ }
        economy.debitThenThrow = false;
        boolean blocked = !manager.start(PLAYER).started();
        UUID round = manager.session(PLAYER).orElseThrow().roundId();
        journal.resolve(round, "stake", true, "probe: external debit known applied");
        manager.recoverRound(round);
        boolean safe = blocked && economy.debited == 5 && economy.balance == 105;
        System.out.println((safe ? "PASS" : "FAIL")
            + " debit-then-exception + retry: expected debited=5, actual=" + economy.debited
            + ", balance=" + economy.balance);
        return safe ? 0 : 1;
    }

    private static int abruptRestart() throws Exception {
        FaultyEconomy economy = new FaultyEconomy();
        Path directory = Files.createTempDirectory("casino-recovery-probe-");
        boolean safe;
        try {
            try (RoundJournal journal = new RoundJournal(directory)) {
                manager(economy, journal).start(PLAYER);
                // No manager closeAll/onDisable: release only the OS-level journal lock.
            }
            try (RoundJournal journal = new RoundJournal(directory)) {
                BlackjackManager newProcess = manager(economy, journal);
                ArrayList<String> errors = new ArrayList<>();
                newProcess.recoverAll(errors::add);
                safe = errors.isEmpty() && economy.balance == 105 && economy.debited == 5
                    && economy.credited == 10 && newProcess.recent(PLAYER).size() == 1;
            }
        } finally {
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) Files.delete(path);
            }
            Files.delete(directory);
        }
        System.out.println((safe ? "PASS" : "FAIL")
            + " abrupt restart: funded round recovered and settled=" + safe + ", balance=" + economy.balance);
        return safe ? 0 : 1;
    }

    private static int haltedProcess() throws Exception {
        Path directory = Files.createTempDirectory("casino-halt-probe-");
        boolean safe = false;
        Process child = null;
        try {
            child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), BlackjackFailureProbe.class.getName(),
                "halt-child", directory.toString()).inheritIO().start();
            if (!child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Child JVM did not terminate within 15 seconds");
            }
            if (child.exitValue() != 0) throw new IllegalStateException("Child JVM failed");
            FaultyEconomy economy = new FaultyEconomy(directory.resolve("wallet.txt"));
            boolean funded = economy.balance == 95 && economy.debited == 5 && economy.credited == 0;
            try (RoundJournal journal = new RoundJournal(directory)) {
                BlackjackManager recovered = manager(economy, journal);
                ArrayList<String> errors = new ArrayList<>();
                recovered.recoverAll(errors::add);
                recovered.recoverAll(errors::add);
                safe = funded && errors.isEmpty() && economy.balance == 105 && economy.debited == 5
                    && economy.credited == 10 && recovered.recent(PLAYER).size() == 1;
            }
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor();
            }
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) Files.delete(path);
            }
            Files.delete(directory);
        }
        System.out.println((safe ? "PASS" : "FAIL")
            + " real JVM halt: funded round recovered; repeated recovery paid and recorded once=" + safe);
        return safe ? 0 : 1;
    }

    private static BlackjackManager manager(FaultyEconomy economy, RoundJournal journal) {
        return new BlackjackManager(economy, List.of(5L), 3, 2, false,
            () -> new BlackjackDeck(List.of(
                new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.HEARTS),
                new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES),
                new BlackjackCard(BlackjackRank.NINE, BlackjackSuit.HEARTS),
                new BlackjackCard(BlackjackRank.EIGHT, BlackjackSuit.SPADES))),
            new InMemoryBlackjackHistoryRepository(), journal);
    }

    private static final class FaultyEconomy implements EconomyProvider {
        long balance = 100;
        long credited;
        long debited;
        boolean creditThenThrow;
        boolean debitThenThrow;
        Path wallet;

        FaultyEconomy() { }

        FaultyEconomy(Path wallet) throws java.io.IOException {
            this.wallet = wallet;
            if (Files.exists(wallet)) {
                List<String> values = Files.readAllLines(wallet);
                balance = Long.parseLong(values.get(0));
                credited = Long.parseLong(values.get(1));
                debited = Long.parseLong(values.get(2));
            }
        }

        private void persist() {
            if (wallet == null) return;
            try {
                Files.writeString(wallet, balance + "\n" + credited + "\n" + debited + "\n");
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }

        public long getBalance(UUID id) { return balance; }
        public boolean has(UUID id, long amount) { return balance >= amount; }
        public boolean withdraw(UUID id, long amount) {
            if (balance < amount) return false;
            balance -= amount;
            debited += amount;
            persist();
            if (debitThenThrow) throw new IllegalStateException("Timeout after committed debit");
            return true;
        }
        public void deposit(UUID id, long amount) {
            balance += amount;
            credited += amount;
            persist();
            if (creditThenThrow) throw new IllegalStateException("Timeout after committed credit");
        }
    }
}
