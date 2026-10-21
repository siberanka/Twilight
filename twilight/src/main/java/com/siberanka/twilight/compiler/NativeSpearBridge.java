package com.siberanka.twilight.compiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class NativeSpearBridge {
   private static final String MAPPINGS = "twilight/geyser_item_mappings.json";

   private NativeSpearBridge() {
   }

   public static void apply(Map<String, byte[]> packFiles0) {
      applySpears(packFiles0);
      NativeTridentBridge.apply(packFiles0);
   }

   private static void applySpears(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 != null) {
         try {
            var parsedJson2 = map(BedrockAnimationBridge.Json.read(resourceBytes1));
            var itemDefinitions3 = map(parsedJson2.get("items"));
            if (itemDefinitions3 == null) {
               return;
            }

            boolean flag4 = false;

            for (var value6 : itemDefinitions3.entrySet()) {
               if (isNativeSpearBase((String)value6.getKey())) {
                  var value7 = list(value6.getValue());
                  if (value7 != null) {
                     for (Object value9 : value7) {
                        var value10 = map(value9);
                        if (value10 != null) {
                           String bedrockIdentifier11 = string(value10.get("bedrock_identifier"));
                           if (bedrockIdentifier11 != null && bedrockIdentifier11.startsWith("twilight:")) {
                              String suffix12 = bedrockIdentifier11.substring("twilight:".length());
                              if (suffix12.matches("[a-z0-9_\\-.]+")) {
                                 String geometryName13 = "models/entity/geometry." + suffix12 + ".geo.json";
                                 String animationName14 = "animations/" + suffix12 + ".animation.json";
                                 String attachablePath15 = "attachables/" + suffix12 + ".json";
                                 if (packFiles0.containsKey(geometryName13) && packFiles0.containsKey(attachablePath15)) {
                                    try {
                                       rewriteOne(packFiles0, value10, suffix12, geometryName13, animationName14, attachablePath15);
                                       flag4 = true;
                                    } catch (RuntimeException error17) {
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (flag4) {
               packFiles0.put("twilight/geyser_item_mappings.json", BedrockAnimationBridge.Json.bytes(parsedJson2));
            }
         } catch (RuntimeException error18) {
         }
      }
   }

   public static void syncMapping(Map<String, byte[]> packFiles0, Object value1) {
      byte[] resourceBytes2 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes2 != null && value1 != null) {
         try {
            Class class3 = Class.forName("com.google.gson.JsonElement");
            Class class4 = Class.forName("com.google.gson.JsonParser");
            Object value5 = class4.getMethod("parseString", String.class).invoke(null, new String(resourceBytes2, StandardCharsets.UTF_8));
            Object value6 = value5.getClass().getMethod("get", String.class).invoke(value5, "items");
            if (value6 == null) {
               throw new IllegalStateException("Generated mapping is missing items");
            }

            value1.getClass().getMethod("add", String.class, class3).invoke(value1, "items", value6);
         } catch (ReflectiveOperationException error7) {
            throw new IllegalStateException("Could not synchronize Geyser custom_mappings with generated pack", error7);
         }
      } else {
         throw new IllegalStateException("Missing generated pack mapping or separate mapping root");
      }
   }

   private static boolean isNativeSpearBase(String text0) {
      return text0 != null && text0.startsWith("minecraft:") && (text0.endsWith("_spear") || text0.equals("minecraft:spear"));
   }

   private static void rewriteOne(Map<String, byte[]> packFiles0, Map<String, Object> mapping1, String text2, String text3, String text4, String text5) {
      var value6 = requiredMap((byte[])packFiles0.get(text3));
      var value7 = requiredList(value6.get("minecraft:geometry"));
      if (value7.size() == 1) {
         var value8 = requiredMap(value7.get(0));
         var description9 = requiredMap(value8.get("description"));
         var value10 = requiredList(value8.get("bones"));
         if (!value10.isEmpty()) {
            var value11 = requiredMap((byte[])packFiles0.get(text5));
            var attachable12 = requiredMap(requiredMap(value11.get("minecraft:attachable")).get("description"));
            if (("twilight:" + text2).equals(string(attachable12.get("identifier")))) {
               var animations13 = map(attachable12.get("animations"));
               String text14 = animations13 == null ? null : string(animations13.get("head"));
               var value15 = packFiles0.containsKey(text4) ? requiredMap((byte[])packFiles0.get(text4)) : null;
               var animations16 = value15 == null ? null : map(value15.get("animations"));
               Object value17 = text14 != null && animations16 != null ? animations16.get(text14) : null;
               boolean flag18 = value10.size() == 1 && hasTextureMesh(requiredMap(value10.get(0)));
               boolean flag19 = value10.size() == 1 && isFlat(requiredMap(value10.get(0)));
               String text20 = null;
               if (!flag18 && !flag19) {
                  if (value10.size() == 1) {
                     var value21 = requiredMap(value10.get(0));
                     text20 = string(value21.get("name"));
                     value21.put("name", "spear");
                     value21.put("binding", "q.item_slot_to_bone_name(c.item_slot)");
                  } else {
                     Map mapping29 = null;

                     for (Object value23 : value10) {
                        var value24 = requiredMap(value23);
                        if ("spear".equals(string(value24.get("name")))) {
                           if (mapping29 != null || value24.containsKey("parent")) {
                              return;
                           }

                           mapping29 = value24;
                        }
                     }

                     var value31 = mapping29;
                     if (value31 == null) {
                        value31 = new LinkedHashMap<>();
                        value31.put("name", "spear");
                        value31.put("pivot", List.of(0, 0, 0));
                     }

                     value31.put("binding", "q.item_slot_to_bone_name(c.item_slot)");
                     ArrayList entries33 = new ArrayList<>();
                     entries33.add(value31);

                     for (Object value25 : value10) {
                        var value26 = requiredMap(value25);
                        if (value26 != value31) {
                           if (!value26.containsKey("parent")) {
                              value26.put("parent", "spear");
                           }

                           value26.remove("binding");
                           entries33.add(value26);
                        }
                     }

                     value8.put("bones", entries33);
                  }
               } else {
                  value8.put("bones", List.of(nativeFlatBone()));
                  value6.put("format_version", "1.16.0");
                  description9.remove("visible_bounds_width");
                  description9.remove("visible_bounds_height");
                  description9.remove("visible_bounds_offset");
               }

               if (description9.containsKey("identifier")) {
                  LinkedHashMap lookup30 = new LinkedHashMap<>();
                  lookup30.put("held_first_person", "animation.spear.held_first_person");
                  lookup30.put("held_third_person", "animation.spear.held_third_person");
                  lookup30.put("held_controller", "controller.animation.spear.held");
                  lookup30.put("hit", "animation.spear.hit");
                  lookup30.put("hit_controller", "controller.animation.spear.hit");
                  LinkedHashMap lookup32 = new LinkedHashMap<>();
                  lookup32.put("format_version", "1.10.0");
                  LinkedHashMap lookup34 = new LinkedHashMap<>();
                  if (value17 != null) {
                     if (text20 != null && !"spear".equals(text20)) {
                        renameBoneInAnimation(value17, text20, "spear");
                     } else if (flag18 || flag19) {
                        renameBoneInAnimation(value17, "bone", "spear");
                     }

                     lookup34.put(text14, value17);
                     lookup30.put("head", text14);
                  }

                  lookup32.put("animations", lookup34);
                  attachable12.put("animations", lookup30);
                  LinkedHashMap lookup36 = new LinkedHashMap<>();
                  ArrayList entries37 = new ArrayList<>();
                  String text38 = "c.item_slot == 'main_hand' || c.item_slot == 'off_hand'";
                  entries37.add(Map.of("held_controller", text38));
                  entries37.add(Map.of("hit_controller", text38));
                  if (value17 != null) {
                     entries37.add(Map.of("head", "c.item_slot == 'head'"));
                  }

                  lookup36.put("animate", entries37);
                  attachable12.put("scripts", lookup36);
                  attachable12.put("sound_effects", Map.of("hit", "item.spear.hit"));
                  var bedrockOptions27 = map(mapping1.get("bedrock_options"));
                  if (bedrockOptions27 == null) {
                     bedrockOptions27 = new LinkedHashMap<>();
                  }

                  bedrockOptions27.put("display_handheld", Boolean.TRUE);
                  var value28 = list(bedrockOptions27.get("tags"));
                  if (value28 == null) {
                     value28 = new ArrayList<>();
                  }

                  if (!value28.contains("minecraft:is_spear")) {
                     value28.add("minecraft:is_spear");
                  }

                  bedrockOptions27.put("tags", value28);
                  mapping1.put("bedrock_options", bedrockOptions27);
                  packFiles0.put(text3, BedrockAnimationBridge.Json.bytes(value6));
                  packFiles0.put(text5, BedrockAnimationBridge.Json.bytes(value11));
                  if (value17 != null) {
                     packFiles0.put(text4, BedrockAnimationBridge.Json.bytes(lookup32));
                  } else {
                     packFiles0.remove(text4);
                  }
               }
            }
         }
      }
   }

   private static boolean hasTextureMesh(Map<String, Object> mapping0) {
      var value1 = list(mapping0.get("texture_meshes"));
      return value1 != null && !value1.isEmpty();
   }

   private static boolean isFlat(Map<String, Object> mapping0) {
      var value1 = list(mapping0.get("cubes"));
      if (value1 != null && !value1.isEmpty()) {
         for (Object value3 : value1) {
            var value4 = map(value3);
            if (value4 == null) {
               return false;
            }

            var value5 = list(value4.get("size"));
            if (value5 == null || value5.size() < 3) {
               return false;
            }

            if (!(Math.abs(number(value5.get(2))) < 0.1)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static Map<String, Object> nativeFlatBone() {
      LinkedHashMap lookup0 = new LinkedHashMap<>();
      lookup0.put("local_pivot", List.of(0, 0.5, 0));
      lookup0.put("rotation", List.of(0, -45, 90));
      lookup0.put("use_pixel_depth", Boolean.FALSE);
      lookup0.put("texture", "default");
      LinkedHashMap lookup1 = new LinkedHashMap<>();
      lookup1.put("name", "spear");
      lookup1.put("binding", "q.item_slot_to_bone_name(c.item_slot)");
      lookup1.put("texture_meshes", List.of(lookup0));
      return lookup1;
   }

   private static void renameBoneInAnimation(Object value0, String text1, String text2) {
      var value3 = map(value0);
      if (value3 != null) {
         var value4 = map(value3.get("bones"));
         if (value4 != null && value4.containsKey(text1)) {
            Object value5 = value4.remove(text1);
            value4.put(text2, value5);
         }
      }
   }

   private static Map<String, Object> map(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : null;
   }

   private static Map<String, Object> requiredMap(Object value0) {
      var value1 = map(value0);
      if (value1 == null) {
         throw new IllegalArgumentException("expected object");
      } else {
         return value1;
      }
   }

   private static List<Object> requiredList(Object value0) {
      var value1 = list(value0);
      if (value1 == null) {
         throw new IllegalArgumentException("expected array");
      } else {
         return value1;
      }
   }

   private static Map<String, Object> requiredMap(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         throw new IllegalArgumentException("missing file");
      } else {
         return requiredMap(BedrockAnimationBridge.Json.read(resourceBytes0));
      }
   }

   private static String string(Object value0) {
      return value0 instanceof String ? (String)value0 : null;
   }

   private static double number(Object value0) {
      return value0 instanceof Number ? ((Number)value0).doubleValue() : Double.NaN;
   }
}
