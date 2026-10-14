package com.hayden.bazaarhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings saved to config/bazaarhelper.json (inside your Modrinth profile folder). */
public final class Config {
    /** 1.25 by default; 1.125 or 1.0 with the Bazaar Flipper account upgrade. */
    public double taxPercent = 1.25;
    /** Coin total the book is built for. */
    /** Read coins from your purse (+ bids + stock) instead of the number below. */
    public boolean autoCoins = true;
    /** Used when autoCoins is off, or before the purse shows on the sidebar. */
    public double defaultTotal = 600_000_000;
    /** Share of the total kept uninvested. */
    public double reservePct = 15;
    /** Max share of the total (stock + bid) in any one item. */
    public double maxItemPct = 12;
    /** Max items quoted (each uses a buy order and a sell offer). */
    public int maxItems = 8;

    /** Inventory slots you can give to stock. 27 = main inventory without the hotbar. */
    public int slotBudget = 27;
    /** Target stock = what you expect to turn over in this many hours. */
    public double stockHours = 6;
    /** Share of the spread to lean by when long or short. */
    public double skewShare = 0.25;
    /** The skew never moves a quote more than this % past the best price. */
    public double maxSkewPct = 2.0;

    /** "Lots of people in it": min coins traded per day on the thinner side. */
    public double minDailyCoinFlow = 25_000_000;
    /** Min units traded per WEEK on the thinner side (insta-buys vs insta-sells). Market making needs deep two-way flow. */
    public double minWeeklyUnits = 20_000;
    /** Skip items under this margin after tax. */
    public double minMarginPct = 1.0;
    /** Ranking penalty per competing order near the top price (0 = ignore crowding). */
    public double crowdWeight = 0.15;
    /** Skip items with more competing orders than this near the top (0.1 wars). */
    public int maxCrowd = 12;

    /** Hypixel's cap on open Bazaar orders. Next stops suggesting new orders at the cap. */
    public int maxOrders = 14;
    /** Hypixel's daily Bazaar limit in coins. The board warns as you get close. */
    public double dailyLimit = 15_000_000_000.0;

    /** Guard: stop bidding / pull bids on bad-fill signals (see Guard.java). */
    public boolean guardOn = true;
    /** Pull bids when the mid falls this much in 30 minutes. */
    public double guardDropPct = 3;
    /** Stop bidding when the mid is this far above its average. */
    public double guardDeviationPct = 8;
    /** Hours of price history that make the fair value. */
    public double fairHours = 6;
    /** Extra edge per % of price swing. 0 = quote the plain top of book. */
    public double volK = 0.25;

    /** Enter (rebindable) clicks the green slot once, and presses Done on a sign the mod filled in. */
    public boolean enterClicks = true;
    /** Use Hypixel's preset amount buttons when one is within this % of the planned size (0 = never). */
    public double presetTolerancePct = 25;

    /** Plan with realistic fills (time on top x flow) instead of a flat 10% of flow. */
    public boolean fillModel = true;
    /** Leave out items where an order can't expect one unit before it's jumped (bot wars). */
    public boolean skipWars = true;
    /** How long you usually take to re-price after being jumped (seconds). */
    public double reactSecs = 60;
    /** Bid size = this x what's expected to fill before you're jumped. */
    public double sizeFactor = 1.5;
    /** Below this many units expected per order, an item counts as a bot war. */
    public double minFillPerOrder = 1;
    /** Hard cap on one order's coins (0 = none). Placements count toward your daily limit. */
    public double maxOrderCoins = 50_000_000;
    /** When jumped, wait instead of re-placing if the orders ahead clear within this many seconds. */
    public double maxWaitSecs = 90;

    /** Sound on outbid, fills and pull warnings. */
    public boolean sounds = true;

    /** Item ids you've excluded from the book (the X on a board row). */
    public java.util.List<String> excluded = new java.util.ArrayList<>();
    /** Chat notes for quotes going up, fills and realized P&L. */
    public boolean chatNotes = true;
    /** Set after the first-run setup screen. */
    public boolean setupDone = false;

    /** Prints screen titles, sign text and Bazaar chat lines, to tune message matching. */
    public boolean debug = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private transient Path file;

    public Guard.Settings guardSettings() {
        Guard.Settings d = Guard.Settings.defaults();
        return new Guard.Settings(fairHours, guardDropPct, d.dropMinutes(), guardDeviationPct, d.spreadBlowout(),
                d.whaleDays(), d.minEdgePct(), volK);
    }

    /** Items that fit under the order cap: each item can need a bid and an ask. */
    public int effectiveMaxItems() {
        return Math.max(1, Math.min(maxItems, maxOrders / 2));
    }

    public Planner.Settings plannerSettings(double total) {
        return new Planner.Settings(taxPercent, total, reservePct, maxItemPct, effectiveMaxItems(), slotBudget, stockHours,
                minDailyCoinFlow, minWeeklyUnits / 7.0, minMarginPct, maxCrowd, crowdWeight);
    }

    public static Config load(Path configDir) {
        Path file = configDir.resolve("bazaarhelper.json");
        Config c = null;
        try {
            if (Files.exists(file)) c = GSON.fromJson(Files.readString(file), Config.class);
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not read config, using defaults", e);
        }
        if (c == null) c = new Config();
        if (c.excluded == null) c.excluded = new java.util.ArrayList<>();
        c.file = file;
        c.save();
        return c;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not save config", e);
        }
    }

    // ---------- trades ----------

    public static TradeBook loadTrades(Path configDir) {
        Path f = configDir.resolve("bazaarhelper-trades.json");
        try {
            if (Files.exists(f)) {
                TradeBook t = GSON.fromJson(Files.readString(f), TradeBook.class);
                if (t != null) { t.fixAfterLoad(); return t; }
            }
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not read trades, starting fresh", e);
        }
        return new TradeBook();
    }

    public static void saveTrades(Path configDir, TradeBook t) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("bazaarhelper-trades.json"), GSON.toJson(t));
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not save trades", e);
        }
    }

    // ---------- price history ----------

    public static PriceHistory loadHistory(Path configDir) {
        Path f = configDir.resolve("bazaarhelper-history.json");
        try {
            if (Files.exists(f)) {
                PriceHistory h = new Gson().fromJson(Files.readString(f), PriceHistory.class);
                if (h != null) { h.fixAfterLoad(); return h; }
            }
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not read price history, starting fresh", e);
        }
        return PriceHistory.empty();
    }

    /** Compact JSON (no pretty printing): this file holds a day of prices. */
    public static void saveHistory(Path configDir, PriceHistory h) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("bazaarhelper-history.json"), new Gson().toJson(h));
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not save price history", e);
        }
    }

    // ---------- desk book (live quotes, cost basis, P&L) ----------

    public static QuoteBook loadBook(Path configDir) {
        Path f = configDir.resolve("bazaarhelper-book.json");
        try {
            if (Files.exists(f)) {
                QuoteBook b = GSON.fromJson(Files.readString(f), QuoteBook.class);
                if (b != null && b.items != null) {
                    if (b.dailyRealized == null) b.dailyRealized = new java.util.LinkedHashMap<>();
                    if (b.dailyVolume == null) b.dailyVolume = new java.util.LinkedHashMap<>();
                    if (b.toClaim == null) b.toClaim = new java.util.LinkedHashMap<>();
                    return b;
                }
            }
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not read desk book, starting fresh", e);
        }
        return new QuoteBook();
    }

    public static void saveBook(Path configDir, QuoteBook book) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("bazaarhelper-book.json"), GSON.toJson(book));
        } catch (Exception e) {
            BazaarHelper.LOG.warn("Could not save desk book", e);
        }
    }
}
