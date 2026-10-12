package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure flip math. No Minecraft code here so it can be unit tested. */
public final class FlipMath {
    /** Price step on the Bazaar. */
    public static final double TICK = 0.1;
    /** Assumed share of an item's daily flow you can realistically capture. */
    public static final double CAPTURE_SHARE = 0.10;

    private FlipMath() {}

    public record Flip(
            String id,
            double buyAt,          // your buy order price (top bid + 0.1)
            double sellAt,         // your sell offer price (lowest ask - 0.1)
            double profitPerUnit,  // after tax
            double marginPct,      // profit / buyAt
            double dailyUnits,     // units that flow through BOTH sides per day
            double unitsPerDay,    // units you can expect to flip per day (capped by budget)
            double estDailyProfit) {}

    /**
     * @param topBid   highest buy order
     * @param topAsk   lowest sell offer
     * @param instaBuyWeek  units insta-bought in 7 days (these fill your sell offers)
     * @param instaSellWeek units insta-sold in 7 days (these fill your buy orders)
     * @param taxPct   Bazaar tax in percent, e.g. 1.25
     * @param budget   max coins tied up in this item at once
     * @return the flip, or null if there is no profitable spread
     */
    public static Flip evaluate(String id, double topBid, double topAsk,
                                double instaBuyWeek, double instaSellWeek,
                                double taxPct, double budget) {
        if (topBid <= 0 || topAsk <= 0) return null;
        double buyAt = round1(topBid + TICK);
        double sellAt = round1(topAsk - TICK);
        if (sellAt <= buyAt) return null;

        double profit = sellAt * (1 - taxPct / 100.0) - buyAt;
        if (profit <= 0) return null;

        double dailyUnits = Math.min(instaBuyWeek, instaSellWeek) / 7.0;
        double capturable = dailyUnits * CAPTURE_SHARE;
        double affordable = budget / buyAt;
        double unitsPerDay = Math.min(capturable, affordable);

        return new Flip(id, buyAt, sellAt, profit, profit / buyAt * 100.0,
                dailyUnits, unitsPerDay, unitsPerDay * profit);
    }

    /** Ranks every product, dropping thin markets below minWeeklyVolume on either side. */
    public static List<Flip> rank(Collection<BazaarData.Product> products, double taxPct,
                                  double budget, double minWeeklyVolume, double minMarginPct) {
        return rank(products, taxPct, budget, minWeeklyVolume, minMarginPct, 0, Integer.MAX_VALUE);
    }

    /**
     * Same, but crowded items (0.1 wars) rank lower and very crowded ones are dropped.
     * Each competing order beyond the first divides the score by (1 + crowdWeight).
     */
    public static List<Flip> rank(Collection<BazaarData.Product> products, double taxPct,
                                  double budget, double minWeeklyVolume, double minMarginPct,
                                  double crowdWeight, int maxCrowd) {
        List<Flip> out = new ArrayList<>();
        java.util.Map<String, Double> score = new java.util.HashMap<>();
        for (BazaarData.Product p : products) {
            if (p.instaBuyWeek() < minWeeklyVolume || p.instaSellWeek() < minWeeklyVolume) continue;
            if (p.crowd() > maxCrowd) continue;
            Flip f = evaluate(p.id(), p.topBid(), p.topAsk(), p.instaBuyWeek(), p.instaSellWeek(), taxPct, budget);
            if (f == null || f.marginPct() < minMarginPct) continue;
            out.add(f);
            score.put(f.id(), crowdAdjusted(f.estDailyProfit(), p.crowd(), crowdWeight));
        }
        out.sort(Comparator.comparingDouble((Flip f) -> score.get(f.id())).reversed());
        return out;
    }

    public static double crowdAdjusted(double estDaily, int crowd, double crowdWeight) {
        return estDaily / (1 + crowdWeight * Math.max(0, crowd - 1));
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** 1234567 -> "1.23M". */
    public static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (a >= 1e4) return String.format(Locale.US, "%.1fk", v / 1e3);
        return String.format(Locale.US, "%,.1f", v);
    }

    /** "75m" -> 75000000, "1.5b" -> 1500000000, "20k" -> 20000, "1,000" -> 1000. */
    public static double parseAmount(String s) {
        String t = s.trim().toLowerCase(Locale.US).replace(",", "");
        double mult = 1;
        if (t.endsWith("k")) { mult = 1e3; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1e6; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("b")) { mult = 1e9; t = t.substring(0, t.length() - 1); }
        return Double.parseDouble(t) * mult;
    }
}
