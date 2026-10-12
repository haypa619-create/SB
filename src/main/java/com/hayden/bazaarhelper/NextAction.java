package com.hayden.bazaarhelper;

import java.util.List;

/**
 * Picks the most urgent thing to do across your whole book, so one click or one key
 * press (N) takes you there. Pure logic.
 *
 * Priority (lower first; ties go to the better item, i.e. book order):
 *   0  pull a bid the guard flags (price falling fast), or re-price an OUTBID quote
 *   1  list stock when LONG (inventory risk)
 *   2  place a bid when SHORT (nothing to sell soon)
 *   3  list stock
 *   4  place a bid
 */
public final class NextAction {
    public enum Kind { PULL_BID, REPRICE_BID, REPRICE_ASK, PLACE_BID, PLACE_ASK }

    public record Candidate(String id, String name, MarketMaker.Quote quote) {}

    public record Pick(String id, String name, Kind kind, int priority) {
        public String describe() {
            return switch (kind) {
                case PULL_BID -> "Pull bid: " + name;
                case REPRICE_BID -> "Re-price bid: " + name;
                case REPRICE_ASK -> "Re-price ask: " + name;
                case PLACE_BID -> "Bid " + name;
                case PLACE_ASK -> "List " + name;
            };
        }
    }

    private NextAction() {}

    public static Pick pick(List<Candidate> cands) {
        return pick(cands, true);
    }

    /**
     * @param canPlace false when you're at the open-order cap: only pulls and re-prices are offered
     */
    public static Pick pick(List<Candidate> cands, boolean canPlace) {
        Pick best = null;
        for (Candidate c : cands) {
            Pick p = bestFor(c);
            if (p != null && !canPlace && (p.kind() == Kind.PLACE_BID || p.kind() == Kind.PLACE_ASK)) continue;
            if (p != null && (best == null || p.priority() < best.priority())) best = p;
        }
        return best;
    }

    /** How many sides across the book need you. */
    public static int count(List<Candidate> cands) {
        int n = 0;
        for (Candidate c : cands) {
            MarketMaker.Quote q = c.quote();
            if (needs(q.bidAction())) n++;
            if (needs(q.askAction())) n++;
        }
        return n;
    }

    private static boolean needs(MarketMaker.Action a) {
        return a == MarketMaker.Action.PLACE || a == MarketMaker.Action.OUTBID || a == MarketMaker.Action.PULL;
    }

    private static Pick bestFor(Candidate c) {
        MarketMaker.Quote q = c.quote();
        if (q.bidAction() == MarketMaker.Action.PULL) return new Pick(c.id(), c.name(), Kind.PULL_BID, 0);
        if (q.bidAction() == MarketMaker.Action.OUTBID) return new Pick(c.id(), c.name(), Kind.REPRICE_BID, 0);
        if (q.askAction() == MarketMaker.Action.OUTBID) return new Pick(c.id(), c.name(), Kind.REPRICE_ASK, 0);
        boolean ask = q.askAction() == MarketMaker.Action.PLACE;
        boolean bid = q.bidAction() == MarketMaker.Action.PLACE;
        if (ask && q.state() == MarketMaker.State.LONG) return new Pick(c.id(), c.name(), Kind.PLACE_ASK, 1);
        if (bid && q.state() == MarketMaker.State.SHORT) return new Pick(c.id(), c.name(), Kind.PLACE_BID, 2);
        if (ask) return new Pick(c.id(), c.name(), Kind.PLACE_ASK, 3);
        if (bid) return new Pick(c.id(), c.name(), Kind.PLACE_BID, 4);
        return null;
    }
}
