package com.vividflash.bagambletracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.PluginLootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
@PluginDescriptor(
	name = "BA Gamble Tracker",
	description = "Record Low/Medium BA gamble rewards to Loot Tracker.",
	tags = {"barbarian", "assault", "ba", "gamble", "minigame", "loot", "tracker"}
)
public class BaGambleTrackerPlugin extends Plugin
{
	private static final int BA_LOBBY_REGION = 10039;

	/**
	 * Ticks to wait for a reward before the gamble is dropped.
	 */
	private static final int REWARD_WINDOW_TICKS = 10;

	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> tickGains = new HashMap<>();
	private final Map<Integer, Integer> tickLosses = new HashMap<>();
	private final Map<Integer, Integer> previousGains = new HashMap<>();
	private final Deque<PendingGamble> pending = new ArrayDeque<>();
	private final int[] points = new int[BaRole.values().length];
	private final int[] levels = new int[BaRole.values().length];

	private boolean inventoryKnown;
	private boolean pointsKnown;
	private boolean reloaded;

	@Override
	protected void startUp()
	{
		reset();
	}

	@Override
	protected void shutDown()
	{
		reset();
	}

	private void reset()
	{
		inventory.clear();
		tickGains.clear();
		tickLosses.clear();
		previousGains.clear();
		pending.clear();
		inventoryKnown = false;
		pointsKnown = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			// A loading screen leaves the inventory and the points alone, and a
			// gamble taken just before one still pays out, so only a login or a
			// hop is worth starting over for.
			if (reloaded)
			{
				reloaded = false;
				reset();
			}

			return;
		}

		if (state != GameState.LOADING)
		{
			reloaded = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}

		Map<Integer, Integer> current = countItems(container);

		if (inventoryKnown)
		{
			for (Map.Entry<Integer, Integer> entry : current.entrySet())
			{
				int change = entry.getValue() - inventory.getOrDefault(entry.getKey(), 0);
				if (change > 0)
				{
					tickGains.merge(entry.getKey(), change, Integer::sum);
				}
			}

			for (Map.Entry<Integer, Integer> entry : inventory.entrySet())
			{
				int change = entry.getValue() - current.getOrDefault(entry.getKey(), 0);
				if (change > 0)
				{
					tickLosses.merge(entry.getKey(), change, Integer::sum);
				}
			}
		}

		inventory.clear();
		inventory.putAll(current);
		inventoryKnown = true;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		Player player = client.getLocalPlayer();
		if (!inRewardArea(player))
		{
			return;
		}

		TileItem item = event.getItem();
		if (item.getOwnership() != TileItem.OWNERSHIP_SELF)
		{
			return;
		}

		// A reward that does not fit in the inventory lands under the player.
		if (!event.getTile().getWorldLocation().equals(player.getWorldLocation()))
		{
			return;
		}

		tickGains.merge(item.getId(), item.getQuantity(), Integer::sum);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		seedInventory();
		dropLostItems();

		// A gamble is only bought at the shop, but its reward can land after the
		// walk out, so only the reading is gated on being there.
		GambleTier tier = null;
		if (inRewardArea(client.getLocalPlayer()))
		{
			tier = readGamble();
		}
		else
		{
			pointsKnown = false;
		}

		// The tick before counts too, in case a reward is applied ahead of the
		// points it cost, but only for a gamble that nothing else is waiting on.
		boolean lookBack = tier != null && pending.isEmpty();

		if (tier != null)
		{
			pending.addLast(new PendingGamble(tier, REWARD_WINDOW_TICKS));
		}

		boolean rewardTaken = false;
		if (!pending.isEmpty() && (!tickGains.isEmpty() || (lookBack && !previousGains.isEmpty())))
		{
			Map<Integer, Integer> reward = new HashMap<>(tickGains);
			if (lookBack)
			{
				previousGains.forEach((id, quantity) -> reward.merge(id, quantity, Integer::sum));
			}

			// What arrives belongs to the gamble that has been waiting longest.
			record(pending.removeFirst().getTier(), reward);
			rewardTaken = true;
		}

		pending.removeIf(gamble -> !gamble.age());

		previousGains.clear();
		if (!rewardTaken)
		{
			previousGains.putAll(tickGains);
		}

		tickGains.clear();
		tickLosses.clear();
	}

	/**
	 * Takes the inventory baseline, on the client thread. Plugin startUp and
	 * shutDown run on the event dispatch thread, where reading client state is
	 * not safe.
	 */
	private void seedInventory()
	{
		if (inventoryKnown)
		{
			return;
		}

		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container != null)
		{
			inventory.putAll(countItems(container));
			inventoryKnown = true;
		}
	}

	/**
	 * Nets an item lost from the inventory against the same item gained on the
	 * tick, so that dropping something is not read as receiving it.
	 */
	private void dropLostItems()
	{
		tickLosses.forEach((id, lost) ->
		{
			Integer gained = tickGains.get(id);
			if (gained == null)
			{
				return;
			}

			if (gained > lost)
			{
				tickGains.put(id, gained - lost);
			}
			else
			{
				tickGains.remove(id);
			}
		});
	}

	/**
	 * Reads the honour point totals and returns the gamble they were just spent
	 * on, if any.
	 *
	 * <p>A gamble takes its whole cost out of one role. Buying penance gear
	 * takes points out of all four roles at once, and a role level up moves that
	 * role's level varbit as well, so both are left alone.
	 */
	private GambleTier readGamble()
	{
		BaRole[] roles = BaRole.values();
		int spent = 0;
		int rolesCharged = 0;
		boolean leveled = false;

		for (int i = 0; i < roles.length; i++)
		{
			int currentPoints = roles[i].points(client);
			int currentLevel = roles[i].level(client);

			if (pointsKnown)
			{
				if (currentPoints < points[i])
				{
					spent = points[i] - currentPoints;
					rolesCharged++;
				}

				if (currentLevel != levels[i])
				{
					leveled = true;
				}
			}

			points[i] = currentPoints;
			levels[i] = currentLevel;
		}

		if (!pointsKnown)
		{
			pointsKnown = true;
			return null;
		}

		if (rolesCharged != 1 || leveled)
		{
			return null;
		}

		return GambleTier.forCost(spent);
	}

	/**
	 * Hands a reward to the Loot Tracker. A high gamble is tracked so that its
	 * reward is not taken for the next gamble's, but it is not passed on, since
	 * the client's own Loot Tracker already records that tier.
	 */
	private void record(GambleTier tier, Map<Integer, Integer> reward)
	{
		if (!tier.isRecorded())
		{
			return;
		}

		List<ItemStack> items = new ArrayList<>(reward.size());
		reward.forEach((id, quantity) -> items.add(new ItemStack(id, quantity)));

		log.debug("{} for {} points: {}", tier.getLootName(), tier.getCost(), items);

		eventBus.post(PluginLootReceived.builder()
			.source(this)
			.name(tier.getLootName())
			.type(LootRecordType.EVENT)
			.items(items)
			.build());
	}

	private static boolean inRewardArea(Player player)
	{
		return player != null && player.getWorldLocation().getRegionID() == BA_LOBBY_REGION;
	}

	private static Map<Integer, Integer> countItems(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();

		for (Item item : container.getItems())
		{
			if (item.getId() > -1)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		return counts;
	}
}
