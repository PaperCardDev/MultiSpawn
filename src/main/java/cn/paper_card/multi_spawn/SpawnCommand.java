package cn.paper_card.multi_spawn;

import cn.paper_card.mc_command.TheMcCommand;
import cn.paper_card.player_coins.api.NotEnoughCoinsException;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class SpawnCommand extends TheMcCommand {

    private final @NotNull Permission permission;
    private final @NotNull MultiSpawn plugin;

    private final static String NAME_BED = "bed";
    private final static String NAME_WORLD = "world";

    private final static String NAME_DEATH = "death";

    private final @NotNull HashMap<UUID, Long> lastTeleport;

    protected SpawnCommand(@NotNull MultiSpawn plugin) {
        super("spawn");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("multi-spawn.command.spawn"));
        this.lastTeleport = new HashMap<>();
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private void onBed(@NotNull Player player) {
        final Location bedSpawnLocation = player.getBedSpawnLocation();
        if (bedSpawnLocation == null) {
            plugin.sendError(player, "你还没有设置床");
            return;
        }
        this.teleport(player, bedSpawnLocation, "你的床");
    }

    private void onWorld(@NotNull Player player) {
        final World world = player.getWorld();
        final Location spawnLocation = world.getSpawnLocation();
        this.teleport(player, spawnLocation, "世界出生点");
    }

    private void onDeath(@NotNull Player player) {
        final Location spawnLocation = player.getLastDeathLocation();
        if (spawnLocation == null) {
            plugin.sendError(player, "你没有上次死亡位置");
            return;
        }
        this.teleport(player, spawnLocation, "上次死亡位置");
    }

    private void teleport(@NotNull Player player, @NotNull Location location, @NotNull String name) {
        // 冷却
        final Long lastTp;
        synchronized (this.lastTeleport) {
            lastTp = this.lastTeleport.get(player.getUniqueId());
        }

        final long cur = System.currentTimeMillis();

        // todo: 先写死
        final long coolDown = 60 * 1000L;

        if (lastTp != null) {
            final long delta = lastTp - cur + coolDown;
            if (delta > 0) {

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("命令冷却，在").color(NamedTextColor.YELLOW));
                text.append(Component.text(plugin.minutesAndSeconds(delta)).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                text.append(Component.text("后你才能再次使用此命令").color(NamedTextColor.YELLOW));

                player.sendMessage(text.build());
                return;
            }
        }

        // 花费硬币
        final PlayerCoinsApi api = plugin.getPlayerCoinsApi();

        plugin.getTaskScheduler().runTaskAsynchronously(() -> {

            player.teleportAsync(location);

            synchronized (this.lastTeleport) {
                this.lastTeleport.put(player.getUniqueId(), cur);
            }

            // todo: 先写死
            final long needCoins = 1;
            final long leftCoins;

            try {
                leftCoins = api.consumeCoins(player.getUniqueId(), needCoins, "spawn传送到：" + name);
            } catch (NotEnoughCoinsException e) {
                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("你没有足够的硬币来进行传送，需要").color(NamedTextColor.RED));
                text.append(plugin.coinsNumber(needCoins));
                text.append(Component.text("枚硬币，你只有").color(NamedTextColor.RED));
                text.append(plugin.coinsNumber(e.getLeftCoins()));
                text.append(Component.text("枚硬币").color(NamedTextColor.RED));

                player.sendMessage(text.build());
                return;
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
                plugin.sendException(player, e);
                return;
            }

            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已花费").color(NamedTextColor.GREEN));
            text.append(plugin.coinsNumber(needCoins));
            text.append(Component.text("枚硬币将你传送到").color(NamedTextColor.GREEN));
            text.append(Component.text(name).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            text.append(Component.text("，你还有").color(NamedTextColor.GREEN));
            text.append(plugin.coinsNumber(leftCoins));
            text.append(Component.text("枚硬币~").color(NamedTextColor.GREEN));
            player.sendMessage(text.build());
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

        if (!(commandSender instanceof final Player player)) {
            plugin.sendError(commandSender, "该命令只能由玩家来执行！");
            return true;
        }


        final String argSpawnName = strings.length > 0 ? strings[0] : null;

        if (argSpawnName == null) {
            plugin.sendError(commandSender, "必须指定一个出生点的名字！");
            return true;
        }

        if (NAME_BED.equals(argSpawnName)) {
            this.onBed(player);
            return true;
        }

        if (NAME_WORLD.equals(argSpawnName)) {
            this.onWorld(player);
            return true;
        }

        if (NAME_DEATH.equals(argSpawnName)) {
            this.onDeath(player);
            return true;
        }

        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            final MultiSpawnApi.SpawnLocationInfo info;

            try {
                info = plugin.getSpawnLocationListService().queryByName(argSpawnName);
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
                plugin.sendException(commandSender, e);
                return;
            }

            if (info == null) {
                plugin.sendError(commandSender, "名字为 %s 的出生点不存在！".formatted(argSpawnName));
                return;
            }

            final World world = plugin.getServer().getWorld(info.worldUuid());

            if (world == null) {
                plugin.sendError(commandSender, "以 %s 为ID的世界不存在！".formatted(info.worldUuid().toString()));
                return;
            }

            final Location location = new Location(world, info.x(), info.y(), info.z());

            // 直接传送
            this.teleport(player, location, info.name());
        });


        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length == 1) {
            final String argName = strings[0];
            final LinkedList<String> list = new LinkedList<>();
            if (argName.isEmpty()) list.add("<出生点名称>");
            final List<String> names;

            list.add(NAME_BED);
            list.add(NAME_WORLD);
            list.add(NAME_DEATH);

            try {
                names = plugin.getSpawnLocationListService().queryAllNames();
                for (final String name : names) {
                    if (name.startsWith(argName)) list.add(name);
                }
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
            }

            return list;
        }
        return null;
    }
}
