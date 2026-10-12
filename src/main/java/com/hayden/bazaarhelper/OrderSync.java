package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads your real orders off Hypixel's "Your Bazaar Orders" menu (Manage Orders), so the board
 * knows exactly what's live instead of guessing from chat. Pure logic: the Minecraft side hands
 * it each menu item's name and tooltip lines. Read-only.
 *
 * An order item looks like:
 *   BUY Broken Radar
 *     Order amount: 182x
 *     Filled: 50/182 27%
 *     Price per unit: 461,188.0 coins
 *     You have 50 items to claim!
 */
public final class OrderSync {
    private OrderSync() {}

    public record Order(QuickOrder.Side side, String itemName, long amount, long filled, double price, boolean claimable) {
        /** Units still waiting to fill. */
        public long open() {
            return Math.max(0, amount - filled);
        }
    }

    private static final Pattern AMOUNT = Pattern.compile("(?:Order|Offer) amount:\\s*([\\d,.]+[kKmM]?)x");
    private static final Pattern FILLED = Pattern.compile("Filled:\\s*([\\d,.]+[kKmM]?)\\s*/\\s*([\\d,.]+[kKmM]?)");
    private static final Pattern PRICE = Pattern.compile("Price per unit:\\s*([\\d,.]+)\\s*coins?");

    /** True for the Manage Orders menu (yours or co-op's). */
    public static boolean isOrdersTitle(String title) {
        String t = BazaarData.stripFormatting(title == null ? "" : title).toLowerCase(Locale.US);
        return t.contains("bazaar orders");
    }

    /** One menu item, or null if it isn't an order. */
    public static Order parse(String name, List<String> lore) {
        if (name == null || lore == null) return null;
        String n = clean(name);
        QuickOrder.Side side;
        String item;
        if (n.regionMatches(true, 0, "BUY ", 0, 4)) { side = QuickOrder.Side.BUY; item = n.substring(4).trim(); }
        else if (n.regionMatches(true, 0, "SELL ", 0, 5)) { side = QuickOrder.Side.SELL; item = n.substring(5).trim(); }
        else return null;

        long amount = -1, filled = 0;
        double price = -1;
        boolean claimable = false;
        for (String raw : lore) {
            String l = clean(raw);
            Matcher m;
            if ((m = AMOUNT.matcher(l)).find()) amount = (long) FlipMath.parseAmount(m.group(1));
            if ((m = FILLED.matcher(l)).find()) {
                filled = (long) FlipMath.parseAmount(m.group(1));
                if (amount < 0) amount = (long) FlipMath.parseAmount(m.group(2));
            }
            if ((m = PRICE.matcher(l)).find()) price = Double.parseDouble(m.group(1).replace(",", ""));
            if (l.toLowerCase(Locale.US).contains("to claim")) claimable = true;
        }
        if (amount <= 0 || price <= 0 || item.isEmpty()) return null;
        return new Order(side, item, amount, Math.min(filled, amount), price, claimable);
    }

    /** Every order in the menu. Menu items that aren't orders are skipped. */
    public static List<Order> parseAll(List<String> names, List<List<String>> lores) {
        List<Order> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Order o = parse(names.get(i), i < lores.size() ? lores.get(i) : null);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** Whether the menu still shows an open order for this item and side. */
    public static boolean hasOpen(List<Order> orders, String itemName, QuickOrder.Side side) {
        for (Order o : orders) {
            if (o.side() == side && o.open() > 0 && o.itemName().equalsIgnoreCase(itemName.trim())) return true;
        }
        return false;
    }

    private static String clean(String s) {
        return BazaarData.stripFormatting(s).replaceAll("[^\\x20-\\x7E]", "").trim();
    }
}
