# BA Gamble Loot

Records the rewards from Barbarian Assault low and medium gambles in the Loot
Tracker. RuneLite records high gambles already, so all three tiers appear in the
panel. A reward that drops to the floor on a full inventory is counted too.

Rewards are matched to gambles in the order they arrive, and a high gamble takes
its own reward rather than leaving it for the next one. Two rewards landing on
the same tick cannot be told apart: they go into one record, and the other gamble
records nothing.

## Gamble Settings

| Setting | Default | Effect |
| --- | --- | --- |
| Low gamble | Off | Off, Block or Highlight |
| Medium gamble | Off | Off, Block or Highlight |
| High gamble | Off | Off, Block or Highlight |

Block consumes the click on that row, and on Accept while the row is still
selected, and outlines the row in red. Highlight outlines it in green and lets
the click through.

## Diagnostics

| Setting | Default | Effect |
| --- | --- | --- |
| Only use the accept path | On | Records only what the selected shop row names |
| Unexpected charge | On | Prints a gamble that cost something other than its listed price |
| Blocked clicks | On | Prints each click a guard consumed |
| Gamble detection | On | Prints the Accept click, the row it read, the points spent |
| Rewards | On | Prints what a gamble paid out, or that nothing arrived |

## License

BSD 2-Clause.
