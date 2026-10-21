package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class ArmorAndFishingRodRepair {
   private ArmorAndFishingRodRepair() {
   }

   public static int apply(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      int number3 = 0;

      try {
         number3 += BedrockArmorBridge.repair(resourceIndex0, entries1, packFiles2);
      } catch (RuntimeException error5) {
         throw new IllegalStateException("Twilight worn-armor conversion failed", error5);
      }

      return number3 + fixFishingRods(entries1, packFiles2);
   }

   private static int fixFishingRods(List<ItemCandidate> entries0, Map<String, byte[]> packFiles1) {
      int number2 = 0;
      HashSet identifiers3 = new HashSet<>();

      for (ItemCandidate itemCandidate5 : entries0) {
         String text6 = itemCandidate5.baseItem();
         if (text6 != null && text6.toLowerCase(Locale.ROOT).endsWith(":fishing_rod")) {
            String text7 = BedrockPackCompiler.bedrockIdentifier(itemCandidate5);
            if (text7 != null) {
               String suffix8 = text7.substring(text7.indexOf(58) + 1);
               if (identifiers3.add(suffix8) && isFlatSpriteGeometry(packFiles1, suffix8)) {
                  number2 += fixFlatRodFacing(packFiles1, suffix8);
               }
            }
         }
      }

      return number2;
   }

   private static boolean isFlatSpriteGeometry(Map<String, byte[]> packFiles0, String text1) {
      byte[] resourceBytes2 = (byte[])packFiles0.get("models/entity/geometry." + text1 + ".geo.json");
      if (resourceBytes2 == null) {
         return false;
      }

      try {
         var jsonObject3 = BedrockAnimationBridge.obj(BedrockAnimationBridge.Json.read(resourceBytes2));
         var value4 = array(jsonObject3.get("minecraft:geometry"));
         if (value4.size() != 1) {
            return false;
         }

         var jsonObject5 = array(BedrockAnimationBridge.obj(value4.get(0)).get("bones"));
         if (jsonObject5.size() != 1) {
            return false;
         }

         var jsonObject6 = array(BedrockAnimationBridge.obj(jsonObject5.get(0)).get("cubes"));
         if (jsonObject6.size() != 1) {
            return false;
         }

         var jsonObject7 = array(BedrockAnimationBridge.obj(jsonObject6.get(0)).get("size"));
         if (jsonObject7.size() != 3) {
            return false;
         }

         double value8 = Double.POSITIVE_INFINITY;
         double value10 = 0.0;

         for (Object value13 : jsonObject7) {
            if (!(value13 instanceof Number number14)) {
               return false;
            }

            value8 = Math.min(value8, Math.abs(number14.doubleValue()));
            value10 = Math.max(value10, Math.abs(number14.doubleValue()));
         }

         return value8 < 0.05 && value10 >= 8.0;
      } catch (Exception error15) {
         return false;
      }
   }

   private static int fixFlatRodFacing(Map<String, byte[]> packFiles0, String text1) {
      String animationName2 = "animations/" + text1 + ".animation.json";
      byte[] resourceBytes3 = (byte[])packFiles0.get(animationName2);
      if (resourceBytes3 == null) {
         return 0;
      }

      try {
         var jsonObject4 = BedrockAnimationBridge.obj(BedrockAnimationBridge.Json.read(resourceBytes3));
         var animations5 = BedrockAnimationBridge.obj(jsonObject4.get("animations"));
         boolean flag6 = false;

         for (var value8 : animations5.entrySet()) {
            String text9 = (String)value8.getKey();
            if (text9.endsWith(".third_person_right") || text9.endsWith(".third_person_left")) {
               var jsonObject10 = BedrockAnimationBridge.obj(value8.getValue());
               var jsonObject11 = BedrockAnimationBridge.obj(jsonObject10.get("bones"));
               var jsonObject12 = BedrockAnimationBridge.obj(jsonObject11.get("bone"));
               if (jsonObject12.get("rotation") instanceof List entries14 && entries14.size() >= 3 && entries14.get(1) instanceof Number number15) {
                  double value22 = number15.doubleValue();
                  if (!(value22 > 90.0) && !(value22 < -90.0)) {
                     double value18 = value22 + 180.0;
                     if (value18 > 180.0) {
                        value18 -= 360.0;
                     }

                     var value20 = entries14;
                     value20.set(1, value18);
                     flag6 = true;
                  }
               }
            }
         }

         if (!flag6) {
            return 0;
         }

         packFiles0.put(animationName2, BedrockAnimationBridge.Json.bytes(jsonObject4));
         return 1;
      } catch (RuntimeException error21) {
         throw new IllegalStateException("Invalid Bedrock animation for custom fishing rod", error21);
      }
   }

   private static List<Object> array(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }
}
