package org.yomirein.sochatserver.database;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class DatabaseConfigurator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfigurator.class);

    public static void createPgsqlDatabase(String name, HikariDataSource ds, Properties properties) {
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();
            st.executeUpdate("CREATE DATABASE  " + name);

            properties.setProperty("db.name", name);
            LOGGER.info("Created database successfully");

        }
        catch (Exception e){
            LOGGER.error("Error creating database", e);
        }
    }

    // Creates types
    //
    // chat_role for roles in chats, groups and maybe channels in Future
    // chat_type says everything for itself
    //
    public static void initTypes(HikariDataSource ds) {
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();
            st.executeUpdate("""
                CREATE TYPE chat_role AS ENUM ('MEMBER', 'ADMIN','OWNER');
            """);

            st.executeUpdate("""
                CREATE TYPE chat_type AS ENUM ('PRIVATE', 'GROUP_INSECURE','GROUP_SECURE', 'CHANNEL');
            """);
        }
        catch (Exception e) {
            LOGGER.error("Error initializing db types", e);
        }
    }


    // Init every column in database
    // Database docs will be made in future
    // Or else you can read it like that, i don't think it that hard
    // TODO: Make database docs
    public static void initColumns(HikariDataSource ds) {
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();

            st.executeUpdate("""
                CREATE TABLE users (
                    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    nickname varchar(255),
                    username varchar(255) NOT NULL UNIQUE,
                    description TEXT,
                    ed25519_public_key text NOT NULL,
                    x25519_public_key text NOT NULL
                );
            """);

            st.executeUpdate("""
                CREATE TABLE friendship (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    friend_id BIGINT NOT NULL,
                    status VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE,
                    UNIQUE (user_id, friend_id)
                );
            """);

            st.executeUpdate("""
                CREATE TABLE trust_keys (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    fn_owner_id BIGINT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """
            );

            st.executeUpdate("""
                CREATE TABLE chat (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    type chat_type NOT NULL,
                    title TEXT,

                    CHECK (
                        (type = 'PRIVATE' AND title IS NULL) OR
                        (type IN ('GROUP_SECURE','GROUP_INSECURE', 'CHANNEL') AND title IS NOT NULL)
                    )
                );
            """);

            st.executeUpdate("""
                CREATE TABLE message (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    chat_id BIGINT NOT NULL,
                    sender_id BIGINT NOT NULL,
                    reply_message_id BIGINT,
                    content TEXT NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    message TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    key_version TEXT NOT NULL,
                    FOREIGN KEY (chat_id) REFERENCES chat(id) ON DELETE CASCADE,
                    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (reply_message_id) REFERENCES message(id) ON DELETE SET NULL
                );
            """);
            st.executeUpdate("""
                CREATE TABLE chat_participants (
                    chat_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role chat_role NOT NULL,
                    last_read_message_id BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (chat_id, user_id)
                );
            """);

            st.executeUpdate("""
                CREATE TABLE chat_sender_keys(
                    chat_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    key_version BIGINT NOT NULL,
                    chat_key TEXT NOT NULL,
                    PRIMARY KEY (chat_id, user_id, key_version),
                    FOREIGN KEY (chat_id) REFERENCES chat(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """);

            st.executeUpdate("""
               CREATE TABLE media (
                    media_id TEXT PRIMARY KEY,
                    message_id BIGINT,
                    sender_id BIGINT NOT NULL,
                    mime_type TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    file_size BIGINT NOT NULL,
                    width INTEGER,
                    height INTEGER,
                    length INTEGER,
                    nonce TEXT,
                    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
               );
            """
            );

        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Factory for HikariDataSource so i don't have to make it every second
    public static HikariDataSource dataSourceFactory(String type, String ipPort, String dbName, String psqlName, String psqlPassword){
        if (type.equals("sqlite")) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:sqlite:" + dbName + ".db");

            cfg.setMaximumPoolSize(1);
            cfg.setMinimumIdle(1);
            cfg.setConnectionTimeout(10000);
            cfg.setIdleTimeout(300000);
            cfg.setPoolName("app-pool");

            cfg.addDataSourceProperty("journal_mode", "WAL");
            cfg.addDataSourceProperty("busy_timeout", "5000");

            return new HikariDataSource(cfg);
        } else if (type.equals("postgresql")) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:postgresql://" + ipPort + "/" + dbName);
            cfg.setUsername(psqlName);
            cfg.setPassword(psqlPassword);

            cfg.setMaximumPoolSize(10);
            cfg.setMinimumIdle(2);
            cfg.setPoolName("app-pool");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            return new HikariDataSource(cfg);
        } else {
            throw new RuntimeException("Invalid database type: " + type);
        }
    }
}
