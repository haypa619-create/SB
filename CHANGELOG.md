# Changelog

## 0.13.0
- Home tab (key 1): account value in big numbers, today's change, 24h account-value chart, cash / in orders / stock / trades cards, one Next button, and a "Needs you" list where each line fixes the problem in one click
- Item page: click any item name (Quotes, Orders, Positions, Market) or /bzh item <name>. 24h bid/ask chart with your quotes, cost, fair value, target and stop; 1h/6h/24h change; flow, depth, crowding, margin, est/day, fair value, edge, guard; your position and history; Bid / Ask / Trade / Exclude
- Market tab: 24h change column and a Sort button (per slot, margin, est/day, 24h change, crowd)
- Tabs are 1-9 now; B opens Home the first time

## 0.12.0
- Trading tab: directional trades with size, entry (buy order or insta-buy), target and stop; reward:risk after tax before you commit
- Trades are kept apart from market making: one owner per item, traded items leave the book, their fills and P&L never touch market-making numbers, and you can't trade an item you're market making
- Stop-hit and target alerts; N sells a stopped-out trade first (green Sell Instantly slot)
- Insta-buy and insta-sell detection; green-slot guides for both
- Market tab: Trade button. Orders tab shows trade orders. Journal shows trading P&L separately; the journal file tags rows mm / trade
- /bzh trade [item]

## 0.11.1
- Fix: the green-slot guide ends when you close the Bazaar (it kept pointing at Manage Orders)
- Fix: a cancel finishes when the order is gone from Manage Orders, or on any "order cancelled" chat line, including SkyHanni's rewritten one
- ✕ Stop button next to ← Board / Next ▶ while a guide is running

## 0.11.0
- Tabbed desk: Quotes, Orders, Positions, Market, Journal, Alerts, Settings. Number keys 1-7 switch tabs; B reopens your last tab
- Orders tab: every open order with on top / shared / outbid / pull status, age, and Re-price / Cancel / Pull buttons; Sync / claim highlights Manage Orders
- Positions tab: holdings with cost, MTM, slots, and a List button for any item
- Market tab: everything passing your filters ranked by profit per slot, with Exclude / Include
- Alerts tab: log of the last 100 alerts
- Journal: last 7 days shown day by day
- Fix: SHORT skew is capped at 2% past the best price (it reached 16.9% on wide items, which other mods and Hypixel block). /bzh maxskew to change
- Fix: when Hypixel says a price "isn't competitive enough", the mod refetches prices and fills a safe price into the next Custom Price sign
- Fix: after cancelling to re-price, N puts the same item back up first

## 0.10.0
- N and B now work on the board and inside Bazaar menus (key presses go to the open screen). The mod warns if another control shares the key
- Manage Orders sync: opening /bz > Manage Orders reads your real orders, fills and claims, and the board matches them exactly
- Outbid / undercut alerts after every price refresh, with a sound. Fill pings, and a warning when a filled buy won't fit in your inventory
- Re-pricing and pulling: green slots lead through Manage Orders > your order > Cancel, then N re-places it
- Guard: the mod's own price history (saved across restarts) stops bids on fast drops, stretched prices, spread blowouts and one-order tops, and tells you to pull live bids when an item is falling
- Fair value quoting: bids must sit an edge below the 6h average, asks an edge above; the edge widens when prices swing
- Journal screen and config/bazaarhelper-journal.csv: P&L today / 7 days / all time, per item round trips, profit per trip, time to fill
- Max open orders (default 14) and daily limit (default 15B) with header readouts

## 0.9.0
- Coins are automatic: purse from the SkyBlock sidebar + coins in your buy orders + stock you hold. Settings > Coins switches Auto / Manual
- Prices refresh on their own every 20s whenever you're in a world, so the board is always current when you open it. Refresh button removed

## 0.8.0
- After you click Bid or Ask on the board, every Bazaar menu outlines the slot to click next in pulsing green: the item, Create Buy Order / Create Sell Offer, Custom Amount, Custom Price, then confirm
- The highlight stops once the order is set up

## 0.7.0
- First-run setup screen and a Settings screen: every setting is a button, no commands needed
- Next: one button, the N hotkey, or the Next button in Bazaar menus does the most urgent thing on the board (re-price outbid, unload long, rebuild short, then list and bid)
- Board header shows to-do count, data age, and warns when your inventory is nearly full
- Board auto-refreshes every 20s while open (toggle in Settings), with backoff if the API is down
- X on any row removes that item from the book; Settings > Clear excluded brings them back
- Paging when the board doesn't fit the window
- Reset quote tracking from Settings
- Chat notes can be turned off
- Mod icon

## 0.6.1
- Liquidity floor is weekly units (default 20,000 on the thinner side)

## 0.6.0
- Market-making desk: slot-aware book ranked by profit per inventory slot, two-sided quote board with SHORT / BALANCED / LONG skew, live quote tracking and P&L (realized + mark-to-market) from Bazaar chat

## 0.5.0
- B hotkey and a board button in Bazaar menus

## 0.4.0
- Order book crowding: items in 0.1 wars rank lower or are skipped

## 0.3.0
- One-click orders: opens /bz and fills in Custom Amount and Custom Price signs

## 0.2.0
- Retargeted to Minecraft 26.2 for SkyBlock Enhanced

## 0.1.0
- First version
