package com.siberanka.twilight.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class FishingRod3DCastLineRepair {
   private static final String HIDDEN_BONE = "twilight_cast_line";

   private FishingRod3DCastLineRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 == null) {
         return 0;
      }

      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return 0;
      }

      Map<String, Object> mapping2;
      try {
         mapping2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
      } catch (RuntimeException error39) {
         return 0;
      }

      var itemDefinitions3 = list(obj(mapping2.get("items")).get("minecraft:fishing_rod"));
      LinkedHashMap<String, String[]> lookup4 = new LinkedHashMap<>();

      for (Object entry6 : itemDefinitions3) {
         var jsonObject7 = obj(entry6);
         String identifier8 = str(jsonObject7.get("bedrock_identifier"));
         Object entry9 = jsonObject7.get("custom_model_data");
         if (identifier8 != null && entry9 instanceof Number) {
            String text10 = jsonObject7.getOrDefault("type", "") + ":" + ((Number)entry9).intValue();
            boolean flag11 = "fishing_rod_cast".equals(str(obj(jsonObject7.get("predicate")).get("property")));
            String[] segments12 = lookup4.computeIfAbsent(text10, var0x -> new String[2]);
            segments12[flag11 ? 1 : 0] = identifier8;
         }
      }

      int number40 = 0;

      for (String[] segments42 : lookup4.values()) {
         if (segments42[0] != null && segments42[1] != null) {
            String suffix43 = segments42[0].substring(segments42[0].indexOf(58) + 1);
            String suffix44 = segments42[1].substring(segments42[1].indexOf(58) + 1);
            String attachablePath45 = "attachables/" + suffix43 + ".json";
            String attachablePath46 = "attachables/" + suffix44 + ".json";
            if (packFiles0.containsKey(attachablePath45) && packFiles0.containsKey(attachablePath46)) {
               var value47 = description((byte[])packFiles0.get(attachablePath45));
               var value13 = description((byte[])packFiles0.get(attachablePath46));
               if (!value47.isEmpty() && !value13.isEmpty() && segments42[0].equals(str(value47.get("identifier"))) && segments42[1].equals(str(value13.get("identifier")))) {
                  String texturePath14 = str(obj(value47.get("textures")).get("java_anim_0"));
                  String texturePath15 = str(obj(value13.get("textures")).get("java_anim_0"));
                  if (texturePath14 != null && texturePath15 != null) {
                     BufferedImage image16 = read((byte[])packFiles0.get(texturePath14 + ".png"));
                     BufferedImage image17 = read((byte[])packFiles0.get(texturePath15 + ".png"));
                     if (image16 != null
                        && image17 != null
                        && image16.getWidth() == image17.getWidth()
                        && image16.getHeight() == image17.getHeight()
                        && image16.getWidth() >= 32
                        && image16.getHeight() >= 32) {
                        boolean[][] values18 = new boolean[image16.getHeight()][image16.getWidth()];
                        int number19 = 0;

                        for (int number20 = 0; number20 < image16.getHeight(); number20++) {
                           for (int number21 = 0; number21 < image16.getWidth(); number21++) {
                              boolean flag22 = image16.getRGB(number21, number20) != image17.getRGB(number21, number20);
                              values18[number20][number21] = flag22;
                              if (flag22) {
                                 number19++;
                              }
                           }
                        }

                        if (number19 >= 8) {
                           String geometryName48 = "models/entity/geometry." + suffix44 + ".geo.json";
                           String animationName49 = "animations/" + suffix44 + ".animation.json";
                           if (packFiles0.containsKey(geometryName48) && packFiles0.containsKey(animationName49)) {
                              var jsonObject50 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(geometryName48)));
                              var value23 = list(jsonObject50.get("minecraft:geometry"));
                              if (value23.size() == 1) {
                                 var jsonObject24 = obj(value23.get(0));
                                 var value25 = list(jsonObject24.get("bones"));
                                 if (value25.size() >= 2 && !value25.stream().anyMatch(var0x -> "twilight_cast_line".equals(str(obj(var0x).get("name"))))) {
                                    Map mapping26 = null;

                                    for (Object entry28 : value25) {
                                       var jsonObject29 = obj(entry28);
                                       if (list(jsonObject29.get("cubes")).size() >= 8) {
                                          if (mapping26 != null) {
                                             mapping26 = null;
                                             break;
                                          }

                                          mapping26 = jsonObject29;
                                       }
                                    }

                                    if (mapping26 != null) {
                                       var value51 = list(mapping26.get("cubes"));
                                       if (value51.size() >= 10) {
                                          ArrayList entries52 = new ArrayList<>();

                                          for (Object entry30 : value51) {
                                             var jsonObject31 = obj(entry30);
                                             var value32 = list(jsonObject31.get("size"));
                                             if (value32.size() == 3) {
                                                double value33 = num(value32.get(0));
                                                double value35 = num(value32.get(1));
                                                double value37 = num(value32.get(2));
                                                if (value33 <= 0.1 && value35 >= 5.0 && value37 >= 2.0 && overlapWithDiff(jsonObject31, values18) >= 8) {
                                                   entries52.add(entry30);
                                                }
                                             }
                                          }

                                          if (entries52.size() == 1) {
                                             var jsonObject54 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName49)));
                                             var animations55 = obj(jsonObject54.get("animations"));
                                             var animations56 = obj(value13.get("animations"));
                                             var jsonObject57 = obj(animations55.get(animations56.get("third_person_right")));
                                             var jsonObject58 = obj(animations55.get(animations56.get("third_person_left")));
                                             if (!jsonObject57.isEmpty() && !jsonObject58.isEmpty() && jsonObject57.containsKey("bones") && jsonObject58.containsKey("bones")) {
                                                var value34 = new ArrayList<>(value51);
                                                value34.removeAll(entries52);
                                                mapping26.put("cubes", value34);
                                                LinkedHashMap lookup59 = new LinkedHashMap<>();
                                                lookup59.put("name", "twilight_cast_line");
                                                lookup59.put("parent", mapping26.get("name"));
                                                lookup59.put("pivot", mapping26.getOrDefault("pivot", List.of(0, 8, 0)));
                                                lookup59.put("cubes", entries52);
                                                value25.add(lookup59);

                                                for (Map mapping61 : List.of(jsonObject57, jsonObject58)) {
                                                   var jsonObject38 = obj(mapping61.get("bones"));
                                                   jsonObject38.put("twilight_cast_line", Map.of("scale", List.of(0.001, 0.001, 0.001)));
                                                }

                                                byte[] resourceBytes60 = BedrockAnimationBridge.Json.bytes(jsonObject50);
                                                byte[] resourceBytes62 = BedrockAnimationBridge.Json.bytes(jsonObject54);
                                                if (!Arrays.equals((byte[])packFiles0.get(geometryName48), resourceBytes60)) {
                                                   packFiles0.put(geometryName48, resourceBytes60);
                                                   number40++;
                                                }

                                                if (!Arrays.equals((byte[])packFiles0.get(animationName49), resourceBytes62)) {
                                                   packFiles0.put(animationName49, resourceBytes62);
                                                   number40++;
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return number40;
   }

   private static int overlapWithDiff(Map<String, Object> mapping0, boolean[][] values1) {
      int number2 = 0;

      for (Object value4 : obj(mapping0.get("uv")).values()) {
         var jsonObject5 = obj(value4);
         var value6 = list(jsonObject5.get("uv"));
         var value7 = list(jsonObject5.get("uv_size"));
         if (value6.size() == 2 && value7.size() == 2) {
            double value8 = num(value6.get(0));
            double value10 = num(value6.get(1));
            double value12 = num(value7.get(0));
            double value14 = num(value7.get(1));
            int number16 = Math.max(0, (int)Math.floor(Math.min(value8, value8 + value12)));
            int number17 = Math.min(values1[0].length, (int)Math.ceil(Math.max(value8, value8 + value12)));
            int number18 = Math.max(0, (int)Math.floor(Math.min(value10, value10 + value14)));
            int number19 = Math.min(values1.length, (int)Math.ceil(Math.max(value10, value10 + value14)));

            for (int number20 = number18; number20 < number19; number20++) {
               for (int number21 = number16; number21 < number17; number21++) {
                  if (values1[number20][number21]) {
                     number2++;
                  }
               }
            }
         }
      }

      return number2;
   }

   private static BufferedImage read(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         return null;
      }

      try {
         return ImageIO.read(new ByteArrayInputStream(resourceBytes0));
      } catch (Exception error2) {
         return null;
      }
   }

   private static Map<String, Object> description(byte[] resourceBytes0) {
      try {
         var jsonObject1 = obj(BedrockAnimationBridge.Json.read(resourceBytes0));
         return obj(obj(jsonObject1.get("minecraft:attachable")).get("description"));
      } catch (Exception error2) {
         return Map.of();
      }
   }

   private static double num(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : Double.NaN;
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
}
