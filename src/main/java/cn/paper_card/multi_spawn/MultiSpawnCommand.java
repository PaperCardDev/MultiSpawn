package cn.paper_card.multi_spawn;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

class MultiSpawnCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;
    private final @NotNull MultiSpawn plugin;

    public MultiSpawnCommand(@NotNull MultiSpawn plugin) {
        super("multi-spawn");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("multi-spawn.command"));

        this.addSubCommand(new Set());
        this.addSubCommand(new Remove());
        this.addSubCommand(new Reload());
        this.addSubCommand(new Config());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    class Set extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Set() {
            super("set");
            this.permission = plugin.addPermission(MultiSpawnCommand.this.permission.getName() + ".set");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argName = strings.length > 0 ? strings[0] : null;

            if (argName == null) {
                plugin.sendError(commandSender, "必须指定参数：出生点的名称");
                return true;
            }

            if (!(commandSender instanceof final Player player)) {
                plugin.sendError(commandSender, "该命令只能由玩家来执行！");
                return true;
            }

            final Location location = player.getLocation();
            final World world = location.getWorld();

            if (world == null) {
                plugin.sendError(commandSender, "无法获取你所在的世界！");
                return true;
            }

            final MultiSpawnApi.SpawnLocationInfo info = new MultiSpawnApi.SpawnLocationInfo(
                    argName,
                    world.getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );

            final boolean added;
            try {
                added = plugin.getSpawnLocationListService().addOrUpdateByName(info);
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
                plugin.sendException(commandSender, e);
                return true;
            }

            if (added) {
                plugin.sendInfo(commandSender, "添加了一个新的出生点：%s (%s: %d,%d,%d)".formatted(
                        info.name(), world.getName(), info.x(), info.y(), info.z()
                ));
                return true;
            }

            plugin.sendInfo(commandSender, "已将出生点[%s]的位置更新为: (%s: %d,%d,%d)".formatted(
                    info.name(), world.getName(), info.x(), info.y(), info.z()
            ));

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argName = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (argName.isEmpty()) list.add("<出生点名称>");
                final List<String> names;

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

    class Remove extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Remove() {
            super("remove");
            this.permission = plugin.addPermission(MultiSpawnCommand.this.permission.getName() + ".remove");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argName = strings.length > 0 ? strings[0] : null;

            if (argName == null) {
                plugin.sendError(commandSender, "必须指定参数：出生点的名称");
                return true;
            }

            final MultiSpawnApi.SpawnLocationInfo info;

            try {
                info = plugin.getSpawnLocationListService().queryByName(argName);
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
                plugin.sendException(commandSender, e);
                return true;
            }

            if (info == null) {
                plugin.sendError(commandSender, "以[%s]为名称的出生点不存在！".formatted(argName));
                return true;
            }

            final boolean removed;

            try {
                removed = plugin.getSpawnLocationListService().removeByName(argName);
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("", e);
                plugin.sendException(commandSender, e);
                return true;
            }

            if (!removed) {
                plugin.sendInfo(commandSender, "删除出生点失败，可能是因为以[%s]为名称的出生点不存在".formatted(argName));
                return true;
            }


            final World world = plugin.getServer().getWorld(info.worldUuid());
            final String worldName = world != null ? world.getName() : "null";

            plugin.sendInfo(commandSender, "删除了出生点：%s (%s: %d,%d,%d)".formatted(
                    info.name(), worldName, info.x(), info.y(), info.z()
            ));

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argName = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (argName.isEmpty()) list.add("<出生点名称>");
                final List<String> names;

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

    class Reload extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Reload() {
            super("reload");
            this.permission = plugin.addPermission(MultiSpawnCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            plugin.getConfigManager().reload();
            plugin.sendInfo(commandSender, "已重载配置");
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Config extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Config() {
            super("config");
            this.permission = plugin.addPermission(MultiSpawnCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("==== 配置信息 ===="));

            final ConfigManager cm = plugin.getConfigManager();

            // 冷却
            text.appendNewline();
            text.append(Component.text("传送冷却："));
            text.append(Component.text(plugin.minutesAndSeconds(cm.getCoolDown())));

            // 硬币
            text.appendNewline();
            text.append(Component.text("硬币花费，传送到自定义传送点："));
            text.append(Component.text(cm.getCoinsSpawnCustom()));

            // 硬币
            text.appendNewline();
            text.append(Component.text("硬币花费，传送到床："));
            text.append(Component.text(cm.getCoinsSpawnBed()));

            // 硬币
            text.appendNewline();
            text.append(Component.text("硬币花费，传送到世界出生点："));
            text.append(Component.text(cm.getCoinsSpawnWorld()));

            // 硬币
            text.appendNewline();
            text.append(Component.text("硬币花费，传送到上次死亡位置："));
            text.append(Component.text(cm.getCoinsSpawnDeath()));

            commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }
}
