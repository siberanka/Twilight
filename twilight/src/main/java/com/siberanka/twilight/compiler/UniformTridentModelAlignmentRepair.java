package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UniformTridentModelAlignmentRepair {
   private static final double MODEL_Y = -3.0;
   private static final String MARKER = "twilight/trident-uniform-model-alignment.json";
   private static final String WIELD = "controller.animation.trident.wield";

   private UniformTridentModelAlignmentRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         var value1 = mappedTridents(packFiles0);
         if (value1.isEmpty()) {
            return 0;
         }

         var value2 = loadMarker(packFiles0);
         int number3 = 0;
         boolean flag4 = false;
         Iterator iterator5 = new ArrayList<>(packFiles0.keySet()).iterator();

         while (true) {
            Map<String, Object> mapping8;
            String text9;
            String text13;
            byte[] resourceBytes14;
            Map<String, Object> mapping15;
            while (true) {
               Map<String, Object> mapping7;
               while (true) {
                  if (!iterator5.hasNext()) {
                     if (flag4) {
                        LinkedHashMap lookup30 = new LinkedHashMap<>();
                        lookup30.put("version", 1);
                        lookup30.put("model_y", -3.0);
                        lookup30.put("applied_identifiers", new ArrayList<>(value2));
                        byte[] resourceBytes31 = BedrockAnimationBridge.Json.bytes(lookup30);
                        if (!Arrays.equals((byte[])packFiles0.get("twilight/trident-uniform-model-alignment.json"), resourceBytes31)) {
                           packFiles0.put("twilight/trident-uniform-model-alignment.json", resourceBytes31);
                           number3++;
                        }
                     }

                     return number3;
                  }

                  String text6 = (String)iterator5.next();
                  if (text6.startsWith("attachables/") && text6.endsWith(".json")) {
                     try {
                        mapping7 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text6)));
                        break;
                     } catch (RuntimeException error28) {
                     }
                  }
               }

               mapping8 = obj(obj(mapping7.get("minecraft:attachable")).get("description"));
               text9 = string(mapping8.get("identifier"));
               if (text9 != null && value1.contains(text9) && !value2.contains(text9)) {
                  var animations10 = obj(mapping8.get("animations"));
                  var scripts11 = obj(mapping8.get("scripts"));
                  if ("controller.animation.trident.wield".equals(animations10.get("wield")) && List.of("wield").equals(scripts11.get("animate"))) {
                     String suffix12 = text9.substring(text9.indexOf(58) + 1);
                     text13 = "models/entity/geometry." + suffix12 + ".geo.json";
                     resourceBytes14 = (byte[])packFiles0.get(text13);
                     if (resourceBytes14 != null) {
                        try {
                           mapping15 = obj(BedrockAnimationBridge.Json.read(resourceBytes14));
                           break;
                        } catch (RuntimeException error29) {
                        }
                     }
                  }
               }
            }

            var value16 = list(mapping15.get("minecraft:geometry"));
            if (value16.size() == 1) {
               var jsonObject17 = obj(value16.get(0));
               var description18 = obj(jsonObject17.get("description"));
               if (Objects.equals(obj(mapping8.get("geometry")).get("default"), description18.get("identifier"))) {
                  var value19 = list(jsonObject17.get("bones"));
                  if (!value19.isEmpty()) {
                     Map mapping20 = null;
                     int number21 = 0;

                     for (Object entry23 : value19) {
                        var jsonObject24 = obj(entry23);
                        if ("pole".equals(jsonObject24.get("name"))) {
                           mapping20 = jsonObject24;
                        }

                        number21 += list(jsonObject24.get("cubes")).size();
                     }

                     if (mapping20 != null && number21 != 0 && String.valueOf(mapping20.get("binding")).contains("item_slot_to_bone_name")) {
                        boolean flag32 = true;

                        for (Object entry37 : value19) {
                           for (Object entry26 : list(obj(entry37).get("cubes"))) {
                              var jsonObject27 = obj(entry26);
                              if (!hasXYZ(jsonObject27.get("origin")) || jsonObject27.containsKey("pivot") && !hasXYZ(jsonObject27.get("pivot"))) {
                                 flag32 = false;
                              }
                           }
                        }

                        if (flag32) {
                           for (Object entry38 : value19) {
                              for (Object entry40 : list(obj(entry38).get("cubes"))) {
                                 var jsonObject41 = obj(entry40);
                                 jsonObject41.put("origin", shifted(jsonObject41.get("origin")));
                                 if (jsonObject41.containsKey("pivot")) {
                                    jsonObject41.put("pivot", shifted(jsonObject41.get("pivot")));
                                 }
                              }
                           }

                           if (hasXYZ(description18.get("visible_bounds_offset"))) {
                              var value35 = list(description18.get("visible_bounds_offset"));
                              description18.put("visible_bounds_offset", List.of(number(value35.get(0)), number(value35.get(1)) + -0.1875, number(value35.get(2))));
                           }

                           byte[] resourceBytes36 = BedrockAnimationBridge.Json.bytes(mapping15);
                           if (!Arrays.equals(resourceBytes14, resourceBytes36)) {
                              packFiles0.put(text13, resourceBytes36);
                              number3++;
                           }

                           value2.add(text9);
                           flag4 = true;
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

   private static Set<String> mappedTridents(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 == null) {
         return Set.of();
      }

      try {
         var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         LinkedHashSet identifiers3 = new LinkedHashSet<>();

         for (Object value5 : list(obj(jsonObject2.get("items")).get("minecraft:trident"))) {
            String bedrockIdentifier6 = string(obj(value5).get("bedrock_identifier"));
            if (bedrockIdentifier6 != null && bedrockIdentifier6.startsWith("twilight:")) {
               identifiers3.add(bedrockIdentifier6);
            }
         }

         return identifiers3;
      } catch (RuntimeException error7) {
         return Set.of();
      }
   }

   private static Set<String> loadMarker(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/trident-uniform-model-alignment.json");
      LinkedHashSet identifiers2 = new LinkedHashSet<>();
      if (resourceBytes1 == null) {
         return identifiers2;
      }

      try {
         var jsonObject3 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         if (!Objects.equals(jsonObject3.get("version"), 1L) && !Objects.equals(jsonObject3.get("version"), 1)) {
            return identifiers2;
         }

         if (jsonObject3.get("model_y") instanceof Number number4 && number4.doubleValue() == -3.0) {
            for (Object value6 : list(jsonObject3.get("applied_identifiers"))) {
               String text7 = string(value6);
               if (text7 != null) {
                  identifiers2.add(text7);
               }
            }

            return identifiers2;
         } else {
            return identifiers2;
         }
      } catch (RuntimeException error8) {
         return identifiers2;
      }
   }

   private static List<Object> shifted(Object value0) {
      var value1 = list(value0);
      return List.of(number(value1.get(0)), number(value1.get(1)) + -3.0, number(value1.get(2)));
   }

   private static boolean hasXYZ(Object value0) {
      var value1 = list(value0);
      return value1.size() == 3 && value1.get(0) instanceof Number && value1.get(1) instanceof Number && value1.get(2) instanceof Number;
   }

   private static double number(Object value0) {
      return ((Number)value0).doubleValue();
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String string(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
