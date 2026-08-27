package com.vividflash.bagambletracker;

/**
 * What a guard does to its gamble row in the reward shop.
 */
public enum GuardMode
{
	OFF,
	BLOCK,
	HIGHLIGHT;

	static GuardMode forTier(BaGambleTrackerConfig config, GambleTier tier)
	{
		switch (tier)
		{
			case LOW:
				return config.lowGambleGuard();
			case MEDIUM:
				return config.mediumGambleGuard();
			default:
				return config.highGambleGuard();
		}
	}
}
