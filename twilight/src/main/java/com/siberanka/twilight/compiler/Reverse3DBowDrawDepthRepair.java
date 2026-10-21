package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Reverse3DBowDrawDepthRepair {
   private static final String MARKER = "twilight/3d-bow-draw-depth-repair.json";
   private static final double EPS = 1.0E-4;

   private Reverse3DBowDrawDepthRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
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
         } catch (RuntimeException error36) {
            return 0;
         }

         LinkedHashSet identifiers37 = new LinkedHashSet<>();
         byte[] resourceBytes38 = (byte[])packFiles0.get("twilight/3d-bow-draw-depth-repair.json");
         if (resourceBytes38 != null) {
            try {
               var jsonObject39 = obj(BedrockAnimationBridge.Json.read(resourceBytes38));
               if (num(jsonObject39.get("version")) == 1.0) {
                  for (Object entry7 : list(jsonObject39.get("applied_identifiers"))) {
                     String text8 = str(entry7);
                     if (text8 != null) {
                        identifiers37.add(text8);
                     }
                  }
               }
            } catch (RuntimeException error35) {
            }
         }

         int number40 = 0;
         Iterator iterator42 = identifiers2.iterator();

         while (true) {
            Map<String, Object> mapping11;
            String text44;
            String text45;
            while (true) {
               Map<String, Object> mapping10;
               while (true) {
                  if (!iterator42.hasNext()) {
                     if (number40 > 0) {
                        LinkedHashMap lookup43 = new LinkedHashMap<>();
                        lookup43.put("version", 1);
                        lookup43.put("applied_identifiers", new ArrayList<>(identifiers37));
                        packFiles0.put("twilight/3d-bow-draw-depth-repair.json", BedrockAnimationBridge.Json.bytes(lookup43));
                        number40++;
                     }

                     return number40;
                  }

                  text44 = (String)iterator42.next();
                  if (!identifiers37.contains(text44)) {
                     text45 = text44.substring(text44.indexOf(58) + 1);
                     byte[] resourceBytes9 = (byte[])packFiles0.get("attachables/" + text45 + ".json");
                     if (resourceBytes9 != null) {
                        try {
                           mapping10 = obj(obj(obj(BedrockAnimationBridge.Json.read(resourceBytes9)).get("minecraft:attachable")).get("description"));
                           break;
                        } catch (RuntimeException error32) {
                        }
                     }
                  }
               }

               if (text44.equals(str(mapping10.get("identifier")))) {
                  mapping11 = obj(mapping10.get("geometry"));
                  var animations12 = obj(mapping10.get("animations"));
                  if (mapping11.containsKey("state_0") && mapping11.containsKey("state_1") && mapping11.containsKey("state_2") && mapping11.containsKey("state_3")) {
                     String text13 = str(animations12.get("state_0_first_person_right"));
                     if (text13 != null) {
                        String animationName14 = "animations/" + text45 + "_state_0.animation.json";

                        try {
                           var animations15 = obj(obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName14))).get("animations"));
                           var jsonObject16 = obj(obj(animations15.get(text13)).get("bones"));
                           var jsonObject17 = list(obj(jsonObject16.get("bone_basis")).get("rotation"));
                           if (jsonObject17.size() == 3 && !(Math.abs(Math.abs(num(jsonObject17.get(1))) - 180.0) > 1.0E-4)) {
                              break;
                           }
                        } catch (Exception error33) {
                        }
                     }
                  }
               }
            }

            Map[] values46 = new Map[4];
            Map[] values47 = new Map[4];
            List[] values48 = new List[4];
            String[] segments18 = new String[4];
            boolean flag19 = true;

            for (int number20 = 0; number20 < 4; number20++) {
               segments18[number20] = "models/entity/geometry." + text45 + "_state_" + number20 + ".geo.json";
               byte[] resourceBytes21 = (byte[])packFiles0.get(segments18[number20]);
               if (resourceBytes21 == null) {
                  flag19 = false;
                  break;
               }

               try {
                  values46[number20] = obj(BedrockAnimationBridge.Json.read(resourceBytes21));
                  var value22 = list(values46[number20].get("minecraft:geometry"));
                  if (value22.size() != 1) {
                     flag19 = false;
                     break;
                  }

                  values47[number20] = obj(value22.get(0));
                  if (!Objects.equals(obj(values47[number20].get("description")).get("identifier"), mapping11.get("state_" + number20))) {
                     flag19 = false;
                     break;
                  }

                  values48[number20] = allCubes(values47[number20]);
                  if (values48[number20].size() < 4) {
                     flag19 = false;
                     break;
                  }
               } catch (RuntimeException error34) {
                  flag19 = false;
                  break;
               }
            }

            if (flag19 && values48[1].size() == values48[2].size() && values48[1].size() == values48[3].size()) {
               ArrayList<Integer> arrayListInteger49 = new ArrayList<>();

               for (int number50 = 0; number50 < values48[1].size(); number50++) {
                  Map mapping53 = (Map)values48[1].get(number50);
                  Map mapping23 = (Map)values48[2].get(number50);
                  Map mapping24 = (Map)values48[3].get(number50);
                  var value25 = list(mapping53.get("origin"));
                  var value26 = list(mapping23.get("origin"));
                  var value27 = list(mapping24.get("origin"));
                  if (!vec3(value25)
                     || !vec3(value26)
                     || !vec3(value27)
                     || !sameXY(value25, value26)
                     || !sameXY(value25, value27)
                     || !vec3(list(mapping53.get("size")))
                     || !sameVector(list(mapping53.get("size")), list(mapping23.get("size")))
                     || !sameVector(list(mapping53.get("size")), list(mapping24.get("size")))
                     || !sameVector(list(mapping53.get("rotation")), list(mapping23.get("rotation")))
                     || !sameVector(list(mapping53.get("rotation")), list(mapping24.get("rotation")))) {
                     flag19 = false;
                     break;
                  }

                  double value28 = num(value26.get(2)) - num(value25.get(2));
                  double value30 = num(value27.get(2)) - num(value25.get(2));
                  if (!(Math.abs(value28) < 1.0E-4) || !(Math.abs(value30) < 1.0E-4)) {
                     if (!(value28 < -1.0E-4) || !(value30 < value28 - 1.0E-4)) {
                        flag19 = false;
                        break;
                     }

                     if (!pivotOkay(mapping53, mapping23, mapping24)) {
                        flag19 = false;
                        break;
                     }

                     arrayListInteger49.add(number50);
                  }
               }

               if (flag19 && arrayListInteger49.size() >= 2) {
                  for (int number54 : arrayListInteger49) {
                     Map mapping56 = (Map)values48[1].get(number54);

                     for (int number57 = 2; number57 <= 3; number57++) {
                        Map mapping58 = (Map)values48[number57].get(number54);
                        mapping58.put("origin", mirrorZ(list(mapping56.get("origin")), list(mapping58.get("origin"))));
                        if (mapping56.containsKey("pivot") && mapping58.containsKey("pivot")) {
                           mapping58.put("pivot", mirrorZ(list(mapping56.get("pivot")), list(mapping58.get("pivot"))));
                        }
                     }
                  }

                  for (int number52 = 2; number52 <= 3; number52++) {
                     byte[] resourceBytes55 = BedrockAnimationBridge.Json.bytes(values46[number52]);
                     if (!Arrays.equals((byte[])packFiles0.get(segments18[number52]), resourceBytes55)) {
                        packFiles0.put(segments18[number52], resourceBytes55);
                        number40++;
                     }
                  }

                  identifiers37.add(text44);
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static boolean pivotOkay(Map<String, Object> mapping0, Map<String, Object> mapping1, Map<String, Object> mapping2) {
      return !mapping0.containsKey("pivot") && !mapping1.containsKey("pivot") && !mapping2.containsKey("pivot")
         ? true
         : vec3(list(mapping0.get("pivot")))
            && vec3(list(mapping1.get("pivot")))
            && vec3(list(mapping2.get("pivot")))
            && sameXY(list(mapping0.get("pivot")), list(mapping1.get("pivot")))
            && sameXY(list(mapping0.get("pivot")), list(mapping2.get("pivot")));
   }

   private static List<Object> mirrorZ(List<Object> entries0, List<Object> entries1) {
      return Arrays.asList(num(entries1.get(0)), num(entries1.get(1)), 2.0 * num(entries0.get(2)) - num(entries1.get(2)));
   }

   private static boolean vec3(List<Object> entries0) {
      return entries0.size() == 3 && entries0.get(0) instanceof Number && entries0.get(1) instanceof Number && entries0.get(2) instanceof Number;
   }

   private static boolean sameXY(List<Object> entries0, List<Object> entries1) {
      return vec3(entries0) && vec3(entries1) && Math.abs(num(entries0.get(0)) - num(entries1.get(0))) < 1.0E-4 && Math.abs(num(entries0.get(1)) - num(entries1.get(1))) < 1.0E-4;
   }

   private static boolean sameVector(List<Object> entries0, List<Object> entries1) {
      return entries0.isEmpty() && entries1.isEmpty()
         ? true
         : vec3(entries0)
            && vec3(entries1)
            && Math.abs(num(entries0.get(0)) - num(entries1.get(0))) < 1.0E-4
            && Math.abs(num(entries0.get(1)) - num(entries1.get(1))) < 1.0E-4
            && Math.abs(num(entries0.get(2)) - num(entries1.get(2))) < 1.0E-4;
   }

   private static List<Map<String, Object>> allCubes(Map<String, Object> mapping0) {
      ArrayList entries1 = new ArrayList<>();

      for (Object value3 : list(mapping0.get("bones"))) {
         for (Object value5 : list(obj(value3).get("cubes"))) {
            entries1.add(obj(value5));
         }
      }

      return entries1;
   }

   private static double num(Object value0) {
      return value0 instanceof Number ? ((Number)value0).doubleValue() : Double.NaN;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Collections.emptyMap();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
