package com.hayden.bazaarhelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The last 100 alerts (outbid, undercut, pull, fills), newest first. */
public final class AlertsScreen extends DeskScreen {
    private static int page = 0;

    public AlertsScreen() {
        super(Tab.ALERTS, Component.literal("Alerts"));
    }

    @Override
    protected void initBody(int top) {
        int w = contentWidth(), left = left(), y = top;
        List<BazaarHelper.AlertEntry> log = BazaarHelper.alertLog();
        int clearW = 70;
        label(Component.literal("Alerts").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("   last " + log.size() + ", newest first").withStyle(ChatFormatting.GRAY)),
                left, y, w - clearW - 4, "Outbid and undercut alerts fire once until they clear. Press N to deal with the most urgent.");
        button(Component.literal("Clear"), left + w - clearW, y, clearW, "Empty the list",
                () -> { BazaarHelper.clearAlerts(); BazaarHelper.show(new AlertsScreen()); });
        y += ROW_H;
        if (log.isEmpty()) {
            label(Component.literal("No alerts yet.").withStyle(ChatFormatting.GRAY), left, y, w, "");
            return;
        }
        int perPage = rowsThatFit(y);
        int pages = (log.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * perPage; i < Math.min(log.size(), (page + 1) * perPage); i++) {
            BazaarHelper.AlertEntry a = log.get(i);
            label(Component.literal(ago(a.time()) + "  ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(a.text()).withColor(a.color())), left, y, w, a.text());
            y += ROW_H;
        }
        pager(y + 4, page, pages, p -> { page = p; BazaarHelper.show(new AlertsScreen()); });
    }
}
