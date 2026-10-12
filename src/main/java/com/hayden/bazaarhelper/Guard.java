package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Protects the desk from bad fills and prices quotes around a fair value. Pure logic.
 *
 * A market maker's worst trade is being filled right before the price moves against you.
 * From the mod's own price history it checks each item for:
 *   - falling fast:   mid down more than dropPct over the last dropMinutes  -> stop bidding, pull a live bid
 *   - stretched:      mid more than maxDeviationPct above its fairHours average -> stop bidding
 *                     (below average -> don't list stock cheap, unless you're LONG)
 *   - spread blowout: spread more than spreadBlowout x its usual size -> stop bidding (someone
 *                     bought out the asks or dumped the bids; the "profit" probably isn't real)
 *   - one big order:  a single order at the top bid holding more than a day of flow -> stop bidding
 *                     (if they pull it, your bid is left far above the market)
 *
 * Fair value = average mid over fairHours. Edge = max(minEdgePct, volK x how much the mid has
 * swung around that average), so quotes need more room when the item is jumpy:
 *   bid only if it's at least edge below fair, ask at least edge above fair.
 */
public final class Guard {
    private Guard() {}

    public record Settings(double fairHours, double dropPct, double dropMinutes, double maxDeviationPct,
                           double spreadBlowout, double whaleDays, double minEdgePct, double volK) {
        public static Settings defaults() {
            return new Settings(6, 3, 30, 8, 3, 1, 0.1, 0.25);
        }
    }

    /**
     * fair <= 0 means not enough history yet (needs about 30 minutes). Then only the
     * one-big-order check runs and quotes use the plain top of book.
     */
    public record Assessment(double fair, double edgePct, boolean holdBid, boolean holdAsk, boolean pullBid,
                             String reason, String info) {
        public static final Assessment NONE = new Assessment(0, 0, false, false, false, "", "");

        public boolean hasFair() { return fair > 0; }
    }

    /** History needed before fair value and the other history checks switch on. */
    public static final long MIN_HISTORY_MS = 30 * 60_000L;

    public static Assessment assess(List<PriceHistory.Sample> history, BazaarData.Product p, long now, Settings s) {
        if (p == null || p.topBid() <= 0 || p.topAsk() <= 0) return Assessment.NONE;
        double mid = (p.topBid() + p.topAsk()) / 2;
        double spreadPct = (p.topAsk() - p.topBid()) / mid * 100;

        boolean holdBid = false, holdAsk = false, pullBid = false;
        List<String> reasons = new ArrayList<>();
        List<String> info = new ArrayList<>();

        // ---- one big order at the top bid ----
        double dayFlow = p.instaSellWeek() / 7.0;
        if (p.topBidOrders() == 1 && dayFlow > 0 && p.topBidUnits() >= s.whaleDays() * dayFlow) {
            holdBid = true;
            reasons.add(String.format(Locale.US, "one order holds the top bid (%s units, %.1f days of flow)",
                    FlipMath.fmt(p.topBidUnits()), p.topBidUnits() / dayFlow));
        }
        double dayBuyFlow = p.instaBuyWeek() / 7.0;
        if (p.topAskOrders() == 1 && dayBuyFlow > 0 && p.topAskUnits() >= s.whaleDays() * dayBuyFlow) {
            info.add("one seller holds the top ask (" + FlipMath.fmt(p.topAskUnits()) + " units)");
        }

        // ---- history checks ----
        List<PriceHistory.Sample> window = new ArrayList<>();
        long from = now - (long) (s.fairHours() * 3_600_000L);
        if (history != null) for (PriceHistory.Sample x : history) if (x.time() >= from && x.time() <= now) window.add(x);
        boolean enough = window.size() >= 8 && now - window.get(0).time() >= MIN_HISTORY_MS;
        if (!enough) {
            info.add("fair value after ~30 min of price history");
            return new Assessment(0, 0, holdBid, holdAsk, pullBid, String.join("; ", reasons), String.join("; ", info));
        }

        double sum = 0;
        for (PriceHistory.Sample x : window) sum += x.mid();
        double fair = sum / window.size();
        double var = 0;
        for (PriceHistory.Sample x : window) var += Math.pow(x.mid() / fair - 1, 2);
        double swingPct = Math.sqrt(var / window.size()) * 100;
        double edgePct = Math.max(s.minEdgePct(), s.volK() * swingPct);
        info.add(String.format(Locale.US, "fair %s (%sh avg), swings %.1f%%, edge %.2f%%",
                FlipMath.fmt(fair), FlipMath.fmt(s.fairHours()), swingPct, edgePct));

        // falling fast: compare with the oldest sample inside the drop window
        long dropFrom = now - (long) (s.dropMinutes() * 60_000L);
        PriceHistory.Sample old = null;
        for (PriceHistory.Sample x : window) if (x.time() >= dropFrom) { old = x; break; }
        if (old != null && now - old.time() >= s.dropMinutes() * 60_000L * 0.6) {
            double change = (mid / old.mid() - 1) * 100;
            if (change <= -s.dropPct()) {
                holdBid = true;
                pullBid = true;
                reasons.add(String.format(Locale.US, "falling fast (%.1f%% in %.0f min)", change, s.dropMinutes()));
            } else if (change >= s.dropPct()) {
                info.add(String.format(Locale.US, "rising fast (+%.1f%% in %.0f min)", change, s.dropMinutes()));
            }
        }

        double dev = (mid / fair - 1) * 100;
        if (dev > s.maxDeviationPct()) {
            holdBid = true;
            reasons.add(String.format(Locale.US, "%.1f%% above its %sh average", dev, FlipMath.fmt(s.fairHours())));
        } else if (dev < -s.maxDeviationPct()) {
            holdAsk = true;
            reasons.add(String.format(Locale.US, "%.1f%% below its %sh average: don't sell cheap", -dev, FlipMath.fmt(s.fairHours())));
        }

        List<Double> spreads = new ArrayList<>();
        for (PriceHistory.Sample x : window) spreads.add(x.spreadPct());
        Collections.sort(spreads);
        double usual = spreads.get(spreads.size() / 2);
        if (usual > 0 && spreadPct > s.spreadBlowout() * usual) {
            holdBid = true;
            reasons.add(String.format(Locale.US, "spread blew out (%.1f%% vs usual %.1f%%)", spreadPct, usual));
        }

        return new Assessment(fair, edgePct, holdBid, holdAsk, pullBid, String.join("; ", reasons), String.join("; ", info));
    }
}
