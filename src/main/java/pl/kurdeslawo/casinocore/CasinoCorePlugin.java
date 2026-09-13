package pl.kurdeslawo.casinocore;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.command.CasinoCommand;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;

import java.util.List;

public final class CasinoCorePlugin extends JavaPlugin {
    private EconomyProvider economyProvider;
    private RouletteManager rouletteManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadServices();

        PluginCommand casinoCommand = getCommand("casino");
        if (casinoCommand == null) {
            throw new IllegalStateException("Command 'casino' is missing from plugin.yml");
        }
        CasinoCommand executor = new CasinoCommand(this);
        casinoCommand.setExecutor(executor);
        casinoCommand.setTabCompleter(executor);

        getLogger().info("CasinoCore enabled with economy provider '"
            + getConfig().getString("economy.provider", "local") + "'.");
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        loadServices();
    }

    private void loadServices() {
        String providerName = getConfig().getString("economy.provider", "local");
        if (!"local".equalsIgnoreCase(providerName)) {
            throw new IllegalStateException("Unsupported economy provider: " + providerName);
        }

        long startingBalance = getConfig().getLong("economy.local.starting-balance", 0L);
        this.economyProvider = new LocalEconomyProvider(startingBalance);

        List<Long> stakes = getConfig().getLongList("roulette.stakes");
        if (rouletteManager == null) {
            rouletteManager = new RouletteManager(stakes);
        } else {
            rouletteManager.setAllowedStakes(stakes);
        }
    }

    public EconomyProvider economyProvider() {
        return economyProvider;
    }

    public RouletteManager rouletteManager() {
        return rouletteManager;
    }
}
