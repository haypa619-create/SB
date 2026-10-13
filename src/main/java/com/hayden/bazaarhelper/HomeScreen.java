package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Home: the account at a glance, brokerage-app style. Big account value and today's change,
 * a 24h account-value chart, four plain cards, one Next button, and what needs you.
 */
public final class HomeScreen extends DeskScreen {
    private static final int HERO_H = 78;
    private int heroTop;

    public HomeScreen() {
        super(Tab.HOME, Component.literal("Home"));
    }

    @Override
    public DeskScreen refreshed() {
        return new HomeScreen();
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left();
        heroTop = top;
        int y = top + HERO_H + 6;

        // ---- four cards ----
        double purse = BazaarHelper.detectedPurse();
        double bids = BazaarHelper.lockedInBidsPublic();
        double stock = BazaarHelper.stockValuePublic();
        double trades = BazaarHelper.tradesValue();
        int cardW = (w - 3 * 4) / 4;
        card(left, y, cardW, "Cash", purse >= 0 ? FlipMath.fmt(purse) : "?", ChatFormatting.GOLD,
                "Your purse, from the SkyBlock sidebar. This is your buying power.");
        card(left + (cardW + 4), y, cardW, "In buy orders", FlipMath.fmt(bids), ChatFormatting.GREEN,
                "Coins sitting in your market-making buy orders");
        card(left + 2 * (cardW + 4), y, cardW, "Stock", FlipMath.fmt(stock), ChatFormatting.AQUA,
                "Market-making stock (inventory + listed) at the best bid");
        card(left + 3 * (cardW + 4), y, cardW, "Trades", FlipMath.fmt(trades), ChatFormatting.LIGHT_PURPLE,
                "Open trades at the best bid, plus their entry orders");
        y += ROW_H + 4;

        // ---- the one button ----
        String next = BazaarHelper.nextDescribe();
        if (next != null) {
            button(Component.literal("▶  Next: " + next + "   (N)").withStyle(ChatFormatting.GREEN), left, y, w,
                    "The most urgent thing across your desk. Same as pressing N.", () -> { this.onClose(); BazaarHelper.startNext(); })
                    .setHeight(24);
        } else {
            label(Component.literal("✔ Nothing to do right now. Quotes are up.").withStyle(ChatFormatting.DARK_GREEN), left, y, w,
                    "N checks again with fresh prices.");
        }
        y += 30;

        // ---- needs attention ----
        List<BazaarHelper.Attention> att = BazaarHelper.attention();
        label(Component.literal(att.isEmpty() ? "Nothing needs you" : "Needs you (" + att.size() + ")")
                .withStyle(att.isEmpty() ? ChatFormatting.DARK_GREEN : ChatFormatting.WHITE), left, y, w,
                "Click any line to fix it");
        y += ROW_H;
        int max = Math.max(1, Math.min(att.size(), rowsThatFit(y) - 1));
        for (int i = 0; i < Math.min(att.size(), max); i++) {
            BazaarHelper.Attention a = att.get(i);
            button(Component.literal("•  " + a.text()).withStyle(a.color()), left, y, w, a.tip(), () -> { this.onClose(); a.action().run(); });
            y += ROW_H;
        }
        if (att.size() > max) {
            label(Component.literal("+" + (att.size() - max) + " more on the Orders and Trading tabs").withStyle(ChatFormatting.GRAY),
                    left, y, w, "");
        }
    }

    private void card(int x, int y, int w, String name, String value, ChatFormatting color, String tip) {
        label(Component.literal(name + "  ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(color)),
                x, y, w, tip);
    }

    @Override
    protected void drawExtra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = contentWidth(), left = left(), y = heroTop;
        int leftW = (int) (w * 0.44);
        panel(g, left, y, w, HERO_H);

        // ---- account value, big ----
        double value = BazaarHelper.accountValue();
        double today = BazaarHelper.todayRealized();
        g.text(this.font, Component.literal("Account value"), left + 8, y + 6, 0xFF8A93A0, false);
        bigText(g, Component.literal(value > 0 ? FlipMath.fmt(value) : "waiting for purse"), left + 8, y + 18, value > 0 ? 3f : 1.5f, 0xFFFFFFFF);
        double base = value - today;
        String pct = base > 0 ? String.format(Locale.US, " (%+.2f%%)", today / base * 100) : "";
        bigText(g, Component.literal("Today " + signed(today) + pct), left + 8, y + 48, 1.25f,
                today >= 0 ? 0xFF55FF55 : 0xFFFF5555);
        String d = QuoteBook.day(System.currentTimeMillis());
        g.text(this.font, Component.literal("market making " + signed(BazaarHelper.DESK.realizedOn(d))
                + "   trading " + signed(BazaarHelper.TRADES.realizedOn(d))), left + 8, y + 64, 0xFF8A93A0, false);

        // ---- 24h account value chart ----
        int cx = left + leftW, cw = w - leftW - 8, cy = y + 14, ch = HERO_H - 24;
        long now = System.currentTimeMillis();
        List<PriceHistory.Sample> eq = BazaarHelper.history(PriceHistory.EQUITY);
        g.text(this.font, Component.literal("Account value, 24h"), cx, y + 4, 0xFF8A93A0, false);
        double[] series = Stats.resample(eq, now - 24 * 3_600_000L, now, Math.max(2, cw));
        double[] range = Stats.range(series);
        if (range == null || eq.size() < 2) {
            g.text(this.font, Component.literal("Builds up as you play (one point every 2 min)"), cx, cy + ch / 2 - 4, 0xFF5A626C, false);
            return;
        }
        double first = Double.NaN;
        for (double v : series) if (Double.isFinite(v)) { first = v; break; }
        double last = series[series.length - 1];
        int color = Double.isFinite(first) && last < first ? 0xFFFF5555 : 0xFF55FF55;
        lineChart(g, cx, cy, cw, ch, series, range, color, null);
        g.text(this.font, Component.literal(FlipMath.fmt(range[1])), cx + cw - textWidth(Component.literal(FlipMath.fmt(range[1]))), cy, 0xFF5A626C, false);
        g.text(this.font, Component.literal(FlipMath.fmt(range[0])), cx + cw - textWidth(Component.literal(FlipMath.fmt(range[0]))), cy + ch - 8, 0xFF5A626C, false);
    }
}
