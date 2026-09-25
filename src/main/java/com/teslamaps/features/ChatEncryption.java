package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from NiaMod's ChatEncryptionFeature, ASCII-safe and with a fixed TeslaMaps key
public class ChatEncryption {

    public static final String MARK = "tmE:";
    private static final Pattern TOKEN = Pattern.compile("tmE:([A-Za-z0-9_-]{38,})");
    private static final int NONCE = 12, TAG_BITS = 128, MAX_LEN = 256;
    private static final Set<String> COMMANDS = Set.of("msg", "w", "tell", "message", "r", "pc", "gc", "oc", "cc", "ac");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static SecretKeySpec key;

    private ChatEncryption() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(m -> !typed() || fits(m, 0));
        ClientSendMessageEvents.MODIFY_CHAT.register(m -> typed() ? encryptAll(m) : m);
        ClientSendMessageEvents.ALLOW_COMMAND.register(m -> {
            if (!typed()) return true;
            int at = bodyStart(m);
            return at < 0 || fits(m.substring(at), at);
        });
        ClientSendMessageEvents.MODIFY_COMMAND.register(m -> {
            if (!typed()) return m;
            int at = bodyStart(m);
            return at < 0 ? m : m.substring(0, at) + encryptAll(m.substring(at));
        });
        Feature.of("ChatEncryption");
    }

    public static void set(boolean on) {
        TeslaMapsConfig.get().chatEncrypt = on;
        TeslaMapsConfig.save();
    }

    /** Only what the player typed in the chat box; automated sends stay readable. */
    private static boolean typed() {
        return TeslaMapsConfig.get().chatEncrypt && Minecraft.getInstance().screen instanceof ChatScreen;
    }

    private static int bodyStart(String cmd) {
        String[] w = cmd.split(" ", 3);
        String c = w[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(c)) return -1;
        boolean named = c.equals("msg") || c.equals("w") || c.equals("tell") || c.equals("message");
        if (named) return w.length < 3 ? -1 : w[0].length() + w[1].length() + 2;
        return w.length < 2 ? -1 : w[0].length() + 1;
    }

    private static boolean fits(String body, int prefix) {
        if (body.isBlank() || body.startsWith(MARK)) return true;
        int bytes = NONCE + TAG_BITS / 8 + body.trim().getBytes(StandardCharsets.UTF_8).length;
        int len = prefix + MARK.length() + (bytes * 4 + 2) / 3;
        if (len <= MAX_LEN) return true;
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§c[TeslaMaps] Too long to send encrypted ("
                + len + "/" + MAX_LEN + "). Shorten it or /tmap encrypt off."));
        return false;
    }

    private static String encryptAll(String body) {
        if (body.isBlank() || body.startsWith(MARK)) return body;
        try {
            byte[] iv = new byte[NONCE];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(body.trim().getBytes(StandardCharsets.UTF_8));
            byte[] all = Arrays.copyOf(iv, NONCE + enc.length);
            System.arraycopy(enc, 0, all, NONCE, enc.length);
            return MARK + B64.encodeToString(all);
        } catch (Exception e) {
            return body;
        }
    }

    private static String decrypt(String b64) {
        try {
            byte[] all = B64D.decode(b64);
            if (all.length < NONCE + TAG_BITS / 8) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, NONCE));
            return new String(cipher.doFinal(all, NONCE, all.length - NONCE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKeySpec key() throws Exception {
        if (key == null) {
            byte[] k = MessageDigest.getInstance("SHA-256").digest("TeslaMaps encrypted chat v1".getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(k, "AES");
        }
        return key;
    }

    /** Incoming line with the tokens decrypted, or null when there is none. Always on. */
    public static Component decode(Component message) {
        if (!message.getString().contains(MARK)) return null;
        MutableComponent out = Component.empty();
        boolean[] hit = {false};
        message.visit((style, text) -> {
            Matcher m = TOKEN.matcher(text);
            int last = 0;
            while (m.find()) {
                String plain = decrypt(m.group(1));
                if (plain == null) continue;
                hit[0] = true;
                if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
                Style s = style.withUnderlined(true)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Encrypted with TeslaMaps")));
                out.append(Component.literal(plain.replace('§', '&')).setStyle(s));
                last = m.end();
            }
            if (last < text.length()) out.append(Component.literal(text.substring(last)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return hit[0] ? out : null;
    }
}
