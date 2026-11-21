package com.example.playextmusic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.util.StringUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayExtMusicCommand implements CommandExecutor, TabCompleter, Listener {
    private final PlayExtMusicPlugin plugin;
    private final Map<UUID, PackRequest> requests = new HashMap<>();
    private final Map<UUID, String> loadedPackVersions = new HashMap<>();

    PlayExtMusicCommand(PlayExtMusicPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3 && args.length != 5 && args.length != 8
                && args.length != 9 && args.length != 10) {
            sender.sendMessage("用法：/playextmusic <file> <start> <end> [source targets [x y z [volume [minVolume]]]]");
            sender.sendMessage("時間格式：秒數或 mm:ss；選項需按順序提供。");
            return true;
        }

        try {
            Path input = resolveAudio(args[0]);
            double start = parseTime(args[1]);
            double end = parseTime(args[2]);
            if (end <= start) throw new IllegalArgumentException("結束時間必須大於開始時間。");
            if (end - start > plugin.maxAudioSeconds()) {
                throw new IllegalArgumentException("音訊片段超過設定的長度上限。");
            }

            SoundCategory category = args.length >= 5 ? parseCategory(args[3]) : SoundCategory.MASTER;
            List<Player> players = args.length >= 5
                    ? selectPlayers(sender, args[4])
                    : sender instanceof Player player ? List.of(player) : List.of();
            if (players.isEmpty()) throw new IllegalArgumentException("沒有找到可接收音效的玩家目標。");

            Location origin = sender instanceof Entity entity
                    ? entity.getLocation().clone() : players.getFirst().getLocation().clone();
            Location location = args.length >= 8
                    ? new Location(origin.getWorld(), coordinate(args[5], origin.getX()),
                    coordinate(args[6], origin.getY()), coordinate(args[7], origin.getZ()))
                    : origin;
            float volume = args.length >= 9 ? parseNonNegativeFloat(args[8], "volume") : 1.0f;
            float minVolume = args.length >= 10 ? parseNonNegativeFloat(args[9], "minVolume") : 0.0f;
            if (minVolume > 1.0f) throw new IllegalArgumentException("minVolume 必須介於 0 和 1。");

            sender.sendMessage("正在轉換音訊並更新共用資源包（新音效首次載入時才會重新載入）…");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ResourcePackManager.PackSnapshot pack = plugin.resourcePackManager()
                            .prepare(input, start, end);
                    Playback playback = new Playback("playextmusic:" + pack.soundId(), pack,
                            category, location.clone(), volume, minVolume);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player player : players) {
                            if (!player.isOnline()) continue;
                            if (pack.version().equals(loadedPackVersions.get(player.getUniqueId()))) {
                                play(player, playback);
                            } else {
                                requestPack(player, pack, playback);
                            }
                        }
                        sender.sendMessage("音效已加入共用資源包；目標玩家載入後播放。重播相同片段不會再傳送資源包。");
                    });
                } catch (Exception exception) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> sender.sendMessage("音訊處理失敗：" + exception.getMessage()));
                    plugin.getLogger().warning("Failed to process external audio: " + exception.getMessage());
                }
            });
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("錯誤：" + exception.getMessage());
        } catch (IOException exception) {
            sender.sendMessage("音訊檔案錯誤：" + exception.getMessage());
        }
        return true;
    }

    private void requestPack(Player player, ResourcePackManager.PackSnapshot pack, Playback playback) {
        UUID id = player.getUniqueId();
        requests.put(id, new PackRequest(pack, playback));
        player.setResourcePack(pack.url(), pack.sha1(), plugin.resourcePackPrompt(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            try {
                ResourcePackManager.PackSnapshot pack = plugin.resourcePackManager().current();
                if (pack != null && !pack.version().equals(loadedPackVersions.get(player.getUniqueId()))) {
                    requestPack(player, pack, null);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Unable to preload music pack for " + player.getName()
                        + ": " + exception.getMessage());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.remove(id);
        loadedPackVersions.remove(id);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PackRequest request = requests.remove(id);
        if (request == null) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            loadedPackVersions.put(id, request.pack().version());
            if (request.playback() != null) play(event.getPlayer(), request.playback());
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            event.getPlayer().sendMessage("音樂資源包未載入，因此這次音效沒有播放。");
        }
    }

    private void play(Player player, Playback playback) {
        if (!player.getWorld().equals(playback.location().getWorld())) {
            player.sendMessage("音訊資源包已載入，但玩家與音效位置不在同一個世界，未播放。");
            return;
        }
        Location at = playback.location();
        double maxDistance = playback.volume() * 16.0;
        if (playback.minVolume() > 0
                && player.getLocation().distanceSquared(at) > maxDistance * maxDistance) {
            player.playSound(player.getLocation(), playback.sound(), playback.category(),
                    playback.minVolume(), 1.0f);
        } else {
            player.playSound(at, playback.sound(), playback.category(), playback.volume(), 1.0f);
        }
    }

    private Path resolveAudio(String requested) throws IOException {
        if (requested.contains("/") || requested.contains("\\") || requested.contains("..")) {
            throw new IllegalArgumentException("請只輸入 musicSrc 資料夾內的檔名，不要輸入路徑。");
        }
        Path file = plugin.musicDirectory().resolve(requested).normalize();
        if (!file.startsWith(plugin.musicDirectory()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("找不到音訊檔：" + requested);
        }
        return file;
    }

    private List<Player> selectPlayers(CommandSender sender, String selector) {
        List<Player> result = new ArrayList<>();
        for (Entity entity : Bukkit.selectEntities(sender, selector)) {
            if (entity instanceof Player player) result.add(player);
        }
        return result;
    }

    private SoundCategory parseCategory(String text) {
        try {
            return SoundCategory.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("無效的音訊來源：" + text
                    + "；請使用 master、music、record、weather、block、hostile、neutral、player、ambient 或 voice。");
        }
    }

    private static double parseTime(String value) {
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                double minutes = Double.parseDouble(parts[0]);
                double seconds = Double.parseDouble(parts[1]);
                if (minutes < 0 || seconds < 0 || seconds >= 60) throw new IllegalArgumentException();
                return minutes * 60 + seconds;
            }
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException();
            return seconds;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("時間格式無效，使用秒數或 mm:ss，例如 0:20。");
        }
    }

    private static double coordinate(String value, double base) {
        if (value.startsWith("~")) {
            return base + (value.length() == 1 ? 0 : Double.parseDouble(value.substring(1)));
        }
        return Double.parseDouble(value);
    }

    private static float parseNonNegativeFloat(String value, String name) {
        float parsed;
        try {
            parsed = Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " 必須是有效數字。");
        }
        if (!Float.isFinite(parsed) || parsed < 0) throw new IllegalArgumentException(name + " 必須是非負數。");
        return parsed;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            try (var files = Files.list(plugin.musicDirectory())) {
                return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                        .filter(name -> StringUtil.startsWithIgnoreCase(name, args[0])).toList();
            } catch (IOException ignored) {
                return List.of();
            }
        }
        if (args.length == 4) {
            return StringUtil.copyPartialMatches(args[3], List.of("master", "music", "record", "weather",
                    "block", "hostile", "neutral", "player", "ambient", "voice"), new ArrayList<>());
        }
        if (args.length == 5) {
            return StringUtil.copyPartialMatches(args[4], List.of("@a", "@p", "@r", "@s"), new ArrayList<>());
        }
        if (args.length >= 6 && args.length <= 8) return List.of("~");
        if (args.length == 9 || args.length == 10) return List.of("1");
        return List.of();
    }

    private record Playback(String sound, ResourcePackManager.PackSnapshot pack,
                            SoundCategory category, Location location, float volume, float minVolume) { }
    private record PackRequest(ResourcePackManager.PackSnapshot pack, Playback playback) { }
}
