package org.example.sawervatoryy.miragePUtils;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Журнал наказаний, выданных через MiragePUtils (нужен для «лесенки» и истории).
 * Сами баны и муты хранит LiteBans. SQLite (по умолчанию) или MySQL/MariaDB — драйверы входят в Paper.
 */
public final class Database {

    private static final String COLS = "id,type,target_uuid,target_name,staff_uuid,staff_name,reason,category,"
            + "created,duration,silent,animation,reset_by";

    private final MiragePUtils plugin;
    private boolean mysql;
    private Connection connection;

    public Database(MiragePUtils plugin) {
        this.plugin = plugin;
    }

    public synchronized void init() throws Exception {
        mysql = plugin.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("mysql");
        connect();
        String autoId = mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS mirage_punishments ("
                    + "id " + autoId + ","
                    + "type VARCHAR(16) NOT NULL,"
                    + "target_uuid VARCHAR(36) NOT NULL,"
                    + "target_name VARCHAR(32),"
                    + "staff_uuid VARCHAR(36),"
                    + "staff_name VARCHAR(32),"
                    + "reason VARCHAR(512),"
                    + "category VARCHAR(64),"
                    + "created BIGINT NOT NULL,"
                    + "duration BIGINT NOT NULL,"
                    + "silent INT NOT NULL,"
                    + "animation VARCHAR(32),"
                    + "reset_by VARCHAR(32)"
                    + ")");
        }
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE INDEX " + (mysql ? "" : "IF NOT EXISTS ")
                    + "idx_mirage_target ON mirage_punishments(target_uuid)");
        } catch (SQLException ignored) {
            // MySQL: индекс уже существует
        }
    }

    private void connect() throws Exception {
        FileConfiguration c = plugin.getConfig();
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + c.getString("database.mysql.host", "localhost") + ":"
                    + c.getInt("database.mysql.port", 3306) + "/" + c.getString("database.mysql.database", "mirageputils")
                    + "?useSSL=false&characterEncoding=utf8&autoReconnect=true";
            connection = DriverManager.getConnection(url,
                    c.getString("database.mysql.user", "root"), c.getString("database.mysql.password", ""));
        } else {
            Class.forName("org.sqlite.JDBC");
            File file = new File(plugin.getDataFolder(), c.getString("database.sqlite-file", "punishments.db"));
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
            }
        }
    }

    private Connection conn() throws SQLException {
        try {
            if (connection == null || connection.isClosed() || (mysql && !connection.isValid(2))) {
                connect();
            }
        } catch (SQLException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SQLException(ex);
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
            // закрытие при выключении
        }
    }

    public synchronized int insert(Punishment p) throws SQLException {
        String sql = "INSERT INTO mirage_punishments (type,target_uuid,target_name,staff_uuid,staff_name,reason,"
                + "category,created,duration,silent,animation,reset_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.type.name());
            ps.setString(2, p.targetUuid.toString());
            ps.setString(3, p.targetName);
            ps.setString(4, p.staffUuid == null ? null : p.staffUuid.toString());
            ps.setString(5, p.staffName);
            ps.setString(6, p.reason);
            ps.setString(7, p.category);
            ps.setLong(8, p.created);
            ps.setLong(9, p.duration);
            ps.setInt(10, p.silent ? 1 : 0);
            ps.setString(11, p.animation);
            ps.setString(12, p.resetBy);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Сколько раз игрока уже наказывали по каждой категории (причине) — основа «лесенки». */
    public synchronized Map<String, Integer> categoryCounts(UUID uuid) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT category, COUNT(*) FROM mirage_punishments WHERE target_uuid=? AND reset_by IS NULL GROUP BY category")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cat = rs.getString(1);
                    if (cat != null) out.put(cat, rs.getInt(2));
                }
            }
        }
        return out;
    }

    public synchronized int countTotal(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM mirage_punishments WHERE target_uuid=? AND reset_by IS NULL")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public synchronized List<Punishment> history(UUID uuid, int limit) throws SQLException {
        List<Punishment> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT " + COLS + " FROM mirage_punishments WHERE target_uuid=? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** Сбрасывает лесенку игрока: записи остаются в истории, но больше не учитываются. */
    public synchronized int resetCounts(UUID uuid, String by) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE mirage_punishments SET reset_by=? WHERE target_uuid=? AND reset_by IS NULL")) {
            ps.setString(1, by);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate();
        }
    }

    private Punishment map(ResultSet rs) throws SQLException {
        Punishment p = new Punishment();
        p.id = rs.getInt("id");
        PunishType type = PunishType.parse(rs.getString("type"));
        p.type = type == null ? PunishType.WARN : type;
        p.targetUuid = UUID.fromString(rs.getString("target_uuid"));
        p.targetName = rs.getString("target_name");
        String su = rs.getString("staff_uuid");
        p.staffUuid = su == null ? null : UUID.fromString(su);
        p.staffName = rs.getString("staff_name");
        p.reason = rs.getString("reason");
        p.category = rs.getString("category");
        p.created = rs.getLong("created");
        p.duration = rs.getLong("duration");
        p.silent = rs.getInt("silent") == 1;
        p.animation = rs.getString("animation");
        p.resetBy = rs.getString("reset_by");
        return p;
    }
}
