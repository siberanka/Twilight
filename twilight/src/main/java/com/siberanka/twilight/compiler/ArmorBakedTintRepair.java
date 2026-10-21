package com.siberanka.twilight.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class ArmorBakedTintRepair {
   private static final String ARMOR_CONTROLLER_PATH = "render_controllers/";
   private static final String BAKED_ID = "controller.render.twilight.armor.baked.";

   private ArmorBakedTintRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         int number1 = 0;
         int number2 = 0;
         int number3 = 0;
         ArrayList entries4 = new ArrayList<>();

         for (String text6 : new ArrayList<>(packFiles0.keySet())) {
            if (text6.length() >= 80 && text6.startsWith("render_controllers/twilight_armor_fixed_") && text6.endsWith(".json")) {
               String controllerPath7 = "render_controllers/af_" + hash(text6, 16) + ".json";
               if (!packFiles0.containsKey(controllerPath7)) {
                  packFiles0.put(controllerPath7, (byte[])packFiles0.remove(text6));
                  number1++;
                  number3++;
               }
            }
         }

         Iterator iterator55 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            String text8;
            String text13;
            double value18;
            double value20;
            String text27;
            String text57;
            String text59;
            Map mapping60;
            double value62;
            byte[] resourceBytes64;
            while (true) {
               BufferedImage image29;
               while (true) {
                  Map<String, Object> mapping10;
                  while (true) {
                     if (!iterator55.hasNext()) {
                        if (number2 > 0 || number3 > 0) {
                           LinkedHashMap lookup56 = new LinkedHashMap<>();
                           lookup56.put("strategy", "Static armor tint baked in texture; animated armor untouched; short resource paths");
                           lookup56.put("baked", number2);
                           lookup56.put("shortened", number3);
                           lookup56.put("items", entries4);
                           byte[] resourceBytes58 = BedrockAnimationBridge.Json.bytes(lookup56);
                           if (different(packFiles0, "twilight/armor-baked-report.json", resourceBytes58)) {
                              packFiles0.put("twilight/armor-baked-report.json", resourceBytes58);
                              number1++;
                           }
                        }

                        return number1;
                     }

                     text57 = (String)iterator55.next();
                     if (text57.startsWith("attachables/") && text57.endsWith(".json") && !text57.endsWith(".player.json")) {
                        text59 = text57.substring("attachables/".length(), text57.length() - ".json".length());
                        if (text59.startsWith("_")) {
                        }

                        text8 = "attachables/" + text59 + ".player.json";
                        byte[] resourceBytes9 = (byte[])packFiles0.get(text57);
                        if (resourceBytes9 != null) {
                           try {
                              mapping10 = obj(BedrockAnimationBridge.Json.read(resourceBytes9));
                              break;
                           } catch (RuntimeException error49) {
                           }
                        }
                     }
                  }

                  var attachable11 = obj(obj(mapping10.get("minecraft:attachable")).get("description"));
                  var renderControllers12 = list(attachable11.get("render_controllers"));
                  if (renderControllers12.size() == 1
                     && renderControllers12.get(0) instanceof String
                     && !(text13 = (String) renderControllers12.get(0)).startsWith("controller.render.twilight.armor.baked.")
                     && text13.startsWith("controller.render.twilight.armor.")) {
                     mapping60 = null;

                     for (String text16 : new ArrayList<>(packFiles0.keySet())) {
                        if (text16.startsWith("render_controllers/") && text16.endsWith(".json")) {
                           try {
                              var renderControllers17 = obj(obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text16))).get("render_controllers"));
                              if (renderControllers17.containsKey(text13)) {
                                 mapping60 = obj(renderControllers17.get(text13));
                                 break;
                              }
                           } catch (RuntimeException error50) {
                           }
                        }
                     }

                     if (mapping60 != null) {
                        var color61 = obj(mapping60.get("color"));
                        if (color61.size() >= 3) {
                           value62 = unit(color61.get("r"), 1.0);
                           value18 = unit(color61.get("g"), 1.0);
                           value20 = unit(color61.get("b"), 1.0);
                           if (!(Math.abs(value62 - 1.0) < 1.0E-5) || !(Math.abs(value18 - 1.0) < 1.0E-5) || !(Math.abs(value20 - 1.0) < 1.0E-5)) {
                              var textures22 = obj(obj(mapping60.get("arrays")).get("textures"));
                              var value23 = list(textures22.get("Array.frames"));
                              if (value23.size() == 1 && value23.get(0) instanceof String text24 && text24.startsWith("Texture.")) {
                                 String suffix63 = text24.substring("Texture.".length());
                                 var textures26 = obj(attachable11.get("textures"));
                                 text27 = str(textures26.get(suffix63));
                                 if (text27 != null) {
                                    byte[] resourceBytes28 = (byte[])packFiles0.get(text27 + ".png");
                                    if (resourceBytes28 != null) {
                                       try {
                                          image29 = ImageIO.read(new ByteArrayInputStream(resourceBytes28));
                                          break;
                                       } catch (Exception error51) {
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (image29 != null) {
                  BufferedImage image30 = new BufferedImage(image29.getWidth(), image29.getHeight(), 2);

                  for (int number31 = 0; number31 < image29.getHeight(); number31++) {
                     for (int number32 = 0; number32 < image29.getWidth(); number32++) {
                        int number33 = image29.getRGB(number32, number31);
                        int number34 = number33 >>> 24 & 0xFF;
                        int number35 = scaled(number33 >>> 16 & 0xFF, value62);
                        int number36 = scaled(number33 >>> 8 & 0xFF, value18);
                        int number37 = scaled(number33 & 0xFF, value20);
                        image30.setRGB(number32, number31, number34 << 24 | number35 << 16 | number36 << 8 | number37);
                     }
                  }

                  try (ByteArrayOutputStream pngBuffer65 = new ByteArrayOutputStream()) {
                     if (ImageIO.write(image30, "png", pngBuffer65)) {
                        resourceBytes64 = pngBuffer65.toByteArray();
                        break;
                     }
                  } catch (Exception error53) {
                  }
               }
            }

            String text66 = hash(text59, 16);
            String texturePath67 = "textures/twilight/armor_baked_" + text66;
            String text68 = "controller.render.twilight.armor.baked." + text66;
            String controllerPath69 = "render_controllers/ab_" + text66 + ".json";
            var value70 = new LinkedHashMap<>(mapping60);
            value70.remove("arrays");
            ArrayList entries71 = new ArrayList<>();
            entries71.add("Texture.twilight_baked");
            var textures38 = list(mapping60.get("textures"));
            if (textures38.size() > 1) {
               entries71.addAll(textures38.subList(1, textures38.size()));
            }

            value70.put("textures", entries71);
            value70.remove("color");
            LinkedHashMap lookup39 = new LinkedHashMap<>();
            lookup39.put("format_version", "1.8.0");
            lookup39.put("render_controllers", Map.of(text68, value70));
            byte[] resourceBytes40 = BedrockAnimationBridge.Json.bytes(lookup39);
            if (different(packFiles0, texturePath67 + ".png", resourceBytes64)) {
               packFiles0.put(texturePath67 + ".png", resourceBytes64);
               number1++;
            }

            if (different(packFiles0, controllerPath69, resourceBytes40)) {
               packFiles0.put(controllerPath69, resourceBytes40);
               number1++;
            }

            for (String text42 : List.of(text57, text8)) {
               byte[] resourceBytes43 = (byte[])packFiles0.get(text42);
               if (resourceBytes43 != null) {
                  Map<String, Object> mapping44;
                  try {
                     mapping44 = obj(BedrockAnimationBridge.Json.read(resourceBytes43));
                  } catch (Exception error54) {
                     continue;
                  }

                  var attachable45 = obj(obj(mapping44.get("minecraft:attachable")).get("description"));
                  var renderControllers46 = list(attachable45.get("render_controllers"));
                  if (renderControllers46.size() == 1 && text13.equals(renderControllers46.get(0))) {
                     attachable45.put("render_controllers", List.of(text68));
                     obj(attachable45.get("textures")).put("twilight_baked", texturePath67);
                     byte[] resourceBytes47 = BedrockAnimationBridge.Json.bytes(mapping44);
                     if (different(packFiles0, text42, resourceBytes47)) {
                        packFiles0.put(text42, resourceBytes47);
                        number1++;
                     }
                  }
               }
            }

            number2++;
            entries4.add(
               Map.of(
                  "attachable",
                  text59,
                  "source",
                  text27,
                  "target",
                  texturePath67,
                  "color",
                  String.format(Locale.ROOT, "#%02X%02X%02X", scaled(255, value62), scaled(255, value18), scaled(255, value20))
               )
            );
         }
      } else {
         return 0;
      }
   }

   private static boolean different(Map<String, byte[]> packFiles0, String text1, byte[] resourceBytes2) {
      return !Arrays.equals((byte[])packFiles0.get(text1), resourceBytes2);
   }

   private static int scaled(int number0, double value1) {
      return Math.max(0, Math.min(255, (int)Math.round(number0 * value1)));
   }

   private static double unit(Object value0, double value1) {
      return value0 instanceof Number number3 ? number3.doubleValue() : value1;
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String hash(String text0, int number1) {
      try {
         byte[] resourceBytes2 = MessageDigest.getInstance("SHA-256").digest(text0.getBytes(StandardCharsets.UTF_8));
         StringBuilder builder3 = new StringBuilder(number1);

         for (byte number7 : resourceBytes2) {
            builder3.append(Character.forDigit(number7 >> 4 & 15, 16)).append(Character.forDigit(number7 & 15, 16));
            if (builder3.length() >= number1) {
               break;
            }
         }

         return builder3.substring(0, number1);
      } catch (Exception error8) {
         throw new IllegalStateException(error8);
      }
   }
}
