package cn.paper_card.multi_spawn;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;

public final class MultiSpawn extends JavaPlugin implements Listener, MultiSpawnApi {

    private MyService myService = null;
    private PlayerCoinsApi playerCoinsApi = null;

    private final @NotNull TaskScheduler taskScheduler;

    private final boolean enable = false;

    public MultiSpawn() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    @Override
    public void onEnable() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        try {
            this.myService = new MyService(api.getLocalSQLite().connectNormal());
        } catch (SQLException e) {
            throw new RuntimeException("无法连接到数据库", e);
        }

        this.getServer().getPluginManager().registerEvents(this, this);

        this.playerCoinsApi = this.getServer().getServicesManager().load(PlayerCoinsApi.class);


        final PluginCommand command = this.getCommand("spawn");
        final SpawnCommand spawnCommand = new SpawnCommand(this);
        assert command != null;
        command.setTabCompleter(spawnCommand);
        command.setExecutor(spawnCommand);

        final PluginCommand command1 = this.getCommand("multi-spawn");
        final MultiSpawnCommand multiSpawnCommand = new MultiSpawnCommand(this);
        assert command1 != null;
        command1.setTabCompleter(multiSpawnCommand);
        command1.setExecutor(multiSpawnCommand);
    }

    @EventHandler
    public void onSetSpawn(@NotNull PlayerSetSpawnEvent event) {

        if (!enable) return;

        final PlayerSetSpawnEvent.Cause cause = event.getCause();


        final Player player = event.getPlayer();

        if (cause.equals(PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN)) {

            final Location bedSpawnLocation = player.getBedSpawnLocation();

            if (bedSpawnLocation != null) return;

            String name;
            try {
                name = getPlayerSpawnPointService().queryPlayerSpawnPointName(player.getUniqueId());
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
                return;
            }

            if (name == null) {
                final List<String> names;

                try {
                    names = getSpawnLocationListService().queryAllNames();
                } catch (Exception e) {
                    getSLF4JLogger().error("", e);
                    sendException(player, e);
                    return;
                }

                final int size = names.size();

                if (size == 0) return;

                final int index = new Random().nextInt(size);
                final String randomName = names.get(index);

                try {
                    getPlayerSpawnPointService().addOrUpdateByUuid(new PlayerSpawnPointInfo(player.getUniqueId(), randomName));
                } catch (Exception e) {
                    getSLF4JLogger().error("", e);
                    sendException(player, e);
                }

                name = randomName;
            }


            final SpawnLocationInfo info;

            try {
                info = getSpawnLocationListService().queryByName(name);
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
                return;
            }

            if (info == null) {
                player.sendMessage(Component.text("名称为[%s]的出生点不存在！".formatted(name)));
                return;
            }

            final World world = getServer().getWorld(info.worldUuid());

            if (world == null) {
                player.sendMessage(Component.text("出生点错误：UUID为[%s]的世界不存在！".formatted(info.worldUuid().toString())));
                return;
            }

            final Location location = new Location(world, info.x(), info.y(), info.z());

            event.setForced(true);
            event.setLocation(location);

            player.sendTitlePart(TitlePart.TITLE, Component.text()
                    .append(Component.text("重生在").color(NamedTextColor.GREEN))
                    .append(Component.text(info.name()).color(NamedTextColor.DARK_AQUA))
                    .build());
        }
    }

    @EventHandler
    public void onDeath(@NotNull PlayerDeathEvent event) {

        if (!enable) return;

        final Player player = event.getPlayer();

        // FIXME : 这里可能会有异常
        final Location bedSpawnLocation = player.getBedSpawnLocation();

        if (bedSpawnLocation != null) return;

        // 查询
        final String name;

        try {
            name = getPlayerSpawnPointService().queryPlayerSpawnPointName(player.getUniqueId());
        } catch (Exception e) {
            getSLF4JLogger().error("", e);
            sendException(player, e);
            return;
        }

        if (name == null) return;

        final SpawnLocationInfo info;

        try {
            info = getSpawnLocationListService().queryByName(name);
        } catch (Exception e) {
            getSLF4JLogger().error("", e);
            sendException(player, e);
            return;
        }

        if (info == null) {
            sendError(player, "名称为[%s]的出生点已经不存在，请及时更换你的出生点".formatted(name));
            return;
        }

        final World world = getServer().getWorld(info.worldUuid());

        if (world == null) {
            sendError(player, "出生点错误：以[%s]为UUID的世界不存在！".formatted(info.worldUuid().toString()));
            return;
        }

        final Location location = new Location(world, info.x(), info.y(), info.z());

        player.setBedSpawnLocation(location, true);

        player.getScheduler().run(this, task1 -> player.teleportAsync(location), () -> {
        });
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (player.hasPlayedBefore()) return;

        // 新玩家

        this.getServer().getAsyncScheduler().runNow(this, task -> {
            final List<String> names;

            try {
                names = this.getSpawnLocationListService().queryAllNames();
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
                return;
            }

            final int size = names.size();

            if (size == 0) return;


            final int index = new Random().nextInt(size);

            final String name = names.get(index);

            final SpawnLocationInfo info;

            try {
                info = getSpawnLocationListService().queryByName(name);
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
                return;
            }

            if (info == null) return;

            final World world = getServer().getWorld(info.worldUuid());

            if (world == null) return;

            final Location location = new Location(world, info.x(), info.y(), info.z());

            try {
                getPlayerSpawnPointService().addOrUpdateByUuid(new PlayerSpawnPointInfo(
                        player.getUniqueId(),
                        name
                ));
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
            }

            player.getScheduler().run(this, task1 -> {
                player.teleportAsync(location);
                player.sendTitlePart(TitlePart.TITLE, Component.text()
                        .append(Component.text("出生在").color(NamedTextColor.GREEN))
                        .append(Component.text(info.name()).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        .build());

                player.setBedSpawnLocation(location, true);
            }, () -> {
            });
        });
    }

    @Override
    public void onDisable() {
        try {
            this.myService.destroy();
        } catch (SQLException e) {
            getSLF4JLogger().error("", e);
        }
        this.taskScheduler.cancelTasks(this);
    }

    @Override
    public @NotNull SpawnLocationListService getSpawnLocationListService() {
        return this.myService;
    }

    @Override
    public @NotNull PlayerSpawnPointService getPlayerSpawnPointService() {
        return this.myService;
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text(this.getName()).color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text(error).color(NamedTextColor.RED));
        sender.sendMessage(text.build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        final TextComponent.Builder text = Component.text();
        this.appendPrefix(text);
        text.appendSpace();
        text.append(Component.text(info).color(NamedTextColor.GREEN));
        sender.sendMessage(text.build());
    }

    @NotNull PlayerCoinsApi getPlayerCoinsApi() {
        return this.playerCoinsApi;
    }

    @NotNull TextComponent coinsNumber(long c) {
        return Component.text(c).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    @NotNull TextComponent coinsNumber(@NotNull String c) {
        return Component.text(c).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    @NotNull String minutesAndSeconds(long ms) {
        ms /= 1000L;
        final long minutes = ms / 60;
        final long seconds = ms % 60;

        final StringBuilder sb = new StringBuilder();

        if (minutes != 0) {
            sb.append(minutes);
            sb.append('分');
        }

        if (minutes == 0 || seconds != 0) {
            sb.append(seconds);
            sb.append('秒');
        }

        return sb.toString();
    }
}
