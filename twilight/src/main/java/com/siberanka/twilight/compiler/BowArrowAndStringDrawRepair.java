package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Restores projectile translation after the depth pass while preserving its
 * facing. A stationary string ribbon already encodes its draw in the texture;
 * mirroring its U coordinates reverses that motion and misplaces idle as well.
 */
public final class BowArrowAndStringDrawRepair {
   private static final String PREV = "twilight/3d-bow-draw-depth-repair.json";
   private static final String MARKER = "twilight/3d-bow-arrow-string-repair.json";
   private static final double E = 1.0E-4;

   private BowArrowAndStringDrawRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
         byte[] resourceBytes2 = (byte[])packFiles0.get("twilight/3d-bow-draw-depth-repair.json");
         if (resourceBytes1 != null && resourceBytes2 != null) {
            LinkedHashSet<String> identifiers3 = new LinkedHashSet<>();

            try {
               var jsonObject4 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));

               for (Object entry6 : list(obj(jsonObject4.get("items")).get("minecraft:bow"))) {
                  String identifier7 = str(obj(entry6).get("bedrock_identifier"));
                  if (identifier7 != null && identifier7.startsWith("twilight:")) {
                     identifiers3.add(identifier7);
                  }
               }

               var jsonObject31 = obj(BedrockAnimationBridge.Json.read(resourceBytes2));
               if (number(jsonObject31.get("version")) != 1.0) {
                  return 0;
               }

               LinkedHashSet identifiers34 = new LinkedHashSet<>();

               for (Object entry8 : list(jsonObject31.get("applied_identifiers"))) {
                  String text9 = str(entry8);
                  if (text9 != null) {
                     identifiers34.add(text9);
                  }
               }

               identifiers3.retainAll(identifiers34);
            } catch (Exception error29) {
               return 0;
            }

            if (identifiers3.isEmpty()) {
               return 0;
            }

            LinkedHashSet identifiers30 = new LinkedHashSet<>();
            LinkedHashSet<String> legacyIdentifiers = new LinkedHashSet<>();
            if (packFiles0.containsKey("twilight/3d-bow-arrow-string-repair.json")) {
               try {
                  var jsonObject32 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/3d-bow-arrow-string-repair.json")));
                  double version = number(jsonObject32.get("version"));
                  if (version == 1.0 || version == 2.0) {
                     for (Object entry39 : list(jsonObject32.get("applied_identifiers"))) {
                        String text41 = str(entry39);
                        if (text41 != null) {
                           if (version == 1.0) legacyIdentifiers.add(text41);
                           else identifiers30.add(text41);
                        }
                     }
                  }
                  if (version == 2.0) {
                     for (Object identifier : list(jsonObject32.get("pending_legacy_identifiers"))) {
                        String text = str(identifier);
                        if (text != null) legacyIdentifiers.add(text);
                     }
                  }
               } catch (Exception error28) {
               }
            }

            int number33 = 0;

            for (String text40 : identifiers3) {
               if (!identifiers30.contains(text40)) {
                  boolean legacy = legacyIdentifiers.contains(text40);
                  String suffix42 = text40.substring(text40.indexOf(58) + 1);
                  if (hasBasis(packFiles0, suffix42)) {
                     String[] segments43 = new String[4];
                     Map[] values10 = new Map[4];
                     List[] values11 = new List[4];
                     boolean flag12 = true;

                     for (int number13 = 1; number13 <= 3; number13++) {
                        segments43[number13] = "models/entity/geometry." + suffix42 + "_state_" + number13 + ".geo.json";

                        try {
                           values10[number13] = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(segments43[number13])));
                           var value14 = list(values10[number13].get("minecraft:geometry"));
                           if (value14.size() != 1) {
                              flag12 = false;
                              break;
                           }

                           values11[number13] = getCubes(obj(value14.get(0)));
                        } catch (Exception error27) {
                           flag12 = false;
                           break;
                        }
                     }

                     if (flag12 && values11[1].size() >= 10 && values11[1].size() == values11[2].size() && values11[1].size() == values11[3].size()) {
                        ArrayList<Integer> arrayListInteger44 = new ArrayList<>();
                        ArrayList<Integer> arrayListInteger45 = new ArrayList<>();
                        int number15 = 0;

                        while (number15 < values11[1].size()) {
                           Map mapping16 = (Map)values11[1].get(number15);
                           Map mapping17 = (Map)values11[2].get(number15);
                           Map mapping18 = (Map)values11[3].get(number15);
                           var value19 = list(mapping16.get("origin"));
                           var value20 = list(mapping17.get("origin"));
                           var value21 = list(mapping18.get("origin"));
                           if (vec(value19) && vec(value20) && vec(value21)) {
                              if (sameVec(list(mapping16.get("size")), list(mapping17.get("size")))
                                 && sameVec(list(mapping16.get("size")), list(mapping18.get("size")))
                                 && sameVec(list(mapping16.get("rotation")), list(mapping17.get("rotation")))
                                 && sameVec(list(mapping16.get("rotation")), list(mapping18.get("rotation")))) {
                                 double value22 = number(value20.get(2)) - number(value19.get(2));
                                 double value24 = number(value21.get(2)) - number(value19.get(2));
                                 // v1 already restored the projectile's negative-Z draw. Only its
                                 // string UVs need migration; never reverse the arrow a third time.
                                 if (legacy) {
                                    value22 = -value22;
                                    value24 = -value24;
                                 }
                                 if (!(Math.abs(value22) > 1.0E-4) && !(Math.abs(value24) > 1.0E-4)) {
                                    var value26 = list(mapping16.get("size"));
                                    if (vec(value26)
                                       && number(value26.get(0)) < 0.1
                                       && number(value26.get(1)) >= 12.0
                                       && number(value26.get(2)) >= 2.0
                                       && hasStringFaces(mapping16)
                                       && hasStringFaces(mapping17)
                                       && hasStringFaces(mapping18)) {
                                       arrayListInteger45.add(number15);
                                    }
                                 } else {
                                    if (!sameXY(value19, value20) || !sameXY(value19, value21) || value22 <= 1.0E-4 || value24 <= value22 + 1.0E-4) {
                                       flag12 = false;
                                       break;
                                    }

                                    if ((mapping16.containsKey("pivot") || mapping17.containsKey("pivot") || mapping18.containsKey("pivot"))
                                       && (
                                          !sameXY(list(mapping16.get("pivot")), list(mapping17.get("pivot")))
                                             || !sameXY(list(mapping16.get("pivot")), list(mapping18.get("pivot")))
                                       )) {
                                       flag12 = false;
                                       break;
                                    }

                                    arrayListInteger44.add(number15);
                                 }

                                 number15++;
                                 continue;
                              }

                              flag12 = false;
                              break;
                           }

                           flag12 = false;
                           break;
                        }

                        if (flag12 && arrayListInteger44.size() >= 2 && arrayListInteger45.size() == 1) {
                           for (int number49 : arrayListInteger44) {
                              if (legacy) continue;
                              Map mapping52 = (Map)values11[1].get(number49);

                              for (int number54 = 2; number54 <= 3; number54++) {
                                 Map mapping56 = (Map)values11[number54].get(number49);
                                 mapping56.put("origin", mirrorZ(list(mapping52.get("origin")), list(mapping56.get("origin"))));
                                 if (vec(list(mapping52.get("pivot"))) && vec(list(mapping56.get("pivot")))) {
                                    mapping56.put("pivot", mirrorZ(list(mapping52.get("pivot")), list(mapping56.get("pivot"))));
                                 }
                              }
                           }

                           for (int number50 : arrayListInteger45) {
                              // The converted UVs already draw the string backwards, in step
                              // with the arrow. v1 flipped U and made it move toward the tip.
                              // Retain the converted UVs on fresh builds; undo only a known v1.
                              if (!legacy) continue;
                              for (int number53 = 1; number53 <= 3; number53++) {
                                 var jsonObject55 = obj(((Map)values11[number53].get(number50)).get("uv"));

                                 for (String text58 : List.of("east", "west")) {
                                    var jsonObject59 = obj(jsonObject55.get(text58));
                                    var value60 = list(jsonObject59.get("uv"));
                                    var value23 = list(jsonObject59.get("uv_size"));
                                    jsonObject59.put("uv", Arrays.asList(number(value60.get(0)) + number(value23.get(0)), number(value60.get(1))));
                                    jsonObject59.put("uv_size", Arrays.asList(-number(value23.get(0)), number(value23.get(1))));
                                 }
                              }
                           }

                           for (int number48 = 1; number48 <= 3; number48++) {
                              byte[] resourceBytes51 = BedrockAnimationBridge.Json.bytes(values10[number48]);
                              if (!Arrays.equals(resourceBytes51, (byte[])packFiles0.get(segments43[number48]))) {
                                 packFiles0.put(segments43[number48], resourceBytes51);
                                 number33++;
                              }
                           }

                           identifiers30.add(text40);
                        }
                     }
                  }
               }
            }

            if (number33 > 0) {
               LinkedHashMap lookup37 = new LinkedHashMap<>();
               lookup37.put("version", 2);
               lookup37.put("applied_identifiers", new ArrayList<>(identifiers30));
               legacyIdentifiers.removeAll(identifiers30);
               if (!legacyIdentifiers.isEmpty()) {
                  lookup37.put("pending_legacy_identifiers", new ArrayList<>(legacyIdentifiers));
               }
               packFiles0.put("twilight/3d-bow-arrow-string-repair.json", BedrockAnimationBridge.Json.bytes(lookup37));
               number33++;
            }

            return number33;
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static boolean hasBasis(Map<String, byte[]> packFiles0, String text1) {
      try {
         var animations2 = obj(obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("animations/" + text1 + "_state_0.animation.json"))).get("animations"));

         for (var value4 : animations2.entrySet()) {
            if (((String)value4.getKey()).contains("first_person_right")) {
               var jsonObject5 = list(obj(obj(obj(value4.getValue()).get("bones")).get("bone_basis")).get("rotation"));
               return vec(jsonObject5) && Math.abs(Math.abs(number(jsonObject5.get(1))) - 180.0) < 1.0E-4;
            }
         }
      } catch (Exception error6) {
      }

      return false;
   }

   private static List<Map<String, Object>> getCubes(Map<String, Object> mapping0) {
      ArrayList entries1 = new ArrayList<>();

      for (Object value3 : list(mapping0.get("bones"))) {
         for (Object value5 : list(obj(value3).get("cubes"))) {
            entries1.add(obj(value5));
         }
      }

      return entries1;
   }

   private static boolean hasStringFaces(Map<String, Object> mapping0) {
      var jsonObject1 = obj(mapping0.get("uv"));

      for (String text3 : List.of("east", "west")) {
         var jsonObject4 = obj(jsonObject1.get(text3));
         if (!vec2(list(jsonObject4.get("uv"))) || !vec2(list(jsonObject4.get("uv_size")))) {
            return false;
         }

         if (Math.abs(number(list(jsonObject4.get("uv_size")).get(0))) < 1.0E-4) {
            return false;
         }
      }

      return true;
   }

   private static List<Object> mirrorZ(List<Object> entries0, List<Object> entries1) {
      return Arrays.asList(number(entries1.get(0)), number(entries1.get(1)), 2.0 * number(entries0.get(2)) - number(entries1.get(2)));
   }

   private static boolean sameXY(List<Object> entries0, List<Object> entries1) {
      return vec(entries0)
         && vec(entries1)
         && Math.abs(number(entries0.get(0)) - number(entries1.get(0))) < 1.0E-4
         && Math.abs(number(entries0.get(1)) - number(entries1.get(1))) < 1.0E-4;
   }

   private static boolean sameVec(List<Object> entries0, List<Object> entries1) {
      return entries0.size() == 0 && entries1.size() == 0
         ? true
         : vec(entries0) && vec(entries1) && sameXY(entries0, entries1) && Math.abs(number(entries0.get(2)) - number(entries1.get(2))) < 1.0E-4;
   }

   private static boolean vec(List<Object> entries0) {
      return entries0.size() == 3 && entries0.get(0) instanceof Number && entries0.get(1) instanceof Number && entries0.get(2) instanceof Number;
   }

   private static boolean vec2(List<Object> entries0) {
      return entries0.size() == 2 && entries0.get(0) instanceof Number && entries0.get(1) instanceof Number;
   }

   private static double number(Object value0) {
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
