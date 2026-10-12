# Changelog

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
