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
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.utils.ConfigReader;
import static org.yomirein.sochatserver.database.DatabaseConfigurator.dataSourceFactory;
import static org.yomirein.sochatserver.database.DatabaseConfigurator.createPgsqlDatabase;
import static org.yomirein.sochatserver.database.DatabaseConfigurator.initColumns;
import static org.yomirein.sochatserver.database.DatabaseConfigurator.initTypes;

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
                createPgsqlDatabase(dbName, ds, properties);
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

    // BufferedReader readLine for easier use
    // (I won't use Scanner because I think BufferedReader more compatible for just typing a few words in console)
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
