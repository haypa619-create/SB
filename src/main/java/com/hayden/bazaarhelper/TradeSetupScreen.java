package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Set up a directional trade on one item: size, entry (buy order or insta-buy), target %
 * and stop %. Shows cost, reward, risk and reward:risk after tax before you commit.
 */
public final class TradeSetupScreen extends DeskScreen {
    private final String id;
    private long units;
    private TradeBook.Entry entry = TradeBook.Entry.ORDER;
    private double targetPct = 5, stopPct = 5;

    public TradeSetupScreen(String id) {
        super(Tab.TRADING, Component.literal("New Trade"));
        this.id = id;
        this.units = Math.max(1, BazaarHelper.DATA.stackSize(id));
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left(), y = top;
        BazaarData.Product p = BazaarHelper.DATA.get(id);
        String name = BazaarHelper.DATA.nameFor(id);
        double tax = BazaarHelper.CONFIG.taxPercent;
        if (p == null) {
            label(Component.literal("No price data for " + name + " yet.").withStyle(ChatFormatting.RED), left, y, w, "");
            return;
        }
        double price = entry == TradeBook.Entry.ORDER ? FlipMath.round1(p.topBid() + FlipMath.TICK) : p.topAsk();
        double target = TradeBook.pctPrice(price, targetPct), stop = TradeBook.pctPrice(price, -stopPct);
        double cost = units * price;
        double reward = units * (target * (1 - tax / 100.0) - price);
        double risk = units * (price - stop * (1 - tax / 100.0));
        double rr = TradeBook.rewardRisk(price, target, stop, tax);

        label(Component.literal("New trade: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.format(Locale.US, "   bid %s / ask %s", FlipMath.fmt(p.topBid()), FlipMath.fmt(p.topAsk())))
                                .withStyle(ChatFormatting.GRAY)),
                left, y, w, "While this trade is open, " + name + " is out of your market-making book.");
        y += ROW_H + 4;

        int colW = (w - 8) / 2, right = left + colW + 8;
        long stack = Math.max(1, BazaarHelper.DATA.stackSize(id));
        row(left, y, colW, "Units", BoardScreen.shortUnits(units) + " (" + (int) Math.ceil(units / (double) stack) + " slots)",
                () -> units = Math.max(1, units - stack), () -> units = units + stack,
                "Steps of one stack (" + stack + "). Use x10 for big sizes.");
        Button x10 = button(Component.literal("x10"), right, y, 40, "Ten times the size", () -> { units = units * 10; rebuild(); });
        button(Component.literal("÷10"), right + 44, y, 40, "A tenth of the size", () -> { units = Math.max(1, units / 10); rebuild(); });
        x10.active = units < 1_000_000;
        y += ROW_H;
        button(Component.literal("Entry: " + (entry == TradeBook.Entry.ORDER ? "buy order @ best bid + 0.1" : "insta-buy @ best ask"))
                        .withStyle(ChatFormatting.YELLOW), left, y, colW,
                "Buy order: cheaper, waits for a seller. Insta-buy: right now, pays the spread.",
                () -> { entry = entry == TradeBook.Entry.ORDER ? TradeBook.Entry.INSTA : TradeBook.Entry.ORDER; rebuild(); });
        y += ROW_H;
        row(left, y, colW, "Target", String.format(Locale.US, "+%s%%  (%s)", SettingsScreen.trim(targetPct), FlipMath.fmt(target)),
                () -> targetPct = Math.max(0.5, targetPct - 0.5), () -> targetPct = Math.min(500, targetPct + 0.5),
                "Sell offer price when you exit, as % above your entry");
        row(right, y, colW, "Stop", String.format(Locale.US, "-%s%%  (%s)", SettingsScreen.trim(stopPct), FlipMath.fmt(stop)),
                () -> stopPct = Math.max(0.5, stopPct - 0.5), () -> stopPct = Math.min(90, stopPct + 0.5),
                "If the best bid falls to this, the mod alerts you and N sells now");
        y += ROW_H + 4;

        double purse = BazaarHelper.detectedPurse();
        boolean tooBig = purse >= 0 && cost > purse;
        MutableComponent sum = Component.literal("cost " + FlipMath.fmt(cost)).withStyle(tooBig ? ChatFormatting.RED : ChatFormatting.GOLD)
                .append(Component.literal("   reward " + signed(reward)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   risk -" + FlipMath.fmt(risk)).withStyle(ChatFormatting.RED))
                .append(Component.literal(String.format(Locale.US, "   reward:risk %.1f", rr))
                        .withStyle(rr >= 2 ? ChatFormatting.GREEN : rr >= 1 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        label(sum, left, y, w, "After the " + SettingsScreen.trim(tax) + "% tax on the sale."
                + (tooBig ? "\nMore than your purse (" + FlipMath.fmt(purse) + ")." : "")
                + "\nThe guard's view: " + BazaarHelper.guardInfo(id).trim());
        y += ROW_H + 6;

        button(Component.literal("Open trade and buy").withStyle(ChatFormatting.GREEN), left, y, colW,
                "Saves the trade, then opens /bz " + name + " with green slots and the numbers typed in",
                () -> { this.onClose(); BazaarHelper.openTrade(id, units, entry, targetPct, stopPct); });
        button(Component.literal("Cancel"), right, y, colW, "Back to the Trading tab", () -> BazaarHelper.openTab(Tab.TRADING));
    }

    /** [ name ] [ - ] [ value ] [ + ] */
    private void row(int x, int y, int w, String name, String value, Runnable down, Runnable up, String tip) {
        int btn = 20, valW = Math.max(90, w / 2 - btn);
        int labelW = w - valW - 2 * btn - 6;
        label(Component.literal(name), x, y, labelW, tip);
        button(Component.literal("-"), x + labelW + 2, y, btn, "", () -> { down.run(); rebuild(); });
        label(Component.literal(value).withStyle(ChatFormatting.YELLOW), x + labelW + btn + 4, y, valW, tip);
        button(Component.literal("+"), x + w - btn, y, btn, "", () -> { up.run(); rebuild(); });
    }

    private void rebuild() {
        this.rebuildWidgets();
    }
}
