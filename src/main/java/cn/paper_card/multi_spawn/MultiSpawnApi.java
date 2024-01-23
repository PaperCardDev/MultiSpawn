package cn.paper_card.multi_spawn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface MultiSpawnApi {

    record SpawnLocationInfo(
            String name,
            UUID worldUuid,
            int x,
            int y,
            int z
    ) {
    }

    record PlayerSpawnPointInfo(
            UUID playerUuid,
            String name
    ) {
    }

    interface SpawnLocationListService {
        boolean addOrUpdateByName(@NotNull SpawnLocationInfo info) throws Exception;

        boolean removeByName(@NotNull String name) throws Exception;

        @Nullable SpawnLocationInfo queryByName(@NotNull String name) throws Exception;

        @NotNull List<String> queryAllNames() throws Exception;

    }

    interface PlayerSpawnPointService {
        boolean addOrUpdateByUuid(@NotNull PlayerSpawnPointInfo info) throws Exception;

        @Nullable String queryPlayerSpawnPointName(@NotNull UUID uuid) throws Exception;

    }

    @NotNull SpawnLocationListService getSpawnLocationListService();

    @NotNull PlayerSpawnPointService getPlayerSpawnPointService();
}