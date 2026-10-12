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

    public Planner.Settings plannerSettings(double total) {
        return new Planner.Settings(taxPercent, total, reservePct, maxItemPct, maxItems, slotBudget, stockHours,
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

    // ---------- desk book (live quotes, cost basis, P&L) ----------

    public static QuoteBook loadBook(Path configDir) {
        Path f = configDir.resolve("bazaarhelper-book.json");
        try {
            if (Files.exists(f)) {
                QuoteBook b = GSON.fromJson(Files.readString(f), QuoteBook.class);
                if (b != null && b.items != null) return b;
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
