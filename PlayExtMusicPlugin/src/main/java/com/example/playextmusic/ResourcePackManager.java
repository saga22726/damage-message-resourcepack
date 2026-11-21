package com.example.playextmusic;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds one cumulative, versioned resource pack from every clip requested so far. */
final class ResourcePackManager {
    private final PlayExtMusicPlugin plugin;
    private final Object lock = new Object();
    private final Path clipsDirectory;
    private final Path packFile;

    ResourcePackManager(PlayExtMusicPlugin plugin) {
        this.plugin = plugin;
        clipsDirectory = plugin.packDirectory().resolve("clips");
        packFile = plugin.packDirectory().resolve(plugin.preloadPackName());
    }

    PackSnapshot prepare(Path source, double start, double end) throws Exception {
        synchronized (lock) {
            Files.createDirectories(clipsDirectory);
            String clipId = clipId(source, start, end);
            Path clip = clipsDirectory.resolve(clipId + ".ogg");
            boolean added = !Files.isRegularFile(clip) || Files.size(clip) == 0;

            if (added) {
                convert(source, start, end, clip);
            }

            if (added || !Files.isRegularFile(packFile)) {
                buildPack();
            }
            return snapshot(clipId);
        }
    }

    PackSnapshot current() throws Exception {
        synchronized (lock) {
            if (!Files.isRegularFile(packFile)) {
                return null;
            }
            return snapshot(null);
        }
    }

    private String clipId(Path source, double start, double end) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        digest.update(("\n" + Double.toString(start) + "\n" + Double.toString(end))
                .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private void convert(Path source, double start, double end, Path destination)
            throws IOException, InterruptedException {
        Path temporaryOgg = clipsDirectory.resolve(destination.getFileName() + ".tmp.ogg");
        Path diagnostic = clipsDirectory.resolve(destination.getFileName() + ".log");
        Files.deleteIfExists(temporaryOgg);
        Process process = new ProcessBuilder(
                plugin.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
                "-ss", Double.toString(start), "-i", source.toString(),
                "-t", Double.toString(end - start), "-vn", "-acodec", "libvorbis",
                "-q:a", "5", temporaryOgg.toString())
                .redirectErrorStream(true)
                .redirectOutput(diagnostic.toFile())
                .start();

        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            Files.deleteIfExists(temporaryOgg);
            throw new IOException("ffmpeg 轉換逾時。");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(temporaryOgg)
                || Files.size(temporaryOgg) == 0) {
            String message = Files.exists(diagnostic)
                    ? Files.readString(diagnostic, StandardCharsets.UTF_8) : "";
            Files.deleteIfExists(temporaryOgg);
            throw new IOException("ffmpeg 轉換失敗，請確認已安裝並支援 libvorbis。 " + message);
        }
        moveReplacing(temporaryOgg, destination);
        Files.deleteIfExists(diagnostic);
    }

    private void buildPack() throws Exception {
        List<Path> clips;
        try (var stream = Files.list(clipsDirectory)) {
            clips = stream.filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.ogg"))
                    .sorted()
                    .toList();
        }
        Path temporaryZip = packFile.resolveSibling(packFile.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporaryZip)))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(("{\"pack\":{\"pack_format\":64,\"description\":\"PlayExtMusic preloaded audio\"}}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            StringBuilder sounds = new StringBuilder("{");
            for (int i = 0; i < clips.size(); i++) {
                String id = clips.get(i).getFileName().toString().replaceFirst("\\.ogg$", "");
                if (i > 0) sounds.append(',');
                sounds.append('\"').append(id).append("\":{\"sounds\":[{\"name\":\"playextmusic:")
                        .append(id).append("\",\"stream\":true}]}");
            }
            sounds.append('}');
            zip.putNextEntry(new ZipEntry("assets/playextmusic/sounds.json"));
            zip.write(sounds.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            for (Path clip : clips) {
                String id = clip.getFileName().toString().replaceFirst("\\.ogg$", "");
                zip.putNextEntry(new ZipEntry("assets/playextmusic/sounds/" + id + ".ogg"));
                Files.copy(clip, zip);
                zip.closeEntry();
            }
        }
        moveReplacing(temporaryZip, packFile);
    }

    private PackSnapshot snapshot(String soundId) throws Exception {
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(packFile));
        return new PackSnapshot(soundId, sha1,
                HexFormat.of().formatHex(sha1),
                plugin.publicBaseUrl() + "/packs/" + packFile.getFileName());
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record PackSnapshot(String soundId, byte[] sha1, String version, String url) {
        PackSnapshot {
            sha1 = sha1.clone();
        }

        @Override
        public byte[] sha1() {
            return sha1.clone();
        }
    }
}
