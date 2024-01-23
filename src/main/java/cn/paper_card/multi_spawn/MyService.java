package cn.paper_card.multi_spawn;

import cn.paper_card.database.api.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

class MyService implements MultiSpawnApi.SpawnLocationListService, MultiSpawnApi.PlayerSpawnPointService {

    private final Connection connection;

    private SpawnListTable listTable = null;

    private PlayerSpawnTable playerSpawnTable = null;

    MyService(@NotNull Connection connection) {
        this.connection = connection;
    }

    private @NotNull SpawnListTable getListTable() throws Exception {
        if (this.listTable == null) {
            this.listTable = new SpawnListTable(this.connection);
        }
        return this.listTable;
    }

    private @NotNull PlayerSpawnTable getPlayerSpawnTable() throws Exception {
        if (this.playerSpawnTable == null) {
            this.playerSpawnTable = new PlayerSpawnTable(this.connection);
        }
        return this.playerSpawnTable;
    }

    void destroy() throws SQLException {
        synchronized (this) {
            SQLException exception = null;

            if (this.listTable != null) {
                try {
                    this.listTable.close();
                } catch (SQLException e) {
                    exception = e;
                }
                this.listTable = null;
            }


            if (this.playerSpawnTable != null) {
                try {
                    this.playerSpawnTable.close();
                } catch (SQLException e) {
                    exception = e;
                }
                this.playerSpawnTable = null;
            }

            try {
                this.connection.close();
            } catch (SQLException e) {
                exception = e;
            }

            if (exception != null) throw exception;
        }
    }

    @Override
    public boolean addOrUpdateByName(MultiSpawnApi.@NotNull SpawnLocationInfo info) throws Exception {
        synchronized (this) {
            final SpawnListTable t = this.getListTable();
            final int updated = t.updateAllByName(info);
            if (updated == 1) return false;
            if (updated == 0) {
                final int inserted = t.insert(info);
                if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                return true;
            }
            throw new Exception("根据一个名字[%s]更新了%d条数据！".formatted(info.name(), updated));
        }
    }

    @Override
    public boolean removeByName(@NotNull String name) throws Exception {
        synchronized (this) {
            final SpawnListTable t = this.getListTable();
            final int deleted = t.deleteByName(name);
            if (deleted == 1) return true;
            if (deleted == 0) return false;
            throw new Exception("根据一个名称[%s]删除了%d条数据！".formatted(name, deleted));
        }
    }

    @Override
    public @Nullable MultiSpawnApi.SpawnLocationInfo queryByName(@NotNull String name) throws Exception {
        synchronized (this) {
            final SpawnListTable t = this.getListTable();
            return t.queryByName(name);
        }
    }

    @Override
    public @NotNull List<String> queryAllNames() throws Exception {
        synchronized (this) {
            final SpawnListTable t = this.getListTable();
            return t.queryAllNames();
        }
    }

    @Override
    public boolean addOrUpdateByUuid(@NotNull MultiSpawnApi.PlayerSpawnPointInfo info) throws Exception {
        synchronized (this) {
            final PlayerSpawnTable t = this.getPlayerSpawnTable();
            final int updated = t.updateByUuid(info);
            if (updated == 1) return false;
            if (updated == 0) {
                final int inserted = t.insert(info);
                if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                return true;
            }
            throw new Exception("根据一个UUID更新了%d条数据！".formatted(updated));
        }
    }

    @Override
    public @Nullable String queryPlayerSpawnPointName(@NotNull UUID uuid) throws Exception {
        synchronized (this) {
            final PlayerSpawnTable t = this.getPlayerSpawnTable();
            return t.queryNameByUuid(uuid);
        }
    }

    static class SpawnListTable {
        private final static String NAME = "all_spawn_locations";

        private final PreparedStatement statementInsert;
        private final PreparedStatement statementUpdateByName;

        private final PreparedStatement statementDeleteByName;

        private final PreparedStatement statementQueryByName;

        private final PreparedStatement statementQueryAllName;


        SpawnListTable(@NotNull Connection connection) throws SQLException {
            this.create(connection);

            try {
                this.statementInsert = connection.prepareStatement
                        ("INSERT INTO %s (name, wid1, wid2, x, y, z) VALUES (?, ?, ?, ?, ?, ?)".formatted(NAME));

                this.statementUpdateByName = connection.prepareStatement
                        ("UPDATE %s SET wid1=?,wid2=?,x=?,y=?,z=? WHERE name=?".formatted(NAME));

                this.statementDeleteByName = connection.prepareStatement
                        ("DELETE FROM %s WHERE name=?".formatted(NAME));

                this.statementQueryByName = connection.prepareStatement
                        ("SELECT name,wid1,wid2,x,y,z FROM %s WHERE name=?".formatted(NAME));

                this.statementQueryAllName = connection.prepareStatement
                        ("SELECT name FROM %s".formatted(NAME));

            } catch (SQLException e) {
                try {
                    this.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

        private void create(@NotNull Connection connection) throws SQLException {
            Util.executeSQL(connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        name VARCHAR(24) NOT NULL,
                        wid1 INTEGER NOT NULL,
                        wid2 INTEGER NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL
                    )""".formatted(NAME));
        }

        void close() throws SQLException {
            Util.closeAllStatements(this.getClass(), this);
        }

        int insert(@NotNull MultiSpawnApi.SpawnLocationInfo info) throws SQLException {
            final PreparedStatement ps = this.statementInsert;
            ps.setString(1, info.name());
            ps.setLong(2, info.worldUuid().getMostSignificantBits());
            ps.setLong(3, info.worldUuid().getLeastSignificantBits());
            ps.setInt(4, info.x());
            ps.setInt(5, info.y());
            ps.setInt(6, info.z());
            return ps.executeUpdate();
        }

        int updateAllByName(@NotNull MultiSpawnApi.SpawnLocationInfo info) throws SQLException {
            final PreparedStatement ps = this.statementUpdateByName;

            ps.setLong(1, info.worldUuid().getMostSignificantBits());
            ps.setLong(2, info.worldUuid().getLeastSignificantBits());
            ps.setInt(3, info.x());
            ps.setInt(4, info.y());
            ps.setInt(5, info.z());
            ps.setString(6, info.name());
            return ps.executeUpdate();
        }

        int deleteByName(@NotNull String name) throws SQLException {
            final PreparedStatement ps = this.statementDeleteByName;
            ps.setString(1, name);
            return ps.executeUpdate();
        }

        @Nullable MultiSpawnApi.SpawnLocationInfo queryByName(@NotNull String name) throws SQLException {
            final PreparedStatement ps = this.statementQueryByName;
            ps.setString(1, name);

            final MultiSpawnApi.SpawnLocationInfo info;

            final ResultSet resultSet = ps.executeQuery();

            try {
                if (resultSet.next()) {
                    final String name2 = resultSet.getString(1);
                    final long wid1 = resultSet.getLong(2);
                    final long wid2 = resultSet.getLong(3);
                    final int x = resultSet.getInt(4);
                    final int y = resultSet.getInt(5);
                    final int z = resultSet.getInt(6);
                    info = new MultiSpawnApi.SpawnLocationInfo(
                            name2,
                            new UUID(wid1, wid2),
                            x, y, z
                    );

                } else info = null;

                if (resultSet.next()) throw new SQLException("不应该还有数据！");

            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            resultSet.close();

            return info;
        }

        @NotNull LinkedList<String> queryAllNames() throws SQLException {
            final ResultSet resultSet = this.statementQueryAllName.executeQuery();

            final LinkedList<String> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    list.add(name);
                }
            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            resultSet.close();

            return list;
        }
    }

    static class PlayerSpawnTable {
        private final static String NAME = "player_spawn_location";

        private final PreparedStatement statementInsert;
        private final PreparedStatement statementUpdateByUuid;

        private final PreparedStatement statementQueryNameByUuid;

        PlayerSpawnTable(@NotNull Connection connection) throws SQLException {
            this.create(connection);

            try {

                this.statementInsert = connection.prepareStatement
                        ("INSERT INTO %s (uid1, uid2, name) VALUES (?, ?, ?)".formatted(NAME));

                this.statementUpdateByUuid = connection.prepareStatement
                        ("UPDATE %s SET name=? WHERE uid1=? AND uid2=?".formatted(NAME));

                this.statementQueryNameByUuid = connection.prepareStatement
                        ("SELECT name FROM %s WHERE uid1=? AND uid2=?".formatted(NAME));

            } catch (SQLException e) {
                try {
                    this.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

        private void create(@NotNull Connection connection) throws SQLException {
            Util.executeSQL(connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        uid1 INTEGER NOT NULL,
                        uid2 INTEGER NOT NULL,
                        name VARCHAR(24) NOT NULL
                    )""".formatted(NAME));
        }


        void close() throws SQLException {
            Util.closeAllStatements(this.getClass(), this);
        }

        int insert(@NotNull MultiSpawnApi.PlayerSpawnPointInfo info) throws SQLException {
            final PreparedStatement ps = this.statementInsert;
            ps.setLong(1, info.playerUuid().getMostSignificantBits());
            ps.setLong(2, info.playerUuid().getLeastSignificantBits());
            ps.setString(3, info.name());
            return ps.executeUpdate();
        }

        int updateByUuid(@NotNull MultiSpawnApi.PlayerSpawnPointInfo info) throws SQLException {
            final PreparedStatement ps = this.statementUpdateByUuid;
            ps.setString(1, info.name());
            ps.setLong(2, info.playerUuid().getMostSignificantBits());
            ps.setLong(3, info.playerUuid().getLeastSignificantBits());

            return ps.executeUpdate();
        }

        @Nullable String queryNameByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.statementQueryNameByUuid;
            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());

            final ResultSet resultSet = ps.executeQuery();

            final String name;

            try {
                if (resultSet.next()) {
                    name = resultSet.getString(1);
                } else name = null;

                if (resultSet.next()) throw new SQLException("不应该还有数据！");

            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }
            resultSet.close();

            return name;
        }

    }
}
