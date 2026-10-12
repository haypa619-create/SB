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

    public MarketScreen() {
        super(Tab.MARKET, Component.literal("Market"));
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left(), y = top;
        List<BazaarHelper.MarketRow> rows = BazaarHelper.marketRows();
        long inBook = rows.stream().filter(BazaarHelper.MarketRow::inBook).count();
        label(Component.literal(rows.size() + " items pass your filters").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("   " + inBook + " in your book   ranked by profit per slot").withStyle(ChatFormatting.GRAY)),
                left, y, w, "Filters: min weekly units, min daily coin flow, max crowd, min margin (Settings).\n"
                        + "Est/day assumes you capture your share of the flow at the current spread.");
        y += ROW_H;
        if (rows.isEmpty()) {
            label(Component.literal("Nothing passes. Lower Min weekly units or Max crowd in Settings.").withStyle(ChatFormatting.GRAY),
                    left, y, w, "");
            return;
        }

        int nameW = (int) (w * 0.32), marginW = (int) (w * 0.16), estW = (int) (w * 0.22), flowW = (int) (w * 0.14);
        int btnW = w - nameW - marginW - estW - flowW - 16;
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
            label(name, x, y, nameW, r.name() + (r.inBook() ? "  (in your book)" : "") + (r.excluded() ? "  (excluded)" : "")
                    + (warn ? "\nGuard: " + r.guard() : ""));
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
            label(Component.literal("crowd " + l.crowd()).withStyle(l.crowd() <= 3 ? ChatFormatting.GREEN
                            : l.crowd() <= 8 ? ChatFormatting.YELLOW : ChatFormatting.RED), x, y, flowW,
                    String.format(Locale.US, "%d orders near the top price (0.1 wars)\n%s units a day flow through both sides",
                            l.crowd(), FlipMath.fmt(f.dailyUnits())));
            x += flowW + 4;
            String id = f.id();
            button(Component.literal(r.excluded() ? "Include" : "Exclude").withStyle(r.excluded() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    x, y, btnW, r.excluded() ? "Let the book use this item again" : "Keep this item out of your book",
                    () -> { BazaarHelper.toggleExcluded(id); BazaarHelper.show(new MarketScreen()); });
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new MarketScreen()); });
    }
}
