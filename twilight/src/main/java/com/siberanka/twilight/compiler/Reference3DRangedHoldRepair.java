package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Reference3DRangedHoldRepair {
   private static final List<String> HANDS = List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left");

   private Reference3DRangedHoldRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         LinkedHashMap lookup1 = new LinkedHashMap<>();
         LinkedHashSet identifiers2 = new LinkedHashSet<>();

         try {
            var jsonObject3 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/geyser_item_mappings.json")));
            var itemDefinitions4 = obj(jsonObject3.get("items"));

            for (String text6 : List.of("minecraft:bow", "minecraft:crossbow")) {
               Reference3DRangedHoldRepair.Kind referenceDRangedHoldRepairKind7 = text6.equals("minecraft:bow")
                  ? Reference3DRangedHoldRepair.Kind.BOW
                  : Reference3DRangedHoldRepair.Kind.CROSSBOW;

               for (Object entry9 : list(itemDefinitions4.get(text6))) {
                  var jsonObject10 = obj(entry9);
                  String identifier11 = str(jsonObject10.get("bedrock_identifier"));
                  if (identifier11 != null && identifier11.startsWith("twilight:")) {
                     lookup1.put(identifier11, referenceDRangedHoldRepairKind7);
                     var jsonObject12 = obj(jsonObject10.get("predicate"));
                     if ("charge_type".equals(str(jsonObject12.get("property")))) {
                        identifiers2.add(identifier11);
                     }
                  }
               }
            }
         } catch (RuntimeException error36) {
            return 0;
         }

         int number37 = 0;
         Iterator iterator38 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            Map<String, Object> mapping40;
            while (true) {
               if (!iterator38.hasNext()) {
                  return number37;
               }

               String text39 = (String)iterator38.next();
               if (text39.startsWith("attachables/") && text39.endsWith(".json") && !text39.endsWith(".player.json")) {
                  try {
                     mapping40 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text39)));
                     break;
                  } catch (RuntimeException error34) {
                  }
               }
            }

            var attachable41 = obj(obj(mapping40.get("minecraft:attachable")).get("description"));
            String identifier42 = str(attachable41.get("identifier"));
            Reference3DRangedHoldRepair.Kind referenceDRangedHoldRepairKind43 = (Reference3DRangedHoldRepair.Kind)lookup1.get(identifier42);
            if (referenceDRangedHoldRepairKind43 != null) {
               String suffix44 = identifier42.substring(identifier42.indexOf(58) + 1);
               String text45 = "twilight/ranged-hold-" + suffix44 + ".json";
               if (!packFiles0.containsKey(text45)) {
                  var geometry46 = obj(attachable41.get("geometry"));
                  var animations13 = obj(attachable41.get("animations"));
                  boolean flag14 = geometry46.containsKey("state_0") && geometry46.containsKey("state_3");
                  if (is3D(packFiles0, suffix44, flag14)) {
                     LinkedHashMap lookup15 = new LinkedHashMap<>();
                     int number16 = flag14 ? 4 : 1;
                     boolean flag17 = true;

                     for (int number18 = 0; number18 < number16; number18++) {
                        String animationName19 = "animations/" + suffix44 + (flag14 ? "_state_" + number18 : "") + ".animation.json";
                        byte[] resourceBytes20 = (byte[])packFiles0.get(animationName19);
                        if (resourceBytes20 == null) {
                           flag17 = false;
                           break;
                        }

                        try {
                           var jsonObject21 = obj(BedrockAnimationBridge.Json.read(resourceBytes20));
                           var animations22 = obj(jsonObject21.get("animations"));

                           for (String text24 : HANDS) {
                              String text25 = (flag14 ? "state_" + number18 + "_" : "") + text24;
                              String text26 = str(animations13.get(text25));
                              if (text26 != null && animations22.containsKey(text26)) {
                                 if (!obj(obj(animations22.get(text26)).get("bones")).containsKey("bone")) {
                                    flag17 = false;
                                 }
                              } else {
                                 flag17 = false;
                              }
                           }

                           lookup15.put(animationName19, jsonObject21);
                        } catch (RuntimeException error35) {
                           flag17 = false;
                           break;
                        }

                        if (!flag17) {
                           break;
                        }
                     }

                     if (flag17) {
                        Map mapping47 = (Map)lookup15.values().iterator().next();
                        var animations48 = obj(mapping47.get("animations"));
                        String text49 = str(animations13.get((flag14 ? "state_0_" : "") + "first_person_right"));
                        var jsonObject50 = obj(obj(animations48.get(text49)).get("bones"));
                        var jsonObject51 = obj(jsonObject50.get("bone_x"));
                        if (!jsonObject51.isEmpty()) {
                           for (int number52 = 0; number52 < number16; number52++) {
                              String animationName53 = "animations/" + suffix44 + (flag14 ? "_state_" + number52 : "") + ".animation.json";
                              Map mapping54 = (Map)lookup15.get(animationName53);
                              var animations55 = obj(mapping54.get("animations"));

                              for (String text28 : HANDS) {
                                 String text29 = (flag14 ? "state_" + number52 + "_" : "") + text28;
                                 String text30 = str(animations13.get(text29));
                                 var jsonObject31 = obj(obj(animations55.get(text30)).get("bones"));
                                 if (text28.startsWith("third_person")) {
                                    var jsonObject32 = obj(jsonObject31.get("bone"));
                                    jsonObject32.put("rotation", List.of(90.0, 0.0, 0.0));
                                    jsonObject32.put("position", List.of(0.0, 13.0, -3.0));
                                    jsonObject32.put("scale", List.of(1.0, 1.0, 1.0));
                                    LinkedHashMap lookup33 = new LinkedHashMap<>();
                                    if (referenceDRangedHoldRepairKind43 == Reference3DRangedHoldRepair.Kind.BOW) {
                                       lookup33.put("position", List.of(0.0, -2.0, 0.75));
                                    } else {
                                       lookup33.put("position", List.of(0.0, 1.5, 0.5));
                                    }

                                    lookup33.put("rotation", List.of(0.0, 0.0, 0.0));
                                    lookup33.put("scale", List.of(1.0, 1.0, 1.0));
                                    jsonObject31.put("bone_x", lookup33);
                                    jsonObject31.put("bone_y", Map.of("rotation", List.of(0.0, 0.0, 0.0)));
                                    jsonObject31.put("bone_z", Map.of("rotation", List.of(0.0, 0.0, 0.0)));
                                    if (referenceDRangedHoldRepairKind43 == Reference3DRangedHoldRepair.Kind.CROSSBOW) {
                                       // Same model-facing correction as 3D bows. The crossbow already uses
                                       // this basis in first person; only the third-person rig was missing it.
                                       jsonObject31.put("bone_basis", Map.of("rotation", List.of(0.0, 180.0, 0.0)));
                                    }
                                 } else if (text28.equals("first_person_right")) {
                                    if (referenceDRangedHoldRepairKind43 == Reference3DRangedHoldRepair.Kind.BOW && number52 > 0) {
                                       applyBowPull(jsonObject31, jsonObject51);
                                    } else if (referenceDRangedHoldRepairKind43 == Reference3DRangedHoldRepair.Kind.CROSSBOW && flag14 && number52 > 0) {
                                       applyCrossbowPull(jsonObject31, jsonObject51);
                                    } else if (referenceDRangedHoldRepairKind43 == Reference3DRangedHoldRepair.Kind.CROSSBOW && identifiers2.contains(identifier42)) {
                                       applyCrossbowCharged(jsonObject31);
                                    }
                                 }
                              }

                              byte[] resourceBytes56 = BedrockAnimationBridge.Json.bytes(mapping54);
                              if (!Arrays.equals((byte[])packFiles0.get(animationName53), resourceBytes56)) {
                                 packFiles0.put(animationName53, resourceBytes56);
                                 number37++;
                              }
                           }

                           packFiles0.put(
                              text45,
                              BedrockAnimationBridge.Json.bytes(
                                 Map.of(
                                    "reference",
                                    "Java generated pack display + SMC Bedrock ranged 3D holding rig",
                                    "format",
                                    "3d_bone_chain",
                                    "type",
                                    referenceDRangedHoldRepairKind43.toString().toLowerCase(Locale.ROOT),
                                    "states",
                                    number16,
                                    "texture_and_charge_selection",
                                    "unchanged"
                                 )
                              )
                           );
                           number37++;
                        }
                     }
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static boolean is3D(Map<String, byte[]> packFiles0, String text1, boolean flag2) {
      String geometryName3 = "models/entity/geometry." + text1 + (flag2 ? "_state_0" : "") + ".geo.json";

      try {
         var jsonObject4 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(geometryName3)));
         var value5 = list(jsonObject4.get("minecraft:geometry"));
         if (value5.size() != 1) {
            return false;
         }

         var jsonObject6 = list(obj(value5.get(0)).get("bones"));
         if (jsonObject6.size() < 5) {
            return false;
         }

         HashSet identifiers7 = new HashSet<>();
         int number8 = 0;

         for (Object value10 : jsonObject6) {
            var jsonObject11 = obj(value10);
            identifiers7.add(str(jsonObject11.get("name")));
            number8 += list(jsonObject11.get("cubes")).size();
         }

         return identifiers7.containsAll(List.of("bone", "bone_x", "bone_y", "bone_z", "bone_basis")) && number8 > 8;
      } catch (RuntimeException error12) {
         return false;
      }
   }

   private static void applyBowPull(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      obj(mapping0.get("bone")).put("rotation", List.of(135.0, 60.0, -15.0));
      obj(mapping0.get("bone")).put("position", List.of(-4.5, 19.5, -5.5));
      obj(mapping0.get("bone")).put("scale", List.of(1.4, 1.4, 1.4));
      var value2 = list(mapping1.get("position"));
      var value3 = list(mapping1.get("rotation"));
      var jsonObject4 = obj(mapping0.get("bone_x"));
      jsonObject4.put("position", List.of(v(value2, 0) - 0.25, v(value2, 1) - 3.25, v(value2, 2) - 4.5));
      jsonObject4.put("rotation", List.of(v(value3, 0) - 20.0, 0.0, 0.0));
      jsonObject4.put("scale", List.of(1.25, 1.25, 1.25));
      var jsonObject5 = obj(mapping0.get("bone_y"));
      jsonObject5.put("rotation", List.of(0.0, -19.0, 0.0));
   }

   private static void applyCrossbowPull(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      obj(mapping0.get("bone")).put("rotation", List.of(65.0, 60.0, -71.0));
      obj(mapping0.get("bone")).put("position", List.of(0.0, 22.5, -12.25));
      obj(mapping0.get("bone")).put("scale", List.of(1.4, 1.4, 1.4));
      var value2 = list(mapping1.get("position"));
      var jsonObject3 = obj(mapping0.get("bone_x"));
      jsonObject3.put("position", List.of(v(value2, 0) - 7.75, v(value2, 1) - 2.0, v(value2, 2) + 1.75));
      jsonObject3.put("rotation", List.of(-1.02, 0.0, 0.0));
      jsonObject3.put("scale", List.of(1.0, 1.0, 1.0));
      obj(mapping0.get("bone_y")).put("rotation", List.of(0.0, 10.75, 0.0));
      obj(mapping0.get("bone_z")).put("rotation", List.of(0.0, 0.0, 0.16));
   }

   private static void applyCrossbowCharged(Map<String, Object> mapping0) {
      obj(mapping0.get("bone")).put("rotation", List.of(65.0, 60.0, -71.0));
      obj(mapping0.get("bone")).put("position", List.of(0.0, 22.5, -12.25));
      obj(mapping0.get("bone")).put("scale", List.of(1.4, 1.4, 1.4));
      var jsonObject1 = obj(mapping0.get("bone_x"));
      jsonObject1.put("position", List.of(-0.37, 2.75, 5.0));
      jsonObject1.put("rotation", List.of(-1.02, 0.0, 0.0));
      jsonObject1.put("scale", List.of(1.0, 1.0, 1.0));
      obj(mapping0.get("bone_y")).put("rotation", List.of(0.0, 9.75, 0.0));
      obj(mapping0.get("bone_z")).put("rotation", List.of(0.0, 0.0, 0.15));
   }

   private static double v(List<Object> entries0, int number1) {
      return entries0.size() > number1 && entries0.get(number1) instanceof Number number2 ? number2.doubleValue() : 0.0;
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

   private enum Kind {
      BOW,
      CROSSBOW;
   }
}
