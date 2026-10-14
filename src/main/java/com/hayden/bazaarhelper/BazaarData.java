package com.hayden.bazaarhelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Hypixel's public Bazaar API (no API key needed).
 *
 * Naming trap in the API: "sell_summary" is the list of BUY ORDERS (what insta-sellers hit),
 * and "buy_summary" is the list of SELL OFFERS (what insta-buyers hit).
 */
public final class BazaarData {
    public static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    public static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";

    /**
     * bidCrowd / askCrowd: how many separate orders sit within CROWD_BAND of the best price
     * on that side. A high count means other flippers are fighting there, so you'd be
     * undercut and have to relist constantly.
     */
    public record Product(String id, double topBid, double topAsk,
                          double instaBuyWeek, double instaSellWeek,
                          int bidCrowd, int askCrowd,
                          double topBidUnits, int topBidOrders, double topAskUnits, int topAskOrders,
                          double[] bidLevels, double[] askLevels) {
        /** Without order book depth (tests, simple callers). */
        public Product(String id, double topBid, double topAsk, double instaBuyWeek, double instaSellWeek,
                       int bidCrowd, int askCrowd, double topBidUnits, int topBidOrders, double topAskUnits, int topAskOrders) {
            this(id, topBid, topAsk, instaBuyWeek, instaSellWeek, bidCrowd, askCrowd,
                    topBidUnits, topBidOrders, topAskUnits, topAskOrders, null, null);
        }

        public Product(String id, double topBid, double topAsk, double instaBuyWeek, double instaSellWeek) {
            this(id, topBid, topAsk, instaBuyWeek, instaSellWeek, 1, 1);
        }

        public Product(String id, double topBid, double topAsk, double instaBuyWeek, double instaSellWeek,
                       int bidCrowd, int askCrowd) {
            this(id, topBid, topAsk, instaBuyWeek, instaSellWeek, bidCrowd, askCrowd, 0, 0, 0, 0, null, null);
        }

        /** Competing orders on the busier side; that's the side you'll relist most. */
        public int crowd() {
            return Math.max(bidCrowd, askCrowd);
        }
    }

    /** Orders within 0.5% of the best price count as competing with you. */
    public static final double CROWD_BAND = 0.005;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private volatile Map<String, Product> products = Map.of();
    private volatile Map<String, String> nameToId = Map.of();
    private volatile Map<String, String> idToName = Map.of();
    private volatile long lastUpdated = 0;

    public Map<String, Product> products() { return products; }
    public long lastUpdated() { return lastUpdated; }

    public Product get(String id) { return id == null ? null : products.get(id); }

    public String idForName(String name) {
        return name == null ? null : nameToId.get(normalize(name));
    }

    public String nameFor(String id) {
        String n = idToName.get(id);
        return n != null ? n : prettyId(id);
    }

    public void refreshBazaar() throws Exception {
        products = parseBazaar(fetch(BAZAAR_URL));
        lastUpdated = System.currentTimeMillis();
    }

    public void refreshNames() throws Exception {
        String json = fetch(ITEMS_URL);
        Map<String, String> ids = parseItemNames(json);
        Map<String, String> byName = new HashMap<>();
        ids.forEach((id, name) -> byName.put(normalize(name), id));
        idToName = ids;
        nameToId = byName;
        materialStack = parseStackSizes(json);
    }

    // ---------- stack sizes (inventory slots per unit) ----------

    private volatile Map<String, Integer> materialStack = Map.of();
    private final Map<String, Integer> observedStack = new java.util.concurrent.ConcurrentHashMap<>();

    /** Records the real max stack size seen on an item in your inventory. Beats any guess. */
    public void observeStack(String id, int max) {
        if (id != null && max > 0) observedStack.put(id, max);
    }

    /** Units per inventory slot: what you've seen in game, else from the item's material, else 64. */
    public int stackSize(String id) {
        Integer s = observedStack.get(id);
        if (s != null) return s;
        s = materialStack.get(id);
        return s != null ? s : 64;
    }

    /** Vanilla stack size for a SkyBlock item's base material. Only non-64 materials are listed. */
    static int stackForMaterial(String material) {
        if (material == null) return 64;
        return switch (material.toUpperCase(Locale.US)) {
            case "ENCHANTED_BOOK", "POTION", "SPLASH_POTION", "BOOK_AND_QUILL", "WRITABLE_BOOK",
                 "WATER_BUCKET", "LAVA_BUCKET", "MILK_BUCKET", "SADDLE", "CAKE" -> 1;
            case "ENDER_PEARL", "SNOW_BALL", "SNOWBALL", "EGG", "SIGN", "OAK_SIGN", "BUCKET", "HONEY_BOTTLE",
                 "ARMOR_STAND", "BANNER" -> 16;
            default -> 64;
        };
    }

    static Map<String, Integer> parseStackSizes(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Integer> out = new HashMap<>();
        JsonArray items = root.getAsJsonArray("items");
        if (items == null) return out;
        for (JsonElement el : items) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("id") && o.has("material")) {
                int s = stackForMaterial(o.get("material").getAsString());
                if (s != 64) out.put(o.get("id").getAsString(), s);
            }
        }
        return out;
    }

    private String fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "BazaarHelper/0.1")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " from " + url);
        return res.body();
    }

    static Map<String, Product> parseBazaar(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) {
            throw new IllegalStateException("Bazaar API returned success=false");
        }
        Map<String, Product> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("products").entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            double topBid = firstPrice(p.getAsJsonArray("sell_summary")); // highest buy order
            double topAsk = firstPrice(p.getAsJsonArray("buy_summary"));  // lowest sell offer
            JsonObject qs = p.getAsJsonObject("quick_status");
            double instaBuyWeek = qs != null && qs.has("buyMovingWeek") ? qs.get("buyMovingWeek").getAsDouble() : 0;
            double instaSellWeek = qs != null && qs.has("sellMovingWeek") ? qs.get("sellMovingWeek").getAsDouble() : 0;
            int bidCrowd = crowd(p.getAsJsonArray("sell_summary"), topBid, false);
            int askCrowd = crowd(p.getAsJsonArray("buy_summary"), topAsk, true);
            JsonObject bidTop = first(p.getAsJsonArray("sell_summary"));
            JsonObject askTop = first(p.getAsJsonArray("buy_summary"));
            out.put(e.getKey(), new Product(e.getKey(), topBid, topAsk, instaBuyWeek, instaSellWeek, bidCrowd, askCrowd,
                    num(bidTop, "amount"), (int) num(bidTop, "orders"), num(askTop, "amount"), (int) num(askTop, "orders"),
                    levels(p.getAsJsonArray("sell_summary")), levels(p.getAsJsonArray("buy_summary"))));
        }
        return out;
    }

    static Map<String, String> parseItemNames(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> out = new HashMap<>();
        JsonArray items = root.getAsJsonArray("items");
        if (items == null) return out;
        for (JsonElement el : items) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("id") && o.has("name")) {
                out.put(o.get("id").getAsString(), stripFormatting(o.get("name").getAsString()));
            }
        }
        return out;
    }

    /** Counts orders within CROWD_BAND of the best price. Asks count upward, bids downward. */
    static int crowd(JsonArray levels, double best, boolean asks) {
        if (levels == null || best <= 0) return 0;
        double limit = asks ? best * (1 + CROWD_BAND) : best * (1 - CROWD_BAND);
        int count = 0;
        for (JsonElement el : levels) {
            JsonObject lvl = el.getAsJsonObject();
            double price = lvl.get("pricePerUnit").getAsDouble();
            if (asks ? price > limit : price < limit) break;
            count += lvl.has("orders") ? lvl.get("orders").getAsInt() : 1;
        }
        return count;
    }

    /** Top DEPTH price levels as {price, amount, price, amount, ...}, best first. */
    public static final int DEPTH = 12;

    static double[] levels(JsonArray arr) {
        if (arr == null) return new double[0];
        int n = Math.min(DEPTH, arr.size());
        double[] out = new double[n * 2];
        for (int i = 0; i < n; i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            out[2 * i] = num(o, "pricePerUnit");
            out[2 * i + 1] = num(o, "amount");
        }
        return out;
    }

    private static JsonObject first(JsonArray arr) {
        return arr == null || arr.isEmpty() ? null : arr.get(0).getAsJsonObject();
    }

    private static double num(JsonObject o, String key) {
        return o != null && o.has(key) ? o.get(key).getAsDouble() : 0;
    }

    private static double firstPrice(JsonArray arr) {
        if (arr == null || arr.isEmpty()) return 0;
        return arr.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
    }

    public static String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    static String normalize(String name) {
        return stripFormatting(name).trim().toLowerCase(Locale.US);
    }

    /** ENCHANTED_COAL -> Enchanted Coal (fallback when the item list has no name). */
    static String prettyId(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(Locale.US).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
