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
		description = "Block or highlight a gamble row, per tier.",
		position = 0
	)
	String guardSection = "guardSection";

	@ConfigSection(
		name = "Diagnostics",
		description = "Chat output for an unrecorded gamble.",
		position = 1,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "lowGambleGuard",
		name = "Low gamble",
		description = "Block: red, click eaten. Highlight: green, click through.",
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
		description = "Block: red, click eaten. Highlight: green, click through.",
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
		description = "Block: red, click eaten. Highlight: green, click through.",
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
		description = "Selected row only, no honour point fallback.",
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
		description = "Charges that differ from the listed price.",
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
		description = "Accept clicks, the row read, points spent.",
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
		description = "Loot paid out, or none arriving.",
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
		description = "Clicks a guard ate.",
		section = debugSection,
		position = 2
	)
	default boolean logGuards()
	{
		return true;
	}
}
