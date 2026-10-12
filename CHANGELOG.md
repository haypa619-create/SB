# Changelog

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
