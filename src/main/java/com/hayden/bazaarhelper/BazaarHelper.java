package com.hayden.bazaarhelper;

import com.hayden.bazaarhelper.mixin.ContainerScreenAccessor;
import com.hayden.bazaarhelper.mixin.SignEditAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Market-making desk for the Hypixel SkyBlock Bazaar.
 *
 * Builds a book (which items to quote, target stock, slots), shows a two-sided quote board
 * with inventory skew, tracks your live quotes and P&L from Bazaar chat, and gets you to
 * the right order screen with the number already typed.
 *
 * Rule it follows: one click from you = at most one action. A board click sends one /bz
 * command. Signs get text typed in, but you press Done and confirm. It never clicks menu
 * slots, presses Done, or confirms orders. Relisting is done with Bazaar Utils.
 */
public final class BazaarHelper implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("bazaarhelper");
    public static Config CONFIG;
    public static QuoteBook DESK;
    public static final BazaarData DATA = new BazaarData();
    public static final QuickOrder QUICK = new QuickOrder();

    /** Reuse prices fetched within this window. */
    private static final long CACHE_MS = 30_000;
    /** Main inventory + hotbar. */
    private static final int MAIN_SLOTS = 36;

    private static Path configDir;
    /** Screens opened from a command or hotkey wait one tick, or closing chat overwrites them. */
    private static volatile Screen pendingScreen;
    /** The last book built, so board clicks use the same targets you saw. */
    private static volatile Planner.Book lastBook;
    /** Coin total the board was last opened with. */
    private static volatile double lastTotal = -1;

    @Override
    public void onInitializeClient() {
        configDir = FabricLoader.getInstance().getConfigDir();
        CONFIG = Config.load(configDir);
        DESK = Config.loadBook(configDir);
        CompletableFuture.runAsync(() -> {
            try { DATA.refreshNames(); } catch (Exception e) { LOG.warn("Item data load failed: {}", e.toString()); }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
        PanelKey.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen s = pendingScreen;
            if (s != null) {
                pendingScreen = null;
                setScreen(s);
            }
            if (PanelKey.pressed()) openBoard();
            if (PanelKey.nextPressed()) startNext();
            if (++tickCount % 20 == 0) readPurse(client);
            autoRefreshTick();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            onScreenOpened(screen);
            addBazaarMenuButtons(screen);
            if (screen instanceof AbstractContainerScreen<?> cs && QuickOrder.isBazaarTitle(screen.getTitle().getString())) {
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> highlightNextSlot(cs, g));
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String text = message.getString();
            if (text.startsWith("[BzH]")) return; // our own messages
            if (CONFIG.debug && text.contains("[Bazaar]")) {
                LOG.info("Bazaar chat: {}", text);
                say(Component.literal("raw: " + text.replace("[Bazaar]", "<Bazaar>")).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (text.startsWith("[Bazaar]") && text.contains(" Setup!")) QUICK.clear(); // order placed: stop highlighting
            String note = DESK.onChat(text, CONFIG.taxPercent);
            if (text.contains("[Bazaar]")) Config.saveBook(configDir, DESK);
            if (note != null && CONFIG.chatNotes) say(Component.literal(note).withStyle(ChatFormatting.GRAY));
        });
    }

    // ---------- screens ----------

    /** 26.2 moved this from Minecraft to Gui; if this line fails to compile, use Minecraft.getInstance().setScreen(s). */
    private static void setScreen(Screen s) {
        Minecraft.getInstance().gui.setScreen(s);
    }

    /** Fills Hypixel's Custom Amount / Custom Price sign for the order you started. Never presses Done. */
    private static void onScreenOpened(Screen screen) {
        if (CONFIG.debug) say(Component.literal("Screen: " + screen.getClass().getSimpleName()
                + " \"" + screen.getTitle().getString() + "\"").withStyle(ChatFormatting.DARK_GRAY));
        if (!(screen instanceof AbstractSignEditScreen)) return;

        String[] lines = ((SignEditAccessor) screen).bazaarhelper$getMessages();
        if (CONFIG.debug) say(Component.literal("Sign lines: " + String.join(" | ", lines)).withStyle(ChatFormatting.DARK_GRAY));

        String text = QUICK.prefillFor(lines, System.currentTimeMillis());
        if (text != null) {
            lines[0] = text;
            if (CONFIG.debug) say(Component.literal("Filled in " + text).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Lets other screens switch screens safely (next tick). */
    public static void show(Screen s) {
        pendingScreen = s;
    }

    /**
     * Two buttons in the top-left of Bazaar menus:
     *   "← Board"  close the menu, open the board
     *   "Next ▶"   close the menu, do the next most urgent thing (same as N)
     * Each click is one action.
     */
    private static void addBazaarMenuButtons(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        if (!QuickOrder.isBazaarTitle(screen.getTitle().getString())) return;
        Screens.getWidgets(screen).add(Button.builder(
                        Component.literal("← Board").withStyle(ChatFormatting.GOLD),
                        b -> { closeMenu(); openBoard(); })
                .bounds(4, 4, 62, 20)
                .tooltip(Tooltip.create(Component.literal("Back to your quote board (B)")))
                .build());
        Screens.getWidgets(screen).add(Button.builder(
                        Component.literal("Next ▶").withStyle(ChatFormatting.GREEN),
                        b -> { closeMenu(); startNext(); })
                .bounds(70, 4, 62, 20)
                .tooltip(Tooltip.create(Component.literal("Do the next most urgent thing on your board (N)")))
                .build());
    }

    /**
     * Outlines the menu slot to click next for the order you started from the board
     * (the item, then Create Buy Order / Create Sell Offer, Custom Amount, Custom Price,
     * confirm). Drawing only: you still do every click.
     */
    private static void highlightNextSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        QuickOrder.Active a = QUICK.current(System.currentTimeMillis());
        if (a == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Slot> slots = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue; // menu slots only
            slots.add(slot);
            ItemStack st = slot.getItem();
            names.add(st.isEmpty() ? null : st.getHoverName().getString());
        }
        int i = QuickOrder.pickSlot(screen.getTitle().getString(), names, a);
        if (i < 0) return;

        ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
        int x = acc.bazaarhelper$getLeftPos() + slots.get(i).x;
        int y = acc.bazaarhelper$getTopPos() + slots.get(i).y;
        // Pulse between bright and soft green so it catches the eye.
        double t = (System.currentTimeMillis() % 1000) / 1000.0;
        int alpha = 0x60 + (int) (0x50 * (0.5 + 0.5 * Math.sin(t * 2 * Math.PI)));
        g.fill(x, y, x + 16, y + 16, (alpha << 24) | 0x55FF55);
        g.outline(x - 2, y - 2, 20, 20, 0xFF55FF55);
        g.outline(x - 1, y - 1, 18, 18, 0xFF55FF55);

        String hint = (a.side() == QuickOrder.Side.BUY ? "Bid " : "Ask ") + a.itemName() + ": click the green slot";
        g.centeredText(mc.font, Component.literal(hint).withStyle(ChatFormatting.GREEN),
                screen.width / 2, 8, 0xFFFFFFFF);
    }

    private static void closeMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.closeContainer();
    }

    // ---------- QoL: excludes, tracking reset, auto-refresh ----------

    public static void excludeItem(String id) {
        if (!CONFIG.excluded.contains(id)) CONFIG.excluded.add(id);
        CONFIG.save();
        say(Component.literal("Removed " + DATA.nameFor(id) + " from the book. Settings > Clear excluded brings it back.")
                .withStyle(ChatFormatting.GRAY));
        show(buildBoard(boardTotal()));
    }

    public static void resetQuoteTracking() {
        for (QuoteBook.Item it : DESK.items.values()) { it.bidUnits = 0; it.askUnits = 0; }
        Config.saveBook(configDir, DESK);
    }

    private static int tickCount = 0;
    /** Last purse seen on the SkyBlock sidebar, or -1 before it shows up. */
    private static volatile double purse = -1;

    /** Reads "Purse: ..." off the sidebar once a second. Display-only. */
    private static void readPurse(Minecraft mc) {
        if (mc.level == null) return;
        try {
            Scoreboard sb = mc.level.getScoreboard();
            Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (obj == null) return;
            List<String> lines = new ArrayList<>();
            for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
                PlayerTeam team = sb.getPlayersTeam(e.owner());
                lines.add(team == null ? e.owner()
                        : team.getPlayerPrefix().getString() + e.owner() + team.getPlayerSuffix().getString());
            }
            double p = Wallet.parsePurse(lines);
            if (p >= 0) purse = p;
        } catch (Exception e) {
            if (CONFIG.debug) LOG.warn("Sidebar read failed: {}", e.toString());
        }
    }

    /** Coins locked in your live buy orders, from Bazaar chat. */
    private static double lockedInBids() {
        double sum = 0;
        for (QuoteBook.Item it : DESK.items.values()) sum += it.bidUnits * it.bidPrice;
        return sum;
    }

    /** Stock you hold (inventory + live sell offers), valued at the top bid. */
    private static double stockValue() {
        double sum = 0;
        for (Map.Entry<String, Long> e : scanInventory().counts().entrySet()) {
            BazaarData.Product p = DATA.get(e.getKey());
            if (p != null) sum += e.getValue() * p.topBid();
        }
        for (Map.Entry<String, QuoteBook.Item> e : DESK.items.entrySet()) {
            if (e.getValue().askUnits <= 0) continue;
            String id = DATA.idForName(e.getKey());
            BazaarData.Product p = id == null ? null : DATA.get(id);
            if (p != null) sum += e.getValue().askUnits * p.topBid();
        }
        return sum;
    }

    /** One line for tooltips: where the coin total came from. */
    public static String capitalNote() {
        if (lastTotal > 0) return "Coins: " + FlipMath.fmt(lastTotal) + " (set with /bzh board; /bzh board alone goes back to auto)";
        if (!CONFIG.autoCoins) return "Coins: " + FlipMath.fmt(CONFIG.defaultTotal) + " (manual, Settings > Coins)";
        if (purse < 0) return "Coins: waiting for Purse on the SkyBlock sidebar, using " + FlipMath.fmt(CONFIG.defaultTotal) + " for now";
        return "Coins (auto): purse " + FlipMath.fmt(purse) + " + in bids " + FlipMath.fmt(lockedInBids())
                + " + stock " + FlipMath.fmt(stockValue());
    }

    public static boolean coinsAreAuto() {
        return lastTotal <= 0 && CONFIG.autoCoins && purse >= 0;
    }

    public static double detectedPurse() {
        return purse;
    }

    private static volatile boolean refreshing = false;
    private static volatile long lastAutoAttempt = 0;

    /** Prices refresh on their own every 20s while you're in a world, and an open board redraws. */
    private static final long REFRESH_MS = 20_000;

    private static void autoRefreshTick() {
        if (refreshing || Minecraft.getInstance().level == null) return;
        long now = System.currentTimeMillis();
        long every = REFRESH_MS;
        if (now - DATA.lastUpdated() < every || now - lastAutoAttempt < every) return; // also backs off if the API is down
        lastAutoAttempt = now;
        refreshing = true;
        CompletableFuture.runAsync(() -> {
            try {
                DATA.refreshBazaar();
                Minecraft.getInstance().execute(() -> {
                    if (BoardScreen.isOpen()) show(buildBoard(boardTotal()));
                });
            } catch (Exception e) {
                LOG.warn("Auto-refresh failed: {}", e.toString());
            } finally {
                refreshing = false;
            }
        });
    }

    // ---------- inventory ----------

    record InventoryScan(Map<String, Long> counts, int freeSlots) {}

    /** Counts Bazaar items in your inventory by SkyBlock id, and how many slots are empty. Display-only. */
    static InventoryScan scanInventory() {
        Map<String, Long> counts = new HashMap<>();
        int free = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new InventoryScan(counts, 0);
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < Math.min(MAIN_SLOTS, inv.getContainerSize()); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) { free++; continue; }
            String id = skyblockId(stack);
            if (id == null) continue;
            counts.merge(id, (long) stack.getCount(), Long::sum);
            DATA.observeStack(id, stack.getMaxStackSize());
        }
        return new InventoryScan(counts, free);
    }

    /** SkyBlock item id from the item's custom data, falling back to its name. */
    static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            String id = tag.getStringOr("id", "");
            if (id.isEmpty()) id = tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
            if (!id.isEmpty() && DATA.get(id) != null) return id;
        }
        return DATA.idForName(stack.getHoverName().getString());
    }

    // ---------- board ----------

    /** Coins to plan with: auto from your purse + bids + stock, unless you set it by hand. */
    private static double boardTotal() {
        if (lastTotal > 0) return lastTotal;
        if (CONFIG.autoCoins && purse >= 0) return Wallet.capital(purse, lockedInBids(), stockValue());
        return CONFIG.defaultTotal;
    }

    /** Opens the board: instantly if prices are fresh, otherwise after a quick fetch. First run opens setup. */
    public static void openBoard() {
        if (!CONFIG.setupDone) {
            show(new SettingsScreen(true));
            return;
        }
        if (!DATA.products().isEmpty() && System.currentTimeMillis() - DATA.lastUpdated() < CACHE_MS) {
            pendingScreen = buildBoard(boardTotal());
        } else {
            openBoardFresh();
        }
    }

    public static void openBoardFresh() {
        say(Component.literal("Loading quote board...").withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            try {
                DATA.refreshBazaar();
                Minecraft.getInstance().execute(() -> pendingScreen = buildBoard(boardTotal()));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> say(Component.literal("Couldn't reach the Hypixel API: "
                        + e.getMessage()).withStyle(ChatFormatting.RED)));
            }
        });
    }

    private static Planner.Book buildBook(double total) {
        List<BazaarData.Product> usable = new ArrayList<>();
        for (BazaarData.Product p : DATA.products().values()) {
            if (!CONFIG.excluded.contains(p.id())) usable.add(p);
        }
        Planner.Book b = Planner.build(usable, DATA::stackSize, CONFIG.plannerSettings(total));
        lastBook = b;
        return b;
    }

    /** Quotes for every line in the book, for the board and for Next. */
    private static List<NextAction.Candidate> candidates(Planner.Book book, InventoryScan inv) {
        List<NextAction.Candidate> out = new ArrayList<>();
        for (Planner.Line l : book.lines()) {
            String id = l.flip().id();
            out.add(new NextAction.Candidate(id, DATA.nameFor(id), quoteFor(l, inv.counts().getOrDefault(id, 0L))));
        }
        return out;
    }

    /** Unrealized P&L across everything the desk holds, in the book or not. */
    private static double totalMtm() {
        double sum = 0;
        for (Map.Entry<String, QuoteBook.Item> e : DESK.items.entrySet()) {
            sum += QuoteBook.markToMarket(e.getValue(), DATA.get(DATA.idForName(e.getKey())));
        }
        return sum;
    }

    private static MarketMaker.Quote quoteFor(Planner.Line l, long inventory) {
        String name = DATA.nameFor(l.flip().id());
        QuoteBook.Item d = DESK.get(name);
        BazaarData.Product p = DATA.get(l.flip().id());
        return MarketMaker.quote(p, l.targetUnits(), inventory,
                d == null ? 0 : (long) d.askUnits, d == null ? 0 : d.askPrice,
                d == null ? 0 : (long) d.bidUnits, d == null ? 0 : d.bidPrice,
                CONFIG.taxPercent, CONFIG.skewShare);
    }

    private static BoardScreen buildBoard(double total) {
        Planner.Book book = buildBook(total);
        InventoryScan inv = scanInventory();
        List<NextAction.Candidate> cands = candidates(book, inv);
        List<BoardScreen.Row> rows = new ArrayList<>();
        for (int i = 0; i < book.lines().size(); i++) {
            Planner.Line l = book.lines().get(i);
            String id = l.flip().id();
            String name = DATA.nameFor(id);
            QuoteBook.Item d = DESK.get(name);
            rows.add(new BoardScreen.Row(l, name, inv.counts().getOrDefault(id, 0L), cands.get(i).quote(), d,
                    QuoteBook.markToMarket(d, DATA.get(id))));
        }
        return new BoardScreen(new BoardScreen.Header(book, inv.freeSlots(), DESK.realizedTotal, totalMtm(),
                NextAction.pick(cands), NextAction.count(cands), DATA.lastUpdated()), rows);
    }

    /**
     * Next (button, N key, or the Next button in Bazaar menus): refresh prices, then do the single
     * most urgent thing on the board. Sends at most one command.
     */
    public static void startNext() {
        if (!CONFIG.setupDone) { show(new SettingsScreen(true)); return; }
        CompletableFuture.runAsync(() -> {
            try {
                DATA.refreshBazaar();
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> say(Component.literal("Couldn't reach the Hypixel API: "
                        + e.getMessage()).withStyle(ChatFormatting.RED)));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                Planner.Book book = buildBook(boardTotal());
                NextAction.Pick p = NextAction.pick(candidates(book, scanInventory()));
                if (p == null) {
                    say(Component.literal("✔ Nothing to do. All quotes are live.").withStyle(ChatFormatting.GREEN));
                    return;
                }
                switch (p.kind()) {
                    case REPRICE_BID, REPRICE_ASK -> openBazaarFor(p.id());
                    case PLACE_BID -> sendQuote(p.id(), QuickOrder.Side.BUY);
                    case PLACE_ASK -> sendQuote(p.id(), QuickOrder.Side.SELL);
                }
            });
        });
    }

    // ---------- orders ----------

    /**
     * One board click: re-check live prices and your inventory, recompute the quote, then send
     * exactly one command, /bz <item>. The sign gets the amount and price typed in.
     * Requires a Booster Cookie on Hypixel.
     */
    public static void startQuote(String id, QuickOrder.Side side) {
        say(Component.literal("Checking live price...").withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            try {
                DATA.refreshBazaar();
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> say(Component.literal("Couldn't reach the Hypixel API: "
                        + e.getMessage()).withStyle(ChatFormatting.RED)));
                return;
            }
            Minecraft.getInstance().execute(() -> sendQuote(id, side));
        });
    }

    private static void sendQuote(String id, QuickOrder.Side side) {
        Planner.Book book = lastBook;
        Planner.Line line = book == null ? null : book.find(id);
        if (line == null || DATA.get(id) == null) {
            say(Component.literal("That item isn't in your book anymore. Reopen the board.").withStyle(ChatFormatting.RED));
            return;
        }
        String name = DATA.nameFor(id);
        long have = scanInventory().counts().getOrDefault(id, 0L);
        MarketMaker.Quote q = quoteFor(line, have);

        double price;
        long amount;
        if (side == QuickOrder.Side.BUY) {
            if (q.bidAction() != MarketMaker.Action.PLACE) {
                say(Component.literal(name + ": not bidding now (" + (q.note().isEmpty() ? q.bidAction().name().toLowerCase(Locale.US) : q.note()) + ").")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            price = q.bidPrice();
            amount = q.bidSize();
        } else {
            if (q.askAction() != MarketMaker.Action.PLACE) {
                say(Component.literal(name + ": no stock in your inventory to list.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            price = q.askPrice();
            amount = 0; // a sell offer lists all of that item you carry
        }

        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        QUICK.start(new QuickOrder.Active(name, side, price, amount, System.currentTimeMillis()));
        conn.sendCommand("bz " + name);

        if (side == QuickOrder.Side.BUY) {
            say(Component.literal("Bid " + name + " (" + q.state().name().toLowerCase(Locale.US) + "): ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.format(Locale.US,
                            "click the green slots. Amount %,d and price %s get typed in for you.",
                            amount, QuickOrder.formatPrice(price))).withStyle(ChatFormatting.GRAY)));
        } else {
            say(Component.literal("Ask " + name + " (" + q.state().name().toLowerCase(Locale.US) + "): ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(String.format(Locale.US,
                            "click the green slots. Price %s gets typed in. Lists your %,d.",
                            QuickOrder.formatPrice(price), have)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** For outbid quotes: open the item so you can re-price from Your Orders (Bazaar Utils). */
    public static void openBazaarFor(String id) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        conn.sendCommand("bz " + DATA.nameFor(id));
        say(Component.literal("Re-price from Your Orders with Bazaar Utils' sign button, then B to come back.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ---------- commands ----------

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("bzh")
                // Only runs when you trigger it, not when a server tries to.
                .requires(FabricClientCommandSource::attended)
                .executes(BazaarHelper::help)
                .then(literal("help").executes(BazaarHelper::help))
                .then(literal("board")
                        .executes(c -> { lastTotal = -1; openBoard(); return 1; })
                        .then(argument("coins", StringArgumentType.word()).executes(c -> {
                            Double total = parse(c, StringArgumentType.getString(c, "coins"));
                            if (total == null) return 0;
                            lastTotal = total;
                            openBoard();
                            return 1;
                        })))
                .then(literal("panel").executes(c -> { openBoard(); return 1; }))
                .then(literal("next").executes(c -> { startNext(); return 1; }))
                .then(literal("setup").executes(c -> { show(new SettingsScreen(true)); return 1; }))
                .then(literal("book").executes(c -> withData(c, () -> printBook(c, boardTotal()))))
                .then(literal("item")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(c -> withData(c, () -> item(c, StringArgumentType.getString(c, "name"))))))
                .then(literal("pnl")
                        .executes(BazaarHelper::pnl)
                        .then(literal("reset").executes(c -> {
                            DESK.resetPnl();
                            Config.saveBook(configDir, DESK);
                            reply(c, "P&L reset. Cost basis kept.", true);
                            return 1;
                        })))
                .then(literal("clear")
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            DESK.clearQuotes(StringArgumentType.getString(c, "name"));
                            Config.saveBook(configDir, DESK);
                            reply(c, "Cleared live quotes for " + StringArgumentType.getString(c, "name"), true);
                            return 1;
                        })))
                .then(literal("settings")
                        .executes(c -> { show(new SettingsScreen(false)); return 1; })
                        .then(literal("print").executes(BazaarHelper::settings)))
                .then(setter("tax", "percent", v -> CONFIG.taxPercent = v, 0, 5, "Tax"))
                .then(setter("total", "coins", v -> CONFIG.defaultTotal = v, 1_000_000, 1e12, "Total"))
                .then(setter("reserve", "percent", v -> CONFIG.reservePct = v, 0, 90, "Reserve %"))
                .then(setter("maxitem", "percent", v -> CONFIG.maxItemPct = v, 1, 100, "Max per item %"))
                .then(setter("stockhours", "hours", v -> CONFIG.stockHours = v, 0.5, 48, "Stock hours"))
                .then(setter("skew", "share", v -> CONFIG.skewShare = v, 0, 0.9, "Skew"))
                .then(setter("minflow", "coins", v -> CONFIG.minDailyCoinFlow = v, 0, 1e12, "Min daily coin flow"))
                .then(setter("minunits", "units", v -> CONFIG.minWeeklyUnits = v, 0, 1e10, "Min weekly units"))
                .then(setter("minmargin", "percent", v -> CONFIG.minMarginPct = v, 0, 100, "Min margin %"))
                .then(setter("crowdweight", "weight", v -> CONFIG.crowdWeight = v, 0, 5, "Crowd weight"))
                .then(intSetter("slots", 1, MAIN_SLOTS, v -> CONFIG.slotBudget = v, "Slot budget"))
                .then(intSetter("maxitems", 1, 30, v -> CONFIG.maxItems = v, "Max items"))
                .then(intSetter("maxcrowd", 1, 1000, v -> CONFIG.maxCrowd = v, "Max crowd"))
                .then(literal("debug").executes(c -> {
                    CONFIG.debug = !CONFIG.debug;
                    CONFIG.save();
                    reply(c, "Debug " + (CONFIG.debug ? "on: screens, signs and Bazaar chat lines will print" : "off"), true);
                    return 1;
                }))
        );
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> setter(
            String name, String argName, Consumer<Double> apply, double min, double max, String label) {
        return literal(name).then(argument(argName, StringArgumentType.word()).executes(c -> {
            Double v = parse(c, StringArgumentType.getString(c, argName));
            if (v == null) return 0;
            if (v < min || v > max) {
                reply(c, label + " must be between " + FlipMath.fmt(min) + " and " + FlipMath.fmt(max) + ".", false);
                return 0;
            }
            apply.accept(v);
            CONFIG.save();
            reply(c, label + " set to " + FlipMath.fmt(v), true);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> intSetter(
            String name, int min, int max, Consumer<Integer> apply, String label) {
        return literal(name).then(argument("count", IntegerArgumentType.integer(min, max)).executes(c -> {
            int v = IntegerArgumentType.getInteger(c, "count");
            apply.accept(v);
            CONFIG.save();
            reply(c, label + " set to " + v, true);
            return 1;
        }));
    }

    /** Fetches fresh prices off the main thread if needed, then runs the action on the game thread. */
    private static int withData(CommandContext<FabricClientCommandSource> c, Runnable action) {
        if (!DATA.products().isEmpty() && System.currentTimeMillis() - DATA.lastUpdated() < CACHE_MS) {
            action.run();
            return 1;
        }
        reply(c, "Fetching Bazaar prices...", true);
        CompletableFuture.runAsync(() -> {
            try {
                DATA.refreshBazaar();
                Minecraft.getInstance().execute(action);
            } catch (Exception e) {
                LOG.warn("Bazaar refresh failed", e);
                Minecraft.getInstance().execute(() -> reply(c, "Couldn't reach the Hypixel API: " + e.getMessage(), false));
            }
        });
        return 1;
    }

    private static int help(CommandContext<FabricClientCommandSource> c) {
        String[] rows = {
                "B  open the quote board  |  N  do the next most urgent thing",
                "Bazaar menus get ← Board and Next ▶ buttons in the top-left",
                "/bzh settings  settings screen  |  /bzh setup  first-run setup",
                "/bzh board [600m]  open the board for a coin total",
                "/bzh book  print the book in chat",
                "/bzh pnl  realized and mark-to-market P&L  |  /bzh pnl reset",
                "/bzh item <name>  quote math for one item",
                "/bzh clear <item name>  forget live quotes (after cancelling a bid)",
                "/bzh settings print  show settings in chat",
                "/bzh slots <27>  inventory slots for stock",
                "/bzh stockhours <6>  target stock = this many hours of turnover",
                "/bzh skew <0.25>  share of spread to lean when long/short",
                "/bzh minflow <25m>  min daily coin flow (thinner side)",
                "/bzh minunits <20k>  min units traded per WEEK (thinner side)",
                "/bzh tax, total, reserve, maxitem, maxitems, minmargin, maxcrowd, crowdweight",
                "/bzh debug  print screens, signs and Bazaar chat lines",
        };
        c.getSource().sendFeedback(Component.literal("Bazaar Helper").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String r : rows) c.getSource().sendFeedback(Component.literal(r).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static void printBook(CommandContext<FabricClientCommandSource> c, double total) {
        Planner.Book b = buildBook(total);
        if (b.lines().isEmpty()) {
            reply(c, "No items pass your filters. Try /bzh minflow, /bzh minunits or /bzh minmargin lower.", false);
            return;
        }
        c.getSource().sendFeedback(Component.literal(String.format(Locale.US, "Book for %s  |  slots %d/%d  |  est. %s/day",
                FlipMath.fmt(total), b.slotsUsed(), b.slotBudget(), FlipMath.fmt(b.estDailyProfit())))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int i = 1;
        for (Planner.Line l : b.lines()) {
            FlipMath.Flip f = l.flip();
            c.getSource().sendFeedback(Component.literal(i++ + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(DATA.nameFor(f.id())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(Locale.US, "  stock %,d (%d slot%s)", l.targetUnits(), l.slots(),
                            l.slots() == 1 ? "" : "s")).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  bid " + FlipMath.fmt(f.buyAt()) + " / ask " + FlipMath.fmt(f.sellAt()))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  ~" + FlipMath.fmt(l.estDailyProfit()) + "/day, "
                            + FlipMath.fmt(l.profitPerSlot()) + "/slot").withStyle(ChatFormatting.GREEN))
                    .append(crowdTag(f.id())));
        }
        c.getSource().sendFeedback(Component.literal("Committed " + FlipMath.fmt(b.committed())
                + " (stock + bids)  |  reserve " + FlipMath.fmt(b.reserve())
                + (b.idle() >= 1_000_000 ? "  |  idle " + FlipMath.fmt(b.idle()) : "")).withStyle(ChatFormatting.YELLOW));
        if (b.idle() >= 1_000_000) {
            c.getSource().sendFeedback(Component.literal("Idle coins: slots or liquidity ran out first. More expensive liquid items, or more slots, put them to work.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        c.getSource().sendFeedback(Component.literal("Est/day assumes you catch 10% of daily volume. Use it to rank, not as a promise.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static int pnl(CommandContext<FabricClientCommandSource> c) {
        c.getSource().sendFeedback(Component.literal("Desk P&L since reset").withStyle(ChatFormatting.GOLD));
        double mtmTotal = 0;
        for (Map.Entry<String, QuoteBook.Item> e : DESK.items.entrySet()) {
            QuoteBook.Item it = e.getValue();
            String id = DATA.idForName(e.getKey());
            double mtm = QuoteBook.markToMarket(it, DATA.get(id));
            mtmTotal += mtm;
            if (it.realized == 0 && it.costUnits == 0) continue;
            c.getSource().sendFeedback(Component.literal(String.format(Locale.US, "%s  realized %s (%d trips)  holding %,d @ %s  MTM %s",
                    DATA.nameFor(id != null ? id : e.getKey()), BoardScreen.signed(it.realized), it.roundTrips,
                    (long) it.costUnits, FlipMath.fmt(it.avgCost), BoardScreen.signed(mtm))).withStyle(ChatFormatting.GRAY));
        }
        c.getSource().sendFeedback(Component.literal("Realized " + BoardScreen.signed(DESK.realizedTotal)
                + "  |  MTM " + BoardScreen.signed(mtmTotal)
                + "  |  total " + BoardScreen.signed(DESK.realizedTotal + mtmTotal)).withStyle(ChatFormatting.YELLOW));
        if (DATA.products().isEmpty()) {
            c.getSource().sendFeedback(Component.literal("MTM needs prices: open the board once first.").withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static void item(CommandContext<FabricClientCommandSource> c, String name) {
        String id = DATA.idForName(name);
        if (id == null) id = name.trim().toUpperCase(Locale.US).replace(' ', '_');
        BazaarData.Product p = DATA.get(id);
        if (p == null) { reply(c, "Couldn't find \"" + name + "\" on the Bazaar.", false); return; }
        c.getSource().sendFeedback(Component.literal(DATA.nameFor(id)).withStyle(ChatFormatting.GOLD).append(crowdTag(id)));
        c.getSource().sendFeedback(Component.literal("Top bid " + FlipMath.fmt(p.topBid()) + "  |  top ask " + FlipMath.fmt(p.topAsk())
                + "  |  stacks of " + DATA.stackSize(id)).withStyle(ChatFormatting.WHITE));
        FlipMath.Flip f = FlipMath.evaluate(id, p.topBid(), p.topAsk(), p.instaBuyWeek(), p.instaSellWeek(),
                CONFIG.taxPercent, Double.MAX_VALUE);
        if (f == null) {
            c.getSource().sendFeedback(Component.literal("No profitable spread after tax right now.").withStyle(ChatFormatting.RED));
        } else {
            c.getSource().sendFeedback(Component.literal(String.format(Locale.US,
                    "Quote %s / %s: +%s/unit (%.2f%%)  |  weekly flow %s units, %s coins/day",
                    FlipMath.fmt(f.buyAt()), FlipMath.fmt(f.sellAt()), FlipMath.fmt(f.profitPerUnit()), f.marginPct(),
                    FlipMath.fmt(f.dailyUnits() * 7), FlipMath.fmt(f.dailyUnits() * f.buyAt()))).withStyle(ChatFormatting.YELLOW));
            if (f.dailyUnits() * 7 < CONFIG.minWeeklyUnits) {
                c.getSource().sendFeedback(Component.literal("Under your min weekly units (" + FlipMath.fmt(CONFIG.minWeeklyUnits)
                        + "), so it won't be in the book.").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static int settings(CommandContext<FabricClientCommandSource> c) {
        c.getSource().sendFeedback(Component.literal("Settings").withStyle(ChatFormatting.GOLD));
        String[] rows = {
                "Total " + FlipMath.fmt(CONFIG.defaultTotal) + "  |  reserve " + FlipMath.fmt(CONFIG.reservePct)
                        + "%  |  max per item " + FlipMath.fmt(CONFIG.maxItemPct) + "%  |  max items " + CONFIG.maxItems,
                "Slots " + CONFIG.slotBudget + "  |  stock hours " + FlipMath.fmt(CONFIG.stockHours)
                        + "  |  skew " + CONFIG.skewShare + "  |  tax " + CONFIG.taxPercent + "%",
                "Min daily flow " + FlipMath.fmt(CONFIG.minDailyCoinFlow) + "  |  min weekly units " + FlipMath.fmt(CONFIG.minWeeklyUnits)
                        + "  |  min margin " + FlipMath.fmt(CONFIG.minMarginPct) + "%",
                "Max crowd " + CONFIG.maxCrowd + "  |  crowd weight " + CONFIG.crowdWeight + "  |  debug " + (CONFIG.debug ? "on" : "off"),
        };
        for (String r : rows) c.getSource().sendFeedback(Component.literal(r).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    // ---------- helpers ----------

    /** Competing orders near the top for an item, colored green/yellow/red. */
    static MutableComponent crowdTag(String id) {
        BazaarData.Product p = DATA.get(id);
        int crowd = p == null ? 0 : p.crowd();
        ChatFormatting color = crowd <= 3 ? ChatFormatting.GREEN : crowd <= 7 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("  crowd " + crowd).withStyle(color);
    }

    private static Double parse(CommandContext<FabricClientCommandSource> c, String s) {
        try {
            return FlipMath.parseAmount(s);
        } catch (NumberFormatException e) {
            reply(c, "\"" + s + "\" isn't a number. Try 600m, 1.5b, 20k or 1.125.", false);
            return null;
        }
    }

    private static void reply(CommandContext<FabricClientCommandSource> c, String msg, boolean ok) {
        c.getSource().sendFeedback(Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** Chat line outside a command. */
    static void say(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("[BzH] ").withStyle(ChatFormatting.GOLD).append(msg));
    }
}
