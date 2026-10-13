package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Base for every desk tab: draws the tab bar, handles keys, and has small widget helpers.
 *
 *   [ 1 Quotes ][ 2 Orders ][ 3 Positions ][ 4 Trading ][ 5 Market ][ 6 Journal ][ 7 Alerts ][ 8 Settings ]
 *
 * Keys on any tab: 1-8 switch tabs, N does the next thing, B closes.
 * Built only from vanilla Buttons so it survives Minecraft rendering changes.
 */
public abstract class DeskScreen extends Screen {
    public enum Tab {
        QUOTES("Quotes", "Your two-sided quotes for every item in the book"),
        ORDERS("Orders", "Every open order: on top or outbid, filled, waiting to claim"),
        POSITIONS("Positions", "Stock you hold: cost, mark-to-market, slots"),
        TRADING("Trading", "Directional trades with target and stop, kept apart from market making"),
        MARKET("Market", "Best items on the Bazaar right now, in your book or not"),
        JOURNAL("Journal", "P&L by day and by item, fill times"),
        ALERTS("Alerts", "Outbid, undercut, pull and fill alerts"),
        SETTINGS("Settings", "Every desk setting");

        final String label, tip;

        Tab(String label, String tip) {
            this.label = label;
            this.tip = tip;
        }
    }

    public static final int ROW_H = 22;

    /** The tab on screen right now (null when none), so refreshes redraw the right one. */
    private static volatile Tab openTab;
    /** B reopens the tab you were last on. */
    private static volatile Tab lastTab = Tab.QUOTES;

    private final Tab tab;

    protected DeskScreen(Tab tab, Component title) {
        super(title);
        this.tab = tab;
    }

    public static Tab openTab() {
        return openTab;
    }

    public static Tab lastTab() {
        return lastTab;
    }

    /** Whether to show the tab bar (the first-run setup hides it). */
    protected boolean showTabs() {
        return true;
    }

    @Override
    protected final void init() {
        openTab = tab;
        lastTab = tab;
        int y = 4;
        if (showTabs()) {
            addTabBar(y);
            y += ROW_H + 2;
        }
        initBody(y);
    }

    /** Lay out this tab's widgets, starting at y. */
    protected abstract void initBody(int top);

    @Override
    public void removed() {
        if (openTab == tab) openTab = null;
        super.removed();
    }

    private void addTabBar(int y) {
        Tab[] tabs = Tab.values();
        int w = contentWidth();
        int left = left();
        int each = (w - (tabs.length - 1) * 2) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            String badge = BazaarHelper.tabBadge(t);
            Component label = Component.literal((i + 1) + " " + t.label + (badge.isEmpty() ? "" : " " + badge))
                    .withStyle(t == tab ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            Button b = Button.builder(label, x -> BazaarHelper.openTab(t))
                    .bounds(left + i * (each + 2), y, each, 20)
                    .tooltip(Tooltip.create(Component.literal(t.tip + "  (key " + (i + 1) + ")")))
                    .build();
            b.active = t != tab;
            addRenderableWidget(b);
        }
    }

    // ---------- layout helpers ----------

    protected int contentWidth() {
        return Math.min(560, this.width - 16);
    }

    protected int left() {
        return (this.width - contentWidth()) / 2;
    }

    /** Rows that fit between top and the bottom (leaving room for paging). */
    protected int rowsThatFit(int top) {
        return Math.max(1, (this.height - top - 28) / ROW_H);
    }

    protected Button label(Component text, int x, int y, int w, String tip) {
        Button b = Button.builder(text, x2 -> {}).bounds(x, y, w, 20)
                .tooltip(tip == null || tip.isEmpty() ? null : Tooltip.create(Component.literal(tip))).build();
        b.active = false;
        addRenderableWidget(b);
        return b;
    }

    protected Button button(Component text, int x, int y, int w, String tip, Runnable action) {
        Button b = Button.builder(text, x2 -> action.run()).bounds(x, y, w, 20)
                .tooltip(tip == null || tip.isEmpty() ? null : Tooltip.create(Component.literal(tip))).build();
        addRenderableWidget(b);
        return b;
    }

    /** ◀ Page n of m ▶ under a list. onPage gets the new page. */
    protected void pager(int y, int page, int pages, java.util.function.IntConsumer onPage) {
        if (pages <= 1) return;
        int left = left();
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> onPage.accept(page - 1))
                .bounds(left, y, 20, 20).build()).active = page > 0;
        label(Component.literal("Page " + (page + 1) + " of " + pages).withStyle(ChatFormatting.GRAY), left + 24, y, 90, "");
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> onPage.accept(page + 1))
                .bounds(left + 118, y, 20, 20).build()).active = page < pages - 1;
    }

    // ---------- keys ----------

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (showTabs()) {
            int k = e.key();
            if (k >= 49 && k <= 48 + Tab.values().length) { // number keys 1-8
                BazaarHelper.openTab(Tab.values()[k - 49]);
                return true;
            }
            if (PanelKey.isNext(e)) { this.onClose(); BazaarHelper.startNext(); return true; }
            if (PanelKey.isBoard(e)) { this.onClose(); return true; }
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- formatting ----------

    static String signed(double v) {
        return (v >= 0 ? "+" : "") + FlipMath.fmt(v);
    }

    static ChatFormatting pnlColor(double v) {
        return v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    static String ago(long time) {
        if (time <= 0) return "n/a";
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        if (s < 86400) return (s / 3600) + "h " + (s % 3600) / 60 + "m";
        return (s / 86400) + "d";
    }
}
