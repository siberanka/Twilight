package com.siberanka.twilight.compiler;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protected resource packs corrupt chunk CRCs and the zlib checksum, which Java's stb_image
 * ignores. Every colour type, bit depth, filter and Adam7 interlacing is checked against
 * the pixels the encoder wrote.
 */
class PngImagesTest {
    @Test
    void decodesProtectedPalettePngLikeJava() throws Exception {
        // The real case: 1x1, 1-bit palette with tRNS, all CRCs wrong, a private chunk, bad Adler-32.
        int[][] pixels = {{0x00000000}};
        byte[] png = encode(pixels, 3, 1, false, new int[]{0x000000}, new int[]{0}, true);
        BufferedImage image = PngImages.read(png);
        assertEquals(1, image.getWidth());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
    }

    @Test
    void decodesEveryColourTypeDepthFilterAndInterlace() throws Exception {
        Random random = new Random(7);
        for (boolean interlaced : new boolean[]{false, true}) {
            for (int[] format : new int[][]{{6, 8}, {6, 16}, {2, 8}, {2, 16}, {4, 8}, {4, 16},
                    {0, 1}, {0, 2}, {0, 4}, {0, 8}, {0, 16}, {3, 1}, {3, 2}, {3, 4}, {3, 8}}) {
                int colorType = format[0], depth = format[1];
                int width = 1 + random.nextInt(13), height = 1 + random.nextInt(11);
                int[] palette = null, alphas = null;
                if (colorType == 3) {
                    int entries = 1 << depth;
                    palette = new int[entries];
                    alphas = new int[entries];
                    for (int index = 0; index < entries; index++) {
                        palette[index] = random.nextInt(1 << 24);
                        alphas[index] = random.nextInt(256);
                    }
                }
                int[][] pixels = new int[height][width];
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    pixels[y][x] = random.nextInt(); // channel values are reduced to what the format holds
                }
                byte[] png = encode(pixels, colorType, depth, interlaced, palette, alphas, true);
                BufferedImage image = PngImages.decodeLenient(png);
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    assertEquals(expected(pixels[y][x], colorType, depth, palette, alphas), image.getRGB(x, y),
                            "type " + colorType + " depth " + depth + " interlaced " + interlaced + " at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void validImagesStillUseImageIoAndGarbageIsRejected() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 1, 0x80FF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(source, "PNG", out);
        assertEquals(0x80FF0000, PngImages.read(out.toByteArray()).getRGB(1, 1));
        assertNull(PngImages.read("not an image".getBytes(StandardCharsets.UTF_8)));
        byte[] truncated = encode(new int[][]{{1, 2, 3}}, 6, 8, false, null, null, true);
        byte[] cut = java.util.Arrays.copyOf(truncated, 40);
        assertThrows(IOException.class, () -> PngImages.read(cut));
    }

    // --- reference model and encoder -----------------------------------------------------------

    /** What a pixel value becomes after encoding in the format (samples from the ARGB bytes). */
    private static int expected(int argb, int colorType, int depth, int[] palette, int[] alphas) {
        int a = argb >>> 24, r = argb >>> 16 & 0xFF;
        int max = (1 << Math.min(depth, 8)) - 1;
        java.util.function.IntUnaryOperator reduce = v -> depth >= 8 ? v : (v * max / 255) * 255 / max;
        return switch (colorType) {
            case 6 -> argb;
            case 2 -> 0xFF000000 | argb & 0xFFFFFF;
            case 4 -> a << 24 | r << 16 | r << 8 | r;
            case 0 -> { int v = reduce.applyAsInt(r); yield 0xFF000000 | v << 16 | v << 8 | v; }
            default -> { int index = (argb & 0xFF) % palette.length; yield alphas[index] << 24 | palette[index]; }
        };
    }

    private static byte[] encode(int[][] pixels, int colorType, int depth, boolean interlaced, int[] palette,
                                 int[] alphas, boolean corrupt) throws IOException {
        int height = pixels.length, width = pixels[0].length;
        int channels = switch (colorType) { case 0, 3 -> 1; case 2 -> 3; case 4 -> 2; default -> 4; };
        int bitsPerPixel = channels * depth, stride = Math.max(1, bitsPerPixel / 8);
        int[][] passes = interlaced ? new int[][]{{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4},
                {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}} : new int[][]{{0, 0, 1, 1}};
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        int filterCycle = 0;
        for (int[] pass : passes) {
            int passWidth = width <= pass[0] ? 0 : (width - pass[0] + pass[2] - 1) / pass[2];
            int passHeight = height <= pass[1] ? 0 : (height - pass[1] + pass[3] - 1) / pass[3];
            if (passWidth == 0 || passHeight == 0) continue;
            int rowBytes = (passWidth * bitsPerPixel + 7) / 8;
            byte[] previous = new byte[rowBytes];
            for (int row = 0; row < passHeight; row++) {
                byte[] line = new byte[rowBytes];
                for (int column = 0; column < passWidth; column++) {
                    int argb = pixels[pass[1] + row * pass[3]][pass[0] + column * pass[2]];
                    int[] samples = samples(argb, colorType, depth, palette);
                    for (int channel = 0; channel < channels; channel++) {
                        put(line, column * channels + channel, depth, samples[channel]);
                    }
                }
                int filter = filterCycle++ % 5;
                raw.write(filter);
                raw.writeBytes(filter(filter, line, previous, stride));
                previous = line;
            }
        }
        Deflater deflater = new Deflater();
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream zlib = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!deflater.finished()) zlib.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        byte[] idat = zlib.toByteArray();
        if (corrupt) idat[idat.length - 1] ^= 0x5A; // Adler-32

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(png, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) depth)
                .put((byte) colorType).put((byte) 0).put((byte) 0).put((byte) (interlaced ? 1 : 0)).array(), corrupt);
        if (palette != null) {
            byte[] plte = new byte[palette.length * 3], trns = new byte[palette.length];
            for (int index = 0; index < palette.length; index++) {
                plte[index * 3] = (byte) (palette[index] >>> 16);
                plte[index * 3 + 1] = (byte) (palette[index] >>> 8);
                plte[index * 3 + 2] = (byte) palette[index];
                trns[index] = (byte) alphas[index];
            }
            chunk(png, "PLTE", plte, corrupt);
            chunk(png, "tRNS", trns, corrupt);
        }
        chunk(png, "iaPR", new byte[]{1, 2, 3, 4}, false);
        int half = idat.length / 2;
        chunk(png, "IDAT", java.util.Arrays.copyOfRange(idat, 0, half), corrupt);
        chunk(png, "IDAT", java.util.Arrays.copyOfRange(idat, half, idat.length), corrupt);
        chunk(png, "IEND", new byte[0], corrupt);
        return png.toByteArray();
    }

    private static int[] samples(int argb, int colorType, int depth, int[] palette) {
        int a = argb >>> 24, r = argb >>> 16 & 0xFF, g = argb >>> 8 & 0xFF, b = argb & 0xFF;
        int max = (1 << Math.min(depth, 8)) - 1;
        java.util.function.IntUnaryOperator scale = v -> depth == 16 ? v << 8 | v : depth == 8 ? v : v * max / 255;
        return switch (colorType) {
            case 6 -> new int[]{scale.applyAsInt(r), scale.applyAsInt(g), scale.applyAsInt(b), scale.applyAsInt(a)};
            case 2 -> new int[]{scale.applyAsInt(r), scale.applyAsInt(g), scale.applyAsInt(b)};
            case 4 -> new int[]{scale.applyAsInt(r), scale.applyAsInt(a)};
            case 0 -> new int[]{scale.applyAsInt(r)};
            default -> new int[]{b % palette.length};
        };
    }

    private static void put(byte[] line, int index, int depth, int value) {
        if (depth == 16) {
            line[index * 2] = (byte) (value >>> 8);
            line[index * 2 + 1] = (byte) value;
        } else if (depth == 8) {
            line[index] = (byte) value;
        } else {
            int bit = index * depth;
            line[bit >>> 3] |= (byte) (value << (8 - depth - (bit & 7)));
        }
    }

    private static byte[] filter(int type, byte[] line, byte[] previous, int stride) {
        byte[] out = new byte[line.length];
        for (int index = 0; index < line.length; index++) {
            int left = index >= stride ? line[index - stride] & 0xFF : 0;
            int up = previous[index] & 0xFF;
            int upLeft = index >= stride ? previous[index - stride] & 0xFF : 0;
            int predictor = switch (type) {
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) >>> 1;
                case 4 -> { int p = left + up - upLeft, pa = Math.abs(p - left), pb = Math.abs(p - up), pc = Math.abs(p - upLeft);
                    yield pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft; }
                default -> 0;
            };
            out[index] = (byte) ((line[index] & 0xFF) - predictor);
        }
        return out;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data, boolean corrupt) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue() ^ (corrupt ? 0x13579BDF : 0)).array());
    }
}
