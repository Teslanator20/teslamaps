package com.teslamaps.features;

// Ported from SkyHanni's features/chat/RareDropMessages.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rarity in front of pet names in drop lines, and the real name behind "RARE DROP! Enchanted Book". */
public final class RareDropNames {

    private static final Pattern[] PET = {
            Pattern.compile("(?<start>(?:§.)*PET DROP! )(?:§.)*§(?<color>.)(?<pet>[^§(.]+)(?<end>(?: .*)?)"),
            Pattern.compile("(?<start>(?:§.)*⛃ (?:§.)*(?:GOOD|GREAT|OUTSTANDING) CATCH! (?:§.)*You caught a (?:§.)*\\[Lvl 1] )(?:§.)*§(?<color>.)(?<pet>[^§(.]+)(?<end>.*)"),
            Pattern.compile("(?<start>(?:§.)*You claimed a )(?:§.)*§(?<color>.)(?<pet>[^§(.]+)(?<end>.*You can manage your Pets.*)"),
            Pattern.compile("(?<start>.*has obtained (?:§.)*\\[Lvl 1] )(?:§.)*§(?<color>.)(?<pet>[^§(.]+)(?<end>.*)"),
            Pattern.compile("(?<start>(?:§.)*\\[NPC] Oringo(?:§.)*: (?:§.)*✆ (?:§.)*• )(?:§.)*§(?<color>.)(?<pet>[^§(.]+)(?<end> Pet.*)"),
    };
    private static final Pattern BOOK = Pattern.compile("^RARE DROP! Enchanted Book(?: \\(\\+\\d+%? ✯ Magic Find\\))?.*");
    private static final String[] IGNORED_AREAS = {"Dark Auction", "Dungeon Hub", "Catacombs", "Kuudra"};

    private static long bookLineAt;
    private static String bookName;
    private static long bookNameAt;
    private static int lastSig = Integer.MIN_VALUE;
    private static Map<String, Integer> books = new HashMap<>();

    private RareDropNames() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RareDropBookName").section("Chat").when(() -> cfg().rareDropBookName)
                .tick(RareDropNames::tick)
                .serverChat(RareDropNames::onChat);
    }

    private static String rarity(char color) {
        return switch (color) {
            case 'f' -> "COMMON";
            case 'a' -> "UNCOMMON";
            case '9' -> "RARE";
            case '5' -> "EPIC";
            case '6' -> "LEGENDARY";
            case 'd' -> "MYTHIC";
            case 'b' -> "DIVINE";
            case 'c' -> "SPECIAL";
            default -> null;
        };
    }

    /** Rewritten pet line, or null. {@code t} is the stripped text. */
    static Component petRarity(Component message, String t) {
        if (!cfg().rareDropPetRarity) return null;
        if (!(t.contains("PET DROP!") || t.contains("CATCH!") || t.contains("You claimed a ")
                || t.contains("[Lvl 1]") || t.contains("Oringo"))) return null;
        if (!SkyblockUtils.isInSkyblock()) return null;
        String legacy = ChatText.formatted(message);
        for (Pattern p : PET) {
            Matcher m = p.matcher(legacy);
            if (!m.matches()) continue;
            String color = m.group("color");
            String rarity = rarity(Character.toLowerCase(color.charAt(0)));
            if (rarity == null) return null;
            String start = m.group("start");
            if (start.endsWith("a ") && (rarity.charAt(0) == 'U' || rarity.charAt(0) == 'E')) {
                start = start.substring(0, start.length() - 1) + "n ";
            }
            return keepEvents(message, start + "§" + color + "§l" + rarity + " §" + color + m.group("pet") + m.group("end"));
        }
        return null;
    }

    private static Component keepEvents(Component original, String legacy) {
        HoverEvent[] hover = {null};
        ClickEvent[] click = {null};
        original.visit((style, text) -> {
            if (hover[0] == null) hover[0] = style.getHoverEvent();
            if (click[0] == null) click[0] = style.getClickEvent();
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        HoverEvent h = hover[0];
        ClickEvent k = click[0];
        return Component.literal(legacy).withStyle(s -> s.withHoverEvent(h).withClickEvent(k));
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw).trim();
        if (!BOOK.matcher(t).matches()) return;
        String area = SkyblockUtils.getArea();
        for (String a : IGNORED_AREAS) if (area.contains(a)) return;
        bookLineAt = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) return;
        Inventory inv = mc.player.getInventory();
        int sig = 17;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.ENCHANTED_BOOK)) sig = sig * 31 + i * 97 + st.getCount();
        }
        if (sig != lastSig) scan(inv, sig);
        tryApply();
    }

    private static void scan(Inventory inv, int sig) {
        boolean first = lastSig == Integer.MIN_VALUE;
        lastSig = sig;
        Map<String, Integer> now = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(Items.ENCHANTED_BOOK) || st.getCount() != 1) continue;
            List<String> lore = ItemUtil.loreLines(st);
            if (lore.isEmpty()) continue;
            String name = lore.get(0).trim();
            if (!name.isEmpty()) now.merge(name, 1, Integer::sum);
        }
        if (!first) {
            for (Map.Entry<String, Integer> e : now.entrySet()) {
                if (e.getValue() > books.getOrDefault(e.getKey(), 0)) {
                    bookName = e.getKey();
                    bookNameAt = System.currentTimeMillis();
                }
            }
        }
        books = now;
    }

    private static void tryApply() {
        long now = System.currentTimeMillis();
        if (bookName == null || now - bookLineAt > 2000 || now - bookNameAt > 2000) return;
        String name = bookName;
        ChatComponentAccessor acc = (ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        for (int i = 0; i < Math.min(all.size(), 20); i++) {
            GuiMessage g = all.get(i);
            String plain = StringUtil.strip(g.content().getString()).trim();
            if (plain.contains(name) || !BOOK.matcher(plain).matches()) continue;
            String legacy = ChatText.formatted(g.content()).replace("Enchanted Book", name);
            all.set(i, new GuiMessage(g.addedTime(), keepEvents(g.content(), legacy), g.signature(), g.source(), g.tag()));
            bookName = null;
            bookLineAt = 0;
            acc.teslamaps$refreshTrimmedMessages();
            return;
        }
    }
}
