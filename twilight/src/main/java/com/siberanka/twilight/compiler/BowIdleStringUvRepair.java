package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class BowIdleStringUvRepair {
   private static final String MARKER = "twilight/3d-bow-idle-string-uv-repair.json";
   private static final double EPS = 1.0E-4;

   private BowIdleStringUvRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty() && !packFiles0.containsKey("twilight/3d-bow-idle-string-uv-repair.json")) {
         byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
         if (resourceBytes1 == null) {
            return 0;
         }

         LinkedHashSet<String> identifiers2 = new LinkedHashSet<>();

         try {
            var jsonObject3 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));

            for (Object entry5 : list(obj(jsonObject3.get("items")).get("minecraft:bow"))) {
               String identifier6 = str(obj(entry5).get("bedrock_identifier"));
               if (identifier6 != null && identifier6.startsWith("twilight:")) {
                  identifiers2.add(identifier6);
               }
            }
         } catch (RuntimeException error23) {
            return 0;
         }

         int number24 = 0;
         ArrayList entries25 = new ArrayList<>();

         for (String text28 : identifiers2) {
            String suffix7 = text28.substring(text28.indexOf(58) + 1);
            if (hasY180Basis(packFiles0, suffix7)) {
               Map[] values8 = new Map[4];
               List<Map<String, Object>>[] values9 = new List[4];
               String[] segments10 = new String[4];
               boolean flag11 = true;

               for (int number12 = 0; number12 < 4; number12++) {
                  segments10[number12] = "models/entity/geometry." + suffix7 + "_state_" + number12 + ".geo.json";
                  byte[] resourceBytes13 = (byte[])packFiles0.get(segments10[number12]);
                  if (resourceBytes13 == null) {
                     flag11 = false;
                     break;
                  }

                  try {
                     values8[number12] = obj(BedrockAnimationBridge.Json.read(resourceBytes13));
                     var value14 = list(values8[number12].get("minecraft:geometry"));
                     if (value14.size() != 1) {
                        flag11 = false;
                        break;
                     }

                     values9[number12] = cubes(obj(value14.get(0)));
                  } catch (RuntimeException error22) {
                     flag11 = false;
                     break;
                  }
               }

               if (flag11) {
                  ArrayList entries29 = new ArrayList<>();

                  for (Map mapping32 : values9[0]) {
                     if (isStationaryStringRibbon(mapping32)) {
                        ArrayList entries15 = new ArrayList<>();
                        entries15.add(mapping32);
                        boolean flag16 = true;

                        for (int number17 = 1; number17 < 4; number17++) {
                           ArrayList entries18 = new ArrayList<>();

                           for (Map mapping20 : values9[number17]) {
                              if (sameRibbon(mapping20, mapping32)) {
                                 entries18.add(mapping20);
                              }
                           }

                           if (entries18.size() != 1) {
                              flag16 = false;
                              break;
                           }

                           entries15.add((Map)entries18.get(0));
                        }

                        if (flag16) {
                           entries29.add(entries15);
                        }
                     }
                  }

                  if (entries29.size() == 1) {
                     List entries31 = (List)entries29.get(0);
                     var jsonObject33 = obj(((Map)entries31.get(0)).get("uv"));
                     var jsonObject34 = obj(((Map)entries31.get(1)).get("uv"));
                     boolean flag35 = true;

                     for (String text39 : List.of("east", "west")) {
                        var jsonObject41 = obj(jsonObject33.get(text39));
                        var jsonObject43 = obj(jsonObject34.get(text39));
                        if (!isHorizontallyMirrored(jsonObject41, jsonObject43)) {
                           flag35 = false;
                           break;
                        }
                     }

                     if (flag35) {
                        for (String text40 : List.of("east", "west")) {
                           var jsonObject42 = obj(jsonObject33.get(text40));
                           var value44 = list(jsonObject42.get("uv"));
                           var value21 = list(jsonObject42.get("uv_size"));
                           jsonObject42.put("uv", Arrays.asList(num(value44.get(0)) + num(value21.get(0)), num(value44.get(1))));
                           jsonObject42.put("uv_size", Arrays.asList(-num(value21.get(0)), num(value21.get(1))));
                        }

                        byte[] resourceBytes38 = BedrockAnimationBridge.Json.bytes(values8[0]);
                        if (!Arrays.equals(resourceBytes38, (byte[])packFiles0.get(segments10[0]))) {
                           packFiles0.put(segments10[0], resourceBytes38);
                           number24++;
                           entries25.add(text28);
                        }
                     }
                  }
               }
            }
         }

         if (!entries25.isEmpty()) {
            LinkedHashMap lookup27 = new LinkedHashMap<>();
            lookup27.put("version", 1);
            lookup27.put("correction", "reverse_uv_u_stationary_bowstring");
            lookup27.put("applied_identifiers", entries25);
            packFiles0.put("twilight/3d-bow-idle-string-uv-repair.json", BedrockAnimationBridge.Json.bytes(lookup27));
            number24++;
         }

         return number24;
      } else {
         return 0;
      }
   }

   private static boolean hasY180Basis(Map<String, byte[]> packFiles0, String text1) {
      byte[] resourceBytes2 = (byte[])packFiles0.get("animations/" + text1 + "_state_0.animation.json");
      if (resourceBytes2 == null) {
         return false;
      }

      try {
         var animations3 = obj(obj(BedrockAnimationBridge.Json.read(resourceBytes2)).get("animations"));

         for (Object value5 : animations3.values()) {
            var jsonObject6 = list(obj(obj(obj(value5).get("bones")).get("bone_basis")).get("rotation"));
            if (vec3(jsonObject6) && Math.abs(Math.abs(num(jsonObject6.get(1))) - 180.0) < 1.0E-4) {
               return true;
            }
         }
      } catch (Exception error7) {
      }

      return false;
   }

   private static boolean isStationaryStringRibbon(Map<String, Object> mapping0) {
      var value1 = list(mapping0.get("size"));
      var value2 = list(mapping0.get("origin"));
      if (vec3(value1) && vec3(value2)) {
         if (num(value1.get(0)) <= 0.06 && num(value1.get(0)) > 0.0 && num(value1.get(1)) >= 14.0 && num(value1.get(2)) >= 3.0) {
            var jsonObject3 = obj(mapping0.get("uv"));
            if (jsonObject3.size() == 2 && jsonObject3.containsKey("east") && jsonObject3.containsKey("west")) {
               for (String text5 : List.of("east", "west")) {
                  var jsonObject6 = obj(jsonObject3.get(text5));
                  var value7 = list(jsonObject6.get("uv"));
                  var value8 = list(jsonObject6.get("uv_size"));
                  if (!vec2(value7) || !vec2(value8) || Math.abs(num(value8.get(0))) < 1.0) {
                     return false;
                  }
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean sameRibbon(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      if (!isStationaryStringRibbon(mapping0)) {
         return false;
      }

      for (String text3 : List.of("origin", "size", "pivot", "rotation")) {
         var value4 = list(mapping0.get(text3));
         var value5 = list(mapping1.get(text3));
         if (!sameVec(value4, value5)) {
            return false;
         }
      }

      return true;
   }

   private static boolean isHorizontallyMirrored(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      var value2 = list(mapping0.get("uv"));
      var value3 = list(mapping0.get("uv_size"));
      var value4 = list(mapping1.get("uv"));
      var value5 = list(mapping1.get("uv_size"));
      return vec2(value2)
         && vec2(value3)
         && vec2(value4)
         && vec2(value5)
         && Math.abs(num(value4.get(0)) - (num(value2.get(0)) + num(value3.get(0)))) < 1.0E-4
         && Math.abs(num(value4.get(1)) - num(value2.get(1))) < 1.0E-4
         && Math.abs(num(value5.get(0)) + num(value3.get(0))) < 1.0E-4
         && Math.abs(num(value5.get(1)) - num(value3.get(1))) < 1.0E-4;
   }

   private static List<Map<String, Object>> cubes(Map<String, Object> mapping0) {
      ArrayList entries1 = new ArrayList<>();

      for (Object value3 : list(mapping0.get("bones"))) {
         for (Object value5 : list(obj(value3).get("cubes"))) {
            entries1.add(obj(value5));
         }
      }

      return entries1;
   }

   private static boolean sameVec(List<Object> entries0, List<Object> entries1) {
      if (entries0.isEmpty() && entries1.isEmpty()) {
         return true;
      }

      if (entries0.size() != entries1.size()) {
         return false;
      }

      for (int number2 = 0; number2 < entries0.size(); number2++) {
         if (!(entries0.get(number2) instanceof Number) || !(entries1.get(number2) instanceof Number) || Math.abs(num(entries0.get(number2)) - num(entries1.get(number2))) > 1.0E-4) {
            return false;
         }
      }

      return true;
   }

   private static double num(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : Double.NaN;
   }

   private static boolean vec2(List<Object> entries0) {
      return entries0.size() == 2 && entries0.get(0) instanceof Number && entries0.get(1) instanceof Number;
   }

   private static boolean vec3(List<Object> entries0) {
      return entries0.size() == 3 && entries0.get(0) instanceof Number && entries0.get(1) instanceof Number && entries0.get(2) instanceof Number;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Map.of();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
