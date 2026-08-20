package org.yomirein.sochatserver;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.utils.ConfigReader;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class Main {

    public static String osName = System.getProperty("os.name");
    public static String osVersion = System.getProperty("os.version");
    public static String osArch = System.getProperty("os.arch");

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        //
        // TODO: Check compliance with the tables
        //

        LOGGER.info("Running on OS: " + osName + " " + osVersion + " " + osArch);

        // This method does everything with databases
        databaseCheck();

        try {
            SoChat soChat = new SoChat();
            SoTurn soTurn = new SoTurn();

            soTurn.run();
            soChat.run();
        } catch (Exception e){
            LOGGER.error("Error starting SoChat", e);
        }
    }

    // Checking for existing 'sochat' database
    private static void databaseCheck() {
        // Firstly getting config to get all data
        Map<String, String> propertiesMap = ConfigReader.getConfig();
        Properties properties = new Properties();
        try {
            ConfigReader.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // If config has url trying to connect to server
        // TODO: Make connection tries
        if (propertiesMap.containsKey("db.url")) {
            LOGGER.info("Config already contains Database info, skipping setup...");
            return;
        }

        // If there's no url in config continue

        // Getting postgres connection credentials
        DbInput input = readDbInput();

        if ("sqlite".equals(input.type)) {

        }
        else if ("postgres".equals(input.type)) {
            // Creating database and save it in config
            try (HikariDataSource ds = dataSourceFactory(input.type, input.ipPort, "", input.user, input.password);) {

                String dbName = resolveDatabase(input.type, ds, input, properties);

                saveConfig(properties, input, dbName);

            }
            catch (Exception e) {
                LOGGER.info("Exit with error: " + e);
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported database type: " + input.type);
        }
    }

    private static void saveConfig(Properties prop, DbInput input, String dbName) {
        prop.setProperty("db.type", input.type);
        prop.setProperty("db.url", input.ipPort);
        prop.setProperty("db.username", input.user);
        prop.setProperty("db.password", input.password);
        prop.setProperty("db.name", dbName);

        try (OutputStream out = new FileOutputStream("config.properties")) {
            prop.store(out, "");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class DbInput {
        String type;
        String ipPort;
        String user;
        String password;
    }

    // Getting postgres authorization, like ip, username and password
    private static DbInput readDbInput() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        DbInput input = new DbInput();

        System.out.println("TYPE ONLY IF YOU KNOW WHAT YOU'RE DOING");
        System.out.println("Type database type: sqlite/postgresql (sqlite): ");
        input.type = readLine(in, "sqlite");

        if (input.type.equals("sqlite")) {
            return input;
        } else if (input.type.equals("postgresql")) {
            System.out.println("Type server Ip:Port (localhost:5432): ");
            input.ipPort = readLine(in, "localhost:5432");

            System.out.println("Type psql root username (postgres): ");
            input.user = readLine(in, "postgres");

            System.out.println("Type psql root password: ");
            input.password = readLine(in, "");
        } else {
            throw new RuntimeException("Invalid database type: " + input.type);
        }

        return input;
    }

    // Creating database or just write existing 'sochat' database without creating new
    private static String resolveDatabase(
        String type,
            HikariDataSource ds,
            DbInput input,
            Properties properties
    ) throws Exception {

        try (Connection con = ds.getConnection()) {

            // Check for 'sochat' db exists
            PreparedStatement ps =
                    con.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?");
            ps.setString(1, "sochat");

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                LOGGER.info("Database exists, using database with default name\n(if you don't want this database change in config.properties or delete to start setup)");
                return "sochat";
            }

            // Create db in not
            LOGGER.info("Database not found.");

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            LOGGER.info("1 - use existing, 2 - create new (default 2)");
            int option = Integer.parseInt(readLine(in, "2"));

            LOGGER.info("Database name (sochat): ");
            String dbName = readLine(in, "sochat");

            if (option == 2) {
                createDatabase(dbName, ds, properties);
                try (HikariDataSource soDs = dataSourceFactory(input.type, input.ipPort, dbName, input.user, input.password )) {
                    initTypes(soDs);
                    initColumns(soDs);
                }
                catch (Exception e)
                {
                    LOGGER.info("Exit with error:"+ e);
                }
            }


            return dbName;
        }
    }

    // Creates database itself, duh
    private static void createDatabase(String name, HikariDataSource ds, Properties properties) {
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
    private static HikariDataSource dataSourceFactory(String type, String ipPort, String dbName, String psqlName, String psqlPassword){
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

    // BufferedReader readLine for easier use
    // (I won't use Scanner because I think BufferedReader more compatible for just typing a few words in console(
    // (and maybe commands in future))
    private static String readLine(BufferedReader in, String prompt) {
        try {
            String input = in.readLine();
            if (!input.isEmpty()) { return input; }
            else { return prompt; }
        }
        catch (IOException _) { return prompt; }
    }
}
