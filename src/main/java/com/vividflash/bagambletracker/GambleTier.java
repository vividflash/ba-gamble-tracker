package com.vividflash.bagambletracker;

/**
 * The gambles sold in the Barbarian Assault reward shop, by the honour point
 * cost that identifies them.
 */
enum GambleTier
{
	LOW(200, "Barbarian Assault low gamble"),
	MEDIUM(400, "Barbarian Assault medium gamble"),
	// The client's own Loot Tracker records the high gamble.
	HIGH(500, null);

	private final int cost;
	private final String lootName;

	GambleTier(int cost, String lootName)
	{
		this.cost = cost;
		this.lootName = lootName;
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
