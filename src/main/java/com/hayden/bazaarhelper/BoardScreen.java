package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * The quote board.
 *
 *   [ desk: coins | slots | realized | MTM ]
 *   [ Next: <most urgent> (N to do) ] [ Settings ] [ Close ]
 *   [x] [ item  position/target  STATE  crowd ] [ BID side ] [ ASK side ]
 *   ...
 *
 * Built only from vanilla Buttons so it survives Minecraft rendering changes.
 * PLACE and OUTBID sides are clickable; live and hold sides are not.
 */
public final class BoardScreen extends Screen {
    public record Row(Planner.Line line, String name, long inventory, MarketMaker.Quote quote,
                      QuoteBook.Item desk, double mtm) {}

    public record Header(Planner.Book book, int freeSlots, double realized, double mtm,
                         NextAction.Pick next, int todo, long dataTime) {}

    private static final int ROW_H = 22;
    private static final int SIDE_W = 116;
    private static final int X_W = 16;

    /** True while a board is on screen, so the mod knows when to auto-refresh. */
    private static volatile boolean open;
    /** Page survives refreshes. */
    private static int page = 0;

    private final Header header;
    private final List<Row> rows;

    public BoardScreen(Header header, List<Row> rows) {
        super(Component.literal("Quote Board"));
        this.header = header;
        this.rows = rows;
    }

    public static boolean isOpen() {
        return open;
    }

    @Override
    protected void init() {
        open = true;
        int w = Math.min(540, this.width - 16);
        int left = (this.width - w) / 2;
        int y = 6;
        Planner.Book book = header.book();

        // ---- header line 1: the desk ----
        boolean slotsTight = header.freeSlots() < Math.min(3, book.slotsUsed());
        MutableComponent sum = Component.literal("◆ " + FlipMath.fmt(book.total())).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(BazaarHelper.coinsAreAuto() ? " auto" : " manual").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format(Locale.US, "   slots %d/%d", book.slotsUsed(), book.slotBudget()))
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.US, " (%d free)", header.freeSlots()))
                        .withStyle(slotsTight ? ChatFormatting.RED : ChatFormatting.DARK_GRAY))
                .append(Component.literal("   realized " + signed(header.realized()))
                        .withStyle(header.realized() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("   MTM " + signed(header.mtm()))
                        .withStyle(header.mtm() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("   " + age(header.dataTime())).withStyle(ChatFormatting.DARK_GRAY));
        Button summary = Button.builder(sum, b -> {}).bounds(left, y, w, 20)
                .tooltip(Tooltip.create(Component.literal(String.format(Locale.US,
                        "%s\n\nCommitted %s (stock + bids)  |  reserve %s  |  idle %s\nEst. %s/day from spread\n\n"
                                + "Realized: spread captured on stock sold, after tax\n"
                                + "MTM: unrealized gain or loss on stock held, at mid price\n"
                                + "Idle: coins the book can't place (slots or liquidity ran out)%s",
                        BazaarHelper.capitalNote(), FlipMath.fmt(book.committed()), FlipMath.fmt(book.reserve()), FlipMath.fmt(book.idle()),
                        FlipMath.fmt(book.estDailyProfit()),
                        slotsTight ? "\n\nInventory nearly full: claimed fills will go to your stash." : ""))))
                .build();
        summary.active = false;
        addRenderableWidget(summary);
        y += 22;

        // ---- header line 2: controls ----
        int ctrlW = 64;
        int nextW = w - 2 * (ctrlW + 4);
        NextAction.Pick next = header.next();
        if (next != null) {
            addRenderableWidget(Button.builder(Component.literal("▶ Next: " + next.describe())
                                    .withStyle(next.priority() == 0 ? ChatFormatting.RED : ChatFormatting.GREEN)
                                    .append(Component.literal("  (" + header.todo() + " to do)").withStyle(ChatFormatting.GRAY)),
                            b -> { this.onClose(); BazaarHelper.startNext(); })
                    .bounds(left, y, nextW, 20)
                    .tooltip(Tooltip.create(Component.literal(
                            "Does the most urgent thing on the board (hotkey: N).\n"
                                    + "Order: re-price outbid quotes, unload LONG items,\nrebuild SHORT items, then list stock and bid.")))
                    .build());
        } else {
            Button done = Button.builder(Component.literal("✔ All quotes live. Nothing to do.").withStyle(ChatFormatting.DARK_GREEN), b -> {})
                    .bounds(left, y, nextW, 20).build();
            done.active = false;
            addRenderableWidget(done);
        }
        int cx = left + nextW + 4;
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> BazaarHelper.show(new SettingsScreen(false)))
                .bounds(cx, y, ctrlW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(cx + ctrlW + 4, y, ctrlW, 20).build());
        y += 28;

        if (rows.isEmpty()) {
            Button empty = Button.builder(Component.literal(
                            "No items pass your filters. Open Settings and lower Min weekly units or Max crowd."), b -> {})
                    .bounds(left, y, w, 20).build();
            empty.active = false;
            addRenderableWidget(empty);
            return;
        }

        // ---- rows (paged if the window is short) ----
        int perPage = Math.max(1, (this.height - y - 28) / ROW_H);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        int infoW = w - X_W - 2 * SIDE_W - 12;
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            Row r = rows.get(i);
            addRenderableWidget(excludeButton(r, left, y));
            addRenderableWidget(infoButton(r, left + X_W + 4, y, infoW));
            addRenderableWidget(bidButton(r, left + X_W + infoW + 8, y));
            addRenderableWidget(askButton(r, left + X_W + infoW + SIDE_W + 12, y));
            y += ROW_H;
        }

        if (pages > 1) {
            y += 4;
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; BazaarHelper.show(new BoardScreen(header, rows)); })
                    .bounds(left, y, 20, 20).build()).active = page > 0;
            Button pg = Button.builder(Component.literal("Page " + (page + 1) + " of " + pages).withStyle(ChatFormatting.GRAY), b -> {})
                    .bounds(left + 24, y, 90, 20).build();
            pg.active = false;
            addRenderableWidget(pg);
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; BazaarHelper.show(new BoardScreen(header, rows)); })
                    .bounds(left + 118, y, 20, 20).build()).active = page < pages - 1;
        }
    }

    @Override
    public void removed() {
        open = false;
        super.removed();
    }

    // ---------- row widgets ----------

    private Button excludeButton(Row r, int x, int y) {
        String id = r.line().flip().id();
        return Button.builder(Component.literal("✕").withStyle(ChatFormatting.DARK_GRAY), b -> BazaarHelper.excludeItem(id))
                .bounds(x, y, X_W, 20)
                .tooltip(Tooltip.create(Component.literal("Remove " + r.name() + " from the book.\nBring it back in Settings > Clear excluded.")))
                .build();
    }

    private Button infoButton(Row r, int x, int y, int width) {
        MarketMaker.Quote q = r.quote();
        ChatFormatting stateColor = switch (q.state()) {
            case SHORT -> ChatFormatting.AQUA;
            case BALANCED -> ChatFormatting.GREEN;
            case LONG -> ChatFormatting.LIGHT_PURPLE;
        };
        String stateIcon = switch (q.state()) {
            case SHORT -> "▼ ";
            case BALANCED -> "● ";
            case LONG -> "▲ ";
        };
        Planner.Line l = r.line();
        MutableComponent label = Component.literal(stateIcon).withStyle(stateColor)
                .append(Component.literal(r.name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format(Locale.US, "  %s/%s", shortUnits(q.position()), shortUnits(l.targetUnits())))
                        .withStyle(ChatFormatting.GRAY))
                .append(BazaarHelper.crowdTag(l.flip().id()));
        QuoteBook.Item d = r.desk();
        String deskLine = d == null || d.costUnits <= 0 ? "No cost basis yet (your claims set it)"
                : String.format(Locale.US, "Avg cost %s on %,d units, MTM %s",
                FlipMath.fmt(d.avgCost), (long) d.costUnits, signed(r.mtm()));
        Button b = Button.builder(label, x2 -> {}).bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal(String.format(Locale.US,
                        "%s  (%s)\nTarget stock %,d: %d slot%s, stacks of %d\nIn inventory %,d, in live ask %,d\n"
                                + "Spread %s / %s, +%s/unit after tax (%.2f%%)\n"
                                + "Est. %s/day, %s per slot\nRealized %s over %d round trips\n%s%s",
                        r.name(), q.state().name(), l.targetUnits(), l.slots(), l.slots() == 1 ? "" : "s", l.stackSize(),
                        r.inventory(), q.position() - r.inventory(),
                        FlipMath.fmt(l.flip().buyAt()), FlipMath.fmt(l.flip().sellAt()),
                        FlipMath.fmt(l.flip().profitPerUnit()), l.flip().marginPct(),
                        FlipMath.fmt(l.estDailyProfit()), FlipMath.fmt(l.profitPerSlot()),
                        signed(d == null ? 0 : d.realized), d == null ? 0 : d.roundTrips,
                        deskLine, q.note().isEmpty() ? "" : "\n\n" + q.note()))))
                .build();
        b.active = false;
        return b;
    }

    private Button bidButton(Row r, int x, int y) {
        MarketMaker.Quote q = r.quote();
        String id = r.line().flip().id();
        return switch (q.bidAction()) {
            case PLACE -> clickable(Component.literal("Bid " + shortUnits(q.bidSize()) + " @ " + FlipMath.fmt(q.bidPrice()))
                            .withStyle(ChatFormatting.GREEN),
                    "Re-checks the price, opens /bz " + r.name() + ".\nCustom Amount and Custom Price get filled in.",
                    x, y, () -> BazaarHelper.startQuote(id, QuickOrder.Side.BUY));
            case OUTBID -> clickable(Component.literal("⚠ Bid outbid").withStyle(ChatFormatting.RED),
                    "Someone's above your bid. Opens /bz " + r.name()
                            + ".\nRe-price from Your Orders with Bazaar Utils' sign button.",
                    x, y, () -> BazaarHelper.openBazaarFor(id));
            case LIVE -> inactive(Component.literal("✔ Bid live").withStyle(ChatFormatting.DARK_GREEN), x, y,
                    "Your bid is on top.");
            case HOLD, EMPTY -> inactive(Component.literal("Bid hold").withStyle(ChatFormatting.DARK_GRAY), x, y,
                    q.note().isEmpty() ? "Bid size is covered by what you hold." : q.note());
        };
    }

    private Button askButton(Row r, int x, int y) {
        MarketMaker.Quote q = r.quote();
        String id = r.line().flip().id();
        return switch (q.askAction()) {
            case PLACE -> clickable(Component.literal("Ask " + shortUnits(q.askSize()) + " @ " + FlipMath.fmt(q.askPrice()))
                            .withStyle(ChatFormatting.AQUA),
                    "Re-checks the price, opens /bz " + r.name() + ".\nCustom Price gets filled in. Lists all of it you carry.",
                    x, y, () -> BazaarHelper.startQuote(id, QuickOrder.Side.SELL));
            case OUTBID -> clickable(Component.literal("⚠ Ask outbid").withStyle(ChatFormatting.RED),
                    "Someone's below your ask. Opens /bz " + r.name()
                            + ".\nRe-price from Your Orders with Bazaar Utils' sign button.",
                    x, y, () -> BazaarHelper.openBazaarFor(id));
            case LIVE -> inactive(Component.literal("✔ Ask live").withStyle(ChatFormatting.DARK_AQUA), x, y,
                    "Your ask is on top.");
            case HOLD, EMPTY -> inactive(Component.literal("No stock").withStyle(ChatFormatting.DARK_GRAY), x, y,
                    "Nothing in your inventory to list yet. Bids fill into stock.");
        };
    }

    private Button clickable(Component label, String tip, int x, int y, Runnable action) {
        return Button.builder(label, b -> {
                    this.onClose();
                    action.run();
                })
                .bounds(x, y, SIDE_W, 20)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
    }

    private Button inactive(Component label, int x, int y, String tip) {
        Button b = Button.builder(label, x2 -> {}).bounds(x, y, SIDE_W, 20)
                .tooltip(Tooltip.create(Component.literal(tip))).build();
        b.active = false;
        return b;
    }

    // ---------- formatting ----------

    static String shortUnits(long u) {
        return u >= 10_000 ? FlipMath.fmt(u) : String.format(Locale.US, "%,d", u);
    }

    static String signed(double v) {
        return (v >= 0 ? "+" : "") + FlipMath.fmt(v);
    }

    static String age(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        return s < 2 ? "live" : s + "s ago";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
