package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Trading journal: realized P&L today / 7 days / all time, then per item: realized, round trips,
 * profit per trip, average time to fill, units bought and sold. The same events are written to
 * config/bazaarhelper-journal.csv for a spreadsheet. Vanilla Buttons only.
 */
public final class JournalScreen extends DeskScreen {
    private static int page = 0;

    public JournalScreen() {
        super(Tab.JOURNAL, Component.literal("Journal"));
    }

    @Override
    protected void initBody(int top) {
        QuoteBook d = BazaarHelper.DESK;
        long now = System.currentTimeMillis();
        int w = contentWidth();
        int left = left();
        int y = top;

        MutableComponent head = Component.literal("Journal").withStyle(ChatFormatting.GOLD)
                .append(part("   today ", d.realizedOn(QuoteBook.day(now))))
                .append(part("   7 days ", d.realizedLastDays(7, now)))
                .append(part("   all time ", d.realizedTotal))
                .append(Component.literal("   trading").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(part(" ", BazaarHelper.TRADES.realizedOn(QuoteBook.day(now))))
                .append(part(" / ", BazaarHelper.TRADES.realizedTotal));
        int fileW = 110;
        inactive(head, left, y, w - fileW - 4, "Market making realized P&L (spread captured, after tax), then trading: today / all time.\nThe two are kept separate. Days are UTC.\n"
                + "Every fill, claim and cancel is also saved to config/" + Journal.FILE + " (opens in Excel / Sheets).");
        button(Component.literal("Where's the file?"), left + w - fileW, y, fileW,
                "Prints where the spreadsheet file is saved.", BazaarHelper::sayJournalPath);
        y += ROW_H;

        // last 7 days, one small box each
        int dayW = (w - 6 * 2) / 7;
        for (int i = 6; i >= 0; i--) {
            long t = now - i * 86_400_000L;
            String day = QuoteBook.day(t);
            double r = d.realizedOn(day);
            int x = left + (6 - i) * (dayW + 2);
            inactive(Component.literal(day.substring(5) + " ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(BoardScreen.signed(r)).withStyle(r > 0 ? ChatFormatting.GREEN : r < 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY)),
                    x, y, dayW, day + " (UTC)\nRealized " + BoardScreen.signed(r) + "\nTraded " + FlipMath.fmt(d.volumeOn(day)));
        }
        y += ROW_H + 4;

        List<Journal.ItemStats> stats = Journal.stats(d);
        if (stats.isEmpty()) {
            inactive(Component.literal("Nothing yet. Fills and claims show up here as you trade.").withStyle(ChatFormatting.GRAY),
                    left, y, w, "Claimed buys set your cost, claimed sells realize P&L.");
            return;
        }
        inactive(Component.literal("Item   realized   round trips   profit per trip   avg time to fill")
                .withStyle(ChatFormatting.GRAY), left, y, w, "Sorted by realized P&L. Drop items that don't pay.");
        y += ROW_H;

        int perPage = Math.max(1, (this.height - y - 28) / ROW_H);
        int pages = (stats.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(stats.size(), (page + 1) * perPage); i++) {
            Journal.ItemStats st = stats.get(i);
            MutableComponent row = Component.literal(st.name()).withStyle(ChatFormatting.WHITE)
                    .append(part("   ", st.realized()))
                    .append(Component.literal(String.format(Locale.US, "   %d trips   %s/trip   fills in %s",
                            st.roundTrips(), FlipMath.fmt(st.realizedPerTrip()), minutes(st.avgFillMinutes())))
                            .withStyle(ChatFormatting.GRAY));
            inactive(row, left, y, w, String.format(Locale.US, "%s\nBought %s units, sold %s units\nAverage time from order up to filled: %s",
                    st.name(), FlipMath.fmt(st.bought()), FlipMath.fmt(st.sold()), minutes(st.avgFillMinutes())));
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new JournalScreen()); });
    }

    private void inactive(Component label, int x, int y, int w, String tip) {
        Button b = Button.builder(label, x2 -> {}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
        b.active = false;
        addRenderableWidget(b);
    }

    private static MutableComponent part(String label, double v) {
        return Component.literal(label + BoardScreen.signed(v)).withStyle(v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    static String minutes(double m) {
        if (m <= 0) return "n/a";
        if (m < 60) return String.format(Locale.US, "%.0fm", m);
        return String.format(Locale.US, "%.1fh", m / 60);
    }

}
