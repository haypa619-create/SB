package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The item page, terminal style: everything about one item on one screen.
 *
 *   header: name, bid / ask, spread, 1h / 6h / 24h change
 *   left:   24h chart of best bid (green) and best ask (blue), with your bid, ask, cost,
 *           fair value and trade target / stop as dashed lines; under it, your position
 *   right:  dense stats: flow, depth, crowding, margin, est/day, fair value, swings, guard
 *   bottom: Bid, Ask, Trade, Exclude, Back
 */
public final class ItemScreen extends DeskScreen {
    private static final int LABEL = 0xFF8A93A0, WHITE = 0xFFFFFFFF, GREEN = 0xFF55FF55, RED = 0xFFFF5555,
            AQUA = 0xFF55FFFF, GOLD = 0xFFFFAA00, PURPLE = 0xFFFF55FF, DIM = 0xFF5A626C;
    private static final int CHART_H = 112;

    private final String id;
    private final Tab from;
    private int bodyTop, actionsY;

    public ItemScreen(String id, Tab from) {
        super(from, Component.literal("Item"));
        this.id = id;
        this.from = from;
    }

    @Override
    public DeskScreen refreshed() {
        return new ItemScreen(id, from);
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left();
        bodyTop = top;
        String name = BazaarHelper.DATA.nameFor(id);
        int backW = 60;
        button(Component.literal("◀ Back"), left + w - backW, top, backW, "Back to " + from.label, () -> BazaarHelper.openTab(from));

        // actions at the bottom of the content
        actionsY = top + 24 + CHART_H + 8 + 9 * 10 + 6;
        actionsY = Math.min(actionsY, this.height - 26);
        boolean inBook = BazaarHelper.inBook(id);
        long held = BazaarHelper.held(id);
        boolean traded = BazaarHelper.TRADES.activeForId(id) != null;
        int bw = (w - 4 * 4) / 5, x = left;
        var bid = button(Component.literal("Bid").withStyle(ChatFormatting.GREEN), x, actionsY, bw,
                inBook ? "Place your market-making bid (green slots, numbers typed in)" : "Not in your book (filters or rank). Use Trade instead.",
                () -> { this.onClose(); BazaarHelper.startQuote(id, QuickOrder.Side.BUY); });
        bid.active = inBook && !traded;
        x += bw + 4;
        var ask = button(Component.literal(inBook ? "Ask" : "List").withStyle(ChatFormatting.AQUA), x, actionsY, bw,
                "List the " + held + " you hold (green slots, price typed in)",
                () -> { this.onClose(); BazaarHelper.startListAny(id); });
        ask.active = held > 0 && !traded;
        x += bw + 4;
        button(Component.literal(traded ? "Trade open" : "Trade").withStyle(ChatFormatting.LIGHT_PURPLE), x, actionsY, bw,
                traded ? "See it on the Trading tab" : "Set up a directional trade with target and stop",
                () -> { if (traded) BazaarHelper.openTab(Tab.TRADING); else BazaarHelper.openTradeSetup(id); });
        x += bw + 4;
        boolean excl = BazaarHelper.CONFIG.excluded.contains(id);
        button(Component.literal(excl ? "Include" : "Exclude"), x, actionsY, bw,
                excl ? "Let the book use this item again" : "Keep this item out of your book",
                () -> { BazaarHelper.toggleExcluded(id); BazaarHelper.show(new ItemScreen(id, from)); });
        x += bw + 4;
        button(Component.literal("Market tab"), x, actionsY, bw, "Compare with every other item",
                () -> BazaarHelper.openTab(Tab.MARKET));
    }

    @Override
    protected void drawExtra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        BazaarData.Product p = BazaarHelper.DATA.get(id);
        if (p == null) return;
        int w = contentWidth(), left = left(), y = bodyTop;
        long now = System.currentTimeMillis();
        String name = BazaarHelper.DATA.nameFor(id);
        double tax = BazaarHelper.CONFIG.taxPercent;
        double mid = (p.topBid() + p.topAsk()) / 2;
        List<PriceHistory.Sample> h = BazaarHelper.history(id);

        // ---- header ----
        int hx = left;
        hx = txt(g, name, hx, y + 6, GOLD) + 10;
        hx = pair(g, "bid ", FlipMath.fmt(p.topBid()), hx, y + 6, GREEN);
        hx = pair(g, "ask ", FlipMath.fmt(p.topAsk()), hx, y + 6, AQUA);
        double spread = p.topAsk() - p.topBid();
        hx = pair(g, "spr ", FlipMath.fmt(spread) + String.format(Locale.US, " (%.1f%%)", mid > 0 ? spread / mid * 100 : 0), hx, y + 6, WHITE);
        for (long[] win : new long[][]{{3_600_000L, 1}, {6 * 3_600_000L, 6}, {24 * 3_600_000L, 24}}) {
            double c = Stats.changePct(h, mid, now, win[0]);
            hx = pair(g, win[1] + "h ", Stats.pct(c), hx, y + 6, !Double.isFinite(c) ? DIM : c >= 0 ? GREEN : RED);
        }

        // ---- chart ----
        int chartW = (int) (w * 0.60);
        int cy = y + 24;
        panel(g, left, cy, chartW, CHART_H);
        int px = left + 4, pw = chartW - 50, py = cy + 12, ph = CHART_H - 26;
        txt(g, "24h  best bid / best ask", left + 4, cy + 2, LABEL);
        long from = now - 24 * 3_600_000L;
        List<PriceHistory.Sample> bids = new ArrayList<>(), asks = new ArrayList<>();
        for (PriceHistory.Sample s : h) {
            bids.add(new PriceHistory.Sample(s.time(), s.bid(), s.bid()));
            asks.add(new PriceHistory.Sample(s.time(), s.ask(), s.ask()));
        }
        double[] bs = Stats.resample(bids, from, now, Math.max(2, pw));
        double[] as = Stats.resample(asks, from, now, Math.max(2, pw));
        bs[bs.length - 1] = p.topBid();
        as[as.length - 1] = p.topAsk();

        QuoteBook.Item d = BazaarHelper.DESK.get(name);
        TradeBook.Trade t = BazaarHelper.TRADES.activeForId(id);
        Guard.Assessment ga = BazaarHelper.assess(id, p, now);
        List<double[]> refs = new ArrayList<>();
        if (ga.hasFair()) refs.add(new double[]{ga.fair(), 0xFF9A9A9A});
        if (d != null && d.bidUnits > 0) refs.add(new double[]{d.bidPrice, 0xFF2E9E2E});
        if (d != null && d.askUnits > 0) refs.add(new double[]{d.askPrice, 0xFF2E8E9E});
        if (d != null && d.costUnits > 0) refs.add(new double[]{d.avgCost, 0xFFFFD24A});
        if (t != null) {
            refs.add(new double[]{t.target, 0xFF55FF55});
            refs.add(new double[]{t.stop, 0xFFFF5555});
            if (t.avgCost > 0) refs.add(new double[]{t.avgCost, 0xFFFF55FF});
        }
        double[] extra = new double[refs.size()];
        for (int i = 0; i < refs.size(); i++) extra[i] = refs.get(i)[0];
        double[] all = new double[bs.length + as.length];
        System.arraycopy(bs, 0, all, 0, bs.length);
        System.arraycopy(as, 0, all, bs.length, as.length);
        double[] range = Stats.range(all, extra);
        lineChart(g, px, py, pw, ph, as, range, 0xFF55AAFF, refs.toArray(new double[0][]));
        lineChart(g, px, py, pw, ph, bs, range, 0xFF55FF55, null);
        if (range != null) {
            txt(g, FlipMath.fmt(range[1]), px + pw + 3, py - 2, DIM);
            txt(g, FlipMath.fmt((range[0] + range[1]) / 2), px + pw + 3, py + ph / 2 - 4, DIM);
            txt(g, FlipMath.fmt(range[0]), px + pw + 3, py + ph - 8, DIM);
        }
        txt(g, "-24h", px, py + ph + 2, DIM);
        txt(g, "-12h", px + pw / 2 - 10, py + ph + 2, DIM);
        txt(g, "now", px + pw - 16, py + ph + 2, DIM);
        if (h.size() < 2) txt(g, "Price history builds up while you play", px + 6, py + ph / 2 - 4, DIM);
        // legend
        int lx = left + 4, ly = cy + CHART_H + 2;
        lx = legend(g, "bid", 0xFF55FF55, lx, ly);
        lx = legend(g, "ask", 0xFF55AAFF, lx, ly);
        if (ga.hasFair()) lx = legend(g, "fair", 0xFF9A9A9A, lx, ly);
        if (d != null && (d.bidUnits > 0 || d.askUnits > 0)) lx = legend(g, "your quotes", 0xFF2E9E2E, lx, ly);
        if (d != null && d.costUnits > 0) lx = legend(g, "cost", 0xFFFFD24A, lx, ly);
        if (t != null) { lx = legend(g, "target", 0xFF55FF55, lx, ly); legend(g, "stop", 0xFFFF5555, lx, ly); }

        // ---- your position (under the chart) ----
        int ypos = cy + CHART_H + 14;
        txt(g, "YOUR POSITION", left, ypos, GOLD);
        ypos += 11;
        long held = BazaarHelper.held(id);
        if (t != null) {
            row(g, left, ypos, chartW, "Trade", String.format(Locale.US, "%s %s @ %s, tgt %s, stop %s", t.status.name().toLowerCase(Locale.US),
                    FlipMath.fmt(t.units), FlipMath.fmt(t.avgCost), FlipMath.fmt(t.target), FlipMath.fmt(t.stop)), PURPLE);
            ypos += 10;
            double u = t.unrealized(p.topBid(), tax);
            row(g, left, ypos, chartW, "Trade P&L", "open " + signed(u) + "   realized " + signed(t.realized), u >= 0 ? GREEN : RED);
            ypos += 10;
        }
        row(g, left, ypos, chartW, "Holding", String.format(Locale.US, "%,d in inventory, %s listed", held,
                d == null ? "0" : FlipMath.fmt(d.askUnits)), WHITE);
        ypos += 10;
        row(g, left, ypos, chartW, "Live quotes", (d != null && d.bidUnits > 0 ? "bid " + FlipMath.fmt(d.bidUnits) + " @ " + FlipMath.fmt(d.bidPrice) : "no bid")
                + "   " + (d != null && d.askUnits > 0 ? "ask " + FlipMath.fmt(d.askUnits) + " @ " + FlipMath.fmt(d.askPrice) : "no ask"), WHITE);
        ypos += 10;
        double mtm = QuoteBook.markToMarket(d, p);
        row(g, left, ypos, chartW, "Cost / MTM", d != null && d.costUnits > 0
                ? FlipMath.fmt(d.avgCost) + " on " + FlipMath.fmt(d.costUnits) + "   MTM " + signed(mtm) : "no cost recorded", mtm >= 0 ? WHITE : RED);
        ypos += 10;
        row(g, left, ypos, chartW, "Realized", d == null ? "+0" : signed(d.realized) + " over " + d.roundTrips + " round trips"
                + (d.fills > 0 ? String.format(Locale.US, ", fills in %s", JournalScreen.minutes(d.fillSecs / d.fills / 60.0)) : ""),
                d != null && d.realized < 0 ? RED : GREEN);

        // ---- stats (right) ----
        int sx = left + chartW + 10, sw = w - chartW - 10, sy = cy;
        panel(g, sx - 4, sy, sw + 4, CHART_H + 14 + 7 * 10);
        sy += 4;
        txt(g, "MARKET", sx, sy, GOLD);
        sy += 11;
        Planner.Line line = BazaarHelper.lineFor(id);
        double ppu = line != null ? line.flip().profitPerUnit() : FlipMath.round1(p.topAsk() - 0.1) * (1 - tax / 100) - FlipMath.round1(p.topBid() + 0.1);
        sy = row(g, sx, sy, sw, "Margin after tax", line != null ? String.format(Locale.US, "%s/unit (%.2f%%)", FlipMath.fmt(ppu), line.flip().marginPct()) : FlipMath.fmt(ppu) + "/unit", ppu > 0 ? GREEN : RED);
        sy = row(g, sx, sy, sw, "Insta-buys / 7d", FlipMath.fmt(p.instaBuyWeek()), WHITE);
        sy = row(g, sx, sy, sw, "Insta-sells / 7d", FlipMath.fmt(p.instaSellWeek()), WHITE);
        sy = row(g, sx, sy, sw, "Coin flow / day", FlipMath.fmt(Math.min(p.instaBuyWeek(), p.instaSellWeek()) / 7 * mid), WHITE);
        sy = row(g, sx, sy, sw, "Top bid", FlipMath.fmt(p.topBidUnits()) + " in " + p.topBidOrders() + " order" + (p.topBidOrders() == 1 ? "" : "s"), WHITE);
        sy = row(g, sx, sy, sw, "Top ask", FlipMath.fmt(p.topAskUnits()) + " in " + p.topAskOrders() + " order" + (p.topAskOrders() == 1 ? "" : "s"), WHITE);
        sy = row(g, sx, sy, sw, "Crowd bid / ask", p.bidCrowd() + " / " + p.askCrowd(), p.crowd() <= 3 ? GREEN : p.crowd() <= 8 ? GOLD : RED);
        FillModel.Estimate fe = BazaarHelper.fillFor(p);
        double onTop = Math.min(fe.bidSecsOnTop(), fe.askSecsOnTop());
        sy = row(g, sx, sy, sw, "On top per quote", "~" + FillModel.secs(onTop) + (fe.measured() ? "" : " (guess)"),
                fe.war() ? RED : onTop >= 300 ? GREEN : onTop >= 60 ? GOLD : RED);
        sy = row(g, sx, sy, sw, "Fills per order", String.format(Locale.US, "~%.1f units%s", fe.bidFillPerOrder(), fe.war() ? "  BOT WAR" : ""), fe.war() ? RED : WHITE);
        sy = row(g, sx, sy, sw, "Stack size", Integer.toString(BazaarHelper.DATA.stackSize(id)), WHITE);
        if (line != null) {
            sy = row(g, sx, sy, sw, "Est. / day", FlipMath.fmt(line.estDailyProfit()), GREEN);
            sy = row(g, sx, sy, sw, "Per slot / day", FlipMath.fmt(line.profitPerSlot()), GREEN);
            sy = row(g, sx, sy, sw, "Target stock", String.format(Locale.US, "%s (%d slots)", FlipMath.fmt(line.targetUnits()), line.slots()), WHITE);
        }
        sy += 4;
        txt(g, "RISK", sx, sy, GOLD);
        sy += 11;
        sy = row(g, sx, sy, sw, "Fair value", ga.hasFair() ? FlipMath.fmt(ga.fair()) + String.format(Locale.US, " (%+.1f%%)", (mid / ga.fair() - 1) * 100) : "needs ~30 min", ga.hasFair() ? WHITE : DIM);
        sy = row(g, sx, sy, sw, "Edge needed", ga.hasFair() ? String.format(Locale.US, "%.2f%%", ga.edgePct()) : "-", WHITE);
        sy = row(g, sx, sy, sw, "In book", BazaarHelper.inBook(id) ? "yes" : BazaarHelper.CONFIG.excluded.contains(id) ? "excluded" : "no", WHITE);
        String guard = ga.reason().isEmpty() ? "clear" : ga.reason();
        txt(g, "Guard:", sx, sy, LABEL);
        int gy = sy;
        for (String part : wrap(guard, sw - 40)) {
            txt(g, part, sx + 36, gy, ga.reason().isEmpty() ? GREEN : RED);
            gy += 10;
        }
    }

    // ---------- drawing bits ----------

    private int txt(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, Component.literal(s), x, y, color, false);
        return x + this.font.width(s);
    }

    private int pair(GuiGraphicsExtractor g, String label, String value, int x, int y, int color) {
        x = txt(g, label, x, y, LABEL);
        return txt(g, value, x, y, color) + 10;
    }

    /** "Label ........ value", right-aligned value. Returns the next y. */
    private int row(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, int color) {
        txt(g, label, x, y, LABEL);
        int vw = this.font.width(value);
        txt(g, value, Math.max(x + this.font.width(label) + 6, x + w - vw - 4), y, color);
        return y + 10;
    }

    private int legend(GuiGraphicsExtractor g, String label, int color, int x, int y) {
        g.fill(x, y + 3, x + 8, y + 5, color);
        return txt(g, label, x + 11, y, LABEL) + 8;
    }

    private List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(next) > width && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(next);
        }
        if (line.length() > 0) out.add(line.toString());
        return out.size() > 3 ? out.subList(0, 3) : out;
    }
}
