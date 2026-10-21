package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

public final class ArmorBowRegressionRepair {
   private ArmorBowRegressionRepair() {
   }

   public static int apply(ResourceIndex resourceIndex0, Map<String, byte[]> packFiles1) {
      return resourceIndex0 != null && packFiles1 != null ? splitAmbiguousArmorColumns(resourceIndex0, packFiles1) + fixFirstPersonBowSprites(resourceIndex0, packFiles1) : 0;
   }

   public static int pruneAfterBake(Map<String, byte[]> packFiles0) {
      int number1 = 0;
      Iterator iterator2 = new ArrayList<>(packFiles0.keySet()).iterator();

      while (true) {
         String text3;
         Map<String, Object> mapping4;
         while (true) {
            if (!iterator2.hasNext()) {
               return number1;
            }

            text3 = (String)iterator2.next();
            if (text3.startsWith("render_controllers/an_") && text3.endsWith(".json")) {
               try {
                  mapping4 = obj(obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text3))).get("render_controllers"));
                  break;
               } catch (RuntimeException error11) {
               }
            }
         }

         if (mapping4.size() == 1) {
            boolean flag5 = false;

            for (String text7 : mapping4.keySet()) {
               if (!text7.startsWith("controller.render.twilight.armor.neutral.")) {
                  flag5 = true;
                  break;
               }

               byte[] resourceBytes8 = ("\"" + text7 + "\"").getBytes(StandardCharsets.UTF_8);

               for (var value10 : packFiles0.entrySet()) {
                  if (!((String)value10.getKey()).equals(text3) && ((String)value10.getKey()).endsWith(".json") && contains((byte[])value10.getValue(), resourceBytes8)) {
                     flag5 = true;
                     break;
                  }
               }

               if (flag5) {
                  break;
               }
            }

            if (!flag5 && packFiles0.remove(text3) != null) {
               number1++;
            }
         }
      }
   }

   private static boolean contains(byte[] resourceBytes0, byte[] resourceBytes1) {
      if (resourceBytes0 == null) {
         return false;
      }

      label28:
      for (int number2 = 0; number2 + resourceBytes1.length <= resourceBytes0.length; number2++) {
         for (int number3 = 0; number3 < resourceBytes1.length; number3++) {
            if (resourceBytes0[number2 + number3] != resourceBytes1[number3]) {
               continue label28;
            }
         }

         return true;
      }

      return false;
   }

   private static int splitAmbiguousArmorColumns(ResourceIndex resourceIndex0, Map<String, byte[]> packFiles1) {
      byte[] resourceBytes2 = (byte[])packFiles1.get("twilight/armor-conversion.json");
      if (resourceBytes2 == null) {
         return 0;
      }

      ArrayList<Wearable> arrayListWearable3 = new ArrayList<>();

      for (Object value5 : list(obj(BedrockAnimationBridge.Json.read(resourceBytes2)).get("items"))) {
         var jsonObject6 = obj(value5);
         String identifier7 = string(jsonObject6.get("identifier"));
         String modelName8 = string(jsonObject6.get("model"));
         String slotName9 = string(jsonObject6.get("slot"));
         String text10 = string(jsonObject6.get("atlas"));
         if (identifier7 != null && modelName8 != null && slotName9 != null && text10 != null && identifier7.startsWith("twilight:")) {
            String suffix11 = identifier7.substring(identifier7.indexOf(58) + 1);
            byte[] resourceBytes12 = (byte[])packFiles1.get("attachables/" + suffix11 + ".json");
            if (resourceBytes12 != null) {
               var renderControllers13 = list(
                  obj(obj(obj(BedrockAnimationBridge.Json.read(resourceBytes12)).get("minecraft:attachable")).get("description")).get("render_controllers")
               );
               if (renderControllers13.size() == 1 && renderControllers13.get(0) instanceof String text14) {
                  int separatorIndex45 = text14.lastIndexOf(".c");
                  if (separatorIndex45 >= 0) {
                     int number16;
                     try {
                        number16 = Integer.parseInt(text14.substring(separatorIndex45 + 2));
                     } catch (NumberFormatException error18) {
                        continue;
                     }

                     arrayListWearable3.add(new ArmorBowRegressionRepair.Wearable(identifier7, modelName8, slotName9, text10, number16));
                  }
               }
            }
         }
      }

      LinkedHashMap<String, List<Wearable>> lookup19 = new LinkedHashMap<>();

      for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable22 : arrayListWearable3) {
         lookup19.computeIfAbsent(family(armorBowRegressionRepairWearable22.model), var0x -> new ArrayList<>()).add(armorBowRegressionRepairWearable22);
      }

      HashMap lookup21 = new HashMap<>();

      for (var value25 : lookup19.entrySet()) {
         if ((value25.getValue()).size() == 4 && (value25.getValue()).stream().map(var0x -> var0x.slot).distinct().count() == 4L) {
            Boolean flag27 = null;
            boolean flag29 = true;

            for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable33 : value25.getValue()) {
               Boolean flag36 = usesOnlyVanillaIcon(resourceIndex0, armorBowRegressionRepairWearable33.model);
               if (flag36 == null) {
                  flag29 = false;
                  break;
               }

               if (flag27 == null) {
                  flag27 = flag36;
               } else if (!flag27.equals(flag36)) {
                  flag29 = false;
                  break;
               }
            }

            if (flag29) {
               lookup21.put((String)value25.getKey(), flag27);
            }
         }
      }

      int number24 = 0;

      for (var value28 : lookup19.entrySet()) {
         if (Boolean.TRUE.equals(lookup21.get(value28.getKey()))) {
            List<Wearable> entries30 = value28.getValue();
            if (entries30.size() == 4) {
               boolean flag32 = true;

               for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable37 : entries30) {
                  if (armorBowRegressionRepairWearable37.column <= 0) {
                     flag32 = false;
                     break;
                  }

                  boolean flag40 = false;

                  for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable46 : arrayListWearable3) {
                     if (!armorBowRegressionRepairWearable46.identifier.equals(armorBowRegressionRepairWearable37.identifier)
                        && armorBowRegressionRepairWearable46.column == armorBowRegressionRepairWearable37.column
                        && armorBowRegressionRepairWearable46.slot.equals(armorBowRegressionRepairWearable37.slot)
                        && armorBowRegressionRepairWearable46.atlas.equals(armorBowRegressionRepairWearable37.atlas)
                        && Boolean.FALSE.equals(lookup21.get(family(armorBowRegressionRepairWearable46.model)))) {
                        flag40 = true;
                        break;
                     }
                  }

                  if (!flag40) {
                     flag32 = false;
                     break;
                  }
               }

               if (flag32) {
                  Integer number35 = null;

                  for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable41 : entries30) {
                     Integer number44 = javaTint(resourceIndex0, armorBowRegressionRepairWearable41.slot, armorBowRegressionRepairWearable41.model);
                     if (number44 == null || number35 != null && !number35.equals(number44)) {
                        flag32 = false;
                        break;
                     }

                     number35 = number44;
                  }

                  if (flag32 && number35 != null) {
                     for (ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable42 : entries30) {
                        number24 += bindNeutralTint(packFiles1, armorBowRegressionRepairWearable42, number35);
                     }
                  }
               }
            }
         }
      }

      return number24;
   }

   private static int bindNeutralTint(Map<String, byte[]> packFiles0, ArmorBowRegressionRepair.Wearable armorBowRegressionRepairWearable1, int number2) {
      String suffix3 = armorBowRegressionRepairWearable1.identifier.substring(armorBowRegressionRepairWearable1.identifier.indexOf(58) + 1);
      byte[] resourceBytes4 = (byte[])packFiles0.get("attachables/" + suffix3 + ".json");
      if (resourceBytes4 == null) {
         return 0;
      }

      var attachable5 = obj(obj(obj(BedrockAnimationBridge.Json.read(resourceBytes4)).get("minecraft:attachable")).get("description"));
      var renderControllers6 = list(attachable5.get("render_controllers"));
      if (renderControllers6.size() == 1 && renderControllers6.get(0) instanceof String text7) {
         int separatorIndex24 = text7.lastIndexOf(".c");
         if (separatorIndex24 < 0) {
            return 0;
         }

         String suffix9 = text7.substring(0, separatorIndex24) + ".c0";
         Map mapping10 = null;

         for (var value12 : packFiles0.entrySet()) {
            if (((String)value12.getKey()).startsWith("render_controllers/armor_") && ((String)value12.getKey()).endsWith(".json")) {
               try {
                  Object value13 = obj(obj(BedrockAnimationBridge.Json.read((byte[])value12.getValue())).get("render_controllers")).get(suffix9);
                  if (value13 instanceof Map) {
                     mapping10 = obj(value13);
                     break;
                  }
               } catch (Exception error23) {
               }
            }
         }

         if (mapping10 == null) {
            return 0;
         }

         String text25 = hash(armorBowRegressionRepairWearable1.identifier);
         String text26 = "controller.render.twilight.armor.neutral." + text25;
         String controllerPath27 = "render_controllers/an_" + text25 + ".json";
         var value14 = new LinkedHashMap<>(mapping10);
         value14.put("color", Map.of("r", (number2 >> 16 & 0xFF) / 255.0, "g", (number2 >> 8 & 0xFF) / 255.0, "b", (number2 & 0xFF) / 255.0, "a", 1));
         byte[] resourceBytes15 = BedrockAnimationBridge.Json.bytes(Map.of("format_version", "1.8.0", "render_controllers", Map.<String, LinkedHashMap>of(text26, value14)));
         int number16 = 0;
         if (!Arrays.equals((byte[])packFiles0.get(controllerPath27), resourceBytes15)) {
            packFiles0.put(controllerPath27, resourceBytes15);
            number16++;
         }

         for (String text18 : List.of(".json", ".player.json")) {
            String attachablePath19 = "attachables/" + suffix3 + text18;
            byte[] resourceBytes20 = (byte[])packFiles0.get(attachablePath19);
            if (resourceBytes20 != null) {
               var jsonObject21 = obj(BedrockAnimationBridge.Json.read(resourceBytes20));
               var attachable22 = obj(obj(jsonObject21.get("minecraft:attachable")).get("description"));
               if (!list(attachable22.get("render_controllers")).equals(List.of(text26))) {
                  attachable22.put("render_controllers", List.of(text26));
                  packFiles0.put(attachablePath19, BedrockAnimationBridge.Json.bytes(jsonObject21));
                  number16++;
               }
            }
         }

         return number16;
      } else {
         return 0;
      }
   }

   private static Boolean usesOnlyVanillaIcon(ResourceIndex resourceIndex0, String text1) {
      var value2 = findModel(resourceIndex0, text1);
      if (value2.isEmpty()) {
         return null;
      }

      try {
         var textures3 = obj(obj(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value2.get()).readBytes())).get("textures"));
         if (textures3.isEmpty()) {
            return null;
         }

         boolean flag4 = false;
         boolean flag5 = false;

         for (Object value7 : textures3.values()) {
            String text8 = string(value7);
            if (text8 != null && !text8.startsWith("#") && !text8.endsWith(":item/empty") && !text8.endsWith("/empty")) {
               if (text8.startsWith("_minecraft:item/") || text8.startsWith("minecraft:item/")) {
                  flag4 = true;
               } else if (text8.startsWith("_") && text8.contains(":")) {
                  flag5 = true;
               }
            }
         }

         return flag5 ? false : flag4 ? true : null;
      } catch (Exception error9) {
         return null;
      }
   }

   private static Optional<ResourceIndex.Asset> findModel(ResourceIndex resourceIndex0, String text1) {
      String text2 = "minecraft";
      String text3 = text1;
      int separatorIndex4 = text1.indexOf(58);
      if (separatorIndex4 >= 0) {
         text2 = text1.substring(0, separatorIndex4);
         text3 = text1.substring(separatorIndex4 + 1);
      }

      return resourceIndex0.find("assets/" + text2 + "/models/" + text3 + ".json");
   }

   private static Integer javaTint(ResourceIndex resourceIndex0, String text1, String text2) {
      var value3 = resourceIndex0.find("assets/minecraft/items/leather_" + text1 + ".json");
      if (value3.isEmpty()) {
         return null;
      }

      try {
         Object value4 = obj(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value3.get()).readBytes())).get("model");
         return scanTint(value4, text2, 0);
      } catch (Exception error5) {
         return null;
      }
   }

   private static Integer scanTint(Object value0, String text1, int number2) {
      if (number2 <= 32 && value0 != null) {
         if (value0 instanceof Map mapping3) {
            var jsonObject5 = obj(mapping3);
            if (text1.equals(jsonObject5.get("model"))) {
               for (Object value7 : list(jsonObject5.get("tints"))) {
                  if (obj(value7).get("default") instanceof Number number9) {
                     return number9.intValue() & 16777215;
                  }
               }
            }

            for (Object value13 : jsonObject5.values()) {
               Integer number15 = scanTint(value13, text1, number2 + 1);
               if (number15 != null) {
                  return number15;
               }
            }
         } else if (value0 instanceof List) {
            for (Object value12 : (List)value0) {
               Integer number14 = scanTint(value12, text1, number2 + 1);
               if (number14 != null) {
                  return number14;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String family(String text0) {
      return text0.replaceFirst("(?i)(?:_?(?:helmet|chestplate|leggings|boots|chest|legs))$", "");
   }

   private static int fixFirstPersonBowSprites(ResourceIndex resourceIndex0, Map<String, byte[]> packFiles1) {
      byte[] resourceBytes2 = (byte[])packFiles1.get("twilight/geyser_item_mappings.json");
      if (resourceBytes2 == null) {
         return 0;
      }

      int number3 = 0;

      for (Object value5 : list(obj(obj(BedrockAnimationBridge.Json.read(resourceBytes2)).get("items")).get("minecraft:bow"))) {
         var jsonObject6 = obj(value5);
         String bedrockIdentifier7 = string(jsonObject6.get("bedrock_identifier"));
         if (bedrockIdentifier7 != null && bedrockIdentifier7.startsWith("twilight:") && Boolean.TRUE.equals(obj(jsonObject6.get("bedrock_options")).get("display_handheld"))) {
            String suffix8 = bedrockIdentifier7.substring(bedrockIdentifier7.indexOf(58) + 1);
            ArrayList<String> arrayListString9 = new ArrayList<>();

            for (String text11 : packFiles1.keySet()) {
               if (text11.startsWith("animations/" + suffix8 + "_state_") && text11.endsWith(".animation.json")) {
                  arrayListString9.add(text11);
               }
            }

            if (!arrayListString9.isEmpty()) {
               boolean flag24 = true;

               for (String text12 : arrayListString9) {
                  String animationName13 = text12.substring(("animations/" + suffix8).length(), text12.length() - ".animation.json".length());
                  String geometryName14 = "models/entity/geometry." + suffix8 + animationName13 + ".geo.json";
                  if (!flatPlane((byte[])packFiles1.get(geometryName14))) {
                     flag24 = false;
                     break;
                  }
               }

               if (flag24) {
                  for (String text27 : arrayListString9) {
                     var jsonObject28 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles1.get(text27)));
                     var animations29 = obj(jsonObject28.get("animations"));
                     boolean flag15 = false;

                     for (var value17 : animations29.entrySet()) {
                        if (((String)value17.getKey()).endsWith(".first_person_right") || ((String)value17.getKey()).endsWith(".first_person_left")) {
                           var jsonObject18 = obj(obj(obj(value17.getValue()).get("bones")).get("bone"));
                           var value19 = list(jsonObject18.get("position"));
                           if (value19.size() == 3 && value19.get(1) instanceof Number number20) {
                              double value30 = number20.doubleValue();
                              if (!(value30 < 12.0)) {
                                 var value23 = new ArrayList<>(value19);
                                 value23.set(1, value30 - 12.5);
                                 jsonObject18.put("position", value23);
                                 flag15 = true;
                              }
                           }
                        }
                     }

                     if (flag15) {
                        packFiles1.put(text27, BedrockAnimationBridge.Json.bytes(jsonObject28));
                        number3++;
                     }
                  }
               }
            }
         }
      }

      return number3;
   }

   private static boolean flatPlane(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         return false;
      }

      try {
         var jsonObject1 = list(obj(BedrockAnimationBridge.Json.read(resourceBytes0)).get("minecraft:geometry"));
         if (jsonObject1.size() != 1) {
            return false;
         }

         var jsonObject2 = list(obj(jsonObject1.get(0)).get("bones"));
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

         double value5 = Double.MAX_VALUE;
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

   private static String hash(String text0) {
      try {
         byte[] resourceBytes1 = MessageDigest.getInstance("SHA-256").digest(text0.getBytes(StandardCharsets.UTF_8));
         StringBuilder builder2 = new StringBuilder();

         for (int number3 = 0; number3 < 6; number3++) {
            builder2.append(String.format("%02x", resourceBytes1[number3] & 255));
         }

         return builder2.toString();
      } catch (Exception error4) {
         throw new IllegalStateException(error4);
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Collections.emptyMap();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : Collections.emptyList();
   }

   private static String string(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private record Wearable(String identifier, String model, String slot, String atlas, int column) {
   }
}
