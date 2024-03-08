package cn.paper_card.multi_spawn;

import cn.paper_card.mc_command.TheMcCommand;
import cn.paper_card.player_coins.api.NotEnoughCoinsException;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        this.teleport(player, bedSpawnLocation, "你的床", plugin.getConfigManager().getCoinsSpawnBed(), 0);
    }

    private void onWorld(@NotNull Player player) {
        final World world = player.getWorld();
        final Location spawnLocation = world.getSpawnLocation();
        this.teleport(player, spawnLocation, "世界出生点", plugin.getConfigManager().getCoinsSpawnWorld(), 0);
    }

    private void onDeath(@NotNull Player player) {
        final Location spawnLocation = player.getLastDeathLocation();
        if (spawnLocation == null) {
            plugin.sendError(player, "你没有上次死亡位置");
            return;
        }
        this.teleport(player, spawnLocation, "上次死亡位置", plugin.getConfigManager().getCoinsSpawnDeath(), 0);
    }

    private void teleport(@NotNull Player player, @NotNull Location location, @NotNull String name, long coins, long enderPearls) {

        // 花费
        final PlayerCoinsApi api = plugin.getPlayerCoinsApi();

        plugin.getTaskScheduler().runTaskAsynchronously(() -> {

            synchronized (this.lastTeleport) {
                this.lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
            }

            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已花费"));

            if (enderPearls > 0) {
                text.appendSpace();
                text.append(plugin.coinsNumber(enderPearls));
                text.append(Component.translatable(Material.ENDER_PEARL.translationKey()));
            }

            if (coins > 0) {

                final String coinsName = api.getCoinsName();

                final long leftCoins;
                try {
                    leftCoins = api.consumeCoins(player.getUniqueId(), coins, "spawn传送到：" + name);
                } catch (NotEnoughCoinsException e) {
                    final TextComponent.Builder t = Component.text();
                    plugin.appendPrefix(t);
                    t.appendSpace();
                    t.append(Component.text("您没有足够的"));
                    t.append(Component.text(coinsName));
                    t.append(Component.text("来进行传送，需要"));
                    t.append(plugin.coinsNumber(coins));
                    t.append(Component.text(coinsName));
                    t.append(Component.text("，您只有"));
                    t.append(plugin.coinsNumber(e.getLeftCoins()));
                    t.append(Component.text(coinsName));
                    t.append(Component.text("。如需使用"));
                    t.append(Component.translatable(Material.ENDER_PEARL.translationKey()));
                    t.append(Component.text("，请将其放在主手"));

                    player.sendMessage(t.build().color(NamedTextColor.YELLOW));
                    return;
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(player, e);
                    return;
                }

                text.appendSpace();
                text.append(plugin.coinsNumber(coins));
                text.append(Component.text(coinsName));
                text.append(Component.text("（剩余"));
                text.append(plugin.coinsNumber(leftCoins));
                text.append(Component.text("）"));
            }

            text.append(Component.text(" 将您传送到 "));
            text.append(Component.text(name).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            text.append(Component.text("~"));

            player.teleportAsync(location);

            player.sendMessage(text.build().color(NamedTextColor.GREEN));
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

        if (!(commandSender instanceof final Player player)) {
            plugin.sendError(commandSender, "该命令只能由玩家来执行！");
            return true;
        }

        final String argSpawnName = strings.length > 0 ? strings[0] : null;

        // 冷却
        final Long lastTp;
        synchronized (this.lastTeleport) {
            lastTp = this.lastTeleport.get(player.getUniqueId());
        }

        final long cur = System.currentTimeMillis();

        final long coolDown = plugin.getConfigManager().getCoolDown();

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
                return true;
            }
        }


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

        // 检查末影珍珠
        final int consumeEnderPeal;
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.ENDER_PEARL) {
            final int amount = item.getAmount();
            final int need = 4;
            if (amount < need) {
                plugin.sendError(commandSender, "您手上的末影珍珠只有%d颗，需要%d颗！".formatted(amount, need));
                return true;
            }
            // 消耗末影珍珠
            item.setAmount(amount - need);
            consumeEnderPeal = need;
        } else {
            consumeEnderPeal = 0;
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
            if (consumeEnderPeal > 0) {
                this.teleport(player, location, info.name(), 0, consumeEnderPeal);
            } else {
                this.teleport(player, location, info.name(), plugin.getConfigManager().getCoinsSpawnCustom(), 0);
            }
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
