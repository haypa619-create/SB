package com.hayden.bazaarhelper;

import java.util.List;

/** Small numbers for the item page, Market tab and Home chart. Pure logic. */
public final class Stats {
    private Stats() {}

    /** % change of the mid from the first sample inside the window to `nowMid`. NaN if no history that old. */
    public static double changePct(List<PriceHistory.Sample> h, double nowMid, long now, long windowMs) {
        if (h == null || h.isEmpty() || nowMid <= 0) return Double.NaN;
        long from = now - windowMs;
        if (h.get(0).time() > from + windowMs / 4) return Double.NaN; // not enough history yet
        for (PriceHistory.Sample s : h) {
            if (s.time() >= from && s.mid() > 0) return (nowMid / s.mid() - 1) * 100;
        }
        return Double.NaN;
    }

    /**
     * Resamples a series to `points` evenly spaced values over [from, to], carrying the last
     * known value forward. Leading gaps are NaN. Uses the mid (bid == ask for value series).
     */
    public static double[] resample(List<PriceHistory.Sample> h, long from, long to, int points) {
        double[] out = new double[points];
        java.util.Arrays.fill(out, Double.NaN);
        if (h == null || h.isEmpty() || points < 2 || to <= from) return out;
        int j = 0;
        double last = Double.NaN;
        for (int i = 0; i < points; i++) {
            long t = from + (to - from) * i / (points - 1);
            while (j < h.size() && h.get(j).time() <= t) { last = h.get(j).mid(); j++; }
            out[i] = last;
        }
        return out;
    }

    /** {min, max} of the finite values (and any extra lines to fit), or null if none. */
    public static double[] range(double[] values, double... extra) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : values) if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        for (double v : extra) if (Double.isFinite(v) && v > 0) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (lo > hi) return null;
        if (hi - lo < 1e-9) { lo -= Math.max(1, lo * 0.01); hi += Math.max(1, hi * 0.01); }
        double pad = (hi - lo) * 0.06;
        return new double[]{lo - pad, hi + pad};
    }

    /** "+1.2%" / "-0.4%" / "n/a" */
    public static String pct(double v) {
        if (!Double.isFinite(v)) return "n/a";
        return String.format(java.util.Locale.US, "%+.1f%%", v);
    }
}
