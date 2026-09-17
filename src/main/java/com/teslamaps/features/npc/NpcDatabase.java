package com.teslamaps.features.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static database of all known Skyblock NPCs with locations */
public final class NpcDatabase {

	private static final List<NpcData> NPCS = new ArrayList<>();
	private static final List<NpcData> UNIQUE_NPCS = new ArrayList<>();

	private static final Map<String, Integer> ISLAND_ORDER = Map.ofEntries(
		Map.entry("Hub", 0),
		Map.entry("The Park", 1),
		Map.entry("Spider's Den", 2),
		Map.entry("The End", 3),
		Map.entry("Gold Mine", 4),
		Map.entry("Deep Caverns", 5),
		Map.entry("Dwarven Mines", 6),
		Map.entry("Crystal Hollows", 7),
		Map.entry("Dungeon Hub", 8),
		Map.entry("The Farming Islands", 9),
		Map.entry("Crimson Isle", 10),
		Map.entry("Jerry's Workshop", 11),
		Map.entry("The Rift", 12),
		Map.entry("Garden", 13),
		Map.entry("Glacite Tunnels", 14),
		Map.entry("Galatea", 15),
		Map.entry("Backwater Bayou", 16),
		Map.entry("Torrhus Canyon", 17),
		Map.entry("Lotus Atoll", 18),
		Map.entry("Critter Safari", 19)
	);

	static {

		bayou("Captain Baha", -22.5, 75.0, -8.5, "NPC");
		bayou("Junker Joel", 6.5, 72.0, 4.5, "NPC");
		bayou("Hattie", 17.5, 71.0, 14.5, "NPC");
		bayou("Roddy", 21.5, 78.0, -23.5, "NPC");


		crimson("Auction Agent", -633.5, 103.0, -783.5, "NPC");
		crimson("Auction Agent", -639.5, 103.0, -783.5, "NPC");
		crimson("Vulcan", -363.0, 115.0, -1031.0, "NPC");
		crimson("Crag", -371.0, 114.0, -1042.0, "NPC");
		crimson("Aviar", -360.5, 117.0, -971.5, "NPC");
		crimson("Gnyl", -376.5, 117.0, -971.5, "NPC");
		crimson("Vesuvius", -381.0, 115.0, -1031.0, "NPC");
		crimson("Drakuu", -324.5, 191.0, -1015.5, "NPC");
		crimson("Jine", -545.5, 119.0, -854.5, "NPC");
		crimson("Bruto", -612.5, 100.0, -786.5, "NPC");
		crimson("Barter", -624.5, 101.0, -794.5, "NPC");
		crimson("Marthos", -644.5, 101.0, -825.5, "NPC");
		crimson("Bromm", -636.5, 103.0, -784.5, "NPC");
		crimson("Pig", -699.5, 131.0, -887.5, "NPC");
		crimson("Gris", -684.5, 124.0, -828.5, "NPC");
		crimson("Deng", -686.4, 102.0, -764.9, "NPC");
		crimson("Kutral", -658.5, 107.0, -741.5, "NPC");
		crimson("Grog", -663.5, 107.0, -736.5, "NPC");
		crimson("Nall", -657.5, 107.0, -738.5, "NPC");
		crimson("An", -621.5, 108.0, -741.5, "NPC");
		crimson("Plenk", -572.5, 118.0, -739.5, "NPC");
		crimson("Pomtair", -569.5, 120.0, -775.5, "NPC");
		crimson("Ugo", -612.5, 107.0, -688.5, "NPC");
		crimson("Suus", -615.5, 115.0, -706.5, "NPC");
		crimson("Bruuh", -635.5, 124.0, -688.5, "NPC");
		crimson("Kaus", -596.0, 113.0, -640.0, "NPC");
		crimson("Kuudra Archaeologist", -570.5, 111.0, -642.5, "NPC");
		crimson("Lasea", -566.0, 108.0, -657.0, "NPC");
		crimson("Chief Scorn", -580.5, 115.5, -687.5, "NPC");
		crimson("Barbarian Emissary", -580.5, 99.0, -710.5, "NPC");
		crimson("Elle", -364.5, 80.0, -476.5, "NPC");
		crimson("Elle", -102.5, 130.0, -917.5, "NPC");
		crimson("Master Tao", -234.0, 108.0, -602.0, "NPC");
		crimson("Krevius", -131.5, 101.0, -753.5, "NPC");
		crimson("Grelius", -131.5, 101.0, -755.5, "NPC");
		crimson("Queen Nyx", -117.5, 105.0, -754.5, "NPC");
		crimson("Mage Emissary", -131.5, 89.0, -722.5, "NPC");
		crimson("Ulyn", -125.5, 99.0, -783.5, "NPC");
		crimson("Colore", -131.0, 89.0, -711.0, "NPC");
		crimson("Offea", -87.5, 93.0, -743.6, "NPC");
		crimson("Alwin", -82.5, 92.0, -734.5, "NPC");
		crimson("Lys", -108.5, 99.0, -803.5, "NPC");
		crimson("Kheharad", -111.5, 99.0, -827.5, "NPC");
		crimson("Kuudra Loremaster", -106.5, 126.0, -817.5, "NPC");
		crimson("Rhanora", -143.5, 100.0, -828.5, "NPC");
		crimson("Mollim", -132.5, 100.0, -789.5, "NPC");
		crimson("Seffea", -139.1, 99.0, -816.3, "NPC");
		crimson("Pablo", -130.0, 127.5, -815.5, "NPC");
		crimson("Avorius", -152.5, 128.0, -809.5, "NPC");
		crimson("Alchemage", -86.0, 118.0, -771.0, "NPC");
		crimson("Belanor", -83.5, 108.0, -775.5, "NPC");
		crimson("Elmar", -60.5, 108.0, -746.5, "NPC");
		crimson("Selenar", -71.0, 107.0, -757.0, "NPC");
		crimson("Hilda", -46.5, 107.0, -779.5, "NPC");
		crimson("Rulenor", -26.5, 122.0, -769.5, "NPC");
		crimson("Ezekiel", -27.5, 107.0, -770.5, "NPC");
		crimson("Ziri", -74.5, 107.0, -793.5, "NPC");
		crimson("Udel", -78.5, 108.0, -787.5, "NPC");
		crimson("Velyna", -86.5, 104.0, -820.5, "NPC");
		crimson("Odexar", -112.5, 99.0, -844.5, "NPC");
		crimson("Rollim", -147.0, 106.0, -854.0, "NPC");
		crimson("Leo", -143.5, 100.0, -828.5, "NPC");
		crimson("Carrolyn", 1.0, 103.1, -803.0, "NPC");
		crimson("Igrupan", -31.0, 93.0, -824.0, "NPC");
		crimson("Mazakala", -3.5, 93.0, -825.5, "NPC");
		crimson("Arba", -3.5, 93.0, -820.5, "NPC");
		crimson("Arbadak", -0.5, 93.0, -824.5, "NPC");
		crimson("Scholar Alluin", -15.5, 114.0, -915.5, "NPC");
		crimson("Eludore", -101.5, 91.0, -872.5, "NPC");
		crimson("Odger", -373.5, 207.0, -810.5, "NPC");
		crimson("The Intruder", -50.0, 104.0, 71.0, "NPC");


		ch("Geonathan Greatforge", 530.5, 106.0, 556.5, "NPC");
		ch("Emissary Sisko", 495.5, 106.0, 556.5, "NPC");
		ch("Gemma", 475.5, 106.0, 513.5, "NPC");


		deep("Lift Operator", 45.5, 150.0, 15.5, "NPC");
		deep("Lift Operator", 45.5, 101.0, 17.5, "NPC");
		deep("Lift Operator", 45.5, 66.0, 15.5, "NPC");
		deep("Lift Operator", 45.5, 38.0, 15.5, "NPC");
		deep("Lift Operator", 45.5, 13.0, 15.5, "NPC");
		deep("Walter", 19.0, 156.0, -36.0, "NPC");
		deep("Redstone Miner", 25.0, 104.0, 16.5, "NPC");
		deep("Lapis Miner", -10.5, 120.0, 35.5, "NPC");
		deep("Rhys", 31.5, 12.0, 14.5, "NPC");


		dungeonHub("Hub Selector", -34.5, 119.0, 9.5, "NPC");
		dungeonHub("Malik", -49.5, 119.0, 10.5, "NPC");
		dungeonHub("Mort", -61.5, 123.0, 0.5, "NPC");
		dungeonHub("Ophelia", -49.5, 119.0, -9.5, "NPC");
		dungeonHub("Adventurer Saul", -54.5, 119.5, -3.5, "NPC");
		dungeonHub("Vulcan", -45.0, 120.2, -27.0, "NPC");
		dungeonHub("Crag", -47.5, 121.0, -24.5, "NPC");
		dungeonHub("Croesus", -27.5, 119.5, 35.5, "NPC");
		dungeonHub("Guildford", -18.5, 86.0, 4.5, "NPC");


		dwarven("Blacksmith", -6.5, 201.0, -154.5, "NPC");
		dwarven("Rhys", -38.0, 200.0, -119.0, "NPC");
		dwarven("Royal Guard", -45.5, 200.0, -118.5, "NPC");
		dwarven("Royal Guard", -45.5, 200.0, -124.5, "NPC");
		dwarven("Emissary Eliza", -37.5, 200.0, -131.5, "NPC");
		dwarven("Ivan", -62.5, 221.0, -131.5, "NPC");
		dwarven("Ian", -67.5, 221.0, -121.5, "NPC");
		dwarven("Ivy", -72.5, 221.0, -135.5, "NPC");
		dwarven("Banker Broadjaw", 13.5, 201.0, -148.5, "NPC");
		dwarven("Bomin", 26.6, 203.9, -144.8, "NPC");
		dwarven("Old Man Garry", 5.4, 200.0, -109.2, "NPC");
		dwarven("Hornum", 35.5, 202.3, -148.5, "NPC");
		dwarven("Brynmor", 32.0, 202.0, -154.5, "NPC");
		dwarven("Gimley", 29.5, 202.3, -151.5, "NPC");
		dwarven("Sargwyn", 37.5, 202.3, -150.5, "NPC");
		dwarven("Tarwen", 33.5, 202.3, -139.5, "NPC");
		dwarven("Bubu", -10.5, 201.0, -103.5, "NPC");
		dwarven("Bulvar", -15.5, 201.0, -98.5, "NPC");
		dwarven("Bednom", -30.0, 213.8, -89.0, "NPC");
		dwarven("Station Master", 38.5, 201.0, -85.5, "NPC");
		dwarven("Lumina", 77.5, 199.0, -110.5, "NPC");
		dwarven("Gwendolyn", 88.5, 198.0, -98.5, "NPC");
		dwarven("Fragilis", 88.0, 199.0, -108.0, "NPC");
		dwarven("Emissary Braum", 89.5, 198.0, -92.5, "NPC");
		dwarven("Geo", 87.5, 199.0, -115.5, "NPC");
		dwarven("Fetchur", 84.0, 224.0, -118.0, "NPC");
		dwarven("Forger", -22.5, 151.0, -49.5, "NPC");
		dwarven("Forger", -22.5, 151.0, -79.5, "NPC");
		dwarven("Forger", -3.5, 149.0, -108.5, "NPC");
		dwarven("Forger", 23.5, 151.0, -87.5, "NPC");
		dwarven("Forger", 23.5, 151.0, -57.5, "NPC");
		dwarven("Fred", -2.5, 149.0, -68.5, "NPC");
		dwarven("Jotraeline Greatforge", -6.5, 145.0, -18.5, "NPC");
		dwarven("Emissary Ceanna", 42.5, 134.5, 22.5, "NPC");
		dwarven("Silnar", 53.1, 141.5, 19.5, "NPC");
		dwarven("Bylma", -9.0, 128.0, 59.0, "NPC");
		dwarven("Emissary Carlton", -72.5, 153.0, -10.5, "NPC");
		dwarven("Emissary Lilith", 58.5, 198.0, -8.5, "NPC");
		dwarven("Castle Guard", 125.0, 187.0, 106.0, "NPC");
		dwarven("Castle Guard", 134.0, 187.0, 106.0, "NPC");
		dwarven("Castle Guard", 137.0, 192.0, 148.0, "NPC");
		dwarven("Castle Guard", 122.0, 192.0, 148.0, "NPC");
		dwarven("Castle Guard", 93.0, 196.0, 176.0, "NPC");
		dwarven("Castle Guard", 108.5, 202.0, 253.0, "NPC");
		dwarven("Castle Guard", 132.0, 202.0, 252.0, "NPC");
		dwarven("Castle Guard", 196.0, 204.0, 181.0, "NPC");
		dwarven("Tornora", 136.0, 196.0, 167.0, "NPC");
		dwarven("Queen Mismyla", 126.5, 195.0, 195.5, "NPC");
		dwarven("King", 129.5, 196.0, 196.5, "NPC");
		dwarven("Sheep", 65.5, 211.0, 198.5, "NPC");
		dwarven("Tal Ker", 193.5, 196.0, 205.5, "NPC");
		dwarven("Dalbrek", 191.0, 216.0, 154.0, "NPC");
		dwarven("Ticket Master", 86.5, 187.0, 114.5, "NPC");
		dwarven("Ticket Master", -146.5, 149.0, -30.5, "NPC");
		dwarven("Ticket Master", -134.5, 173.0, -62.5, "NPC");
		dwarven("Ticket Master", -96.5, 142.0, 111.5, "NPC");
		dwarven("Ticket Master", 46.5, 131.0, 173.5, "NPC");
		dwarven("Ticket Master", 0.5, 145.0, 23.5, "NPC");
		dwarven("Ticket Master", 23.5, 128.0, 207.5, "NPC");
		dwarven("Puzzler", 181.5, 196.0, 135.5, "NPC");
		dwarven("Emissary Wilson", 171.5, 150.0, 31.5, "NPC");
		dwarven("Marigold", 181.5, 150.0, 60.5, "NPC");
		dwarven("Guard Gornum", -158.5, 149.0, -14.5, "NPC");
		dwarven("Emissary Fraiser", -132.5, 174.0, -50.5, "NPC");
		dwarven("Dulin", 15.5, 128.0, 206.5, "NPC");
		dwarven("Cold Enjoyer", -15.2, 136.8, 217.3, "NPC");
		dwarven("Emissary Lissandra", 2.5, 121.1, 237.5, "NPC");
		dwarven("Scout Scardius", -16.5, 121.0, 232.5, "NPC");
		dwarven("Scout Plinius", 10.0, 121.0, 245.0, "NPC");
		dwarven("Dr. Stone", 28.0, 120.0, 238.5, "NPC");
		dwarven("Grandpa Wolf", -55.5, 125.0, 331.5, "NPC");


		galatea("Hina", -553.5, 110.0, -16.5, "NPC");
		galatea("Oden", -584.5, 113.0, -40.5, "NPC");
		galatea("Amaury", -578.5, 114.0, 2.0, "NPC");
		galatea("David Hunterborough", -593.5, 114.0, -13.5, "NPC");
		galatea("Salvage Old Attributes", -594.5, 114.0, -17.5, "NPC");
		galatea("Albert", -592.5, 113.0, 8.5, "NPC");
		galatea("Banker Bonsai", -598.7, 116.0, -37.3, "NPC");
		galatea("Pounce", -598.5, 127.0, -20.5, "NPC");
		galatea("Kysha", -601.5, 115.0, -12.0, "NPC");
		galatea("Talia", -615.5, 108.0, 2.5, "NPC");
		galatea("Loras", -593.5, 118.0, 14.5, "NPC");
		galatea("Swoop", -579.5, 113.8, -23.5, "NPC");
		galatea("Alan", -625.5, 116.0, -36.5, "NPC");
		galatea("Teddy", -656.5, 115.0, -39.5, "NPC");
		galatea("Jaeger", -651.5, 89.0, -24.5, "NPC");
		galatea("Agatha", -648.5, 89.0, -3.5, "NPC");
		galatea("Nemo", -619.5, 87.0, -6.5, "NPC");
		galatea("Kiara", -654.0, 113.0, -70.0, "NPC");
		galatea("Coral", -626.5, 99.0, -87.5, "NPC");
		galatea("Vaeri", -688.5, 116.0, -35.5, "NPC");
		galatea("Rosemary", -703.5, 162.0, 3.5, "NPC");
		galatea("Dax", -705.5, 94.0, 33.5, "NPC");
		galatea("Boris", -684.5, 129.0, 64.5, "NPC");
		galatea("Sawyer", -691.5, 121.0, 81.5, "NPC");
		galatea("Auryon", -557.5, 111.0, 48.5, "NPC");
		galatea("Martin", -579.5, 93.0, 31.5, "NPC");


		gold("Blacksmith", -39.5, 77.0, -299.5, "NPC");
		gold("Iron Forger", -1.5, 75.0, -307.5, "NPC");
		gold("Gold Forger", -27.5, 74.0, -294.5, "NPC");
		gold("Rusty", -20.0, 78.0, -325.4, "NPC");
		gold("Lazy Miner", -11.5, 78.0, -337.5, "NPC");

		hub("Agnes", 11, 75, 2.5, "NPC, can only be found during year of the witch");
		hub("Alcina", 17.5, 75, 1, "NPC, can only be found during year of the witch");
		hub("Carnival Leader", -89.5, 71, 11.5, "NPC, can only be found during carnival");
		hub("Chantelle", -83.5, 71, 11.5, "NPC, can only be found during carnival");
		hub("Doug", -78.5, 70.5, 22.5, "NPC, can only be found during carnival");
		hub("Carnival Fisherman", -81.5, 72, 29.5, "NPC, can only be found during carnival");
		hub("Carnival Pirateman", -107, 73, 28, "NPC, can only be found during carnival");
		hub("Carnival Cowboy", -103.5, 70, 38.5, "NPC, can only be found during carnival");
		hub("Bingo", 3.5, 78, 4.5, "NPC");
		hub("Fear Mongerer", -32.5, 69, 6.5, "NPC");
		hub("Minister", 8.5, 79.0, 17.5, "NPC");
		hub("Scoop", 4.5, 180, 56.5, "NPC");
		hub("Sirius", 91.5, 75, 176.5, "NPC");
		hub("Hub Selector", -5.5, 69.0, -22.5, "NPC");
		hub("Security Sloth", 10.5, 71.0, -15.5, "NPC");
		hub("Biblio", 8.5, 79.0, 10.5, "NPC");
		hub("Clerk Seraphine", -1.5, 79.0, 10.5, "NPC");
		hub("Salesman", -9.5, 71.0, -15.5, "NPC");
		hub("Shen's Agent", 6.5, 53.0, -15.5, "NPC");
		hub("Mayor", 6.5, 79.0, 19.5, "NPC");
		hub("Elizabeth", -6.2, 79.0, 19.3, "NPC");
		hub("The Handler", 40.5, 72.0, 1.5, "NPC");
		hub("Madame Eleanor Q. Goldsworth III", 33.5, 73.0, 11.5, "NPC");
		hub("Guy", 51.5, 78.0, 20.5, "NPC");
		hub("Curator", 27.5, 68.0, 33.5, "NPC");
		hub("Fisherwoman Enid", 41.5, 70.0, -22.2, "NPC");
		hub("Rabbit Bro", 57.5, 70.0, 5.5, "NPC");
		hub("Finance Rabbit", 51.7, 56.9, 11.5, "NPC");
		hub("Finance Rabbit", 51.7, 56.9, 15.5, "NPC");
		hub("Finance Rabbit", 65.3, 56.9, 0.5, "NPC");
		hub("Finance Rabbit", 60.3, 56.9, 11.5, "NPC");
		hub("Finance Rabbit", 60.3, 56.9, 15.5, "NPC");
		hub("Rabbit Granny", 58.5, 68.0, 10.5, "NPC");
		hub("Seymour", 29.0, 66.0, -40.0, "NPC");
		hub("Coach Jackrabbit", 63.5, 68.0, 3.5, "NPC");
		hub("Rabbit Dog", 63.5, 68.0, 10.5, "NPC");
		hub("Rabbit Sis", 68.5, 68.0, 12.5, "NPC");
		hub("Rabbit Cousin", 69.6, 68.0, 4.5, "NPC");
		hub("Rabbit Daddy", 76.5, 65.0, 13.5, "NPC");
		hub("Rabbit Uncle", 77.5, 65.0, 9.5, "NPC");
		hub("Rabbit Security", 66.0, 57.0, 7.5, "NPC");
		hub("Marco", 90.5, 76.0, 3.5, "NPC");
		hub("Vincent", 79.5, 74.0, 53.5, "NPC");
		hub("Fisherman Gerald", 118.5, 71.0, -32.5, "NPC");
		hub("Plumber Joe", 123.5, 74.0, -38.5, "NPC");
		hub("Fishing Merchant", 112.5, 71.0, -44.5, "NPC");
		hub("Gwynnie", 116.5, 71.0, -25.5, "NPC");
		hub("Alda", 71.0, 80.0, -59.0, "NPC");
		hub("Angler Angus", 125.5, 70.0, -67.5, "NPC");
		hub("Gavin", 147.5, 69.9, -59.5, "NPC");
		hub("Captain Baha", 162.5, 69.0, -65.5, "NPC");
		hub("Alchemist", 80.5, 72.0, -90.5, "NPC");
		hub("Ludleth", 81.5, 59.0, -112.5, "NPC");
		hub("Arthur", 53.5, 72.0, -111.5, "NPC");
		hub("Farm Merchant", 63.5, 72.0, -113.5, "NPC");
		hub("Jacob", 46.5, 73.0, -128.5, "NPC");
		hub("Anita", 53.5, 73.0, -128.5, "NPC");
		hub("Farmer Rigby", 61.5, 72.0, -147.5, "NPC");
		hub("Shania", 59.5, 72.0, -144.5, "NPC");
		hub("Dusk", 20.5, 63.0, -135.5, "NPC");
		hub("Smithmonger", 15.5, 63.0, -135.5, "NPC");
		hub("Blacksmith", 10.5, 63.0, -126.5, "NPC");
		hub("Mining Merchant", 14.5, 63.0, -114.0, "NPC");
		hub("Christopher", -14.5, 75.0, -75.5, "NPC");
		hub("Fann", 9.5, 73.0, -62.5, "NPC");
		hub("Mad Redstone Engineer", -8.5, 71.0, -55.5, "NPC");
		hub("Zog", 10.0, 69.0, -71.0, "NPC");
		hub("Builder", -8.5, 71.0, -61.5, "NPC");
		hub("Amelia", -15.5, 74.0, -68.5, "NPC");
		hub("Wool Weaver", -17.5, 71.0, -57.5, "NPC");
		hub("George", 21.5, 74.0, -59.5, "NPC");
		hub("Bea", 11.5, 72.0, -58.5, "NPC");
		hub("Kat", 10.5, 72.0, -53.5, "NPC");
		hub("Liz", -35.5, 70.0, -77.5, "NPC");
		hub("Richard", -32.5, 70.0, -80.5, "NPC");
		hub("Zarina", -32.5, 70.0, -75.5, "NPC");
		hub("Banker", -29.5, 72.0, -38.0, "NPC");
		hub("Bazaar Agent", -35.5, 73.0, -31.5, "NPC");
		hub("Jacobus", -49.5, 70.0, -60.5, "NPC");
		hub("Adventurer", -49.5, 69.0, -66.5, "NPC");
		hub("Ozanne", -54.5, 70.0, -65.5, "NPC");
		hub("Weaponsmith", -50.5, 69.0, -85.5, "NPC");
		hub("Jax", -40.5, 69.0, -92.5, "NPC");
		hub("Rosetta", -53.5, 69.0, -88.5, "NPC");
		hub("Librarian", -68.5, 70.0, -79.5, "NPC");
		hub("Maxwell", -66.5, 70.0, -66.5, "NPC");
		hub("Maths Enjoyer", -72.5, 70.0, -62.5, "NPC");
		hub("Pat", -87.5, 73.0, -94.5, "NPC");
		hub("Bartender", -83.5, 74.0, -135.5, "NPC");
		hub("Maddox the Slayer", -83.5, 68.0, -129.5, "NPC");
		hub("Malik", -80.5, 56.0, -119.5, "NPC");
		hub("Mort", -88.5, 55.0, -128.5, "NPC");
		hub("Ophelia", -85.5, 55.0, -139.5, "NPC");
		hub("Lumber Merchant", -125.0, 73.0, -42.5, "NPC");
		hub("Lumber Jack", -123.5, 73.0, -29.5, "NPC");
		hub("Carpenter", -137.5, 74.0, -41.5, "NPC");
		hub("Apprentice", -152.5, 63.0, -33.5, "NPC");
		hub("Lonely Philosopher", -250.7, 130.0, 41.2, "NPC");
		hub("Elise", 44.5, 77.0, 100.5, "NPC");
		hub("Nicole", 41.5, 93.0, 96.5, "NPC");
		hub("Udium", 46.5, 101.0, 100.5, "NPC");
		hub("Grumblefoot", 44.0, 112.4, 99.6, "NPC");
		hub("Erihann", 42.5, 97.0, 93.5, "NPC");
		hub("Wizard", 48.5, 119.0, 99.5, "NPC");
		hub("Shifty", 114.5, 73.0, 175.0, "NPC");
		hub("Gladiator", 123.5, 79.0, 165.5, "NPC");
		hub("Lucius", 125.0, 73.0, 165.0, "NPC");
		hub("Tia the Fairy", 119.5, 65.0, 147.5, "NPC");
		hub("Karis", 65.5, 81.0, -59.5, "NPC");
		hub("Bazaar", -33.5, 73.0, -22.5, "NPC");
		hub("Auction Agent", -33.5, 73.0, -17.5, "NPC");
		hub("Auction Agent", -33.5, 73.0, -7.5, "NPC");
		hub("Auction Master", -39.5, 73.0, -12.5, "NPC");
		hub("Damia", -16.5, 55.0, -10.5, "NPC");


		spider("Haymitch", -202.5, 83.0, -236.5, "NPC");
		spider("Bramass Beastslayer", -271.5, 113.0, -196.5, "NPC");
		spider("Grandma Wolf", -281.5, 122.0, -190.5, "NPC");
		spider("Shaggy", -278.5, 121.0, -182.5, "NPC");
		spider("Michael", -264.5, 94.5, -284.5, "NPC");
		spider("Ike", -260.5, 94.5, -284.5, "NPC");
		spider("Archaeologist", -360.5, 111.0, -290.0, "NPC");
		spider("Spider Tamer", -298.5, 62.0, -194.5, "NPC");


		end("Pearl Dealer", -504.5, 101.0, -284.5, "NPC");
		end("Guber", -494.5, 121.0, -241.5, "NPC");
		end("Dragon Specialist", -588.5, 22.0, -270.5, "NPC");
		end("Tyzzo", -597.0, 5.0, -272.0, "NPC");


		barn("Farmhand", 144.5, 73.0, -240.5, "NPC");
		barn("Windmill Operator", 98.5, 89.0, -282.5, "NPC");
		barn("Beth", 163.5, 77.0, -359.0, "NPC");
		barn("Mason", 177.5, 77.0, -356.0, "NPC");
		barn("Friendly Hiker", 181.5, 77.0, -381.0, "NPC");
		barn("Farmer Jon", 167.5, 91.0, -598.5, "NPC");
		barn("Jake", 261.5, 184.0, -565.5, "NPC");
		barn("Treasure Hunter", 200.5, 92.0, -437.0, "NPC");
		barn("Shepherd", 388.5, 85.0, -372.5, "NPC");
		barn("Talbot", 285.5, 104.0, -548.5, "NPC");
		barn("Tony", 278.5, 104.0, -544.5, "NPC");
		barn("Tammy", 284.5, 104.0, -544.5, "NPC");
		barn("Trevor", 281.5, 104.0, -542.5, "NPC");
		barn("Hungry Hiker", 269.5, 48.0, -480.5, "NPC");
		barn("Moby", 206.0, 43.0, -500.0, "NPC");


		park("Charlie", -277.5, 80.0, -17.5, "NPC");
		park("Vanessa", -304.5, 76.0, -79.5, "NPC");
		park("Ryan", -364.5, 102.5, -90.5, "NPC");
		park("Old Shaman Nyko", -370.5, 84.0, -64.5, "NPC");
		park("Campfire Initiate", -358.5, 102.5, -94.5, "NPC");
		park("Campfire Initiate", -362.5, 102.5, -97.5, "NPC");
		park("Campfire Adept", -360.5, 102.5, -96.5, "NPC");
		park("Kelly", -350.5, 94.0, 33.5, "NPC");
		park("Gustave", -363.5, 89.0, 44.5, "NPC");
		park("Melancholic Viking", -335.5, 92.5, 73.5, "NPC");
		park("Melody", -411.5, 109.0, 71.5, "NPC");
		park("Worker Xavier", -424.5, 110.0, -15.5, "NPC");
		park("Master Tactician Funk", -452.5, 110.0, 29.5, "NPC");
		park("Molbert", -447.5, 120.0, -63.5, "NPC");
		park("Juliette", -415.5, 130.0, -121.5, "NPC");
		park("Romero", -417.5, 130.0, -121.5, "NPC");


		rift("Seymour", -27.0, 71.0, -44.5, "NPC");
		rift("Plumber Joe", -62.0, 70.0, -78.0, "NPC");
		rift("Alchemist", -49.5, 70.0, -65.5, "NPC");
		rift("Cow", -38.4, 71.2, -164.3, "NPC");
		rift("Cow", -42.5, 72.0, -167.0, "NPC");
		rift("Cow", -47.5, 72.0, -161.0, "NPC");
		rift("Blacksmith", -1.5, 68.0, -142.0, "NPC");
		rift("Kat", -34.5, 70.0, -90.5, "NPC");
		rift("Maddox the Slayer", 204.5, 77.0, 45.0, "NPC");
		rift("Damia", 30.5, 72.0, -94.5, "NPC");
		rift("Sheep", -54.1, 71.7, -163.7, "NPC");
		rift("Sheep", -58.5, 72.0, -166.6, "NPC");
		rift("Sheep", -55.1, 73.2, -167.1, "NPC");
		rift("The Intruder", -50.0, 104.0, 71.0, "NPC");
		rift("Wizard", -47.5, 122.5, 77.5, "NPC");
		rift("Alatar", -46.6, 116.0, 70.2, "NPC");
		rift("Elise CXXXI", 9.9, 55.0, 76.0, "NPC");
		rift("Wizardman", -45.5, 90.0, 69.5, "NPC");
		rift("Wizardman", -222.5, 19.0, -88.5, "NPC");
		rift("Enigma", -35.0, 71.0, 90.0, "NPC");
		rift("Hound", -70.0, 66.0, 147.5, "NPC");
		rift("Motes Grubber", -92.3, 65.0, 156.9, "NPC");
		rift("Motes Grubber", -93.0, 70.5, -73.0, "NPC");
		rift("Motes Grubber", 164.5, 77.0, 125.5, "NPC");
		rift("Motes Grubber", 1.4, 114.8, 21.4, "NPC");
		rift("Inverted Sirius", -96.5, 75.0, 190.5, "NPC");
		rift("Argofay Threebrother", -98.5, 76.3, 153.9, "NPC");
		rift("Argofay Threebrother", -136.0, 75.0, 113.1, "NPC");
		rift("Argofay Threebrother", -92.8, 76.0, 108.7, "NPC");
		rift("Shifted", -120.5, 73.0, 174.5, "NPC");
		rift("Kiermet", -134.5, 67.0, 157.5, "NPC");
		rift("Ribbit", -136.5, 67.0, 158.5, "NPC");
		rift("Vreike", -124.3, 72.9, 172.5, "NPC");
		rift("Dackinoru", -118.3, 72.9, 172.5, "NPC");
		rift("Fafnir", -127.6, 72.9, 172.7, "NPC");
		rift("Ashera", -130.6, 72.9, 169.7, "NPC");
		rift("Arora", -128.6, 72.9, 168.7, "NPC");
		rift("Seskel", -128.6, 72.9, 164.3, "NPC");
		rift("Alabaster", -130.3, 72.9, 166.5, "NPC");
		rift("Garlacius", -128.6, 78.9, 170.7, "NPC");
		rift("Tybalt", -129.3, 78.9, 168.5, "NPC");
		rift("Lazarus", -127.6, 78.9, 166.3, "NPC");
		rift("Porhtal", -157.5, 70.5, 162.0, "NPC");
		rift("Argofay Trafficker", -136.4, 88.0, 114.0, "NPC");
		rift("Argofay Trafficker", -137.6, 97.0, 112.5, "NPC");
		rift("Argofay Trafficker", -150.7, 112.0, 114.5, "NPC");
		rift("Argofay Trafficker", -91.5, 90.0, 106.5, "NPC");
		rift("Tel Kar", -113.3, 69.0, 62.3, "NPC");
		rift("Argofay Serialbather", -137.5, 119.3, 115.5, "NPC");
		rift("Sad Jacquelle", -121.8, 119.7, 137.0, "NPC");
		rift("Argofay Treemerger", -145.0, 93.1, 140.0, "NPC");
		rift("Argofay Bugshopper", -95.0, 110.0, 95.3, "NPC");
		rift("Argofay Bughunter", -85.0, 108.0, 97.0, "NPC");
		rift("Argofay Tencounter", -93.0, 99.1, 95.2, "NPC");
		rift("Argofay Sonfather", -96.0, 100.0, 156.2, "NPC");
		rift("Mushroom Guy", -178.3, 75.0, 12.5, "NPC");
		rift("The Gooey", -166.0, 77.0, -54.0, "NPC");
		rift("Reed", -212.0, 72.0, 60.5, "NPC");
		rift("Roy", -204.6, 75.0, 49.3, "NPC");
		rift("Kay", -191.0, 68.0, 58.5, "NPC");
		rift("The Gill-Man", -205.1, 65.0, 50.3, "NPC");
		rift("Mole", -159.5, 97.0, -75.5, "NPC");
		rift("Dr. Edwin", -79.5, 73.0, 9.5, "NPC");
		rift("Grandma", -70.5, 63.9, -60.0, "NPC");
		rift("Gunther", -68.7, 71.1, -61.5, "NPC");
		rift("Finplex", -57.5, 68.0, -82.5, "NPC");
		rift("Cosmo", -66.5, 70.0, -92.3, "NPC");
		rift("Chef", -103.0, 72.1, -104.0, "NPC");
		rift("Joey McPizza", -105.5, 72.4, -102.5, "NPC");
		rift("Skylark", -71.5, 71.5, -115.0, "NPC");
		rift("Yoshua", -69.0, 71.5, -117.5, "NPC");
		rift("mrr_65", -86.8, 64.0, -114.6, "NPC");
		rift("Sorcerer Okron", -51.5, 70.0, -98.9, "NPC");
		rift("Disinfestor", -40.0, 70.0, -88.0, "NPC");
		rift("Disinfestor", -32.5, 77.0, -88.5, "NPC");
		rift("Disinfestor", -38.0, 71.0, -103.0, "NPC");
		rift("Disinfestor", -37.5, 77.0, -93.5, "NPC");
		rift("Transfigured Seraphine", -16.5, 71.0, -101.0, "NPC");
		rift("Harriette", -39.5, 54.0, -138.5, "NPC");
		rift("Creed", -51.5, 54.0, -137.6, "NPC");
		rift("Dust", -54.4, 54.0, -138.4, "NPC");
		rift("Soma", -52.0, 54.0, -141.0, "NPC");
		rift("Barry", -45.5, 54.0, -142.5, "NPC");
		rift("Violetta", -51.5, 54.0, -147.5, "NPC");
		rift("Frankie", -44.4, 54.0, -146.5, "NPC");
		rift("Stain", -57.6, 54.0, -146.1, "NPC");
		rift("Sick Farmer", -43.0, 72.0, -160.0, "NPC");
		rift("Chicken", -53.9, 72.0, -158.0, "NPC");
		rift("Chicken", -57.1, 71.7, -161.1, "NPC");
		rift("The Baba Yaga", -14.5, 71.0, -173.5, "NPC");
		rift("Taylor", -32.4, 71.0, -38.5, "NPC");
		rift("Lonely vaekx", -31.5, 69.3, -35.5, "NPC");
		rift("Lonely vaekx", -31.5, 69.3, -31.5, "NPC");
		rift("Joliet", -49.0, 68.0, -37.5, "NPC");
		rift("Ramero", -50.5, 68.0, -37.5, "NPC");
		rift("Detective Amog", -51.1, 68.0, -40.7, "NPC");
		rift("Detective Amog", -4.0, 187.0, 42.0, "NPC");
		rift("Unhinged Kloon", -56.5, 79.0, -13.7, "NPC");
		rift("Roger", -2.5, 70.0, -43.5, "NPC");
		rift("Marcia", 2.5, 71.0, -10.5, "NPC");
		rift("Cowboy Nick", -13.0, 70.0, -7.1, "NPC");
		rift("The Bankster", 32.0, 69.0, -37.0, "NPC");
		rift("Nene", 23.5, 88.5, -90.5, "NPC");
		rift("The Prince", 158.0, 74.0, 80.0, "NPC");
		rift("Fairylosopher", 252.4, 106.0, 76.7, "NPC");
		rift("Fairylosopher", 250.5, 123.0, 85.5, "NPC");
		rift("Fairylosopher", 253.5, 123.0, 90.5, "NPC");
		rift("Fairylosopher", 250.5, 123.0, 88.5, "NPC");
		rift("Fairylosopher", 256.5, 123.0, 87.5, "NPC");
		rift("Fairylosopher", 256.5, 123.0, 84.5, "NPC");
		rift("The 7th Sin", 48.0, 169.0, 40.0, "NPC");
		rift("Madame Eleanor Q. Goldsworth X", 23.5, 154.1, 43.0, "NPC");
		rift("Madame Eleanor Q. Goldsworth VII", 8.0, 179.0, 38.0, "NPC");
		rift("Argofay Doorholder", -5.5, 180.1, 24.5, "NPC");
		rift("Madame Eleanor Q. Goldsworth II", -223.5, 17.0, -75.5, "NPC");
		rift("Obo", -228.5, 21.0, -62.5, "NPC");
		rift("Madame Eleanor Q. Goldsworth V", 5.5, 150.0, 22.5, "NPC");
		rift("Montgomery", -17.0, 135.0, 42.5, "NPC");
		rift("Virginia", -16.5, 135.0, 43.5, "NPC");
		rift("Hope", -15.5, 135.0, 44.0, "NPC");
		rift("Ubik Von Neumann", -19.0, 135.0, 41.0, "NPC");
		rift("Ubik Von Neumann", 21.0, 107.0, 54.0, "NPC");
		rift("Ubik Von Neumann", 37.5, 132.0, 17.0, "NPC");
		rift("Old Timer", 16.5, 97.0, 18.5, "NPC");
		rift("Oruo The Almighty", 38.5, 89.0, 18.5, "NPC");
		rift("Chipper McPickaxe", 17.0, 99.8, 27.1, "NPC");
		rift("Madame Eleanor Q. Goldsworth I", -0.5, 114.0, 35.5, "NPC");
		rift("Madame Eleanor Q. Goldsworth IX", 37.5, 129.0, 41.5, "NPC");


		hub("Alixer", -2.5, 79.0, 4.5, "NPC");
		hub("Britette", -17.5, 47.0, -118.5, "NPC");
		hub("Enchantment Nerd", -71.5, 72.0, -80.5, "NPC");
		hub("Feast Baker Scott", 77.5, 73.0, -82.5, "NPC");
		hub("Feast Chef Ted", 83.5, 73.0, -78.5, "NPC");
		hub("Hootie", 11.5, 74.0, -64.5, "NPC");
		hub("Hoppity", 56.5, 71.0, -2.5, "NPC");
		hub("Lukas the Aquarist", 17.5, 72.0, -8.5, "NPC");
		hub("Marco", 59.5, 23.0, 69.5, "NPC");
		hub("Piggy", 14.5, 72.0, -8.5, "NPC");
		hub("Security Clerk", -22.5, 46.0, -110.5, "NPC");
		hub("Simon", -11.5, 77.0, 2.5, "NPC");
		hub("Susan", -1.5, 80.0, 25.5, "NPC");
		hub("Taylor", 36.5, 75.0, -40.5, "NPC");
		hub("Torrhus Canyon", -21.5, 72.0, -22.5, "NPC");
		park("Molbert", -467.5, 121.0, -42.5, "NPC");
		spider("Rick", -263.5, 72.0, -323.5, "NPC");
		end("Gregory", -607.5, 23.0, -282.5, "NPC");
		end("Lone Adventurer", -523.5, 102.0, -273.5, "NPC");
		deep("Lift Operator", 45.5, 122.0, 15.5, "NPC");
		dwarven("Dirt Guy", 42.5, 109.0, 175.5, "NPC");
		dwarven("Don Expresso", 39.5, 203.0, -123.5, "NPC");
		dwarven("Dulin", 81.5, 188.0, 127.5, "NPC");
		dwarven("Lift Operator (Deep Caverns)", -78.5, 201.0, -123.5, "NPC");
		dwarven("Royal Resident", 62.5, 205.0, 199.5, "NPC");
		dwarven("Ticket Master (Lower Royal Mines)", 84.5, 115.0, 134.5, "NPC");
		ch("Chunk", 797.5, 129.0, 707.5, "NPC");
		barn("Hunter Ava", 319.5, 103.0, -469.5, "NPC");
		barn("Husbandry Merchant", 117.5, 74.0, -255.5, "NPC");
		barn("Jake", 203.5, 15.0, -496.5, "NPC");
		crimson("Aranya", -321.5, 153.0, -1009.5, "NPC");
		crimson("Arch", -360.5, 190.0, -551.5, "NPC");
		crimson("Baar", -637.5, 124.0, -790.5, "NPC");
		crimson("Chak", -602.5, 108.0, -867.5, "NPC");
		crimson("Chihai", -604.5, 108.0, -860.5, "NPC");
		crimson("Chuk", -605.5, 108.0, -869.5, "NPC");
		crimson("Corm", -668.5, 127.0, -925.5, "NPC");
		crimson("Cyndarin", -204.5, 113.0, -955.5, "NPC");
		crimson("Dean", -15.5, 124.0, -882.5, "NPC");
		crimson("Desperate Engineer", -288.5, 128.0, -982.5, "NPC");
		crimson("Edelis", -92.5, 105.0, -820.5, "NPC");
		crimson("Edelis", -61.5, 108.0, -811.5, "NPC");
		crimson("Igor", -550.5, 99.0, -706.5, "NPC");
		crimson("Keran", -611.5, 109.0, -668.5, "NPC");
		crimson("Kuudra Believer", -391.5, 82.0, -701.5, "NPC");
		crimson("Pam", -597.5, 110.0, -665.5, "NPC");
		crimson("Porc", -576.5, 119.0, -855.5, "NPC");
		crimson("Prie", -611.5, 109.0, -661.5, "NPC");
		crimson("Strux", -595.5, 124.0, -845.5, "NPC");
		crimson("Truu", -612.5, 123.0, -765.5, "NPC");
		crimson("Turd", -616.5, 116.0, -709.5, "NPC");
		crimson("Undercover Agent (Barbarian)", -15.5, 94.0, -843.5, "NPC");
		crimson("Undercover Agent (Mage)", -625.5, 120.0, -959.5, "NPC");
		crimson("Yoink", -612.5, 124.0, -787.5, "NPC");
		crimson("Ziri", -81.5, 115.0, -791.5, "NPC");
		rift("Argofay Trailblazer", -97.5, 67.0, 147.5, "NPC");
		rift("Autonull", -222.5, 18.0, 4.5, "NPC");
		rift("Blobbercyst", -222.5, 18.0, -11.5, "NPC");
		rift("Chester", -130.5, 74.0, 172.5, "NPC");
		rift("Cryptosis", 29.5, 72.0, -76.5, "NPC");
		rift("Deer", 138.5, 71.0, -12.5, "NPC");
		rift("Detective Amog", 25.5, 178.0, 42.5, "NPC");
		rift("Dr Phear", -147.5, 37.0, 27.5, "NPC");
		rift("Fairylosopher at the Top", 255.5, 131.0, 76.5, "NPC");
		rift("Ikrus", 9.5, 158.0, 60.5, "NPC");
		rift("Jerry", -86.5, 21.0, 3.5, "NPC");
		rift("Joliet", -30.5, 65.0, -42.5, "NPC");
		rift("Master Tactician Fink", -189.5, 72.0, -56.5, "NPC");
		rift("Noway", -223.5, 18.0, 42.5, "NPC");
		rift("Phaser", 59.5, 71.0, -89.5, "NPC");
		rift("Roosterzilla", -223.5, 18.0, 97.5, "NPC");
		rift("Shen Chaos", -259.5, 19.0, -67.5, "NPC");
		rift("Spike", -222.5, 18.0, -49.5, "NPC");
		rift("Unbound Explorer", -28.5, 72.0, 314.5, "NPC");
		galatea("Brian", -628.5, 118.0, 47.5, "NPC");
		galatea("Soul Campfire Adept", -625.5, 118.0, 50.5, "NPC");
		galatea("Soul Campfire Initiate", -624.5, 118.0, 47.5, "NPC");
		galatea("Tilus", -642.5, 56.0, 47.5, "NPC");
		bayou("Carly", 38.5, 72.0, 7.5, "NPC");
		bayou("Jonesy", -9.5, 75.0, -10.5, "NPC");
		bayou("Researcher Gilbert", 27.5, 60.0, -26.5, "NPC");
		bayou("Researcher Gilbert", -19.5, 76.0, -8.5, "NPC");
		dwarven("Fossil Muncher", -26.5, 126.0, 300.5, "NPC");
		dwarven("Researcher Beryl", 35.5, 129.0, 236.5, "NPC");
		dwarven("Sor'Hen", 0.5, 122.0, 225.5, "NPC");
		jerry("Banker Barry", 20.5, 78.0, 45.5, "NPC");
		jerry("Daan", 81.5, 80.0, 110.5, "NPC");
		jerry("Dirk", 79.5, 82.0, 55.5, "NPC");
		jerry("Einary", -15.5, 77.0, 63.5, "NPC");
		jerry("Frosty", -1.5, 77.0, 93.5, "NPC");
		jerry("Frozen Alex", -42.5, 85.0, -0.5, "NPC");
		jerry("Gary", 54.5, 104.0, 56.5, "NPC");
		jerry("George", -72.5, 79.0, 28.5, "NPC");
		jerry("Gulliver", 68.5, 106.0, 34.5, "NPC");
		jerry("Helena", 37.5, 80.0, 75.5, "NPC");
		jerry("Maaike", 49.5, 82.0, 50.5, "NPC");
		jerry("Sherry", 5.5, 77.0, 94.5, "NPC");
		jerry("St. Jerry", -21.5, 77.0, 92.5, "NPC");
		jerry("Terry", -91.5, 79.0, 25.5, "NPC");
		torrhus("Andrea", -711.5, 109.0, 178.5, "NPC");
		torrhus("Ant", -615.5, 76.0, 208.5, "NPC");
		torrhus("Archie", -706.5, 109.0, 165.5, "NPC");
		torrhus("Beekeeper Bartholomew", -666.5, 96.0, 166.5, "NPC");
		torrhus("Dory", -645.5, 162.0, 194.5, "NPC");
		torrhus("Forager Daniel", -639.5, 96.0, 246.5, "NPC");
		torrhus("Forager Rowan", -725.5, 138.0, 228.5, "NPC");
		torrhus("Freddy", -694.5, 95.0, 200.5, "NPC");
		torrhus("Helia", -690.5, 94.0, 155.5, "NPC");
		torrhus("Honey", -627.5, 100.0, 226.5, "NPC");
		torrhus("Hunter Jean", -665.5, 146.0, 170.5, "NPC");
		torrhus("Hunter McNeil", -652.5, 104.0, 167.5, "NPC");
		torrhus("Maxim", -574.5, 154.0, 248.5, "NPC");
		torrhus("Miria", -654.5, 99.0, 220.5, "NPC");
		torrhus("Oen", -566.5, 81.0, 270.5, "NPC");
		torrhus("Safari Manager", -732.5, 100.0, 185.5, "NPC");
		torrhus("Safari Receptionist", -719.5, 100.0, 179.5, "NPC");
		torrhus("Safari Shill", -696.5, 95.0, 177.5, "NPC");
		torrhus("Sanger", -526.5, 98.0, 218.5, "NPC");
		torrhus("Sanger", -746.5, 94.0, 261.5, "NPC");
		torrhus("Sanger", -632.5, 95.0, 160.5, "NPC");
		torrhus("Treehugger Timothy", -541.5, 111.0, 276.5, "NPC");
		lotus("Captain Baha", -26.5, 74.0, 2.5, "NPC");
		lotus("Researcher Betty", -12.5, 72.0, 3.5, "NPC");
		lotus("Researcher Gilbert", -23.5, 74.0, 4.5, "NPC");
		lotus("Researcher Jessica", 75.5, 65.0, -11.5, "NPC");
		lotus("Researcher Kay", 22.5, 84.0, -50.5, "NPC");
		lotus("Researcher Lily", 26.5, 67.0, 14.5, "NPC");
		lotus("Researcher Ribery", -12.5, 72.0, -6.5, "NPC");
		lotus("Researcher Tay", 26.5, 142.0, -61.5, "NPC");
		lotus("Researcher Timmy", 10.5, 76.0, 30.5, "NPC");
		lotus("Researcher Tina", 42.5, 71.0, 29.5, "NPC");
		lotus("The Lotus Eater", 99.5, 69.0, -6.5, "NPC");
		safari("Archie", -36.5, 66.0, 2.5, "NPC");
		safari("Birdwatcher Tim", 7.5, 89.0, 46.5, "NPC");
		safari("Hunter Dennis", -68.5, 67.0, -33.5, "NPC");
		safari("Hunter Tobias", -49.5, 67.0, 2.5, "NPC");
		safari("Safari Manager", -51.5, 69.0, 21.5, "NPC");

		Set<String> seen = new LinkedHashSet<>();
		NPCS.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		for (NpcData npc : NPCS) {
			String key = npc.name() + "|" + npc.island();
			if (seen.add(key)) {
				UNIQUE_NPCS.add(npc);
			}
		}
		UNIQUE_NPCS.sort((a, b) -> {
			int ia = ISLAND_ORDER.getOrDefault(a.island(), 99);
			int ib = ISLAND_ORDER.getOrDefault(b.island(), 99);
			if (ia != ib) return Integer.compare(ia, ib);
			return a.name().compareToIgnoreCase(b.name());
		});
	}

	private NpcDatabase() {}

	/** All known NPCs sorted alphabetically */
	public static List<NpcData> getAll() {
		return Collections.unmodifiableList(NPCS);
	}

	/** Deduplicated NPCs (one entry per name+island) */
	public static List<NpcData> getUnique() {
		return Collections.unmodifiableList(UNIQUE_NPCS);
	}

	/** Filters unique NPCs by name, island, or description */
	public static List<NpcData> searchUnique(String query) {
		String lower = query.toLowerCase();
		List<NpcData> results = new ArrayList<>();
		for (NpcData npc : UNIQUE_NPCS) {
			if (npc.name().toLowerCase().contains(lower)
				|| npc.island().toLowerCase().contains(lower)
				|| npc.description().toLowerCase().contains(lower)) {
				results.add(npc);
			}
		}
		return results;
	}

	/** All positions for a given NPC name on a given island */
	public static List<NpcData> getPositions(String name, String island) {
		List<NpcData> results = new ArrayList<>();
		for (NpcData npc : NPCS) {
			if (npc.name().equals(name) && npc.island().equals(island)) {
				results.add(npc);
			}
		}
		return results;
	}

	private static void hub(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Hub", "hub", x, y, z, desc));
	}

	private static void barn(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "The Farming Islands", "barn", x, y, z, desc));
	}

	private static void gold(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Gold Mine", "gold", x, y, z, desc));
	}

	private static void deep(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Deep Caverns", "deep", x, y, z, desc));
	}

	private static void spider(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Spider's Den", "spider", x, y, z, desc));
	}

	private static void park(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "The Park", "park", x, y, z, desc));
	}

	private static void end(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "The End", "end", x, y, z, desc));
	}

	private static void crimson(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Crimson Isle", "crimson", x, y, z, desc));
	}

	private static void dwarven(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Dwarven Mines", "mines", x, y, z, desc));
	}

	private static void ch(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Crystal Hollows", "nucleus", x, y, z, desc));
	}

	private static void dungeonHub(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Dungeon Hub", "dh", x, y, z, desc));
	}

	private static void jerry(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Jerry's Workshop", "winter", x, y, z, desc));
	}

	private static void rift(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "The Rift", "rift", x, y, z, desc));
	}

	private static void garden(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Garden", "garden", x, y, z, desc));
	}

	private static void glacite(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Glacite Tunnels", "mines", x, y, z, desc));
	}

	private static void galatea(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Galatea", "galatea", x, y, z, desc));
	}

	private static void bayou(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Backwater Bayou", "bayou", x, y, z, desc));
	}

	private static void torrhus(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Torrhus Canyon", "torrhus", x, y, z, desc));
	}

	private static void lotus(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Lotus Atoll", "lotus", x, y, z, desc));
	}

	private static void safari(String name, double x, double y, double z, String desc) {
		NPCS.add(new NpcData(name, "Critter Safari", "safari", x, y, z, desc));
	}
}
