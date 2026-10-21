package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/SimRestartScreen.kt

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class SimRestartScreen extends Screen {
    private enum Tab { PLAN, ADVANCED, SETTINGS, TELEPORT, PRACTICE, BOSS }

    private LinearLayout panel;

    public SimRestartScreen() {
        super(Component.literal(P3Sim.FULL_NAME));
        Tab found = null;
        for (Tab t : Tab.values()) if (t.name().equals(Practice.menuTab)) { found = t; break; }
        tab = found;
        practiceSec = Practice.menuSection;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int restartY = height / 2 - 10;
        int roleY = restartY - 4 * ROW;
        int skillY = roleY;
        int left = cx - RESTART_W / 2 - GAP - SIDE_W;
        int right = cx + RESTART_W / 2 + GAP;
        int roleX = right + SIDE_W + GAP;

        addRenderableWidget(Button.builder(Component.literal("§aRestart P3"), b -> {
            mc().setScreen(null);
            SimActions.send("start", kv("what", Fight.Start.P3.name()));
        }).tooltip(tip("Starts P3 over from its beginning, everything reset (Stop: ends it)." + (inParty() ? " For the whole party." : ""))).bounds(cx - RESTART_W / 2, restartY, RESTART_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§cStop"), b -> {
            mc().setScreen(null);
            SimActions.send("stop");
        }).tooltip(tip("Ends the run: the fight stops and the splits clear." + (inParty() ? " For the whole party." : ""))).bounds(cx - RESTART_W / 2, restartY - ROW, RESTART_W, 20).build());

        if (Ec.BETA) addRenderableWidget(Button.builder(Component.literal("§bFull Run"), b -> com.teslamaps.adm.sim.clear.ui.UiModule.openStartScreen())
                .tooltip(tip("Starts a full F7 run: countdown, the dungeon, the blood room, then the boss. Choose the layout, party and lag first. Rebuilds the world."))
                .bounds(cx - RESTART_W / 2, restartY - 2 * ROW, RESTART_W, 20).build());
        if (com.teslamaps.adm.sim.run.ClientRun.inClear()) {
            addRenderableWidget(Button.builder(Component.literal("§eRestart Room"), b -> { mc().setScreen(null); com.teslamaps.adm.sim.clear.ui.UiServer.send("restart", "room"); })
                    .tooltip(tip("Back to where you entered the room you stand in: the run's state, the clock and your gear as they were."))
                    .bounds(cx - RESTART_W / 2, restartY - 3 * ROW, RESTART_W, 20).build());
            addRenderableWidget(Button.builder(Component.literal("§eRestart Run"), b -> { mc().setScreen(null); com.teslamaps.adm.sim.clear.ui.UiServer.send("restart"); })
                    .tooltip(tip("Back to the start of this run (the same dungeon, no rebuild)."))
                    .bounds(cx - RESTART_W / 2, restartY - 4 * ROW, RESTART_W, 20).build());
        }

        party();

        tabButton(Tab.BOSS, "Boss", "The boss fight: where the run stops (after terms, after Goldor).", left, restartY - 4 * ROW);
        tabButton(Tab.SETTINGS, "Settings", "Every setting: death ticks, terminals, your speed, hotbar, gear, the bots, masks...", left, restartY - 3 * ROW);
        tabButton(Tab.TELEPORT, "Teleport", "Every place in the arena: section starts, devices, pads, phases, early-enter spots.", left, restartY - 2 * ROW);
        tabButton(Tab.PLAN, "Plan", "The section plan: each section's jobs and who does them (yours, a bot's), with Advanced under it.", left, restartY - ROW, Tab.ADVANCED);
        tabButton(Tab.PRACTICE, "Practice", "Practice: a section (your role's part of it), or your own custom one; timed.", left, restartY);

        title("§eSkill Level", right, SIDE_W, skillY);
        List<Triple<Integer, String, String>> skills = List.of(
                Triple.of(0, "Normal PF", "The bots play at a normal party finder pace: P3 in about 34 s."),
                Triple.of(1, "Quality PF", "The bots play at a quality party finder pace: P3 in about 28 s."),
                Triple.of(2, "Advanced PF", "The bots play at an advanced party finder pace: P3 in about 22 s."),
                Triple.of(P3Plan.NEAR_OPTIMAL, "Near Optimal", "Nearly the fastest terms: the archer pre-does S3's device, the tank holds the high EE3, the mage holds by the core for S4 and recores after its 4th."),
                Triple.of(P3Plan.THEORETICAL, "Theoretical", "The fastest terms, in theory: a far EE2, a high EE3, an EE4 by the core.")
        );
        for (int k = 0; k < skills.size(); k++) {
            Triple<Integer, String, String> s = skills.get(k);
            int i = s.first;
            String name = s.second;
            String about = s.third;
            addRenderableWidget(Button.builder(Component.literal(i == P3Plan.skill ? "§a§n" + name : name), b -> {
                P3Plan.chooseSkill(i);
                rebuildWidgets();
            }).tooltip(tip(about + " Your jobs become your class's role in it.")).bounds(right, skillY + k * ROW, SIDE_W, 20).build());
        }

        title("§eRole", roleX, CLASS_W, roleY);
        Map<DungeonClass, String> roles = P3Plan.preset().roles;
        List<DungeonClass> classes = Party.CLASSES();
        for (int idx = 0; idx < classes.size(); idx++) {
            int i = idx;
            DungeonClass c = classes.get(idx);
            PartyClient.Mate other = null;
            for (PartyClient.Mate m : PartyClient.team) if (m.player && m.clazz == c) { other = m; break; }
            String text;
            if (c == P3Sim.myClass()) text = "§a§n" + Roles.label(c);
            else if (other != null) text = "§b" + take(Roles.label(c), 4) + " §7" + take(other.name, 6);
            else text = Roles.label(c);
            String roleText = roles.get(c);
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                P3Sim.classS.setIndex(i);
                TeslaMapsConfig.save();
                rebuildWidgets();
            }).tooltip(tip(other != null ? other.name + " plays the " + Roles.label(c) + ". Click: you take it and they get yours (" + Roles.label(P3Sim.myClass()) + ")."
                    : "Play as " + Roles.label(c) + ": your jobs become its " + P3Plan.skillName() + " role (" + (roleText != null ? roleText : "none") + "); a bot plays each class no player plays."))
                    .bounds(roleX, roleY + i * ROW, CLASS_W, 20).build());
        }

        panel = LinearLayout.vertical().spacing(2);
        panel.defaultCellSetting().alignHorizontallyCenter();
        Tab t = tab;
        if (t == Tab.PLAN) planPanel();
        else if (t == Tab.ADVANCED) advancedPanel();
        else if (t == Tab.SETTINGS) settingsPanel();
        else if (t == Tab.TELEPORT) teleportPanel();
        else if (t == Tab.PRACTICE) practicePanel();
        else if (t != null && t == Tab.BOSS) bossPanel();
        panel.arrangeElements();
        int top = restartY + 20 + 10;
        panel.setPosition(cx - panel.getWidth() / 2, top);
        panel.visitWidgets(w -> addRenderableWidget(w));
        if (tab == Tab.PLAN || tab == Tab.ADVANCED) {
            Button adv = tabCell(Tab.ADVANCED, "Advanced", tab == Tab.ADVANCED ? "Back to the section plan." : "Spawn and early-enter spots, the leap menu, the bots' options.");
            adv.setPosition(cx - adv.getWidth() / 2, top + PANEL_H);
            addRenderableWidget(adv);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        Practice.saveMenu(tab != null ? tab.name() : null, practiceSec >= 1 && practiceSec <= 4 ? practiceSec : 1);
        TeslaMapsConfig.save();
    }

    // the top half

    private void tabButton(Tab t, String name, String about, int x, int y) { tabButton(t, name, about, x, y, null); }

    private void tabButton(Tab t, String name, String about, int x, int y, Tab also) {
        boolean open = tab == t || (also != null && tab == also);
        addRenderableWidget(Button.builder(Component.literal(open ? "§a" + name : name), b -> {
            tab = open ? null : t;
            rebuildWidgets();
        }).tooltip(tip(about + " Click again: closes it.")).bounds(x, y, SIDE_W, 20).build());
    }

    private void title(String t, int x, int w, int y) {
        Component c = Component.literal(t);
        StringWidget sw = new StringWidget(c, font);
        sw.setPosition(x + (w - font.width(c)) / 2, y - 12);
        addRenderableWidget(sw);
    }

    // the plan (default)

    private void planPanel() {
        List<LayoutElement> head = new ArrayList<>();
        head.add(label("", SEC_W));
        for (Pair<String, String> col : COLUMNS) head.add(label("§e" + col.first, JOB_W, col.second));
        row(head);
        for (int s = 1; s <= 4; s++) {
            String[] cells = new String[COLUMNS.size()];
            for (String job : P3Plan.jobsIn(s)) cells[column(job)] = job;
            List<LayoutElement> r = new ArrayList<>();
            r.add(label("§6§lS" + s, SEC_W, "Section " + s + "'s jobs."));
            for (String job : cells) r.add(job == null ? label("", JOB_W) : jobButton(job));
            row(r);
        }
    }

    private Button jobButton(String job) {
        boolean mine = P3Plan.isMine(job);
        DungeonClass doer = P3Plan.doer(job);
        boolean picked = !mine && doer != P3Plan.roleDoer(job);
        String star = P3Plan.isStack(job) ? "§e*" : "";
        PartyClient.Mate mate = null;
        if (!mine) for (PartyClient.Mate m : PartyClient.team) if (m.player && m.clazz == doer) { mate = m; break; }
        boolean joiner = PartyClient.joiner();
        String text;
        if (mine && P3Plan.isStacked(job)) text = "§aYou§e+";
        else if (mine) text = "§aYou" + star;
        else if (mate != null) text = "§d" + take(mate.name, 4) + star;
        else text = (picked ? "§b" : "§7") + (doer != null ? take(Roles.label(doer), 4) : "§8any") + star;
        List<DungeonClass> owners = P3Plan.plan().owners.get(job);
        if (owners == null) owners = List.of();
        String who;
        if (mine && P3Plan.isStacked(job)) {
            DungeonClass h = P3Plan.helperOf(job);
            who = "Yours, stacked: the " + (h != null ? Roles.label(h) : "?") + " bot does it too with Helpers " + onOff(P3Plan.helper) + "§r.";
        } else if (mine) who = "Yours.";
        else if (mate != null) who = mate.name + " plays the " + Roles.label(mate.clazz) + ": theirs, unless their own plan says otherwise.";
        else if (doer != null) {
            DungeonClass rd = P3Plan.roleDoer(job);
            who = "The " + Roles.label(doer) + " bot does it" + (picked ? " (you picked it; its role's: " + (rd != null ? Roles.label(rd) : "any") + ")" : "") + ".";
        } else who = "Whichever bot is least busy does it.";
        String shared = "";
        if (owners.size() > 1) {
            List<String> names = new ArrayList<>();
            for (DungeonClass o : owners) names.add(Roles.label(o));
            shared = " In the " + String.join(" and ", names) + " roles (a stack).";
        }
        String click = mine ? "Left click: the bot's. Right click: " + (P3Plan.isStacked(job) ? "not a stack" : "a stack (its bot does it too)") + "."
                : joiner ? "Left click: yours. (Which bot does a job is the host's pick.)"
                : "Left click: yours. Right click: the next bot.";
        return new ClickButton(JOB_W, text, tip(jobName(job) + ". " + who + shared + " " + click),
                () -> { P3Plan.toggle(job); rebuildWidgets(); },
                () -> {
                    if (P3Plan.isMine(job)) P3Plan.toggleStacked(job);
                    else if (!joiner) P3Plan.cycleDoer(job);
                    rebuildWidgets();
                });
    }

    // advanced

    private void advancedPanel() {
        String skill = P3Plan.skillName();
        String me = Roles.label(P3Sim.myClass());
        Spots.Spot spawn = P3Plan.customSpot("spawn");
        row(List.of(
                label("§eSpawn", LABEL_W, "Where Restart P3 puts you."),
                change(P3Plan.hasCustomSpot("spawn") ? "§aSet here" : "Set here", 60,
                        "Restart P3 puts you where you stand now, facing as you are (as " + me + " in " + skill + "). Now: " + (spawn != null ? at(spawn) : "your first S1 job's spot") + ".",
                        () -> { Spots.Spot h = here(); if (h != null) P3Plan.setCustomSpot("spawn", h); }),
                change("Default", 50, "Back to the default spawn (Normal PF's, as " + me + ").", () -> P3Plan.setCustomSpot("spawn", null))
        ));
        List<LayoutElement> ees = new ArrayList<>();
        ees.add(label("§eEarly enters", LABEL_W, "Where each early enter stands, whoever does it."));
        for (P3Plan.EarlyEnter ee : P3Plan.earlyEnters) {
            if (!(ee.into != 4 || ee.on())) continue;
            DungeonClass owner = ee.owner();
            String who = owner != null ? (ee.byYou() ? "you" : "the " + Roles.label(owner) + " bot") : "nobody in " + skill;
            ees.add(change(P3Plan.hasCustomSpot(ee.key) ? "§a" + ee.label : ee.label, 48,
                    ee.label + " (" + who + ") stands where you stand now, facing as you are (as " + me + " in " + skill + "). Now: " + at(P3Plan.eeSpot(ee)) + ".",
                    () -> { Spots.Spot h = here(); if (h != null) P3Plan.setCustomSpot(ee.key, h); }));
        }
        ees.add(change("Defaults", 56, "Every early enter back to its default spot (Normal PF's) for " + me + " in " + skill + ".", () -> {
            for (P3Plan.EarlyEnter e : P3Plan.earlyEnters) P3Plan.setCustomSpot(e.key, null);
        }));
        row(ees);

        List<DungeonClass> order = P3Plan.botOrder();
        LinearLayout grid = LinearLayout.vertical().spacing(2);
        for (int r = 0; r <= 1; r++) {
            LinearLayout line = LinearLayout.horizontal().spacing(2);
            for (int k = 0; k <= 1; k++) {
                int slot = r * 2 + k + 1;
                DungeonClass c = slot - 1 < order.size() ? order.get(slot - 1) : null;
                line.addChild(new ClickButton(LEAP_W, "§7" + slot + ": §f" + (c != null ? Roles.label(c) : "-"),
                        tip("Leap menu slot " + slot + ": " + (c != null ? Roles.label(c) : "nobody") + ". Left click: swap with the next slot's class; right click: with the one before. (Your own order: Leap Sort goes to Custom.)"),
                        () -> { P3Plan.cycleSlot(slot); P3Plan.save(); rebuildWidgets(); },
                        () -> { P3Plan.cycleSlot(slot, true); P3Plan.save(); rebuildWidgets(); }));
            }
            grid.addChild(line);
        }
        LinearLayout side = LinearLayout.vertical().spacing(2);
        side.addChild(change("Leap Sort: " + (P3Plan.odinSort ? "§aOdin" : "Custom"), 100,
                "The leap menu's order. Odin (green): as Odin's Leap Menu sorts it (by class). Custom: your own, set with the slots.", () -> { P3Plan.odinSort = !P3Plan.odinSort; P3Plan.save(); }));
        LinearLayout gap = LinearLayout.horizontal().spacing(2);
        gap.addChild(change("-", 16, "Bots leap 0.25 s closer together.", () -> { P3Plan.leapGap = Math.max(P3Plan.leapGap - 0.25, 0.05); P3Plan.save(); }));
        gap.addChild(label("§f" + String.format(Locale.ROOT, "%.2f", P3Plan.leapGap) + "s apart", 64, "How far apart the bots' leaps onto an early enterer come."));
        gap.addChild(change("+", 16, "Bots leap 0.25 s further apart.", () -> { P3Plan.leapGap = Math.min(P3Plan.leapGap + 0.25, 5.0); P3Plan.save(); }));
        side.addChild(gap);
        row(List.of(label("§eLeaps", LABEL_W, "The leap menu (2x2 as it opens in game) and the bots' leaps."), grid, side));

        row(List.of(
                toggle("Helpers", P3Plan.helper, 64, "On (green): the bots help with your stacks: a stacked job of yours (right click it in the plan) and the preset's stacks are done by their bot too, whoever gets there first.", () -> { P3Plan.helper = !P3Plan.helper; P3Plan.save(); }),
                toggle("Wait For You", P3Plan.waitForYou, 84, "On (green): a section's last bot job waits until you're at your early enter for the next section.", () -> { P3Plan.waitForYou = !P3Plan.waitForYou; P3Plan.save(); }),
                toggle("Bots", P3Sim.bots(), 44, "The plan does the other four's jobs at its times either way. On (green): bots show them (just the look: a late one is put on its job). Off: nobody's shown.", () -> P3Sim.botsS.set(!P3Sim.bots())),
                toggle("Real Movement", P3Sim.realMoves(), 90, P3Sim.realMovesS.desc, () -> P3Sim.toggleRealMoves()),
                change("Reset Role", 70, "Your jobs, the bots you picked and your stacks back to the " + Roles.label(P3Sim.myClass()) + "'s " + skill + " role.", () -> P3Plan.resetMine())
        ));

        List<LayoutElement> ghosts = new ArrayList<>();
        for (DungeonClass c : P3Plan.botOrder()) {
            GhostRun best = GhostStore.best(skill, c.name());
            String t = best != null ? String.format(Locale.ROOT, "%.2f", best.time / 20.0) : null;
            ghosts.add(toggle(Roles.label(c), P3Plan.ghostOn(c), 56, "On (green): the " + Roles.label(c) + " bot plays your fastest " + skill + " P3 as " + Roles.label(c) + " (S1 starts) instead of its role. " +
                    (t != null ? "Yours: " + t + "s." : "No run of yours yet: it plays its role."), () -> P3Plan.toggleGhost(c)));
        }
        List<LayoutElement> times = P3Plan.skill != P3Plan.RANDOM ? List.of() : List.of(
                label("§eBot times", 56, "Random skill: each bot job takes between these two."),
                change("-", 14, "Shorter.", () -> { P3Plan.botMin = Math.max(P3Plan.botMin - 0.5, 0.0); P3Plan.save(); }),
                label("§f" + String.format(Locale.ROOT, "%.1f", P3Plan.botMin) + "s", 30),
                change("+", 14, "Longer.", () -> { P3Plan.botMin = Math.min(P3Plan.botMin + 0.5, P3Plan.botMax); P3Plan.save(); }),
                change("-", 14, "Shorter.", () -> { P3Plan.botMax = Math.max(P3Plan.botMax - 0.5, P3Plan.botMin); P3Plan.save(); }),
                label("§f" + String.format(Locale.ROOT, "%.1f", P3Plan.botMax) + "s", 30),
                change("+", 14, "Longer.", () -> { P3Plan.botMax = Math.min(P3Plan.botMax + 0.5, 60.0); P3Plan.save(); })
        );
        List<LayoutElement> best = new ArrayList<>();
        best.add(label("§dYour best", LABEL_W, "A bot plays your fastest run as its class."));
        best.addAll(ghosts);
        best.addAll(times);
        row(best);
    }

    private Spots.Spot here() {
        LocalPlayer p = mc().player;
        if (p == null) return null;
        return new Spots.Spot("set", Math.round(p.getX() * 10) / 10.0, Math.floor(p.getY() * 100) / 100.0, Math.round(p.getZ() * 10) / 10.0, p.getYRot(), p.getXRot());
    }

    private String at(Spots.Spot p) { return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.x, p.y, p.z); }

    // settings

    private void settingsPanel() {
        Terminals.Type forced = P3Sim.forcedTerminal();
        if (forced != null) lastType = forced;
        LinearLayout speedRow = LinearLayout.horizontal().spacing(2);
        speedRow.addChild(change("-", 54, "10 less speed.", () -> setSpeed(P3Sim.speed() - 10)));
        speedRow.addChild(change("+", 54, "10 more speed.", () -> setSpeed(P3Sim.speed() + 10)));
        boolean p3Part = !PartyClient.phase.equals("P1") && !PartyClient.phase.equals("P2");
        String part = p3Part ? "P3" : "P1/P2";
        boolean saved = HotbarLayout.has(p3Part);
        String who = Roles.label(P3Sim.myClass());

        List<LayoutElement> deaths = new ArrayList<>();
        for (int idx = 0; idx < DEATH_TICKS.size(); idx++) {
            int i = idx;
            Pair<String, String> d = DEATH_TICKS.get(idx);
            deaths.add(change(pick(d.first, P3Sim.deathTicks() == i), 84, d.second, () -> P3Sim.deathTicksS.setIndex(i)));
        }
        deaths.add(toggle("Real Masks", P3Sim.realMasksS.get(), 84, P3Sim.realMasksS.desc, () -> P3Sim.realMasksS.set(!P3Sim.realMasksS.get())));
        deaths.add(change("Mask: " + (P3Sim.wornMaskS.index() == 0 ? "Spirit" : "Bonzo"), 84, P3Sim.wornMaskS.desc, () -> P3Sim.wornMaskS.setIndex(1 - P3Sim.wornMaskS.index())));
        deaths.add(change("Pet: " + (P3Sim.phoenixS.get() ? "Phoenix" : "Black Cat"), 84, P3Sim.phoenixS.desc, () -> P3Sim.phoenixS.set(!P3Sim.phoenixS.get())));

        List<LayoutElement> terms = new ArrayList<>();
        terms.add(change(pick("Random", forced == null), 66, "Each terminal a random type, as on Hypixel.", () -> P3Sim.terminalS.setIndex(0)));
        for (Terminals.Type t : Terminals.Type.values()) {
            Pair<String, String> n = TYPE_NAMES.get(t);
            terms.add(change(pick(n.first, forced == t), 66, n.second, () -> { lastType = t; P3Sim.terminalS.setIndex(t.ordinal() + 1); }));
        }

        row(List.of(
                stack("§eDeath Ticks", 84, P3Sim.deathTicksS.desc, deaths),
                stack("§eTerminals", 66, P3Sim.terminalS.desc, terms),
                stack("§eSpeed", 110, P3Sim.speedS.desc, List.of(
                        new SpeedSlider(110),
                        speedRow,
                        change("Ping: " + P3Sim.ping() + "ms", 110, P3Sim.pingS.desc, () -> P3Sim.pingS.set(PINGS.get(Math.floorMod(PINGS.indexOf(P3Sim.ping()) + 1, PINGS.size())))),
                        toggle("Jitter", P3Sim.jitterS.get(), 110, P3Sim.jitterS.desc, () -> P3Sim.jitterS.set(!P3Sim.jitterS.get())),
                        toggle("No Melodies", P3Sim.noMelodies(), 110, P3Sim.noMelodiesS.desc, () -> P3Sim.noMelodiesS.set(!P3Sim.noMelodies())),
                        toggle("Click Limit", P3Sim.clickLimitS.get(), 110, P3Sim.clickLimitS.desc, () -> P3Sim.clickLimitS.set(!P3Sim.clickLimitS.get())),
                        new LuckSlider(110),
                        new LuckSlider(110, "SS Luck", P3Sim.ssLuckS),
                        toggle("1st Click Melody", P3Sim.firstClickMelody(), 110, "Always First Click Melody: " + P3Sim.firstClickMelodyS.desc, () -> P3Sim.firstClickMelodyS.set(!P3Sim.firstClickMelody()))
                )),
                stack("§eTimings", 106, "The fight's numbers.", List.of(
                        stepper("Goldor kill", P3Sim.goldorKill() + "t", P3Sim.goldorKillS.desc, () -> P3Sim.goldorKillS.set(Math.max(P3Sim.goldorKill() - 1, 10)), () -> P3Sim.goldorKillS.set(Math.min(P3Sim.goldorKill() + 1, 120))),
                        stepper("Shortbow", P3Sim.shortbowCooldown() + "t", P3Sim.shortbowCooldownS.desc, () -> P3Sim.shortbowCooldownS.set(Math.max(P3Sim.shortbowCooldown() - 1, 1)), () -> P3Sim.shortbowCooldownS.set(Math.min(P3Sim.shortbowCooldown() + 1, 20))),
                        stepper("Hydra", "" + P3Sim.hydraStart(), P3Sim.hydraStartS.desc, () -> P3Sim.hydraStartS.set(Math.max(P3Sim.hydraStart() - 1, 0)), () -> P3Sim.hydraStartS.set(Math.min(P3Sim.hydraStart() + 1, 10))),
                        toggle("Mage On All", P3Sim.mageAll(), 106, "Mage Beam On All Classes: " + P3Sim.mageAllS.desc, () -> P3Sim.mageAllS.set(!P3Sim.mageAll())),
                        toggle("Infinite Charges", P3Sim.breakerInfinite(), 106, "On (green): the Dungeonbreaker never runs out of charges.", () -> P3Sim.toggleBreakerInfinite()),
                        toggle("Perma Break", P3Sim.breakerPerma(), 106, "On (green): blocks the Dungeonbreaker breaks stay broken (through restarts too) until you turn it off; then they all come back.", () -> P3Sim.toggleBreakerPerma())
                )),
                stack("§eFight", 92, "How the fight goes on.", List.of(
                        toggle("Lava Bounce", P3Sim.lava(), 92, P3Sim.lavaS.desc, () -> P3Sim.lavaS.set(!P3Sim.lava())),
                        toggle("Start On Join", P3Sim.autoStart(), 92, P3Sim.autoStartS.desc, () -> P3Sim.autoStartS.set(!P3Sim.autoStart())),
                        toggle("Terror At Terms", P3Sim.terrorAtTerms(), 92, P3Sim.terrorAtTermsS.desc, () -> P3Sim.toggleTerrorAtTerms()),
                        toggle("Black Cat Start", P3Sim.blackCatAtStart(), 92, "Black Cat At Start: " + P3Sim.blackCatS.desc, () -> P3Sim.toggleBlackCatAtStart()),
                        toggle("Section Times", P3Sim.showTimes(), 92, P3Sim.showTimesS.desc, () -> P3Sim.showTimesS.set(!P3Sim.showTimes())),
                        toggle("Hide Players", P3Sim.hidePlayers(), 92, "On (green): other players out of sight.", () -> P3Sim.toggleHidePlayers()),
                        toggle("Debug Bots", P3Sim.debugBots(), 92, P3Sim.debugBotsS.desc, () -> P3Sim.debugBotsS.set(!P3Sim.debugBots()))
                )),
                stack("§eOther", 100, "Your hotbar, recording, leaving.", List.of(
                        act(saved ? "§aSave " + part + " Hotbar" : "Save " + part + " Hotbar", 100, "Saves where your items are right now (hotbar and inventory), the slot you hold, what you wear and your pet, for the " + who + " (" + part + "), on every skill. " +
                                "Every start and restart lays it out (without one, your items stay as you left them).", () -> SimActions.send("hotbarSave", kv("p3", p3Part))),
                        withActive(act("Default Hotbar", 100, "Forgets your saved " + part + " hotbar for the " + who + " (every skill) and lays out the sim's default.", () -> SimActions.send("hotbarReset", kv("p3", p3Part))), saved),
                        act("Reset Items", 100, "Your " + part + " items, gear and pet back to your saved hotbar for the " + who + " now (or the default, forgetting how you left them).", () -> SimActions.send("resetItems", kv("p3", p3Part))),
                        toggle("Record", P3Sim.record(), 100, P3Sim.recordS.desc, () -> P3Sim.recordS.set(!P3Sim.record())),
                        act("§7Leave", 100, "Leaves the sim world.", () -> SimWorld.leave())
                ))
        ));
    }

    private static Button withActive(Button b, boolean active) {
        b.active = active;
        return b;
    }

    private LayoutElement stack(String title, int w, String about, List<? extends LayoutElement> items) {
        LinearLayout c = LinearLayout.vertical().spacing(2);
        c.defaultCellSetting().alignHorizontallyCenter();
        c.addChild(label(title, w, about));
        for (LayoutElement it : items) c.addChild(it);
        return c;
    }

    private LayoutElement stepper(String name, String value, String about, Runnable down, Runnable up) {
        LinearLayout r = LinearLayout.horizontal().spacing(1);
        r.addChild(change("-", 14, "Less.", down));
        r.addChild(label("§7" + name + " §f" + value, 76, about));
        r.addChild(change("+", 14, "More.", up));
        return r;
    }

    private void setSpeed(int v) {
        int s = Math.clamp(v, MIN_SPEED, MAX_SPEED);
        if (s == P3Sim.speed()) return;
        P3Sim.speedS.set(s);
    }

    private class SpeedSlider extends AbstractSliderButton {
        SpeedSlider(int w) {
            super(0, 0, w, 20, Component.empty(), (P3Sim.speed() - MIN_SPEED) / (double) (MAX_SPEED - MIN_SPEED));
            updateMessage();
            setTooltip(tip(P3Sim.speedS.desc));
        }

        private int speed() { return MIN_SPEED + (int) Math.round(value * (MAX_SPEED - MIN_SPEED) / 10) * 10; }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Speed: " + speed() + (P3Sim.phoenixS.get() ? " §8(Phoenix out)" : " §8(" + (speed() + 100) + " with Black Cat)")));
        }

        @Override
        protected void applyValue() { setSpeed(speed()); }
    }

    private class LuckSlider extends AbstractSliderButton {
        private final String name;
        private final NumSetting s;

        LuckSlider(int w) { this(w, "Luck", P3Sim.termLuckS); }

        LuckSlider(int w, String name) { this(w, name, P3Sim.termLuckS); }

        LuckSlider(int w, String name, NumSetting s) {
            super(0, 0, w, 20, Component.empty(), (s.get() / 100 + 1) / 2);
            this.name = name;
            this.s = s;
            updateMessage();
            setTooltip(tip(s.desc));
        }

        private int luck() { return (int) Math.round(value * 40) * 5 - 100; }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + (luck() < 0 ? "§c" : "") + luck() + "%"));
        }

        @Override
        protected void applyValue() {
            s.set(luck());
            TeslaMapsConfig.save();
        }
    }

    private class DelaySlider extends AbstractSliderButton {
        DelaySlider(int w) {
            super(0, 0, w, 20, Component.empty(), Practice.startDelay / 5.0);
            updateMessage();
            setTooltip(tip("Every practice start: you stand on its start this long before anything is up (terminals, levers, Goldor) and the clock runs. 0: at once."));
        }

        private double delay() { return Math.round(value * 50) / 10.0; }

        @Override
        protected void updateMessage() { setMessage(Component.literal("Timer: " + String.format(Locale.ROOT, "%.1f", delay()) + "s")); }

        @Override
        protected void applyValue() { Practice.setStartDelay(delay()); }
    }

    // teleport

    private void bossPanel() {
        row(List.of(stack("§eStop", 120, "Where the run stops on its own (as the menu's Stop: everything back as built).", List.of(
                toggle("Stop After Terms", P3Sim.termsOnly(), 120, P3Sim.termsOnlyS.desc, () -> P3Sim.toggleStopAfter(true)),
                toggle("Stop After Goldor", P3Sim.p3Only(), 120, P3Sim.p3OnlyS.desc, () -> P3Sim.toggleStopAfter(false))
        ))));
    }

    private static Pair<String, Spots.Spot> spot(String name, double x, double y, double z, float yaw, float pitch) {
        return Pair.of(name, new Spots.Spot(name, x, y, z, yaw, pitch));
    }

    private static Pair<String, Spots.Spot> of(String name, Spots.Spot s) { return Pair.of(name, s); }

    private static Pair<String, Spots.Spot> job(Map<String, Station> stations, String name, String id) {
        Vec3 p = Party.STANDS.get(id);
        if (p == null) return Pair.of(name, null);
        Pair<Float, Float> look = Spots.startLook(id);
        if (look.equals(Pair.of(180f, 0f))) {
            look = null;
            Station st = stations.get(id);
            if (st != null) {
                Vec3 aim = st.lever != null ? new Vec3(st.lever.getX() + 0.5, st.lever.getY() + 0.5, st.lever.getZ() + 0.5) : st.at.add(0.0, 1.0, 0.0);
                Vec3 d = aim.subtract(p.add(0.0, 1.62, 0.0));
                look = Pair.of((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) (-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))));
            }
            if (look == null) look = Pair.of(180f, 0f);
        }
        return Pair.of(name, new Spots.Spot(jobName(id), p.x, p.y, p.z, look.first, look.second));
    }

    private static Pair<String, Spots.Spot> ee(String name, String key) {
        for (P3Plan.EarlyEnter e : P3Plan.earlyEnters) if (e.key.equals(key)) return Pair.of(name, P3Plan.eeSpot(e));
        return Pair.of(name, null);
    }

    private void teleportPanel() {
        int w = 64;
        Map<String, Station> stations = new LinkedHashMap<>();
        for (Station st : Station.all()) stations.put(st.id(), st);
        List<Pair<String, List<Pair<String, Spots.Spot>>>> groups = List.of(
                Pair.of("P1", List.of(of("Start", Spots.P1), spot("Agro Spot", 69.53, 225.0, 65.12, -0.25f, 6.57f),
                        spot("LC Pad", 95.61, 224.0, 40.38, 45.78f, 13.31f), spot("RC Pad", 51.64, 224.0, 40.61, -45.13f, 18.90f))),
                Pair.of("P2", List.of(spot("Start", 77.5, 165.0, 40.5, -84.99f, 2.72f), spot("Yellow Pad", 27.75, 170.0, 94.55, -119.09f, 9.95f),
                        spot("Purple Pad", 119.95, 170.0, 95.10, 137.35f, 11.10f), spot("YP DPS", 57.5, 169.0, 65.5, -89.74f, -14.13f))),
                Pair.of("S1", List.of(spot("SS", 108.30, 120.0, 94.0, -89.16f, 1.72f), job(stations, "2", "S1 T2"), spot("4", 93.57, 122.0, 101.52, 90.47f, 16.44f),
                        job(stations, "Levers", P3Plan.isMine("S1 west lever") && !P3Plan.isMine("S1 east lever") ? "S1 west lever" : "S1 east lever"))),
                Pair.of("S2", List.of(spot("EE2", 60.5, 132.0, 139.0, 90.47f, 1.64f), spot("i2", 36.03, 131.0, 139.01, 92.64f, 3.45f), spot("2", 59.53, 120.0, 126.0, -179.59f, 19.56f), spot("4", 39.48, 109.0, 139.61, -0.25f, 29.06f), spot("5", 40.53, 124.0, 125.54, 179.81f, 21.45f))),
                Pair.of("S3", Arrays.asList(ee("EE3", "ee3"), spot("CEE3", 18.5, 121.5, 91.5, 0.02f, -8.14f), spot("Dev", 2.54, 120.0, 77.48, 89.63f, 8.13f), spot("4", 1.11, 109.0, 77.55, 90.40f, 22.47f))),
                Pair.of("S4", Arrays.asList(ee("Core", "core"), ee("Recore", "recore"), spot("1", 41.51, 109.0, 33.36, -179.36f, 17.01f), spot("4", 72.45, 115.0, 45.31, -0.77f, 22.35f)))
        );
        List<LayoutElement> cols = new ArrayList<>();
        for (Pair<String, List<Pair<String, Spots.Spot>>> g : groups) {
            String group = g.first;
            List<LayoutElement> items = new ArrayList<>();
            for (Pair<String, Spots.Spot> e : g.second) {
                String name = e.first;
                Spots.Spot s = e.second;
                if (s == null) items.add(label("§8" + name, w, "Not set."));
                else items.add(act(name, w, "To " + (s.name.equals(name) ? group + " " + name : s.name) + ": " + xyz(s.x, s.y, s.z) + ", facing " + facing(s.yaw) + ".",
                        () -> tp(s.x, s.y, s.z, s.yaw, s.pitch)));
            }
            cols.add(stack("§e" + group, w, "Teleports: " + group + ", in the order you get to them.", items));
        }
        row(cols);
    }

    private void tp(double x, double y, double z, Float yaw, Float pitch) {
        SimActions.send("tp", kv("x", x), kv("y", y), kv("z", z), kv("yaw", yaw), kv("pitch", pitch));
    }

    private String xyz(double x, double y, double z) { return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z); }

    private String facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }

    // practice

    private List<Pair<String, List<Practice.Setup>>> presetColumns(int k) {
        List<Pair<String, List<Practice.Setup>>> out = new ArrayList<>();
        for (Pair<String, List<Practice.Setup>> g : Practice.presetGroups(k)) {
            List<List<Practice.Setup>> chunks = chunked(g.second, 6);
            for (int i = 0; i < chunks.size(); i++) out.add(Pair.of(i == 0 ? g.first : "", chunks.get(i)));
        }
        return out;
    }

    private void practicePanel() {
        Spots.Spot start = Practice.customStart;
        int cps = Practice.checkpoints.size();
        LocalPlayer pl = mc().player;
        int sec = pl != null ? Practice.sectionAt(pl.getX(), pl.getY(), pl.getZ()) : 1;
        Set<String> picked = Practice.customJobsIn(sec);
        int widest = 0;
        for (String s : List.of("Start Custom", "Start Position", "Checkpoint §7(" + cps + ")", "Leap Waypoint §7(4/4)", "Clear")) widest = Math.max(widest, font.width(s));
        int cw = Math.max(100, widest + 10);
        List<LayoutElement> custom = new ArrayList<>();
        custom.add(stack("§eCustom", cw, "Your own practice: a start position (its section is where you stand), that section's jobs you pick and checkpoints to reach in order (within 1 block, the same height), all timed.", List.of(
                withActive(act(start != null ? "§aStart Custom" : "§8Start Custom", cw,
                        start != null ? "Starts the custom practice from your start position: your jobs (every section's picks) then the " + cps + " checkpoint(s), timed." : "Set the Start Position first.",
                        () -> SimActions.send("practiceCustom")), start != null),
                change(start != null ? "§aStart Position" : "Start Position", cw,
                        "Click: the custom practice starts where you stand, facing as you are; its section is the one you're in (or the nearest). " +
                                (start != null ? "Now: S" + Practice.customSection + ", " + at(start) + "." : "Not set."), () -> {
                            Spots.Spot h = here();
                            if (h != null) {
                                LocalPlayer p = mc().player;
                                ItemStack held = p != null ? p.getMainHandItem() : null;
                                int n = Practice.setStart(h, held != null ? Practice.itemKey(held) : null);
                                Ec.msg("§7Custom practice start: §fS" + n + "§7, " + at(h) + (held != null && !held.isEmpty() ? "§7, holding §f" + held.getHoverName().getString() : "") + "§7.");
                            }
                        }),
                new ClickButton(cw, cps > 0 ? "§aCheckpoint §7(" + cps + ")" : "Checkpoint",
                        tip("Left click: a checkpoint where you stand (reached within 1 block across, at this exact height; in order). Right click: removes the last one. Now: " + cps + "."),
                        () -> {
                            Spots.Spot h = here();
                            if (h != null) { Practice.addCheckpoint(h); Ec.msg("§7Checkpoint §f" + Practice.checkpoints.size() + "§7: " + at(h) + "."); }
                            rebuildWidgets();
                        },
                        () -> {
                            Ec.msg(Practice.checkpoints.isEmpty() ? "§7No checkpoints to remove." : "§7Checkpoint §f" + Practice.checkpoints.size() + "§7 removed.");
                            Practice.removeCheckpoint();
                            rebuildWidgets();
                        }),
                new ClickButton(cw, !Practice.leapWaypoints.isEmpty() ? "§aLeap Waypoint §7(" + Practice.leapWaypoints.size() + "/4)" : "Leap Waypoint",
                        tip("Left click: where you stand becomes a leap's landing in the custom practice: the 1st the leap menu's top left, then top right, bottom left, bottom right. Right click: removes the last one. Now: " + Practice.leapWaypoints.size() + "/4."),
                        () -> {
                            Spots.Spot h = here();
                            if (h != null) {
                                int n = Practice.addLeapWaypoint(h);
                                Ec.msg(n == 0 ? "§7All 4 leap waypoints are set (right click removes the last)." : "§7Leap waypoint §f" + n + "§7 (" + QUADRANTS.get(n - 1) + "): " + at(h) + ".");
                            }
                            rebuildWidgets();
                        },
                        () -> {
                            int n = Practice.leapWaypoints.size();
                            Ec.msg(n == 0 ? "§7No leap waypoints to remove." : "§7Leap waypoint §f" + n + "§7 (" + QUADRANTS.get(n - 1) + ") removed.");
                            Practice.removeLeapWaypoint();
                            rebuildWidgets();
                        }),
                new DelaySlider(cw),
                change("§cClear", cw, "Clears the custom practice: start, jobs, checkpoints and leap waypoints; the timer back to 0.", () -> { Practice.clearCustom(); Ec.msg("§7Custom practice cleared."); })
        )));
        LayoutElement io = stack("", 64, "Your custom practice as a short code, to send to someone or take theirs.", List.of(
                change("Export", 64, "Copies the custom practice as a short code (start, jobs, checkpoints, leap waypoints, start timer) to send to someone.", () -> {
                    String code = Practice.exportCode();
                    mc().keyboardHandler.setClipboard(code);
                    Ec.msg("§7Custom practice code copied: §f" + code);
                }),
                change("Import", 64, "Replaces your custom practice with the code on your clipboard.", () -> {
                    if (Practice.importCode(mc().keyboardHandler.getClipboard())) Ec.msg("§7Custom practice imported (S" + Practice.customSection + ", " + Practice.customJobs.size() + " job(s), " + Practice.checkpoints.size() + " checkpoint(s), " + Practice.leapWaypoints.size() + " leap waypoint(s)).");
                    else Ec.msg("§cYour clipboard doesn't hold a custom practice code.");
                })
        ));
        List<String> otherParts = new ArrayList<>();
        for (int s = 1; s <= 4; s++) {
            if (s == sec) continue;
            Set<String> j = Practice.customJobsIn(s);
            if (j.isEmpty()) continue;
            List<String> shorts = new ArrayList<>();
            for (String it : j) shorts.add(COLUMNS.get(column(it)).first);
            otherParts.add("S" + s + " " + String.join(" ", shorts));
        }
        String others = String.join(", ", otherParts);
        List<String> jobs = new ArrayList<>(P3Plan.jobsIn(sec));
        jobs.sort((a, b) -> Integer.compare(column(a), column(b)));
        List<List<String>> jobChunks = chunked(jobs, 6);
        for (int i = 0; i < jobChunks.size(); i++) {
            List<LayoutElement> items = new ArrayList<>();
            for (String job : jobChunks.get(i)) {
                boolean on = picked.contains(job);
                items.add(change((on ? "§a" : "§8") + COLUMNS.get(column(job)).first, JOB_W, jobName(job) + ": " + (on ? "yours to do in the custom practice" : "done at its start") + ". Click: " + (on ? "done at the start" : "yours") + ".", () -> Practice.toggleJob(job)));
            }
            custom.add(stack(i == 0 ? "§6§lS" + sec : "", JOB_W, "The section you're in: go to another to pick its jobs too (the picks of every section stay)." +
                    (!others.isEmpty() ? " Picked elsewhere: " + others + "." : ""), items));
        }
        List<LayoutElement> secCols = new ArrayList<>();
        int s = practiceSec >= 1 && practiceSec <= 4 ? practiceSec : 1;
        secCols.add(sectionTabs(s));
        secCols.add(label("", 8));
        List<Practice.Setup> all = Practice.presets();
        int width = 0;
        for (int k = 1; k <= 4; k++) width = Math.max(width, presetColumns(k).size());
        int nameW = 0;
        for (Practice.Setup p : all) nameW = Math.max(nameW, font.width(p.name) + 10);
        int headW = 0;
        for (int k = 1; k <= 4; k++) for (Pair<String, List<Practice.Setup>> c : presetColumns(k)) headW = Math.max(headW, font.width(c.first) + 6);
        int pw = Math.max(64, Math.max(nameW, headW));
        List<Pair<String, List<Practice.Setup>>> parts = presetColumns(s);
        for (int i = 0; i < width; i++) {
            Pair<String, List<Practice.Setup>> pc = i < parts.size() ? parts.get(i) : Pair.of("", List.of());
            String head = pc.first;
            List<LayoutElement> items = new ArrayList<>();
            for (Practice.Setup p : pc.second) {
                List<String> names = new ArrayList<>();
                for (String j : p.jobs) names.add(jobName(j));
                items.add(act(p.name, pw, "Practice " + p.name + " (S" + s + "): " + String.join(", ", names) +
                        (!p.checkpoints.isEmpty() ? ", then " + p.checkpoints.size() + " checkpoint(s)" : "") +
                        (p.delay != null ? ". Start timer " + String.format(Locale.ROOT, "%.1f", p.delay) + "s." : ". Start timer: the slider's."), () -> SimActions.send("practicePreset", kv("name", p.name))));
            }
            secCols.add(stack(!head.isEmpty() ? "§7" + head : "", pw, "Section " + s + "'s presets: a start, the jobs and checkpoints (the rest of the section done), and their own start timer.", items));
        }
        List<LayoutElement> cols = new ArrayList<>();
        cols.add(io);
        cols.add(label("", 8));
        List<LayoutElement> overflow = new ArrayList<>(custom.subList(1, custom.size()));
        Collections.reverse(overflow);
        cols.addAll(overflow);
        cols.add(custom.get(0));
        cols.add(label("", 16));
        cols.addAll(secCols);
        LinearLayout r = LinearLayout.horizontal().spacing(2);
        r.defaultCellSetting().alignVerticallyTop();
        panel.addChild(r);
        for (LayoutElement e : cols) r.addChild(e);
    }

    private LayoutElement sectionTabs(int open) {
        List<LayoutElement> items = new ArrayList<>();
        for (int s = 1; s <= 4; s++) {
            int sec = s;
            items.add(change(open == s ? "§aS" + s : "S" + s, 64, "Section " + s + "'s practices.", () -> practiceSec = sec));
        }
        return stack("§eSections", 64, "Each section's own practices.", items);
    }

    // pieces

    private void party() {
        int x = width - PARTY_W - 6;
        int[] y = {6};
        java.util.function.Consumer<String> line = t -> {
            StringWidget w = new StringWidget(Component.literal(t), font);
            w.setPosition(x, y[0] + 6);
            addRenderableWidget(w);
            y[0] += 20;
        };
        if (P3Party.hosting()) {
            List<String> names = P3Party.players();
            String code = P3Party.code();
            line.accept("Party code: " + (code != null ? "§b§l" + code : "§7..."));
            String joined = String.join(", ", names);
            line.accept("§7" + (joined.isEmpty() ? "Just you" : joined) + " §8" + Math.max(names.size(), 1) + "/5");
            addRenderableWidget(Button.builder(Component.literal("Close Party"), b -> { P3Party.stopHosting(); rebuildWidgets(); })
                    .tooltip(tip("Drops everyone who joined; your sim goes on.")).bounds(x, y[0], PARTY_W, 20).build());
            return;
        }
        if (PartyClient.joiner()) {
            List<String> names = new ArrayList<>();
            LocalPlayer p = mc().player;
            names.add(p != null ? p.getName().getString() : "You");
            for (PartyClient.Mate m : PartyClient.team) if (m.player) names.add(m.name);
            line.accept("§7In a party");
            line.accept("§7" + String.join(", ", names) + " §8" + names.size() + "/5");
            addRenderableWidget(Button.builder(Component.literal("Leave Party"), b -> { mc().setScreen(null); SimWorld.leave(); })
                    .tooltip(tip("Back to the title screen; the host's sim goes on.")).bounds(x, y[0], PARTY_W, 20).build());
            return;
        }
        if (!Ec.BETA) return;
        addRenderableWidget(Button.builder(Component.literal("Host Party"), b -> { P3Party.host(); rebuildWidgets(); })
                .tooltip(tip("Friends join your sim: you get a 4-letter code to send them.")).bounds(x, y[0], PARTY_W, 20).build());
        y[0] += ROW;
        EditBox box = new EditBox(font, x, y[0], PARTY_W - 42, 20, Component.literal("Code"));
        box.setMaxLength(4);
        box.setHint(Component.literal("§8Code"));
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("Join"), b -> { if (!box.getValue().isBlank()) { mc().setScreen(null); P3Party.join(box.getValue()); } })
                .tooltip(tip("Joins a friend's sim by its party code.")).bounds(x + PARTY_W - 40, y[0], 40, 20).build());
    }

    private void text(String t) { panel.addChild(new StringWidget(Component.literal(t), font)); }

    private void row(List<? extends LayoutElement> widgets) {
        LinearLayout r = LinearLayout.horizontal().spacing(2);
        panel.addChild(r);
        for (LayoutElement w : widgets) r.addChild(w);
    }

    private Tooltip tip(String text) { return Tooltip.create(Component.literal(text)); }

    private LayoutElement label(String t, int w) { return label(t, w, null); }

    private LayoutElement label(String t, int w, String about) {
        FrameLayout cell = new FrameLayout().setMinDimensions(w, 20);
        if (!t.isEmpty()) {
            StringWidget sw = new StringWidget(Component.literal(t), font);
            if (about != null) sw.setTooltip(tip(about));
            cell.addChild(sw);
        }
        return cell;
    }

    private Button change(String label, int w, String about, Runnable run) {
        return Button.builder(Component.literal(label), b -> {
            run.run();
            TeslaMapsConfig.save();
            rebuildWidgets();
        }).tooltip(tip(about)).width(w).build();
    }

    private Button toggle(String name, boolean on, int w, String about, Runnable run) { return change(on ? "§a" + name : name, w, about, run); }

    private Button tabCell(Tab t, String name, String about) {
        return Button.builder(Component.literal(tab == t ? "§a" + name : name), b -> {
            tab = tab == t ? Tab.PLAN : t;
            rebuildWidgets();
        }).tooltip(tip(about)).width(100).build();
    }

    private Button act(String label, int w, String about, Runnable run) {
        return Button.builder(Component.literal(label), b -> { mc().setScreen(null); run.run(); }).tooltip(tip(about)).width(w).build();
    }

    private String pick(String name, boolean on) { return on ? "§a§n" + name : name; }

    private String onOff(boolean b) { return b ? "§aON" : "§cOFF"; }

    private boolean inParty() { return PartyClient.party; }

    private static String take(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    private static Pair<String, Object> kv(String k, Object v) { return Pair.of(k, v); }

    private static <T> List<List<T>> chunked(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) out.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        return out;
    }

    private static class ClickButton extends Button {
        private final Runnable left;
        private final Runnable right;

        ClickButton(int w, String text, Tooltip tooltip, Runnable left, Runnable right) {
            super(0, 0, w, 20, Component.literal(text), b -> left.run(), s -> s.get());
            this.left = left;
            this.right = right;
            setTooltip(tooltip);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo info) {
            return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            Ec.LOG.info("[ec] p3sim menu click: button " + event.button() + " on " + getMessage().getString());
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) right.run();
            else left.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            extractDefaultSprite(g);
            extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }

    private static final int RESTART_W = 100;
    private static final int PARTY_W = 130;
    private static final List<String> QUADRANTS = List.of("top left", "top right", "bottom left", "bottom right");
    private static final int PANEL_H = 6 * 20 + 5 * 2 + 4;
    private static final int GAP = 10;
    private static final int ROW = 22;
    private static final int SIDE_W = 90;
    private static final int CLASS_W = 70;
    private static final int LABEL_W = 64;
    private static final int SEC_W = 24;
    private static final int JOB_W = 40;
    private static final int LEAP_W = 90;
    private static final int SPOT_W = 92;
    private static final int MIN_SPEED = 100;
    private static final int MAX_SPEED = 750;

    private static Tab tab = null;
    private static int practiceSec = 0;

    public static Terminals.Type lastType = Terminals.Type.ORDER;

    public static final List<Integer> PINGS = List.of(0, 50, 100, 150, 200, 300);

    public static final List<Pair<String, String>> COLUMNS;

    static {
        List<Pair<String, String>> c = new ArrayList<>();
        for (int i = 1; i <= 5; i++) c.add(Pair.of("" + i, "Terminal " + i + "."));
        c.add(Pair.of("L", "The left lever, coming in along the track (S1 east, S2 high, S3 west, S4 low)."));
        c.add(Pair.of("R", "The right lever (S1 west, S2 low, S3 east, S4 high)."));
        c.add(Pair.of("D", "The section's device: S1 Simon Says, S2 Lights, S3 Arrows, S4 Target (i4)."));
        c.add(Pair.of("G", "The section's gate: blown with Superboom or a Dungeonbreaker (or open by itself 5 s after the section ends)."));
        COLUMNS = List.copyOf(c);
    }

    public static int column(String job) {
        String s = P3Plan.short_(job);
        switch (s) {
            case "L": return 5;
            case "R": return 6;
            case "D": return 7;
            case "G": return 8;
            default:
                int n;
                try { n = Integer.parseInt(s); } catch (NumberFormatException e) { n = 1; }
                return Math.clamp(n - 1, 0, 4);
        }
    }

    public static String jobName(String job) {
        if (job.startsWith("gate ")) return "S" + job.substring("gate ".length()) + " gate";
        int sp = job.indexOf(' ');
        String s = sp < 0 ? job : job.substring(0, sp);
        String sh = P3Plan.short_(job);
        switch (sh) {
            case "L":
            case "R":
            case "G":
                return job;
            case "D": {
                String after = sp < 0 ? job : job.substring(sp + 1);
                return s + " device (" + (after.equals("SS") ? "Simon Says" : after) + ")";
            }
            default:
                return s + " terminal " + sh;
        }
    }

    public static final List<Pair<String, String>> DEATH_TICKS = List.of(
            Pair.of("Off", "No death ticks: being ahead of Goldor never hurts."),
            Pair.of("Warn", "A death tick only says so: Goldor's line and a title. Nothing is used up and you don't die."),
            Pair.of("Masks", "As on Hypixel: your Spirit Mask, Bonzo's Mask and Phoenix save you; with none left you die (back to the section's start).")
    );

    public static final Map<Terminals.Type, Pair<String, String>> TYPE_NAMES;

    static {
        Map<Terminals.Type, Pair<String, String>> m = new LinkedHashMap<>();
        m.put(Terminals.Type.ORDER, Pair.of("Order", "Every terminal Click in order!: the panes 1 to 14 in order."));
        m.put(Terminals.Type.PANES, Pair.of("Panes", "Every terminal Correct all the panes!: every red pane to green."));
        m.put(Terminals.Type.RUBIX, Pair.of("Rubix", "Every terminal Change all to same color!: every pane to one colour."));
        m.put(Terminals.Type.STARTS, Pair.of("Starts", "Every terminal What starts with: 'X'?: every item whose name starts with the letter."));
        m.put(Terminals.Type.SELECT, Pair.of("Select", "Every terminal Select all the X items!: every item of the colour."));
        m.put(Terminals.Type.MELODY, Pair.of("Melody", "Every terminal Melody: each row's button as the pane passes it."));
        TYPE_NAMES = m;
    }
}
