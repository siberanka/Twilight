package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VanillaFullChargeBowVibrationRepair {
   private static final String CHARGE = "v.twilight_bow_vanilla_charge = math.clamp((q.main_hand_item_max_duration - (q.main_hand_item_use_duration - q.frame_alpha + 1.0)) / 10.0, 0.0, 1.0);";
   private static final String WOBBLE = "v.twilight_bow_vanilla_charge >= 1.0 ? math.sin(q.life_time * 1000.0 * 1.3) * 0.1 - math.sin(q.life_time * 45.0) * 0.5 : 0.0";
   private static final String DRAW = "q.main_hand_item_use_duration > 0.0 && context.is_first_person == 1.0 && context.item_slot == 'main_hand'";
   private static final String ALIAS = "twilight_vanilla_fullcharge_wobble";

   private VanillaFullChargeBowVibrationRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         var value1 = mappedBows(packFiles0);
         if (value1.isEmpty()) {
            return 0;
         }

         int number2 = 0;
         Iterator iterator3 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            String text4;
            Map<String, Object> mapping5;
            String text8;
            Map<String, Object> mapping10;
            Map<String, Object> mapping11;
            List entries13;
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
                     } catch (RuntimeException error21) {
                     }
                  }
               }

               var attachable6 = obj(obj(mapping5.get("minecraft:attachable")).get("description"));
               String identifier7 = str(attachable6.get("identifier"));
               if (identifier7 != null && value1.contains(identifier7)) {
                  text8 = identifier7.substring(identifier7.indexOf(58) + 1);
                  var geometry9 = obj(attachable6.get("geometry"));
                  mapping10 = obj(attachable6.get("animations"));
                  mapping11 = obj(attachable6.get("scripts"));
                  if (geometry9.containsKey("state_0")
                     && geometry9.containsKey("state_3")
                     && mapping11.containsKey("animate")
                     && !mapping10.containsKey("twilight_vanilla_fullcharge_wobble")) {
                     byte[] resourceBytes12 = (byte[])packFiles0.get("models/entity/geometry." + text8 + "_state_0.geo.json");
                     if (resourceBytes12 != null) {
                        try {
                           var jsonObject14 = obj(BedrockAnimationBridge.Json.read(resourceBytes12));
                           var value15 = list(jsonObject14.get("minecraft:geometry"));
                           if (value15.size() == 1) {
                              var jsonObject16 = obj(value15.get(0));
                              if (Objects.equals(geometry9.get("state_0"), obj(jsonObject16.get("description")).get("identifier"))) {
                                 entries13 = list(jsonObject16.get("bones"));
                                 break;
                              }
                           }
                        } catch (RuntimeException error22) {
                        }
                     }
                  }
               }
            }

            if (is3DBow(entries13) && hasDrawStages(packFiles0, text8, mapping10)) {
               String animationName23 = "animations/" + text8 + "_bw.animation.json";
               if (!packFiles0.containsKey(animationName23)) {
                  String text24 = "animation.twilight." + text8 + ".vanilla_fullcharge_wobble";
                  LinkedHashMap lookup25 = new LinkedHashMap<>();
                  lookup25.put("loop", true);
                  lookup25.put(
                     "bones",
                     Map.of(
                        "bone_x",
                        Map.of(
                           "position",
                           List.of(
                              0.0,
                              "v.twilight_bow_vanilla_charge >= 1.0 ? math.sin(q.life_time * 1000.0 * 1.3) * 0.1 - math.sin(q.life_time * 45.0) * 0.5 : 0.0",
                              0.0
                           )
                        )
                     )
                  );
                  LinkedHashMap lookup17 = new LinkedHashMap<>();
                  lookup17.put("format_version", "1.10.0");
                  lookup17.put("animations", Map.of(text24, lookup25));
                  packFiles0.put(animationName23, BedrockAnimationBridge.Json.bytes(lookup17));
                  number2++;
                  var animationScripts18 = list(mapping11.get("pre_animation"));
                  if (animationScripts18.stream().noneMatch(var0x -> String.valueOf(var0x).contains("v.twilight_bow_vanilla_charge"))) {
                     var value19 = new ArrayList<>(animationScripts18);
                     value19.add(
                        "v.twilight_bow_vanilla_charge = math.clamp((q.main_hand_item_max_duration - (q.main_hand_item_use_duration - q.frame_alpha + 1.0)) / 10.0, 0.0, 1.0);"
                     );
                     mapping11.put("pre_animation", value19);
                  }

                  ArrayList entries26 = new ArrayList<>(list(mapping11.get("animate")));
                  entries26.add(
                     Map.of(
                        "twilight_vanilla_fullcharge_wobble",
                        "q.main_hand_item_use_duration > 0.0 && context.is_first_person == 1.0 && context.item_slot == 'main_hand'"
                     )
                  );
                  mapping11.put("animate", entries26);
                  mapping10.put("twilight_vanilla_fullcharge_wobble", text24);
                  byte[] resourceBytes20 = BedrockAnimationBridge.Json.bytes(mapping5);
                  if (!Arrays.equals(resourceBytes20, (byte[])packFiles0.get(text4))) {
                     packFiles0.put(text4, resourceBytes20);
                     number2++;
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static boolean hasDrawStages(Map<String, byte[]> packFiles0, String text1, Map<String, Object> mapping2) {
      for (int number3 = 0; number3 < 4; number3++) {
         String animationName4 = "animations/" + text1 + "_state_" + number3 + ".animation.json";
         byte[] resourceBytes5 = (byte[])packFiles0.get(animationName4);
         if (resourceBytes5 == null) {
            return false;
         }

         String text6 = str(mapping2.get("state_" + number3 + "_first_person_right"));
         if (text6 == null) {
            return false;
         }

         try {
            var jsonObject7 = obj(BedrockAnimationBridge.Json.read(resourceBytes5));
            if (!obj(obj(obj(jsonObject7.get("animations")).get(text6)).get("bones")).containsKey("bone_x")) {
               return false;
            }
         } catch (RuntimeException error8) {
            return false;
         }
      }

      return true;
   }

   private static boolean is3DBow(List<Object> entries0) {
      if (entries0.size() < 5) {
         return false;
      }

      HashSet identifiers1 = new HashSet<>();
      int number2 = 0;

      for (Object value4 : entries0) {
         var jsonObject5 = obj(value4);
         identifiers1.add(str(jsonObject5.get("name")));
         number2 += list(jsonObject5.get("cubes")).size();
      }

      return number2 > 8 && identifiers1.containsAll(List.of("bone", "bone_x", "bone_y", "bone_z", "bone_basis"));
   }

   private static Set<String> mappedBows(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return Set.of();
      }

      try {
         var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         HashSet identifiers3 = new HashSet<>();

         for (Object value5 : list(obj(jsonObject2.get("items")).get("minecraft:bow"))) {
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

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : List.of();
   }
}
