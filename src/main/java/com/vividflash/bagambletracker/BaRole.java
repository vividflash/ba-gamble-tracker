package com.vividflash.bagambletracker;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * The four Barbarian Assault roles and the varbits holding their honour points
 * and role level.
 */
enum BaRole
{
	ATTACKER(VarbitID.BARBASSAULT_POINTS_ATTACKER_BASE, VarbitID.BARBASSAULT_POINTS_ATTACKER_EXTRA, VarbitID.BARBASSULT_ROLELEVEL_ATT),
	COLLECTOR(VarbitID.BARBASSAULT_POINTS_COLLECTOR_BASE, VarbitID.BARBASSAULT_POINTS_COLLECTOR_EXTRA, VarbitID.BARBASSULT_ROLELEVEL_COL),
	DEFENDER(VarbitID.BARBASSAULT_POINTS_DEFENDER_BASE, VarbitID.BARBASSAULT_POINTS_DEFENDER_EXTRA, VarbitID.BARBASSULT_ROLELEVEL_DEF),
	HEALER(VarbitID.BARBASSAULT_POINTS_HEALER_BASE, VarbitID.BARBASSAULT_POINTS_HEALER_EXTRA, VarbitID.BARBASSULT_ROLELEVEL_HEAL);

	/**
	 * A role's points do not fit in one varbit, so the total is split over a
	 * base varbit holding the low 9 bits and an extra varbit counting whole
	 * multiples of 512.
	 */
	private static final int EXTRA_STEP = 512;

	private final int pointsBaseVarbit;
	private final int pointsExtraVarbit;
	private final int levelVarbit;

	BaRole(int pointsBaseVarbit, int pointsExtraVarbit, int levelVarbit)
	{
		this.pointsBaseVarbit = pointsBaseVarbit;
		this.pointsExtraVarbit = pointsExtraVarbit;
		this.levelVarbit = levelVarbit;
	}

	int points(Client client)
	{
		return client.getVarbitValue(pointsExtraVarbit) * EXTRA_STEP + client.getVarbitValue(pointsBaseVarbit);
	}

	int level(Client client)
	{
		return client.getVarbitValue(levelVarbit);
	}
}
