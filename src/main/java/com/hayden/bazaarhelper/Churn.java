package com.hayden.bazaarhelper;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * How fast each item's top of book gets jumped, measured from the 20s price refreshes.
 * Pure logic.
 *
 * A bid is outbid when the top bid goes UP; an ask is undercut when the top ask goes DOWN.
 * (A top bid going down just means it filled or was pulled, which doesn't push you off the top.)
 * From the number of jumps in the last hour it estimates how long a fresh +0.1 quote stays on
 * top. Until it has watched an item for MIN_WATCH_MS it falls back to a guess from crowding.
 */
public final class Churn {
    public static final long WINDOW_MS = 60 * 60_000L;
    public static final long MIN_WATCH_MS = 5 * 60_000L;
    public static final double MAX_SECS = 3600;

    private final Map<String, double[]> last = new HashMap<>();
    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> bidJumps = new HashMap<>(), askJumps = new HashMap<>();

    public synchronized void observe(Iterable<BazaarData.Product> products, long now) {
        for (BazaarData.Product p : products) {
            if (p.topBid() <= 0 || p.topAsk() <= 0) continue;
            double[] prev = last.put(p.id(), new double[]{p.topBid(), p.topAsk()});
            firstSeen.putIfAbsent(p.id(), now);
            if (prev == null) continue;
            if (p.topBid() > prev[0] + 1e-6) bidJumps.computeIfAbsent(p.id(), k -> new ArrayDeque<>()).addLast(now);
            if (p.topAsk() < prev[1] - 1e-6) askJumps.computeIfAbsent(p.id(), k -> new ArrayDeque<>()).addLast(now);
        }
        for (ArrayDeque<Long> q : bidJumps.values()) while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
        for (ArrayDeque<Long> q : askJumps.values()) while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
    }

    /** True once the item has been watched long enough for a real measurement. */
    public synchronized boolean measured(String id, long now) {
        Long f = firstSeen.get(id);
        return f != null && now - f >= MIN_WATCH_MS;
    }

    /** Jumps over the last hour (scaled up if watched for less). NaN if not measured yet. */
    public synchronized double jumpsPerHour(String id, boolean bid, long now) {
        if (!measured(id, now)) return Double.NaN;
        double span = Math.min(now - firstSeen.get(id), WINDOW_MS);
        ArrayDeque<Long> q = (bid ? bidJumps : askJumps).get(id);
        int n = q == null ? 0 : q.size();
        return n * 3_600_000.0 / span;
    }

    /**
     * Expected seconds a fresh +0.1 quote stays on top. Measured when possible, otherwise
     * guessed from how many orders crowd the top (crowd 1 -> 4 min, crowd 8 -> 30s).
     */
    public synchronized double secsOnTop(String id, boolean bid, long now, int crowd) {
        if (!measured(id, now)) return Math.min(MAX_SECS, 240.0 / Math.max(1, crowd));
        double span = Math.min(now - firstSeen.get(id), WINDOW_MS) / 1000.0;
        ArrayDeque<Long> q = (bid ? bidJumps : askJumps).get(id);
        int n = q == null ? 0 : q.size();
        return Math.min(MAX_SECS, span / (n + 1));
    }
}
