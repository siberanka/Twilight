package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Restore3DBowBasisRepair {
   private Restore3DBowBasisRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 == null) {
         return 0;
      }

      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return 0;
      }

      LinkedHashSet identifiers2 = new LinkedHashSet<>();

      try {
         var jsonObject3 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));

         for (Object entry5 : list(obj(jsonObject3.get("items")).get("minecraft:bow"))) {
            String identifier6 = str(obj(entry5).get("bedrock_identifier"));
            if (identifier6 != null && identifier6.startsWith("twilight:")) {
               identifiers2.add(identifier6);
            }
         }
      } catch (RuntimeException error30) {
         return 0;
      }

      if (identifiers2.isEmpty()) {
         return 0;
      }

      int number31 = 0;
      Iterator iterator32 = identifiers2.iterator();

      while (true) {
         String text33;
         String text34;
         Map<String, Object> mapping35;
         while (true) {
            if (!iterator32.hasNext()) {
               return number31;
            }

            text33 = (String)iterator32.next();
            text34 = text33.substring(text33.indexOf(58) + 1);
            byte[] resourceBytes7 = (byte[])packFiles0.get("attachables/" + text34 + ".json");
            if (resourceBytes7 != null) {
               try {
                  mapping35 = obj(obj(BedrockAnimationBridge.Json.read(resourceBytes7)).get("minecraft:attachable"));
                  mapping35 = obj(mapping35.get("description"));
                  break;
               } catch (RuntimeException error28) {
               }
            }
         }

         if (text33.equals(str(mapping35.get("identifier")))) {
            var animations9 = obj(mapping35.get("animations"));
            var geometry10 = obj(mapping35.get("geometry"));
            if (geometry10.containsKey("state_0") && geometry10.containsKey("state_3") && is3DBow(packFiles0, text34, geometry10)) {
               ArrayList entries11 = new ArrayList<>();
               ArrayList entries12 = new ArrayList<>();
               ArrayList entries13 = new ArrayList<>();
               ArrayList entries14 = new ArrayList<>();
               LinkedHashMap lookup15 = null;
               boolean flag16 = true;

               for (int number17 = 0; number17 < 4; number17++) {
                  String animationName18 = "animations/" + text34 + "_state_" + number17 + ".animation.json";
                  byte[] resourceBytes19 = (byte[])packFiles0.get(animationName18);
                  if (resourceBytes19 == null) {
                     flag16 = false;
                     break;
                  }

                  Map<String, Object> mapping20;
                  try {
                     mapping20 = obj(BedrockAnimationBridge.Json.read(resourceBytes19));
                  } catch (RuntimeException error29) {
                     flag16 = false;
                     break;
                  }

                  var animations21 = obj(mapping20.get("animations"));
                  var value22 = bones(animations21, str(animations9.get("state_" + number17 + "_first_person_right")));
                  var value23 = bones(animations21, str(animations9.get("state_" + number17 + "_first_person_left")));
                  var value24 = bones(animations21, str(animations9.get("state_" + number17 + "_third_person_right")));
                  var value25 = bones(animations21, str(animations9.get("state_" + number17 + "_third_person_left")));
                  var jsonObject26 = obj(value22.get("bone_basis"));
                  var jsonObject27 = obj(value23.get("bone_basis"));
                  if (jsonObject26.isEmpty()
                     || !jsonObject26.equals(jsonObject27)
                     || value24.isEmpty()
                     || value25.isEmpty()
                     || !value24.containsKey("bone_x")
                     || !value25.containsKey("bone_x")) {
                     flag16 = false;
                     break;
                  }

                  if (lookup15 == null) {
                     lookup15 = new LinkedHashMap<>(jsonObject26);
                  } else if (!lookup15.equals(jsonObject26)) {
                     flag16 = false;
                     break;
                  }

                  entries11.add(animationName18);
                  entries12.add(mapping20);
                  entries13.add(value24);
                  entries14.add(value25);
               }

               if (flag16 && lookup15 != null) {
                  for (int number36 = 0; number36 < 4; number36++) {
                     for (Map mapping39 : List.of((Map)entries13.get(number36), (Map)entries14.get(number36))) {
                        var jsonObject40 = obj(mapping39.get("bone_basis"));
                        if (!lookup15.equals(jsonObject40)) {
                           mapping39.put("bone_basis", new LinkedHashMap<>(lookup15));
                        }
                     }

                     byte[] resourceBytes38 = BedrockAnimationBridge.Json.bytes(entries12.get(number36));
                     if (!Arrays.equals((byte[])packFiles0.get(entries11.get(number36)), resourceBytes38)) {
                        packFiles0.put((String)entries11.get(number36), resourceBytes38);
                        number31++;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean is3DBow(Map<String, byte[]> packFiles0, String text1, Map<String, Object> mapping2) {
      byte[] resourceBytes3 = (byte[])packFiles0.get("models/entity/geometry." + text1 + "_state_0.geo.json");
      if (resourceBytes3 == null) {
         return false;
      }

      try {
         var jsonObject4 = obj(BedrockAnimationBridge.Json.read(resourceBytes3));
         var value5 = list(jsonObject4.get("minecraft:geometry"));
         if (value5.size() == 1
            && Objects.equals(
               mapping2.get("state_0"), obj(value5.get(0)).get("description") instanceof Map ? obj(obj(value5.get(0)).get("description")).get("identifier") : null
            )) {
            var jsonObject6 = list(obj(value5.get(0)).get("bones"));
            HashSet identifiers7 = new HashSet<>();
            int number8 = 0;

            for (Object value10 : jsonObject6) {
               var jsonObject11 = obj(value10);
               identifiers7.add(str(jsonObject11.get("name")));
               number8 += list(jsonObject11.get("cubes")).size();
            }

            return number8 > 8 && identifiers7.containsAll(List.of("bone", "bone_x", "bone_y", "bone_z", "bone_basis"));
         } else {
            return false;
         }
      } catch (RuntimeException error12) {
         return false;
      }
   }

   private static Map<String, Object> bones(Map<String, Object> mapping0, String text1) {
      return text1 == null ? Map.of() : obj(obj(mapping0.get(text1)).get("bones"));
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
