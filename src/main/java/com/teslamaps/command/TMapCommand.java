package com.teslamaps.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.DoorType;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.screen.HudEditScreen;
import com.teslamaps.screen.MapConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.teslamaps.utils.TabListUtils;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.features.TerminalSimulator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TMapCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommands.literal("ping").executes(context -> {
            com.teslamaps.features.PingMeter.request();
            return 1;
        }));

        if (dispatcher.getRoot().getChild("visited") == null) {
            dispatcher.register(ClientCommands.literal("visited")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(com.teslamaps.dungeon.RoomVisits.query("")));
                        return 1;
                    })
                    .then(ClientCommands.argument("room", StringArgumentType.greedyString())
                            .suggests((c, sb) -> {
                                String typed = sb.getRemaining().toLowerCase();
                                for (String r : com.teslamaps.dungeon.RoomVisits.rooms())
                                    if (r.toLowerCase().startsWith(typed) && !r.equalsIgnoreCase(typed)) sb.suggest(r);
                                return sb.buildFuture();
                            })
                            .executes(context -> {
                                context.getSource().sendFeedback(Component.literal(com.teslamaps.dungeon.RoomVisits.query(
                                        StringArgumentType.getString(context, "room"))));
                                return 1;
                            })));
        }

        // /pv as its own command, but another mod that already owns it keeps it
        if (dispatcher.getRoot().getChild("pv") == null) {
            dispatcher.register(profileViewer("pv"));
        }

        var termsim = ClientCommands.literal("termsim").executes(context -> {
            Minecraft.getInstance().execute(() -> TerminalSimulator.open(TerminalSimulator.ping()));
            return 1;
        });
        termsim.then(ClientCommands.argument("ping", IntegerArgumentType.integer(0))
                .executes(context -> {
                    int ping = IntegerArgumentType.getInteger(context, "ping");
                    Minecraft.getInstance().execute(() -> TerminalSimulator.open(ping));
                    return 1;
                }));
        for (TerminalTypes type : TerminalTypes.values()) {
            termsim.then(ClientCommands.literal(type.name().toLowerCase())
                    .executes(context -> {
                        Minecraft.getInstance().execute(() -> type.getSimulator().open(TerminalSimulator.ping()));
                        return 1;
                    })
                    .then(ClientCommands.argument("ping", IntegerArgumentType.integer(0))
                            .executes(context -> {
                                int ping = IntegerArgumentType.getInteger(context, "ping");
                                Minecraft.getInstance().execute(() -> type.getSimulator().open(ping));
                                return 1;
                            })));
        }
        dispatcher.register(termsim);

        for (char type : new char[]{'f', 'm', 't'}) {
            for (int n = 1; n <= (type == 't' ? 5 : 7); n++) {
                String code = "" + type + n;
                dispatcher.register(ClientCommands.literal(code).executes(context -> {
                    com.teslamaps.features.ChatCommands.queueFloor(code);
                    return 1;
                }));
            }
        }

        dispatcher.register(ClientCommands.literal("cata")
                .executes(context -> {
                    com.teslamaps.dungeon.CataCard.show(Minecraft.getInstance().getUser().getName(), false, true);
                    return 1;
                })
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            var conn = Minecraft.getInstance().getConnection();
                            String typed = b.getRemaining().toLowerCase();
                            if (conn != null) for (var info : conn.getOnlinePlayers()) {
                                String n = info.getProfile().name();
                                if (n.matches("\\w{1,16}") && n.toLowerCase().startsWith(typed) && !n.equalsIgnoreCase(typed)) b.suggest(n);
                            }
                            return b.buildFuture();
                        })
                        .executes(context -> {
                            com.teslamaps.dungeon.CataCard.show(StringArgumentType.getString(context, "name"), false, true);
                            return 1;
                        })));

        dispatcher.register(ClientCommands.literal("tmap")
                .executes(context -> {
                    Minecraft.getInstance().schedule(() -> {
                        Minecraft.getInstance().setScreen(new MapConfigScreen());
                    });
                    return 1;
                })
                .then(com.teslamaps.dungeon.puzzle.PuzzleReset.command())
                .then(ClientCommands.literal("tracker")
                        .then(com.teslamaps.features.tracker.TrackerRows.attach(ClientCommands.argument("id", StringArgumentType.word()))
                                .suggests((c, b) -> {
                                    for (var t : com.teslamaps.features.tracker.Tracker.all()) b.suggest(t.id);
                                    return b.buildFuture();
                                })
                                .then(ClientCommands.literal("reset")
                                        .executes(c -> tracker(c.getSource(), StringArgumentType.getString(c, "id"), "session"))
                                        .then(ClientCommands.literal("session")
                                                .executes(c -> tracker(c.getSource(), StringArgumentType.getString(c, "id"), "session")))
                                        .then(ClientCommands.literal("total")
                                                .executes(c -> tracker(c.getSource(), StringArgumentType.getString(c, "id"), "total"))))
                                .then(ClientCommands.literal("view")
                                        .executes(c -> tracker(c.getSource(), StringArgumentType.getString(c, "id"), "view")))
                                .then(ClientCommands.literal("history")
                                        .executes(c -> tracker(c.getSource(), StringArgumentType.getString(c, "id"), "history")))))
                .then(com.teslamaps.features.SecretWaypoints.command())
                .then(com.teslamaps.features.HideExCoopMembers.command())
                .then(com.teslamaps.features.MinionFeatures.command())
                .then(com.teslamaps.features.IslandAlerts.command())
                .then(com.teslamaps.dungeon.RunLogger.command())
                .then(com.teslamaps.features.customize.CustomizeKey.attach(ClientCommands.literal("customize")
                        .executes(context -> openCustomize(context.getSource(), false))
                        .then(ClientCommands.literal("item")
                                .executes(context -> openCustomize(context.getSource(), true)))))
                .then(ClientCommands.literal("image")
                        .then(ClientCommands.literal("block")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            com.teslamaps.features.SharedImageChat.block(
                                                    StringArgumentType.getString(context, "name"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("unblock")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            com.teslamaps.features.SharedImageChat.unblock(
                                                    StringArgumentType.getString(context, "name"));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("screenshots")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.ScreenshotScreen(null)));
                            return 1;
                        })
                        // the chat buttons under a saved screenshot run these
                        .then(ClientCommands.literal("copy")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            com.teslamaps.features.ScreenshotManager.copyAsync(
                                                    com.teslamaps.features.ScreenshotManager.byName(name),
                                                    err -> com.teslamaps.features.ImageShare.feedback(err == null
                                                            ? "§a[TeslaMaps] §7Copied §f" + name
                                                            : "§c[TeslaMaps] Could not copy " + name + ": " + err));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("delete")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            boolean ok = com.teslamaps.features.ScreenshotManager.delete(
                                                    com.teslamaps.features.ScreenshotManager.byName(name));
                                            context.getSource().sendFeedback(Component.literal(
                                                    ok ? "§7[TeslaMaps] Deleted §f" + name
                                                       : "§c[TeslaMaps] Could not delete " + name));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("share")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            com.teslamaps.features.ScreenshotManager.shareToClipboard(
                                                    com.teslamaps.features.ScreenshotManager.byName(name));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("folder")
                                .executes(context -> {
                                    com.teslamaps.features.ScreenshotManager.openFolder();
                                    return 1;
                                })))
                .then(ClientCommands.literal("cape")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.CapeScreen(null)));
                            return 1;
                        })
                        .then(ClientCommands.literal("off")
                                .executes(context -> {
                                    com.teslamaps.config.TeslaMapsConfig.get().capeEnabled = false;
                                    com.teslamaps.features.capes.CapeManager.select("");
                                    context.getSource().sendFeedback(Component.literal("§7Cape off"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("reload")
                                .executes(context -> {
                                    com.teslamaps.features.capes.CapeManager.rescan();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7Reloaded " + com.teslamaps.features.capes.CapeManager.customs().size()
                                                    + " capes from config/teslamaps/capes"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("report")
                                .executes(context -> capeReport(context.getSource(), null))
                                .then(ClientCommands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String typed = builder.getRemaining().toLowerCase();
                                            for (String name : capedPlayers()) {
                                                if (name.toLowerCase().startsWith(typed)) builder.suggest(name);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> capeReport(context.getSource(),
                                                StringArgumentType.getString(context, "player")))))
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemaining().toLowerCase();
                                    for (var cape : com.teslamaps.features.capes.CapeManager.all()) {
                                        if (cape.name.toLowerCase().startsWith(typed)) builder.suggest(cape.name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String wanted = StringArgumentType.getString(context, "name").trim();
                                    for (var cape : com.teslamaps.features.capes.CapeManager.all()) {
                                        if (cape.name.equalsIgnoreCase(wanted) || cape.id.equalsIgnoreCase(wanted)) {
                                            com.teslamaps.features.capes.CapeManager.select(cape.id);
                                            context.getSource().sendFeedback(Component.literal("§7Cape: §f" + cape.name));
                                            return 1;
                                        }
                                    }
                                    context.getSource().sendFeedback(Component.literal("§cNo cape called " + wanted));
                                    return 1;
                                })))
                .then(ClientCommands.literal("protect")
                        .executes(context -> {
                            if (!TeslaMapsConfig.get().itemProtectionUuid) {
                                context.getSource().sendFeedback(Component.literal("§7Turn on Protect Single Items first (Inventory)"));
                                return 1;
                            }
                            com.teslamaps.features.ItemProtection.toggle(context.getSource().getPlayer().getMainHandItem());
                            return 1;
                        }))
                .then(ClientCommands.literal("repo")
                        .executes(context -> repoStatus(context.getSource()))
                        .then(ClientCommands.literal("status")
                                .executes(context -> repoStatus(context.getSource())))
                        .then(ClientCommands.literal("reload")
                                .executes(context -> {
                                    com.teslamaps.utils.RepoCache.reloadAll();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7[TeslaMaps] Reloading repo data, §f/tmap repo §7shows the result"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("search")
                        .executes(context -> openSearch(null))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    com.teslamaps.features.ItemIndex.clear();
                                    context.getSource().sendFeedback(Component.literal("§7Item index cleared"));
                                    return 1;
                                }))
                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> openSearch(StringArgumentType.getString(context, "query")))))
                .then(ClientCommands.literal("suggest")
                        .executes(context -> openFeedback(false, null))
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> openFeedback(false, StringArgumentType.getString(context, "text")))))
                .then(ClientCommands.literal("bugreport")
                        .executes(context -> openFeedback(true, null))
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> openFeedback(true, StringArgumentType.getString(context, "text")))))
                .then(com.teslamaps.features.croesus.PriceDebug.command())
                .then(com.teslamaps.features.SackApi.command())
                .then(com.teslamaps.features.Notifications.command())
                .then(com.teslamaps.features.ItemCycle.command())
                .then(com.teslamaps.database.RoomLearn.newCoreCommand())
                .then(com.teslamaps.database.RoomLearn.newRoomCommand())
                .then(com.teslamaps.database.RoomLearn.roomDataCommand())
                .then(com.teslamaps.features.EventTracker.soundsCommand())
                .then(com.teslamaps.features.EventTracker.particlesCommand())
                .then(com.teslamaps.features.EventTracker.playSoundCommand())
                .then(ClientCommands.literal("npc")
                        .executes(context -> openNpcFinder(null))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    com.teslamaps.features.npc.NpcFinder.clearTargets();
                                    context.getSource().sendFeedback(Component.literal("§7NPC waypoints cleared"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clearwarps")
                                .executes(context -> {
                                    com.teslamaps.features.npc.NpcWarps.clearLocked();
                                    context.getSource().sendFeedback(Component.literal("§7Locked warps reset"));
                                    return 1;
                                }))
                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> openNpcFinder(StringArgumentType.getString(context, "query")))))
                .then(ClientCommands.literal("mayor")
                        .executes(context -> {
                            com.teslamaps.api.ElectionApi.refresh();
                            context.getSource().sendFeedback(Component.literal(com.teslamaps.api.ElectionApi.isLoaded()
                                    ? "§7Mayor: §f" + com.teslamaps.api.ElectionApi.mayor() + " §7Minister: §f"
                                    + com.teslamaps.api.ElectionApi.minister() + " §7Perks: §f" + com.teslamaps.api.ElectionApi.perks()
                                    : "§7No election data yet, fetching..."));
                            return 1;
                        }))
                .then(ClientCommands.literal("radar")
                        .executes(context -> {
                            boolean on = com.teslamaps.features.StarMobRadar.toggle();
                            context.getSource().sendFeedback(Component.literal("§7Star mob radar " + (on ? "§aon" : "§coff")));
                            return 1;
                        }))
                .then(ClientCommands.literal("gui")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() -> {
                                Minecraft.getInstance().setScreen(new HudEditScreen(null));
                            });
                            return 1;
                        }))
                .then(ClientCommands.literal("mobs")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() -> Minecraft.getInstance()
                                    .setScreen(new com.teslamaps.screen.MobVisualScreen(null)));
                            return 1;
                        }))
                .then(ClientCommands.literal("buttons")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() -> Minecraft.getInstance()
                                    .setScreen(new com.teslamaps.screen.InventoryButtonEditorScreen(null)));
                            return 1;
                        }))
                .then(ClientCommands.literal("pcmd")
                        .executes(context -> {
                            var list = TeslaMapsConfig.get().chatCommandsWhitelist;
                            context.getSource().sendFeedback(Component.literal("§7Chat command whitelist: §f"
                                    + (list.isEmpty() ? "(empty)" : String.join(", ", list))));
                            return 1;
                        })
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    boolean added = com.teslamaps.features.ChatCommands.toggleWhitelist(name);
                                    context.getSource().sendFeedback(Component.literal(added
                                            ? "§aAdded §f" + name + " §ato the chat command whitelist"
                                            : "§cRemoved §f" + name + " §cfrom the chat command whitelist"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("apikey")
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    TeslaMapsConfig.get().hypixelApiKey = "";
                                    TeslaMapsConfig.save();
                                    HypixelApi.clearCache();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7Hypixel API key cleared, the viewer uses the PV API now"));
                                    return 1;
                                }))
                        .then(ClientCommands.argument("key", StringArgumentType.greedyString())
                                .executes(context -> {
                                    TeslaMapsConfig c = TeslaMapsConfig.get();
                                    c.hypixelApiKey = StringArgumentType.getString(context, "key").trim();
                                    TeslaMapsConfig.save();
                                    HypixelApi.clearCache();
                                    if (c.hypixelApiKey.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7Hypixel API key cleared"));
                                        return 1;
                                    }
                                    context.getSource().sendFeedback(Component.literal("§7Checking API key..."));
                                    Minecraft mc = Minecraft.getInstance();
                                    HypixelApi.validateKey(mc.getUser().getProfileId().toString().replace("-", ""))
                                            .thenAccept(problem -> mc.execute(() -> {
                                                if (mc.player == null) return;
                                                mc.player.sendSystemMessage(Component.literal(problem == null
                                                        ? "§aAPI key works" : "§cAPI key rejected: §7" + problem));
                                            }));
                                    return 1;
                                })))
                .then(profileViewer("pv"))
                .then(ClientCommands.literal("update")
                        .executes(context -> {
                            com.teslamaps.features.Updater.install();
                            return 1;
                        })
                        .then(ClientCommands.literal("check")
                                .executes(context -> {
                                    com.teslamaps.features.Updater.check(true);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("changelog")
                                .executes(context -> {
                                    com.teslamaps.features.Updater.openChangelog();
                                    return 1;
                                })))
                .then(ClientCommands.literal("corpse")
                        .then(ClientCommands.literal("share")
                                .executes(context -> corpseShare(context.getSource(), -1))
                                .then(ClientCommands.argument("id", IntegerArgumentType.integer(0))
                                        .executes(context -> corpseShare(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"))))))
                .then(ClientCommands.literal("shards")
                        .executes(context -> shardReport(context.getSource(), 10))
                        .then(ClientCommands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 60))
                                .executes(context -> shardReport(context.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommands.literal("chscan")
                        .executes(context -> {
                            for (String line : com.teslamaps.features.WorldScanner.report()) {
                                context.getSource().sendFeedback(Component.literal(line));
                            }
                            return 1;
                        })
                        .then(ClientCommands.literal("reset")
                                .executes(context -> {
                                    com.teslamaps.features.WorldScanner.reset();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fCH scanner reset"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("column")
                                .executes(context -> {
                                    for (String line : com.teslamaps.features.WorldScanner.columnDump()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                })))
                .then(ClientCommands.literal("flare")
                        .executes(context -> {
                            for (String line : com.teslamaps.features.hunting.FlareMode.report()) {
                                context.getSource().sendFeedback(Component.literal(line));
                            }
                            return 1;
                        })
                        .then(ClientCommands.literal("on").executes(context -> setFlare(context.getSource(), true)))
                        .then(ClientCommands.literal("off").executes(context -> setFlare(context.getSource(), false)))
                        .then(ClientCommands.literal("pause")
                                .executes(context -> {
                                    com.teslamaps.features.hunting.FlareMode.togglePause();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fFlare session "
                                            + (com.teslamaps.features.hunting.FlareMode.paused() ? "§6paused" : "§aresumed")));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("reset")
                                .executes(context -> {
                                    com.teslamaps.features.hunting.FlareMode.reset();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fFlare session reset"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("mob")
                                .then(ClientCommands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(context, "name").trim();
                                            TeslaMapsConfig.get().flareMob = name.isEmpty() ? "Flare" : name;
                                            TeslaMapsConfig.save();
                                            com.teslamaps.features.hunting.FlareMode.reset();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§b[TeslaMaps] §fFlare mob: §e" + TeslaMapsConfig.get().flareMob));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("be")
                        .executes(context -> {
                            for (String line : com.teslamaps.features.BestiaryTracker.report()) {
                                context.getSource().sendFeedback(Component.literal(line));
                            }
                            return 1;
                        })
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    int n = com.teslamaps.features.BestiaryTracker.clear();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fCleared §e" + n + "§f tracked mob(s)"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("auto")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    cfg.bestiaryAutoTrack = !cfg.bestiaryAutoTrack;
                                    TeslaMapsConfig.save();
                                    if (!cfg.bestiaryAutoTrack) {
                                        context.getSource().sendFeedback(Component.literal(
                                                "§b[TeslaMaps] §fAuto tracking §coff"));
                                        return 1;
                                    }
                                    java.util.List<String> added = com.teslamaps.features.BestiaryTracker.addAllFromTab();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fAuto tracking §aon §7· every non-maxed mob in the tab"
                                                    + (added.isEmpty() ? "" : "§7, added §e" + added.size())));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("mob", StringArgumentType.greedyString())
                                        .suggests((c, b) -> suggestMobs(b, false))
                                        .executes(context -> {
                                            String arg = StringArgumentType.getString(context, "mob").trim();
                                            // "Goblin Raider 12" - a trailing number is the idle cap, not the name
                                            int minutes = 0;
                                            int cut = arg.lastIndexOf(' ');
                                            if (cut > 0) {
                                                try {
                                                    minutes = Integer.parseInt(arg.substring(cut + 1));
                                                    arg = arg.substring(0, cut).trim();
                                                } catch (NumberFormatException ignored) {}
                                            }
                                            String added = com.teslamaps.features.BestiaryTracker.add(arg);
                                            String name = added != null ? added : arg;
                                            String idle = "";
                                            if (minutes > 0) {
                                                name = com.teslamaps.features.BestiaryTracker.setIdle(arg, minutes);
                                                idle = " §7· idle §e" + minutes + "m";
                                            }
                                            context.getSource().sendFeedback(Component.literal(added == null
                                                    ? "§b[TeslaMaps] §7Already tracked: §e" + name + idle
                                                    : "§b[TeslaMaps] §fTracking §e" + name + idle));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("mob", StringArgumentType.greedyString())
                                        .suggests((c, b) -> suggestMobs(b, true))
                                        .executes(context -> {
                                            String arg = StringArgumentType.getString(context, "mob");
                                            String gone = com.teslamaps.features.BestiaryTracker.remove(arg);
                                            context.getSource().sendFeedback(Component.literal(gone == null
                                                    ? "§b[TeslaMaps] §7Not tracked: §e" + arg
                                                    : "§b[TeslaMaps] §fStopped tracking §e" + gone));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("hotspotshare")
                        .then(ClientCommands.argument("id", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    int id = IntegerArgumentType.getInteger(context, "id");
                                    Minecraft.getInstance().execute(() -> com.teslamaps.features.HotspotShare.share(id));
                                    return 1;
                                })))
                .then(ClientCommands.literal("achievements")
                        .executes(context -> {
                            com.teslamaps.features.Achievements.list();
                            return 1;
                        }))
                .then(ClientCommands.literal("safari")
                        .executes(context -> {
                            for (String line : com.teslamaps.safari.SafariHistory.summary(5)) {
                                context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(line));
                            }
                            return 1;
                        })
                        .then(ClientCommands.literal("gui")
                                .executes(context -> {
                                    Minecraft.getInstance().schedule(() ->
                                            Minecraft.getInstance().setScreen(new com.teslamaps.screen.SafariScreen(null)));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("missing")
                                .executes(context -> {
                                    for (String l : com.teslamaps.safari.SafariTracker.missingLines(true))
                                        context.getSource().sendFeedback(Component.literal(l));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("copy")
                                .executes(context -> {
                                    com.teslamaps.safari.SafariTracker.copyMissing();
                                    context.getSource().sendFeedback(Component.literal("§aCopied the missing list"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("share")
                                .executes(context -> {
                                    com.teslamaps.safari.SafariTracker.shareMissing();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("scan")
                                .executes(context -> safariScan(context.getSource(), 6))
                                .then(ClientCommands.argument("chunks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 16))
                                        .executes(context -> safariScan(context.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunks")))))
                        .then(ClientCommands.literal("debug")
                                .executes(context -> safariDebug(context.getSource(), 16))
                                .then(ClientCommands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                        .executes(context -> safariDebug(context.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius")))))
                        .then(ClientCommands.literal("species")
                                .executes(context -> {
                                    for (String line : com.teslamaps.safari.SafariHistory.speciesSummary()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("import")
                                .executes(context -> {
                                    com.teslamaps.safari.SafariLogImport.start();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("testalert")
                                .executes(context -> {
                                    com.teslamaps.safari.SafariBanners.testAll();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("mounds")
                                .executes(context -> {
                                    for (String line : com.teslamaps.safari.SafariESP.debugMounds()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                })))
                .then(ClientCommands.literal("greenhouse")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "§b[TeslaMaps] §fGreenhouse: " + com.teslamaps.features.GreenhousePlanner.status()));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7/tmap greenhouse <code|paste> · rotate · flip · plot <n> · phase · scan · clear"));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7auto: find the plot and the rotation that fits the floor · rotate auto only the rotation"));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7corner1 / corner2 pin the plot by hand"));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7learn reads the floor under your plants · soil <plant> <block> sets it by hand"));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7learnplants: plot is correct -> remember the heads, wrong plants turn red after that"));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7mutation <plant>: that plant grows on its own, out of the plant counter"));
                            return 1;
                        })
                        .then(ClientCommands.literal("todo")
                                .executes(context -> {
                                    for (String line : com.teslamaps.features.GreenhousePlanner.todoReport()) {
                                        context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] " + line));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("mutation")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fPlants in this plot §8· /tmap greenhouse mutation <plant|clear>"));
                                    for (String line : com.teslamaps.features.GreenhousePlanner.mutationList()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("plant", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                    + com.teslamaps.features.GreenhousePlanner.toggleMutation(
                                                            StringArgumentType.getString(context, "plant"))));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("corner1")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                            + com.teslamaps.features.GreenhousePlanner.corner1()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("corner2")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                            + com.teslamaps.features.GreenhousePlanner.corner2()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("auto")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                            + com.teslamaps.features.GreenhousePlanner.autoPlot()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("focus")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fFocus: §e" + com.teslamaps.features.GreenhousePlanner.focusName()
                                                    + "§7 · /tmap greenhouse focus <plant|all|auto>"));
                                    return 1;
                                })
                                .then(ClientCommands.argument("plant", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                    + com.teslamaps.features.GreenhousePlanner.setFocus(
                                                            StringArgumentType.getString(context, "plant"))));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("stages")
                                .executes(context -> {
                                    for (String line : com.teslamaps.features.GreenhousePlanner.stageReport()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.literal("invert")
                                        .executes(context -> {
                                            TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                            cfg.greenhouseStageInverted = !cfg.greenhouseStageInverted;
                                            TeslaMapsConfig.save();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§b[TeslaMaps] §fStage 1 counts as " + (cfg.greenhouseStageInverted
                                                            ? "§aripe" : "§cyoung")));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("max")
                                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 64))
                                                .executes(context -> {
                                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                                    cfg.greenhouseStageMax = IntegerArgumentType.getInteger(context, "n");
                                                    TeslaMapsConfig.save();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "§b[TeslaMaps] §fStage max: §e" + (cfg.greenhouseStageMax == 0
                                                                    ? "auto" : cfg.greenhouseStageMax)));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("debug")
                                .executes(context -> {
                                    for (String line : com.teslamaps.features.GreenhousePlanner.debugDump()) {
                                        context.getSource().sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("learnplants")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                            + com.teslamaps.features.GreenhousePlanner.learnPlants()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("learn")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                            + com.teslamaps.features.GreenhousePlanner.learn()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("soil")
                                .executes(context -> {
                                    var table = com.teslamaps.features.GreenhousePlanner.soilTable();
                                    if (table.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal(
                                                "§b[TeslaMaps] §7No floor blocks known · /tmap greenhouse learn"));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fFloor blocks:"));
                                        table.forEach((k, v) -> context.getSource().sendFeedback(
                                                Component.literal("§7  " + k + " §8= §a" + v)));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("plant", StringArgumentType.word())
                                        .then(ClientCommands.argument("block", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String plant = StringArgumentType.getString(context, "plant");
                                                    String block = StringArgumentType.getString(context, "block");
                                                    String err = com.teslamaps.features.GreenhousePlanner.setSoil(plant, block);
                                                    context.getSource().sendFeedback(Component.literal(err == null
                                                            ? "§b[TeslaMaps] §f" + plant + " §7floor = §a" + block
                                                            : "§b[TeslaMaps] §c" + err));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("phase")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    cfg.greenhousePhase = (cfg.greenhousePhase + 1) % 3;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fPhase §e"
                                            + com.teslamaps.features.GreenhousePlanner.phaseName(cfg.greenhousePhase)));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    com.teslamaps.features.GreenhousePlanner.clear();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fGreenhouse layout cleared"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("paste")
                                .executes(context -> greenhouseLoad(context.getSource(),
                                        Minecraft.getInstance().keyboardHandler.getClipboard())))
                        .then(ClientCommands.literal("preset")
                                .executes(context -> {
                                    var names = com.teslamaps.features.GreenhousePlanner.presetNames();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fPresets: §e"
                                            + (names.isEmpty() ? "§7none" : String.join("§7, §e", names))
                                            + " §7· preset save|load|delete <name>"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("save")
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                            + com.teslamaps.features.GreenhousePlanner.savePreset(
                                                                    StringArgumentType.getString(context, "name"))));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("load")
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .suggests((c, sb) -> com.teslamaps.features.GreenhousePlanner.suggestPresets(sb))
                                                .executes(context -> {
                                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                            + com.teslamaps.features.GreenhousePlanner.loadPreset(
                                                                    StringArgumentType.getString(context, "name"))));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("delete")
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .suggests((c, sb) -> com.teslamaps.features.GreenhousePlanner.suggestPresets(sb))
                                                .executes(context -> {
                                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                            + com.teslamaps.features.GreenhousePlanner.deletePreset(
                                                                    StringArgumentType.getString(context, "name"))));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("scan")
                                .executes(context -> {
                                    com.teslamaps.features.GreenhousePlanner.rescan();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fRescanning for the 10x10 plot"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("rotate")
                                .then(ClientCommands.literal("auto")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] "
                                                    + com.teslamaps.features.GreenhousePlanner.autoRotate()));
                                            return 1;
                                        }))
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    cfg.greenhouseRotation = (cfg.greenhouseRotation + 1) & 3;
                                    com.teslamaps.features.GreenhousePlanner.pinRotation();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fRotation §e" + (cfg.greenhouseRotation * 90) + "°§7 · the yellow box is planner 0,0"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("flip")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    cfg.greenhouseMirror = !cfg.greenhouseMirror;
                                    com.teslamaps.features.GreenhousePlanner.pinRotation();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fMirrored: §e" + cfg.greenhouseMirror));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("plot")
                                .then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 15))
                                        .executes(context -> {
                                            TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                            cfg.greenhousePlot = IntegerArgumentType.getInteger(context, "n");
                                            com.teslamaps.features.GreenhousePlanner.rebuild();
                                            TeslaMapsConfig.save();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§b[TeslaMaps] §fPlot §e" + cfg.greenhousePlot + "§7/"
                                                            + com.teslamaps.features.GreenhousePlanner.plotCount()));
                                            return 1;
                                        })))
                        .then(ClientCommands.argument("code", StringArgumentType.greedyString())
                                .executes(context -> greenhouseLoad(context.getSource(),
                                        StringArgumentType.getString(context, "code")))))
                .then(ClientCommands.literal("names")
                        .executes(context -> {
                            context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                    "§b[TeslaMaps] §f" + PlayerNameCache.status()
                                            + "§7 · run /g list and /f list to fill it, /tmap names clear to reset"));
                            return 1;
                        })
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    PlayerNameCache.clear();
                                    context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                            "§b[TeslaMaps] §fName list cleared"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("profile")
                        .executes(context -> {
                            context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                    "§b[TeslaMaps] §fActive profile: §a" + com.teslamaps.config.ConfigProfiles.active()
                                            + "§7 · available: " + String.join(", ", com.teslamaps.config.ConfigProfiles.list())));
                            return 1;
                        })
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    com.teslamaps.config.ConfigProfiles.switchTo(name);
                                    context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                            "§b[TeslaMaps] §fSwitched to profile §a" + com.teslamaps.config.ConfigProfiles.active()));
                                    return 1;
                                })))
                .then(sackFill("ep", "ENDER_PEARL", 16))
                .then(sackFill("ij", "INFLATABLE_JERRY", 64))
                .then(sackFill("jerry", "INFLATABLE_JERRY", 64))
                .then(sackFill("sl", "SPIRIT_LEAP", 16))
                .then(sackFill("leap", "SPIRIT_LEAP", 16))
                .then(sackFill("sb", "SUPERBOOM_TNT", 64))
                .then(sackFill("dd", "DUNGEON_DECOY", 64))
                .then(sackFill("tap", "TOXIC_ARROW_POISON", 64))
                .then(sackFill("twap", "TWILIGHT_ARROW_POISON", 64))
                .then(ClientCommands.literal("perf")
                        .executes(context -> perfReport(context.getSource()))
                        .then(ClientCommands.literal("on").executes(context -> {
                            com.teslamaps.debug.Perf.setEnabled(true);
                            context.getSource().sendFeedback(Component.literal(
                                    "§b[TeslaMaps] §fPerf counters §aon§f, counters reset."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("off").executes(context -> {
                            com.teslamaps.debug.Perf.setEnabled(false);
                            context.getSource().sendFeedback(Component.literal(
                                    "§b[TeslaMaps] §fPerf counters §coff§f."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reset").executes(context -> {
                            com.teslamaps.debug.Perf.reset();
                            context.getSource().sendFeedback(Component.literal(
                                    "§b[TeslaMaps] §fPerf counters reset."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("spike")
                                .then(ClientCommands.argument("ms", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000))
                                        .executes(context -> {
                                            int ms = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "ms");
                                            com.teslamaps.debug.Perf.setSpikeThresholdMs(ms);
                                            context.getSource().sendFeedback(Component.literal(ms == 0
                                                    ? "§b[TeslaMaps] §fSpike logging off."
                                                    : "§b[TeslaMaps] §fLogging frames over §e" + ms + "ms§f to latest.log."));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("debug")
                        .executes(context -> {
                            TeslaMapsConfig config = TeslaMapsConfig.get();
                            config.debugMode = !config.debugMode;
                            TeslaMapsConfig.save();
                            context.getSource().sendFeedback(Component.literal(
                                    "Debug mode " + (config.debugMode ? "enabled" : "disabled")));
                            return 1;
                        })
                        .then(ClientCommands.literal("fakelevel")
                                .executes(context -> {
                                    com.teslamaps.features.SkillOverflow.preview("Foraging");
                                    return 1;
                                })
                                .then(ClientCommands.argument("skill", StringArgumentType.word())
                                        .executes(context -> {
                                            com.teslamaps.features.SkillOverflow.preview(
                                                    StringArgumentType.getString(context, "skill"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("fakeimg")
                                .then(ClientCommands.argument("token", StringArgumentType.word())
                                        .executes(context -> {
                                            String token = StringArgumentType.getString(context, "token");
                                            Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.literal(
                                                    "§9Party §8> §b[MVP§c+§b] §fffnn§f: §f"
                                                            + com.teslamaps.features.ImageShare.TAG + " " + token));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("chat")
                                .then(ClientCommands.literal("clipboard")
                                        .executes(context -> com.teslamaps.features.ChatSimulator.send(
                                                Minecraft.getInstance().keyboardHandler.getClipboard(), false))
                                        .then(ClientCommands.literal("silent")
                                                .executes(context -> com.teslamaps.features.ChatSimulator.send(
                                                        Minecraft.getInstance().keyboardHandler.getClipboard(), true))))
                                .then(ClientCommands.literal("silent")
                                        .then(ClientCommands.argument("line", StringArgumentType.greedyString())
                                                .executes(context -> com.teslamaps.features.ChatSimulator.send(
                                                        StringArgumentType.getString(context, "line"), true))))
                                .then(ClientCommands.argument("line", StringArgumentType.greedyString())
                                        .executes(context -> com.teslamaps.features.ChatSimulator.send(
                                                StringArgumentType.getString(context, "line"), false))))
                        .then(ClientCommands.literal("actionbar")
                                .executes(context -> {
                                    boolean on = com.teslamaps.features.ActionBarFilter.toggleDebug();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fAction bar debug " + (on ? "§aon" : "§coff")
                                            + " §7(prints every new action bar + its segments)"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("copy")
                                        .executes(context -> {
                                            String raw = com.teslamaps.features.ActionBarFilter.lastRaw();
                                            Minecraft.getInstance().keyboardHandler.setClipboard(
                                                    com.teslamaps.features.ActionBarFilter.escape(raw));
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§b[TeslaMaps] §fCopied last action bar"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("testcolors")
                                .executes(context -> {
                                    com.teslamaps.debug.LevelPrefixPreview.print(
                                            context.getSource()::sendFeedback, 0);
                                    return 1;
                                })
                                .then(ClientCommands.literal("final")
                                        .executes(context -> {
                                            com.teslamaps.debug.LevelPrefixPreview.printFinal(
                                                    context.getSource()::sendFeedback);
                                            return 1;
                                        }))
                                .then(ClientCommands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000))
                                        .executes(context -> {
                                            com.teslamaps.debug.LevelPrefixPreview.print(
                                                    context.getSource()::sendFeedback,
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("deployables")
                                .executes(context -> {
                                    com.teslamaps.features.Deployables.debugDump();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("deaths")
                                .executes(context -> {
                                    com.teslamaps.debug.DeathProbe.arm(30);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("packets")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(com.teslamaps.debug.PacketLogger.start(30, null)
                                            + "\n§7/tmap debug packets <sec> [name] [full] [in|out] [r=25] [id=123] | stop"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("stop")
                                        .executes(context -> {
                                            com.teslamaps.debug.PacketLogger.stop();
                                            context.getSource().sendFeedback(Component.literal("§fPacket log stopped."));
                                            return 1;
                                        }))
                                .then(ClientCommands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600))
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(com.teslamaps.debug.PacketLogger.start(
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds"), null)));
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("options", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    context.getSource().sendFeedback(Component.literal(com.teslamaps.debug.PacketLogger.start(
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds"),
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "options"))));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("particles")
                                .executes(context -> {
                                    com.teslamaps.features.HotspotWatcher.startCensus(10);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("dragon")
                                .executes(context -> {
                                    com.teslamaps.dungeon.WitherDragons.debugDump();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("map")
                                .executes(context -> {
                                    com.teslamaps.scanner.MapScanner.debugDump();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("fish")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7Sea creature messages: §f" + com.teslamaps.utils.SeaCreatureList.size()
                                            + " §7(" + (com.teslamaps.utils.SeaCreatureList.ready() ? "§aloaded" : "§eloading") + "§7)"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("sacks")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7Sack list: §f" + (com.teslamaps.utils.SackItems.ready() ? "§aloaded" : "§eloading")
                                            + " §7| item table: §f" + (com.teslamaps.utils.SkyblockItems.loaded() ? "§aloaded" : "§eloading")
                                            + " §7| /gfs suggestions: §f" + com.teslamaps.utils.SackItems.suggestions().size()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("area")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "§7Area: §f\"" + com.teslamaps.utils.SkyblockUtils.getArea() + "\""
                                            + " §7foraging island: "
                                            + (com.teslamaps.features.foraging.Foraging.onForagingIsland() ? "§ayes" : "§cno")));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("sound")
                                .executes(context -> {
                                    TeslaMapsConfig c = TeslaMapsConfig.get();
                                    c.soundDebug = !c.soundDebug;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            "Sound debug: " + (c.soundDebug ? "§aON" : "§cOFF")));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("croesus")
                                .executes(context -> {
                                    TeslaMapsConfig c = TeslaMapsConfig.get();
                                    c.croesusDebug = !c.croesusDebug;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            "Croesus debug: " + (c.croesusDebug ? "§aON" : "§cOFF")));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("core")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null || mc.level == null) {
                                        context.getSource().sendFeedback(Component.literal("Not in world"));
                                        return 0;
                                    }
                                    int x = (int) mc.player.getX();
                                    int z = (int) mc.player.getZ();

                                    int[] gridPos = ComponentGrid.worldToGrid(x, z);
                                    if (gridPos == null) {
                                        context.getSource().sendFeedback(Component.literal("Not in dungeon area"));
                                        return 0;
                                    }

                                    int[] center = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
                                    int core = CoreHasher.calculateCore(mc.level, center[0], center[1]);

                                    context.getSource().sendFeedback(Component.literal(
                                            String.format("Grid: [%d,%d] Center: [%d,%d] Core: %d",
                                                    gridPos[0], gridPos[1], center[0], center[1], core)));

                                    var roomData = RoomDatabase.getInstance().findByCore(core);
                                    if (roomData != null) {
                                        context.getSource().sendFeedback(Component.literal("Matched room: " + roomData.getName()));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("No matching room in database"));
                                    }

                                    return 1;
                                }))
                        .then(ClientCommands.literal("doors")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("=== Door Debug ==="));
                                    var allDoors = DoorScanner.getAllDoors();
                                    context.getSource().sendFeedback(Component.literal("Total doors found: " + allDoors.size()));
                                    int normal = 0, wither = 0, blood = 0, entrance = 0;
                                    for (var entry : allDoors.entrySet()) {
                                        DoorType type = entry.getValue();
                                        switch (type) {
                                            case NORMAL -> normal++;
                                            case WITHER -> wither++;
                                            case BLOOD -> blood++;
                                            case ENTRANCE -> entrance++;
                                        }
                                    }
                                    context.getSource().sendFeedback(Component.literal("Normal: " + normal + ", Wither: " + wither + ", Blood: " + blood + ", Entrance: " + entrance));
                                    context.getSource().sendFeedback(Component.literal("Rescanning doors..."));
                                    DoorScanner.scanAllDoors();
                                    context.getSource().sendFeedback(Component.literal("After rescan: " + DoorScanner.getAllDoors().size() + " doors"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("rooms")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("=== Room Debug ==="));
                                    for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                        StringBuilder sb = new StringBuilder();
                                        sb.append(room.getName()).append(" [");
                                        for (int[] comp : room.getComponents()) {
                                            sb.append("(").append(comp[0]).append(",").append(comp[1]).append(")");
                                        }
                                        sb.append("] ").append(room.getCheckmarkState());
                                        context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("mapitem")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    context.getSource().sendFeedback(Component.literal("=== Map Item Debug ==="));

                                    for (int i = 0; i < 9; i++) {
                                        ItemStack stack = mc.player.getInventory().getItem(i);
                                        if (stack.getItem() instanceof MapItem) {
                                            MapId mapId = stack.get(DataComponents.MAP_ID);
                                            context.getSource().sendFeedback(Component.literal("Slot " + i + ": Map found, ID=" + mapId));
                                            if (mapId != null) {
                                                MapItemSavedData state = MapItem.getSavedData(mapId, mc.level);
                                                if (state != null && state.colors != null) {
                                                    context.getSource().sendFeedback(Component.literal("  MapState: colors length=" + state.colors.length));
                                                    byte c1 = state.colors[64 + 64*128]; // center
                                                    byte c2 = state.colors[32 + 32*128]; // top-left area
                                                    context.getSource().sendFeedback(Component.literal("  Sample colors: center=" + c1 + ", topleft=" + c2));
                                                } else {
                                                    context.getSource().sendFeedback(Component.literal("  MapState is null or has no colors"));
                                                }
                                            }
                                        }
                                    }

                                    ItemStack offhand = mc.player.getOffhandItem();
                                    if (offhand.getItem() instanceof MapItem) {
                                        context.getSource().sendFeedback(Component.literal("Offhand: Map found"));
                                    }

                                    return 1;
                                }))
                        .then(ClientCommands.literal("mobs")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    context.getSource().sendFeedback(Component.literal("=== Nearby Entity Names ==="));
                                    context.getSource().sendFeedback(Component.literal("ESP list: " + TeslaMapsConfig.get().customESPMobs));

                                    int count = 0;
                                    for (Entity entity : mc.level.entitiesForRendering()) {
                                        if (!(entity instanceof LivingEntity) && entity.getCustomName() == null
                                                && !(entity instanceof net.minecraft.world.entity.Display)) continue;
                                        if (entity == mc.player) continue;

                                        double dist = entity.distanceToSqr(mc.player);
                                        if (dist > 400) continue;  // Within 20 blocks

                                        String name = entity.getName().getString();
                                        String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "null";

                                        boolean matchesESP = false;
                                        for (String pattern : TeslaMapsConfig.get().customESPMobs) {
                                            if (name.toLowerCase().contains(pattern.toLowerCase()) ||
                                                (entity.getCustomName() != null && customName.toLowerCase().contains(pattern.toLowerCase()))) {
                                                matchesESP = true;
                                                break;
                                            }
                                        }

                                        StringBuilder hex = new StringBuilder();
                                        for (char c : name.toCharArray()) {
                                            if (c > 127) {
                                                hex.append(String.format("[U+%04X]", (int) c));
                                            }
                                        }

                                        String espStatus = matchesESP ? " [ESP MATCH]" : "";
                                        context.getSource().sendFeedback(Component.literal(
                                                String.format("%.1f: %s | custom: %s %s%s",
                                                        Math.sqrt(dist), name, customName, hex.toString(), espStatus)));
                                        count++;
                                        if (count > 20) {
                                            context.getSource().sendFeedback(Component.literal("... and more"));
                                            break;
                                        }
                                    }

                                    if (count == 0) {
                                        context.getSource().sendFeedback(Component.literal("No entities nearby"));
                                    }

                                    return 1;
                                }))
                        .then(ClientCommands.literal("crypts")
                                .executes(context -> {
                                    int cryptsFound = TabListUtils.getCryptsFound();
                                    int totalCrypts = DungeonManager.getTotalCrypts();
                                    context.getSource().sendFeedback(Component.literal("=== Crypts Info ==="));
                                    if (cryptsFound >= 0) {
                                        context.getSource().sendFeedback(Component.literal("Crypts: " + cryptsFound + "/" + totalCrypts + " (5 for S+)"));
                                        if (cryptsFound >= 5) {
                                            context.getSource().sendFeedback(Component.literal(" Crypt bonus achieved!"));
                                        } else {
                                            context.getSource().sendFeedback(Component.literal("Need " + (5 - cryptsFound) + " more for bonus"));
                                        }
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("Total crypts in dungeon: " + totalCrypts));
                                    }
                                    java.util.Set<DungeonRoom> shown = new java.util.HashSet<>();
                                    for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                        if (!shown.contains(room) && room.getCrypts() > 0) {
                                            context.getSource().sendFeedback(Component.literal("  " + room.getName() + ": " + room.getCrypts()));
                                            shown.add(room);
                                        }
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("secrets")
                                .executes(context -> {
                                    double secretsPercent = TabListUtils.getSecretsPercentage();
                                    int totalSecrets = 0;
                                    for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                        if (room.getSecrets() > 0) {
                                            totalSecrets += room.getSecrets();
                                        }
                                    }
                                    context.getSource().sendFeedback(Component.literal("=== Secrets Info ==="));
                                    if (secretsPercent >= 0) {
                                        context.getSource().sendFeedback(Component.literal(String.format("Secrets found: %.1f%%", secretsPercent)));
                                        context.getSource().sendFeedback(Component.literal("Total secrets in map: " + totalSecrets));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("Total secrets in map: " + totalSecrets));
                                        context.getSource().sendFeedback(Component.literal("Found count: Not in dungeon"));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("scanblocks")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null || mc.level == null) {
                                        context.getSource().sendFeedback(Component.literal("Not in world"));
                                        return 0;
                                    }

                                    int px = (int) mc.player.getX();
                                    int py = (int) mc.player.getY();
                                    int pz = (int) mc.player.getZ();

                                    context.getSource().sendFeedback(Component.literal("=== Block Scan at " + px + ", " + py + ", " + pz + " ==="));

                                    Map<String, Integer> blockCounts = new HashMap<>();
                                    int radius = 3;

                                    int smoothSlabCount = 0;
                                    for (int x = px - radius; x <= px + radius; x++) {
                                        for (int y = py - radius; y <= py + radius; y++) {
                                            for (int z = pz - radius; z <= pz + radius; z++) {
                                                BlockPos pos = new BlockPos(x, y, z);
                                                Block block = mc.level.getBlockState(pos).getBlock();
                                                String name = block.getName().getString();
                                                blockCounts.merge(name, 1, Integer::sum);
                                                if (name.contains("Smooth Stone Slab")) {
                                                    smoothSlabCount++;
                                                }
                                            }
                                        }
                                    }

                                    blockCounts.entrySet().stream()
                                            .filter(e -> !e.getKey().equals("Air"))
                                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                                            .limit(15)
                                            .forEach(e -> context.getSource().sendFeedback(
                                                    Component.literal("  " + e.getValue() + "x " + e.getKey())));

                                    context.getSource().sendFeedback(Component.literal("Smooth slabs in area: " + smoothSlabCount + " (need 10+ for crypt)"));

                                    BlockPos below = new BlockPos(px, py - 1, pz);
                                    Block blockBelow = mc.level.getBlockState(below).getBlock();
                                    context.getSource().sendFeedback(Component.literal("Standing on: " + blockBelow.getName().getString()));

                                    return 1;
                                }))
                        .then(ClientCommands.literal("entities")
                                .executes(context -> dumpEntities(context.getSource(), 8))
                                .then(ClientCommands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 48))
                                        .executes(context -> dumpEntities(context.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius")))))
                        .then(ClientCommands.literal("status")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("=== TeslaMaps Status ==="));
                                    context.getSource().sendFeedback(Component.literal("In Dungeon: " + DungeonManager.isInDungeon()));
                                    context.getSource().sendFeedback(Component.literal("State: " + DungeonManager.getCurrentState()));
                                    context.getSource().sendFeedback(Component.literal("Floor: " + DungeonManager.getCurrentFloor()));
                                    context.getSource().sendFeedback(Component.literal("Rooms loaded: " + DungeonManager.getGrid().getAllRooms().size()));
                                    context.getSource().sendFeedback(Component.literal("DB loaded: " + RoomDatabase.getInstance().isLoaded()));
                                    context.getSource().sendFeedback(Component.literal("DB rooms: " + RoomDatabase.getInstance().getAllRooms().size()));
                                    context.getSource().sendFeedback(Component.literal("Packet-hidden spawns: "
                                            + com.teslamaps.features.PacketHiders.cancelledCount()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("rescan")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("Forcing dungeon scan..."));
                                    RoomScanner.forceScan();
                                    int roomCount = DungeonManager.getGrid().getAllRooms().size();
                                    context.getSource().sendFeedback(Component.literal("Scan complete. Found " + roomCount + " rooms."));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("dumptab")
                                .executes(context -> {
                                    com.teslamaps.utils.GuiDump.dumpTab();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("dumptooltip")
                                .executes(context -> {
                                    com.teslamaps.utils.GuiDump.dumpTooltip();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("dumpgui")
                                .executes(context -> {
                                    com.teslamaps.utils.GuiDump.dump(null);
                                    return 1;
                                })
                                .then(ClientCommands.argument("match", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            com.teslamaps.utils.GuiDump.dump(
                                                    StringArgumentType.getString(context, "match"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("testdrop")
                                .executes(context -> {
                                    String shown = com.teslamaps.features.RareDropEffects.test(null);
                                    context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                            "§b[TeslaMaps] §fDrop effect: §a" + shown));
                                    return 1;
                                })
                                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String shown = com.teslamaps.features.RareDropEffects.test(
                                                    StringArgumentType.getString(context, "item"));
                                            context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                                    "§b[TeslaMaps] §fDrop effect: §a" + shown));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("esp")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.EspListScreen()));
                            return 1;
                        })
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> suggest(nearbyEntityNames(), builder))
                                        .executes(context -> {
                                            String raw = StringArgumentType.getString(context, "name");
                                            TeslaMapsConfig config = TeslaMapsConfig.get();

                                            // The name is greedy, so a trailing on/off is split off here
                                            // rather than being its own argument.
                                            boolean tracer = false;
                                            String name = raw;
                                            if (name.toLowerCase().endsWith(" on")) {
                                                tracer = true;
                                                name = name.substring(0, name.length() - 3).trim();
                                            } else if (name.toLowerCase().endsWith(" off")) {
                                                name = name.substring(0, name.length() - 4).trim();
                                            }
                                            if (name.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("Usage: /tmap add <name> [on]"));
                                                return 1;
                                            }

                                            final String entry = name;
                                            boolean exists = config.customESPMobs.stream()
                                                    .anyMatch(s -> s.equalsIgnoreCase(entry));
                                            if (!exists) config.customESPMobs.add(entry);

                                            config.customESPTracers.removeIf(s -> s.equalsIgnoreCase(entry));
                                            if (tracer) config.customESPTracers.add(entry);
                                            TeslaMapsConfig.save();

                                            String suffix = tracer ? " (tracer on)" : "";
                                            context.getSource().sendFeedback(Component.literal(exists
                                                    ? "Updated '" + entry + "'" + suffix
                                                    : "Added '" + entry + "' to ESP list" + suffix));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests((context, builder) ->
                                                suggest(TeslaMapsConfig.get().customESPMobs, builder))
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            TeslaMapsConfig config = TeslaMapsConfig.get();

                                            boolean removed = config.customESPMobs.removeIf(s -> s.equalsIgnoreCase(name));
                                            config.customESPTracers.removeIf(s -> s.equalsIgnoreCase(name));

                                            if (removed) {
                                                TeslaMapsConfig.save();
                                                context.getSource().sendFeedback(Component.literal("Removed '" + name + "' from ESP list"));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal("'" + name + "' not found in ESP list"));
                                            }
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("list")
                                .executes(context -> {
                                    TeslaMapsConfig config = TeslaMapsConfig.get();
                                    if (config.customESPMobs.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("ESP list is empty"));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("=== Custom ESP List ==="));
                                        for (String mob : config.customESPMobs) {
                                            context.getSource().sendFeedback(Component.literal("  - " + mob));
                                        }
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    TeslaMapsConfig config = TeslaMapsConfig.get();
                                    int count = config.customESPMobs.size();
                                    config.customESPMobs.clear();
                                    config.customESPTracers.clear();
                                    config.customESPColors.clear();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("Cleared " + count + " entries from ESP list"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("hotkeys")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.KeybindMessageScreen()));
                            return 1;
                        })
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String msg = StringArgumentType.getString(context, "message");
                                    TeslaMapsConfig.get().keybindChatMessage = msg;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("Keybind message set to: " + msg));
                                    return 1;
                                })))
                .then(ClientCommands.literal("shortcut")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.ShortcutScreen()));
                            return 1;
                        }))
                .then(ClientCommands.literal("waypoints")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.WaypointRoutesScreen()));
                            return 1;
                        })
                        .then(ClientCommands.literal("reload")
                                .executes(context -> {
                                    com.teslamaps.dungeon.DungeonWaypoints.load();
                                    context.getSource().sendFeedback(Component.literal("Reloaded dungeon waypoints from config/teslamaps/dungeon_waypoints.json"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("debug")
                                .executes(context -> {
                                    com.teslamaps.dungeon.DungeonWaypoints.debug();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("packs")
                                .executes(context -> {
                                    Minecraft.getInstance().schedule(() ->
                                            Minecraft.getInstance().setScreen(new com.teslamaps.screen.WaypointPackScreen(null)));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("edit")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.toggleEditMode()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("add")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.addAtTarget(
                                                    TeslaMapsConfig.parseColor(cfg.waypointAddColor), cfg.waypointAddFilled, cfg.waypointAddThroughWalls)));
                                    return 1;
                                })
                                .then(ClientCommands.argument("color", StringArgumentType.word())
                                        .executes(context -> {
                                            TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                            int color = parseHexColor(StringArgumentType.getString(context, "color"));
                                            context.getSource().sendFeedback(Component.literal(
                                                    com.teslamaps.dungeon.DungeonWaypoints.addAtTarget(color, cfg.waypointAddFilled, cfg.waypointAddThroughWalls)));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("remove")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.removeNearest()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.clearRoom()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("convert")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.convertLegacy()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("title")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.DungeonWaypoints.editTarget(null, null, true)));
                                    return 1;
                                })
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    com.teslamaps.dungeon.DungeonWaypoints.editTarget(
                                                            StringArgumentType.getString(context, "text"), null, true)));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("type")
                                .then(ClientCommands.argument("type", StringArgumentType.word())
                                        .suggests((c, sb) -> {
                                            for (String t : com.teslamaps.dungeon.DungeonWaypoints.TYPES) sb.suggest(t.toLowerCase());
                                            return sb.buildFuture();
                                        })
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    com.teslamaps.dungeon.DungeonWaypoints.editTarget(
                                                            null, StringArgumentType.getString(context, "type"), false)));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("head")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "§b[TeslaMaps] §fHead ESP: §e/tmap head add <name> [hash] §7| §elist §7| " +
                                            "§eremove <name> §7| §eclear §7| §eprobe [radius]"));
                            return 1;
                        })
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> addHead(context.getSource(),
                                                StringArgumentType.getString(context, "name"), null))
                                        .then(ClientCommands.argument("hash", StringArgumentType.word())
                                                .executes(context -> addHead(context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "hash"))))))
                        .then(ClientCommands.literal("list")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    if (cfg.headTargets.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("§7No heads added yet"));
                                        return 1;
                                    }
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §f" + cfg.headTargets.size() + " head(s):"));
                                    for (TeslaMapsConfig.HeadTarget t : cfg.headTargets) {
                                        context.getSource().sendFeedback(Component.literal(
                                                "§7- §f" + t.name + " §8" + t.hash + " §7" + t.color));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(TMapCommand::suggestHeads)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                            boolean removed = cfg.headTargets.removeIf(t -> t.name.equalsIgnoreCase(name));
                                            if (removed) TeslaMapsConfig.save();
                                            context.getSource().sendFeedback(Component.literal(removed
                                                    ? "§7Removed head §f" + name
                                                    : "§cNo head named " + name));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    TeslaMapsConfig cfg = TeslaMapsConfig.get();
                                    int n = cfg.headTargets.size();
                                    cfg.headTargets.clear();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("§7Cleared " + n + " head(s)"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("probe")
                                .executes(context -> probeHeads(context.getSource(), 16))
                                .then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(context -> probeHeads(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "radius"))))))
                .then(ClientCommands.literal("route")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.WaypointRoutesScreen()));
                            return 1;
                        })
                        .then(ClientCommands.literal("load")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.load("")))
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests(TMapCommand::suggestRoutes)
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.load(StringArgumentType.getString(context, "name"))))))
                        .then(ClientCommands.literal("unload")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.unload())))
                        .then(ClientCommands.literal("save")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.save(StringArgumentType.getString(context, "name"))))))
                        .then(ClientCommands.literal("erase")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests(TMapCommand::suggestRoutes)
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.erase(StringArgumentType.getString(context, "name"))))))
                        .then(ClientCommands.literal("list")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.list())))
                        .then(ClientCommands.literal("add")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.addHere()))
                                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.add(IntegerArgumentType.getInteger(context, "number"))))))
                        .then(ClientCommands.literal("remove")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.removeNearest())))
                        .then(ClientCommands.literal("delete")
                                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.delete(IntegerArgumentType.getInteger(context, "number"))))))
                        .then(ClientCommands.literal("skip")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.skip(1)))
                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.skip(IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(ClientCommands.literal("unskip")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.unskip(1)))
                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.unskip(IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(ClientCommands.literal("skipto")
                                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.skipTo(IntegerArgumentType.getInteger(context, "number"))))))
                        .then(ClientCommands.literal("export")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.export("coleweight")))
                                .then(ClientCommands.argument("format", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String n : com.teslamaps.features.waypoints.RouteFormats.names()) b.suggest(n);
                                            return b.buildFuture();
                                        })
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.export(StringArgumentType.getString(context, "format"))))))
                        .then(ClientCommands.literal("exportall")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.exportAll("skyblocker")))
                                .then(ClientCommands.argument("format", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("skytils");
                                            b.suggest("skyblocker");
                                            return b.buildFuture();
                                        })
                                        .executes(context -> routeFeedback(context,
                                                com.teslamaps.features.waypoints.OrderedWaypoints.exportAll(StringArgumentType.getString(context, "format"))))))
                        .then(ClientCommands.literal("exportfile")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.exportFile())))
                        .then(ClientCommands.literal("setup")
                                .executes(context -> routeFeedback(context, com.teslamaps.features.waypoints.OrderedWaypoints.toggleSetup()))))
                .then(ClientCommands.literal("pest")
                        .executes(context -> {
                            com.teslamaps.features.PestRoutine.advance();
                            return 1;
                        })
                        .then(ClientCommands.literal("reset")
                                .executes(context -> {
                                    com.teslamaps.features.PestRoutine.reset();
                                    context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fPest routine reset"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("slots")
                                .executes(context -> {
                                    com.teslamaps.features.PestRoutine.dumpSlots();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("start")
                                .executes(context -> {
                                    com.teslamaps.features.PestRoutine.start();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fRoutine armed at /loadout"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("skipspray")
                                .executes(context -> {
                                    com.teslamaps.features.PestRoutine.skipSpray();
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00a7b[TeslaMaps] \u00a7fSkipped the spray trip"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("skip")
                                .executes(context -> {
                                    com.teslamaps.features.PestRoutine.skipPhilip();
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00a7b[TeslaMaps] \u00a7fSkipped the Philip step"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("farm")
                        .then(ClientCommands.literal("pos1")
                                .executes(context -> {
                                    com.teslamaps.features.FarmProgress.setPoint(1);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("pos2")
                                .executes(context -> {
                                    com.teslamaps.features.FarmProgress.setPoint(2);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("detect")
                                .executes(context -> {
                                    com.teslamaps.features.FarmProgress.toggleDetection();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    com.teslamaps.features.FarmProgress.clear();
                                    return 1;
                                })))
                .then(ClientCommands.literal("profit")
                        .executes(context -> {
                            com.teslamaps.features.FarmProfit.printBreakdown();
                            return 1;
                        })
                        .then(ClientCommands.literal("exclude")
                                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String item = StringArgumentType.getString(context, "item");
                                            boolean ex = com.teslamaps.features.FarmProfit.toggleExcluded(item);
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§b[TeslaMaps] §f" + item + (ex ? " §cexcluded" : " §acounted again")));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("window")
                                .executes(context -> {
                                    com.teslamaps.features.FarmProfit.cycleWindow();
                                    context.getSource().sendFeedback(Component.literal(
                                            "§b[TeslaMaps] §fProfit window: §e"
                                                    + com.teslamaps.features.FarmProfit.window().label));
                                    return 1;
                                })))
                .then(ClientCommands.literal("feast")
                        .then(ClientCommands.literal("fetch")
                                .executes(context -> {
                                    com.teslamaps.features.HarvestFeast.fetch(true);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("share")
                                .executes(context -> {
                                    com.teslamaps.features.HarvestFeast.share();
                                    return 1;
                                })))
                .then(ClientCommands.literal("mouselock")
                        .executes(context -> {
                            com.teslamaps.features.FarmingSensitivity.toggleLock();
                            return 1;
                        }))
                .then(ClientCommands.literal("tppest")
                        .executes(context -> {
                            com.teslamaps.features.GardenPlots.teleportToPest();
                            return 1;
                        }))
                .then(ClientCommands.literal("prince")
                        .executes(context -> {
                            com.teslamaps.dungeon.PrinceWaypoints.load();
                            context.getSource().sendFeedback(Component.literal("Reloaded prince waypoints from config/teslamaps/prince_waypoints.json"));
                            return 1;
                        })
                        .then(ClientCommands.literal("mark")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.PrinceWaypoints.mark()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            com.teslamaps.dungeon.PrinceWaypoints.clearRoom()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("crown")
                                .then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 5))
                                        .executes(context -> {
                                            int n = IntegerArgumentType.getInteger(context, "n");
                                            TeslaMapsConfig.get().princeCrownVariant = n;
                                            TeslaMapsConfig.save();
                                            context.getSource().sendFeedback(Component.literal(
                                                    n == 0 ? "Prince crown: drawn icon" : "Prince crown: image " + n));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("actionbar")
                        .executes(context -> {
                            TeslaMapsConfig c = TeslaMapsConfig.get();
                            context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fAction bar filter: "
                                    + (c.actionBarFilter ? "§aon" : "§coff")));
                            context.getSource().sendFeedback(Component.literal("§7Custom hidden: §f"
                                    + (c.actionBarHidden.isEmpty() ? "(none)" : String.join(", ", c.actionBarHidden))));
                            context.getSource().sendFeedback(Component.literal(
                                    "§7/tmap actionbar <text> toggles a substring · clear · /tmap debug actionbar"));
                            return 1;
                        })
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    TeslaMapsConfig.get().actionBarHidden.clear();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("Cleared action bar filters"));
                                    return 1;
                                }))
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> {
                                    TeslaMapsConfig c = TeslaMapsConfig.get();
                                    String text = StringArgumentType.getString(context, "text").trim();
                                    if (c.actionBarHidden.remove(text)) {
                                        context.getSource().sendFeedback(Component.literal("§cShowing again §f" + text));
                                    } else {
                                        c.actionBarHidden.add(text);
                                        c.actionBarFilter = true;
                                        context.getSource().sendFeedback(Component.literal("§aHiding §f" + text));
                                    }
                                    TeslaMapsConfig.save();
                                    return 1;
                                })))
                .then(com.teslamaps.features.ChatUserFilters.command())
                .then(ClientCommands.literal("mutesound")
                        .executes(context -> {
                            TeslaMapsConfig c = TeslaMapsConfig.get();
                            context.getSource().sendFeedback(Component.literal("Muted sounds: "
                                    + (c.mutedSounds.isEmpty() ? "(none)" : String.join(", ", c.mutedSounds))));
                            return 1;
                        })
                        .then(ClientCommands.literal("clear")
                                .executes(context -> {
                                    TeslaMapsConfig.get().mutedSounds.clear();
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("Cleared muted sounds"));
                                    return 1;
                                }))
                        .then(ClientCommands.argument("id", StringArgumentType.greedyString())
                                .executes(context -> {
                                    TeslaMapsConfig c = TeslaMapsConfig.get();
                                    String id = StringArgumentType.getString(context, "id").trim();
                                    if (c.mutedSounds.remove(id)) context.getSource().sendFeedback(Component.literal("§cUnmuted §f" + id));
                                    else { c.mutedSounds.add(id); context.getSource().sendFeedback(Component.literal("§aMuted §f" + id)); }
                                    TeslaMapsConfig.save();
                                    return 1;
                                })))
                .then(ClientCommands.literal("slayerpb")
                        .executes(context -> {
                            com.teslamaps.slayer.SlayerTimer.printPbs();
                            return 1;
                        })
                        .then(ClientCommands.literal("reset")
                                .executes(context -> {
                                    com.teslamaps.slayer.SlayerTimer.reset();
                                    context.getSource().sendFeedback(Component.literal("Slayer PBs cleared"));
                                    return 1;
                                })))
                .then(ClientCommands.literal("reset")
                        .executes(context -> {
                            com.teslamaps.slayer.SlayerHUD.resetSession();
                            com.teslamaps.features.SkillTracker.reset();
                            context.getSource().sendFeedback(Component.literal("Tracker reset"));
                            return 1;
                        }))
                .then(ClientCommands.literal("sendcoords")
                        .executes(context -> sendCoords(context.getSource(), ""))
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> sendCoords(context.getSource(),
                                        StringArgumentType.getString(context, "message")))))
        );

    }

    private static int sendCoords(FabricClientCommandSource source, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            source.sendFeedback(Component.literal("Not in world"));
            return 0;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        var p = mc.player.blockPosition();
        String area = c.chatWaypointShareArea ? com.teslamaps.utils.SkyblockUtils.getArea() : "";
        String msg = message == null ? "" : message.trim();
        String text = "x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ()
                + (area.isEmpty() ? "" : " | " + area + (msg.isEmpty() ? "" : " |"))
                + (msg.isEmpty() ? "" : " " + msg);
        String prefix = switch (c.sendCoordsChannel == null ? "Party" : c.sendCoordsChannel) {
            case "Current" -> null;
            case "All" -> "ac ";
            case "Guild" -> "gc ";
            case "Coop" -> "cc ";
            default -> "pc ";
        };
        if (prefix == null) mc.getConnection().sendChat(text);
        else mc.getConnection().sendCommand(prefix + text);
        return 1;
    }

    private static final java.util.Set<String> RESERVED_ALIASES = java.util.Set.of("tmap", "ping");

    public static String expandShortcut(String command) {
        if (command == null || command.isEmpty()) return command;
        int sp = command.indexOf(' ');
        String alias = (sp < 0 ? command : command.substring(0, sp)).trim();
        String args = sp < 0 ? "" : command.substring(sp + 1);
        if (alias.isEmpty()) return command;
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) return lowercased(command, alias, args);

        for (TeslaMapsConfig.Shortcut sc : TeslaMapsConfig.get().shortcuts) {
            if (sc.alias == null || sc.command == null) continue;
            if (!sc.alias.trim().equalsIgnoreCase(alias)) continue;
            String expanded = sc.command.trim();
            if (expanded.indexOf(';') >= 0 && com.teslamaps.features.CommandChains.active())
                expanded = expanded.substring(0, expanded.indexOf(';')).trim();
            if (expanded.isEmpty()) continue;
            return args.isEmpty() ? expanded : expanded + " " + args;
        }
        return warpShortcut(lowercased(command, alias, args));
    }

    private static String lowercased(String command, String alias, String args) {
        if (!TeslaMapsConfig.get().commandLowercase || !TeslaMapsConfig.get().section("Chat")) return command;
        String low = alias.toLowerCase(java.util.Locale.ROOT);
        if (low.equals(alias)) return command;
        return args.isEmpty() ? low : low + " " + args;
    }

    // SkyHanni ShortenWarpCommand: /crimson -> /warp crimson
    private static String warpShortcut(String command) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.warpShortcuts || !c.section("Chat") || !com.teslamaps.utils.SkyblockUtils.isInSkyblock()) return command;
        String name = command.trim().toLowerCase(java.util.Locale.ROOT);
        if (name.isEmpty() || name.indexOf(' ') >= 0) return command;
        if (name.equals("jerry") && com.teslamaps.utils.SkyblockUtils.isInArea("Private Island")) return command;
        if (name.equals("barn") && com.teslamaps.utils.SkyblockUtils.isInGarden()) return command;
        return HypixelSuggestions.warps().contains(name) ? "warp " + name : command;
    }

    /** The same tree for /pv and /tmap pv. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> profileViewer(String name) {
        return ClientCommands.literal(name)
                .executes(context -> openProfileViewer(context.getSource(), null))
                .then(ClientCommands.literal("dump")
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String player = StringArgumentType.getString(context, "player");
                                    context.getSource().sendFeedback(Component.literal("§7Dumping " + player + "..."));
                                    com.teslamaps.profileviewer.ProfileDump.dump(player);
                                    return 1;
                                })))
                .then(ClientCommands.literal("fake")
                        .executes(context -> {
                            com.teslamaps.profileviewer.FakeProfile.open();
                            return 1;
                        }))
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggest(PlayerNameCache.names(), builder))
                        .executes(context -> openProfileViewer(context.getSource(),
                                StringArgumentType.getString(context, "player"))));
    }

    private static int openProfileViewer(FabricClientCommandSource source, String player) {
        Minecraft mc = Minecraft.getInstance();
        String name = player == null || player.isBlank() ? mc.getUser().getName() : player.trim();
        com.teslamaps.profileviewer.screen.ProfileViewerScreen.open(name);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggest(
            java.util.Collection<String> options, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(remaining)) builder.suggest(option);
        }
        return builder.buildFuture();
    }

    private static int routeFeedback(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                                     String message) {
        context.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §r" + message));
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestRoutes(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return suggest(com.teslamaps.features.waypoints.RouteStorage.names(), builder);
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestHeads(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (TeslaMapsConfig.HeadTarget t : TeslaMapsConfig.get().headTargets) names.add(t.name);
        return suggest(names, builder);
    }

    private static int addHead(FabricClientCommandSource source, String name, String hash) {
        String label = "manual";
        if (hash == null) {
            com.teslamaps.debug.HeadProbe.Grab grab = com.teslamaps.debug.HeadProbe.grab();
            if (grab == null) {
                source.sendFeedback(Component.literal(
                        "§cNo head found - look at a skull block, stand next to the head, or hold it. "
                                + "§7Try §e/tmap head probe"));
                return 0;
            }
            hash = grab.hash();
            label = grab.label();
        }
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String finalHash = hash;
        cfg.headTargets.removeIf(t -> t.name.equalsIgnoreCase(name) || finalHash.equalsIgnoreCase(t.hash));
        cfg.headTargets.add(new TeslaMapsConfig.HeadTarget(name, hash, cfg.headESPColor));
        TeslaMapsConfig.save();
        source.sendFeedback(Component.literal("§7Added head §f" + name + " §8(" + label + ") §b" + hash
                + (cfg.headESP ? "" : " §7- turn on §eHead ESP§7 in /tmap")));
        return 1;
    }

    private static int probeHeads(FabricClientCommandSource source, int radius) {
        java.util.List<String> lines = com.teslamaps.debug.HeadProbe.dump(radius);
        source.sendFeedback(Component.literal("§b[TeslaMaps] §fHeads within " + radius + " blocks:"));
        int shown = 0;
        for (String line : lines) {
            source.sendFeedback(Component.literal(line));
            if (++shown >= 40) {
                source.sendFeedback(Component.literal("§7... " + (lines.size() - shown) + " more"));
                break;
            }
        }
        return 1;
    }

    /** Names of living entities within 20 blocks, for `/tmap add` completion. */
    private static java.util.List<String> nearbyEntityNames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return java.util.List.of();

        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == mc.player) continue;
            if (entity.distanceToSqr(mc.player) > 400) continue;

            if (entity.getCustomName() != null) {
                // Hypixel name tags carry literal color codes even after getString().
                String custom = entity.getCustomName().getString()
                        .replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
                if (!custom.isEmpty()) names.add(custom);
            }
            names.add(entity.getName().getString());
            if (names.size() > 60) break;
        }
        return new java.util.ArrayList<>(names);
    }

    private static int parseHexColor(String hex) {
        hex = hex.replace("#", "");
        try {
            long v = Long.parseLong(hex, 16);
            if (hex.length() == 8) { // RRGGBBAA -> ARGB
                int r = (int) ((v >> 24) & 0xFF), g = (int) ((v >> 16) & 0xFF), b = (int) ((v >> 8) & 0xFF), a = (int) (v & 0xFF);
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
            return 0xFF000000 | (int) (v & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return 0xFF55FFFF;
        }
    }

    // an exact match keeps the popup open and Enter then applies it instead of sending
    private static java.util.concurrent.CompletableFuture<Suggestions> suggestMobs(SuggestionsBuilder builder,
                                                                                   boolean tracked) {
        String typed = builder.getRemaining();
        String lower = typed.toLowerCase();
        for (String name : com.teslamaps.features.BestiaryTracker.suggestions(tracked)) {
            if (name.equalsIgnoreCase(typed)) continue;
            if (name.toLowerCase().startsWith(lower)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static int repoStatus(FabricClientCommandSource source) {
        var list = com.teslamaps.utils.RepoCache.status();
        if (list.isEmpty()) {
            source.sendFeedback(Component.literal("§7[TeslaMaps] No repo file loaded yet"));
            return 1;
        }
        source.sendFeedback(Component.literal("§6[TeslaMaps] Repo data:"));
        long now = System.currentTimeMillis();
        for (var s : list) {
            String color = switch (s.source()) {
                case "live" -> "§a";
                case "cache" -> "§e";
                default -> "§c";
            };
            long min = Math.max(0, (now - s.time()) / 60_000);
            String line = " " + color + s.name() + " §7" + s.source() + ", " + min + " min ago";
            if (s.error() != null) line += " §8(" + s.error() + ")";
            source.sendFeedback(Component.literal(line));
        }
        return 1;
    }

    private static int openSearch(String query) {
        Minecraft.getInstance().schedule(() ->
                Minecraft.getInstance().setScreen(new com.teslamaps.screen.ItemSearchScreen(query)));
        return 1;
    }

    private static int openFeedback(boolean bug, String text) {
        Minecraft.getInstance().schedule(() ->
                Minecraft.getInstance().setScreen(new com.teslamaps.screen.FeedbackScreen(bug, text)));
        return 1;
    }

    private static int openNpcFinder(String query) {
        Minecraft.getInstance().schedule(() ->
                Minecraft.getInstance().setScreen(new com.teslamaps.screen.NpcFinderScreen(query)));
        return 1;
    }

    private static int corpseShare(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (com.teslamaps.features.Announce.NOBODY.equals(c.corpseFinderChannel)) {
            source.sendFeedback(Component.literal("§7Set a Share Channel under Mining > Corpse Finder first"));
        } else if (!com.teslamaps.esp.CorpseFinder.share(id)) {
            source.sendFeedback(Component.literal("§7No seen corpse to share"));
        }
        return 1;
    }

    private static int shardReport(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
                                   int count) {
        for (String line : com.teslamaps.features.hunting.ShardReport.toMax(count)) {
            source.sendFeedback(Component.literal(line));
        }
        return 1;
    }

    /** Online players wearing a TeslaMaps cape - the only ones worth reporting. */
    private static java.util.List<String> capedPlayers() {
        java.util.List<String> names = new java.util.ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return names;
        for (var info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.isBlank()) continue;
            if (com.teslamaps.features.capes.CapeSync.isUser(info.getProfile().id().toString())) names.add(name);
        }
        return names;
    }

    private static int capeReport(FabricClientCommandSource source, String name) {
        Minecraft mc = Minecraft.getInstance();
        java.util.UUID target = null;
        if (name == null || name.isBlank()) {
            // no name given: whoever is under the crosshair
            if (mc.crosshairPickEntity instanceof net.minecraft.world.entity.player.Player player) {
                target = player.getUUID();
                name = player.getGameProfile().name();
            } else {
                source.sendFeedback(Component.literal("§b[TeslaMaps] §cLook at a player or give a name"));
                return 1;
            }
        } else {
            var connection = mc.getConnection();
            var info = connection == null ? null : connection.getPlayerInfoIgnoreCase(name);
            if (info == null) {
                source.sendFeedback(Component.literal("§b[TeslaMaps] §cNo player called " + name));
                return 1;
            }
            target = info.getProfile().id();
            name = info.getProfile().name();
        }
        if (!com.teslamaps.features.capes.CapeSync.isUser(target.toString())) {
            source.sendFeedback(Component.literal("§b[TeslaMaps] §7" + name + " wears no TeslaMaps cape"));
            return 1;
        }
        String reported = name;
        com.teslamaps.features.capes.CapeSync.report(target, line -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + line
                    + (line.startsWith("§c") ? "" : " " + reported + "'s cape")));
        });
        return 1;
    }

    private static int setFlare(FabricClientCommandSource source, boolean on) {
        TeslaMapsConfig.get().flareMode = on;
        TeslaMapsConfig.save();
        if (on) com.teslamaps.features.hunting.FlareMode.reset();
        source.sendFeedback(Component.literal(
                "§b[TeslaMaps] §fFlare Mode " + (on ? "§aon" : "§coff")));
        return 1;
    }

    // dumps every entity nearby (not just mobs) so unknown skyblock props can be identified
    private static int greenhouseLoad(FabricClientCommandSource source, String code) {
        if (code == null || code.isBlank()) {
            source.sendFeedback(Component.literal("§b[TeslaMaps] §cNothing to read"));
            return 0;
        }
        String err = com.teslamaps.features.GreenhousePlanner.load(code);
        if (err != null) {
            source.sendFeedback(Component.literal("§b[TeslaMaps] §c" + err));
            return 0;
        }
        com.teslamaps.features.GreenhousePlanner.rescan();
        source.sendFeedback(Component.literal("§b[TeslaMaps] §fLayout loaded · "
                + com.teslamaps.features.GreenhousePlanner.status()));
        source.sendFeedback(Component.literal(
                "§7Stand on the greenhouse. The yellow box marks planner 0,0 — /tmap greenhouse rotate until it matches."));
        return 1;
    }

    private static int safariScan(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int chunks) {
        for (String line : com.teslamaps.safari.SafariScanner.scan(chunks)) {
            source.sendFeedback(Component.literal(line));
        }
        return 1;
    }

    private static int safariDebug(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int radius) {
        for (String line : com.teslamaps.safari.SafariScanner.debug(radius)) {
            source.sendFeedback(Component.literal(line));
        }
        return 1;
    }

    private static int dumpEntities(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            source.sendFeedback(Component.literal("Not in world"));
            return 0;
        }
        net.minecraft.world.phys.AABB box = mc.player.getBoundingBox().inflate(radius);
        source.sendFeedback(Component.literal("=== Entities within " + radius + " ==="));
        int count = 0;
        for (Entity e : mc.level.getEntities(mc.player, box, x -> true)) {
            String type = net.minecraft.world.entity.EntityType.getKey(e.getType()).toString();
            StringBuilder extra = new StringBuilder();
            if (e instanceof net.minecraft.world.entity.item.ItemEntity ie) {
                extra.append(" item=").append(ie.getItem().getItem().toString())
                     .append(" x").append(ie.getItem().getCount())
                     .append(" nograv=").append(ie.isNoGravity());
            }
            if (e instanceof net.minecraft.world.entity.Display.ItemDisplay id) {
                var slot = id.getSlot(0);
                if (slot != null) extra.append(" display=").append(slot.get().getItem().toString());
            }
            if (e.getCustomName() != null) extra.append(" custom='").append(e.getCustomName().getString()).append("'");
            // everything the dead-mob hider judges on, so a corpse that slips through can be read off
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                extra.append(String.format(" hp=%.1f dying=%b deathTime=%d hurt=%d",
                        le.getHealth(), le.isDeadOrDying(), le.deathTime, le.hurtTime));
                StringBuilder armor = new StringBuilder();
                for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values()) {
                    if (!s.isArmor()) continue;
                    if (!le.getItemBySlot(s).isEmpty()) armor.append(s.getName().charAt(0));
                }
                extra.append(" armor=").append(armor.isEmpty() ? "-" : armor.toString());
                extra.append(" nograv=").append(le.isNoGravity());
                extra.append(" hidden=").append(com.teslamaps.features.DeadMobHider.shouldHide(e));
            }
            if (e.getVehicle() != null) extra.append(" riding=").append(e.getVehicle().getType().toShortString());
            source.sendFeedback(Component.literal(String.format("%s | %s | %.1f %.1f %.1f | d=%.1f%s",
                    type, e.getName().getString(), e.getX(), e.getY(), e.getZ(),
                    Math.sqrt(e.distanceToSqr(mc.player)), extra)));
            if (++count >= 40) {
                source.sendFeedback(Component.literal("... more"));
                break;
            }
        }
        if (count == 0) source.sendFeedback(Component.literal("none"));
        return 1;
    }

    private static void sendServer(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> sackFill(String name, String id, int def) {
        return ClientCommands.literal(name)
                .executes(c -> { com.teslamaps.features.AutoGFS.fillFromSack(id, def); return 1; })
                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 2240))
                        .executes(c -> { com.teslamaps.features.AutoGFS.fillFromSack(id, IntegerArgumentType.getInteger(c, "amount")); return 1; }));
    }

    private static int perfReport(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        java.util.List<com.teslamaps.debug.Perf.Entry> list = com.teslamaps.debug.Perf.snapshot();
        if (!com.teslamaps.debug.Perf.enabled && list.isEmpty()) {
            source.sendFeedback(Component.literal(
                    "§b[TeslaMaps] §fPerf counters are off. §7/tmap perf on"));
            return 1;
        }

        long frames = Math.max(1, com.teslamaps.debug.Perf.frames());
        source.sendFeedback(Component.literal("§b[TeslaMaps] §fPerf over §e" + frames
                + "§f frames §8(us/frame · worst frame · worst call)"));

        int shown = 0;
        for (com.teslamaps.debug.Perf.Entry e : list) {
            if (shown++ >= 30) break;
            double perFrame = e.totalNs / 1000.0 / frames;
            String colour = perFrame >= 200 ? "§c" : perFrame >= 50 ? "§e" : "§a";
            source.sendFeedback(Component.literal(String.format(java.util.Locale.ROOT,
                    "§7%-24s %s%7.1f §8| §f%6d §8| §f%6d §8| %d calls",
                    e.name, colour, perFrame, e.maxFrameNs / 1000, e.maxNs / 1000, e.calls)));
        }
        if (list.size() > 30) {
            source.sendFeedback(Component.literal("§8  … " + (list.size() - 30) + " more"));
        }
        if (com.teslamaps.debug.Perf.spikeThresholdMs() > 0) {
            source.sendFeedback(Component.literal("§8  spikes logged: " + com.teslamaps.debug.Perf.spikes()));
        }
        return 1;
    }

    private static int openCustomize(FabricClientCommandSource source, boolean itemTab) {
        Minecraft mc = Minecraft.getInstance();
        // the armor tab builds a fake player, which needs a level
        if (mc.player == null || mc.level == null) {
            source.sendFeedback(Component.literal("§c[TeslaMaps] Join a world first"));
            return 0;
        }
        mc.schedule(() -> mc.setScreen(new com.teslamaps.screen.customize.CustomizeScreen(null, itemTab)));
        return 1;
    }

    private static int tracker(FabricClientCommandSource source, String id, String action) {
        var t = com.teslamaps.features.tracker.Tracker.get(id);
        if (t == null) {
            source.sendFeedback(Component.literal("§b[TeslaMaps] §cNo tracker " + id));
            return 0;
        }
        if (action.equals("history")) {
            var lines = t.historyLines();
            source.sendFeedback(Component.literal("§b[TeslaMaps] §f" + id + " history" + (lines.isEmpty() ? ": §7none (Tracker History off?)" : ":")));
            for (String l : lines) source.sendFeedback(Component.literal("  " + l));
            return 1;
        }
        switch (action) {
            case "total" -> t.resetTotal();
            case "view" -> t.toggleView();
            default -> t.resetSession();
        }
        source.sendFeedback(Component.literal("§b[TeslaMaps] §f" + id + ": " + (action.equals("view") ? "showing " + t.view() : action + " reset")));
        return 1;
    }
}
