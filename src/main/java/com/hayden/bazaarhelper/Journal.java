package com.hayden.bazaarhelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trading journal: every Bazaar event appended to config/bazaarhelper-journal.csv (opens in
 * Excel or Google Sheets), plus per-item stats for the Journal screen. No Minecraft code.
 */
public final class Journal {
    public static final String FILE = "bazaarhelper-journal.csv";
    static final String HEADER = "time_utc,event,item,units,price,pnl,fill_minutes";

    private Journal() {}

    public static String row(long now, QuoteBook.Event e) {
        return String.join(",",
                java.time.Instant.ofEpochMilli(now).toString(),
                e.kind().name().toLowerCase(Locale.US),
                csv(e.item() == null ? "" : e.item()),
                num(e.units()), num(e.price()), num(e.pnl()),
                e.fillSecs() > 0 ? String.format(Locale.US, "%.1f", e.fillSecs() / 60.0) : "");
    }

    public static void append(Path configDir, long now, QuoteBook.Event e) {
        if (e == null) return;
        try {
            Path f = configDir.resolve(FILE);
            boolean fresh = !Files.exists(f);
            String text = (fresh ? HEADER + "\n" : "") + row(now, e) + "\n";
            Files.writeString(f, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            BazaarHelper.LOG.warn("Could not write journal", ex);
        }
    }

    public record ItemStats(String name, double realized, long roundTrips, double avgFillMinutes,
                            double bought, double sold, double realizedPerTrip) {}

    /** Per-item stats from the desk, best realized first. */
    public static List<ItemStats> stats(QuoteBook book) {
        List<ItemStats> out = new ArrayList<>();
        for (Map.Entry<String, QuoteBook.Item> e : book.items.entrySet()) {
            QuoteBook.Item it = e.getValue();
            if (it.roundTrips == 0 && it.fills == 0 && it.boughtUnits == 0) continue;
            out.add(new ItemStats(title(e.getKey()), it.realized, it.roundTrips,
                    it.fills > 0 ? it.fillSecs / it.fills / 60.0 : 0,
                    it.boughtUnits, it.soldUnits, it.roundTrips > 0 ? it.realized / it.roundTrips : 0));
        }
        out.sort(Comparator.comparingDouble(ItemStats::realized).reversed());
        return out;
    }

    /** "enchanted coal" -> "Enchanted Coal" (the desk keys names in lowercase). */
    static String title(String key) {
        StringBuilder sb = new StringBuilder();
        for (String w : key.split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String num(double v) {
        return v == 0 ? "0" : String.format(Locale.US, "%.1f", v);
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
