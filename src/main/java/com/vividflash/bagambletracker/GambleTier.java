package com.vividflash.bagambletracker;

/**
 * The gambles sold in the Barbarian Assault reward shop, by the shop row that
 * buys them and the honour point cost that identifies them.
 */
enum GambleTier
{
	LOW(14, 200, "Barbarian Assault low gamble"),
	MEDIUM(15, 400, "Barbarian Assault medium gamble"),
	// The client's own Loot Tracker records the high gamble.
	HIGH(16, 500, null);

	private final int row;
	private final int cost;
	private final String lootName;

	GambleTier(int row, int cost, String lootName)
	{
		this.row = row;
		this.cost = cost;
		this.lootName = lootName;
	}

	int getRow()
	{
		return row;
	}

	int getCost()
	{
		return cost;
	}

	String getLootName()
	{
		return lootName;
	}

	boolean isRecorded()
	{
		return lootName != null;
	}

	/**
	 * The shop builds its rows in a fixed order, one per iteration with no gaps,
	 * and takes each row's eligibility from the matching bit of the shop's own
	 * eligibility mask. The three gambles are the last rows, so the row number
	 * names the tier whatever the shop charges for it.
	 */
	static GambleTier forRow(int row)
	{
		for (GambleTier tier : values())
		{
			if (tier.row == row)
			{
				return tier;
			}
		}

		return null;
	}

	static GambleTier forCost(int cost)
	{
		for (GambleTier tier : values())
		{
			if (tier.cost == cost)
			{
				return tier;
			}
		}

		return null;
	}
}
