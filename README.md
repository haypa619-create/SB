# Bazaar Helper

A market-making desk for the Hypixel SkyBlock Bazaar. Built for **Minecraft 26.2** to run inside the **SkyBlock Enhanced** modpack on Modrinth.

You quote both sides of a set of items all the time, earn the spread on whatever trades against you, and manage the inventory in between. Bazaar Helper:
- builds your **book**: which items to make markets in, how much stock to hold, how many inventory slots each takes
- shows a two-sided **quote board** with inventory skew
- tells you the **next** most urgent thing to do, one key press away
- tracks live quotes, cost basis and **P&L** from Bazaar chat
- opens the right order screen with the amount and price already typed in

Relisting is done with **Bazaar Utils** and price tooltips come from **Skyblocker**. Both are already in SkyBlock Enhanced.

## Quick start

1. Add the jar to your SkyBlock Enhanced instance (see Install below).
2. Join Hypixel and press **B**. The first time, a setup screen asks for your tax tier and slots (coins are read from your purse). Click **Done**.
3. On the board, press **N** (or click **Next**) and follow the chat line. Repeat.

No commands needed after that.

## Fast path: N then Enter, Enter, Enter

Press **N** (works in the world, on any tab, or inside a Bazaar menu). Then press **Enter** at each step: Enter clicks the green slot once, and on a sign the mod filled in, Enter presses Done. No aiming, no mouse. One key press is still exactly one click.

The chain is also shorter when it can be:
- **Price:** when your price is exactly the best + 0.1, the green slot is Hypixel's "Top Order +0.1" button (one click) instead of Custom Price + a sign.
- **Amount:** when a Hypixel preset ("Buy a stack!" etc.) is within 25% of the planned size, the green slot is that preset (one click) instead of Custom Amount + a sign. Settings > Preset amounts (0 = always exact).

A bid is usually: N, then Enter about 5 times (item, Create Buy Order, amount, price, Confirm). Turn it off in Settings > Enter clicks, or rebind it in Controls > Bazaar Helper > Click the Green Slot.

## Controls

| Key or button | What it does |
|---|---|
| **B** | Open the quote board (also works inside Bazaar menus; on the board it closes it) |
| **N** | Do the next most urgent thing (works in the world, on the board, and inside Bazaar menus) |
| **Enter** | In a Bazaar menu: click the green slot once. On a sign the mod filled in: Done |
| **← Board** / **Next ▶** | Top-left of every Bazaar menu: back to the board, or straight to the next thing |
| **Bid / Ask @ price** | Place that quote: runs `/bz <item>`, outlines each slot to click in green, fills in the signs |
| **⚠ outbid / ⚠ Pull bid** | Green slots lead you through Manage Orders > your order > Cancel. Then N re-places it at the new price |
| **Journal** | P&L today / 7 days / all time, and per item: round trips, profit per trip, time to fill |
| **✕** | Remove an item from the book |
| **Settings** | Every setting as buttons |

Rebind keys in Options > Controls > Bazaar Helper.

## Tabs

Press **B** to open the desk (it reopens the tab you were last on). Number keys 1-9 switch tabs:

| Key | Tab | What's on it |
|---|---|---|
| 1 | **Home** | Account value (big), today's change, a 24h account-value chart, cash / in orders / stock / trades, one **Next** button, and a "Needs you" list where every line is one click to fix |
| 2 | **Quotes** | Your two-sided quotes for every item in the book (the board) |
| 3 | **Orders** | Every open order: on top, shared, outbid or pull; how long it's been up; what's waiting to claim. Re-price / Cancel / Pull buttons, and **Sync / claim** to open Manage Orders |
| 4 | **Positions** | Stock you hold: units, avg cost, mid, MTM, slots, and a **List** button (works for items no longer in the book too) |
| 5 | **Trading** | Directional trades with a target and a stop, kept apart from market making (see below) |
| 6 | **Market** | Every item that passes your filters with margin, est/day, per slot, 24h change and crowd. Sort button. Trade / Exclude from here |
| 7 | **Journal** | P&L today / 7 days / all time, the last 7 days one by one, and per item stats |
| 8 | **Alerts** | The last 100 outbid, undercut, pull, fill and rejection alerts |
| 9 | **Settings** | Every setting |

**Click any item name** (on Quotes, Orders, Positions or Market, or `/bzh item <name>`) for its **item page**: a 24h chart of best bid and ask with your quotes, cost, fair value and trade target / stop drawn on it; 1h / 6h / 24h change; margin, weekly flow, coin flow, depth at the top, crowding, est/day, per slot, target stock; fair value, edge and the guard's view; your holding, live quotes, cost, MTM, realized and fill times; and Bid / Ask / Trade / Exclude buttons.

N works on every tab. Open tabs redraw with fresh prices every 20s (except Settings).

## Trading (kept apart from market making)

For directional bets: buy, hold, sell at a target or bail at a stop.

- **Start one:** Market tab > **Trade** on any item, or `/bzh trade <item>`. Pick the size, entry (buy order at best bid + 0.1, or insta-buy at the best ask), target % and stop %. It shows cost, reward, risk and reward:risk after tax.
- **Enter / exit:** green slots with the numbers typed in. **List** puts a sell offer at the target, **Sell** insta-sells now, **Close** stops tracking.
- **Stop hit:** alert + low note, and N goes straight to Sell Instantly. **Target reached:** alert.

How they stay apart:
- **One owner per item.** An item with an open trade leaves the market-making book until the trade closes. You can't open a trade on an item that has market-making orders or stock (a Bazaar sell offer lists every unit you carry, so they'd mix).
- The trade's fills, cost and P&L are tracked only on the Trading tab. Market-making quotes, cost basis, realized P&L and capital never include them.
- Both share Hypixel's open-order cap and daily limit. The journal file tags each row `mm` or `trade`.

## The board

**Header:** coins, slots used and free, realized P&L, mark-to-market, data age. Then **Next** with your to-do count, plus Settings / Close. Prices refresh on their own every 20s, so there's no Refresh button.

**Coins are automatic.** The mod reads **Purse** off the SkyBlock sidebar and adds the coins sitting in your buy orders and the stock you hold (at the top bid). As you grow from 600m to 1b+, the book grows with you. Hover the coin total to see the breakdown. Settings > Coins switches to Manual if you want to cap it.

**Rows:** `● item  position/target  crowd` | bid side | ask side. Hover the item for target, slots, spread, est/day, profit per slot, cost basis and P&L.

| State | When | Quotes |
|---|---|---|
| ▼ SHORT | position < 0.5 x target | Bid sharpened by skew x spread to rebuild stock |
| ● BALANCED | in between | Bid top + 0.1, ask top - 0.1 |
| ▲ LONG | position > 1.5 x target | Bid held, ask sharpened to unload |

**Position** = stock in your inventory + units in your live sell offer. Bid size tops you up toward 2 x target. Ask size is all the stock you carry, because a Bazaar sell offer lists all of that item in your inventory.

**Line 2:** open orders vs your cap, fills waiting to claim, coins traded today vs the daily limit, and when your orders were last synced.

**Next** works in this order: pull bids the guard flags and re-price outbid quotes, unload LONG items, rebuild SHORT items, list stock, then bid. Ties go to the better item. At your order cap it only fixes quotes.

## Live orders: synced from Manage Orders

Open **/bz > Manage Orders** now and then. The mod reads every order there (item, price, amount, how much filled, what's waiting to claim) and makes the board match exactly. Between syncs it tracks orders from Bazaar chat.

## Alerts

Prices refresh every 20s. After each refresh the mod checks your live quotes:
- **Outbid / undercut:** a low note and a chat line. Press N to fix it.
- **Pull your bid:** the guard says the price is falling fast. Press N.
- **Fill:** a ping. If a filled buy won't fit in your inventory, a red warning before you claim.

Each alert fires once until it clears. Sounds can be turned off in Settings.

## Guard: protection and fair value

The mod keeps its own price history (Hypixel's API has none; it's saved, so it survives restarts). After about 30 minutes of history per item it:
- stops bidding and tells you to **pull** live bids when the price falls more than 3% in 30 minutes
- stops bidding when the price is more than 8% above its 6-hour average (and holds stock when it's 8% below, unless you're LONG)
- stops bidding when the spread blows out to 3x its usual size (someone bought out the asks or dumped the bids)
- stops bidding when one order holds the top bid with more than a day of flow (if they pull it, your bid is left far above the market)
- prices around **fair value** (6-hour average mid): a bid must sit at least the edge below fair, an ask at least the edge above. Edge = max(0.1%, 0.25 x how much the price swings), so jumpy items need more room

Hover an item on the board to see what the guard thinks. Turn it off in Settings, or tune it with `/bzh drop`, `/bzh deviation`, `/bzh fairhours`, `/bzh volk`.

## Limits

- **Max open orders** (default 14): Next won't suggest new orders past it, and the book plans at most half that many items (each can need a bid and an ask). Set it to your real cap in Settings.
- **Daily limit** (default 15B): the board shows coins traded today, and the mod warns before an order would go past it. `/bzh dailylimit` to change.

## Journal

Every setup, fill, claim and cancel is appended to `config/bazaarhelper-journal.csv` (opens in Excel or Google Sheets): time, event, item, units, price, P&L, minutes to fill. The **Journal** screen sorts items by realized P&L so you can drop the ones that don't pay.

## Realistic fills (why orders weren't filling)

Your bid only fills while it's the top bid; insta-sells hit the top first. On contested items the top gets jumped by 0.1 every few seconds, so a quote barely sits there. The mod now measures that:

- **Time on top:** every 20s refresh it records when each item's top bid goes up and top ask goes down (someone jumped). From the last hour it estimates how long a fresh +0.1 quote stays on top. For the first 5 minutes of watching an item it guesses from crowding (shown with "?").
- **Fills per order** = flow per second x time on top. **Share of time on top** = time on top / (time on top + your relist time).
- **Est/day** = profit per unit x flow x share on top. The book ranks on that, not on a flat 10% of flow.
- **Bot wars** (an order can't expect even 1 unit before it's jumped) are left out (Settings > Skip bot wars).
- **Order size** = 1.5x what's expected to fill, capped by Settings > Max order (default 50M). Big orders that get jumped don't fill; they just burn your daily limit on every re-place.
- **Queued, not outbid:** when you're jumped by orders the flow will clear within 90s (Settings > Wait if queue), the quote shows **⏳ queued** and no alert fires. You're back on top once they fill. Re-placing would cost limit and land you behind them anyway.

The Market tab's "top" column and the item page show time on top and fills per order for each item.

## The book: profit per inventory slot

Slots are the binding constraint, so items are ranked by **estimated profit per slot**. That favors expensive items, but only ones with deep two-way flow. Each item must clear:
- **Min weekly units** (default 20,000 on the thinner side of insta-buys vs insta-sells)
- **Min daily coin flow** (default 25m)
- **Max crowd** (default 12 orders stacked near the top), which skips 0.1 wars
- **Min margin** after tax (default 1%)

Per item: **target stock** = turnover over `stock hours` (default 6), capped at `max per item`% of coins. **Capital** = stock + a bid of the same size. **Slots** = stock / stack size. Items are added best-per-slot first until slots, coins or max items run out. Whatever's left shows as **idle**.

## P&L

From Bazaar chat:
- Claiming a filled buy order sets your average cost.
- Claiming a filled sell offer realizes `units x (price x (1 - tax) - avg cost)`.
- **MTM** = stock held x (mid price - avg cost).

Stock bought before you installed the mod has no recorded cost, so selling it isn't counted as profit. `/bzh pnl` prints the breakdown and `/bzh pnl reset` zeroes P&L while keeping your cost basis.

## Rule boundary

One click or key press = at most one action. A board click, N, or Next sends one `/bz` command. Signs get a number typed in, which sends nothing until you press Done. The mod never clicks menu slots, presses Done, or confirms orders. Keep it that way if you change it: no auto-clicking, auto-relisting or macros.

Use at your own risk. Check Hypixel's current rules on allowed modifications.

## Install (Modrinth App)

1. Open the SkyBlock Enhanced instance and go to **Content**.
2. Use the dropdown next to **Add content**, pick **Add from file**, and choose the jar.
3. Launch and press **B**.

If a modpack update removes it, add the jar again.

## Build

Locally on Windows: install **Java 25** (Temurin), then run `gradlew.bat build` in this folder. The jar ends up in `build/libs/`.

On GitHub: push this folder to a repo. The included workflow (`.github/workflows/build.yml`) builds on every push and attaches the jar as a download under the run's **Artifacts**.

To move to a newer Minecraft version, update `gradle.properties` from https://fabricmc.net/develop and the `"minecraft"` line in `fabric.mod.json`.

## Troubleshooting

| Problem | Fix |
|---|---|
| Signs don't fill in | `/bzh debug`, open a Custom Amount and a Custom Price sign, and send the `Sign lines:` output. Patterns are in `QuickOrder.classify()`. |
| Quotes never show live, or P&L doesn't move | `/bzh debug`, place and claim one order, and send the `raw:` lines. Patterns are at the top of `QuoteBook.java`. |
| Board shows quotes you cancelled | Settings > Reset quote tracking. Cancelled buy orders aren't named in chat. |
| No green slot in a Bazaar menu | `/bzh debug` prints menu titles. Slot names are matched in `QuickOrder.pickSlot()`. |
| No ← Board button in a Bazaar menu | `/bzh debug` prints menu titles. Add the missing one in `QuickOrder.isBazaarTitle()`. |
| N does nothing outside menus | Another control uses N. The mod warns in chat when you join; rebind in Options > Controls > Bazaar Helper. |
| Manage Orders doesn't sync | `/bzh debug` prints how many orders it read. Order patterns are in `OrderSync.java`. |
| Lots of idle coins | Give it more slots, lower stock hours, or lower min weekly units. |

## Commands (optional)

`/bzh board [600m]`, `/bzh next`, `/bzh journal`, `/bzh settings`, `/bzh setup`, `/bzh book`, `/bzh pnl [reset]`, `/bzh item <name>`, `/bzh clear <item>`, `/bzh debug`. Every setting also has a command: `tax`, `total`, `reserve`, `maxitem`, `maxitems`, `slots`, `stockhours`, `skew`, `minflow`, `minunits`, `minmargin`, `maxcrowd`, `crowdweight`, `maxorders`, `dailylimit`, `drop`, `deviation`, `fairhours`, `volk`. `/bzh help` lists them.

## Project layout

```
src/main/java/com/hayden/bazaarhelper/
  BazaarHelper.java     Wiring: hotkeys, commands, board building, inventory scan, orders, sign filling, chat hook
  BoardScreen.java      The quote board
  SettingsScreen.java   Setup and settings
  Planner.java          Slot-aware book
  MarketMaker.java      Two-sided quote with inventory skew
  NextAction.java       Picks the most urgent thing to do
  QuoteBook.java        Live quotes, cost basis, P&L from chat
  BazaarData.java       Hypixel API: prices, crowding, names, stack sizes
  FlipMath.java         Spread, tax and volume math
  QuickOrder.java       Sign prompts, Bazaar menu detection, which slot to highlight
  OrderSync.java        Reads your orders off the Manage Orders menu
  Guard.java            Bad-fill protection and fair value
  PriceHistory.java     The mod's own rolling price history
  Journal.java          CSV journal and per-item stats
  JournalScreen.java    The Journal screen
  Wallet.java           Coins from your purse
  PanelKey.java         B and N hotkeys
  Config.java           Settings and desk book files
  mixin/SignEditAccessor.java
```
