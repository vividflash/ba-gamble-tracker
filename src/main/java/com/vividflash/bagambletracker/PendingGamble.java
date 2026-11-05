package com.vividflash.bagambletracker;

// A gamble whose points have been spent and whose reward has not arrived yet.
class PendingGamble
{
	private final GambleTier tier;
	private final int expiresAt;

	PendingGamble(GambleTier tier, int expiresAt)
	{
		this.tier = tier;
		this.expiresAt = expiresAt;
	}

	GambleTier getTier()
	{
		return tier;
	}

	boolean isExpired(int tick)
	{
		return tick >= expiresAt;
	}
}
