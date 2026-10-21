package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VanillaTridentWieldRepair {
   private static final Map<String, String> VANILLA_ANIMS = Map.of(
      "wield",
      "controller.animation.trident.wield",
      "wield_first_person",
      "animation.trident.wield_first_person",
      "wield_first_person_raise",
      "animation.trident.wield_first_person_raise",
      "wield_first_person_raise_shake",
      "animation.trident.wield_first_person_raise_shake",
      "wield_first_person_riptide",
      "animation.trident.wield_first_person_riptide",
      "wield_third_person",
      "animation.trident.wield_third_person",
      "wield_third_person_raise",
      "animation.trident.wield_third_person_raise"
   );
   private static final String CHARGE = "variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);";

   private VanillaTridentWieldRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         var value1 = mappedTridentIds(packFiles0);
         if (value1.isEmpty()) {
            return 0;
         }

         int number2 = 0;
         Iterator iterator3 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            String text4;
            Map<String, Object> mapping5;
            Map<String, Object> mapping6;
            String text8;
            String text9;
            byte[] resourceBytes10;
            Map<String, Object> mapping11;
            while (true) {
               while (true) {
                  if (!iterator3.hasNext()) {
                     return number2;
                  }

                  text4 = (String)iterator3.next();
                  if (text4.startsWith("attachables/") && text4.endsWith(".json")) {
                     try {
                        mapping5 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text4)));
                        break;
                     } catch (RuntimeException error22) {
                     }
                  }
               }

               mapping6 = obj(obj(mapping5.get("minecraft:attachable")).get("description"));
               String identifier7 = str(mapping6.get("identifier"));
               if (identifier7 != null && value1.contains(identifier7)) {
                  text8 = identifier7.substring(identifier7.indexOf(58) + 1);
                  text9 = "models/entity/geometry." + text8 + ".geo.json";
                  resourceBytes10 = (byte[])packFiles0.get(text9);
                  if (resourceBytes10 != null) {
                     try {
                        mapping11 = obj(BedrockAnimationBridge.Json.read(resourceBytes10));
                        break;
                     } catch (RuntimeException error23) {
                     }
                  }
               }
            }

            var value12 = list(mapping11.get("minecraft:geometry"));
            if (value12.size() == 1) {
               var jsonObject13 = obj(value12.get(0));
               var value14 = list(jsonObject13.get("bones"));
               if (!value14.isEmpty()) {
                  Map mapping15 = null;
                  Map mapping16 = null;

                  for (Object entry18 : value14) {
                     var jsonObject19 = obj(entry18);
                     if ("pole".equals(jsonObject19.get("name"))) {
                        mapping15 = jsonObject19;
                     }

                     if ("bone_basis".equals(jsonObject19.get("name"))) {
                        mapping16 = jsonObject19;
                     }
                  }

                  if (mapping15 != null && hasPoleBinding(mapping15) && VANILLA_ANIMS.get("wield").equals(obj(mapping6.get("animations")).get("wield"))) {
                     if (mapping16 != null) {
                        var value24 = list(mapping16.get("rotation"));
                        if (!isRotation(value24, 0.0, 180.0, 0.0)) {
                           var value26 = previousBasis(packFiles0, text8, mapping6);
                           if (value26 == null) {
                              continue;
                           }

                           mapping16.put("rotation", new ArrayList<>(value26));
                           byte[] resourceBytes28 = BedrockAnimationBridge.Json.bytes(mapping11);
                           if (!Arrays.equals(resourceBytes10, resourceBytes28)) {
                              packFiles0.put(text9, resourceBytes28);
                              number2++;
                           }
                        }
                     }

                     var scripts25 = obj(mapping6.get("scripts"));
                     ArrayList entries27 = new ArrayList<>();
                     entries27.add("wield");
                     if (!entries27.equals(scripts25.get("animate"))) {
                        scripts25.put("animate", entries27);
                     }

                     var animationScripts29 = list(scripts25.get("pre_animation"));
                     if (animationScripts29.stream().noneMatch(var0x -> String.valueOf(var0x).contains("variable.charge_amount"))) {
                        var value20 = new ArrayList<>(animationScripts29);
                        value20.add(
                           "variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);"
                        );
                        scripts25.put("pre_animation", value20);
                     }

                     var animations30 = obj(mapping6.get("animations"));
                     if (!animations30.equals(VANILLA_ANIMS)) {
                        animations30.clear();
                        animations30.putAll(VANILLA_ANIMS);
                     }

                     byte[] resourceBytes21 = BedrockAnimationBridge.Json.bytes(mapping5);
                     if (!Arrays.equals((byte[])packFiles0.get(text4), resourceBytes21)) {
                        packFiles0.put(text4, resourceBytes21);
                        number2++;
                     }
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static List<Object> previousBasis(Map<String, byte[]> packFiles0, String text1, Map<String, Object> mapping2) {
      String animationName3 = "animations/" + text1 + ".animation.json";
      byte[] resourceBytes4 = (byte[])packFiles0.get(animationName3);
      if (resourceBytes4 == null) {
         return null;
      }

      try {
         var jsonObject5 = obj(BedrockAnimationBridge.Json.read(resourceBytes4));
         var animations6 = obj(mapping2.get("animations"));
         String text7 = str(animations6.get("first_person_right"));
         if (text7 == null) {
            return null;
         }

         var animations8 = obj(obj(obj(jsonObject5.get("animations")).get(text7)).get("bones"));
         var jsonObject9 = list(obj(animations8.get("bone_basis")).get("rotation"));
         return isRotation(jsonObject9, 0.0, 180.0, 0.0) ? jsonObject9 : null;
      } catch (RuntimeException error10) {
         return null;
      }
   }

   private static boolean isRotation(List<Object> entries0, double value1, double value3, double value5) {
      return entries0.size() == 3 && number(entries0.get(0)) == value1 && number(entries0.get(1)) == value3 && number(entries0.get(2)) == value5;
   }

   private static double number(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : Double.NaN;
   }

   private static boolean hasPoleBinding(Map<String, Object> mapping0) {
      String text1 = str(mapping0.get("binding"));
      return text1 != null && text1.contains("item_slot_to_bone_name");
   }

   private static Set<String> mappedTridentIds(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return Set.of();
      }

      try {
         var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         LinkedHashSet identifiers3 = new LinkedHashSet<>();

         for (Object value5 : list(obj(jsonObject2.get("items")).get("minecraft:trident"))) {
            String identifier6 = str(obj(value5).get("bedrock_identifier"));
            if (identifier6 != null && identifier6.startsWith("twilight:")) {
               identifiers3.add(identifier6);
            }
         }

         return identifiers3;
      } catch (RuntimeException error7) {
         return Set.of();
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
