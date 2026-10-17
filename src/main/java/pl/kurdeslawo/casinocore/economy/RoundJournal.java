package pl.kurdeslawo.casinocore.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Write-ahead round records. Never replay an external call whose outcome is unknown. */
public final class RoundJournal implements AutoCloseable {
    public enum State {
        INTENT,
        UNKNOWN,
        SUCCESS,
        REJECTED
    }

    public record Transaction(String kind, long amount, State state, String detail) {}

    public record Entry(
            UUID id,
            UUID player,
            String game,
            String created,
            boolean complete,
            boolean cancelled,
            Map<String, String> data,
            Map<String, Transaction> transactions) {
        public Entry {
            data = Map.copyOf(data);
            transactions = Map.copyOf(transactions);
        }

        public boolean needsReview() {
            return transactions.values().stream()
                    .anyMatch(t -> t.state == State.UNKNOWN || t.state == State.INTENT);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, UUID> openRounds = new java.util.HashMap<>();
    private final Path directory;
    private FileChannel lockChannel;
    private FileLock lock;
    private boolean unavailable;

    /** Ephemeral development/test journal. Production uses the Path constructor. */
    public RoundJournal() {
        directory = null;
    }

    public RoundJournal(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
            lockChannel =
                    FileChannel.open(
                            this.directory.resolve("journal.lock"),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("Journal is already open");
            try (var paths = Files.list(this.directory)) {
                for (Path path :
                        paths.filter(p -> p.getFileName().toString().endsWith(".round"))
                                .sorted()
                                .toList()) {
                    Entry entry = decode(Files.readAllBytes(path));
                    if (!path.getFileName().toString().equals(entry.id + ".round")) {
                        throw new IOException("Round filename/id mismatch");
                    }
                    if (entries.put(entry.id, entry) != null)
                        throw new IOException("Duplicate round id");
                    if (!entry.complete && openRounds.putIfAbsent(entry.player, entry.id) != null) {
                        throw new IOException("Multiple unfinished rounds for one player");
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            close();
            throw new IllegalStateException(
                    "Cannot open round journal; CasinoCore must stay disabled", exception);
        }
    }

    public synchronized void begin(UUID round, UUID player, String game, Map<String, String> data) {
        checkAvailable();
        if (entries.containsKey(round)) throw new IllegalStateException("Duplicate round " + round);
        if (openRounds.containsKey(player)) {
            throw new IllegalStateException(
                    "Player has an unresolved casino round; contact an administrator");
        }
        save(
                new Entry(
                        round,
                        player,
                        game,
                        Instant.now().toString(),
                        false,
                        false,
                        data,
                        Map.of()));
    }

    public synchronized void bindProvider(String identity) {
        checkAvailable();
        if (directory == null) return;
        Path marker = directory.resolve("provider.txt");
        try {
            if (Files.exists(marker)) {
                if (!Files.readString(marker).equals(identity)) {
                    throw new IOException("Journal belongs to a different economy provider");
                }
            } else {
                if (!entries.isEmpty())
                    throw new IOException("Journal provider identity is missing");
                try (FileChannel channel =
                        FileChannel.open(
                                marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = java.nio.charset.StandardCharsets.UTF_8.encode(identity);
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
            }
        } catch (IOException exception) {
            unavailable = true;
            throw new IllegalStateException("Cannot bind journal to economy provider", exception);
        }
    }

    public synchronized boolean needsReview(UUID player) {
        if (unavailable) return true;
        UUID id = openRounds.get(player);
        return id != null && entries.get(id).needsReview();
    }

    public synchronized Entry get(UUID id) {
        checkAvailable();
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalStateException("Missing journal round " + id);
        return entry;
    }

    public synchronized List<Entry> entries(String game) {
        checkAvailable();
        return entries.values().stream()
                .filter(e -> game == null || e.game.equals(game))
                .sorted(Comparator.comparing(Entry::created).thenComparing(e -> e.id.toString()))
                .toList();
    }

    public synchronized void checkpoint(UUID id, Map<String, String> data) {
        Entry e = get(id);
        if (e.complete) throw new IllegalStateException("Round is already complete");
        save(new Entry(e.id, e.player, e.game, e.created, false, false, data, e.transactions));
    }

    public synchronized boolean withdraw(
            UUID id, String operation, long amount, EconomyProvider economy) {
        return transact(id, operation, "withdraw", amount, economy);
    }

    public synchronized void deposit(UUID id, long amount, EconomyProvider economy) {
        transact(id, "payout", "deposit", amount, economy);
    }

    private boolean transact(
            UUID id, String operation, String kind, long amount, EconomyProvider economy) {
        Entry e = get(id);
        if (e.complete || amount <= 0)
            throw new IllegalStateException("Invalid journal transaction");
        Transaction prior = e.transactions.get(operation);
        if (prior != null) {
            if (prior.amount != amount || !prior.kind.equals(kind))
                throw new IllegalStateException("Transaction mismatch");
            if (prior.state == State.SUCCESS) return true;
            if (prior.state == State.INTENT || prior.state == State.UNKNOWN)
                throw uncertain(id, operation);
        }
        if (e.needsReview()) throw uncertain(id, operation);
        putTransaction(
                e, operation, new Transaction(kind, amount, State.INTENT, "Before external call"));
        boolean applied;
        try {
            if (kind.equals("withdraw")) applied = economy.withdraw(e.player, amount);
            else {
                economy.deposit(e.player, amount);
                applied = true;
            }
        } catch (RuntimeException exception) {
            boolean rejected =
                    exception instanceof EconomyTransactionException transactionException
                            && transactionException.definitelyRejected();
            putTransaction(
                    get(id),
                    operation,
                    new Transaction(
                            kind,
                            amount,
                            rejected ? State.REJECTED : State.UNKNOWN,
                            exception.toString()));
            if (rejected) throw exception;
            throw uncertain(id, operation);
        }
        // If this write fails after an applied call, INTENT remains on disk: no automatic replay.
        putTransaction(
                get(id),
                operation,
                new Transaction(
                        kind,
                        amount,
                        applied ? State.SUCCESS : State.REJECTED,
                        "Provider returned " + applied));
        return applied;
    }

    public synchronized void requireFunded(UUID id) {
        Entry e = get(id);
        Transaction stake = e.transactions.get("stake");
        if (e.needsReview() || stake == null || stake.state != State.SUCCESS)
            throw uncertain(id, "stake/double");
    }

    public synchronized void complete(UUID id, Map<String, String> data) {
        Entry e = get(id);
        requireFunded(id);
        long payout = Long.parseLong(data.getOrDefault("payout", "0"));
        Transaction paid = e.transactions.get("payout");
        if (payout > 0 && (paid == null || paid.state != State.SUCCESS || paid.amount != payout)) {
            throw new IllegalStateException("Cannot complete an unpaid round");
        }
        save(new Entry(e.id, e.player, e.game, e.created, true, false, data, e.transactions));
    }

    public synchronized void cancelUnfunded(UUID id) {
        Entry e = get(id);
        Transaction stake = e.transactions.get("stake");
        if (e.needsReview() || (stake != null && stake.state != State.REJECTED)) {
            throw new IllegalStateException("Cannot cancel a funded or ambiguous round");
        }
        save(new Entry(e.id, e.player, e.game, e.created, true, true, e.data, e.transactions));
    }

    /** Administrator evidence changes bookkeeping only; it never calls the economy. */
    public synchronized void resolve(UUID id, String operation, boolean applied, String evidence) {
        Entry e = get(id);
        Transaction t = e.transactions.get(operation);
        if (e.complete || t == null || (t.state != State.INTENT && t.state != State.UNKNOWN)) {
            throw new IllegalStateException("Only an unresolved transaction can be reconciled");
        }
        if (evidence == null || evidence.isBlank())
            throw new IllegalArgumentException("Evidence is required");
        putTransaction(
                e,
                operation,
                new Transaction(
                        t.kind,
                        t.amount,
                        applied ? State.SUCCESS : State.REJECTED,
                        Instant.now() + " " + evidence));
    }

    private void putTransaction(Entry e, String operation, Transaction t) {
        if (!operation.matches("stake|double|payout"))
            throw new IllegalArgumentException("Unknown operation");
        Map<String, Transaction> transactions = new LinkedHashMap<>(e.transactions);
        Transaction previous = transactions.get(operation);
        String detail =
                (previous == null ? "" : previous.detail + "\n")
                        + Instant.now()
                        + " "
                        + t.state
                        + " "
                        + t.detail;
        transactions.put(operation, new Transaction(t.kind, t.amount, t.state, detail));
        save(
                new Entry(
                        e.id,
                        e.player,
                        e.game,
                        e.created,
                        e.complete,
                        e.cancelled,
                        e.data,
                        transactions));
    }

    private IllegalStateException uncertain(UUID id, String operation) {
        return new IllegalStateException(
                "Transaction requires administrator review: round="
                        + id
                        + " operation="
                        + operation
                        + "; DO NOT retry money transfers blindly");
    }

    private void checkAvailable() {
        if (unavailable)
            throw new IllegalStateException("Round journal unavailable; all transactions blocked");
    }

    private void save(Entry entry) {
        checkAvailable();
        if (directory != null) {
            Path target = directory.resolve(entry.id + ".round");
            Path temporary = directory.resolve(entry.id + ".tmp");
            try {
                byte[] encoded = encode(entry);
                try (FileChannel channel =
                        FileChannel.open(
                                temporary,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = ByteBuffer.wrap(encoded);
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                unavailable = true;
                throw new IllegalStateException(
                        "Cannot persist round " + entry.id + "; transactions blocked", exception);
            }
        }
        entries.put(entry.id, entry);
        if (entry.complete) openRounds.remove(entry.player, entry.id);
        else openRounds.put(entry.player, entry.id);
    }

    private static byte[] encode(Entry e) throws IOException {
        Properties p = new Properties();
        p.setProperty("schema", "1");
        p.setProperty("id", e.id.toString());
        p.setProperty("player", e.player.toString());
        p.setProperty("game", e.game);
        p.setProperty("created", e.created);
        p.setProperty("complete", Boolean.toString(e.complete));
        p.setProperty("cancelled", Boolean.toString(e.cancelled));
        e.data.forEach((key, value) -> p.setProperty("data." + key, value));
        e.transactions.forEach(
                (operation, t) -> {
                    String prefix = "tx." + operation + ".";
                    p.setProperty(prefix + "kind", t.kind);
                    p.setProperty(prefix + "amount", Long.toString(t.amount));
                    p.setProperty(prefix + "state", t.state.name());
                    p.setProperty(prefix + "detail", t.detail);
                });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p.store(out, "CasinoCore durable round journal; do not edit while server is running");
        byte[] payload = out.toByteArray();
        byte[] header =
                ("#sha256=" + checksum(payload) + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        framed.write(header);
        framed.write(payload);
        return framed.toByteArray();
    }

    private static Entry decode(byte[] bytes) throws IOException {
        int separator = -1;
        for (int i = 0; i < bytes.length; i++)
            if (bytes[i] == '\n') {
                separator = i;
                break;
            }
        if (separator < 0) throw new IOException("Missing round checksum");
        String header = new String(bytes, 0, separator, java.nio.charset.StandardCharsets.US_ASCII);
        byte[] payload = java.util.Arrays.copyOfRange(bytes, separator + 1, bytes.length);
        if (!header.equals("#sha256=" + checksum(payload)))
            throw new IOException("Round checksum mismatch");
        Properties p = new Properties();
        p.load(new ByteArrayInputStream(payload));
        if (!"1".equals(p.getProperty("schema")))
            throw new IOException("Unsupported journal schema");
        Map<String, String> data = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("data.")) data.put(key.substring(5), p.getProperty(key));
        }
        Map<String, Transaction> transactions = new LinkedHashMap<>();
        for (String operation : List.of("stake", "double", "payout")) {
            String prefix = "tx." + operation + ".";
            if (p.containsKey(prefix + "state")) {
                long amount = Long.parseLong(p.getProperty(prefix + "amount"));
                String kind = p.getProperty(prefix + "kind");
                if (amount <= 0 || !List.of("deposit", "withdraw").contains(kind))
                    throw new IOException("Invalid transaction");
                transactions.put(
                        operation,
                        new Transaction(
                                kind,
                                amount,
                                State.valueOf(p.getProperty(prefix + "state")),
                                p.getProperty(prefix + "detail", "")));
            }
        }
        String game = p.getProperty("game");
        if (!List.of("blackjack", "roulette").contains(game)) throw new IOException("Unknown game");
        return new Entry(
                UUID.fromString(p.getProperty("id")),
                UUID.fromString(p.getProperty("player")),
                game,
                p.getProperty("created"),
                Boolean.parseBoolean(p.getProperty("complete")),
                Boolean.parseBoolean(p.getProperty("cancelled")),
                data,
                transactions);
    }

    private static String checksum(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public synchronized void close() {
        unavailable = true;
        try {
            if (lock != null && lock.isValid()) lock.release();
        } catch (IOException ignored) {
            /* channel close releases the lock as well */
        }
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) {
            /* no transaction may run after close */
        }
    }
}
