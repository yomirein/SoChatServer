package org.yomirein.sochatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yomirein.sochatserver.utils.ConfigReader;
import org.yomirein.sochatserver.utils.JwtService;

public class SoTurn {

    public static String osName = System.getProperty("os.name");
    public static String osVersion = System.getProperty("os.version");
    public static String osArch = System.getProperty("os.arch");

    Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private static volatile  Process turnProcess;

    public void run() throws IOException, InterruptedException {
        String executableName = checkTurn();
        Thread thread = configureTurnThread(executableName);

        LOGGER.info("Starting SoTurn...");

        thread.start();
    }

    public String checkTurn() throws IOException, InterruptedException {
        String soTurnName = "soturn-" + osName.toLowerCase().split(" ")[0] + "-" + "x86-64";

        if (osName.contains("Windows")) { soTurnName += ".exe"; }

        if (Paths.get(soTurnName).toFile().exists()) {
            LOGGER.info("SoTurn exists, continuing...");
        } else {
            LOGGER.info("No SoTurn found, downloading...");

            String fileUrl = "https://github.com/So-Chat/SoTurn/releases/download/v0.1-MVP/" + soTurnName;
            Path savePath = Paths.get(soTurnName).toAbsolutePath();

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .header("Accept", "application/octet-stream")
                    .build();

            client.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(savePath)
            );

            LOGGER.info("SoTurn download complete!");
        }
        return soTurnName;
    }

    public Thread configureTurnThread(String executableName) throws IOException, InterruptedException {
        LOGGER.info("SoTurn configuring thread");
        Thread turnThread = new Thread(() -> {
            ProcessBuilder pb = new ProcessBuilder(
                    Paths.get(executableName).toAbsolutePath().toString(),
                    "--public-ip", ConfigReader.getConfig().get("turn.ip"),
                    "--realm", ConfigReader.getConfig().get("turn.realm"),
                    "--port", ConfigReader.getConfig().get("turn.port"),
                    "--jwt", JwtService.SECRET
            );
            pb.redirectErrorStream(true);

            Logger turnThreadLogger = LoggerFactory.getLogger(pb.getClass());

            try {
                turnProcess = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(turnProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        turnThreadLogger.info(line);
                    }
                }
                turnProcess.waitFor();

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = turnProcess;

            if (p == null) {
                return;
            }

            ProcessHandle handle = p.toHandle();

            handle.descendants()
                    .forEach(child -> {
                        child.destroyForcibly();
                    });

            handle.destroyForcibly();

            try {
                p.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }));

        return turnThread;
    }


}
