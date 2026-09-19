package com.teslamaps.features.mobvisuals;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skull texture id to mob. Several mobs share a hitbox shape and are only told apart by the
 * skull Hypixel puts on them - Garden pests, the Tiki family, Titanoboa segments and so on.
 * A mob with a head and a body skull has both listed. Ported from GanKura.
 */
final class MobSkins {

    static final Map<String, Mob> SKINS = build();

    private MobSkins() {}

    private static Map<String, Mob> build() {
        Map<String, Mob> map = new LinkedHashMap<>();
        map.put("645f2c0bbfe3b8b19b7452072db69a5f59da38ff61415545156e5701e1be756d", Mob.TITANOBOA);
        map.put("b82086882b25e9e914362f2048c285c18c8d698a336f7e83f0a1964c760b11", Mob.TITANOBOA);
        map.put("b2b6074d0c9d6b89a494cf4f74158282a64ee23ba8a0725633ad70932ada1a8f", Mob.BLUE_RINGED_OCTOPUS);
        map.put("3fcb203b029aac4958407daf4072a01a353105162373ce7e559c669d78325bcb", Mob.GIANT_ISOPOD);
        map.put("f3c802e580bfefc18c4af94cceb82968b5b4aeab0d832346a633a7473a41dfac", Mob.WIKI_TIKI);
        map.put("e64331c8fb750f9043334320c94580e7896955695156d80689e5d0a6c60a10e7", Mob.WIKI_TIKI);
        map.put("9122f7a19b3197766b381fb36bfeb6f442d62509e44cc7847c75c8e8c387225a", Mob.WIKI_TIKI);
        map.put("c5fd6b9a59ec5b97db8bdc158fbd5f91ef7b317b859fcebe6d09e7bd80eaca9d", Mob.WIKI_TIKI);
        map.put("55b194025806687642e2bc239895d646a6d8c193d9253b61bfce908f6ce1b84a", Mob.FIERY_SCUTTLER);
        map.put("6d6bcd3bea0dff1f45d808e7a8550f95106af41b6d8d18a0793e19c9255ae845", Mob.WATER_HYDRA);
        map.put("f62bef8ca58d83766c7e26f9fb4fe06c11b93542a9c10f36f460a3b427748223", Mob.SHRIEKY_TIKI);
        map.put("7a267c2699e38062ea4baedbfa4bb80a631048f7c8d2ee4c460f40036b084b2b", Mob.SHRIEKY_TIKI);
        map.put("419773998a0b85ffebde5ff925ea4c9b9e007436b0eb9a43afd2d3b367d241f6", Mob.SHRIEKY_TIKI);
        map.put("28bdbd87b64e049562c986bb4c5b1336d92e68431260a9e0e8f3338d302b9761", Mob.SNEAKY_TIKI);
        map.put("b89f1cb02d4acd71940d9e6c29fe75ce3b15d291d9e383030b59e068b5f5a135", Mob.SNEAKY_TIKI);
        map.put("3eac8820104cc421e0392142dc7c79eb84bd2be8f8155c29d72f5fc58d4ee953", Mob.SNEAKY_TIKI);
        map.put("bb19e5b72167db517ff8b9e2dd0735996641bdb6b6267a758ad2864022023cb7", Mob.CHEEKY_TIKI);
        map.put("dfe6925f85cf02d2f8b501291d26aa5f4f6a136de0e974c2e9e3c20633ae9202", Mob.CHEEKY_TIKI);
        map.put("247da91d9d4dd57bad2678baf648e4d9dd4b9f454a606962ceaac11ed00dea9f", Mob.CHEEKY_TIKI);
        map.put("88a51208885548fbbe08271148db3691f59e275a8d723be094b44d34ccfd9c85", Mob.ANT);
        map.put("321fcf90d9090de8fd720cb2817a4dfdc2283e828fd44f8bfdc7ff9a8505cc", Mob.QUEEN_ANT);
        map.put("1b06d15155c60d0f0f267be05c8436c5a1e764a8c8aca236bb37223f49d9a1d0", Mob.WATER_SNAKE);
        map.put("23a0d55ad300e4f8870e754fb06db58c7f15fc292026edc79ea7e4ef3cc808cb", Mob.WATER_SNAKE);
        map.put("407b3c3d2c3fe259d69207a14ca5cd99713c7096ba122bb40326f3489e5d0d6c", Mob.GAZER);
        map.put("8b329e108ac28b0bec8d47b7cdce253df1db80b46052b5915d963e1bcbab0db4", Mob.GIMMIEGOLD);
        map.put("9924c105aa431dabd47952dc1dddd6f751f883423f4db1487d9bacc2cfe99c7a", Mob.MANTIS_SHRIMP);
        map.put("53de4135a3b19a2187029c86a0020e58c907c7bdd4e37b7643f120e16a0aa9ab", Mob.TROODON);
        map.put("f4c4f8e5fce1ec2d299cb8a395792ecddc497a1d8af86faaa5e20373016c7225", Mob.DRIFTLING);
        map.put("b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7", Mob.SHYWORM);
        map.put("a684e00e7394cb0c84c082e4dbc7c7e91ea67f6bc718a1aace7f99596b65d422", Mob.SHYWORM);
        map.put("fc63cd0d480971a7beae5fd503e5d51658cd906330843cbad92018f5b98b4fe5", Mob.CHUCKWALLA);
        map.put("a89a76deedd42b410344100df2fa79b6eeac7e6f287745d656179368340ffade", Mob.FLITTER);
        map.put("9d90e777826a52461368e26d1b2e19bfa1ba582d602483e545f4124d0f731842", Mob.FLY);
        map.put("f379e09252817314bd0b694f7d53b48af2c7fa8499109802a41bb294d2f93e3e", Mob.FIELD_MOUSE);
        map.put("a8abb471db0ab78703011979dc8b40798a941f3a4dec3ec61cbeec2af8cffe8", Mob.RAT);
        map.put("be6baf6431a9daa2ca604d5a3c26e9a761d5952f0817174a4fe0b764616e21ff", Mob.MITE);
        map.put("52a9fe05bc663efcd12e56a3ccc5ec035bf577b78708548b6f4ffcf1d30eccfe", Mob.MOSQUITO);
        map.put("6403ba4027a333d8d2fd32ab59d1cfdbaa7d908d80d2381db2a69cbe65450ad8", Mob.EARTHWORM);
        map.put("fd40aa50905235b628e7379eb31fa45cd41b503f09721db3c437fcee39207dfc", Mob.EARTHWORM);
        map.put("a24c69f96ce556221e195c8ef2bfad71ebf7f95f5ae914a484a8d0ec21672674", Mob.CRICKET);
        map.put("4b24a482a32db1ea78fb98060b0c2fa4a373cbd18a68edddeb7419455a59cda9", Mob.LOCUST);
        map.put("65485c4b34e5b5470be94de100e61f7816f81bc5a11dfdf0eccf890172da5d0a", Mob.MOTH);
        map.put("bee4fcc5aac27bdbb0be210df08b05bca5aebc89bf2821f608a55fd2cf0434be", Mob.LUNAR_MOTH);
        map.put("7a79d0fd677b54530961117ef84adc206e2cc5045c1344d61d776bf8ac2fe1ba", Mob.SLUG);
        map.put("70a1e836bf1968b2eaa4837227a19204f17295d870ee9e754bd6b6d60ddbed3c", Mob.BEETLE);
        map.put("4ce79e90adf34718f313ec24d6c6135b69b3788c618498446ccc83ca640c0b14", Mob.FIREFLY);
        map.put("254aff4c0b2dce3a672349cc0ee9e6f3a9deebe4b3556e84611eca250a7821bf", Mob.DRAGONFLY);
        map.put("1e04bb6367caa4e88f5fd0ee80f0745d137a6060223dbbc42a16471fdf64bb83", Mob.PRAYING_MANTIS);
        // Stag Beetle and Woodlouse share one skull; without a nametag read they cannot be
        // split, so the shared skin maps to neither and both stay nametag-driven.
        return map;
    }
}
