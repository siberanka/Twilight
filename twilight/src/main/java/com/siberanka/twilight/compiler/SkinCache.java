/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.compiler;

import com.siberanka.twilight.geyser.HeadTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

/**
 * Player skins from Mojang's texture server, kept under {@code cache/skins/<hash>.png}: a head's display model draws
 * the skin's head. Only {@code textures.minecraft.net} is asked, by hash, and only PNG skins of Java's sizes are kept;
 * legacy 64x32 skins are widened to 64x64 so that every skin maps the same way.
 */
final class SkinCache {
    private static final int MAX_BYTES = 256 * 1024;
    private final Path directory;
    private final boolean download;
    private HttpClient client;

    SkinCache(Path directory, boolean download) {
        this.directory = directory;
        this.download = download;
    }

    /** The skin as a 64x64 PNG, or empty when it is neither cached nor downloadable. */
    Optional<byte[]> skin(String hash) {
        if (!HeadTextures.valid(hash)) return Optional.empty();
        Path file = directory.resolve(hash + ".png");
        try {
            if (Files.isRegularFile(file)) return Optional.of(Files.readAllBytes(file));
            if (!download) return Optional.empty();
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://textures.minecraft.net/texture/" + hash))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", "Twilight").GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > MAX_BYTES) return Optional.empty();
            byte[] png = normalized(response.body());
            if (png == null) return Optional.empty();
            Files.createDirectories(directory);
            Path temporary = directory.resolve(hash + ".png.tmp");
            Files.write(temporary, png);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(png);
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** The skin as a 64x64 PNG, or null when it is not a skin. */
    static byte[] normalized(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64 && image.getHeight() != 32) return null;
        BufferedImage square = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < 64; x++) square.setRGB(x, y, image.getRGB(x, y));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(square, "PNG", output);
        return output.toByteArray();
    }
}
