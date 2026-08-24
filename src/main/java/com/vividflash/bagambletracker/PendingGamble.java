package com.vividflash.bagambletracker;

/**
 * A gamble whose points have been spent and whose reward has not arrived yet.
 */
class PendingGamble
{
	private final GambleTier tier;
	private int ticksLeft;

	PendingGamble(GambleTier tier, int ticksLeft)
	{
		this.tier = tier;
		this.ticksLeft = ticksLeft;
	}

	GambleTier getTier()
	{
		return tier;
	}

	boolean age()
	{
		return --ticksLeft > 0;
	}
}
