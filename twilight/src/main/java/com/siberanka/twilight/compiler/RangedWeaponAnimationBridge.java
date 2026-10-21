package com.siberanka.twilight.compiler;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RangedWeaponAnimationBridge {
   private static final String MAPPING_FILE = "twilight/geyser_item_mappings.json";
   private static final String PLAYER_CONTROLLER = "animation_controllers/twilight_ranged_player.animation_controllers.json";
   private static final String TEMPLATE = "/twilight/player_controller_template.json";

   private RangedWeaponAnimationBridge() {
   }

   public static void install(Map<String, byte[]> packFiles0) {
      apply(packFiles0);
   }

   static int[] apply(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return new int[]{0, 0, 0};
      }

      try {
         var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         var itemDefinitions3 = obj(jsonObject2.get("items"));
         if (itemDefinitions3 == null) {
            return new int[]{0, 0, 0};
         }

         var value4 = identifiers(itemDefinitions3.get("minecraft:bow"), packFiles0);
         var value5 = identifiers(itemDefinitions3.get("minecraft:crossbow"), packFiles0);
         int number6 = fixCrossbows(packFiles0, value5);
         if (value4.isEmpty() && value5.isEmpty()) {
            return new int[]{0, 0, number6};
         }

         Map<String, Object> mapping7;
         try (InputStream inputStream8 = RangedWeaponAnimationBridge.class.getResourceAsStream("/twilight/player_controller_template.json")) {
            if (inputStream8 == null) {
               return new int[]{value4.size(), value5.size(), number6};
            }

            mapping7 = obj(BedrockAnimationBridge.Json.read(inputStream8.readAllBytes()));
         }

         var jsonObject21 = obj(mapping7.get("animation_controllers"));
         var jsonObject9 = obj(jsonObject21.get("controller.animation.player.root"));
         var jsonObject10 = obj(jsonObject9.get("states"));
         var jsonObject11 = obj(jsonObject10.get("third_person"));
         var jsonObject12 = obj(jsonObject10.get("first_person"));
         var animations13 = list(jsonObject11.get("animations"));
         var animations14 = list(jsonObject12.get("animations"));
         String text15 = predicate(value4);
         String text16 = predicate(value5);
         if (!value4.isEmpty()) {
            replaceCondition(
               animations13,
               "third_person_bow_equipped",
               "(query.get_equipped_item_name == 'bow' || " + text15 + ") && variable.item_use_normalized > 0.0 && variable.item_use_normalized < 1.0"
            );
            String text17 = findCondition(animations13, "use_item_progress");
            if (text17 != null) {
               replaceCondition(animations13, "use_item_progress", "(" + text17 + ") && !(" + text15 + ")");
            }
         }

         if (!value5.isEmpty()) {
            replaceCondition(animations13, "crossbow_controller", "(query.get_equipped_item_name == 'crossbow' || " + text16 + ")");
            replaceCondition(
               animations14,
               "first_person_crossbow_equipped",
               "(query.get_equipped_item_name == 'crossbow' || " + text16 + ") && variable.item_use_normalized > 0.0 && variable.item_use_normalized < 1.0"
            );
            var jsonObject23 = obj(jsonObject21.get("controller.animation.player.crossbow"));
            if (jsonObject23 != null) {
               rewriteCrossbowController(jsonObject23, text16);
            }
         }

         LinkedHashMap lookup24 = new LinkedHashMap<>();
         lookup24.put("controller.animation.player.root", jsonObject9);
         if (!value5.isEmpty()) {
            lookup24.put("controller.animation.player.crossbow", jsonObject21.get("controller.animation.player.crossbow"));
         }

         packFiles0.put(
            "animation_controllers/twilight_ranged_player.animation_controllers.json",
            BedrockAnimationBridge.Json.bytes(Map.of("format_version", "1.10.0", "animation_controllers", lookup24))
         );
         return new int[]{value4.size(), value5.size(), number6};
      } catch (Exception error20) {
         return new int[]{0, 0, 0};
      }
   }

   private static List<String> identifiers(Object value0, Map<String, byte[]> packFiles1) {
      var value2 = list(value0);
      if (value2 == null) {
         return List.of();
      }

      LinkedHashSet identifiers3 = new LinkedHashSet<>();

      for (Object value5 : value2) {
         var jsonObject6 = obj(value5);
         if (jsonObject6 != null && jsonObject6.get("bedrock_identifier") instanceof String text8 && text8.matches("[a-z0-9_\\-.]+:[a-z0-9_\\-.]+")) {
            String suffix9 = text8.substring(text8.indexOf(58) + 1);
            if (packFiles1.containsKey("attachables/" + suffix9 + ".json")) {
               identifiers3.add(text8);
            }
         }
      }

      return List.copyOf(identifiers3);
   }

   private static String predicate(List<String> entries0) {
      StringBuilder builder1 = new StringBuilder("q.is_item_name_any('slot.weapon.mainhand', 0");

      for (String text3 : entries0) {
         builder1.append(", '").append(text3).append("'");
      }

      return builder1.append(')').toString();
   }

   private static void replaceCondition(List<Object> entries0, String text1, String text2) {
      for (int number3 = 0; number3 < entries0.size(); number3++) {
         var jsonObject4 = obj(entries0.get(number3));
         if (jsonObject4 != null && jsonObject4.containsKey(text1)) {
            entries0.set(number3, Map.of(text1, text2));
            return;
         }
      }

      throw new IllegalStateException("Unknown current player animation: " + text1);
   }

   private static String findCondition(List<Object> entries0, String text1) {
      for (Object value3 : entries0) {
         var jsonObject4 = obj(value3);
         if (jsonObject4 != null && jsonObject4.get(text1) instanceof String text5) {
            return text5;
         }
      }

      return null;
   }

   private static void rewriteCrossbowController(Map<String, Object> mapping0, String text1) {
      var jsonObject2 = obj(mapping0.get("states"));
      String text3 = "(query.get_equipped_item_name == 'crossbow' || " + text1 + ")";

      for (Object value5 : jsonObject2.values()) {
         var jsonObject6 = obj(value5);
         var value7 = list(jsonObject6.get("transitions"));

         for (int number8 = 0; number8 < value7.size(); number8++) {
            var jsonObject9 = obj(value7.get(number8));
            if (jsonObject9 != null) {
               for (String text11 : List.of("default", "charge", "hold")) {
                  if (jsonObject9.get(text11) instanceof String text12) {
                     if (text12.contains("query.get_equipped_item_name != 'crossbow'")) {
                        text12 = text12.replace("query.get_equipped_item_name != 'crossbow'", "!" + text3);
                     }

                     if (text11.equals("default") && text12.contains("query.item_remaining_use_duration <= 0.0")) {
                        text12 = text12.replace("query.item_remaining_use_duration <= 0.0", "q.main_hand_item_use_duration <= 0.0");
                     }

                     if (text11.equals("charge") && text12.contains("query.item_remaining_use_duration")) {
                        text12 = "q.main_hand_item_use_duration > 0.0 && q.main_hand_item_max_duration > 0.0 && !q.item_is_charged";
                     }

                     value7.set(number8, Map.of(text11, text12));
                  }
               }
            }
         }
      }
   }

   private static int fixCrossbows(Map<String, byte[]> packFiles0, List<String> entries1) {
      int number2 = 0;

      for (String text4 : entries1) {
         try {
            String suffix5 = text4.substring(text4.indexOf(58) + 1);
            String attachablePath6 = "attachables/" + suffix5 + ".json";
            byte[] resourceBytes7 = (byte[])packFiles0.get(attachablePath6);
            if (resourceBytes7 != null) {
               var jsonObject8 = obj(BedrockAnimationBridge.Json.read(resourceBytes7));
               var attachable9 = obj(obj(jsonObject8.get("minecraft:attachable")).get("description"));
               var geometry10 = obj(attachable9.get("geometry"));
               if (geometry10 != null && geometry10.containsKey("state_3")) {
                  var scripts11 = obj(attachable9.get("scripts"));
                  var animationScripts12 = list(scripts11.get("pre_animation"));
                  if (animationScripts12 != null && !animationScripts12.isEmpty()) {
                     String text13 = String.valueOf(animationScripts12.get(0));
                     if (text13.contains("v.twilight_model_index")
                        && (text13.contains("item_remaining_use_duration") || text13.contains("q.main_hand_item_use_duration"))) {
                        TreeSet identifiers14 = new TreeSet<>();
                        Matcher matcher15 = Pattern.compile(">=\\s*(\\d+(?:\\.\\d+)?)").matcher(text13);

                        while (matcher15.find()) {
                           double value16 = Double.parseDouble(matcher15.group(1));
                           if (value16 > 0.0 && value16 <= 1.0) {
                              identifiers14.add(value16);
                           }
                        }

                        if (identifiers14.size() >= 2) {
                           double value27 = (Double)identifiers14.first();
                           double value18 = (Double)identifiers14.last();
                           String text20 = "q.main_hand_item_use_duration";
                           String text21 = "q.main_hand_item_max_duration";
                           String text22 = "(" + text20 + " > 0.0 && " + text21 + " > 0.0)";
                           String text23 = "math.max(0.0, " + text21 + " - (" + text20 + " - q.frame_alpha + 1.0))";
                           String text24 = "math.clamp(" + text23 + " / 10.0, 0.0, 1.0)";
                           String text25 = "v.twilight_model_index = "
                              + text22
                              + " ? ("
                              + text24
                              + ">="
                              + fmt(value18)
                              + " ? 3 : ("
                              + text24
                              + ">="
                              + fmt(value27)
                              + " ? 2 : 1)) : 0;";
                           if (!text13.equals(text25)) {
                              animationScripts12.set(0, text25);
                              packFiles0.put(attachablePath6, BedrockAnimationBridge.Json.bytes(jsonObject8));
                              number2++;
                           }
                        }
                     }
                  }
               }
            }
         } catch (RuntimeException error26) {
         }
      }

      return number2;
   }

   private static String fmt(double value0) {
      return BigDecimal.valueOf(value0).stripTrailingZeros().toPlainString();
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : null;
   }
}
