# BA Gamble Loot

Records the rewards from Barbarian Assault low and medium gambles in the Loot
Tracker.

## Features

**Gamble Settings**

Block eats the click on that row and on Accept. Highlight only outlines it.
Each tier is `Off`, `Block` or `Highlight`.

- **Low gamble**: default `Off`.
- **Medium gamble**: default `Off`.
- **High gamble**: default `Off`.

**Diagnostics**

Chat output for an unrecorded gamble.

- **Only use the accept path**: selected row only, no honour point fallback.
  Default `On`.
- **Unexpected charge**: reports charges that differ from the listed price.
  Default `On`.
- **Blocked clicks**: reports clicks a guard ate. Default `On`.
- **Gamble detection**: reports accept clicks, the row read and points spent.
  Default `On`.
- **Rewards**: reports loot paid out, or none arriving. Default `On`.
