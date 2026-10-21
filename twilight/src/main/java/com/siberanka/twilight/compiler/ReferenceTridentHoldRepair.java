package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;

public final class ReferenceTridentHoldRepair {
   private static final List<String> VIEWS = List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head");

   private ReferenceTridentHoldRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0, ResourceIndex resourceIndex1) {
      return apply(packFiles0, var1x -> {
         if (resourceIndex1 == null) {
            return null;
         }

         var value2 = resourceIndex1.find(var1x);
         if (value2.isEmpty()) {
            for (String text4 : resourceIndex1.paths()) {
               if (text4.endsWith("/" + var1x)) {
                  value2 = resourceIndex1.find(text4);
                  if (value2.isPresent()) {
                     break;
                  }
               }
            }
         }

         return value2.isPresent() ? ((ResourceIndex.Asset)value2.get()).readBytes() : null;
      });
   }

   public static int apply(Map<String, byte[]> packFiles0, ReferenceTridentHoldRepair.AssetReader referenceTridentHoldRepairAssetReader1) {
      if (packFiles0 != null && referenceTridentHoldRepairAssetReader1 != null && packFiles0.containsKey("twilight/geyser_item_mappings.json")) {
         Map<String, Object> mapping2;
         try {
            mapping2 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/geyser_item_mappings.json")));
         } catch (Exception error15) {
            return 0;
         }

         var itemDefinitions3 = arr(obj(mapping2.get("items")).get("minecraft:trident"));
         int number4 = 0;

         for (Object entry6 : itemDefinitions3) {
            var jsonObject7 = obj(entry6);
            String identifier8 = str(jsonObject7.get("bedrock_identifier"));
            if (identifier8 != null && identifier8.startsWith("twilight:")) {
               String suffix9 = identifier8.substring(identifier8.indexOf(58) + 1);
               String text10 = resolveItemModel(jsonObject7, referenceTridentHoldRepairAssetReader1);
               if (text10 != null) {
                  var value11 = readDisplay(text10, referenceTridentHoldRepairAssetReader1, new HashSet<>());
                  if (value11 != null) {
                     String text12 = resolveThrowingModel(jsonObject7, referenceTridentHoldRepairAssetReader1);
                     var value13 = text12 == null ? value11 : readDisplay(text12, referenceTridentHoldRepairAssetReader1, new HashSet<>());
                     if (value13 == null) {
                        value13 = value11;
                     }

                     boolean flag14 = !packFiles0.containsKey("attachables/" + suffix9 + ".json");
                     if (flag14) {
                        if (packFiles0.containsKey("textures/twilight/" + suffix9 + ".png")) {
                           if (value11.isEmpty()) {
                              if ("minecraft:item/trident".equals(text10)) {
                                 number4 += writeNativeTrident(packFiles0, identifier8, suffix9);
                              }
                           } else {
                              number4 += writeFlat(packFiles0, identifier8, suffix9, value11);
                           }
                        }
                     } else {
                        number4 += write3D(packFiles0, identifier8, suffix9, value11, value13);
                     }
                  }
               }
            }
         }

         return number4;
      } else {
         return 0;
      }
   }

   private static String resolveItemModel(Map<String, Object> mapping0, ReferenceTridentHoldRepair.AssetReader referenceTridentHoldRepairAssetReader1) {
      try {
         String text2 = str(mapping0.get("type"));
         if ("definition".equals(text2)) {
            String modelName12 = str(mapping0.get("model"));
            if (modelName12 == null) {
               return null;
            }

            String[] segments13 = split(modelName12);
            if (segments13 == null) {
               return null;
            }

            byte[] resourceBytes14 = referenceTridentHoldRepairAssetReader1.read("assets/" + segments13[0] + "/items/" + segments13[1] + ".json");
            if (resourceBytes14 != null) {
               String modelName15 = idleModel(obj(BedrockAnimationBridge.Json.read(resourceBytes14)).get("model"));
               if (modelName15 != null) {
                  return qualify(modelName15, segments13[0]);
               }
            }

            String suffix16 = segments13[1].substring(segments13[1].lastIndexOf(47) + 1);
            String text17 = segments13[0] + ":item/" + suffix16;
            if (hasAsset(text17, referenceTridentHoldRepairAssetReader1)) {
               return text17;
            }

            return null;
         }

         if ("legacy".equals(text2)) {
            Object value3 = mapping0.get("custom_model_data");
            byte[] resourceBytes4 = referenceTridentHoldRepairAssetReader1.read("assets/minecraft/models/item/trident.json");
            if (resourceBytes4 == null) {
               return null;
            }

            var jsonObject5 = obj(BedrockAnimationBridge.Json.read(resourceBytes4));

            for (Object value7 : arr(jsonObject5.get("overrides"))) {
               var jsonObject8 = obj(value7);
               var jsonObject9 = obj(jsonObject8.get("predicate"));
               if (value3 instanceof Number
                  && jsonObject9.get("custom_model_data") instanceof Number
                  && ((Number)value3).intValue() == ((Number)jsonObject9.get("custom_model_data")).intValue()
                  && !jsonObject9.containsKey("throwing")) {
                  String modelName10 = str(jsonObject8.get("model"));
                  if (modelName10 != null) {
                     return qualify(modelName10, "minecraft");
                  }
               }
            }
         }
      } catch (Exception error11) {
      }

      return null;
   }

   private static String resolveThrowingModel(Map<String, Object> mapping0, ReferenceTridentHoldRepair.AssetReader referenceTridentHoldRepairAssetReader1) {
      try {
         String text2 = str(mapping0.get("type"));
         if ("definition".equals(text2)) {
            String modelName11 = str(mapping0.get("model"));
            if (modelName11 == null) {
               return null;
            }

            String[] segments12 = split(modelName11);
            if (segments12 == null) {
               return null;
            }

            byte[] resourceBytes13 = referenceTridentHoldRepairAssetReader1.read("assets/" + segments12[0] + "/items/" + segments12[1] + ".json");
            if (resourceBytes13 == null) {
               return null;
            }

            String modelName14 = throwingModel(obj(BedrockAnimationBridge.Json.read(resourceBytes13)).get("model"));
            return modelName14 == null ? null : qualify(modelName14, segments12[0]);
         }

         if ("legacy".equals(text2)) {
            Object value3 = mapping0.get("custom_model_data");
            byte[] resourceBytes4 = referenceTridentHoldRepairAssetReader1.read("assets/minecraft/models/item/trident.json");
            if (resourceBytes4 == null) {
               return null;
            }

            for (Object value6 : arr(obj(BedrockAnimationBridge.Json.read(resourceBytes4)).get("overrides"))) {
               var jsonObject7 = obj(value6);
               var jsonObject8 = obj(jsonObject7.get("predicate"));
               if (value3 instanceof Number
                  && jsonObject8.get("custom_model_data") instanceof Number
                  && ((Number)value3).intValue() == ((Number)jsonObject8.get("custom_model_data")).intValue()
                  && jsonObject8.get("throwing") instanceof Number
                  && !(((Number)jsonObject8.get("throwing")).doubleValue() < 1.0)) {
                  String modelName9 = str(jsonObject7.get("model"));
                  if (modelName9 != null) {
                     return qualify(modelName9, "minecraft");
                  }
               }
            }
         }
      } catch (Exception error10) {
      }

      return null;
   }

   private static String throwingModel(Object value0) {
      if (!(value0 instanceof Map)) {
         return null;
      }

      var jsonObject1 = obj(value0);
      String text2 = str(jsonObject1.get("type"));
      if (!"condition".equals(text2) && !"minecraft:condition".equals(text2)) {
         if ("select".equals(text2) || "minecraft:select".equals(text2)) {
            String text6 = throwingModel(jsonObject1.get("fallback"));
            if (text6 != null) {
               return text6;
            }

            for (Object value5 : arr(jsonObject1.get("cases"))) {
               text6 = throwingModel(obj(value5).get("model"));
               if (text6 != null) {
                  return text6;
               }
            }
         }

         if ("range_dispatch".equals(text2) || "minecraft:range_dispatch".equals(text2)) {
            String text8 = throwingModel(jsonObject1.get("fallback"));
            if (text8 != null) {
               return text8;
            }

            for (Object value12 : arr(jsonObject1.get("entries"))) {
               text8 = throwingModel(obj(value12).get("model"));
               if (text8 != null) {
                  return text8;
               }
            }
         }

         return null;
      } else {
         String text3 = str(jsonObject1.get("property"));
         if (!"minecraft:using_item".equals(text3) && !"using_item".equals(text3)) {
            String text4 = throwingModel(jsonObject1.get("on_false"));
            return text4 != null ? text4 : throwingModel(jsonObject1.get("on_true"));
         } else {
            return idleModel(jsonObject1.get("on_true"));
         }
      }
   }

   private static String idleModel(Object value0) {
      if (!(value0 instanceof Map)) {
         return null;
      }

      var jsonObject1 = obj(value0);
      String text2 = str(jsonObject1.get("type"));
      if ("model".equals(text2) || "minecraft:model".equals(text2)) {
         return str(jsonObject1.get("model"));
      }

      if (!"condition".equals(text2) && !"minecraft:condition".equals(text2)) {
         if ("select".equals(text2) || "minecraft:select".equals(text2)) {
            String text6 = idleModel(jsonObject1.get("fallback"));
            if (text6 != null) {
               return text6;
            }

            for (Object value5 : arr(jsonObject1.get("cases"))) {
               text6 = idleModel(obj(value5).get("model"));
               if (text6 != null) {
                  return text6;
               }
            }
         }

         if ("range_dispatch".equals(text2) || "minecraft:range_dispatch".equals(text2)) {
            String text8 = idleModel(jsonObject1.get("fallback"));
            if (text8 != null) {
               return text8;
            }

            for (Object value11 : arr(jsonObject1.get("entries"))) {
               text8 = idleModel(obj(value11).get("model"));
               if (text8 != null) {
                  return text8;
               }
            }
         }

         return jsonObject1.containsKey("model") && jsonObject1.get("model") instanceof String ? str(jsonObject1.get("model")) : null;
      } else {
         String text3 = idleModel(jsonObject1.get("on_false"));
         return text3 != null ? text3 : idleModel(jsonObject1.get("on_true"));
      }
   }

   private static Map<String, Object> readDisplay(String text0, ReferenceTridentHoldRepair.AssetReader referenceTridentHoldRepairAssetReader1, Set<String> identifiers2) {
      if (!identifiers2.add(text0)) {
         return null;
      }

      String[] segments3 = split(text0);
      if (segments3 == null) {
         return null;
      }

      byte[] resourceBytes4;
      try {
         resourceBytes4 = referenceTridentHoldRepairAssetReader1.read("assets/" + segments3[0] + "/models/" + segments3[1] + ".json");
      } catch (Exception error11) {
         return null;
      }

      if (resourceBytes4 == null) {
         return null;
      }

      try {
         var jsonObject5 = obj(BedrockAnimationBridge.Json.read(resourceBytes4));
         var jsonObject6 = obj(jsonObject5.get("display"));
         String text7 = str(jsonObject5.get("parent"));
         LinkedHashMap lookup8 = new LinkedHashMap<>();
         if (text7 != null && !text7.startsWith("builtin/") && !text7.endsWith("item/generated") && !text7.endsWith("item/handheld")) {
            var value9 = readDisplay(qualify(text7, segments3[0]), referenceTridentHoldRepairAssetReader1, identifiers2);
            if (value9 != null) {
               lookup8.putAll(value9);
            }
         }

         lookup8.putAll(jsonObject6);
         return lookup8;
      } catch (RuntimeException error10) {
         return null;
      }
   }

   private static boolean hasAsset(String text0, ReferenceTridentHoldRepair.AssetReader referenceTridentHoldRepairAssetReader1) {
      String[] segments2 = split(text0);
      if (segments2 == null) {
         return false;
      }

      try {
         return referenceTridentHoldRepairAssetReader1.read("assets/" + segments2[0] + "/models/" + segments2[1] + ".json") != null;
      } catch (Exception error4) {
         return false;
      }
   }

   private static String qualify(String text0, String text1) {
      return text0.contains(":") ? text0 : text1 + ":" + text0;
   }

   private static String[] split(String text0) {
      if (text0 == null) {
         return null;
      }

      int separatorIndex1 = text0.indexOf(58);
      String suffix2 = separatorIndex1 >= 0 ? text0.substring(0, separatorIndex1) : "minecraft";
      String suffix3 = separatorIndex1 >= 0 ? text0.substring(separatorIndex1 + 1) : text0;
      return suffix2.matches("[a-z0-9_.-]+") && suffix3.matches("[a-z0-9_./-]+") && !suffix3.contains("..") ? new String[]{suffix2, suffix3} : null;
   }

   private static int write3D(Map<String, byte[]> packFiles0, String text1, String text2, Map<String, Object> mapping3, Map<String, Object> mapping4) {
      String attachablePath5 = "attachables/" + text2 + ".json";
      String geometryName6 = "models/entity/geometry." + text2 + ".geo.json";
      String animationName7 = "animations/" + text2 + ".animation.json";
      if (packFiles0.containsKey(geometryName6) && packFiles0.containsKey(animationName7)) {
         Map<String, Object> mapping8;
         Map<String, Object> mapping9;
         Map<String, Object> mapping10;
         try {
            mapping8 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(attachablePath5)));
            mapping9 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(geometryName6)));
            mapping10 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName7)));
         } catch (RuntimeException error28) {
            return 0;
         }

         var attachable11 = obj(obj(mapping8.get("minecraft:attachable")).get("description"));
         if (!text1.equals(attachable11.get("identifier"))) {
            return 0;
         }

         var value12 = arr(mapping9.get("minecraft:geometry"));
         if (value12.size() != 1) {
            return 0;
         }

         var jsonObject13 = arr(obj(value12.get(0)).get("bones"));
         HashSet identifiers14 = new HashSet<>();
         int number15 = 0;

         for (Object value17 : jsonObject13) {
            var jsonObject18 = obj(value17);
            identifiers14.add(str(jsonObject18.get("name")));
            number15 += arr(jsonObject18.get("cubes")).size();
         }

         if (number15 >= 2 && identifiers14.containsAll(List.of("bone", "bone_x", "bone_y", "bone_z", "bone_basis"))) {
            boolean flag29 = obj(attachable11.get("animations")).containsKey("wield") && identifiers14.contains("pole");
            var animations30 = obj(attachable11.get("animations"));
            var scripts31 = obj(attachable11.get("scripts"));
            var animations19 = obj(mapping10.get("animations"));
            var value20 = views(mapping3);
            var value21 = views(mapping4);
            int number22 = 0;
            if (flag29) {
               LinkedHashMap lookup23 = new LinkedHashMap<>();

               for (String text25 : VIEWS) {
                  String text26 = "animation.twilight." + text2 + "." + text25;
                  String text27 = "animation.twilight." + text2 + ".java_throw_offset_" + text25;
                  // Each view is a map of bone transformations, not a Boolean.
                  animations19.put(text26, Map.of("loop", true, "bones", value20.get(text25)));
                  animations19.put(text27, Map.of("loop", true, "bones", justDisplayBones(obj(value21.get(text25)))));
                  animations30.put(text25, text26);
                  animations30.put("use_offset_" + text25, text27);
                  lookup23.put(text25, text26);
               }

               ArrayList entries33 = new ArrayList<>();
               entries33.add(Map.of("wield", "q.is_using_item"));

               for (String text37 : VIEWS) {
                  String text39 = context(text37);
                  entries33.add(Map.of(text37, "!q.is_using_item && (" + text39 + ")"));
                  entries33.add(Map.of("use_offset_" + text37, "q.is_using_item && (" + text39 + ")"));
               }

               scripts31.put("animate", entries33);
               number22 += put(packFiles0, attachablePath5, BedrockAnimationBridge.Json.bytes(mapping8));
            } else {
               for (String text34 : VIEWS) {
                  String text36 = str(animations30.get(text34));
                  if (text36 != null && animations19.containsKey(text36)) {
                     var jsonObject38 = obj(animations19.get(text36));
                     jsonObject38.put("loop", true);
                     jsonObject38.put("bones", value20.get(text34));
                  }
               }
            }

            return number22 + put(packFiles0, animationName7, BedrockAnimationBridge.Json.bytes(mapping10));
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static Map<String, Object> views(Map<String, Object> mapping0) {
      LinkedHashMap lookup1 = new LinkedHashMap<>();

      for (String text3 : VIEWS) {
         var jsonObject10003 = obj(mapping0.get(switch (text3) {
            case "first_person_right" -> "firstperson_righthand";
            case "first_person_left" -> "firstperson_lefthand";
            case "third_person_right" -> "thirdperson_righthand";
            case "third_person_left" -> "thirdperson_lefthand";
            default -> "head";
         }));

         lookup1.put(text3, pose(text3, jsonObject10003, obj(mapping0.get(switch (text3) {
            case "first_person_left" -> "firstperson_righthand";
            case "third_person_left" -> "thirdperson_righthand";
            default -> "";
         }))));
      }

      return lookup1;
   }

   private static Map<String, Object> pose(String text0, Map<String, Object> mapping1, Map<String, Object> mapping2) {
      var value3 = mapping1.isEmpty() ? mapping2 : mapping1;
      var value4 = nums(value3.get("rotation"), 0.0, 0.0, 0.0);
      var value5 = nums(value3.get("translation"), 0.0, 0.0, 0.0);
      var value6 = nums(value3.get("scale"), 1.0, 1.0, 1.0);
      double value7 = num(value4.get(0));
      double value9 = num(value4.get(1));
      double value11 = num(value4.get(2));
      double value13 = num(value5.get(0));
      double value15 = num(value5.get(1));
      double value17 = num(value5.get(2));
      boolean flag19 = text0.endsWith("left");
      boolean flag20 = text0.startsWith("first_person");
      boolean flag21 = text0.equals("head");
      LinkedHashMap lookup22 = new LinkedHashMap<>();
      LinkedHashMap lookup23 = new LinkedHashMap<>();
      LinkedHashMap lookup24 = new LinkedHashMap<>();
      LinkedHashMap lookup25 = new LinkedHashMap<>();
      LinkedHashMap lookup26 = new LinkedHashMap<>();
      if (flag21) {
         lookup22.put("position", List.of(0, 19.9, 0));
         lookup23.put("scale", List.of(num(value6.get(0)) * 0.625, num(value6.get(1)) * 0.625, num(value6.get(2)) * 0.625));
         lookup23.put("position", List.of(-value13 * 0.625, value15 * 0.625, value17 * 0.625));
      } else if (flag20) {
         lookup22.put("rotation", flag19 ? List.of(180, 0, 180) : List.of(90, 60, -40));
         lookup22.put("position", flag19 ? List.of(-18.5, 18.3, 15) : List.of(0, 15, 2));
         lookup22.put("scale", flag19 ? List.of(1.2, 1.2, 1.2) : List.of(1.4, 1.4, 1.4));
         lookup23.put("position", List.of(flag19 ? value13 : -value13, value15, value17));
         lookup23.put("scale", value6);
      } else {
         lookup22.put("rotation", List.of(90, 0, 0));
         lookup22.put("position", List.of(0, 13, -3));
         lookup23.put("position", List.of(flag19 ? value13 : -value13, value15, value17));
         lookup23.put("scale", value6);
      }

      lookup23.put("rotation", List.of(-value7, 0, 0));
      lookup24.put("rotation", List.of(0, flag19 ? value9 : -value9, 0));
      lookup25.put("rotation", List.of(0, 0, flag19 ? -value11 : value11));
      lookup26.put("rotation", List.of(0, 180, 0));
      LinkedHashMap lookup27 = new LinkedHashMap<>();
      lookup27.put("bone", lookup22);
      lookup27.put("bone_x", lookup23);
      lookup27.put("bone_y", lookup24);
      lookup27.put("bone_z", lookup25);
      lookup27.put("bone_basis", lookup26);
      return lookup27;
   }

   private static Map<String, Object> justDisplayBones(Map<String, Object> mapping0) {
      var value1 = new LinkedHashMap<>(mapping0);
      value1.remove("bone");
      return value1;
   }

   private static String context(String text0) {
      return switch (text0) {
         case "first_person_right" -> "c.is_first_person && c.item_slot == 'main_hand'";
         case "first_person_left" -> "c.is_first_person && c.item_slot == 'off_hand'";
         case "third_person_right" -> "!c.is_first_person && c.item_slot == 'main_hand'";
         case "third_person_left" -> "!c.is_first_person && c.item_slot == 'off_hand'";
         default -> "!c.is_first_person && c.item_slot == 'head'";
      };
   }

   private static int writeNativeTrident(Map<String, byte[]> packFiles0, String text1, String text2) {
      String attachablePath3 = "attachables/" + text2 + ".json";
      if (packFiles0.containsKey(attachablePath3)) {
         return 0;
      }

      LinkedHashMap lookup4 = new LinkedHashMap<>();
      lookup4.put("identifier", text1);
      lookup4.put("materials", Map.of("default", "entity_alphatest", "enchanted", "entity_alphatest_glint"));
      lookup4.put("textures", Map.of("default", "textures/entity/trident", "enchanted", "textures/misc/enchanted_item_glint"));
      lookup4.put("geometry", Map.of("default", "geometry.trident"));
      LinkedHashMap lookup5 = new LinkedHashMap<>();
      lookup5.put("wield", "controller.animation.trident.wield");
      lookup5.put("wield_first_person", "animation.trident.wield_first_person");
      lookup5.put("wield_first_person_raise", "animation.trident.wield_first_person_raise");
      lookup5.put("wield_first_person_raise_shake", "animation.trident.wield_first_person_raise_shake");
      lookup5.put("wield_first_person_riptide", "animation.trident.wield_first_person_riptide");
      lookup5.put("wield_third_person", "animation.trident.wield_third_person");
      lookup5.put("wield_third_person_raise", "animation.trident.wield_third_person_raise");
      lookup4.put("animations", lookup5);
      LinkedHashMap lookup6 = new LinkedHashMap<>();
      lookup6.put(
         "pre_animation",
         List.of(
            "variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);"
         )
      );
      lookup6.put("animate", List.of("wield"));
      lookup4.put("scripts", lookup6);
      lookup4.put("render_controllers", List.of("controller.render.item_default"));
      LinkedHashMap lookup7 = new LinkedHashMap<>();
      lookup7.put("format_version", "1.10");
      lookup7.put("minecraft:attachable", Map.of("description", lookup4));
      packFiles0.put(attachablePath3, BedrockAnimationBridge.Json.bytes(lookup7));
      return 1;
   }

   private static int writeFlat(Map<String, byte[]> packFiles0, String text1, String text2, Map<String, Object> mapping3) {
      String attachablePath4 = "attachables/" + text2 + ".json";
      String geometryName5 = "models/entity/geometry." + text2 + ".geo.json";
      String animationName6 = "animations/" + text2 + ".animation.json";
      if (!packFiles0.containsKey(attachablePath4) && !packFiles0.containsKey(geometryName5) && !packFiles0.containsKey(animationName6)) {
         String text7 = "geometry.twilight." + text2;
         String text8 = "animation.twilight." + text2 + ".";
         LinkedHashMap lookup9 = new LinkedHashMap<>();
         lookup9.put("identifier", text1);
         lookup9.put("materials", Map.of("default", "entity_alphatest", "enchanted", "entity_alphatest_glint"));
         lookup9.put("textures", Map.of("default", "textures/twilight/" + text2, "enchanted", "textures/misc/enchanted_item_glint"));
         lookup9.put("geometry", Map.of("default", text7));
         LinkedHashMap lookup10 = new LinkedHashMap<>();
         LinkedHashMap lookup11 = new LinkedHashMap<>();
         ArrayList entries12 = new ArrayList<>();

         for (String text14 : VIEWS) {
            lookup10.put(text14, text8 + text14);

            var jsonObject15 = pose(text14, obj(mapping3.get(switch (text14) {
               case "first_person_right" -> "firstperson_righthand";
               case "first_person_left" -> "firstperson_lefthand";
               case "third_person_right" -> "thirdperson_righthand";
               case "third_person_left" -> "thirdperson_lefthand";
               default -> "head";
            })), Map.of());
            LinkedHashMap lookup21 = new LinkedHashMap<>();

            for (var value18 : jsonObject15.entrySet()) {
               if (!((String)value18.getKey()).equals("bone_basis")) {
                  lookup21.put(((String)value18.getKey()).replace("bone", "s_mc"), value18.getValue());
               }
            }

            lookup11.put(text8 + text14, Map.of("loop", true, "bones", lookup21));
            entries12.add(Map.of(text14, context(text14)));
         }

         lookup9.put("animations", lookup10);
         lookup9.put("scripts", Map.of("animate", entries12));
         lookup9.put("render_controllers", List.of("controller.render.item_default"));
         ArrayList entries19 = new ArrayList<>();
         entries19.add(
            new LinkedHashMap<>(
               Map.of("name", "s_mc", "binding", "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)", "pivot", List.<Integer>of(0, 8, 0))
            )
         );
         entries19.add(Map.of("name", "s_mc_x", "parent", "s_mc", "pivot", List.<Integer>of(0, 8, 0)));
         entries19.add(Map.of("name", "s_mc_y", "parent", "s_mc_x", "pivot", List.<Integer>of(0, 8, 0)));
         entries19.add(
            Map.of(
               "name",
               "s_mc_z",
               "parent",
               "s_mc_y",
               "pivot",
               List.<Integer>of(0, 8, 0),
               "texture_meshes",
               List.<Map<String, Object>>of(
                  Map.of(
                     "texture",
                     "default",
                     "position",
                     List.<Integer>of(0, 8, 0),
                     "rotation",
                     List.<Integer>of(90, 0, -180),
                     "local_pivot",
                     List.of(8, 0.5, 8)
                  )
               )
            )
         );
         var description20 = Map.of(
            "description",
            Map.of(
               "identifier",
               text7,
               "texture_width",
               16,
               "texture_height",
               16,
               "visible_bounds_width",
               4,
               "visible_bounds_height",
               4.5,
               "visible_bounds_offset",
               List.of(0, 0.75, 0)
            ),
            "bones",
            entries19
         );
         packFiles0.put(
            attachablePath4,
            BedrockAnimationBridge.Json.bytes(Map.of("format_version", "1.10.0", "minecraft:attachable", Map.<String, LinkedHashMap>of("description", lookup9)))
         );
         packFiles0.put(geometryName5, BedrockAnimationBridge.Json.bytes(Map.of("format_version", "1.21.0", "minecraft:geometry", List.<Map>of(description20))));
         packFiles0.put(animationName6, BedrockAnimationBridge.Json.bytes(Map.of("format_version", "1.10.0", "animations", lookup11)));
         return 3;
      } else {
         return 0;
      }
   }

   private static List<Object> nums(Object value0, double value1, double value3, double value5) {
      var value7 = arr(value0);
      return value7.size() == 3 ? value7 : List.of(value1, value3, value5);
   }

   private static double num(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : 0.0;
   }

   private static int put(Map<String, byte[]> packFiles0, String text1, byte[] resourceBytes2) {
      if (Arrays.equals((byte[])packFiles0.get(text1), resourceBytes2)) {
         return 0;
      }

      packFiles0.put(text1, resourceBytes2);
      return 1;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> arr(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   @FunctionalInterface
   public interface AssetReader {
      byte[] read(String text1) throws IOException;
   }
}
