package com.hayden.bazaarhelper;

import java.util.Locale;

/**
 * Remembers the order you just started from the panel, and decides what to type into
 * Hypixel's sign when it asks for a custom amount or price. Pure logic, no Minecraft code.
 *
 * It only ever fills in text. You press Done and confirm the order yourself.
 */
public final class QuickOrder {
    public enum Side { BUY, SELL }
    public enum Field { AMOUNT, PRICE, UNKNOWN }

    public record Active(String itemName, Side side, double price, long amount, long createdAt) {}

    /** A started order goes stale after this, so an old price never gets typed in. */
    public static final long TTL_MS = 3 * 60 * 1000;

    private volatile Active active;

    public void start(Active a) { active = a; }

    public void clear() { active = null; }

    public Active current(long now) {
        Active a = active;
        if (a == null) return null;
        if (now - a.createdAt() > TTL_MS) { active = null; return null; }
        return a;
    }

    /**
     * Works out which number a Hypixel sign is asking for from its prompt lines
     * (lines 2-4; line 1 is where you type). Unclear prompts return UNKNOWN so nothing is typed.
     */
    public static Field classify(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i] != null) sb.append(' ').append(BazaarData.stripFormatting(lines[i]).toLowerCase(Locale.US));
        }
        String p = sb.toString();
        boolean price = p.contains("price") || p.contains("per unit") || p.contains("coins");
        boolean amount = p.contains("amount") || p.contains("quantity") || p.contains("how many");
        if (price && !amount) return Field.PRICE;
        if (amount && !price) return Field.AMOUNT;
        return Field.UNKNOWN;
    }

    /** Text to put on the sign's first line, or null to leave it alone. */
    public String prefillFor(String[] lines, long now) {
        Active a = current(now);
        if (a == null || lines == null || lines.length == 0) return null;
        if (lines[0] != null && !BazaarData.stripFormatting(lines[0]).isBlank()) return null; // you already typed
        return switch (classify(lines)) {
            case PRICE -> formatPrice(a.price());
            case AMOUNT -> a.side() == Side.BUY && a.amount() > 0 ? Long.toString(a.amount()) : null;
            case UNKNOWN -> null;
        };
    }

    /**
     * Whether a menu title looks like part of the Bazaar flow, so the "Flip Panel" button
     * shows there. Errs toward showing it; tune with /bzh debug (it prints every title).
     */
    public static boolean isBazaarTitle(String title) {
        String t = BazaarData.stripFormatting(title).toLowerCase(Locale.US);
        return t.contains("bazaar") || t.contains("➜")        // "Bazaar ➜ Mining", "Mining ➜ Coal"
                || t.contains("buy order") || t.contains("sell offer")
                || t.contains("how many") || t.contains("how much")
                || t.contains("at what price") || t.contains("your orders")
                || t.contains("confirm") && (t.contains("order") || t.contains("offer"));
    }

    /**
     * Which menu slot to highlight for the order in progress, so you just click the glowing
     * slot at each step. Checks the latest step first, since earlier labels can still be on
     * screen. Returns the index into {@code names} (menu slots only), or -1 for none.
     *
     * Buy:  search results (the item) > item page (Create Buy Order) > How many (Custom Amount)
     *       > How much (Custom Price) > Confirm.
     * Sell: search results > item page (Create Sell Offer) > price screen (Custom Price) > Confirm.
     */
    public static int pickSlot(String title, java.util.List<String> names, Active a) {
        if (a == null || names == null) return -1;
        String t = BazaarData.stripFormatting(title == null ? "" : title).toLowerCase(Locale.US);
        boolean buy = a.side() == Side.BUY;

        if (t.contains("confirm")) {
            int i = find(names, buy ? "buy order" : "sell offer", false);
            return i >= 0 ? i : find(names, "confirm", false);
        }
        int i = find(names, "custom price", false);
        if (i >= 0) return i;
        if (buy) {
            i = find(names, "custom amount", false);
            if (i >= 0) return i;
        }
        i = find(names, buy ? "create buy order" : "create sell offer", false);
        if (i >= 0) return i;
        // Search results: the item itself. Exact name so "Enchanted X" isn't picked for "X".
        return find(names, a.itemName().toLowerCase(Locale.US), true);
    }

    private static int find(java.util.List<String> names, String needle, boolean exact) {
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (n == null) continue;
            n = BazaarData.stripFormatting(n).toLowerCase(Locale.US).trim();
            if (exact ? n.equals(needle) : n.contains(needle)) return i;
        }
        return -1;
    }

    /** 130.4 -> "130.4", 12345.0 -> "12345.0". No commas, since the sign wants a plain number. */
    public static String formatPrice(double price) {
        return String.format(Locale.US, "%.1f", FlipMath.round1(price));
    }
}
