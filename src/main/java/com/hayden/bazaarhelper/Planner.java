package com.hayden.bazaarhelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Builds a market-making book: which items to quote, how much stock to hold in each,
 * and how much to keep bidding. Pure logic, no Minecraft code.
 *
 * The binding constraint is inventory slots, so items are ranked by estimated profit
 * per slot. That favors expensive items, but only ones that trade enough: every item
 * must clear a minimum daily coin flow AND a minimum number of units per day, so a
 * pricey item with three trades a day never makes the book.
 *
 * Per item:
 *   target stock T = what you'd turn over in stockHours, capped by maxItemPct of capital
 *   capital        = T units of stock + a bid for T units (two-sided)
 *   slots          = ceil(T / stack size)
 */
public final class Planner {
    /** Largest amount one Bazaar order can hold. */
    public static final long MAX_ORDER_UNITS = 71_680;

    private Planner() {}

    public record Settings(double taxPct, double total, double reservePct, double maxItemPct, int maxItems,
                           int slotBudget, double stockHours, double minDailyCoinFlow, double minDailyUnits,
                           double minMarginPct, int maxCrowd, double crowdWeight) {}

    public record Line(FlipMath.Flip flip, int crowd, int stackSize, long targetUnits, int slots,
                       double stockCoins, double bidCoins, double estDailyProfit, double profitPerSlot) {}

    public record Book(List<Line> lines, double total, double reserve, double committed,
                       int slotsUsed, int slotBudget, double estDailyProfit) {
        /** Investable coins the book couldn't use (slots or liquidity ran out first). */
        public double idle() {
            return Math.max(0, total - reserve - committed);
        }

        public Line find(String id) {
            for (Line l : lines) if (l.flip().id().equals(id)) return l;
            return null;
        }
    }

    private record Candidate(FlipMath.Flip flip, int crowd, int stack, double unitsPerDay, long target, double score) {}

    public static Book build(Collection<BazaarData.Product> products, ToIntFunction<String> stackSize, Settings s) {
        double reserve = s.total() * s.reservePct() / 100.0;
        double perItemCap = s.total() * s.maxItemPct() / 100.0;

        List<Candidate> cands = new ArrayList<>();
        for (BazaarData.Product p : products) {
            if (p.crowd() > s.maxCrowd()) continue;
            FlipMath.Flip f = FlipMath.evaluate(p.id(), p.topBid(), p.topAsk(), p.instaBuyWeek(), p.instaSellWeek(),
                    s.taxPct(), Double.MAX_VALUE);
            if (f == null || f.marginPct() < s.minMarginPct()) continue;
            if (f.dailyUnits() < s.minDailyUnits()) continue;
            if (f.dailyUnits() * f.buyAt() < s.minDailyCoinFlow()) continue;

            int stack = Math.max(1, stackSize.applyAsInt(p.id()));
            double u = f.dailyUnits() * FlipMath.CAPTURE_SHARE;
            long target = (long) Math.floor(Math.min(Math.min(u * s.stockHours() / 24.0,
                    perItemCap / (2 * f.buyAt())), MAX_ORDER_UNITS));
            if (target < 1) continue;
            int slots = slotsFor(target, stack);
            double est = estDaily(u, target, s.stockHours(), f.profitPerUnit());
            double score = FlipMath.crowdAdjusted(est, p.crowd(), s.crowdWeight()) / slots;
            cands.add(new Candidate(f, p.crowd(), stack, u, target, score));
        }
        cands.sort(Comparator.comparingDouble(Candidate::score).reversed());

        double capLeft = s.total() - reserve;
        int slotsLeft = s.slotBudget();
        double minLine = 500_000; // skip lines too small to bother quoting
        List<Line> lines = new ArrayList<>();
        double committed = 0, est = 0;
        int used = 0;

        for (Candidate c : cands) {
            if (lines.size() >= s.maxItems() || slotsLeft <= 0) break;
            FlipMath.Flip f = c.flip();
            long t = Math.min(c.target(), (long) slotsLeft * c.stack());
            t = Math.min(t, (long) Math.floor(capLeft / (2 * f.buyAt())));
            if (t < 1) continue;
            double stockCoins = t * f.buyAt();
            if (2 * stockCoins < minLine) continue;
            int slots = slotsFor(t, c.stack());
            double lineEst = estDaily(c.unitsPerDay(), t, s.stockHours(), f.profitPerUnit());
            lines.add(new Line(f, c.crowd(), c.stack(), t, slots, stockCoins, stockCoins, lineEst, lineEst / slots));
            capLeft -= 2 * stockCoins;
            slotsLeft -= slots;
            committed += 2 * stockCoins;
            used += slots;
            est += lineEst;
        }
        return new Book(lines, s.total(), reserve, committed, used, s.slotBudget(), est);
    }

    static int slotsFor(long units, int stack) {
        return (int) Math.ceil(units / (double) stack);
    }

    /** You can't turn over more than your stock allows: T units every stockHours. */
    static double estDaily(double unitsPerDay, long target, double stockHours, double profitPerUnit) {
        return Math.min(unitsPerDay, target * 24.0 / stockHours) * profitPerUnit;
    }
}
