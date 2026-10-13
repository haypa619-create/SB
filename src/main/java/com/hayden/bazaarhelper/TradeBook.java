package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Directional trades, kept apart from market making. Pure logic, Gson-serializable.
 *
 * One owner per item: while a trade on an item is active, that item is out of the market-making
 * book, and its fills, stock and P&L belong only to the trade. Close the trade and the item can go
 * back in the book.
 *
 * A trade: buy (with a buy order at a price, or insta-buy), hold, then sell at the target with a
 * sell offer, or insta-sell when the stop is hit.
 *
 *   PLANNED  -> ENTERING (buy order up) -> OPEN (holding) -> EXITING (sell offer up) -> CLOSED
 */
public final class TradeBook {
    public enum Status { PLANNED, ENTERING, OPEN, EXITING, CLOSED }
    public enum Entry { ORDER, INSTA }

    public static final class Trade {
        public long id;
        public String itemId, name;
        public Status status = Status.PLANNED;
        public Entry entry = Entry.ORDER;
        /** Units you plan to buy. */
        public long plannedUnits;
        public double entryPrice, target, stop;
        /** Units held (in inventory or in the exit offer) and their average cost. */
        public double units, avgCost;
        /** Units still waiting in the entry buy order / exit sell offer. */
        public double pendingBuy, pendingSell;
        public double realized;
        public long openedAt, closedAt;

        public boolean active() {
            return status != Status.CLOSED;
        }

        /** Unrealized P&L if sold to the top bid now (after tax). */
        public double unrealized(double topBid, double taxPct) {
            return units <= 0 || topBid <= 0 ? 0 : units * (topBid * (1 - taxPct / 100.0) - avgCost);
        }

        public boolean stopHit(double topBid) {
            return units > 0 && stop > 0 && topBid > 0 && topBid <= stop;
        }

        public boolean targetHit(double topBid) {
            return units > 0 && target > 0 && topBid >= target;
        }

        /** Open orders this trade uses (counts toward Hypixel's cap). */
        public int liveOrders() {
            return (pendingBuy > 0 ? 1 : 0) + (pendingSell > 0 ? 1 : 0);
        }
    }

    public List<Trade> trades = new ArrayList<>();
    public long nextId = 1;
    public double realizedTotal = 0;
    public Map<String, Double> dailyRealized = new LinkedHashMap<>();

    public void fixAfterLoad() {
        if (trades == null) trades = new ArrayList<>();
        if (dailyRealized == null) dailyRealized = new LinkedHashMap<>();
    }

    // ---------- lookups ----------

    /** The active trade on this item (by name, as chat shows it), or null. */
    public Trade activeFor(String itemName) {
        if (itemName == null) return null;
        String k = QuoteBook.key(itemName);
        for (Trade t : trades) if (t.active() && QuoteBook.key(t.name).equals(k)) return t;
        return null;
    }

    public Trade activeForId(String itemId) {
        for (Trade t : trades) if (t.active() && t.itemId.equals(itemId)) return t;
        return null;
    }

    public Trade byId(long id) {
        for (Trade t : trades) if (t.id == id) return t;
        return null;
    }

    public List<Trade> active() {
        List<Trade> out = new ArrayList<>();
        for (Trade t : trades) if (t.active()) out.add(t);
        return out;
    }

    public List<Trade> closed() {
        List<Trade> out = new ArrayList<>();
        for (Trade t : trades) if (!t.active()) out.add(t);
        return out;
    }

    public int liveOrders() {
        int n = 0;
        for (Trade t : trades) if (t.active()) n += t.liveOrders();
        return n;
    }

    // ---------- lifecycle ----------

    /** Plans a trade. Refuses (returns null) if the item already has an active trade. */
    public Trade plan(String itemId, String name, long units, Entry entry, double entryPrice,
                      double target, double stop, long now) {
        if (activeForId(itemId) != null || units <= 0) return null;
        Trade t = new Trade();
        t.id = nextId++;
        t.itemId = itemId;
        t.name = name;
        t.plannedUnits = units;
        t.entry = entry;
        t.entryPrice = entryPrice;
        t.target = target;
        t.stop = stop;
        t.openedAt = now;
        trades.add(t);
        return t;
    }

    /** Close by hand (you sold elsewhere, or gave up). Keeps what was realized. */
    public void close(Trade t, long now) {
        t.status = Status.CLOSED;
        t.closedAt = now;
        t.pendingBuy = 0;
        t.pendingSell = 0;
    }

    /** Drop a trade that never bought anything. */
    public boolean delete(Trade t) {
        if (t.units > 0 || t.realized != 0) return false;
        return trades.remove(t);
    }

    /**
     * Feeds a Bazaar event. Only events for an item with an active trade count.
     * Returns a short note when the trade changed, else null.
     */
    public String onEvent(QuoteBook.Event e, double taxPct, long now) {
        if (e == null || e.item() == null) return null;
        Trade t = activeFor(e.item());
        if (t == null) return null;
        switch (e.kind()) {
            case SETUP_BUY -> {
                t.pendingBuy += e.units();
                if (t.status == Status.PLANNED) t.status = Status.ENTERING;
                return "Trade " + t.name + ": buy order up for " + FlipMath.fmt(e.units()) + " @ " + FlipMath.fmt(e.price());
            }
            case CLAIM_BUY, INSTA_BUY -> {
                buy(t, e.units(), e.price());
                if (e.kind() == QuoteBook.EventKind.CLAIM_BUY) t.pendingBuy = Math.max(0, t.pendingBuy - e.units());
                if (t.status == Status.PLANNED || t.status == Status.ENTERING) t.status = Status.OPEN;
                return String.format(Locale.US, "Trade %s: holding %s @ %s (target %s, stop %s)", t.name,
                        FlipMath.fmt(t.units), FlipMath.fmt(t.avgCost), FlipMath.fmt(t.target), FlipMath.fmt(t.stop));
            }
            case SETUP_SELL -> {
                t.pendingSell += e.units();
                t.status = Status.EXITING;
                return "Trade " + t.name + ": sell offer up @ " + FlipMath.fmt(e.price());
            }
            case CANCEL_SELL -> {
                t.pendingSell = 0;
                if (t.status == Status.EXITING) t.status = Status.OPEN;
                return null;
            }
            case CLAIM_SELL -> {
                String note = sell(t, e.units(), e.price() * (1 - taxPct / 100.0), now);
                t.pendingSell = Math.max(0, t.pendingSell - e.units());
                return note;
            }
            case INSTA_SELL -> {
                return sell(t, e.units(), e.price(), now); // insta-sell chat shows coins received
            }
            default -> {
                return null;
            }
        }
    }

    private static void buy(Trade t, double units, double price) {
        double n = t.units + units;
        t.avgCost = n > 0 ? (t.avgCost * t.units + price * units) / n : 0;
        t.units = n;
    }

    /** @param netPrice coins received per unit, after tax */
    private String sell(Trade t, double units, double netPrice, long now) {
        double sold = Math.min(units, t.units);
        double pnl = sold * (netPrice - t.avgCost);
        t.units -= sold;
        t.realized += pnl;
        realizedTotal += pnl;
        dailyRealized.merge(QuoteBook.day(now), pnl, Double::sum);
        String note = String.format(Locale.US, "Trade %s: sold %s, %s%s", t.name, FlipMath.fmt(sold),
                pnl >= 0 ? "+" : "", FlipMath.fmt(pnl));
        if (t.units <= 0.5 && t.pendingBuy <= 0) {
            t.units = 0;
            t.status = Status.CLOSED;
            t.closedAt = now;
            t.pendingSell = 0;
            note += String.format(Locale.US, ". Closed, %s%s total.", t.realized >= 0 ? "+" : "", FlipMath.fmt(t.realized));
        }
        return note;
    }

    /** Manage Orders shows the real entry / exit orders for traded items. */
    public void syncFrom(List<OrderSync.Order> orders) {
        for (Trade t : active()) {
            double buy = 0, sell = 0;
            for (OrderSync.Order o : orders) {
                if (!QuoteBook.key(o.itemName()).equals(QuoteBook.key(t.name))) continue;
                if (o.side() == QuickOrder.Side.BUY) buy += o.open(); else sell += o.open();
            }
            t.pendingBuy = buy;
            t.pendingSell = sell;
            if (t.status == Status.EXITING && sell <= 0) t.status = t.units > 0 ? Status.OPEN : t.status;
            if (t.status == Status.ENTERING && buy <= 0 && t.units <= 0) t.status = Status.PLANNED;
        }
    }

    public double realizedOn(String day) {
        return dailyRealized == null ? 0 : dailyRealized.getOrDefault(day, 0.0);
    }

    /** Units held across active trades for an item id (so market making doesn't count them). */
    public double unitsHeld(String itemId) {
        Trade t = activeForId(itemId);
        return t == null ? 0 : t.units;
    }

    /** Target / stop as prices from % moves off the entry price. */
    public static double pctPrice(double entry, double pct) {
        return FlipMath.round1(entry * (1 + pct / 100.0));
    }

    /** Reward : risk after tax, using target and stop. 0 if undefined. */
    public static double rewardRisk(double entry, double target, double stop, double taxPct) {
        double reward = target * (1 - taxPct / 100.0) - entry;
        double risk = entry - stop * (1 - taxPct / 100.0);
        return reward > 0 && risk > 0 ? reward / risk : 0;
    }
}
