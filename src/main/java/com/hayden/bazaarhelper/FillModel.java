package com.hayden.bazaarhelper;

/**
 * Realistic fills for a market maker who gets jumped by 0.1. Pure logic.
 *
 * Insta-sells fill the highest buy order; insta-buys fill the lowest sell offer. So your bid only
 * fills while it's on top. If a +0.1 quote stays on top for T seconds and flow is F units/second:
 *
 *   fill per order   = F x T                    (what one placement can expect to fill)
 *   share on top     = T / (T + R)              (R = how long you take to re-price when jumped)
 *   units per day    = min(bid side, ask side) of F x share on top x 86,400
 *   order size       = fill per order x sizeFactor, so orders aren't 10x what can fill
 *                      (each placement eats your daily Bazaar limit)
 *   bot war          = fill per order under minFillPerOrder on either side: skip it
 */
public final class FillModel {
    private FillModel() {}

    public record Estimate(double bidSecsOnTop, double askSecsOnTop, double bidFlowPerSec, double askFlowPerSec,
                           double bidFillPerOrder, double askFillPerOrder, double bidShare, double askShare,
                           double unitsPerDay, long maxOrderUnits, boolean war, boolean measured) {}

    public static final double WEEK_SECS = 7 * 86_400.0;

    public static Estimate estimate(double instaBuyWeek, double instaSellWeek, double bidSecsOnTop, double askSecsOnTop,
                                    double reactSecs, double sizeFactor, double minFillPerOrder, boolean measured) {
        double bidFlow = instaSellWeek / WEEK_SECS;   // insta-sells hit buy orders
        double askFlow = instaBuyWeek / WEEK_SECS;    // insta-buys hit sell offers
        double bidFill = bidFlow * bidSecsOnTop, askFill = askFlow * askSecsOnTop;
        double r = Math.max(1, reactSecs);
        double bidShare = bidSecsOnTop / (bidSecsOnTop + r), askShare = askSecsOnTop / (askSecsOnTop + r);
        double perDay = Math.min(bidFlow * bidShare, askFlow * askShare) * 86_400;
        long size = Math.max(1, (long) Math.ceil(bidFill * Math.max(1, sizeFactor)));
        boolean war = Math.min(bidFill, askFill) < minFillPerOrder;
        return new Estimate(bidSecsOnTop, askSecsOnTop, bidFlow, askFlow, bidFill, askFill, bidShare, askShare,
                perDay, size, war, measured);
    }

    /**
     * Units ahead of a price in the book: on the bid side, everything bid higher; on the ask
     * side, everything offered lower. levels = {price, amount, price, amount, ...} best first.
     */
    public static double unitsAhead(double[] levels, double myPrice, boolean bid) {
        if (levels == null) return Double.NaN;
        double sum = 0;
        for (int i = 0; i + 1 < levels.length; i += 2) {
            double price = levels[i];
            boolean ahead = bid ? price > myPrice + 0.05 : price < myPrice - 0.05;
            if (!ahead) break;
            sum += levels[i + 1];
        }
        return sum;
    }

    /** Seconds until the flow clears the orders ahead of you (Infinity if no flow). */
    public static double waitSecs(double unitsAhead, double flowPerSec) {
        if (!(unitsAhead > 0)) return 0;
        return flowPerSec > 0 ? unitsAhead / flowPerSec : Double.POSITIVE_INFINITY;
    }

    public static String secs(double s) {
        if (!Double.isFinite(s)) return "never";
        if (s < 90) return String.format(java.util.Locale.US, "%.0fs", s);
        if (s < 5400) return String.format(java.util.Locale.US, "%.0fm", s / 60);
        return String.format(java.util.Locale.US, "%.1fh", s / 3600);
    }
}
