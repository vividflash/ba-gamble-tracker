package com.vividflash.bagambletracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BaGambleTrackerConfig.GROUP)
public interface BaGambleTrackerConfig extends Config
{
	String GROUP = "bagambletracker";

	@ConfigSection(
		name = "Gamble Settings",
		description = "Stops or flags a gamble row in the reward shop, per tier.",
		position = 0
	)
	String guardSection = "guardSection";

	@ConfigSection(
		name = "Diagnostics",
		description = "Readouts for working out why a gamble went unrecorded.",
		position = 1,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "lowGambleGuard",
		name = "Low gamble",
		description = "Block outlines the row in red and consumes the click on it, and on Accept while it is selected. Highlight outlines the row in green and lets the click through.",
		section = guardSection,
		position = 0
	)
	default GuardMode lowGambleGuard()
	{
		return GuardMode.OFF;
	}

	@ConfigItem(
		keyName = "mediumGambleGuard",
		name = "Medium gamble",
		description = "Block outlines the row in red and consumes the click on it, and on Accept while it is selected. Highlight outlines the row in green and lets the click through.",
		section = guardSection,
		position = 1
	)
	default GuardMode mediumGambleGuard()
	{
		return GuardMode.OFF;
	}

	@ConfigItem(
		keyName = "highGambleGuard",
		name = "High gamble",
		description = "Block outlines the row in red and consumes the click on it, and on Accept while it is selected. Highlight outlines the row in green and lets the click through.",
		section = guardSection,
		position = 2
	)
	default GuardMode highGambleGuard()
	{
		return GuardMode.OFF;
	}

	@ConfigItem(
		keyName = "acceptPathOnly",
		name = "Only use the accept path",
		description = "Drops the honour point fallback, so a gamble only records if the row you clicked and the Accept that followed were both seen. For telling the two detections apart.",
		section = debugSection,
		position = 0
	)
	default boolean acceptPathOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logCostMismatch",
		name = "Unexpected charge",
		description = "Prints when a gamble charges something other than the price the shop lists.",
		section = debugSection,
		position = 1
	)
	default boolean logCostMismatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logGambles",
		name = "Gamble detection",
		description = "Prints the Accept click, the row it read, and the honour points that moved.",
		section = debugSection,
		position = 3
	)
	default boolean logGambles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logRewards",
		name = "Rewards",
		description = "Prints what a gamble paid out, or that its reward never arrived.",
		section = debugSection,
		position = 4
	)
	default boolean logRewards()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logGuards",
		name = "Blocked clicks",
		description = "Prints each click a guard consumed.",
		section = debugSection,
		position = 2
	)
	default boolean logGuards()
	{
		return true;
	}
}
