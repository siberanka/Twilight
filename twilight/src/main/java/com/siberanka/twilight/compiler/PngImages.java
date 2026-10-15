/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads PNG textures the way Minecraft: Java Edition does.
 *
 * <p>Java decodes resource-pack PNGs with stb_image, which ignores chunk CRCs and the
 * zlib checksum. Pack "protection" tools rely on that and corrupt both, so the strict
 * {@link ImageIO} reader rejects textures every Java player sees. When ImageIO fails on
 * a PNG, this class decodes it leniently: all colour types and bit depths, palettes,
 * {@code tRNS} transparency and Adam7 interlacing, with CRCs and the zlib checksum ignored.
 */
final class PngImages {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final long MAX_PIXELS = 1L << 26;
    // Adam7 passes: x start, y start, x step, y step.
    private static final int[][] ADAM7 = {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4},
            {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}};

    private PngImages() {}

    /** Decodes an image, or returns null for data no reader understands (like {@link ImageIO#read}). */
    static BufferedImage read(byte[] bytes) throws IOException {
        IOException strictFailure = null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) return image;
        } catch (IOException | RuntimeException failure) {
            strictFailure = failure instanceof IOException io ? io : new IOException(failure.getMessage(), failure);
        }
        if (!isPng(bytes)) {
            if (strictFailure != null) throw strictFailure;
            return null;
        }
        try {
            return decodeLenient(bytes);
        } catch (IOException | RuntimeException lenientFailure) {
            IOException failure = new IOException("PNG could not be decoded: " + lenientFailure.getMessage(), lenientFailure);
            if (strictFailure != null) failure.addSuppressed(strictFailure);
            throw failure;
        }
    }

    static boolean isPng(byte[] bytes) {
        if (bytes.length < SIGNATURE.length) return false;
        for (int index = 0; index < SIGNATURE.length; index++) if (bytes[index] != SIGNATURE[index]) return false;
        return true;
    }

    static BufferedImage decodeLenient(byte[] bytes) throws IOException {
        if (!isPng(bytes)) throw new IOException("not a PNG");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(SIGNATURE.length);
        int width = 0, height = 0, depth = 0, colorType = -1, interlace = 0;
        byte[] palette = null, transparency = null;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            String type = new String(new byte[]{buffer.get(), buffer.get(), buffer.get(), buffer.get()},
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            if (length < 0 || length > buffer.remaining()) throw new IOException("truncated " + type + " chunk");
            byte[] data = new byte[length];
            buffer.get(data);
            if (buffer.remaining() >= 4) buffer.getInt(); // CRC, ignored like stb_image
            switch (type) {
                case "IHDR" -> {
                    if (length < 13) throw new IOException("short IHDR");
                    ByteBuffer header = ByteBuffer.wrap(data);
                    width = header.getInt();
                    height = header.getInt();
                    depth = header.get() & 0xFF;
                    colorType = header.get() & 0xFF;
                    header.get(); // compression method
                    header.get(); // filter method
                    interlace = header.get() & 0xFF;
                }
                case "PLTE" -> palette = data;
                case "tRNS" -> transparency = data;
                case "IDAT" -> compressed.writeBytes(data);
                default -> { }
            }
            if (type.equals("IEND")) break;
        }
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw new IOException("invalid size " + width + "x" + height);
        int channels = switch (colorType) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new IOException("unsupported colour type " + colorType);
        };
        boolean validDepth = switch (colorType) {
            case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
            case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
            default -> depth == 8 || depth == 16;
        };
        if (!validDepth) throw new IOException("unsupported bit depth " + depth + " for colour type " + colorType);
        if (colorType == 3 && palette == null) throw new IOException("palette image without PLTE");
        if (interlace > 1) throw new IOException("unknown interlace method " + interlace);

        int bitsPerPixel = channels * depth;
        int filterStride = Math.max(1, bitsPerPixel / 8);
        long expected = 0;
        int[][] passes = interlace == 1 ? ADAM7 : new int[][]{{0, 0, 1, 1}};
        for (int[] pass : passes) {
            int passWidth = passSize(width, pass[0], pass[2]), passHeight = passSize(height, pass[1], pass[3]);
            if (passWidth > 0 && passHeight > 0) expected += (long) passHeight * (1 + rowBytes(passWidth, bitsPerPixel));
        }
        if (expected > Integer.MAX_VALUE - 8) throw new IOException("image data too large");
        byte[] raw = inflate(compressed.toByteArray(), (int) expected);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int offset = 0;
        for (int[] pass : passes) {
            int passWidth = passSize(width, pass[0], pass[2]), passHeight = passSize(height, pass[1], pass[3]);
            if (passWidth == 0 || passHeight == 0) continue;
            int stride = rowBytes(passWidth, bitsPerPixel);
            byte[] previous = new byte[stride], current = new byte[stride];
            for (int row = 0; row < passHeight; row++) {
                int filter = raw[offset++] & 0xFF;
                System.arraycopy(raw, offset, current, 0, stride);
                offset += stride;
                unfilter(filter, current, previous, filterStride);
                for (int column = 0; column < passWidth; column++) {
                    image.setRGB(pass[0] + column * pass[2], pass[1] + row * pass[3],
                            argb(current, column, colorType, depth, channels, palette, transparency));
                }
                byte[] swap = previous;
                previous = current;
                current = swap;
            }
        }
        return image;
    }

    private static int passSize(int size, int start, int step) {
        return size <= start ? 0 : (size - start + step - 1) / step;
    }

    private static int rowBytes(int width, int bitsPerPixel) {
        return (int) (((long) width * bitsPerPixel + 7) / 8);
    }

    /** Raw deflate after the two-byte zlib header; the trailing Adler-32 is not verified. */
    private static byte[] inflate(byte[] zlib, int expected) throws IOException {
        if (zlib.length < 2) throw new IOException("missing image data");
        if ((zlib[0] & 0x0F) != 8 || (zlib[1] & 0x20) != 0) throw new IOException("unsupported zlib stream");
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(zlib, 2, zlib.length - 2);
            byte[] out = new byte[expected];
            int filled = 0;
            while (filled < expected) {
                int count = inflater.inflate(out, filled, expected - filled);
                if (count == 0) break; // finished, starved or dictionary-bound: nothing more to read
                filled += count;
            }
            if (filled < expected) throw new IOException("image data ends after " + filled + " of " + expected + " bytes");
            return out;
        } catch (DataFormatException failure) {
            throw new IOException("corrupt image data: " + failure.getMessage(), failure);
        } finally {
            inflater.end();
        }
    }

    private static void unfilter(int filter, byte[] row, byte[] previous, int stride) throws IOException {
        for (int index = 0; index < row.length; index++) {
            int left = index >= stride ? row[index - stride] & 0xFF : 0;
            int up = previous[index] & 0xFF;
            int upLeft = index >= stride ? previous[index - stride] & 0xFF : 0;
            int value = row[index] & 0xFF;
            value += switch (filter) {
                case 0 -> 0;
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) >>> 1;
                case 4 -> paeth(left, up, upLeft);
                default -> throw new IOException("unknown filter type " + filter);
            };
            row[index] = (byte) value;
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    /** Sample {@code index} of a row at the source bit depth (16-bit samples keep their full value). */
    private static int sample(byte[] row, int index, int depth) {
        return switch (depth) {
            case 16 -> (row[index * 2] & 0xFF) << 8 | row[index * 2 + 1] & 0xFF;
            case 8 -> row[index] & 0xFF;
            default -> {
                int bit = index * depth;
                yield (row[bit >>> 3] & 0xFF) >>> (8 - depth - (bit & 7)) & (1 << depth) - 1;
            }
        };
    }

    private static int to8(int value, int depth) {
        return switch (depth) {
            case 16 -> value >>> 8;
            case 8 -> value;
            default -> value * 255 / ((1 << depth) - 1);
        };
    }

    private static int argb(byte[] row, int column, int colorType, int depth, int channels, byte[] palette,
                            byte[] transparency) {
        int base = column * channels;
        switch (colorType) {
            case 3 -> {
                int index = sample(row, column, depth);
                if (index * 3 + 2 >= palette.length) return 0;
                int alpha = transparency != null && index < transparency.length ? transparency[index] & 0xFF : 255;
                return alpha << 24 | (palette[index * 3] & 0xFF) << 16 | (palette[index * 3 + 1] & 0xFF) << 8
                        | palette[index * 3 + 2] & 0xFF;
            }
            case 0 -> {
                int value = sample(row, base, depth);
                int gray = to8(value, depth);
                boolean keyed = transparency != null && transparency.length >= 2
                        && value == ((transparency[0] & 0xFF) << 8 | transparency[1] & 0xFF);
                return (keyed ? 0 : 0xFF000000) | gray << 16 | gray << 8 | gray;
            }
            case 4 -> {
                int gray = to8(sample(row, base, depth), depth);
                return to8(sample(row, base + 1, depth), depth) << 24 | gray << 16 | gray << 8 | gray;
            }
            case 2 -> {
                int r = sample(row, base, depth), g = sample(row, base + 1, depth), b = sample(row, base + 2, depth);
                boolean keyed = transparency != null && transparency.length >= 6
                        && r == ((transparency[0] & 0xFF) << 8 | transparency[1] & 0xFF)
                        && g == ((transparency[2] & 0xFF) << 8 | transparency[3] & 0xFF)
                        && b == ((transparency[4] & 0xFF) << 8 | transparency[5] & 0xFF);
                return (keyed ? 0 : 0xFF000000) | to8(r, depth) << 16 | to8(g, depth) << 8 | to8(b, depth);
            }
            default -> {
                return to8(sample(row, base + 3, depth), depth) << 24 | to8(sample(row, base, depth), depth) << 16
                        | to8(sample(row, base + 1, depth), depth) << 8 | to8(sample(row, base + 2, depth), depth);
            }
        }
    }
}
