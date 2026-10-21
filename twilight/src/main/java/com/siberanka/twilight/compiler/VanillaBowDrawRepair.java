package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VanillaBowDrawRepair {
   private VanillaBowDrawRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         int number1 = 0;
         Iterator iterator2 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            String text3;
            Map mapping4;
            Map mapping5;
            String text7;
            Map mapping9;
            while (true) {
               while (true) {
                  if (!iterator2.hasNext()) {
                     return number1;
                  }

                  text3 = (String)iterator2.next();
                  if (text3.startsWith("attachables/") && text3.endsWith(".json") && !text3.endsWith(".player.json")) {
                     try {
                        mapping4 = object(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text3)));
                        break;
                     } catch (RuntimeException error33) {
                     }
                  }
               }

               mapping5 = object(object(mapping4.get("minecraft:attachable")).get("description"));
               String identifier6 = string(mapping5.get("identifier"));
               if (identifier6 != null && identifier6.startsWith("twilight:")) {
                  text7 = identifier6.substring(identifier6.indexOf(58) + 1);
                  if (text7.startsWith("minecraft_bow_")) {
                     String text8 = "twilight/bow-ref-" + text7 + ".json";
                     if (packFiles0.containsKey(text8)) {
                        try {
                           mapping9 = object(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text8)));
                           break;
                        } catch (RuntimeException error34) {
                        }
                     }
                  }
               }
            }

            if ("rightitem_texture_mesh".equals(string(mapping9.get("format")))) {
               var scripts10 = object(mapping5.get("scripts"));
               var animations11 = object(mapping5.get("animations"));
               var animationScripts12 = list(scripts10.get("pre_animation"));
               var value13 = list(scripts10.get("animate"));
               if (!animationScripts12.isEmpty() && !value13.isEmpty()) {
                  String text14 = "twilight_vanilla_bow_pull";
                  String text15 = "animation.twilight." + text7 + ".vanilla_bow_pull";
                  String animationName16 = "animations/" + text7 + "_vanilla_bow_pull.animation.json";
                  if (!animations11.containsKey(text14) && !packFiles0.containsKey(animationName16) && allStatesValid(packFiles0, text7, animations11)) {
                     var value17 = new ArrayList<>(animationScripts12);
                     value17.add(
                        "v.twilight_bow_vanilla_charge = math.clamp((q.main_hand_item_max_duration - (q.main_hand_item_use_duration - q.frame_alpha + 1.0)) / 10.0, 0.0, 1.0);"
                     );
                     scripts10.put("pre_animation", value17);
                     var value18 = new ArrayList<>(value13);
                     LinkedHashMap lookup19 = new LinkedHashMap<>();
                     lookup19.put(text14, "q.main_hand_item_use_duration > 0.0 && context.is_first_person == 1.0 && context.item_slot == 'main_hand'");
                     value18.add(lookup19);
                     scripts10.put("animate", value18);
                     animations11.put(text14, text15);
                     String animationName20 = "animations/" + text7 + "_state_0.animation.json";
                     var parsedJson21 = object(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName20)));
                     var animations22 = object(parsedJson21.get("animations"));

                     for (int number23 = 1; number23 < 4; number23++) {
                        String animationName24 = "animations/" + text7 + "_state_" + number23 + ".animation.json";
                        var parsedJson25 = object(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName24)));
                        var animations26 = object(parsedJson25.get("animations"));

                        for (String text28 : List.of("first_person_right", "first_person_left")) {
                           String text29 = string(animations11.get("state_0_" + text28));
                           String text30 = string(animations11.get("state_" + number23 + "_" + text28));
                           var value31 = object(animations22.get(text29));
                           var value32 = object(animations26.get(text30));
                           value32.put("bones", BedrockAnimationBridge.Json.read(BedrockAnimationBridge.Json.bytes(value31.get("bones"))));
                        }

                        byte[] resourceBytes39 = BedrockAnimationBridge.Json.bytes(parsedJson25);
                        if (different(packFiles0, animationName24, resourceBytes39)) {
                           packFiles0.put(animationName24, resourceBytes39);
                           number1++;
                        }
                     }

                     LinkedHashMap lookup35 = new LinkedHashMap<>();
                     lookup35.put(
                        "position",
                        List.of(
                           -1.5,
                           "2.5 + (v.twilight_bow_vanilla_charge >= 1.0 ? math.sin(q.life_time * 1000.0 * 1.3) * 0.1 - math.sin(q.life_time * 45.0) * 0.5 : 0.0)",
                           -4.8
                        )
                     );
                     lookup35.put("rotation", List.of(-53.0, 8.0, 35.0));
                     LinkedHashMap lookup36 = new LinkedHashMap<>();
                     lookup36.put("loop", true);
                     lookup36.put("bones", Map.of("rightitem", lookup35));
                     LinkedHashMap lookup37 = new LinkedHashMap<>();
                     lookup37.put("format_version", "1.10.0");
                     lookup37.put("animations", Map.of(text15, lookup36));
                     packFiles0.put(animationName16, BedrockAnimationBridge.Json.bytes(lookup37));
                     number1++;
                     byte[] resourceBytes38 = BedrockAnimationBridge.Json.bytes(mapping4);
                     if (different(packFiles0, text3, resourceBytes38)) {
                        packFiles0.put(text3, resourceBytes38);
                        number1++;
                     }
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static boolean allStatesValid(Map<String, byte[]> packFiles0, String text1, Map<String, Object> mapping2) {
      for (int number3 = 0; number3 < 4; number3++) {
         String animationName4 = "animations/" + text1 + "_state_" + number3 + ".animation.json";
         byte[] resourceBytes5 = (byte[])packFiles0.get(animationName4);
         if (resourceBytes5 == null) {
            return false;
         }

         try {
            var parsedJson6 = object(BedrockAnimationBridge.Json.read(resourceBytes5));
            var animations7 = object(parsedJson6.get("animations"));

            for (String text9 : List.of("first_person_right", "first_person_left")) {
               String text10 = string(mapping2.get("state_" + number3 + "_" + text9));
               if (text10 == null || !animations7.containsKey(text10)) {
                  return false;
               }

               if (!object(animations7.get(text10)).containsKey("bones")) {
                  return false;
               }
            }
         } catch (RuntimeException error11) {
            return false;
         }
      }

      return true;
   }

   private static boolean different(Map<String, byte[]> packFiles0, String text1, byte[] resourceBytes2) {
      return !Arrays.equals((byte[])packFiles0.get(text1), resourceBytes2);
   }

   private static String string(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static Map<String, Object> object(Object value0) {
      return value0 instanceof Map ? (Map)value0 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : List.of();
   }
}
