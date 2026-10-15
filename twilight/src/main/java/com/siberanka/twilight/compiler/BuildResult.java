package com.siberanka.twilight.compiler;

import java.nio.file.Path;
import java.util.List;

public record BuildResult(Path outputDirectory, int candidates, int converted, int threeDimensional,
                          int glyphs, int fontPages, int vanillaFallbackTextures,
                          int namedFonts, int namedGlyphs,
                          int soundDefinitions, int soundFiles, int vanillaFallbackSounds,
                          int skipped, List<String> problems, String packSha256) {}
