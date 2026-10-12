package com.hayden.bazaarhelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the coins you're market making with, so nothing is hard-coded.
 * Pure logic: the Minecraft side hands it the sidebar lines.
 *
 *   capital = purse + coins locked in your live buy orders + stock you hold (at the top bid)
 *
 * Stock counts because the book plans stock and bids together out of one pot.
 */
public final class Wallet {
    private Wallet() {}

    // "Purse: 590,136,337", "Purse: 590,136,337 (+1,250)", "Piggy: 12,345" (Piggy Bank shows it on death-risk areas)
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+(?:\\.\\d+)?)");

    /** Purse from the SkyBlock sidebar, or -1 if it isn't showing. */
    public static double parsePurse(List<String> sidebarLines) {
        if (sidebarLines == null) return -1;
        for (String raw : sidebarLines) {
            if (raw == null) continue;
            // Hypixel pads lines with color codes and emoji so each one is unique; drop all of that.
            String line = BazaarData.stripFormatting(raw).replaceAll("[^\\x20-\\x7E]", "");
            Matcher m = PURSE.matcher(line);
            if (m.find()) {
                try {
                    return Double.parseDouble(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static double capital(double purse, double lockedInBids, double stockValue) {
        return Math.max(0, purse) + Math.max(0, lockedInBids) + Math.max(0, stockValue);
    }
}
