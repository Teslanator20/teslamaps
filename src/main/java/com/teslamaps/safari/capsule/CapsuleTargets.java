package com.teslamaps.safari.capsule;

// Ported from Sparkling Mutuals' esp/SafariEspRules (identify), esp/EspBodyBounds,
// contacts/ContactTargetProxies, MoundContactSurface, ShywormContactParts, ContactTargetPriority
import com.mojang.authlib.properties.Property;
import com.mojang.math.Transformation;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Target;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class CapsuleTargets {

    public static final String BODY_TEXTURE = "a684e00e7394cb0c84c082e4dbc7c7e91ea67f6bc718a1aace7f99596b65d422";
    public static final String MOUND_PROXY = "mound_surface_v1";

    private record Rule(String species, String type, String texture, String shulker, String fish, String variant) {}

    private record Desc(String type, String texture, String shulker, String fish, String variant, boolean invisible, boolean passengers) {}

    private record Label(String species, Entity entity) {}

    private record Mound(double x, double y, double z, ContactSurfaceBox surface) {}

    // mounds sit still, the model box is only read once per entity
    private static final Map<Integer, Mound> mounds = new HashMap<>();

    public static void clearMounds() { mounds.clear(); }

    private static final List<Rule> RULES = List.of(
            new Rule("Cavernfish", "tropical_fish", null, null, "CLAYFISH/GRAY/BROWN", null),
            new Rule("Flitter", "item_display", "a89a76deedd42b410344100df2fa79b6eeac7e6f287745d656179368340ffade", null, null, null),
            new Rule("Shyworm", "armor_stand", "b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7", null, null, null),
            new Rule("Driftling", "armor_stand", "f4c4f8e5fce1ec2d299cb8a395792ecddc497a1d8af86faaa5e20373016c7225", null, null, null),
            new Rule("Chuckwalla", "item_display", "fc63cd0d480971a7beae5fd503e5d51658cd906330843cbad92018f5b98b4fe5", null, null, null),
            new Rule("Rockmite", "silverfish", null, null, null, null),
            new Rule("Rockmite", "item_display", "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800", null, null, null),
            new Rule("Scrappy", "armadillo", null, null, null, null),
            new Rule("Snoozle", "sniffer", null, null, null, null),
            new Rule("Gemzie", "vex", null, null, null, null),
            new Rule("Foxtrot", "fox", null, null, null, null),
            new Rule("Bluebird", "parrot", null, null, null, "BLUE"),
            new Rule("Honeybug", "bee", null, null, null, null),
            new Rule("Treefrog", "frog", null, null, null, null),
            new Rule("Woodchucker", "creaking", null, null, null, null),
            new Rule("Fluffling", "panda", null, null, null, null),
            new Rule("Hideonfloor", "shulker", null, "GREEN", null, null),
            new Rule("Hideonfloor", "block_display", null, "GREEN", null, null),
            new Rule("Hideonfloor", "item_display", null, "GREEN", null, null),
            new Rule("Parakeet", "parrot", null, null, null, "GREEN"),
            new Rule("Macaw", "parrot", null, null, null, "RED_BLUE"),
            new Rule("Areita", "cave_spider", null, null, null, null),
            new Rule("Bloodbat", "bat", null, null, null, null),
            new Rule("Duplico", "interaction", null, null, null, null),
            new Rule("Gazer", "armor_stand", "407b3c3d2c3fe259d69207a14ca5cd99713c7096ba122bb40326f3489e5d0d6c", null, null, null),
            new Rule("Litterbug", "endermite", null, null, null, null),
            new Rule("Solsnatcher", "phantom", null, null, null, null),
            new Rule("Gimmiegold", "item_display", "8b329e108ac28b0bec8d47b7cdce253df1db80b46052b5915d963e1bcbab0db4", null, null, null),
            new Rule("Hideonwall", "shulker", null, "PURPLE", null, null),
            new Rule("Hideonwall", "block_display", null, "PURPLE", null, null),
            new Rule("Hideonwall", "item_display", null, "PURPLE", null, null),
            new Rule("Hideyho", "player", "3504f1f2327a5110e643bb8667082512815fa434a29ed37f4ca83bb16d2db533", null, null, null),
            new Rule("Doomspiral", "warden", null, null, null, null),
            new Rule("Strongarm", "snow_golem", null, null, null, null),
            new Rule("Tepid", "tropical_fish", null, null, "SNOOPER/WHITE/WHITE", null),
            new Rule("Polaris", "polar_bear", null, null, null, null),
            new Rule("Shuddersquid", "glow_squid", null, null, null, null),
            new Rule("Billygoat", "goat", null, null, null, null),
            new Rule("Mantis Shrimp", "item_display", "9924c105aa431dabd47952dc1dddd6f751f883423f4db1487d9bacc2cfe99c7a", null, null, null),
            new Rule("Nozzlenose", "dolphin", null, null, null, null),
            new Rule("Troodon", "item_display", "53de4135a3b19a2187029c86a0020e58c907c7bdd4e37b7643f120e16a0aa9ab", null, null, null),
            new Rule("Wumpa", "ravager", null, null, null, null));

    private static final Map<String, List<Rule>> BY_TYPE = new HashMap<>();
    private static final Set<String> SHULKER_SPECIES = new HashSet<>();

    static {
        for (Rule r : RULES) {
            BY_TYPE.computeIfAbsent(r.type(), k -> new ArrayList<>()).add(r);
            if (r.shulker() != null) SHULKER_SPECIES.add(r.species());
        }
    }

    private static final Pattern TEXTURE = Pattern.compile("textures\\.minecraft\\.net/texture/([a-fA-F0-9]{64})(?![a-fA-F0-9])");
    private static final Map<String, String> textureCache = new LinkedHashMap<>(256, 0.75f, true);

    private CapsuleTargets() {}

    /** Critter bodies, mound surfaces and supported shyworm segments within 64 blocks. */
    public static List<Target> collect(Minecraft mc) {
        List<Target> discovered = new ArrayList<>();
        if (mc.level == null || mc.player == null) return discovered;
        List<Label> labels = labels();
        for (Entity e : EntityCache.all()) {
            if (e.isRemoved() || e instanceof Player || mc.player.distanceToSqr(e) > 4096.0) continue;
            Target t = inspect(mc, e, labels);
            if (t != null) discovered.add(t);
        }
        Map<String, List<Target>> support = shywormSupport(discovered);
        List<Target> out = new ArrayList<>(discovered.size());
        for (Target t : discovered) if (!t.bodyPart() || support.containsKey(t.key())) out.add(t);
        return out;
    }

    private static Target inspect(Minecraft mc, Entity e, List<Label> labels) {
        Desc desc = describe(e);
        String key = String.valueOf(e.getId());
        if (e instanceof Display.ItemDisplay id && "Rockmite".equals(identify(desc))) {
            Mound cached = mounds.get(e.getId());
            ContactSurfaceBox surface;
            if (cached != null && cached.x() == e.getX() && cached.y() == e.getY() && cached.z() == e.getZ()) {
                surface = cached.surface();
            } else {
                surface = moundSurface(mc, id);
                if (mounds.size() > 256) mounds.clear();
                if (surface != null) mounds.put(e.getId(), new Mound(e.getX(), e.getY(), e.getZ(), surface));
            }
            if (surface == null) return null;
            return new Target(key, "Rockmite", surface.bounds(), MOUND_PROXY, false, surface);
        }
        boolean part = e instanceof ArmorStand && BODY_TEXTURE.equals(desc.texture());
        String species = part ? "Shyworm" : identify(desc);
        if (species == null) return null;
        if (part) {
            ArmorStand as = (ArmorStand) e;
            if (!as.isMarker() || as.isSmall() || posed(as.getHeadPose()) || posed(as.getBodyPose())) return null;
        } else if (!current(e, species, labels)) {
            return null;
        }
        String kind = e instanceof Display ? "display" : e instanceof ArmorStand ? "armor_stand" : "native";
        boolean shulker = !part && SHULKER_SPECIES.contains(species);
        ShotVector pos = new ShotVector(e.getX(), e.getY(), e.getZ());
        ShotBounds bounds;
        String proxy;
        ShotBounds visual = visual(pos, kind, shulker);
        if (visual != null) {
            bounds = visual;
            proxy = kind.equals("display") ? "display_body_v1" : "armor_stand_body_v1";
        } else {
            bounds = ShotBounds.of(e.getBoundingBox());
            if (!bounds.valid()) return null;
            proxy = "client_body_v1";
        }
        return new Target(key, species, bounds, proxy, part, null);
    }

    private static boolean posed(Rotations r) {
        return r.x() != 0f || r.y() != 0f || r.z() != 0f;
    }

    private static ShotBounds visual(ShotVector p, String kind, boolean shulker) {
        if (kind.equals("display")) {
            return shulker
                    ? new ShotBounds(new ShotVector(p.x() - 0.5, p.y(), p.z() - 0.5), new ShotVector(p.x() + 0.5, p.y() + 1, p.z() + 0.5))
                    : new ShotBounds(new ShotVector(p.x() - 0.3, p.y() - 0.45, p.z() - 0.3), new ShotVector(p.x() + 0.3, p.y() + 0.15, p.z() + 0.3));
        }
        if (kind.equals("armor_stand")) {
            return new ShotBounds(new ShotVector(p.x() - 0.3, p.y() + 1.35, p.z() - 0.3), new ShotVector(p.x() + 0.3, p.y() + 1.95, p.z() + 0.3));
        }
        return null;
    }

    // a live critter has its own species label within 3 blocks
    private static boolean current(Entity e, String species, List<Label> labels) {
        if (species.equals("Shyworm") && !(e.getY() > 2.0)) return false;
        boolean needVisible = species.equals("Driftling") || species.equals("Chuckwalla");
        for (Label l : labels) {
            if (!l.species().equals(species) || l.entity().distanceToSqr(e) > 9.0) continue;
            if (needVisible && !l.entity().isCustomNameVisible()) continue;
            return true;
        }
        return false;
    }

    private static List<Label> labels() {
        List<Label> out = new ArrayList<>();
        for (Entity e : EntityCache.all()) {
            if (e.getCustomName() == null) continue;
            String name = StringUtil.strip(e.getCustomName().getString());
            String species = speciesIn(name);
            if (species != null) out.add(new Label(species, e));
        }
        return out;
    }

    private static String speciesIn(String name) {
        for (Rule r : RULES) if (name.contains(r.species())) return r.species();
        return null;
    }

    private static String identify(Desc d) {
        if (d.type().endsWith("display") && d.invisible()) return null;
        if (d.type().equals("silverfish") && (d.invisible() || d.passengers())) return null;
        List<Rule> rules = BY_TYPE.get(d.type());
        if (rules == null) return null;
        for (Rule r : rules) {
            if ((r.texture() == null || r.texture().equals(d.texture()))
                    && (r.shulker() == null || r.shulker().equals(d.shulker()))
                    && (r.fish() == null || r.fish().equals(d.fish()))
                    && (r.variant() == null || r.variant().equals(d.variant()))) {
                return r.species();
            }
        }
        return null;
    }

    private static Desc describe(Entity e) {
        DyeColor color = null;
        ItemStack item = e instanceof Display.ItemDisplay id ? itemOf(id) : ItemStack.EMPTY;
        if (e instanceof Shulker s) {
            color = s.getColor();
        } else if (e instanceof Display.BlockDisplay bd) {
            Display.BlockDisplay.BlockRenderState st = bd.blockRenderState();
            if (st != null && st.blockState().getBlock() instanceof ShulkerBoxBlock sb) color = sb.getColor();
        } else if (e instanceof Display.ItemDisplay) {
            if (Block.byItem(item.getItem()) instanceof ShulkerBoxBlock sb) color = sb.getColor();
        }
        String texture = null;
        if (e instanceof ArmorStand as) texture = texture(as.getItemBySlot(EquipmentSlot.HEAD));
        else if (e instanceof Display.ItemDisplay) texture = texture(item);
        String fish = e instanceof TropicalFish tf
                ? tf.getPattern().name() + "/" + tf.getBaseColor().name() + "/" + tf.getPatternColor().name() : null;
        String variant = e instanceof Parrot p && p.getVariant() != null ? p.getVariant().name() : null;
        return new Desc(e.getType().toShortString(), texture, color != null ? color.name() : null, fish, variant,
                e.isInvisible() || !modelVisible(e), !e.getPassengers().isEmpty());
    }

    private static boolean modelVisible(Entity e) {
        if (!(e instanceof Display d)) return true;
        Display.RenderState rs = d.renderState();
        if (rs == null || rs.transformation() == null) return false;
        Transformation t = rs.transformation().get(1.0f);
        if (t == null) return false;
        Vector3fc s = t.scale();
        float max = Math.max(Math.abs(s.x()), Math.max(Math.abs(s.y()), Math.abs(s.z())));
        return d.shouldRenderAtSqrDistance(0.0) && Float.isFinite(max) && max > 0.001f;
    }

    private static ItemStack itemOf(Display.ItemDisplay id) {
        SlotAccess slot = id.getSlot(0);
        return slot == null ? ItemStack.EMPTY : slot.get();
    }

    private static String texture(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return null;
        for (Property p : profile.partialProfile().properties().get("textures")) {
            if (p != null) return textureHash(p.value());
        }
        return null;
    }

    private static String textureHash(String encoded) {
        if (encoded == null) return null;
        if (textureCache.containsKey(encoded)) return textureCache.get(encoded);
        String hash;
        try {
            Matcher m = TEXTURE.matcher(new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8));
            hash = m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
        } catch (IllegalArgumentException ex) {
            hash = null;
        }
        textureCache.put(encoded, hash);
        if (textureCache.size() > 256) {
            var it = textureCache.entrySet().iterator();
            it.next();
            it.remove();
        }
        return hash;
    }

    // oriented box of the mound's item model
    private static ContactSurfaceBox moundSurface(Minecraft mc, Display.ItemDisplay e) {
        if (e.isInvisible()) return null;
        Display.RenderState rs = e.renderState();
        Display.ItemDisplay.ItemRenderState irs = e.itemRenderState();
        if (rs == null || irs == null || rs.billboardConstraints() != Display.BillboardConstraints.FIXED) return null;
        Transformation transform = rs.transformation().get(1.0f);
        ItemStackRenderState item = new ItemStackRenderState();
        mc.getItemModelResolver().updateForNonLiving(item, irs.itemStack(), irs.itemTransform(), e);
        if (item.isEmpty()) return null;
        AABB b = item.getModelBoundingBox();
        if (!Double.isFinite(b.minX) || !Double.isFinite(b.minY) || !Double.isFinite(b.minZ)
                || !Double.isFinite(b.maxX) || !Double.isFinite(b.maxY) || !Double.isFinite(b.maxZ)
                || b.getXsize() <= 0 || b.getYsize() <= 0 || b.getZsize() <= 0) return null;
        Matrix4f m = new Matrix4f()
                .rotationYXZ(-e.getYRot() * (float) (Math.PI / 180.0), e.getXRot() * (float) (Math.PI / 180.0), 0.0f)
                .mul(transform.getMatrix())
                .rotateY((float) Math.PI);
        ShotVector o = moundPoint(m, e, b.minX, b.minY, b.minZ);
        ContactSurfaceBox box = new ContactSurfaceBox(o,
                moundPoint(m, e, b.maxX, b.minY, b.minZ).minus(o),
                moundPoint(m, e, b.minX, b.maxY, b.minZ).minus(o),
                moundPoint(m, e, b.minX, b.minY, b.maxZ).minus(o));
        if (!box.valid()) return null;
        ShotBounds bb = box.bounds();
        double size = Math.max(bb.max().x() - bb.min().x(), Math.max(bb.max().y() - bb.min().y(), bb.max().z() - bb.min().z()));
        return size <= 8.0 ? box : null;
    }

    private static ShotVector moundPoint(Matrix4f m, Entity e, double x, double y, double z) {
        Vector3f p = m.transformPosition(new Vector3f((float) x, (float) y, (float) z));
        return new ShotVector(e.getX() + p.x, e.getY() + p.y, e.getZ() + p.z);
    }

    // a segment counts only when chained (<=1.75 apart, <=8 hops) to exactly one head
    private static Map<String, List<Target>> shywormSupport(List<Target> targets) {
        List<Target> worms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Target t : targets) {
            if (t.species().equals("Shyworm") && t.surface() == null && t.bounds().valid() && seen.add(t.key())) worms.add(t);
        }
        if (worms.size() > 64) return Map.of();
        Map<String, ShotVector> centers = new HashMap<>();
        for (Target t : worms) centers.put(t.key(), t.bounds().center());
        Map<String, List<Target>> neighbors = new HashMap<>();
        for (Target t : worms) {
            List<Target> near = new ArrayList<>();
            for (Target o : worms) {
                if (t != o && centers.get(t.key()).distanceSquared(centers.get(o.key())) <= 3.0625) near.add(o);
            }
            neighbors.put(t.key(), near);
        }
        Map<String, String> headOf = new HashMap<>();
        Set<String> visited = new HashSet<>();
        for (Target start : worms) {
            if (!visited.add(start.key())) continue;
            ArrayDeque<Target> queue = new ArrayDeque<>();
            queue.add(start);
            List<Target> group = new ArrayList<>();
            while (!queue.isEmpty()) {
                Target t = queue.removeFirst();
                group.add(t);
                for (Target n : neighbors.get(t.key())) if (visited.add(n.key())) queue.add(n);
            }
            Target head = null;
            int heads = 0;
            for (Target t : group) {
                if (!t.bodyPart()) {
                    head = t;
                    heads++;
                }
            }
            if (heads == 1) for (Target t : group) headOf.put(t.key(), head.key());
        }
        Map<String, List<Target>> result = new LinkedHashMap<>();
        for (Target part : worms) {
            if (!part.bodyPart()) continue;
            String head = headOf.get(part.key());
            if (head == null) continue;
            ArrayDeque<List<Target>> queue = new ArrayDeque<>();
            queue.add(List.of(part));
            Set<String> used = new HashSet<>();
            used.add(part.key());
            while (!queue.isEmpty()) {
                List<Target> path = queue.removeFirst();
                Target last = path.get(path.size() - 1);
                if (last.key().equals(head)) {
                    result.put(part.key(), path);
                    break;
                }
                if (path.size() < 8) {
                    for (Target n : neighbors.get(last.key())) {
                        if (used.add(n.key())) {
                            List<Target> next = new ArrayList<>(path);
                            next.add(n);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** distance of a target's centre to the sampled curve, for the 64-target cap */
    public static double priority(List<ShotVector> points, Target target) {
        if (points.size() < 2 || !target.bounds().valid()) return Double.POSITIVE_INFINITY;
        ShotVector c = target.bounds().center();
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < points.size(); i++) {
            ShotVector a = points.get(i), b = points.get(i + 1);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
            double len = dx * dx + dy * dy + dz * dz;
            double f = len > 0 ? Math.max(0, Math.min(1, ((c.x() - a.x()) * dx + (c.y() - a.y()) * dy + (c.z() - a.z()) * dz) / len)) : 0;
            best = Math.min(best, c.distanceSquared(a.lerp(b, f)));
        }
        return best;
    }
}
