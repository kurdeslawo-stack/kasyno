package pl.kurdeslawo.casinocore;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import pl.kurdeslawo.casinocore.blackjack.*;
import pl.kurdeslawo.casinocore.blackjack.gui.*;
import pl.kurdeslawo.casinocore.command.CasinoCommand;
import pl.kurdeslawo.casinocore.command.OpenGameCommand;
import pl.kurdeslawo.casinocore.economy.*;
import pl.kurdeslawo.casinocore.roulette.*;
import pl.kurdeslawo.casinocore.roulette.gui.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CasinoCorePlugin extends JavaPlugin {
    private EconomyProvider economyProvider;
    private String activeEconomyProvider;
    private RouletteManager rouletteManager;
    private RouletteGui rouletteGui;
    private RouletteAnimation rouletteAnimation;
    private BlackjackManager blackjackManager;
    private BlackjackGui blackjackGui;
    private BlackjackAnimation blackjackAnimation;
    private RoundJournal roundJournal;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            migrateConfiguration();
            validateConfiguration();
            initializeEconomy();
            if (economyProvider instanceof LocalEconomyProvider) roundJournal = new RoundJournal();
            else {
                requireLegacyRoundsSettled(getDataFolder().toPath().resolve("round-journal"));
                roundJournal =
                        new RoundJournal(
                                getDataFolder().toPath().resolve("round-journal-cents-v2"));
                roundJournal.bindProvider(
                        "vault/"
                                + ((VaultEconomyProvider) economyProvider).providerName()
                                + "/minor-100/v2");
            }
            rouletteManager =
                    new RouletteManager(
                            economyProvider,
                            new RouletteRandom(),
                            stakes("roulette"),
                            multiplier("outside"),
                            multiplier("exact"),
                            new InMemoryRouletteHistoryRepository(),
                            roundJournal);
            rouletteGui = new RouletteGui(this, rouletteManager, economyProvider);
            rouletteAnimation = new RouletteAnimation(this, rouletteManager, rouletteGui);
            blackjackManager =
                    new BlackjackManager(
                            economyProvider,
                            stakes("blackjack"),
                            getConfig().getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                            getConfig().getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                            getConfig().getBoolean("blackjack.rules.dealer-hits-soft-17", false),
                            roundJournal);
            blackjackManager.recoverAll(message -> getLogger().severe("RECOVERY: " + message));
            rouletteManager.recoverAll(message -> getLogger().severe("RECOVERY: " + message));
            blackjackGui = new BlackjackGui(this, blackjackManager, economyProvider);
            blackjackAnimation = new BlackjackAnimation(this, blackjackManager, blackjackGui);
            var events = getServer().getPluginManager();
            events.registerEvents(
                    new RouletteGuiListener(this, rouletteManager, rouletteGui, rouletteAnimation),
                    this);
            events.registerEvents(
                    new BlackjackGuiListener(
                            this, blackjackManager, blackjackGui, blackjackAnimation),
                    this);
            OpenGameCommand rouletteCommand = new OpenGameCommand(this, false);
            getCommand("ruletka").setExecutor(rouletteCommand);
            getCommand("ruletka").setTabCompleter(rouletteCommand);
            OpenGameCommand blackjackCommand = new OpenGameCommand(this, true);
            getCommand("blackjack").setExecutor(blackjackCommand);
            getCommand("blackjack").setTabCompleter(blackjackCommand);
            CasinoCommand admin = new CasinoCommand(this);
            getCommand("casinocore").setExecutor(admin);
            getCommand("casinocore").setTabCompleter(admin);
            getLogger()
                    .info(
                            "CasinoCore v"
                                    + getPluginMeta().getVersion()
                                    + " | Laina | roulette=0-10 uniform | economy="
                                    + (economyProvider instanceof VaultEconomyProvider v
                                            ? "vault/" + v.providerName()
                                            : "local DEVELOPMENT")
                                    + " | Vault="
                                    + getServer().getPluginManager().isPluginEnabled("Vault")
                                    + " | CMD only; furniture disabled");
        } catch (RuntimeException e) {
            getLogger().severe("ERROR: CasinoCore wyłączony: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (rouletteAnimation != null) rouletteAnimation.cancelAll();
        if (blackjackAnimation != null) blackjackAnimation.cancelAll();
        try {
            if (blackjackManager != null) blackjackManager.closeAll();
        } catch (RuntimeException e) {
            getLogger().severe("Blackjack settlement: " + e.getMessage());
        }
        try {
            if (rouletteManager != null) rouletteManager.settleAll();
        } catch (RuntimeException e) {
            getLogger().severe("Roulette settlement: " + e.getMessage());
        }
        if (roundJournal != null) roundJournal.close();
    }

    public boolean reloadPluginConfiguration() {
        var previous = getConfig();
        // Load and validate separately so invalid reloads cannot poison a running GUI or payout
        // config.
        var candidate = new YamlConfiguration();
        try {
            candidate.load(getDataFolder().toPath().resolve("config.yml").toFile());
            candidate.setDefaults(previous.getDefaults());
            validate(candidate);
            if (!provider(candidate).equals(activeEconomyProvider))
                throw new IllegalArgumentException("Zmiana ekonomii wymaga restartu");
            if (economyProvider instanceof VaultEconomyProvider vault)
                validateVaultPrecision(candidate, vault.fractionalDigits());
            reloadConfig();
            rouletteManager.updateConfiguration(
                    stakes("roulette"), multiplier("outside"), multiplier("exact"));
            rouletteAnimation.reloadConfiguration();
            blackjackManager.updateConfiguration(
                    stakes("blackjack"),
                    getConfig().getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                    getConfig().getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                    getConfig().getBoolean("blackjack.rules.dealer-hits-soft-17", false));
            return true;
        } catch (Exception e) {
            getLogger().severe("Config reload odrzucony: " + e.getMessage());
            return false;
        }
    }

    private void migrateConfiguration() {
        if (getConfig().getInt("config-version", 0) >= 2) return;
        try {
            var old = getConfig();
            Path file = getDataFolder().toPath().resolve("config.yml");
            Files.copy(
                    file,
                    getDataFolder()
                            .toPath()
                            .resolve("config-before-laina-" + System.currentTimeMillis() + ".yml"));
            var defaults =
                    YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(
                                    java.util.Objects.requireNonNull(getResource("config.yml")),
                                    java.nio.charset.StandardCharsets.UTF_8));
            for (String prefix : List.of("economy", "blackjack")) {
                var section = old.getConfigurationSection(prefix);
                if (section != null)
                    section.getValues(true)
                            .forEach(
                                    (key, value) -> {
                                        if (!(value
                                                instanceof
                                                org.bukkit.configuration.ConfigurationSection))
                                            defaults.set(prefix + "." + key, value);
                                    });
            }
            if (old.contains("roulette.stakes"))
                defaults.set("roulette.stakes", old.getList("roulette.stakes"));
            defaults.save(file.toFile());
            reloadConfig();
            getLogger()
                    .warning(
                            "Zapisano kopię starego configu. Włączono specyfikację Laina 0-10, x1.8/x8, CMD; stara ekonomia zachowana.");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Migracja configu nie powiodła się", e);
        }
    }

    public static void requireLegacyRoundsSettled(Path directory) {
        if (!Files.exists(directory)) return;
        try (var old = new RoundJournal(directory)) {
            if (old.entries(null).stream().anyMatch(e -> !e.complete()))
                throw new IllegalStateException(
                        "Stary round-journal zawiera nierozliczone rundy. Rozlicz je na 1.0.0 PRZED migracją; nie usuwaj dziennika.");
        }
    }

    private void initializeEconomy() {
        activeEconomyProvider = provider(getConfig());
        if (activeEconomyProvider.equals("local")) {
            economyProvider =
                    new LocalEconomyProvider(
                            Money.parse(
                                    getConfig().getString("economy.local.starting-balance", "0")));
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("Vault"))
            throw new IllegalStateException(
                    "Brak aktywnego Vault; automatyczny fallback jest wyłączony");
        var service = getServer().getServicesManager().getRegistration(Economy.class);
        if (service == null || service.getProvider() == null)
            throw new IllegalStateException("Vault nie ma zarejestrowanego providera Economy");
        if (service.getProvider().fractionalDigits() == 0)
            throw new IllegalStateException(
                    "Provider Vault nie obsługuje ułamków waluty wymaganych do wypłat x1.8");
        validateVaultPrecision(getConfig(), service.getProvider().fractionalDigits());
        economyProvider = new VaultEconomyProvider(service.getProvider());
    }

    private static void validateVaultPrecision(
            org.bukkit.configuration.file.FileConfiguration config, int digits) {
        if (digits >= 0 && digits < 2) {
            var engine =
                    new RouletteEngine(
                            new BigDecimal(config.getString("roulette.payouts.outside", "1.8")),
                            new BigDecimal(config.getString("roulette.payouts.exact", "8")));
            var bj =
                    new BlackjackEngine(
                            config.getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                            config.getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                            false);
            for (String game : List.of("roulette", "blackjack"))
                for (long stake : stakes(config, game)) {
                    List<Long> amounts =
                            game.equals("roulette")
                                    ? List.of(
                                            stake,
                                            engine.totalReturn(
                                                    new RouletteBet(
                                                            new java.util.UUID(0, 0),
                                                            RouletteBetType.RED,
                                                            stake,
                                                            null),
                                                    new RouletteResult(1, RouletteColor.RED)),
                                            engine.totalReturn(
                                                    new RouletteBet(
                                                            new java.util.UUID(0, 0),
                                                            RouletteBetType.EXACT_NUMBER,
                                                            stake,
                                                            1),
                                                    new RouletteResult(1, RouletteColor.RED)))
                                    : List.of(
                                            stake,
                                            bj.totalReturn(BlackjackOutcome.BLACKJACK, stake));
                    for (long value : amounts)
                        if (BigDecimal.valueOf(value, 2).stripTrailingZeros().scale() > digits)
                            throw new IllegalStateException(
                                    "Dokładność ekonomii Vault jest za mała dla skonfigurowanych stawek i wypłat");
                }
        }
    }

    private static String provider(org.bukkit.configuration.file.FileConfiguration c) {
        return c.getString("economy.provider", "vault").trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal multiplier(String key) {
        return new BigDecimal(
                getConfig()
                        .getString("roulette.payouts." + key, key.equals("outside") ? "1.8" : "8"));
    }

    private List<Long> stakes(String game) {
        return stakes(getConfig(), game);
    }

    private static List<Long> stakes(
            org.bukkit.configuration.file.FileConfiguration c, String game) {
        var values = c.getList(game + ".stakes");
        if (values == null) throw new IllegalArgumentException(game + ".stakes missing");
        return values.stream().map(v -> Money.parse(v.toString())).toList();
    }

    private void validateConfiguration() {
        validate(getConfig());
    }

    private static void validate(org.bukkit.configuration.file.FileConfiguration c) {
        if (c.getInt("config-version") != 2)
            throw new IllegalArgumentException("Wymagany config-version: 2");
        if (!List.of("vault", "local").contains(provider(c)))
            throw new IllegalArgumentException("economy.provider: vault lub local");
        Money.parse(c.getString("economy.local.starting-balance", "0"));
        var engine =
                new RouletteEngine(
                        new BigDecimal(c.getString("roulette.payouts.outside", "1.8")),
                        new BigDecimal(c.getString("roulette.payouts.exact", "8")));
        for (String game : List.of("roulette", "blackjack")) {
            List<Long> values = stakes(c, game);
            if (values.isEmpty()
                    || values.size() > 3
                    || values.stream().distinct().count() != values.size()
                    || values.stream().anyMatch(v -> v <= 0))
                throw new IllegalArgumentException(game + ": 1–3 różne dodatnie stawki");
            for (long stake : values) {
                if (game.equals("roulette")) {
                    for (var type : List.of(RouletteBetType.EXACT_NUMBER, RouletteBetType.RED)) {
                        long payout =
                                engine.totalReturn(
                                        new RouletteBet(
                                                new java.util.UUID(0, 0),
                                                type,
                                                stake,
                                                type == RouletteBetType.EXACT_NUMBER ? 1 : null),
                                        new RouletteResult(1, RouletteColor.RED));
                        Money.toVault(payout);
                    }
                } else {
                    var bj =
                            new BlackjackEngine(
                                    c.getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                                    c.getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                                    false);
                    Money.toVault(bj.maximumReturn(stake));
                }
            }
        }
        for (String key :
                List.of(
                        "fast-delay-ticks",
                        "medium-delay-ticks",
                        "slow-delay-ticks",
                        "final-delay-ticks",
                        "final-pulse-delay-ticks",
                        "result-duration-ticks"))
            if (c.getLong("roulette.animation." + key) <= 0
                    || c.getLong("roulette.animation." + key) > 1200)
                throw new IllegalArgumentException("Nieprawidłowy czas animacji: " + key);
        if (c.getLong("roulette.animation.fast-delay-ticks")
                        > c.getLong("roulette.animation.medium-delay-ticks")
                || c.getLong("roulette.animation.medium-delay-ticks")
                        > c.getLong("roulette.animation.slow-delay-ticks")
                || c.getLong("roulette.animation.slow-delay-ticks")
                        > c.getLong("roulette.animation.final-delay-ticks"))
            throw new IllegalArgumentException("Animacja musi stopniowo zwalniać");
        for (String key :
                List.of("deal-delay-ticks", "dealer-delay-ticks", "result-duration-ticks"))
            if (c.getLong("blackjack.animation." + key) <= 0
                    || c.getLong("blackjack.animation." + key) > 1200)
                throw new IllegalArgumentException("Nieprawidłowy czas Blackjacka");
        for (String key : List.of("tick-volume", "final-volume", "win-volume")) {
            double v = c.getDouble("roulette.sounds." + key);
            if (!Double.isFinite(v) || v < 0 || v > 4)
                throw new IllegalArgumentException("Nieprawidłowa głośność: " + key);
        }
        var icons = c.getConfigurationSection("gui.icons");
        if (icons != null)
            for (String key : icons.getKeys(true)) {
                if (key.endsWith(".material")) {
                    Material mat = Material.matchMaterial(icons.getString(key, ""));
                    if (mat == null || mat.isAir() || !mat.isItem())
                        throw new IllegalArgumentException("Nieprawidłowy materiał: " + key);
                }
                if (key.endsWith(".cmd")) {
                    Object value = icons.get(key);
                    if (!(value instanceof Integer n) || n < 0 || n > 16_777_216)
                        throw new IllegalArgumentException(
                                "CMD musi być całkowite 0–16777216: " + key);
                }
            }
    }

    public EconomyProvider economyProvider() {
        return economyProvider;
    }

    public RouletteManager rouletteManager() {
        return rouletteManager;
    }

    public RouletteGui rouletteGui() {
        return rouletteGui;
    }

    public BlackjackManager blackjackManager() {
        return blackjackManager;
    }

    public BlackjackGui blackjackGui() {
        return blackjackGui;
    }

    public RoundJournal roundJournal() {
        return roundJournal;
    }
}
