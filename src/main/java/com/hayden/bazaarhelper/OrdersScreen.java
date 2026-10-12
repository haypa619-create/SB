package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Open orders: every live bid and ask, whether it's on top, outbid or should be pulled,
 * how long it's been up, and what's waiting to claim. Problems sort to the top.
 */
public final class OrdersScreen extends DeskScreen {
    private static int page = 0;

    public OrdersScreen() {
        super(Tab.ORDERS, Component.literal("Open Orders"));
    }

    @Override
    protected void initBody(int top) {
        QuoteBook d = BazaarHelper.DESK;
        int w = contentWidth(), left = left(), y = top;
        List<BazaarHelper.OrderRow> rows = BazaarHelper.orderRows();

        double inBids = 0, inAsks = 0;
        for (BazaarHelper.OrderRow r : rows) {
            if (r.side() == QuickOrder.Side.BUY) inBids += r.units() * r.price(); else inAsks += r.units() * r.price();
        }
        int live = d.liveOrders(), cap = BazaarHelper.CONFIG.maxOrders;
        MutableComponent head = Component.literal(String.format(Locale.US, "orders %d/%d", live, cap))
                .withStyle(live >= cap ? ChatFormatting.RED : ChatFormatting.GOLD)
                .append(Component.literal("   in bids " + FlipMath.fmt(inBids)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   asks worth " + FlipMath.fmt(inAsks)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   " + (d.syncedAt > 0 ? "synced " + ago(d.syncedAt) + " ago" : "never synced"))
                        .withStyle(ChatFormatting.DARK_GRAY));
        int syncW = 110;
        label(head, left, y, w - syncW - 4, "Coins in bids are locked until they fill or you cancel.\n"
                + "Synced = last time you opened Manage Orders; between syncs the mod follows Bazaar chat.");
        button(Component.literal("Sync / claim").withStyle(ChatFormatting.YELLOW), left + w - syncW, y, syncW,
                "Opens /bz and highlights Manage Orders.\nOpening it syncs every order; click filled ones there to claim.",
                () -> { this.onClose(); BazaarHelper.startSync(); });
        y += ROW_H;

        if (d.toClaim != null && !d.toClaim.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> e : d.toClaim.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(Journal.title(e.getKey())).append(" ").append(FlipMath.fmt(e.getValue()));
            }
            label(Component.literal("To claim: " + sb).withStyle(ChatFormatting.YELLOW), left, y, w,
                    "Filled and waiting in Manage Orders. Claim them so stock and coins come back to you.");
            y += ROW_H;
        }

        if (rows.isEmpty()) {
            label(Component.literal("No open orders. Press N or use the Quotes tab to put some up.").withStyle(ChatFormatting.GRAY),
                    left, y, w, "If you have orders the mod doesn't know about, hit Sync / claim.");
            return;
        }

        int infoW = (int) (w * 0.40), priceW = (int) (w * 0.24), statusW = (int) (w * 0.20);
        int btnW = w - infoW - priceW - statusW - 12;
        int perPage = rowsThatFit(y);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            BazaarHelper.OrderRow r = rows.get(i);
            boolean buy = r.side() == QuickOrder.Side.BUY;
            int x = left;
            MutableComponent info = Component.literal(buy ? "BID " : "ASK ").withStyle(buy ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                    .append(Component.literal(r.name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + BoardScreen.shortUnits(r.units()) + (r.orders() > 1 ? " (" + r.orders() + " orders)" : ""))
                            .withStyle(ChatFormatting.GRAY));
            label(info, x, y, infoW, String.format(Locale.US, "%s %s\n%,d units at %s = %s\nUp for %s",
                    buy ? "Buy order" : "Sell offer", r.name(), r.units(), FlipMath.fmt(r.price()),
                    FlipMath.fmt(r.units() * r.price()), ago(r.setAt())));
            x += infoW + 4;
            label(Component.literal(FlipMath.fmt(r.price())).withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(" top " + (r.top() > 0 ? FlipMath.fmt(r.top()) : "-")).withStyle(ChatFormatting.DARK_GRAY)),
                    x, y, priceW, "Your price, and the best " + (buy ? "bid" : "ask") + " on the Bazaar now");
            x += priceW + 4;
            label(Component.literal(r.status()).withStyle(r.color()), x, y, statusW, statusTip(r));
            x += statusW + 4;
            boolean problem = r.pull() || r.color() == ChatFormatting.GOLD;
            String text = r.pull() ? "Pull" : problem ? "Re-price" : "Cancel";
            if (r.id() == null) {
                label(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY), x, y, btnW, "No price data for this item");
            } else {
                QuickOrder.Side side = r.side();
                String id = r.id();
                boolean thenRequote = problem && !r.pull();
                button(Component.literal(text).withStyle(r.pull() ? ChatFormatting.RED : problem ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                        x, y, btnW, "Green slots lead you through Manage Orders > this order > Cancel."
                                + (thenRequote ? "\nThen N puts it back up at the new price." : ""),
                        () -> { this.onClose(); BazaarHelper.startCancel(id, side, !thenRequote); });
            }
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new OrdersScreen()); });
    }

    private static String statusTip(BazaarHelper.OrderRow r) {
        if (r.pull()) return "The guard says this item is falling fast. Pull the bid before it fills into a loss.";
        if (r.color() == ChatFormatting.GOLD) return "Someone's ahead of you. You won't fill until you re-price.";
        if (r.status().contains("shared")) return "You're at the best price, but others are too. Fills are shared.";
        return "You're the best price. Fills come to you first.";
    }
}
