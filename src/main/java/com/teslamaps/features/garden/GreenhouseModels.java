package com.teslamaps.features.garden;

// Head textures from Skyblocker's HeadTextures / GreenhouseCrops, model draw after GreenhousePaste.renderPreview
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.GreenhousePlanner;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.skull.SkullModelBase;
import com.teslamaps.render.MultiBufferSource;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Missing greenhouse plants as translucent, tinted player-head models instead of coloured boxes. */
public final class GreenhouseModels {

    private static final Map<String, String> H = new HashMap<>();
    static {
        H.put("ASHWREATH", "5890f50780fdecedaa85aa40bf3399e9439ee68594c6d022688165608171681d");
        H.put("CHOCONUT", "2a8d74b77a0e510d058c544c7292a8844e70b9293880caffc562ce5ab5a49ad8");
        H.put("DUSTGRAIN", "8698331f183a586ae7258d6b3c83ccd3620bb2411d803123bd6706444c1efdf3");
        H.put("GLOOMGOURD", "7f693e42ba3b763292e7de26fd2b0a08fcee3bec2e017075dc66dfc4a932aa64");
        H.put("LONELILY", "f4672d5fb128c63197da27f40d7143818d318665b3ab2335121e9ae8f9ecc2e3");
        H.put("SCOURROOT", "a9da3b8dcffbb5dd9708b83e54746fced475f0ee16c6c0ce4668cca7999c4d1e");
        H.put("SHADEVINE", "c3c6d9dcb8fbd73de6171a2c2155314d097a9c99d09c9fce9cba068d7e5aedf7");
        H.put("VEILSHROOM", "266754af4859ef6f0adb03e6c58e9e348a507debce6b5a7f660d1269401de674");
        H.put("WITHERBLOOM", "ef831051cf18a4c3ea7a2a83311f218f43b032712799cc81910ab67ee7397b32");
        H.put("CHOCOBERRY", "167bb9880a3ab37435a21b1f135a01a96cca45b49daeb4a1e91baf358e37d89d");
        H.put("CINDERSHADE", "a0646bc0558155207204711cf5d3d07920e0e98c9b2be0b6107becb409a97427");
        H.put("COALROOT", "f946443fa0039354edd31a70c749c4f963464744dc20b79137bd9910356ee90");
        H.put("CREAMBLOOM", "60e98c0b598e2f5ce7c5ee8183ed157c5436a00585da711f8a87f24ea1ff055b");
        H.put("DUSKBLOOM", "7dca7951b36f5f749e883758b379c8008ca55f245987e4ef0c3788cf0c903d5");
        H.put("THORNSHADE", "dcc9a4a7aadb373adc3be05242924c8985e2f993dd8e4d96f20721052ff7e7a8");
        H.put("BLASTBERRY", "bacae0e87ffeadb750260c2e6531004d69d14473376cb22577fafe70569349f3");
        H.put("CHEESEBITE", "411f19c783959807338e2bf7080b1e34abb8c452464c0dce5bdf434cdc250717");
        H.put("CHLORONITE", "98056b960ff385c20cffc3d1524500fcd3bf8c31b6dcafd8520f41dfa749dd28");
        H.put("DONOTEATSHROOM", "3f22178e2e72d6929a6ef9199795a93cfbad999bbee0aba235b277d0b18e0e94");
        H.put("FLESHTRAP", "c7f45f6cb2e4bbf45c5537c4dc3055a323021d62db7d91cc60beb02956401fb9");
        H.put("MAGICJELLYBEAN", "c526a56b80f56a6870f891d1d46fa7f8c71494cad24e94326da84b3829417b81");
        H.put("NOCTILUME", "b1b18493d50ff8972f7ef359893d9063fdc54cb822c679002957c294fc8b0005");
        H.put("SNOOZLING", "24c64afa58bef69ff567b012a2b1638cf475c5bdb050d382308399ffa0b06a8d");
        H.put("SOGGYBUD", "b4bdf477d2f417f75798ad6377b131aca787be9bc05a2fddc1972d81d40c7356");
        H.put("CHORUSFRUIT", "e044238503102f64f30f4d58f0843b215d8dd54fc9f1f70cc17d8dd891bb3e5e");
        H.put("PLANTBOYADVANCE", "765accb195aad0d7212eedd647e3f80ed5d4acdffe4329ead074f7587f366457");
        H.put("PUFFERCLOUD", "9a488340c3d9131b6e97bae6279aac852037367cb93a6b50c60a7d328aec173e");
        H.put("SHELLFRUIT", "72d802cd207f1971a2eb826f1a7477740833c920db00cd5c992176c67672dbf5");
        H.put("STARTLEVINE", "62e94fc2b6024db86b82266fedef39696af591b27830973199fe9097acc340a0");
        H.put("STOPLIGHTPETAL", "4c2b797e7172a05169e313739908515864d6b372f9a5ecc772f81d9c4e402a54");
        H.put("THUNDERLING", "63650fc953438755b13b6d0b72e77e43d183cf8d911f8fe12ca4d66168308d46");
        H.put("TURTLELLINI", "1d1bd06a6738d0da5053eae49a1362b89489d1ac004c222504536f7bcd07679d");
        H.put("ZOMBUD", "b2c4994b7a1c45231b623b8245c117382b267c8856c57cffa2d808c241027a51");
        H.put("ALLINALOE", "241163612258d30dc6ef63b21f61ba89c622e5dcebd99fd36a3b507e80cdc725");
        H.put("DEVOURER", "4100d3b81c8dd0af22af3b42c97045bd844438d2f0297b0f267a46bd35ffb33f");
        H.put("GLASSCORN", "297de27338b9f876e570d1cc01fe1beccfc940467c5c97c467e93e79c81c25ee");
        H.put("GODSEED", "9d6bdd07abe3f398720e5f010648c2c194a2ba0f5ef3e27013b0670336eb4f3c");
        H.put("JERRYFLOWER", "d6a223c5610763ed43cecf187ff1cb061eb1049eca0596ab424379f9106890fb");
        H.put("PHANTOMLEAF", "e1c206ab05cbac4a22f54f8778c3bfbd870f19c380176f288614c77d19b5d122");
        H.put("TIMESTALK", "1b9add43e0e770b7c4ccdcf6708f8b9e875ff13b2a75ab63ff69f2f0e57af9e5");
        H.put("COCOABEANS", "44d72eed58354ce14bfc497138a13564070fb4653898aeb3e66c73082ae1f993");
        H.put("COCOABEAN", "44d72eed58354ce14bfc497138a13564070fb4653898aeb3e66c73082ae1f993");
        H.put("WILDROSE", "f341905af17c74a1c6181a56c88d8f91853f2cff0a9a33aaa16c0d835fdceece");
        H.put("MOONFLOWER", "10ba39f5a3bdb0f3ed6547e6e688fc43d64fabc056f3418b2bbbdfedd7248ba9");
        H.put("SUNFLOWER", "b40d6fc1e1b67c58d7f82350bcac083f9e9547f9131236463164417fbdd3bee4");
        H.put("MELON", "afa92dd43afed9e640cf3d3b008ca5199634ec8512de5e1f5eeaecd761296cb9");
        H.put("PUMPKIN", "18bd4aa55673e90a3c611117277d94f6ce185b5d13d2a862a3376f50a6139c4f");
        H.put("CACTUS", "d4b3ea5cb6b6f046e326621ca11ffb7d6aec22d66c0d81e5039b19ee4400309f");
        H.put("HELIANTHUS", "a98ac9224491a9dd480531bd971591724ce29921ec8f141461276f1bf3a59ed3");
        H.put("FERMENTO", "cb41daeb57d2ae62c66e58eb6debb2a7d446e34541a771350728c9db15beafba");
        H.put("SQUASH", "36ae076649ef22f60e8511831c68fd2b6ea63c32164dab33a8aebc18ff2a54c8");
        H.put("CROPIE", "dd01cba23ede2cd2895107f0c0258e971d2485538fe9649ef2853bd26e6232dc");
        H.put("REDMUSHROOM", "162f0e8d32f3db02ffdf1a16efb21d614a3f0417d49138fdc18bfe52ead705b9");
        H.put("BROWNMUSHROOM", "7019992b5d440f85d2b05148aa9b85f450985d5f16ae960d1cdb32e06e3c896f");
    }

    private static final Map<String, Supplier<PlayerSkinRenderCache.RenderInfo>> skins = new HashMap<>();
    private static final SkullModelBase.State STATE = new SkullModelBase.State();
    private static final List<RenderType> used = new ArrayList<>();
    private static SkullModelBase model;

    private GreenhouseModels() {}

    public static boolean has(String plant) {
        return H.containsKey(plant);
    }

    public static void register() {
        Feature.of("GreenhouseModels").section("Garden")
                .when(() -> TeslaMapsConfig.get().greenhouseModels && TeslaMapsConfig.get().greenhousePlanner)
                .worldCtx(GreenhouseModels::render);
    }

    private static Supplier<PlayerSkinRenderCache.RenderInfo> skin(String plant) {
        return skins.computeIfAbsent(plant, p -> {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + H.get(p) + "\"}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return Minecraft.getInstance().playerSkinRenderCache().createLookup(SkyblockItems.profileFor(value));
        });
    }

    private static void render(FeatureRegistry.WorldCtx w) {
        if (!GreenhousePlanner.modelsVisible() || GreenhousePlanner.modelCells.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = SkullBlockRenderer.createModel(mc.getEntityModels(), SkullBlock.Types.PLAYER);
        if (model == null) return;
        model.setupAnim(STATE);
        int alpha = (int) (Math.max(0.3f, Math.min(1f, TeslaMapsConfig.get().greenhouseAlpha + 0.25f)) * 255);
        int color = (alpha << 24) | 0xB0FFB0;
        PoseStack pose = w.pose();
        Vec3 cam = w.cam();
        MultiBufferSource buffers = w.buffers();
        used.clear();
        for (GreenhousePlanner.ModelCell c : GreenhousePlanner.modelCells) {
            PlayerSkinRenderCache.RenderInfo info = skin(c.plant()).get();
            if (info == null) continue;
            RenderType type = RenderTypes.entityTranslucent(info.playerSkin().body().texturePath());
            if (!used.contains(type)) used.add(type);
            pose.pushPose();
            pose.translate(c.x() - cam.x, c.y() - cam.y, c.z() - cam.z);
            pose.mulPose(SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(0));
            model.renderToBuffer(pose, buffers.getBuffer(type), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            pose.popPose();
        }
        if (buffers instanceof MultiBufferSource.BufferSource bs) for (RenderType t : used) bs.endBatch(t);
    }
}
