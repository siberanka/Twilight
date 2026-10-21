package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;

public final class BowHandheldRepair {
   private static final String MAPPING_PATH = "twilight/geyser_item_mappings.json";

   private BowHandheldRepair() {
   }

   public static int apply(ResourceIndex resourceIndex0, Map<String, byte[]> packFiles1) {
      if (resourceIndex0 != null && packFiles1 != null && packFiles1.containsKey("twilight/geyser_item_mappings.json")) {
         var value2 = json((byte[])packFiles1.get("twilight/geyser_item_mappings.json"));
         var itemDefinitions3 = list(obj(value2.get("items")).get("minecraft:bow"));
         if (itemDefinitions3.isEmpty()) {
            return 0;
         }

         var value4 = resourceIndex0.find("assets/minecraft/models/item/bow.json");
         if (value4.isEmpty()) {
            return 0;
         }

         Map mapping5;
         try {
            mapping5 = json(((ResourceIndex.Asset)value4.get()).readBytes());
         } catch (Exception error32) {
            return 0;
         }

         var jsonObject6 = obj(mapping5.get("display"));
         if (!jsonObject6.containsKey("thirdperson_righthand")) {
            return 0;
         }

         LinkedHashMap lookup7 = new LinkedHashMap<>();
         lookup7.put("third_person_right", thirdPose(obj(jsonObject6.get("thirdperson_righthand"))));
         var jsonObject8 = obj(jsonObject6.getOrDefault("thirdperson_lefthand", jsonObject6.get("thirdperson_righthand")));
         lookup7.put("third_person_left", thirdPose(mirror(jsonObject8)));
         var value9 = flatBowCmds(resourceIndex0, mapping5);
         if (value9.isEmpty()) {
            return 0;
         }

         int number10 = 0;

         for (Object entry12 : itemDefinitions3) {
            var jsonObject13 = obj(entry12);
            if (jsonObject13.get("custom_model_data") instanceof Number number14 && value9.contains(number14.intValue())) {
               String bedrockIdentifier33 = string(jsonObject13.get("bedrock_identifier"));
               if (bedrockIdentifier33 != null && bedrockIdentifier33.startsWith("twilight:")) {
                  String suffix16 = bedrockIdentifier33.substring(bedrockIdentifier33.indexOf(58) + 1);
                  ArrayList<String> arrayListString17 = new ArrayList<>();

                  for (String text19 : packFiles1.keySet()) {
                     if (text19.startsWith("animations/" + suffix16 + "_state_") && text19.endsWith(".animation.json")) {
                        arrayListString17.add(text19);
                     }
                  }

                  if (arrayListString17.isEmpty()) {
                     String animationName34 = "animations/" + suffix16 + ".animation.json";
                     if (packFiles1.containsKey(animationName34)) {
                        arrayListString17.add(animationName34);
                     }
                  }

                  if (!arrayListString17.isEmpty()) {
                     boolean flag35 = true;

                     for (String text20 : arrayListString17) {
                        String animationName21 = text20.substring(("animations/" + suffix16).length(), text20.length() - ".animation.json".length());
                        String geometryName22 = "models/entity/geometry." + suffix16 + animationName21 + ".geo.json";
                        if (!flatGeometry((byte[])packFiles1.get(geometryName22))) {
                           flag35 = false;
                           break;
                        }
                     }

                     if (flag35) {
                        for (String text38 : arrayListString17) {
                           byte[] resourceBytes39 = (byte[])packFiles1.get(text38);
                           var value40 = json(resourceBytes39);
                           var animations23 = obj(value40.get("animations"));
                           boolean flag24 = false;

                           for (var value26 : animations23.entrySet()) {
                              for (String text28 : List.of("third_person_right", "third_person_left")) {
                                 if (((String)value26.getKey()).endsWith("." + text28)) {
                                    var jsonObject29 = obj(obj(value26.getValue()).get("bones"));
                                    Object entry30 = jsonObject29.get("bone");
                                    if (entry30 instanceof Map) {
                                       var jsonObject31 = obj(lookup7.get(text28));
                                       if (!samePose(obj(entry30), jsonObject31)) {
                                          jsonObject29.put("bone", new LinkedHashMap<>(jsonObject31));
                                          flag24 = true;
                                       }
                                    }
                                 }
                              }
                           }

                           if (flag24) {
                              packFiles1.put(text38, BedrockAnimationBridge.Json.bytes(value40));
                              number10++;
                           }
                        }
                     }
                  }
               }
            }
         }

         return number10;
      } else {
         return 0;
      }
   }

   public static Path writeFinalMapping(Path path0, byte[] resourceBytes1, OpenOption[] values2, Map<String, byte[]> packFiles3) throws IOException {
      if (!path0.getFileName().toString().equals("geyser_item_mappings.json")) {
         return Files.write(path0, resourceBytes1, values2);
      } else {
         byte[] resourceBytes4 = packFiles3.getOrDefault("twilight/geyser_item_mappings.json", resourceBytes1);
         if (resourceBytes4 == null) {
            throw new IOException("Twilight Geyser mapping was not generated");
         } else {
            return GeyserMappingPrettyPrinter.write(path0, resourceBytes4, values2);
         }
      }
   }

   private static Set<Integer> flatBowCmds(ResourceIndex resourceIndex0, Map<String, Object> mapping1) {
      LinkedHashMap<Integer, List<String>> lookup2 = new LinkedHashMap<>();
      var value3 = resourceIndex0.find("assets/minecraft/items/bow.json");
      if (value3.isPresent()) {
         try {
            var value4 = json(((ResourceIndex.Asset)value3.get()).readBytes());

            for (Object value6 : list(obj(value4.get("model")).get("entries"))) {
               var jsonObject7 = obj(value6);
               if (jsonObject7.get("threshold") instanceof Number number8) {
                  ArrayList entries21 = new ArrayList<>();
                  collectLeafModels(jsonObject7.get("model"), entries21, 0);
                  if (!entries21.isEmpty()) {
                     lookup2.put(number8.intValue(), entries21);
                  }
               }
            }
         } catch (Exception error10) {
         }
      }

      for (Object value13 : list(mapping1.get("overrides"))) {
         var jsonObject15 = obj(value13);
         Object value17 = obj(jsonObject15.get("predicate")).get("custom_model_data");
         String modelName19 = string(jsonObject15.get("model"));
         if (value17 instanceof Number number22 && modelName19 != null) {
            lookup2.computeIfAbsent(number22.intValue(), var0x -> new ArrayList<>()).add(modelName19);
         }
      }

      LinkedHashSet identifiers12 = new LinkedHashSet<>();

      for (var value16 : lookup2.entrySet()) {
         boolean flag18 = true;

         for (String text23 : new LinkedHashSet<>(value16.getValue())) {
            if (!generatedSpriteNoExplicitDisplay(resourceIndex0, text23, new HashSet<>())) {
               flag18 = false;
               break;
            }
         }

         if (flag18) {
            identifiers12.add((Integer)value16.getKey());
         }
      }

      return identifiers12;
   }

   private static void collectLeafModels(Object value0, List<String> entries1, int number2) {
      if (number2 <= 20 && value0 != null) {
         if (value0 instanceof List) {
            for (Object value9 : (List)value0) {
               collectLeafModels(value9, entries1, number2 + 1);
            }
         } else if (value0 instanceof Map) {
            var jsonObject3 = obj(value0);
            String modelName4 = string(jsonObject3.get("model"));
            if (modelName4 != null) {
               entries1.add(modelName4);
            } else {
               for (Object value6 : jsonObject3.values()) {
                  collectLeafModels(value6, entries1, number2 + 1);
               }
            }
         }
      }
   }

   private static boolean generatedSpriteNoExplicitDisplay(ResourceIndex resourceIndex0, String text1, Set<String> identifiers2) {
      if (text1 != null && identifiers2.add(text1) && identifiers2.size() <= 12) {
         String text3 = "minecraft";
         String text4 = text1;
         int separatorIndex5 = text1.indexOf(58);
         if (separatorIndex5 >= 0) {
            text3 = text1.substring(0, separatorIndex5);
            text4 = text1.substring(separatorIndex5 + 1);
         }

         if (text4.equals("item/generated") || text4.equals("builtin/generated")) {
            return true;
         }

         if (!text4.equals("item/handheld") && !text4.equals("builtin/entity")) {
            var value6 = resourceIndex0.find("assets/" + text3 + "/models/" + text4 + ".json");
            if (value6.isEmpty()) {
               return false;
            }

            try {
               var value7 = json(((ResourceIndex.Asset)value6.get()).readBytes());
               if (value7.containsKey("elements")) {
                  return false;
               }

               var jsonObject8 = obj(value7.get("display"));
               if (!jsonObject8.containsKey("thirdperson_righthand") && !jsonObject8.containsKey("thirdperson_lefthand")) {
                  String text9 = string(value7.get("parent"));
                  if (text9 == null) {
                     return false;
                  }

                  if (text9.indexOf(58) < 0) {
                     text9 = text3 + ":" + text9;
                  }

                  return generatedSpriteNoExplicitDisplay(resourceIndex0, text9, identifiers2);
               } else {
                  return false;
               }
            } catch (Exception error10) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean flatGeometry(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         return false;
      }

      try {
         var value1 = list(json(resourceBytes0).get("minecraft:geometry"));
         if (value1.size() != 1) {
            return false;
         }

         var jsonObject2 = list(obj(value1.get(0)).get("bones"));
         if (jsonObject2.size() != 1) {
            return false;
         }

         var jsonObject3 = list(obj(jsonObject2.get(0)).get("cubes"));
         if (jsonObject3.size() != 1) {
            return false;
         }

         var jsonObject4 = list(obj(jsonObject3.get(0)).get("size"));
         if (jsonObject4.size() != 3) {
            return false;
         }

         double value5 = Double.POSITIVE_INFINITY;
         double value7 = 0.0;

         for (Object value10 : jsonObject4) {
            if (!(value10 instanceof Number number11)) {
               return false;
            }

            double value12 = Math.abs(number11.doubleValue());
            value5 = Math.min(value5, value12);
            value7 = Math.max(value7, value12);
         }

         return value5 < 0.05 && value7 >= 8.0;
      } catch (Exception error14) {
         return false;
      }
   }

   private static Map<String, Object> thirdPose(Map<String, Object> mapping0) {
      double[] values1 = vector(mapping0.get("translation"), new double[]{0.0, 0.0, 0.0});
      double[] values2 = vector(mapping0.get("rotation"), new double[]{0.0, 0.0, 0.0});
      double[] values3 = vector(mapping0.get("scale"), new double[]{1.0, 1.0, 1.0});
      BowHandheldRepair.Q bowHandheldRepairQ4 = BowHandheldRepair.Q.axis(90.0, 1.0, 0.0, 0.0)
         .mul(BowHandheldRepair.Q.axis(-values2[0], 1.0, 0.0, 0.0))
         .mul(BowHandheldRepair.Q.axis(-values2[1], 0.0, 1.0, 0.0))
         .mul(BowHandheldRepair.Q.axis(values2[2], 0.0, 0.0, 1.0))
         .mul(BowHandheldRepair.Q.axis(180.0, 0.0, 1.0, 0.0));
      LinkedHashMap lookup5 = new LinkedHashMap<>();
      lookup5.put("position", List.of(-values1[0], 12.5 + values1[2], -values1[1]));
      lookup5.put("rotation", triple(bowHandheldRepairQ4.eulerXYZ()));
      lookup5.put("scale", triple(values3));
      return lookup5;
   }

   private static Map<String, Object> mirror(Map<String, Object> mapping0) {
      double[] values1 = vector(mapping0.get("translation"), new double[]{0.0, 0.0, 0.0});
      double[] values2 = vector(mapping0.get("rotation"), new double[]{0.0, 0.0, 0.0});
      var value3 = new LinkedHashMap<>(mapping0);
      value3.put("translation", List.of(-values1[0], values1[1], values1[2]));
      value3.put("rotation", List.of(values2[0], -values2[1], -values2[2]));
      return value3;
   }

   private static double[] vector(Object value0, double[] values1) {
      var value2 = list(value0);
      if (value2.size() != 3) {
         return values1;
      }

      double[] values3 = new double[3];

      for (int number5 = 0; number5 < 3; number5++) {
         if (!(value2.get(number5) instanceof Number number4)) {
            return values1;
         }

         values3[number5] = number4.doubleValue();
      }

      return values3;
   }

   private static List<Double> triple(double[] values0) {
      return List.of(values0[0], values0[1], values0[2]);
   }

   private static boolean samePose(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      for (String text3 : List.of("position", "rotation", "scale")) {
         double[] values4 = vector(mapping0.get(text3), new double[]{Double.NaN, Double.NaN, Double.NaN});
         double[] values5 = vector(mapping1.get(text3), new double[]{Double.NaN, Double.NaN, Double.NaN});

         for (int number6 = 0; number6 < 3; number6++) {
            if (!Double.isFinite(values4[number6]) || Math.abs(values4[number6] - values5[number6]) > 1.0E-6) {
               return false;
            }
         }
      }

      return true;
   }

   private static Map<String, Object> json(byte[] resourceBytes0) {
      return obj(BedrockAnimationBridge.Json.read(resourceBytes0));
   }

   private static String string(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Collections.emptyMap();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private record Q(double x, double y, double z, double w) {
      static BowHandheldRepair.Q axis(double value0, double value2, double value4, double value6) {
         double value8 = Math.toRadians(value0) / 2.0;
         double value10 = Math.sin(value8);
         return new BowHandheldRepair.Q(value2 * value10, value4 * value10, value6 * value10, Math.cos(value8));
      }

      BowHandheldRepair.Q mul(BowHandheldRepair.Q bowHandheldRepairQ1) {
         return new BowHandheldRepair.Q(
            this.w * bowHandheldRepairQ1.x + this.x * bowHandheldRepairQ1.w + this.y * bowHandheldRepairQ1.z - this.z * bowHandheldRepairQ1.y,
            this.w * bowHandheldRepairQ1.y - this.x * bowHandheldRepairQ1.z + this.y * bowHandheldRepairQ1.w + this.z * bowHandheldRepairQ1.x,
            this.w * bowHandheldRepairQ1.z + this.x * bowHandheldRepairQ1.y - this.y * bowHandheldRepairQ1.x + this.z * bowHandheldRepairQ1.w,
            this.w * bowHandheldRepairQ1.w - this.x * bowHandheldRepairQ1.x - this.y * bowHandheldRepairQ1.y - this.z * bowHandheldRepairQ1.z
         );
      }

      double[] eulerXYZ() {
         double value1 = this.x * this.x;
         double value3 = this.y * this.y;
         double value5 = this.z * this.z;
         double value7 = 1.0 - 2.0 * (value3 + value5);
         double value9 = 2.0 * (this.x * this.y - this.z * this.w);
         double value11 = 2.0 * (this.x * this.y + this.z * this.w);
         double value13 = 1.0 - 2.0 * (value1 + value5);
         double value15 = 2.0 * (this.x * this.z - this.y * this.w);
         double value17 = 2.0 * (this.y * this.z + this.x * this.w);
         double value19 = 1.0 - 2.0 * (value1 + value3);
         double value21 = Math.max(-1.0, Math.min(1.0, -value15));
         double value23 = Math.asin(value21);
         double value25;
         double value27;
         if (Math.abs(Math.cos(value23)) > 1.0E-7) {
            value25 = Math.atan2(value17, value19);
            value27 = Math.atan2(value11, value7);
         } else {
            value25 = Math.atan2(value21 * value9, value13);
            value27 = 0.0;
         }

         return new double[]{Math.toDegrees(value25), Math.toDegrees(value23), Math.toDegrees(value27)};
      }
   }
}
