package com.teslamaps.features.npc;

/** Single Skyblock NPC entry with location and metadata */
public record NpcData(
	String name,
	String island,
	String warpCommand,
	double x, double y, double z,
	String description
) {
	/** Whether this NPC has known coordinates */
	public boolean hasPosition() {
		return x != 0 || y != 0 || z != 0;
	}
}
