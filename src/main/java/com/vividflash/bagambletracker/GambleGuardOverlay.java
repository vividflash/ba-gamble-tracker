package com.vividflash.bagambletracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks the guarded gamble rows, green where the click still goes through and
 * red where it does not. This is drawn over the interface rather than written
 * into it, because the shop recolours a row on mouseover and on selection and
 * would paint over anything the plugin set.
 */
@Singleton
public class GambleGuardOverlay extends Overlay
{
	private static final Color BLOCK_FILL = new Color(122, 13, 13, 70);
	private static final Color BLOCK_OUTLINE = new Color(122, 13, 13, 200);
	private static final Color HIGHLIGHT_FILL = new Color(13, 122, 13, 70);
	private static final Color HIGHLIGHT_OUTLINE = new Color(13, 122, 13, 200);

	private final Client client;
	private final BaGambleTrackerConfig config;

	@Inject
	GambleGuardOverlay(Client client, BaGambleTrackerConfig config)
	{
		this.client = client;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget layer = client.getWidget(InterfaceID.BarbassaultRewardShop.BUTTON_LAYER);
		if (layer == null || layer.isHidden())
		{
			return null;
		}

		// A row scrolled out of the list is clipped by its container rather than
		// hidden, and keeps reporting bounds off the end of the interface. The
		// list that does the clipping is the rewards layer, which is the one the
		// shop sets a scroll size on, so the drawing is held to that.
		Widget list = client.getWidget(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARDS);
		Rectangle visible = list == null ? layer.getBounds() : list.getBounds().intersection(layer.getBounds());

		Shape clip = graphics.getClip();
		graphics.clip(visible);

		try
		{
			for (GambleTier tier : GambleTier.values())
			{
				GuardMode mode = GuardMode.forTier(config, tier);
				if (mode == null || mode == GuardMode.OFF)
				{
					continue;
				}

				// The rows are the button layer's own children, one per shop row
				// in the order the shop builds them, so the row number places it.
				Widget row = layer.getChild(tier.getRow() - 1);
				if (row == null || row.isHidden())
				{
					continue;
				}

				boolean blocked = mode == GuardMode.BLOCK;
				Rectangle bounds = row.getBounds();
				graphics.setColor(blocked ? BLOCK_FILL : HIGHLIGHT_FILL);
				graphics.fill(bounds);
				graphics.setColor(blocked ? BLOCK_OUTLINE : HIGHLIGHT_OUTLINE);
				graphics.draw(bounds);
			}
		}
		finally
		{
			graphics.setClip(clip);
		}

		return null;
	}
}
