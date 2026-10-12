package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rolling top-of-book history per item, from the 20s price refreshes. Hypixel's API has no
 * price history, so the mod keeps its own (saved to disk, so it survives restarts).
 * One sample per item every SAMPLE_MS, kept for KEEP_MS. Pure logic, Gson-serializable.
 */
public final class PriceHistory {
    public static final long SAMPLE_MS = 2 * 60_000L;
    public static final long KEEP_MS = 24 * 3_600_000L;

    /** Per item: flat [time, bid, ask, time, bid, ask, ...] to keep the file small. */
    public Map<String, double[]> series = new HashMap<>();

    public record Sample(long time, double bid, double ask) {
        public double mid() { return (bid + ask) / 2; }
        public double spreadPct() { return mid() > 0 ? (ask - bid) / mid() * 100 : 0; }
    }

    /** Adds a sample for each item (if SAMPLE_MS has passed since its last one) and drops old ones. */
    public void record(Iterable<BazaarData.Product> products, long now) {
        for (BazaarData.Product p : products) {
            if (p.topBid() <= 0 || p.topAsk() <= 0) continue;
            double[] arr = series.get(p.id());
            int n = arr == null ? 0 : arr.length / 3;
            if (n > 0 && now - (long) arr[(n - 1) * 3] < SAMPLE_MS) continue;
            int drop = 0;
            while (drop < n && now - (long) arr[drop * 3] > KEEP_MS) drop++;
            double[] next = new double[(n - drop + 1) * 3];
            if (n - drop > 0) System.arraycopy(arr, drop * 3, next, 0, (n - drop) * 3);
            int at = (n - drop) * 3;
            next[at] = now;
            next[at + 1] = p.topBid();
            next[at + 2] = p.topAsk();
            series.put(p.id(), next);
        }
    }

    public List<Sample> samples(String id) {
        double[] arr = series.get(id);
        List<Sample> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i + 2 < arr.length; i += 3) out.add(new Sample((long) arr[i], arr[i + 1], arr[i + 2]));
        return out;
    }

    /** Keeps only the given items (e.g. ones liquid enough to ever make the book), to bound memory. */
    public void retain(java.util.Set<String> ids) {
        series.keySet().retainAll(ids);
    }

    public static PriceHistory empty() {
        PriceHistory h = new PriceHistory();
        h.series = new HashMap<>();
        return h;
    }

    public void fixAfterLoad() {
        if (series == null) series = new HashMap<>();
    }
}
