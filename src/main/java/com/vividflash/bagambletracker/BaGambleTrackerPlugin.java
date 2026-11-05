package com.vividflash.bagambletracker;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.PluginLootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
@PluginDescriptor(
	name = "BA Gamble Loot",
	description = "Barbarian Assault Gamble Tracking and Misclick Guard",
	tags = {"barbarian", "assault", "ba", "gamble", "minigame", "loot", "tracker", "guard", "misclick"}
)
public class BaGambleTrackerPlugin extends Plugin
{
	private static final int BA_LOBBY_REGION = 10039;

	private static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";

	// The release the one-time notice below belongs to, not the packaged version.
	private static final String VERSION = "1.1";
	private static final String UPDATE_MESSAGE =
		"BA Gamble Loot v1.1: Each tier can block or highlight its shop row. "
			+ "Settings in Diagnostics. Fixed medium gambles.";

	// Dark red for the one-time update notice, legible on either chatbox background.
	private static final Color UPDATE_MESSAGE_COLOR = new Color(0x8B0000);

	// Ticks to wait for a reward before the gamble is dropped.
	private static final int REWARD_WINDOW_TICKS = 10;


	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private BaGambleTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GambleGuardOverlay overlay;

	// inventory and counted, and tickGains and previousGains, swap roles instead of copying.
	private Map<Integer, Integer> inventory = new HashMap<>();
	private Map<Integer, Integer> counted = new HashMap<>();
	private Map<Integer, Integer> tickGains = new HashMap<>();
	private final Map<Integer, Integer> tickLosses = new HashMap<>();
	private Map<Integer, Integer> previousGains = new HashMap<>();
	private final Deque<PendingGamble> pending = new ArrayDeque<>();
	// values() copies the array on every call.
	private static final BaRole[] ROLES = BaRole.values();
	private final int[] points = new int[ROLES.length];
	private final int[] levels = new int[ROLES.length];

	private boolean inventoryKnown;
	private boolean pointsKnown;
	private boolean reloaded;

	// Set once a tick, so the per-frame and per-spawn handlers do not look up the region.
	private boolean inLobby;

	private GambleTier selectedTier;
	private boolean updateChecked;

	// The guard settings, read on startUp and on change so the overlay does not ask the config each frame.
	private final GuardMode[] guards = new GuardMode[GambleTier.TIERS.length];
	private boolean anyGuardOn;

	@Provides
	BaGambleTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaGambleTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.debug("[state] startUp");
		log.debug("[config] low={} medium={} high={} acceptPathOnly={} logCostMismatch={} logGambles={} logRewards={} logGuards={}",
			config.lowGambleGuard(), config.mediumGambleGuard(), config.highGambleGuard(), config.acceptPathOnly(),
			config.logCostMismatch(), config.logGambles(), config.logRewards(), config.logGuards());
		reset();
		readGuards();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		log.debug("[state] shutDown");
		overlayManager.remove(overlay);
		// No reset here: shutDown runs off the client thread, where clearing the maps could race a
		// handler still using them. startUp clears them before the handlers are registered again.
	}

	private void reset()
	{
		inventory.clear();
		tickGains.clear();
		tickLosses.clear();
		previousGains.clear();
		pending.clear();
		selectedTier = null;
		updateChecked = false;
		inventoryKnown = false;
		pointsKnown = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BaGambleTrackerConfig.GROUP.equals(event.getGroup()))
		{
			readGuards();
		}
	}

	private void readGuards()
	{
		anyGuardOn = false;

		for (GambleTier tier : GambleTier.TIERS)
		{
			GuardMode mode = GuardMode.forTier(config, tier);
			guards[tier.ordinal()] = mode;
			anyGuardOn |= mode != null && mode != GuardMode.OFF;
		}
	}

	GuardMode guardMode(GambleTier tier)
	{
		return guards[tier.ordinal()];
	}

	boolean isAnyGuardOn()
	{
		return anyGuardOn;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			// A loading screen leaves the inventory and the points alone, so it
			// is not worth starting over for. Anything else, a login, a hop or
			// a dropped connection, is.
			if (reloaded)
			{
				log.debug("[state] logged in after a non-loading state change, tracking reset");
				reloaded = false;
				reset();
			}

			return;
		}

		inLobby = false;

		if (state != GameState.LOADING)
		{
			reloaded = true;
		}
	}

	// Follows what the shop has selected. The tier comes from the row rather than from what it
	// charges, so the plugin does not have to know the price.
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// A component op arrives under either action depending on its op index,
		// so both have to be taken or the hook silently never fires.
		if (event.getMenuAction() != MenuAction.CC_OP
			&& event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY)
		{
			return;
		}

		// The rows are the button layer's own children, in the order the shop
		// builds them, so the child index places the tier.
		if (event.getParam1() == InterfaceID.BarbassaultRewardShop.BUTTON_LAYER)
		{
			GambleTier row = GambleTier.forRow(event.getParam0() + 1);
			if (row != null && GuardMode.forTier(config, row) == GuardMode.BLOCK)
			{
				event.consume();
				say(config.logGuards(), "blocked " + name(row));
				return;
			}

			// The click is what selects the row, so remember where it points.
			// The shop holds one selection, so a click on anything else clears
			// this rather than leaving a stale tier behind.
			if (row != null)
			{
				say(config.logGambles(), "selected " + name(row));
			}
			else if (selectedTier != null)
			{
				say(config.logGambles(), name(selectedTier) + " no longer selected, another row clicked");
			}

			selectedTier = row;
			return;
		}

		if (event.getParam1() != InterfaceID.BarbassaultRewardShop.ACCEPT_BUTTON)
		{
			return;
		}

		int row = client.getVarpValue(VarPlayerID.IF1);
		GambleTier tier = GambleTier.forRow(row);

		// The var is the shop's own record of the selection and is read first.
		// It is shared with other interfaces though, so a row the player was
		// seen clicking stands in when it does not name a gamble.
		if (tier == null)
		{
			tier = selectedTier;
		}

		if (tier == null)
		{
			say(config.logGambles(), "accept on row " + row + ", not a gamble");
			return;
		}

		// A row stays selected once clicked, so Accept can buy a guarded tier
		// without the row itself ever being clicked again.
		if (GuardMode.forTier(config, tier) == GuardMode.BLOCK)
		{
			event.consume();
			say(config.logGuards(), "blocked " + name(tier) + " on accept");
			return;
		}

		// The shop keeps the row selected, so the same gamble can be bought over
		// and over without going back to the list. The tier is held until the
		// player selects something else or leaves.
		selectedTier = tier;
		say(config.logGambles(), "accept on " + name(tier) + ", row " + row);
	}

	// Gives up on a waiting gamble when the bank opens. Nothing else the player can reach inside the
	// window moves the inventory in bulk, and a withdrawal booked as gamble loot is a wrong record
	// rather than a missing one.
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.BANKMAIN)
		{
			return;
		}

		pending.forEach(gamble -> say(config.logRewards(),
			name(gamble.getTier()) + " given up on, the bank opened"));
		pending.clear();
		previousGains.clear();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		// A gain only counts in the lobby or while a gamble waits on its reward. Anywhere else the
		// baseline is dropped, and the next tick that needs it takes it again.
		if (!inLobby && pending.isEmpty())
		{
			inventoryKnown = false;
			return;
		}

		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}

		Map<Integer, Integer> current = counted;
		countItems(container, current);

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

		counted = inventory;
		inventory = current;
		inventoryKnown = true;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (!inLobby)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (!inRewardArea(player))
		{
			return;
		}

		// A reward that does not fit in the inventory lands under the player.
		if (!event.getTile().getWorldLocation().equals(player.getWorldLocation()))
		{
			return;
		}

		TileItem item = event.getItem();
		int ownership = item.getOwnership();

		if (wants(config.logRewards()))
		{
			say(config.logRewards(), "ground " + describe(item.getId(), item.getQuantity()) + ", ownership " + ownership);
		}

		if (ownership != TileItem.OWNERSHIP_SELF && ownership != TileItem.OWNERSHIP_NONE)
		{
			return;
		}

		tickGains.merge(item.getId(), item.getQuantity(), Integer::sum);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		maybeAnnounceUpdate();

		if (!tickLosses.isEmpty())
		{
			dropLostItems();
		}

		inLobby = inRewardArea(client.getLocalPlayer());

		if (inLobby || !pending.isEmpty())
		{
			seedInventory();
		}

		// A gamble is only bought at the shop, but its reward can land after the
		// walk out, so only the reading is gated on being there.
		GambleTier tier = null;

		if (inLobby)
		{
			tier = readGamble();
		}
		else
		{
			// The shop is gone, and so is what it had selected.
			dropSelection("left the lobby");
			pointsKnown = false;
		}

		// The tick before counts too, in case a reward is applied ahead of the
		// points it cost, but only for a gamble that nothing else is waiting on.
		boolean lookBack = tier != null && pending.isEmpty();

		if (tier != null)
		{
			pending.addLast(new PendingGamble(tier, client.getTickCount() + REWARD_WINDOW_TICKS));
		}

		boolean rewardTaken = false;
		if (!pending.isEmpty() && (!tickGains.isEmpty() || (lookBack && !previousGains.isEmpty())))
		{
			Map<Integer, Integer> reward = new HashMap<>(tickGains);
			if (lookBack && tickGains.isEmpty())
			{
				previousGains.forEach((id, quantity) -> reward.merge(id, quantity, Integer::sum));
			}

			// What arrives belongs to the gamble that has been waiting longest.
			record(pending.removeFirst().getTier(), reward);
			rewardTaken = true;
		}

		// Every gamble waits the same number of ticks and they queue in order, so the oldest expires first.
		while (!pending.isEmpty() && pending.peekFirst().isExpired(client.getTickCount()))
		{
			say(config.logRewards(), name(pending.removeFirst().getTier()) + " went " + REWARD_WINDOW_TICKS + " ticks with no reward");
		}

		if (rewardTaken)
		{
			previousGains.clear();
		}
		else
		{
			Map<Integer, Integer> older = previousGains;
			previousGains = tickGains;
			tickGains = older;
		}

		tickGains.clear();
		tickLosses.clear();
	}

	private void dropSelection(String reason)
	{
		if (selectedTier == null)
		{
			return;
		}

		say(config.logGambles(), name(selectedTier) + " no longer selected, " + reason);
		selectedTier = null;
	}

	// Says what changed, once, on the first login after an update. A fresh install is stamped without
	// a notice, since there is nothing to catch up on.
	private void maybeAnnounceUpdate()
	{
		if (updateChecked)
		{
			return;
		}

		updateChecked = true;

		String lastSeen = configManager.getConfiguration(BaGambleTrackerConfig.GROUP, LAST_SEEN_VERSION_KEY);
		if (VERSION.equals(lastSeen))
		{
			return;
		}

		configManager.setConfiguration(BaGambleTrackerConfig.GROUP, LAST_SEEN_VERSION_KEY, VERSION);
		if (lastSeen == null || lastSeen.isEmpty())
		{
			log.debug("[update] fresh install, stamped version {} without a notice", VERSION);
			return;
		}

		log.debug("[update] notice queued, last seen {} now {}", lastSeen, VERSION);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(UPDATE_MESSAGE_COLOR, UPDATE_MESSAGE)
				.build())
			.build());
	}

	// Takes the inventory baseline, on the client thread. Plugin startUp and shutDown run on the event
	// dispatch thread, where reading client state is not safe.
	private void seedInventory()
	{
		if (inventoryKnown)
		{
			return;
		}

		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container != null)
		{
			countItems(container, inventory);
			inventoryKnown = true;
		}
	}

	// Nets an item lost from the inventory against the same item gained on the tick, so that dropping
	// something is not read as receiving it.
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

	// Reads the honour point totals and returns the gamble they were just spent on, if any.
	//
	// The shop keeps a row selected, so any charge taken while a gamble row is the selection is that
	// gamble, whatever it cost. Buying gear or a level up selects its own row first, which clears the
	// gamble.
	private GambleTier readGamble()
	{
		BaRole[] roles = ROLES;
		// Made on the first change only, since most ticks change nothing.
		StringBuilder moved = null;
		int spent = 0;
		int rolesCharged = 0;
		boolean leveled = false;

		for (int i = 0; i < roles.length; i++)
		{
			int currentPoints = roles[i].points(client);
			int currentLevel = roles[i].level(client);

			if (pointsKnown)
			{
				if (currentPoints != points[i])
				{
					moved = moved == null ? new StringBuilder() : moved;
					moved.append(' ').append(roles[i]).append(' ')
						.append(points[i]).append(" to ").append(currentPoints);
				}

				if (currentPoints < points[i])
				{
					spent = points[i] - currentPoints;
					rolesCharged++;
				}

				if (currentLevel != levels[i])
				{
					leveled = true;
					moved = moved == null ? new StringBuilder() : moved;
					moved.append(' ').append(roles[i]).append(" level ")
						.append(levels[i]).append(" to ").append(currentLevel);
				}
			}

			points[i] = currentPoints;
			levels[i] = currentLevel;
		}

		if (!pointsKnown)
		{
			// Say so, otherwise a charge swallowed by the first reading in the
			// lobby is indistinguishable from the plugin never running.
			pointsKnown = true;
			say(config.logGambles(), "watching the points");
			return null;
		}

		if (moved == null)
		{
			return null;
		}

		// A level up charges a role too, and moves that role's level with it.
		GambleTier tier = null;

		if (spent > 0 && !leveled)
		{
			// The selected row names the tier whatever it charged. Reading the
			// cost only has to stand in when the shop was never seen open.
			tier = selectedTier;

			if (tier == null && !config.acceptPathOnly())
			{
				tier = byCost(rolesCharged, spent);
			}
		}

		if (wants(config.logGambles()))
		{
			say(config.logGambles(), "points moved:" + moved + ", spent " + spent + " over " + rolesCharged
				+ " role(s), level moved " + leveled + ", selected " + name(selectedTier)
				+ ", reads as " + name(tier));
		}

		// The price is not what names the tier, but a charge that does not match
		// the row is the one thing worth saying out loud: it is either a stale
		// selection or a price this plugin has wrong.
		if (tier != null && spent != tier.getCost() && wants(config.logCostMismatch()))
		{
			GambleTier byCost = byCost(rolesCharged, spent);
			say(config.logCostMismatch(), name(tier) + " charged " + spent + ", not the "
				+ tier.getCost() + " the shop lists" + (byCost == null ? "" : ", which is a " + name(byCost) + " price"));
		}

		return tier;
	}

	private static GambleTier byCost(int rolesCharged, int spent)
	{
		return rolesCharged == 1 ? GambleTier.forCost(spent) : null;
	}

	// Hands a reward to the Loot Tracker. A high gamble is tracked so that its reward is not taken for
	// the next gamble's, but it is not passed on, since the client's own Loot Tracker already records
	// that tier.
	private void record(GambleTier tier, Map<Integer, Integer> reward)
	{
		if (!tier.isRecorded())
		{
			if (wants(config.logRewards()))
			{
				say(config.logRewards(), name(tier) + " took " + describe(reward) + ", not recorded");
			}
			return;
		}

		if (wants(config.logRewards()))
		{
			say(config.logRewards(), name(tier) + " recorded " + describe(reward));
		}

		List<ItemStack> items = new ArrayList<>(reward.size());
		reward.forEach((id, quantity) -> items.add(new ItemStack(id, quantity)));

		eventBus.post(PluginLootReceived.builder()
			.source(this)
			.name(tier.getLootName())
			.type(LootRecordType.EVENT)
			.items(items)
			.build());
	}

	// Whether a say line goes anywhere, so a caller can skip building an expensive one.
	private static boolean wants(boolean enabled)
	{
		return enabled || log.isDebugEnabled();
	}

	private void say(boolean enabled, String message)
	{
		log.debug("[gamble] {} (chat={})", message, enabled);

		if (!enabled)
		{
			return;
		}

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.value("[BA Gamble] " + message)
			.build());
	}

	private String describe(Map<Integer, Integer> items)
	{
		if (items.isEmpty())
		{
			return "nothing";
		}

		StringBuilder text = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			if (text.length() > 0)
			{
				text.append(", ");
			}
			text.append(describe(entry.getKey(), entry.getValue()));
		}
		return text.toString();
	}

	private String describe(int id, int quantity)
	{
		try
		{
			return quantity + " x " + itemManager.getItemComposition(id).getName();
		}
		catch (RuntimeException e)
		{
			// The id alone is enough to read the line.
			return quantity + " x item " + id;
		}
	}

	private static String name(GambleTier tier)
	{
		return tier == null ? "no gamble" : tier.name().toLowerCase(Locale.ROOT) + " gamble";
	}

	boolean isInLobby()
	{
		return inLobby;
	}

	private static boolean inRewardArea(Player player)
	{
		return player != null && player.getWorldLocation().getRegionID() == BA_LOBBY_REGION;
	}

	private static void countItems(ItemContainer container, Map<Integer, Integer> counts)
	{
		counts.clear();

		for (Item item : container.getItems())
		{
			if (item.getId() > -1)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}
}
