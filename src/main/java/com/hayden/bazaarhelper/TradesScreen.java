package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Trading: directional trades with a target and a stop, kept apart from market making.
 * An item with an active trade is out of the market-making book until the trade closes.
 */
public final class TradesScreen extends DeskScreen {
    private static int page = 0;

    public TradesScreen() {
        super(Tab.TRADING, Component.literal("Trading"));
    }

    @Override
    protected void initBody(int top) {
        TradeBook tb = BazaarHelper.TRADES;
        double tax = BazaarHelper.CONFIG.taxPercent;
        long now = System.currentTimeMillis();
        int w = contentWidth(), left = left(), y = top;
        List<TradeBook.Trade> active = tb.active();

        double unreal = 0, inTrades = 0;
        for (TradeBook.Trade t : active) {
            BazaarData.Product p = BazaarHelper.DATA.get(t.itemId);
            if (p != null) unreal += t.unrealized(p.topBid(), tax);
            inTrades += t.units * t.avgCost + t.pendingBuy * t.entryPrice;
        }
        double today = tb.realizedOn(QuoteBook.day(now));
        MutableComponent head = Component.literal("Trading").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("   today " + signed(today)).withStyle(pnlColor(today)))
                .append(Component.literal("   all time " + signed(tb.realizedTotal)).withStyle(pnlColor(tb.realizedTotal)))
                .append(Component.literal("   open " + signed(unreal)).withStyle(pnlColor(unreal)))
                .append(Component.literal("   in trades " + FlipMath.fmt(inTrades)).withStyle(ChatFormatting.GRAY));
        label(head, left, y, w, "Trades are separate from market making: their items leave the book,\n"
                + "and their fills and P&L are tracked here only.\nOpen: what open trades would make if sold to the best bid now (after tax).");
        y += ROW_H;
        label(Component.literal("New trade: Market tab > Trade on any item, or /bzh trade <item>").withStyle(ChatFormatting.GRAY),
                left, y, w, "You pick the size, buy order or insta-buy, target % and stop %.");
        y += ROW_H;

        if (active.isEmpty()) {
            label(Component.literal("No open trades.").withStyle(ChatFormatting.DARK_GRAY), left, y, w, "");
            y += ROW_H;
        }

        int infoW = (int) (w * 0.34), pxW = (int) (w * 0.26), pnlW = (int) (w * 0.12);
        int btnW = (w - infoW - pxW - pnlW - 16 - 4) / 2;
        int perPage = Math.max(1, rowsThatFit(y) - 2);
        int pages = Math.max(1, (active.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(active.size(), (page + 1) * perPage); i++) {
            TradeBook.Trade t = active.get(i);
            BazaarData.Product p = BazaarHelper.DATA.get(t.itemId);
            double bid = p == null ? 0 : p.topBid();
            int x = left;
            ChatFormatting sc = switch (t.status) {
                case PLANNED -> ChatFormatting.GRAY;
                case ENTERING -> ChatFormatting.YELLOW;
                case OPEN -> ChatFormatting.GREEN;
                case EXITING -> ChatFormatting.AQUA;
                case CLOSED -> ChatFormatting.DARK_GRAY;
            };
            boolean stop = t.stopHit(bid), target = t.targetHit(bid);
            MutableComponent info = Component.literal(t.status.name() + " ").withStyle(sc)
                    .append(Component.literal(t.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + BoardScreen.shortUnits((long) (t.units > 0 ? t.units : t.plannedUnits)))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(stop ? " STOP" : target ? " TARGET" : "")
                            .withStyle(stop ? ChatFormatting.RED : ChatFormatting.GREEN));
            label(info, x, y, infoW, String.format(Locale.US,
                    "%s (%s)\nPlanned %,d, holding %s @ %s\nEntry order %s, exit offer %s\nOpened %s ago",
                    t.name, t.entry == TradeBook.Entry.ORDER ? "buy order entry" : "insta-buy entry", t.plannedUnits,
                    FlipMath.fmt(t.units), FlipMath.fmt(t.avgCost), FlipMath.fmt(t.pendingBuy), FlipMath.fmt(t.pendingSell),
                    ago(t.openedAt)));
            x += infoW + 4;
            label(Component.literal("now " + (bid > 0 ? FlipMath.fmt(bid) : "-")).withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("  tgt " + FlipMath.fmt(t.target)).withStyle(ChatFormatting.GREEN))
                            .append(Component.literal("  stop " + FlipMath.fmt(t.stop)).withStyle(ChatFormatting.RED)),
                    x, y, pxW, String.format(Locale.US, "Best bid now %s\nTarget %s (%+.1f%% from here)\nStop %s (%+.1f%% from here)",
                            FlipMath.fmt(bid), FlipMath.fmt(t.target), pct(bid, t.target), FlipMath.fmt(t.stop), pct(bid, t.stop)));
            x += pxW + 4;
            double u = t.unrealized(bid, BazaarHelper.CONFIG.taxPercent);
            label(Component.literal(t.units > 0 ? signed(u) : signed(t.realized)).withStyle(pnlColor(t.units > 0 ? u : t.realized)),
                    x, y, pnlW, "Open P&L at the best bid after tax (realized so far " + signed(t.realized) + ")");
            x += pnlW + 4;
            addActions(t, x, y, btnW);
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new TradesScreen()); });

        // recent closed trades
        List<TradeBook.Trade> closed = tb.closed();
        if (!closed.isEmpty() && y + ROW_H * 2 < this.height - 24) {
            y += 8;
            StringBuilder sb = new StringBuilder();
            for (int i = closed.size() - 1, n = 0; i >= 0 && n < 6; i--, n++) {
                TradeBook.Trade t = closed.get(i);
                if (sb.length() > 0) sb.append("   ");
                sb.append(t.name).append(" ").append(signed(t.realized));
            }
            label(Component.literal("Closed: " + sb).withStyle(ChatFormatting.DARK_GRAY), left, y, w,
                    closed.size() + " closed trades. Every trade event is also in the journal file, tagged \"trade\".");
        }
    }

    private void addActions(TradeBook.Trade t, int x, int y, int btnW) {
        switch (t.status) {
            case PLANNED -> {
                button(Component.literal("Enter").withStyle(ChatFormatting.GREEN), x, y, btnW,
                        "Buy now: green slots, amount" + (t.entry == TradeBook.Entry.ORDER ? " and price" : "") + " typed in",
                        () -> { this.onClose(); BazaarHelper.startTradeEntry(t); });
                button(Component.literal("Delete"), x + btnW + 4, y, btnW, "Remove this plan (nothing bought yet)",
                        () -> { BazaarHelper.deleteTrade(t); BazaarHelper.show(new TradesScreen()); });
            }
            case ENTERING -> {
                button(Component.literal("Cancel buy").withStyle(ChatFormatting.GOLD), x, y, btnW,
                        "Take the entry buy order down (green slots through Manage Orders)",
                        () -> { this.onClose(); BazaarHelper.startCancel(t.itemId, QuickOrder.Side.BUY, true); });
                button(Component.literal("Claim").withStyle(ChatFormatting.YELLOW), x + btnW + 4, y, btnW,
                        "Open Manage Orders to claim what filled",
                        () -> { this.onClose(); BazaarHelper.startSync(); });
            }
            case OPEN -> {
                int b3 = (2 * btnW + 4 - 8) / 3;
                button(Component.literal("List").withStyle(ChatFormatting.AQUA), x, y, b3,
                        "Sell offer at your target " + FlipMath.fmt(t.target) + ", price typed in",
                        () -> { this.onClose(); BazaarHelper.startTradeExit(t); });
                button(Component.literal("Sell").withStyle(ChatFormatting.RED), x + b3 + 4, y, b3,
                        "Insta-sell now to the best bid (green Sell Instantly slot)",
                        () -> { this.onClose(); BazaarHelper.startInstaSell(t); });
                button(Component.literal("Close"), x + 2 * (b3 + 4), y, b3,
                        "Stop tracking (e.g. you sold it another way). Keeps realized P&L.\nThe item can go back in the market-making book.",
                        () -> { BazaarHelper.closeTrade(t); BazaarHelper.show(new TradesScreen()); });
            }
            case EXITING -> {
                button(Component.literal("Sell now").withStyle(ChatFormatting.RED), x, y, btnW,
                        "Cancel the offer first (Orders tab), then insta-sell. Or wait for the target.",
                        () -> { this.onClose(); BazaarHelper.startCancel(t.itemId, QuickOrder.Side.SELL, true); });
                button(Component.literal("Close"), x + btnW + 4, y, btnW,
                        "Stop tracking this trade (keeps realized P&L). The item can go back in the book.",
                        () -> { BazaarHelper.closeTrade(t); BazaarHelper.show(new TradesScreen()); });
            }
            case CLOSED -> {}
        }
    }

    private static double pct(double from, double to) {
        return from > 0 ? (to / from - 1) * 100 : 0;
    }
}
