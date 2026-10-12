package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

/**
 * Desk settings with buttons instead of commands. Also the first-run setup.
 * Each row: [ label ] [ - ] [ value ] [ + ]   (cycles have one button that steps through values)
 * Built only from vanilla Buttons.
 */
public final class SettingsScreen extends DeskScreen {
    private static final int ROW_H = 22;
    private final boolean firstRun;

    public SettingsScreen(boolean firstRun) {
        super(Tab.SETTINGS, Component.literal(firstRun ? "Bazaar Helper Setup" : "Desk Settings"));
        this.firstRun = firstRun;
    }

    @Override
    protected boolean showTabs() {
        return !firstRun;
    }

    @Override
    protected void initBody(int top) {
        Config c = BazaarHelper.CONFIG;
        int colW = Math.min(240, (this.width - 24) / 2);
        int left = (this.width - (2 * colW + 8)) / 2;
        int right = left + colW + 8;
        int y0 = top + 2;

        Button title = Button.builder(Component.literal(firstRun
                        ? "Welcome. Set up your desk, then hit Done (change any of this later with Settings on the board)."
                        : "Desk settings: saved as you click").withStyle(ChatFormatting.GOLD), b -> {})
                .bounds(left, y0, 2 * colW + 8, 20).build();
        title.active = false;
        addRenderableWidget(title);
        int y = y0 + 28;

        // ---- left column: money and inventory ----
        int ly = y;
        coinsMode(left, ly, colW, c);
        ly += ROW_H;
        stepper(left, ly, colW, "Manual coins", "Only used when Coins is set to Manual, or before your purse shows on the sidebar.",
                () -> c.defaultTotal, d -> c.defaultTotal = clamp(c.defaultTotal + d * coinStep(c.defaultTotal), 10e6, 1e11),
                v -> FlipMath.fmt(v));
        ly += ROW_H;
        cycle(left, ly, colW, "Bazaar tax", "1.25% normally. Lower with the Bazaar Flipper account upgrade.",
                new double[]{1.25, 1.125, 1.0}, () -> c.taxPercent, v -> c.taxPercent = v, v -> trim(v) + "%");
        ly += ROW_H;
        stepper(left, ly, colW, "Inventory slots", "Slots you give to stock. 27 = main inventory, 36 = with hotbar.",
                () -> c.slotBudget, d -> c.slotBudget = (int) clamp(c.slotBudget + d, 1, 36), v -> Integer.toString((int) v));
        ly += ROW_H;
        stepper(left, ly, colW, "Reserve", "Share of coins kept out of the book.",
                () -> c.reservePct, d -> c.reservePct = clamp(c.reservePct + d * 5, 0, 90), v -> trim(v) + "%");
        ly += ROW_H;
        stepper(left, ly, colW, "Max per item", "Max share of coins (stock + bid) in one item.",
                () -> c.maxItemPct, d -> c.maxItemPct = clamp(c.maxItemPct + d * 2, 2, 100), v -> trim(v) + "%");

        // ---- right column: what makes the book ----
        int ry = y;
        stepper(right, ry, colW, "Min weekly units", "Units traded per week on the thinner side. Deep two-way flow only.",
                () -> c.minWeeklyUnits, d -> c.minWeeklyUnits = clamp(c.minWeeklyUnits + d * unitStep(c.minWeeklyUnits), 0, 1e9),
                v -> FlipMath.fmt(v));
        ry += ROW_H;
        stepper(right, ry, colW, "Stock hours", "Target stock = this many hours of turnover. Lower = fewer slots per item.",
                () -> c.stockHours, d -> c.stockHours = clamp(c.stockHours + d * (c.stockHours < 2 ? 0.5 : 1), 0.5, 48),
                v -> trim(v) + "h");
        ry += ROW_H;
        stepper(right, ry, colW, "Max items", "Most items quoted at once. Each uses a buy order and a sell offer.",
                () -> c.maxItems, d -> c.maxItems = (int) clamp(c.maxItems + d, 1, 30), v -> Integer.toString((int) v));
        ry += ROW_H;
        stepper(right, ry, colW, "Skew", "Share of the spread to lean by when long or short.",
                () -> c.skewShare, d -> c.skewShare = clamp(Math.round((c.skewShare + d * 0.05) * 100) / 100.0, 0, 0.9),
                v -> String.format(Locale.US, "%.2f", v));
        ry += ROW_H;
        stepper(right, ry, colW, "Max crowd", "Skip items with more competing orders than this near the top.",
                () -> c.maxCrowd, d -> c.maxCrowd = (int) clamp(c.maxCrowd + d * 2, 1, 100), v -> Integer.toString((int) v));
        ry += ROW_H;
        toggle(right, ry, colW, "Chat notes", "Short chat lines when quotes go up, fill, and realize P&L.",
                () -> c.chatNotes, on -> c.chatNotes = on);

        // ---- protection and limits ----
        int gy = y + 6 * ROW_H + 6;
        stepper(left, gy, colW, "Max open orders", "Hypixel's cap on open Bazaar orders. Next stops adding orders at the cap\n"
                        + "and the book plans at most half this many items. Raise it if your cap is higher.",
                () -> c.maxOrders, d -> c.maxOrders = (int) clamp(c.maxOrders + d, 1, 100), v -> Integer.toString((int) v));
        toggle(right, gy, colW, "Guard", "Stops bidding (and tells you to pull live bids) when an item is falling fast,\n"
                        + "stretched above its average, its spread blows out, or one big order props up the top.\n"
                        + "Also prices around a fair value from the mod's own price history.",
                () -> c.guardOn, on -> c.guardOn = on);
        gy += ROW_H;
        toggle(left, gy, colW, "Sounds", "Ping on fills; low note on outbid, undercut and pull warnings.",
                () -> c.sounds, on -> c.sounds = on);
        stepper(right, gy, colW, "Pull on drop", "Pull bids when the price falls this much in 30 minutes.",
                () -> c.guardDropPct, d -> c.guardDropPct = clamp(c.guardDropPct + d * 0.5, 0.5, 50), v -> trim(v) + "%");

        int by = gy + ROW_H + 8;

        // ---- footer ----
        int third = (2 * colW + 8 - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Clear excluded (" + c.excluded.size() + ")"), b -> {
                    c.excluded.clear();
                    c.save();
                    b.setMessage(Component.literal("Clear excluded (0)"));
                })
                .bounds(left, by, third, 20)
                .tooltip(Tooltip.create(Component.literal("Bring back every item you removed with X on the board.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset quote tracking"), b -> {
                    BazaarHelper.resetQuoteTracking();
                    b.setMessage(Component.literal("Tracking reset").withStyle(ChatFormatting.GREEN));
                })
                .bounds(left + third + 4, by, third, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Forget which bids and asks the mod thinks are live.\nUse if the board shows quotes you already cancelled.\nCost basis and P&L are kept.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal(firstRun ? "Done: open board" : "Done").withStyle(ChatFormatting.GREEN), b -> {
                    c.setupDone = true;
                    c.save();
                    BazaarHelper.openBoard();
                })
                .bounds(left + 2 * (third + 4), by, third, 20).build());
    }

    // ---------- row builders ----------

    private interface Fmt { String of(double v); }
    private interface BoolSupplier { boolean get(); }
    private interface BoolConsumer { void accept(boolean v); }

    private void label(int x, int y, int w, String text, String tip) {
        Button b = Button.builder(Component.literal(text).withStyle(ChatFormatting.WHITE), x2 -> {})
                .bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
        b.active = false;
        addRenderableWidget(b);
    }

    private void stepper(int x, int y, int w, String name, String tip, DoubleSupplier get, IntConsumer step, Fmt fmt) {
        int valW = 64, btnW = 20;
        int labelW = w - valW - 2 * btnW - 6;
        label(x, y, labelW, name, tip);
        Button value = Button.builder(Component.literal(fmt.of(get.getAsDouble())).withStyle(ChatFormatting.YELLOW), b -> {})
                .bounds(x + labelW + btnW + 4, y, valW, 20).build();
        value.active = false;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            step.accept(-1);
            BazaarHelper.CONFIG.save();
            value.setMessage(Component.literal(fmt.of(get.getAsDouble())).withStyle(ChatFormatting.YELLOW));
        }).bounds(x + labelW + 2, y, btnW, 20).build());
        addRenderableWidget(value);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            step.accept(1);
            BazaarHelper.CONFIG.save();
            value.setMessage(Component.literal(fmt.of(get.getAsDouble())).withStyle(ChatFormatting.YELLOW));
        }).bounds(x + w - btnW, y, btnW, 20).build());
    }

    private void cycle(int x, int y, int w, String name, String tip, double[] values,
                       DoubleSupplier get, java.util.function.DoubleConsumer set, Fmt fmt) {
        int valW = 64 + 2 * 20 + 4;
        label(x, y, w - valW - 2, name, tip);
        addRenderableWidget(Button.builder(Component.literal(fmt.of(get.getAsDouble())).withStyle(ChatFormatting.YELLOW), b -> {
            double cur = get.getAsDouble();
            int i = 0;
            for (int k = 0; k < values.length; k++) if (Math.abs(values[k] - cur) < 1e-9) i = k;
            set.accept(values[(i + 1) % values.length]);
            BazaarHelper.CONFIG.save();
            b.setMessage(Component.literal(fmt.of(get.getAsDouble())).withStyle(ChatFormatting.YELLOW));
        }).bounds(x + w - valW, y, valW, 20).tooltip(Tooltip.create(Component.literal("Click to cycle"))).build());
    }

    private void toggle(int x, int y, int w, String name, String tip, BoolSupplier get, BoolConsumer set) {
        int valW = 64 + 2 * 20 + 4;
        label(x, y, w - valW - 2, name, tip);
        addRenderableWidget(Button.builder(onOff(get.get()), b -> {
            set.accept(!get.get());
            BazaarHelper.CONFIG.save();
            b.setMessage(onOff(get.get()));
        }).bounds(x + w - valW, y, valW, 20).build());
    }

    /** Coins: Auto (purse + bids + stock, read live) or Manual (the number below). */
    private void coinsMode(int x, int y, int w, Config c) {
        int valW = 64 + 2 * 20 + 4;
        label(x, y, w - valW - 2, "Coins", "Auto reads your Purse off the SkyBlock sidebar and adds coins in\n"
                + "your buy orders and the stock you hold. Follows you as you grow.");
        addRenderableWidget(Button.builder(coinsLabel(c), b -> {
                    c.autoCoins = !c.autoCoins;
                    c.save();
                    b.setMessage(coinsLabel(c));
                }).bounds(x + w - valW, y, valW, 20)
                .tooltip(Tooltip.create(Component.literal(BazaarHelper.capitalNote() + "\n\nClick to switch Auto / Manual")))
                .build());
    }

    private static Component coinsLabel(Config c) {
        if (!c.autoCoins) return Component.literal("Manual").withStyle(ChatFormatting.YELLOW);
        double p = BazaarHelper.detectedPurse();
        return Component.literal(p >= 0 ? "Auto: " + FlipMath.fmt(p) : "Auto").withStyle(ChatFormatting.GREEN);
    }

    private static Component onOff(boolean on) {
        return Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // ---------- helpers ----------

    static double coinStep(double total) {
        if (total < 100e6) return 10e6;
        if (total < 1e9) return 50e6;
        return 250e6;
    }

    static double unitStep(double units) {
        if (units < 10_000) return 1_000;
        if (units < 100_000) return 5_000;
        return 25_000;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.US, "%s", v);
    }

}
