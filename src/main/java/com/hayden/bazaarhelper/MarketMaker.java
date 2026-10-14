package com.hayden.bazaarhelper;

/**
 * Works out what your two-sided quote on one item should be right now. Pure logic.
 *
 * Position = stock in your inventory + units sitting in your live sell offer.
 *   SHORT    (position < 0.5 x target): bid sharper to rebuild stock
 *   BALANCED                           : bid top + 0.1, ask top - 0.1
 *   LONG     (position > 1.5 x target): stop bidding, ask sharper to unload
 *
 * Bid size tops you up toward 2 x target counting what you hold and what's already bidding.
 * Ask size is whatever stock you hold (a Bazaar sell offer lists all of that item you carry).
 */
public final class MarketMaker {
    public enum State { SHORT, BALANCED, LONG }
    /** PULL: you have a live bid but the guard says get out (price falling fast). */
    /**
     * QUEUED: outbid, but the orders ahead of you are small enough that the flow will clear them
     * soon and you're back on top. Waiting beats re-placing (which costs daily limit and clicks).
     */
    public enum Action { PLACE, LIVE, OUTBID, HOLD, EMPTY, PULL, QUEUED }

    /**
     * Fill-aware limits.
     * @param maxBidUnits   biggest bid worth placing (what can fill before you're jumped)
     * @param maxOrderCoins hard cap on one order's coins (0 = none)
     * @param bidWaitSecs   seconds until the flow clears the bids ahead of your live bid (NaN = unknown)
     * @param askWaitSecs   same for your live ask
     * @param maxWaitSecs   wait instead of re-pricing when the queue clears within this
     */
    public record Limits(long maxBidUnits, double maxOrderCoins, double bidWaitSecs, double askWaitSecs, double maxWaitSecs) {
        public static final Limits NONE = new Limits(Long.MAX_VALUE, 0, Double.NaN, Double.NaN, 0);
    }

    public static final double SHORT_BAND = 0.5;
    public static final double LONG_BAND = 1.5;
    /** Price gap treated as "still on top", allows for rounding in chat totals. */
    public static final double TOLERANCE = 0.05;

    private MarketMaker() {}

    public record Quote(State state, long position, double bidPrice, long bidSize, Action bidAction,
                        double askPrice, long askSize, Action askAction, String note) {}

    /**
     * @param target        target stock from the plan
     * @param inventory     units of this item in your inventory now
     * @param liveAskUnits  units in your live sell offer (0 if none)
     * @param liveAskPrice  its price
     * @param liveBidUnits  units in your live buy order (0 if none)
     * @param liveBidPrice  its price
     * @param skewShare     share of the spread to lean by when SHORT or LONG (e.g. 0.25)
     */
    public static Quote quote(BazaarData.Product p, long target, long inventory,
                              long liveAskUnits, double liveAskPrice, long liveBidUnits, double liveBidPrice,
                              double taxPct, double skewShare) {
        return quote(p, target, inventory, liveAskUnits, liveAskPrice, liveBidUnits, liveBidPrice, taxPct, skewShare,
                Guard.Assessment.NONE);
    }

    /** Largest skew by default, as % of the price. Hypixel and other mods reject big jumps over the best price. */
    public static final double DEFAULT_MAX_SKEW_PCT = 2.0;

    /** Same, with the guard's view of the item (fair value, edge, and reasons to stay out). */
    public static Quote quote(BazaarData.Product p, long target, long inventory,
                              long liveAskUnits, double liveAskPrice, long liveBidUnits, double liveBidPrice,
                              double taxPct, double skewShare, Guard.Assessment g) {
        return quote(p, target, inventory, liveAskUnits, liveAskPrice, liveBidUnits, liveBidPrice, taxPct, skewShare, g,
                DEFAULT_MAX_SKEW_PCT);
    }

    /**
     * @param maxSkewPct the skew never moves a quote more than this % past the best price. On wide
     *                   items a quarter of the spread can be 15%+ over the best bid, which wastes edge
     *                   and gets orders blocked.
     */
    public static Quote quote(BazaarData.Product p, long target, long inventory,
                              long liveAskUnits, double liveAskPrice, long liveBidUnits, double liveBidPrice,
                              double taxPct, double skewShare, Guard.Assessment g, double maxSkewPct) {
        return quote(p, target, inventory, liveAskUnits, liveAskPrice, liveBidUnits, liveBidPrice, taxPct, skewShare, g,
                maxSkewPct, Limits.NONE);
    }

    public static Quote quote(BazaarData.Product p, long target, long inventory,
                              long liveAskUnits, double liveAskPrice, long liveBidUnits, double liveBidPrice,
                              double taxPct, double skewShare, Guard.Assessment g, double maxSkewPct, Limits lim) {
        if (g == null) g = Guard.Assessment.NONE;
        if (lim == null) lim = Limits.NONE;
        long position = inventory + liveAskUnits;
        State state = position < target * SHORT_BAND ? State.SHORT
                : position > target * LONG_BAND ? State.LONG : State.BALANCED;

        double topBid = p.topBid(), topAsk = p.topAsk();
        String note = "";

        // ---- prices ----
        double bidPrice = FlipMath.round1(topBid + FlipMath.TICK);
        double askPrice = FlipMath.round1(topAsk - FlipMath.TICK);
        double skew = (topBid > 0 && topAsk > 0) ? FlipMath.round1(Math.max(0, (topAsk - topBid) * skewShare)) : 0;
        if (state == State.SHORT) bidPrice = FlipMath.round1(bidPrice + Math.min(skew, FlipMath.round1(topBid * maxSkewPct / 100.0)));
        if (state == State.LONG) askPrice = FlipMath.round1(askPrice - Math.min(skew, FlipMath.round1(topAsk * maxSkewPct / 100.0)));
        // never cross the book after skewing
        if (topAsk > 0 && bidPrice > topAsk - 2 * FlipMath.TICK) bidPrice = FlipMath.round1(topAsk - 2 * FlipMath.TICK);
        if (topBid > 0 && askPrice < topBid + 2 * FlipMath.TICK) askPrice = FlipMath.round1(topBid + 2 * FlipMath.TICK);
        boolean profitable = topBid > 0 && topAsk > 0 && askPrice * (1 - taxPct / 100.0) > bidPrice;

        // ---- bid side ----
        long bidSize = 0;
        Action bidAction;
        double bidCap = g.hasFair() ? FlipMath.round1(g.fair() * (1 - g.edgePct() / 100.0)) : Double.MAX_VALUE;
        if (liveBidUnits > 0) {
            if (g.pullBid()) {
                bidAction = Action.PULL;
                note = "pull your bid: " + g.reason();
            } else if (liveBidPrice + TOLERANCE < topBid) {
                if (lim.bidWaitSecs() <= lim.maxWaitSecs()) {
                    bidAction = Action.QUEUED;
                    note = "jumped, but the bids ahead clear in ~" + FillModel.secs(lim.bidWaitSecs()) + ": wait, don't re-place";
                } else {
                    bidAction = Action.OUTBID;
                }
            } else {
                bidAction = Action.LIVE;
            }
        } else if (topBid <= 0 || topAsk <= 0) {
            bidAction = Action.HOLD;
            note = "one side of the book is empty";
        } else if (state == State.LONG) {
            bidAction = Action.HOLD;
            note = "long: stop bidding, sell down";
        } else if (!profitable) {
            bidAction = Action.HOLD;
            note = "spread closed after tax";
        } else if (g.holdBid()) {
            bidAction = Action.HOLD;
            note = "staying out: " + g.reason();
        } else if (bidPrice > bidCap) {
            bidAction = Action.HOLD;
            note = "bid would be above fair value (" + FlipMath.fmt(g.fair()) + " minus " + String.format(java.util.Locale.US, "%.2f", g.edgePct()) + "% edge)";
        } else {
            bidSize = Math.max(0, Math.min(2 * target - position, Planner.MAX_ORDER_UNITS));
            bidSize = Math.min(bidSize, lim.maxBidUnits());
            if (lim.maxOrderCoins() > 0 && bidPrice > 0) bidSize = Math.min(bidSize, (long) Math.floor(lim.maxOrderCoins() / bidPrice));
            bidAction = bidSize > 0 ? Action.PLACE : Action.HOLD;
        }

        // ---- ask side ----
        Action askAction;
        if (liveAskUnits > 0) {
            if (topAsk > 0 && liveAskPrice - TOLERANCE > topAsk) {
                askAction = lim.askWaitSecs() <= lim.maxWaitSecs() ? Action.QUEUED : Action.OUTBID;
                if (askAction == Action.QUEUED && note.isEmpty())
                    note = "undercut, but the offers ahead clear in ~" + FillModel.secs(lim.askWaitSecs()) + ": wait";
            } else {
                askAction = Action.LIVE;
            }
        } else if (inventory > 0 && g.holdAsk() && state != State.LONG) {
            askAction = Action.HOLD;
            if (note.isEmpty()) note = "holding stock: " + g.reason();
        } else if (inventory > 0 && topAsk > 0) {
            askAction = Action.PLACE;
            double floor = g.hasFair() ? FlipMath.round1(g.fair() * (1 + g.edgePct() / 100.0)) : 0;
            if (state != State.LONG && askPrice < floor) {
                askPrice = Math.max(askPrice, floor);
                if (note.isEmpty()) note = "ask raised to fair value + edge";
            }
        } else if (inventory > 0) {
            askAction = Action.PLACE;
            askPrice = FlipMath.round1(Math.max(askPrice, topBid * 1.03)); // no asks to undercut: list 3% over bid
            note = note.isEmpty() ? "no sell offers on the book" : note;
        } else {
            askAction = Action.EMPTY;
        }
        if (state == State.SHORT && note.isEmpty()) note = "short: bidding sharper to rebuild stock";

        return new Quote(state, position, bidPrice, bidSize, bidAction, askPrice, inventory, askAction, note);
    }
}
