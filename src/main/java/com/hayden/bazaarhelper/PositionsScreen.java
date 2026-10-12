package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Positions: what you hold, what it cost, what it's worth now, and how many slots it takes.
 * Items not in the book any more show as LONG so you unload them.
 */
public final class PositionsScreen extends DeskScreen {
    private static int page = 0;

    public PositionsScreen() {
        super(Tab.POSITIONS, Component.literal("Positions"));
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left(), y = top;
        List<BazaarHelper.PositionRow> rows = BazaarHelper.positionRows();

        double value = 0, cost = 0, mtm = 0;
        int slots = 0;
        for (BazaarHelper.PositionRow r : rows) {
            value += r.held() * r.mid();
            cost += r.costUnits() * r.avgCost();
            mtm += r.mtm();
            slots += r.slots();
        }
        int free = BazaarHelper.freeSlots();
        MutableComponent head = Component.literal("stock worth " + FlipMath.fmt(value)).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("   cost " + FlipMath.fmt(cost)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("   MTM " + signed(mtm)).withStyle(pnlColor(mtm)))
                .append(Component.literal(String.format(Locale.US, "   slots %d used, %d free", slots, free))
                        .withStyle(free < 3 ? ChatFormatting.RED : ChatFormatting.GRAY));
        label(head, left, y, w, "Worth: held units at the mid price.\nCost: what you paid, from your claimed buys.\n"
                + "MTM: worth minus cost on units with a known cost.");
        y += ROW_H;

        if (rows.isEmpty()) {
            label(Component.literal("You don't hold any Bazaar items in your inventory.").withStyle(ChatFormatting.GRAY),
                    left, y, w, "Bids fill into stock here.");
            return;
        }

        int infoW = (int) (w * 0.36), costW = (int) (w * 0.24), mtmW = (int) (w * 0.14), slotW = (int) (w * 0.10);
        int btnW = w - infoW - costW - mtmW - slotW - 16;
        int perPage = rowsThatFit(y);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            BazaarHelper.PositionRow r = rows.get(i);
            int x = left;
            ChatFormatting sc = switch (r.state()) {
                case SHORT -> ChatFormatting.AQUA;
                case BALANCED -> ChatFormatting.GREEN;
                case LONG -> ChatFormatting.LIGHT_PURPLE;
            };
            String icon = switch (r.state()) { case SHORT -> "▼ "; case BALANCED -> "● "; case LONG -> "▲ "; };
            MutableComponent info = Component.literal(icon).withStyle(sc)
                    .append(Component.literal(r.name()).withStyle(r.inBook() ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal("  " + BoardScreen.shortUnits(r.held())
                            + (r.inAsk() > 0 ? " +" + BoardScreen.shortUnits(r.inAsk()) + " listed" : "")
                            + (r.target() > 0 ? " / " + BoardScreen.shortUnits(r.target()) : "")).withStyle(ChatFormatting.GRAY));
            label(info, x, y, infoW, String.format(Locale.US, "%s\nIn inventory %,d, listed %,d\n%s",
                    r.name(), r.held(), r.inAsk(),
                    r.inBook() ? "Target stock " + r.target() + " (" + r.state().name() + ")"
                            : "Not in your book any more: sell it down."));
            x += infoW + 4;
            label(Component.literal(r.costUnits() > 0 ? "cost " + FlipMath.fmt(r.avgCost()) : "cost ?").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("  mid " + (r.mid() > 0 ? FlipMath.fmt(r.mid()) : "-")).withStyle(ChatFormatting.WHITE)),
                    x, y, costW, r.costUnits() > 0
                            ? String.format(Locale.US, "Average cost %s on %,d units", FlipMath.fmt(r.avgCost()), (long) r.costUnits())
                            : "No cost recorded (bought before the mod, or not claimed yet)");
            x += costW + 4;
            label(Component.literal(signed(r.mtm())).withStyle(pnlColor(r.mtm())), x, y, mtmW,
                    "Unrealized gain or loss at the mid price");
            x += mtmW + 4;
            label(Component.literal(r.slots() + " slot" + (r.slots() == 1 ? "" : "s")).withStyle(ChatFormatting.GRAY), x, y, slotW,
                    "Inventory slots this stock takes");
            x += slotW + 4;
            if (r.held() > 0) {
                String id = r.id();
                button(Component.literal("List").withStyle(ChatFormatting.AQUA), x, y, btnW,
                        "Sell offer at the top ask - 0.1 (green slots, price typed in).", () -> { this.onClose(); BazaarHelper.startListAny(id); });
            } else {
                label(Component.literal("listed").withStyle(ChatFormatting.DARK_GRAY), x, y, btnW, "All of it is in your sell offer");
            }
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new PositionsScreen()); });
    }
}
