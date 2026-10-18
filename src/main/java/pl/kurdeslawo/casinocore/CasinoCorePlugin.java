package pl.kurdeslawo.casinocore;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import pl.kurdeslawo.casinocore.blackjack.BlackjackEngine;
import pl.kurdeslawo.casinocore.blackjack.BlackjackManager;
import pl.kurdeslawo.casinocore.blackjack.gui.BlackjackAnimation;
import pl.kurdeslawo.casinocore.blackjack.gui.BlackjackGui;
import pl.kurdeslawo.casinocore.blackjack.gui.BlackjackGuiListener;
import pl.kurdeslawo.casinocore.command.CasinoCommand;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;
import pl.kurdeslawo.casinocore.economy.VaultEconomyProvider;
import pl.kurdeslawo.casinocore.roulette.InMemoryRouletteHistoryRepository;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;
import pl.kurdeslawo.casinocore.roulette.RouletteRandom;
import pl.kurdeslawo.casinocore.roulette.gui.RouletteAnimation;
import pl.kurdeslawo.casinocore.roulette.gui.RouletteGui;
import pl.kurdeslawo.casinocore.roulette.gui.RouletteGuiListener;
import pl.kurdeslawo.casinocore.roulette.interaction.RouletteInteractionListener;
import pl.kurdeslawo.casinocore.roulette.interaction.RouletteTableInteractionManager;

import java.util.List;
import java.util.Locale;

public final class CasinoCorePlugin extends JavaPlugin {
    private pl.kurdeslawo.casinocore.visual.TableEffects tableEffects;
    private static final long MAX_EXACT_VAULT_INTEGER = 9_007_199_254_740_992L;
    private EconomyProvider economyProvider;
    private String activeEconomyProvider;
    private RouletteManager rouletteManager;
    private RouletteGui rouletteGui;
    private RouletteAnimation rouletteAnimation;
    private RouletteTableInteractionManager interactionManager;
    private BlackjackManager blackjackManager;
    private BlackjackGui blackjackGui;
    private BlackjackAnimation blackjackAnimation;
    private RoundJournal roundJournal;
    private RouletteRandom rouletteRandom;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!validateConfiguration() || !initializeEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            roundJournal =
                    economyProvider instanceof LocalEconomyProvider
                            ? new RoundJournal()
                            : new RoundJournal(getDataFolder().toPath().resolve("round-journal"));
            if (economyProvider instanceof VaultEconomyProvider vault)
                roundJournal.bindProvider("vault/" + vault.providerName());
        } catch (RuntimeException exception) {
            getLogger()
                    .severe("ERROR: Cannot open durable round journal: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rouletteRandom = new RouletteRandom();
        rouletteRandom.updateWeights(rouletteWeights());
        rouletteManager =
                new RouletteManager(
                        economyProvider,
                        rouletteRandom,
                        stakes(),
                        getConfig().getInt("roulette.payouts.outside", 2),
                        getConfig().getInt("roulette.payouts.exact", 12),
                        new InMemoryRouletteHistoryRepository(),
                        roundJournal);
        rouletteGui = new RouletteGui(this, rouletteManager, economyProvider);
        rouletteAnimation = new RouletteAnimation(this, rouletteManager, rouletteGui);
        blackjackManager =
                new BlackjackManager(
                        economyProvider,
                        blackjackStakes(),
                        getConfig().getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                        getConfig().getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                        getConfig().getBoolean("blackjack.rules.dealer-hits-soft-17", false),
                        roundJournal);
        blackjackManager.recoverAll(message -> getLogger().severe("RECOVERY: " + message));
        rouletteManager.recoverAll(message -> getLogger().severe("RECOVERY: " + message));
        blackjackGui = new BlackjackGui(this, blackjackManager, economyProvider);
        blackjackAnimation = new BlackjackAnimation(this, blackjackManager, blackjackGui);
        interactionManager = new RouletteTableInteractionManager(this);
        interactionManager.load();

        getServer()
                .getPluginManager()
                .registerEvents(
                        new RouletteGuiListener(
                                this, rouletteManager, rouletteGui, rouletteAnimation),
                        this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new RouletteInteractionListener(interactionManager, rouletteGui), this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new BlackjackGuiListener(
                                this, blackjackManager, blackjackGui, blackjackAnimation),
                        this);

        PluginCommand casinoCommand = getCommand("casino");
        if (casinoCommand == null) {
            throw new IllegalStateException("Command 'casino' is missing from plugin.yml");
        }
        CasinoCommand executor = new CasinoCommand(this);
        casinoCommand.setExecutor(executor);
        casinoCommand.setTabCompleter(executor);
        tableEffects = new pl.kurdeslawo.casinocore.visual.TableEffects(this);
        boolean vaultDetected = getServer().getPluginManager().isPluginEnabled("Vault");
        String economyName =
                economyProvider instanceof VaultEconomyProvider vault
                        ? "vault/" + vault.providerName()
                        : "local (development only)";
        getLogger()
                .info(
                        "CasinoCore v"
                                + getPluginMeta().getVersion()
                                + " enabled | economy="
                                + economyName
                                + " | Vault="
                                + (vaultDetected ? "detected" : "not detected"));
        if (economyProvider instanceof LocalEconomyProvider) {
            getLogger()
                    .warning(
                            "LocalEconomyProvider is active. It is intended only for development and loses balances on restart.");
        }
    }

    @Override
    public void onDisable() {
        if (tableEffects != null) tableEffects.close();
        if (rouletteAnimation != null) {
            rouletteAnimation.cancelAll();
        }
        if (blackjackAnimation != null) blackjackAnimation.cancelAll();
        if (blackjackManager != null) {
            try {
                blackjackManager.closeAll();
            } catch (RuntimeException exception) {
                getLogger()
                        .severe(
                                "ERROR: A pending Blackjack round could not be settled during shutdown: "
                                        + exception.getMessage());
            }
        }
        if (rouletteManager != null) {
            try {
                rouletteManager.settleAll();
            } catch (RuntimeException exception) {
                getLogger()
                        .severe(
                                "ERROR: A pending Roulette round could not be settled during shutdown: "
                                        + exception.getMessage());
            }
        }
        if (roundJournal != null) roundJournal.close();
    }

    public boolean reloadPluginConfiguration() {
        reloadConfig();
        return applyPluginConfiguration();
    }

    private boolean applyPluginConfiguration() {
        if (!validateConfiguration()) {
            return false;
        }
        String configuredProvider = configuredEconomyProvider();
        if (!configuredProvider.equals(activeEconomyProvider)) {
            getLogger()
                    .severe(
                            "Economy provider changes require a server restart. CasinoCore remains on '"
                                    + activeEconomyProvider
                                    + "'.");
            return false;
        }
        rouletteManager.updateConfiguration(
                stakes(),
                getConfig().getInt("roulette.payouts.outside", 2),
                getConfig().getInt("roulette.payouts.exact", 12));
        rouletteRandom.updateWeights(rouletteWeights());
        rouletteAnimation.reloadConfiguration();
        blackjackManager.updateConfiguration(
                blackjackStakes(),
                getConfig().getInt("blackjack.payouts.blackjack-profit-numerator", 3),
                getConfig().getInt("blackjack.payouts.blackjack-profit-denominator", 2),
                getConfig().getBoolean("blackjack.rules.dealer-hits-soft-17", false));
        interactionManager.updateDimensions();
        return true;
    }

    private boolean initializeEconomy() {
        activeEconomyProvider = configuredEconomyProvider();
        if (activeEconomyProvider.equals("local")) {
            long startingBalance = getConfig().getLong("economy.local.starting-balance", 0L);
            economyProvider = new LocalEconomyProvider(startingBalance);
            return true;
        }
        if (!activeEconomyProvider.equals("vault")) {
            getLogger()
                    .severe(
                            "ERROR: Unknown economy.provider '"
                                    + activeEconomyProvider
                                    + "'. Allowed: local, vault.");
            return false;
        }
        if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
            getLogger()
                    .severe(
                            "ERROR: economy.provider is 'vault', but Vault is missing or disabled. CasinoCore will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger()
                    .severe(
                            "ERROR: Vault is active, but no Economy provider is registered. CasinoCore will be disabled.");
            return false;
        }
        economyProvider = new VaultEconomyProvider(registration.getProvider());
        return true;
    }

    private boolean validateConfiguration() {
        try {
            RouletteRandom.validateWeights(rouletteWeights());
        } catch (IllegalArgumentException exception) {
            getLogger().severe("roulette.odds.weights: " + exception.getMessage());
            return false;
        }
        if (getConfig().getInt("roulette.numbers.min", 0) != 0
                || getConfig().getInt("roulette.numbers.max", 12) != 12) {
            getLogger().severe("This Roulette version requires roulette.numbers.min=0 and max=12.");
            return false;
        }
        List<Long> stakes = stakes();
        int outside = getConfig().getInt("roulette.payouts.outside", 2);
        int exact = getConfig().getInt("roulette.payouts.exact", 12);
        if (stakes.isEmpty()
                || stakes.size() > 3
                || stakes.stream().anyMatch(value -> value == null || value <= 0)
                || outside <= 0
                || exact <= 0) {
            getLogger()
                    .severe(
                            "Roulette stakes (1-3 positive values) and payout multipliers must be valid.");
            return false;
        }
        long maxStake = stakes.stream().mapToLong(Long::longValue).max().orElseThrow();
        if (maxStake > Long.MAX_VALUE / Math.max(outside, exact)) {
            getLogger()
                    .severe("Roulette stake and payout configuration can overflow a long value.");
            return false;
        }
        if ("vault".equals(configuredEconomyProvider())
                && maxStake * Math.max(outside, exact) > MAX_EXACT_VAULT_INTEGER) {
            getLogger().severe("Vault Roulette payouts cannot exceed 2^53 coins.");
            return false;
        }
        double width = getConfig().getDouble("roulette.interaction.width", 2.75);
        double height = getConfig().getDouble("roulette.interaction.height", 1.5);
        double removeRadius = getConfig().getDouble("roulette.interaction.remove-radius", 4.0);
        if (width <= 0 || height <= 0 || removeRadius <= 0) {
            getLogger()
                    .severe(
                            "Roulette interaction width, height and remove radius must be positive.");
            return false;
        }
        if ("local".equals(configuredEconomyProvider())
                && getConfig().getLong("economy.local.starting-balance", 0L) < 0) {
            getLogger().severe("economy.local.starting-balance cannot be negative.");
            return false;
        }
        long fastDelay = getConfig().getLong("roulette.animation.fast-delay-ticks", 2L);
        long mediumDelay = getConfig().getLong("roulette.animation.medium-delay-ticks", 3L);
        long slowDelay = getConfig().getLong("roulette.animation.slow-delay-ticks", 5L);
        long finalDelay = getConfig().getLong("roulette.animation.final-delay-ticks", 8L);
        long pulseDelay = getConfig().getLong("roulette.animation.final-pulse-delay-ticks", 4L);
        long resultDuration = getConfig().getLong("roulette.animation.result-duration-ticks", 50L);
        if (fastDelay <= 0
                || mediumDelay <= 0
                || slowDelay <= 0
                || finalDelay <= 0
                || pulseDelay <= 0
                || resultDuration <= 0) {
            getLogger().severe("Roulette animation delays must be positive tick values.");
            return false;
        }
        if (fastDelay > mediumDelay || mediumDelay > slowDelay || slowDelay > finalDelay) {
            getLogger()
                    .severe(
                            "Roulette animation delays must keep the order fast <= medium <= slow <= final.");
            return false;
        }
        for (String key : List.of("tick-volume", "final-volume", "win-volume")) {
            double volume = getConfig().getDouble("roulette.sounds." + key, 1.0);
            if (!Double.isFinite(volume) || volume < 0.0 || volume > 4.0) {
                getLogger().severe("roulette.sounds." + key + " must be between 0 and 4.");
                return false;
            }
        }
        List<Long> blackjackStakes = blackjackStakes();
        int blackjackNumerator =
                getConfig().getInt("blackjack.payouts.blackjack-profit-numerator", 3);
        int blackjackDenominator =
                getConfig().getInt("blackjack.payouts.blackjack-profit-denominator", 2);
        if (blackjackStakes.isEmpty()
                || blackjackStakes.size() > 3
                || blackjackStakes.stream().anyMatch(value -> value == null || value <= 0)
                || blackjackNumerator <= 0
                || blackjackDenominator <= 0) {
            getLogger().severe("Blackjack stakes and payout ratio must be valid.");
            return false;
        }
        long maxBlackjackStake =
                blackjackStakes.stream().mapToLong(Long::longValue).max().orElseThrow();
        long maximumBlackjackReturn;
        try {
            maximumBlackjackReturn =
                    new BlackjackEngine(blackjackNumerator, blackjackDenominator, false)
                            .maximumReturn(maxBlackjackStake);
        } catch (ArithmeticException exception) {
            getLogger()
                    .severe("Blackjack stake and payout configuration can overflow a long value.");
            return false;
        }
        if ("vault".equals(configuredEconomyProvider())
                && maximumBlackjackReturn > MAX_EXACT_VAULT_INTEGER) {
            getLogger().severe("Vault Blackjack payouts cannot exceed 2^53 coins.");
            return false;
        }
        if (getConfig().getLong("blackjack.animation.deal-delay-ticks", 5) <= 0
                || getConfig().getLong("blackjack.animation.dealer-delay-ticks", 6) <= 0
                || getConfig().getLong("blackjack.animation.result-duration-ticks", 50) <= 0) {
            getLogger().severe("Blackjack animation timings must be positive tick values.");
            return false;
        }
        return true;
    }

    private String configuredEconomyProvider() {
        String configured = getConfig().getString("economy.provider");
        return configured == null ? "vault" : configured.trim().toLowerCase(Locale.ROOT);
    }

    private List<Long> stakes() {
        return getConfig().getLongList("roulette.stakes");
    }

    private List<Long> blackjackStakes() {
        return getConfig().getLongList("blackjack.stakes");
    }

    private List<Long> rouletteWeights() {
        var section = getConfig().getConfigurationSection("roulette.odds.weights");
        if (section == null && getConfig().contains("roulette.odds.weights")) {
            throw new IllegalArgumentException("Oczekiwano mapy wag dla numerów 0–12");
        }
        if (section != null
                && section.getKeys(false).stream().anyMatch(k -> !k.matches("[0-9]|1[0-2]"))) {
            throw new IllegalArgumentException("Dozwolone numery: 0–12");
        }
        java.util.ArrayList<Long> weights = new java.util.ArrayList<>();
        for (int number = 0; number <= 12; number++) {
            Object value = section == null ? 1 : section.get(Integer.toString(number), 1);
            if (!(value instanceof Integer || value instanceof Long)) {
                throw new IllegalArgumentException(
                        "Waga numeru " + number + " musi być liczbą całkowitą");
            }
            weights.add(((Number) value).longValue());
        }
        return weights;
    }

    public RouletteRandom rouletteRandom() {
        return rouletteRandom;
    }

    public boolean updateRouletteWeight(int number, long weight) {
        if (number < 0 || number > 12 || weight < 0 || weight > RouletteRandom.MAX_WEIGHT)
            return false;
        return updateChanceSetting("roulette.odds.weights." + number, weight);
    }

    public boolean updateBlackjackDifficulty(boolean hitSoft17) {
        return updateChanceSetting("blackjack.rules.dealer-hits-soft-17", hitSoft17);
    }

    private boolean updateChanceSetting(String path, Object value) {
        Object previous = getConfig().get(path);
        getConfig().set(path, value);
        try {
            if (!applyPluginConfiguration()) {
                getConfig().set(path, previous);
                return false;
            }
            var destination = getDataFolder().toPath().resolve("config.yml");
            var temporary = getDataFolder().toPath().resolve("config.yml.chances.tmp");
            java.nio.file.Files.writeString(temporary, getConfig().saveToString());
            java.nio.file.Files.move(
                    temporary,
                    destination,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (java.io.IOException | RuntimeException exception) {
            getConfig().set(path, previous);
            applyPluginConfiguration();
            getLogger().severe("Nie zapisano ustawień szans: " + exception.getMessage());
            return false;
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

    public RouletteTableInteractionManager interactionManager() {
        return interactionManager;
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
