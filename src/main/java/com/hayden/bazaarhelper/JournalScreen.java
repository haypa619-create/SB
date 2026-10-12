package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Trading journal: realized P&L today / 7 days / all time, then per item: realized, round trips,
 * profit per trip, average time to fill, units bought and sold. The same events are written to
 * config/bazaarhelper-journal.csv for a spreadsheet. Vanilla Buttons only.
 */
public final class JournalScreen extends Screen {
    private static final int ROW_H = 22;
    private static int page = 0;

    public JournalScreen() {
        super(Component.literal("Journal"));
    }

    @Override
    protected void init() {
        QuoteBook d = BazaarHelper.DESK;
        long now = System.currentTimeMillis();
        int w = Math.min(540, this.width - 16);
        int left = (this.width - w) / 2;
        int y = 6;

        MutableComponent head = Component.literal("Journal").withStyle(ChatFormatting.GOLD)
                .append(part("   today ", d.realizedOn(QuoteBook.day(now))))
                .append(part("   7 days ", d.realizedLastDays(7, now)))
                .append(part("   all time ", d.realizedTotal));
        inactive(head, left, y, w, "Realized P&L: spread captured on stock sold, after tax. Days are UTC.\n"
                + "Every fill, claim and cancel is also saved to config/" + Journal.FILE + " (opens in Excel / Sheets).");
        y += ROW_H;

        int ctrlW = 64;
        addRenderableWidget(Button.builder(Component.literal("◀ Board"), b -> BazaarHelper.openBoard())
                .bounds(left, y, ctrlW + 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Where's the file?"), b -> BazaarHelper.sayJournalPath())
                .bounds(left + ctrlW + 20, y, 110, 20)
                .tooltip(Tooltip.create(Component.literal("Prints where the spreadsheet file is saved.")))
                .build());
        y += ROW_H + 6;

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
        if (pages > 1) {
            y += 4;
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; BazaarHelper.show(new JournalScreen()); })
                    .bounds(left, y, 20, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; BazaarHelper.show(new JournalScreen()); })
                    .bounds(left + 24, y, 20, 20).build()).active = page < pages - 1;
        }
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

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (PanelKey.isBoard(e)) { BazaarHelper.openBoard(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
