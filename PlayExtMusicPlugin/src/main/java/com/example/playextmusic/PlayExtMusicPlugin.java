package com.example.playextmusic;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public final class PlayExtMusicPlugin extends JavaPlugin {
    private HttpServer httpServer;
    private Path musicDirectory;
    private Path packDirectory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        musicDirectory = getDataFolder().toPath()
                .resolve(getConfig().getString("music-directory", "musicSrc"))
                .normalize();
        packDirectory = getDataFolder().toPath().resolve("packs");
        try {
            Files.createDirectories(musicDirectory);
            Files.createDirectories(packDirectory);
            startHttpServer();
        } catch (IOException exception) {
            getLogger().severe("Unable to prepare music folders/HTTP server: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        var command = getCommand("playextmusic");
        if (command == null) {
            getLogger().severe("playextmusic is missing from plugin.yml");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        var executor = new PlayExtMusicCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(executor, this);
        getLogger().info("Audio source folder: " + musicDirectory.toAbsolutePath());
    }

    private void startHttpServer() throws IOException {
        String bindAddress = getConfig().getString("http.bind-address", "0.0.0.0");
        int port = getConfig().getInt("http.port", 8766);
        httpServer = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        httpServer.createContext("/packs/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring("/packs/".length());
            if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            Path file = packDirectory.resolve(name).normalize();
            if (!file.startsWith(packDirectory) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, Files.size(file));
            try (var output = exchange.getResponseBody()) {
                Files.copy(file, output);
            }
        });
        httpServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "PlayExtMusic-HTTP");
            thread.setDaemon(true);
            return thread;
        }));
        httpServer.start();
    }

    @Override
    public void onDisable() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    Path musicDirectory() {
        return musicDirectory;
    }

    Path packDirectory() {
        return packDirectory;
    }

    String ffmpeg() {
        return getConfig().getString("ffmpeg", "ffmpeg");
    }

    String publicBaseUrl() {
        return getConfig().getString("http.public-base-url", "http://127.0.0.1:8766")
                .replaceAll("/+$", "");
    }

    String resourcePackPrompt() {
        return getConfig().getString("resource-pack-prompt", "Load external music?");
    }

    int maxAudioSeconds() {
        return getConfig().getInt("max-audio-seconds", 600);
    }
}
