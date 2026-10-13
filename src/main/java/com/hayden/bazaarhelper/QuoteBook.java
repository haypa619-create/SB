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
        /** When the live bid / ask went up (for time-to-fill). */
        public long bidSetAt, askSetAt;
        /** Fills seen and total seconds they took from setup. */
        public long fills;
        public double fillSecs;
        public double boughtUnits, soldUnits;
        /** How many separate orders make up the live bid / ask (Hypixel caps total orders). */
        public int bidOrders, askOrders;
    }

    public enum EventKind { SETUP_BUY, SETUP_SELL, FILLED_BUY, FILLED_SELL, CLAIM_BUY, CLAIM_SELL, CANCEL_BUY, CANCEL_SELL, REJECTED,
        INSTA_BUY, INSTA_SELL }

    /** What the last chat line did, for alerts and the journal. Not saved. */
    public record Event(EventKind kind, String item, double units, double price, double pnl, double fillSecs) {}

    public transient Event lastEvent;

    /**
     * Items owned by something else (open trades). Their chat lines are reported in lastEvent
     * but never touch market-making quotes, cost basis or P&L, so the two books never mix.
     */
    public transient java.util.function.Predicate<String> notMine;

    /** Keyed by lowercase item name, as it appears in chat. */
    public Map<String, Item> items = new LinkedHashMap<>();
    public double realizedTotal = 0;
    public long since = System.currentTimeMillis();
    /** Realized P&L by UTC day ("2026-10-12"). */
    public Map<String, Double> dailyRealized = new LinkedHashMap<>();
    /** Coins put into new orders by UTC day, for the daily limit. */
    public Map<String, Double> dailyVolume = new LinkedHashMap<>();
    /** Filled orders not claimed yet: item name -> units. */
    public Map<String, Double> toClaim = new LinkedHashMap<>();
    /** When the Manage Orders menu last synced the live quotes (0 = never). */
    public long syncedAt = 0;

    // [Bazaar] Buy Order Setup! 160x Enchanted Coal for 20,832.0 coins.
    static final Pattern SETUP = Pattern.compile(
            "^\\[Bazaar\\] (Buy Order|Sell Offer) Setup! ([\\d,]+)x (.+?) for ([\\d,.]+) coins?\\.?$");
    // [Bazaar] Your Buy Order for 160x Enchanted Coal was filled!
    static final Pattern FILLED = Pattern.compile(
            "^\\[Bazaar\\] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+?) was filled!.*$");
    // [Bazaar] Cancelled! Refunded 64x Enchanted Coal from cancelling Sell Offer!
    static final Pattern CANCELLED_SELL = Pattern.compile(
            "^\\[Bazaar\\] Cancelled! Refunded ([\\d,]+)x (.+?) from cancelling Sell Offer!.*$");
    // [Bazaar] Cancelled! Refunded 20,832.0 coins from cancelling Buy Order!  (doesn't name the item)
    static final Pattern CANCELLED_BUY = Pattern.compile(
            "^\\[Bazaar\\] Cancelled! Refunded ([\\d,.]+) coins? from cancelling Buy Order!.*$");
    // [Bazaar] Your price isn't competitive enough with the best order/offer's price.
    static final Pattern REJECTED = Pattern.compile("^\\[Bazaar\\] Your price isn't competitive enough.*$");
    // [Bazaar] Bought 64x Enchanted Coal for 8,960 coins!   (insta-buy)
    static final Pattern INSTA_BUY = Pattern.compile("^\\[Bazaar\\] Bought ([\\d,]+)x (.+?) for ([\\d,.]+) coins?!?.*$");
    // [Bazaar] Sold 64x Enchanted Coal for 8,800 coins!   (insta-sell; coins received)
    static final Pattern INSTA_SELL = Pattern.compile("^\\[Bazaar\\] Sold ([\\d,]+)x (.+?) for ([\\d,.]+) coins?!?.*$");
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
        return onChat(raw, taxPct, System.currentTimeMillis());
    }

    /**
     * What a Bazaar chat line says, without changing anything. Price is per unit
     * (for insta-sells, coins received per unit).
     */
    public static Event parse(String raw) {
        String line = BazaarData.stripFormatting(raw).trim();
        Matcher m;
        if ((m = SETUP.matcher(line)).matches()) {
            double u = num(m.group(2));
            return u <= 0 ? null : new Event(m.group(1).startsWith("Buy") ? EventKind.SETUP_BUY : EventKind.SETUP_SELL,
                    m.group(3), u, num(m.group(4)) / u, 0, 0);
        }
        if ((m = FILLED.matcher(line)).matches())
            return new Event(m.group(1).startsWith("Buy") ? EventKind.FILLED_BUY : EventKind.FILLED_SELL, m.group(3), num(m.group(2)), 0, 0, 0);
        if ((m = CANCELLED_SELL.matcher(line)).matches()) return new Event(EventKind.CANCEL_SELL, m.group(2), num(m.group(1)), 0, 0, 0);
        if ((m = CANCELLED_BUY.matcher(line)).matches()) return new Event(EventKind.CANCEL_BUY, null, 0, num(m.group(1)), 0, 0);
        if (REJECTED.matcher(line).matches()) return new Event(EventKind.REJECTED, null, 0, 0, 0, 0);
        if ((m = CLAIM_BUY.matcher(line)).matches()) return new Event(EventKind.CLAIM_BUY, m.group(2), num(m.group(1)), num(m.group(4)), 0, 0);
        if ((m = CLAIM_SELL.matcher(line)).matches()) return new Event(EventKind.CLAIM_SELL, m.group(3), num(m.group(2)), num(m.group(4)), 0, 0);
        if ((m = INSTA_BUY.matcher(line)).matches()) {
            double u = num(m.group(1));
            return u <= 0 ? null : new Event(EventKind.INSTA_BUY, m.group(2), u, num(m.group(3)) / u, 0, 0);
        }
        if ((m = INSTA_SELL.matcher(line)).matches()) {
            double u = num(m.group(1));
            return u <= 0 ? null : new Event(EventKind.INSTA_SELL, m.group(2), u, num(m.group(3)) / u, 0, 0);
        }
        return null;
    }

    public String onChat(String raw, double taxPct, long now) {
        lastEvent = null;
        String line = BazaarData.stripFormatting(raw).trim();
        Matcher m;
        if (dailyRealized == null) dailyRealized = new LinkedHashMap<>();
        if (dailyVolume == null) dailyVolume = new LinkedHashMap<>();
        if (toClaim == null) toClaim = new LinkedHashMap<>();

        Event pe = parse(line);
        if (pe != null && (pe.kind() == EventKind.SETUP_BUY || pe.kind() == EventKind.SETUP_SELL
                || pe.kind() == EventKind.INSTA_BUY || pe.kind() == EventKind.INSTA_SELL)) {
            dailyVolume.merge(day(now), pe.units() * pe.price(), Double::sum); // the daily limit is account-wide
        }
        // Insta trades and other owners' items: report only, never touch the market-making book.
        if (pe != null && (pe.kind() == EventKind.INSTA_BUY || pe.kind() == EventKind.INSTA_SELL
                || pe.item() != null && notMine != null && notMine.test(pe.item()))) {
            lastEvent = pe;
            return null;
        }

        if ((m = SETUP.matcher(line)).matches()) {
            double units = num(m.group(2));
            if (units <= 0) return null;
            double unit = num(m.group(4)) / units;
            Item it = getOrCreate(m.group(3));
            boolean buy = m.group(1).startsWith("Buy");
            lastEvent = new Event(buy ? EventKind.SETUP_BUY : EventKind.SETUP_SELL, m.group(3), units, unit, 0, 0);
            if (buy) {
                it.bidPrice = unit;
                it.bidOrders = it.bidUnits > 0 ? it.bidOrders + 1 : 1;
                it.bidUnits = units;
                it.bidSetAt = now;
                return "Bid up: " + fmtUnits(units) + " " + m.group(3) + " @ " + FlipMath.fmt(unit);
            } else {
                it.askPrice = unit;
                it.askOrders = it.askUnits > 0 ? it.askOrders + 1 : 1;
                it.askUnits = units;
                it.askSetAt = now;
                return "Ask up: " + fmtUnits(units) + " " + m.group(3) + " @ " + FlipMath.fmt(unit);
            }
        }
        if ((m = FILLED.matcher(line)).matches()) {
            double units = num(m.group(2));
            boolean buy = m.group(1).startsWith("Buy");
            toClaim.merge(key(m.group(3)), units, Double::sum);
            Item it = getOrCreate(m.group(3));
            long setAt = buy ? it.bidSetAt : it.askSetAt;
            double secs = setAt > 0 && now > setAt ? (now - setAt) / 1000.0 : 0;
            if (secs > 0) { it.fills++; it.fillSecs += secs; }
            lastEvent = new Event(buy ? EventKind.FILLED_BUY : EventKind.FILLED_SELL, m.group(3), units,
                    buy ? it.bidPrice : it.askPrice, 0, secs);
            if (buy) { it.bidUnits = 0; it.bidSetAt = 0; it.bidOrders = 0; return "Bid filled: " + m.group(3) + ". Claim it, then list the ask."; }
            it.askUnits = 0;
            it.askSetAt = 0;
            it.askOrders = 0;
            return "Ask filled: " + m.group(3) + ". Claim the coins.";
        }
        if ((m = CANCELLED_SELL.matcher(line)).matches()) {
            Item it = get(m.group(2));
            if (it != null) { it.askUnits = 0; it.askSetAt = 0; it.askOrders = 0; }
            lastEvent = new Event(EventKind.CANCEL_SELL, m.group(2), num(m.group(1)), 0, 0, 0);
            return null;
        }
        if (REJECTED.matcher(line).matches()) {
            lastEvent = new Event(EventKind.REJECTED, null, 0, 0, 0, 0);
            return null;
        }
        if ((m = CANCELLED_BUY.matcher(line)).matches()) {
            lastEvent = new Event(EventKind.CANCEL_BUY, null, 0, num(m.group(1)), 0, 0);
            return null;
        }
        if ((m = CLAIM_BUY.matcher(line)).matches()) {
            double units = num(m.group(1));
            double price = num(m.group(4));
            Item it = getOrCreate(m.group(2));
            double newUnits = it.costUnits + units;
            it.avgCost = newUnits > 0 ? (it.avgCost * it.costUnits + price * units) / newUnits : 0;
            it.costUnits = newUnits;
            it.boughtUnits += units;
            claimed(m.group(2), units);
            lastEvent = new Event(EventKind.CLAIM_BUY, m.group(2), units, price, 0, 0);
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
            it.soldUnits += units;
            realizedTotal += pnl;
            dailyRealized.merge(day(now), pnl, Double::sum);
            claimed(m.group(3), units);
            lastEvent = new Event(EventKind.CLAIM_SELL, m.group(3), units, price, pnl, 0);
            return "Realized " + (pnl >= 0 ? "+" : "") + FlipMath.fmt(pnl) + " on " + m.group(3)
                    + "  (desk total " + (realizedTotal >= 0 ? "+" : "") + FlipMath.fmt(realizedTotal) + ")";
        }
        return null;
    }

    private void claimed(String itemName, double units) {
        String k = key(itemName);
        Double left = toClaim.get(k);
        if (left == null) return;
        if (left - units <= 0.5) toClaim.remove(k); else toClaim.put(k, left - units);
    }

    /**
     * Makes the live quotes match the Manage Orders menu exactly. Everything not in the menu is
     * treated as not live. Returns a one-line summary.
     */
    public String syncFrom(java.util.List<OrderSync.Order> orders, long now) {
        if (toClaim == null) toClaim = new LinkedHashMap<>();
        java.util.Set<String> seenBid = new java.util.HashSet<>(), seenAsk = new java.util.HashSet<>();
        Map<String, Double> claim = new LinkedHashMap<>();
        int live = 0;
        for (OrderSync.Order o : orders) {
            Item it = getOrCreate(o.itemName());
            String k = key(o.itemName());
            if (o.filled() > 0 && o.claimable()) claim.merge(k, (double) o.filled(), Double::sum);
            if (o.open() <= 0) continue;
            live++;
            if (o.side() == QuickOrder.Side.BUY) {
                boolean first = seenBid.add(k);
                if (first && it.bidUnits <= 0) it.bidSetAt = 0; // wasn't live before: start the fill clock now
                it.bidUnits = (first ? 0 : it.bidUnits) + o.open();
                it.bidOrders = first ? 1 : it.bidOrders + 1;
                it.bidPrice = first ? o.price() : Math.max(it.bidPrice, o.price()); // best bid counts
                if (it.bidSetAt == 0) it.bidSetAt = now;
            } else {
                boolean first = seenAsk.add(k);
                if (first && it.askUnits <= 0) it.askSetAt = 0;
                it.askUnits = (first ? 0 : it.askUnits) + o.open();
                it.askOrders = first ? 1 : it.askOrders + 1;
                it.askPrice = first ? o.price() : Math.min(it.askPrice, o.price());
                if (it.askSetAt == 0) it.askSetAt = now;
            }
        }
        for (Map.Entry<String, Item> e : items.entrySet()) {
            if (!seenBid.contains(e.getKey())) { e.getValue().bidUnits = 0; e.getValue().bidSetAt = 0; e.getValue().bidOrders = 0; }
            if (!seenAsk.contains(e.getKey())) { e.getValue().askUnits = 0; e.getValue().askSetAt = 0; e.getValue().askOrders = 0; }
        }
        toClaim = claim;
        syncedAt = now;
        return "Synced " + live + " live order" + (live == 1 ? "" : "s") + " from Manage Orders"
                + (claim.isEmpty() ? "" : ", " + claim.size() + " to claim");
    }

    /** Live orders the desk knows about (each live bid or ask counts as one). */
    public int liveOrders() {
        int n = 0;
        for (Item it : items.values()) {
            if (it.bidUnits > 0) n += Math.max(1, it.bidOrders);
            if (it.askUnits > 0) n += Math.max(1, it.askOrders);
        }
        return n;
    }

    public double realizedOn(String day) {
        return dailyRealized == null ? 0 : dailyRealized.getOrDefault(day, 0.0);
    }

    public double volumeOn(String day) {
        return dailyVolume == null ? 0 : dailyVolume.getOrDefault(day, 0.0);
    }

    /** Realized over the last n UTC days including today. */
    public double realizedLastDays(int n, long now) {
        double sum = 0;
        for (int i = 0; i < n; i++) sum += realizedOn(day(now - i * 86_400_000L));
        return sum;
    }

    public static String day(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
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
        if (it != null) { it.bidUnits = 0; it.askUnits = 0; it.bidSetAt = 0; it.askSetAt = 0; it.bidOrders = 0; it.askOrders = 0; }
    }

    public void clearAsk(String itemName) {
        Item it = get(itemName);
        if (it != null) { it.askUnits = 0; it.askSetAt = 0; it.askOrders = 0; }
    }

    public void clearBid(String itemName) {
        Item it = get(itemName);
        if (it != null) { it.bidUnits = 0; it.bidSetAt = 0; it.bidOrders = 0; }
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
