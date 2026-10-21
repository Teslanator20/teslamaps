// Ported from ADM's p3sim/clear/secrets/Loot.kt
package com.teslamaps.adm.sim.clear.secrets;

public interface Drop {
    final class Item implements Drop {
        public final LootItem item;
        public final int count;

        public Item(LootItem item, int count) { this.item = item; this.count = count; }

        public Item(LootItem item) { this(item, 1); }
    }

    final class Essence implements Drop {
        public final String type;
        public final int amount;

        public Essence(String type, int amount) { this.type = type; this.amount = amount; }

        public Essence(String type) { this(type, 10); }
    }

    final class Blessing implements Drop {
        public final com.teslamaps.adm.sim.clear.secrets.Blessing blessing;

        public Blessing(com.teslamaps.adm.sim.clear.secrets.Blessing blessing) { this.blessing = blessing; }
    }

    /** Coins, a junk item or an empty chest. */
    final class Nothing implements Drop {
        public static final Nothing INSTANCE = new Nothing();

        private Nothing() {}

        @Override public String toString() { return "Nothing"; }
    }
}
