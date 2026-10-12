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

## Controls

| Key or button | What it does |
|---|---|
| **B** | Open the quote board |
| **N** | Do the next most urgent thing |
| **← Board** / **Next ▶** | Top-left of every Bazaar menu: back to the board, or straight to the next thing |
| **Bid / Ask @ price** | Place that quote: runs `/bz <item>`, outlines each slot to click in green, fills in the signs |
| **⚠ outbid** | Open the item to re-price from Your Orders (Bazaar Utils' sign button) |
| **✕** | Remove an item from the book |
| **Settings** | Every setting as buttons |

Rebind keys in Options > Controls > Bazaar Helper.

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

**Next** works in this order: re-price outbid quotes, unload LONG items, rebuild SHORT items, list stock, then bid. Ties go to the better item.

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
| Lots of idle coins | Give it more slots, lower stock hours, or lower min weekly units. |

## Commands (optional)

`/bzh board [600m]`, `/bzh next`, `/bzh settings`, `/bzh setup`, `/bzh book`, `/bzh pnl [reset]`, `/bzh item <name>`, `/bzh clear <item>`, `/bzh debug`. Every setting also has a command: `tax`, `total`, `reserve`, `maxitem`, `maxitems`, `slots`, `stockhours`, `skew`, `minflow`, `minunits`, `minmargin`, `maxcrowd`, `crowdweight`. `/bzh help` lists them.

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
  QuickOrder.java       Sign prompts and Bazaar menu detection
  PanelKey.java         B and N hotkeys
  Config.java           Settings and desk book files
  mixin/SignEditAccessor.java
```
