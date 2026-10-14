package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Market scanner: every item that passes your filters, ranked by profit per inventory slot,
 * whether it's in your book or not. Exclude or bring back items from here.
 */
public final class MarketScreen extends DeskScreen {
    private static int page = 0;

    /** Column to sort by (the Sort button cycles). */
    enum Sort {
        PER_SLOT("profit per slot"), MARGIN("margin"), EST_DAY("est. per day"), CHANGE_24H("24h change"), CROWD("calmest top");
        final String label;
        Sort(String label) { this.label = label; }
    }

    private static Sort sort = Sort.PER_SLOT;

    @Override
    public DeskScreen refreshed() {
        return new MarketScreen();
    }

    public MarketScreen() {
        super(Tab.MARKET, Component.literal("Market"));
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left(), y = top;
        List<BazaarHelper.MarketRow> rows = new java.util.ArrayList<>(BazaarHelper.marketRows());
        long now = System.currentTimeMillis();
        java.util.Map<String, Double> chg = new java.util.HashMap<>();
        for (BazaarHelper.MarketRow r : rows) {
            BazaarData.Product p = BazaarHelper.DATA.get(r.line().flip().id());
            double mid = p == null ? 0 : (p.topBid() + p.topAsk()) / 2;
            chg.put(r.line().flip().id(), Stats.changePct(BazaarHelper.history(r.line().flip().id()), mid, now, 24 * 3_600_000L));
        }
        java.util.Comparator<BazaarHelper.MarketRow> cmp = switch (sort) {
            case PER_SLOT -> java.util.Comparator.comparingDouble(r -> -r.line().profitPerSlot());
            case MARGIN -> java.util.Comparator.comparingDouble(r -> -r.line().flip().marginPct());
            case EST_DAY -> java.util.Comparator.comparingDouble(r -> -r.line().estDailyProfit());
            case CHANGE_24H -> java.util.Comparator.comparingDouble(r -> {
                double c = chg.get(r.line().flip().id());
                return Double.isFinite(c) ? -c : Double.MAX_VALUE;
            });
            case CROWD -> java.util.Comparator.comparingDouble(r -> r.line().fill() == null ? r.line().crowd()
                    : -Math.min(r.line().fill().bidSecsOnTop(), r.line().fill().askSecsOnTop()));
        };
        rows.sort(cmp);
        long inBook = rows.stream().filter(BazaarHelper.MarketRow::inBook).count();
        int sortW = 150;
        label(Component.literal(rows.size() + " items pass your filters").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("   " + inBook + " in your book").withStyle(ChatFormatting.GRAY)),
                left, y, w - sortW - 4, "Filters: min weekly units, min daily coin flow, max crowd, min margin (Settings).\n"
                        + "Est/day assumes you capture your share of the flow at the current spread.\nClick a name for the item page.");
        button(Component.literal("Sort: " + sort.label).withStyle(ChatFormatting.YELLOW), left + w - sortW, y, sortW,
                "Click to change the sort", () -> { sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]; page = 0;
                    BazaarHelper.show(new MarketScreen()); });
        y += ROW_H;
        if (rows.isEmpty()) {
            label(Component.literal("Nothing passes. Lower Min weekly units or Max crowd in Settings.").withStyle(ChatFormatting.GRAY),
                    left, y, w, "");
            return;
        }

        int nameW = (int) (w * 0.29), marginW = (int) (w * 0.11), estW = (int) (w * 0.22), chgW = (int) (w * 0.10), flowW = (int) (w * 0.10);
        int btnW = w - nameW - marginW - estW - chgW - flowW - 20;
        int perPage = rowsThatFit(y);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            BazaarHelper.MarketRow r = rows.get(i);
            Planner.Line l = r.line();
            FlipMath.Flip f = l.flip();
            int x = left;
            boolean warn = !r.guard().isEmpty();
            MutableComponent name = Component.literal("#" + (i + 1) + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(r.name()).withStyle(r.excluded() ? ChatFormatting.DARK_GRAY
                            : r.inBook() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .append(Component.literal(warn ? " ⚠" : "").withStyle(ChatFormatting.RED));
            String rid = f.id();
            button(name, x, y, nameW, r.name() + (r.inBook() ? "  (in your book)" : "") + (r.excluded() ? "  (excluded)" : "")
                    + (warn ? "\nGuard: " + r.guard() : "") + "\nClick for the item page", () -> BazaarHelper.openItem(rid, Tab.MARKET));
            x += nameW + 4;
            label(Component.literal(String.format(Locale.US, "%.2f%%", f.marginPct())).withStyle(ChatFormatting.YELLOW), x, y, marginW,
                    String.format(Locale.US, "Bid %s / ask %s\n+%s per unit after tax", FlipMath.fmt(f.buyAt()), FlipMath.fmt(f.sellAt()),
                            FlipMath.fmt(f.profitPerUnit())));
            x += marginW + 4;
            label(Component.literal(FlipMath.fmt(l.estDailyProfit()) + "/d").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("  " + FlipMath.fmt(l.profitPerSlot()) + "/slot").withStyle(ChatFormatting.GRAY)),
                    x, y, estW, String.format(Locale.US, "Est. %s per day, %s per inventory slot\nTarget stock %,d (%d slots)",
                            FlipMath.fmt(l.estDailyProfit()), FlipMath.fmt(l.profitPerSlot()), l.targetUnits(), l.slots()));
            x += estW + 4;
            double c24 = chg.get(f.id());
            label(Component.literal(Stats.pct(c24)).withStyle(!Double.isFinite(c24) ? ChatFormatting.DARK_GRAY
                            : c24 >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED), x, y, chgW,
                    "Mid price change over 24h, from the mod's own history (n/a until it has a day)");
            x += chgW + 4;
            FillModel.Estimate fe = l.fill();
            if (fe != null) {
                double top = Math.min(fe.bidSecsOnTop(), fe.askSecsOnTop());
                label(Component.literal((fe.war() ? "war " : "top ") + FillModel.secs(top) + (fe.measured() ? "" : "?"))
                                .withStyle(fe.war() ? ChatFormatting.RED : top >= 300 ? ChatFormatting.GREEN : top >= 60 ? ChatFormatting.YELLOW : ChatFormatting.GOLD),
                        x, y, flowW, String.format(Locale.US, "A +0.1 quote stays on top ~%s (%s)\n~%.1f units fill per order, %d%% of the time on top\n%d orders near the top%s",
                                FillModel.secs(top), fe.measured() ? "measured" : "guessed, measuring now", fe.bidFillPerOrder(),
                                Math.round(100 * Math.min(fe.bidShare(), fe.askShare())), l.crowd(),
                                fe.war() ? "\nBot war: orders get jumped before they fill" : ""));
                x += flowW + 4;
                String id0 = f.id();
                int half0 = (btnW - 4) / 2;
                button(Component.literal("Trade").withStyle(ChatFormatting.LIGHT_PURPLE), x, y, half0,
                        "Set up a directional trade (target and stop).", () -> BazaarHelper.openTradeSetup(id0));
                button(Component.literal(r.excluded() ? "Incl" : "Excl").withStyle(r.excluded() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                        x + half0 + 4, y, btnW - half0 - 4, r.excluded() ? "Let the book use this item again" : "Keep this item out of your book",
                        () -> { BazaarHelper.toggleExcluded(id0); BazaarHelper.show(new MarketScreen()); });
                y += ROW_H;
                continue;
            }
            label(Component.literal("crowd " + l.crowd()).withStyle(l.crowd() <= 3 ? ChatFormatting.GREEN
                            : l.crowd() <= 8 ? ChatFormatting.YELLOW : ChatFormatting.RED), x, y, flowW,
                    String.format(Locale.US, "%d orders near the top price (0.1 wars)\n%s units a day flow through both sides",
                            l.crowd(), FlipMath.fmt(f.dailyUnits())));
            x += flowW + 4;
            String id = f.id();
            int half = (btnW - 4) / 2;
            button(Component.literal("Trade").withStyle(ChatFormatting.LIGHT_PURPLE), x, y, half,
                    "Set up a directional trade (target and stop).\nThe item leaves your market-making book while the trade is open.",
                    () -> BazaarHelper.openTradeSetup(id));
            button(Component.literal(r.excluded() ? "Incl" : "Excl").withStyle(r.excluded() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    x + half + 4, y, btnW - half - 4, r.excluded() ? "Let the book use this item again" : "Keep this item out of your book",
                    () -> { BazaarHelper.toggleExcluded(id); BazaarHelper.show(new MarketScreen()); });
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new MarketScreen()); });
    }
}
