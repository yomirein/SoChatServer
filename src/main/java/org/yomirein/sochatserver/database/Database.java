package org.yomirein.sochatserver.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.yomirein.sochatserver.utils.ConfigReader;

import java.sql.Connection;
import java.sql.SQLException;

public class Database {

    // Nothing interesting here actually
    // Connecting to database using config data(it going after Main.java where it inits)
    // It static so we can easily use it in every repository we want

    private static final HikariDataSource ds;

    static {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + ConfigReader.getConfig().get("db.url") + "/" + ConfigReader.getConfig().get("db.name"));
        cfg.setUsername(ConfigReader.getConfig().get("db.username"));
        cfg.setPassword(ConfigReader.getConfig().get("db.password"));
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setPoolName("app-pool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        ds = new HikariDataSource(cfg);
    }

    private Database() {}

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
