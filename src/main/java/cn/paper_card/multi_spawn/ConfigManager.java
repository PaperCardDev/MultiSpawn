package cn.paper_card.multi_spawn;

import org.jetbrains.annotations.NotNull;

class ConfigManager {
    private final @NotNull MultiSpawn plugin;

    private final static String PATH_COOL_DOWN = "cool-down";
    private final static String PATH_COINS_SPAWN_BED = "coins-spawn-bed";
    private final static String PATH_COINS_SPAWN_WORLD = "coins-spawn-world";
    private final static String PATH_COINS_SPAWN_DEATH = "coins-spawn-death";

    private final static String PATH_COINS_SPAWN_CUSTOM = "coins-spawn-custom";

    ConfigManager(@NotNull MultiSpawn plugin) {
        this.plugin = plugin;
    }

    long getCoolDown() {
        return this.plugin.getConfig().getLong(PATH_COOL_DOWN, 60 * 1000L);
    }

    void setCoolDown(long v) {
        this.plugin.getConfig().set(PATH_COOL_DOWN, v);
    }

    long getCoinsSpawnBed() {
        return this.plugin.getConfig().getLong(PATH_COINS_SPAWN_BED, 1);
    }

    void setCoinsSpawnBed(long v) {
        this.plugin.getConfig().set(PATH_COINS_SPAWN_BED, v);
    }

    long getCoinsSpawnWorld() {
        return this.plugin.getConfig().getLong(PATH_COINS_SPAWN_WORLD, 1);
    }

    void setCoinsSpawnWorld(long v) {
        this.plugin.getConfig().set(PATH_COINS_SPAWN_WORLD, v);
    }

    long getCoinsSpawnDeath() {
        return this.plugin.getConfig().getLong(PATH_COINS_SPAWN_DEATH, 10);
    }

    void setCoinsSpawnDeath(long v) {
        this.plugin.getConfig().set(PATH_COINS_SPAWN_DEATH, v);
    }

    long getCoinsSpawnCustom() {
        return this.plugin.getConfig().getLong(PATH_COINS_SPAWN_CUSTOM, 1);
    }

    void setCoinsSpawnCustom(long v) {
        this.plugin.getConfig().set(PATH_COINS_SPAWN_CUSTOM, v);
    }


    void setDefaults() {
        this.setCoolDown(this.getCoolDown());
        this.setCoinsSpawnBed(this.getCoinsSpawnBed());
        this.setCoinsSpawnWorld(this.getCoinsSpawnWorld());
        this.setCoinsSpawnDeath(this.getCoinsSpawnDeath());
        this.setCoinsSpawnCustom(this.getCoinsSpawnCustom());
    }

    void save() {
        this.plugin.saveConfig();
    }

}
