package pl.kurdeslawo.casinocore.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import pl.kurdeslawo.casinocore.economy.Money;

/** Compact history entries shared by both casino games. */
public final class CasinoHistoryLore {
    private CasinoHistoryLore() {}

    public static Component entry(long stake, long payout) {
        if (payout == 0) {
            return Component.text("-" + Money.format(stake) + "₭", NamedTextColor.RED);
        }
        long net = payout - stake;
        NamedTextColor color =
                net > 0 ? NamedTextColor.GREEN : net < 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
        String balance = net > 0 ? "+" + Money.format(net) : Money.format(net);
        return Component.text(
                        "-" + Money.format(stake) + "₭ + " + Money.format(payout) + "₭ = ",
                        NamedTextColor.WHITE)
                .append(Component.text(balance + "₭", color));
    }
}
