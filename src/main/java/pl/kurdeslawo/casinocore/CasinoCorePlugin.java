package pl.kurdeslawo.casinocore;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.command.CasinoCommand;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;
import pl.kurdeslawo.casinocore.economy.VaultEconomyProvider;
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
    private static final long MAX_EXACT_VAULT_INTEGER = 9_007_199_254_740_992L;
    private EconomyProvider economyProvider;
    private String activeEconomyProvider;
    private RouletteManager rouletteManager;
    private RouletteGui rouletteGui;
    private RouletteAnimation rouletteAnimation;
    private RouletteTableInteractionManager interactionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!validateRouletteConfiguration() || !initializeEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rouletteManager = new RouletteManager(economyProvider, new RouletteRandom(), stakes(),
            getConfig().getInt("roulette.payouts.outside", 2),
            getConfig().getInt("roulette.payouts.exact", 12));
        rouletteGui = new RouletteGui(rouletteManager, economyProvider);
        rouletteAnimation = new RouletteAnimation(this, rouletteManager, rouletteGui);
        interactionManager = new RouletteTableInteractionManager(this);
        interactionManager.load();

        getServer().getPluginManager().registerEvents(
            new RouletteGuiListener(this, rouletteManager, rouletteGui, rouletteAnimation), this);
        getServer().getPluginManager().registerEvents(
            new RouletteInteractionListener(interactionManager, rouletteGui), this);

        PluginCommand casinoCommand = getCommand("casino");
        if (casinoCommand == null) {
            throw new IllegalStateException("Command 'casino' is missing from plugin.yml");
        }
        CasinoCommand executor = new CasinoCommand(this);
        casinoCommand.setExecutor(executor);
        casinoCommand.setTabCompleter(executor);
        getLogger().info("CasinoCore enabled with economy provider '" + activeEconomyProvider + "'.");
    }

    @Override
    public void onDisable() {
        if (rouletteAnimation != null) {
            rouletteAnimation.cancelAll();
        }
        if (rouletteManager != null) {
            try {
                rouletteManager.settleAll();
            } catch (RuntimeException exception) {
                getLogger().severe("A pending Roulette round could not be settled during shutdown: " + exception.getMessage());
            }
        }
    }

    public boolean reloadPluginConfiguration() {
        reloadConfig();
        if (!validateRouletteConfiguration()) {
            return false;
        }
        String configuredProvider = getConfig().getString("economy.provider", "local").toLowerCase(Locale.ROOT);
        if (!configuredProvider.equals(activeEconomyProvider)) {
            getLogger().severe("Economy provider changes require a server restart. CasinoCore remains on '"
                + activeEconomyProvider + "'.");
            return false;
        }
        rouletteManager.updateConfiguration(stakes(),
            getConfig().getInt("roulette.payouts.outside", 2),
            getConfig().getInt("roulette.payouts.exact", 12));
        interactionManager.updateDimensions();
        return true;
    }

    private boolean initializeEconomy() {
        activeEconomyProvider = getConfig().getString("economy.provider", "local").toLowerCase(Locale.ROOT);
        if (activeEconomyProvider.equals("local")) {
            long startingBalance = getConfig().getLong("economy.local.starting-balance", 0L);
            economyProvider = new LocalEconomyProvider(startingBalance);
            return true;
        }
        if (!activeEconomyProvider.equals("vault")) {
            getLogger().severe("Unknown economy.provider '" + activeEconomyProvider + "'. Allowed: local, vault.");
            return false;
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("economy.provider is 'vault', but Vault is not installed. CasinoCore will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger().severe("Vault is installed, but no Economy service is registered. CasinoCore will be disabled.");
            return false;
        }
        economyProvider = new VaultEconomyProvider(registration.getProvider());
        return true;
    }

    private boolean validateRouletteConfiguration() {
        if (getConfig().getInt("roulette.numbers.min", 0) != 0
            || getConfig().getInt("roulette.numbers.max", 12) != 12) {
            getLogger().severe("This Roulette version requires roulette.numbers.min=0 and max=12.");
            return false;
        }
        List<Long> stakes = stakes();
        int outside = getConfig().getInt("roulette.payouts.outside", 2);
        int exact = getConfig().getInt("roulette.payouts.exact", 12);
        if (stakes.isEmpty() || stakes.size() > 3 || stakes.stream().anyMatch(value -> value == null || value <= 0)
            || outside <= 0 || exact <= 0) {
            getLogger().severe("Roulette stakes (1-3 positive values) and payout multipliers must be valid.");
            return false;
        }
        long maxStake = stakes.stream().mapToLong(Long::longValue).max().orElseThrow();
        if (maxStake > Long.MAX_VALUE / Math.max(outside, exact)) {
            getLogger().severe("Roulette stake and payout configuration can overflow a long value.");
            return false;
        }
        if ("vault".equalsIgnoreCase(getConfig().getString("economy.provider", "local"))
            && maxStake * Math.max(outside, exact) > MAX_EXACT_VAULT_INTEGER) {
            getLogger().severe("Vault Roulette payouts cannot exceed 2^53 coins.");
            return false;
        }
        double width = getConfig().getDouble("roulette.interaction.width", 2.75);
        double height = getConfig().getDouble("roulette.interaction.height", 1.5);
        if (width <= 0 || height <= 0) {
            getLogger().severe("Roulette interaction width and height must be positive.");
            return false;
        }
        return true;
    }

    private List<Long> stakes() {
        return getConfig().getLongList("roulette.stakes");
    }

    public EconomyProvider economyProvider() { return economyProvider; }
    public RouletteManager rouletteManager() { return rouletteManager; }
    public RouletteGui rouletteGui() { return rouletteGui; }
    public RouletteTableInteractionManager interactionManager() { return interactionManager; }
}
