package com.hayden.bazaarhelper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your desk's book, rebuilt from Hypixel's Bazaar chat messages. Pure logic, Gson-serializable.
 *
 *  - Live quotes: the buy order and sell offer you have up on each item
 *  - Cost basis: average cost of stock bought through buy orders
 *  - Realized P&L: spread captured on stock you've sold, after tax
 *
 * Read-only: it listens to chat and never touches your orders. If a message format differs
 * from what's below, /bzh debug prints the raw lines so the patterns can be fixed.
 */
public final class QuoteBook {
    public static final class Item {
        public double bidUnits, bidPrice, askUnits, askPrice;
        public double costUnits, avgCost, realized, unknownCostSold;
        public long roundTrips;
    }

    /** Keyed by lowercase item name, as it appears in chat. */
    public Map<String, Item> items = new LinkedHashMap<>();
    public double realizedTotal = 0;
    public long since = System.currentTimeMillis();

    // [Bazaar] Buy Order Setup! 160x Enchanted Coal for 20,832.0 coins.
    static final Pattern SETUP = Pattern.compile(
            "^\\[Bazaar\\] (Buy Order|Sell Offer) Setup! ([\\d,]+)x (.+?) for ([\\d,.]+) coins?\\.?$");
    // [Bazaar] Your Buy Order for 160x Enchanted Coal was filled!
    static final Pattern FILLED = Pattern.compile(
            "^\\[Bazaar\\] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+?) was filled!.*$");
    // [Bazaar] Cancelled! Refunded 64x Enchanted Coal from cancelling Sell Offer!
    static final Pattern CANCELLED_SELL = Pattern.compile(
            "^\\[Bazaar\\] Cancelled! Refunded ([\\d,]+)x (.+?) from cancelling Sell Offer!.*$");
    // [Bazaar] Claimed 160x Enchanted Coal worth 20,832.0 coins bought for 130.2 each!
    static final Pattern CLAIM_BUY = Pattern.compile(
            "^\\[Bazaar\\] Claimed ([\\d,]+)x (.+?) worth ([\\d,.]+) coins? bought for ([\\d,.]+) each!.*$");
    // [Bazaar] Claimed 8,842.4 coins from selling 64x Enchanted Coal at 139.9 each!
    static final Pattern CLAIM_SELL = Pattern.compile(
            "^\\[Bazaar\\] Claimed ([\\d,.]+) coins? from selling ([\\d,]+)x (.+?) at ([\\d,.]+) each!.*$");

    public Item get(String itemName) {
        return items.get(key(itemName));
    }

    private Item getOrCreate(String itemName) {
        return items.computeIfAbsent(key(itemName), k -> new Item());
    }

    /** Feed every chat line. Returns a short note when something changed, else null. */
    public String onChat(String raw, double taxPct) {
        String line = BazaarData.stripFormatting(raw).trim();
        Matcher m;

        if ((m = SETUP.matcher(line)).matches()) {
            double units = num(m.group(2));
            if (units <= 0) return null;
            double unit = num(m.group(4)) / units;
            Item it = getOrCreate(m.group(3));
            if (m.group(1).startsWith("Buy")) {
                it.bidPrice = unit;
                it.bidUnits = units;
                return "Bid up: " + fmtUnits(units) + " " + m.group(3) + " @ " + FlipMath.fmt(unit);
            } else {
                it.askPrice = unit;
                it.askUnits = units;
                return "Ask up: " + fmtUnits(units) + " " + m.group(3) + " @ " + FlipMath.fmt(unit);
            }
        }
        if ((m = FILLED.matcher(line)).matches()) {
            Item it = get(m.group(3));
            if (it == null) return null;
            if (m.group(1).startsWith("Buy")) { it.bidUnits = 0; return "Bid filled: " + m.group(3) + ". Claim it, then list the ask."; }
            it.askUnits = 0;
            return "Ask filled: " + m.group(3) + ". Claim the coins.";
        }
        if ((m = CANCELLED_SELL.matcher(line)).matches()) {
            Item it = get(m.group(2));
            if (it != null) it.askUnits = 0;
            return null;
        }
        if ((m = CLAIM_BUY.matcher(line)).matches()) {
            double units = num(m.group(1));
            double price = num(m.group(4));
            Item it = getOrCreate(m.group(2));
            double newUnits = it.costUnits + units;
            it.avgCost = newUnits > 0 ? (it.avgCost * it.costUnits + price * units) / newUnits : 0;
            it.costUnits = newUnits;
            return null;
        }
        if ((m = CLAIM_SELL.matcher(line)).matches()) {
            double units = num(m.group(2));
            double price = num(m.group(4));
            Item it = getOrCreate(m.group(3));
            double withCost = Math.min(units, it.costUnits);
            double pnl = withCost * (price * (1 - taxPct / 100.0) - it.avgCost);
            it.unknownCostSold += units - withCost;
            it.costUnits -= withCost;
            if (it.costUnits <= 1e-9) { it.costUnits = 0; it.avgCost = 0; }
            it.realized += pnl;
            it.roundTrips++;
            realizedTotal += pnl;
            return "Realized " + (pnl >= 0 ? "+" : "") + FlipMath.fmt(pnl) + " on " + m.group(3)
                    + "  (desk total " + (realizedTotal >= 0 ? "+" : "") + FlipMath.fmt(realizedTotal) + ")";
        }
        return null;
    }

    /** Unrealized P&L on stock with a known cost, marked at the mid price. */
    public static double markToMarket(Item it, BazaarData.Product p) {
        if (it == null || p == null || it.costUnits <= 0 || p.topBid() <= 0 || p.topAsk() <= 0) return 0;
        double mid = (p.topBid() + p.topAsk()) / 2;
        return it.costUnits * (mid - it.avgCost);
    }

    /** Forget live quotes for an item (e.g. after cancelling a buy order, which chat doesn't name). */
    public void clearQuotes(String itemName) {
        Item it = get(itemName);
        if (it != null) { it.bidUnits = 0; it.askUnits = 0; }
    }

    public void resetPnl() {
        for (Item it : items.values()) { it.realized = 0; it.roundTrips = 0; it.unknownCostSold = 0; }
        realizedTotal = 0;
        since = System.currentTimeMillis();
    }

    static String key(String name) {
        return BazaarData.stripFormatting(name).trim().toLowerCase(Locale.US);
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String fmtUnits(double u) {
        return String.format(Locale.US, "%,dx", (long) u);
    }
}
