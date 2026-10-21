package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class ArmorFixedColumnBridge {
   private ArmorFixedColumnBridge() {
   }

   public static int apply(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      byte[] resourceBytes3 = (byte[])packFiles2.get("twilight/armor-conversion.json");
      if (resourceBytes3 == null) {
         return 0;
      }

      var parsedJson4 = object(BedrockAnimationBridge.Json.read(resourceBytes3));
      ArrayList<Armor> arrayListArmor5 = new ArrayList<>();

      for (Object entry7 : array(parsedJson4.get("items"))) {
         var value8 = object(entry7);
         String identifier9 = string(value8.get("identifier"));
         String modelName10 = string(value8.get("model"));
         String slotName11 = string(value8.get("slot"));
         if (identifier9 != null && identifier9.startsWith("twilight:") && modelName10 != null && slotName11 != null) {
            arrayListArmor5.add(new ArmorFixedColumnBridge.Armor(identifier9, modelName10, slotName11));
         }
      }

      if (arrayListArmor5.isEmpty()) {
         return 0;
      }

      HashMap lookup33 = new HashMap<>();
      byte[] resourceBytes34 = (byte[])packFiles2.get("twilight/armor-fallback-report.json");
      if (resourceBytes34 != null) {
         for (Object entry37 : array(object(BedrockAnimationBridge.Json.read(resourceBytes34)).get("selected"))) {
            var value39 = object(entry37);
            String identifier42 = string(value39.get("identifier"));
            int columnIndex12 = number(value39.get("fallback_column"), -1);
            if (identifier42 != null && columnIndex12 > 0) {
               lookup33.put(identifier42, new ArmorFixedColumnBridge.Selection(columnIndex12, null));
            }
         }
      }

      HashMap lookup36 = new HashMap<>();
      byte[] resourceBytes38 = (byte[])packFiles2.get("twilight/armor-icons.json");
      if (resourceBytes38 != null) {
         for (Object entry43 : array(object(BedrockAnimationBridge.Json.read(resourceBytes38)).get("items"))) {
            var value47 = object(entry43);
            String identifier13 = string(value47.get("identifier"));
            var value14 = array(value47.get("tints"));
            if (identifier13 != null && !value14.isEmpty()) {
               var value15 = object(value14.get(0));
               if ("dye".equals(string(value15.get("type"))) && value15.get("default") instanceof Number number16) {
                  lookup36.put(identifier13, number16.intValue() & 16777215);
               }
            }
         }
      }

      LinkedHashMap<String, List<Armor>> lookup41 = new LinkedHashMap<>();

      for (ArmorFixedColumnBridge.Armor armorFixedColumnBridgeArmor48 : arrayListArmor5) {
         if (!armorFixedColumnBridgeArmor48.model.startsWith("minecraft:")) {
            lookup41.computeIfAbsent(family(armorFixedColumnBridgeArmor48.model), var0x -> new ArrayList<>()).add(armorFixedColumnBridgeArmor48);
         }
      }

      for (List<Armor> entries49 : lookup41.values()) {
         if (entries49.size() == 4 && entries49.stream().map(var0x -> var0x.slot).distinct().count() == 4L) {
            Integer number51 = null;
            boolean flag54 = true;

            for (ArmorFixedColumnBridge.Armor armorFixedColumnBridgeArmor59 : entries49) {
               Integer number62 = (Integer)lookup36.get(armorFixedColumnBridgeArmor59.identifier);
               if (number62 == null) {
                  flag54 = false;
                  break;
               }

               if (number51 == null) {
                  number51 = number62;
               } else if (!number51.equals(number62)) {
                  flag54 = false;
                  break;
               }
            }

            if (flag54 && number51 != null) {
               for (ArmorFixedColumnBridge.Armor armorFixedColumnBridgeArmor60 : entries49) {
                  lookup33.putIfAbsent(armorFixedColumnBridgeArmor60.identifier, new ArmorFixedColumnBridge.Selection(0, number51));
               }
            }
         }
      }

      int number46 = 0;
      ArrayList entries50 = new ArrayList<>();

      for (ArmorFixedColumnBridge.Armor armorFixedColumnBridgeArmor55 : arrayListArmor5) {
         ArmorFixedColumnBridge.Selection armorFixedColumnBridgeSelection58 = (ArmorFixedColumnBridge.Selection)lookup33.get(armorFixedColumnBridgeArmor55.identifier);
         if (armorFixedColumnBridgeSelection58 != null) {
            String suffix61 = armorFixedColumnBridgeArmor55.identifier.substring(armorFixedColumnBridgeArmor55.identifier.indexOf(58) + 1);
            String text63 = null;
            String text18 = null;
            byte[] resourceBytes19 = null;

            for (String text21 : List.of("attachables/" + suffix61 + ".json", "attachables/" + suffix61 + ".player.json")) {
               byte[] resourceBytes22 = (byte[])packFiles2.get(text21);
               if (resourceBytes22 != null) {
                  var parsedJson23 = object(BedrockAnimationBridge.Json.read(resourceBytes22));
                  var attachable24 = object(object(parsedJson23.get("minecraft:attachable")).get("description"));
                  var renderControllers25 = array(attachable24.get("render_controllers"));
                  if (!renderControllers25.isEmpty()) {
                     if (renderControllers25.size() == 1
                        && renderControllers25.get(0) instanceof String text26
                        && (text26.endsWith(".c" + armorFixedColumnBridgeSelection58.column) || text26.equals("controller.render.twilight.armor.fixed." + suffix61))) {
                        text63 = text26;
                     } else {
                        String text66 = null;

                        for (Object entry28 : renderControllers25) {
                           if (entry28 instanceof Map mapping29 && mapping29.size() == 1) {
                              String text30 = String.valueOf(mapping29.keySet().iterator().next());
                              if (text30.endsWith(".c" + armorFixedColumnBridgeSelection58.column)) {
                                 text66 = text30;
                                 break;
                              }
                           }
                        }

                        if (text66 != null) {
                           if (armorFixedColumnBridgeSelection58.staticTint != null) {
                              if (resourceBytes19 == null) {
                                 text18 = "render_controllers/twilight_armor_fixed_" + suffix61 + ".json";
                                 String text68 = "controller.render.twilight.armor.fixed." + suffix61;
                                 var value70 = findController(packFiles2, text66);
                                 if (value70 == null) {
                                    continue;
                                 }

                                 var value72 = new LinkedHashMap<>(value70);
                                 int number73 = armorFixedColumnBridgeSelection58.staticTint;
                                 LinkedHashMap lookup31 = new LinkedHashMap<>();
                                 lookup31.put("r", (number73 >> 16 & 0xFF) / 255.0);
                                 lookup31.put("g", (number73 >> 8 & 0xFF) / 255.0);
                                 lookup31.put("b", (number73 & 0xFF) / 255.0);
                                 lookup31.put("a", 1);
                                 value72.put("color", lookup31);
                                 LinkedHashMap lookup32 = new LinkedHashMap<>();
                                 lookup32.put("format_version", "1.8.0");
                                 lookup32.put("render_controllers", Map.of(text68, value72));
                                 resourceBytes19 = BedrockAnimationBridge.Json.bytes(lookup32);
                                 text63 = text68;
                              }
                           } else {
                              text63 = text66;
                           }

                           if (text63 != null) {
                              var value69 = List.of(text63);
                              if (!renderControllers25.equals(value69)) {
                                 attachable24.put("render_controllers", value69);
                                 byte[] resourceBytes71 = BedrockAnimationBridge.Json.bytes(parsedJson23);
                                 if (!Arrays.equals(resourceBytes22, resourceBytes71)) {
                                    packFiles2.put(text21, resourceBytes71);
                                    number46++;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (resourceBytes19 != null) {
               byte[] resourceBytes64 = (byte[])packFiles2.get(text18);
               if (!Arrays.equals(resourceBytes64, resourceBytes19)) {
                  packFiles2.put(text18, resourceBytes19);
                  number46++;
               }
            }

            if (text63 != null) {
               LinkedHashMap lookup65 = new LinkedHashMap<>();
               lookup65.put("identifier", armorFixedColumnBridgeArmor55.identifier);
               lookup65.put("model", armorFixedColumnBridgeArmor55.model);
               lookup65.put("column", armorFixedColumnBridgeSelection58.column);
               lookup65.put("controller", text63);
               if (armorFixedColumnBridgeSelection58.staticTint != null) {
                  lookup65.put("tint", String.format(Locale.ROOT, "#%06X", armorFixedColumnBridgeSelection58.staticTint));
               }

               entries50.add(lookup65);
            }
         }
      }

      byte[] resourceBytes53 = BedrockAnimationBridge.Json.bytes(
         Map.of(
            "strategy",
            "Fixed per-item armor controller from Java shader atlas; keep animated frames",
            "resolved",
            entries50,
            "total",
            arrayListArmor5.size(),
            "not_resolved",
            arrayListArmor5.size() - entries50.size()
         )
      );
      if (!Arrays.equals((byte[])packFiles2.get("twilight/armor-fixed-columns.json"), resourceBytes53)) {
         packFiles2.put("twilight/armor-fixed-columns.json", resourceBytes53);
      }

      return number46;
   }

   private static Map<String, Object> findController(Map<String, byte[]> packFiles0, String text1) {
      for (var value3 : packFiles0.entrySet()) {
         if (((String)value3.getKey()).startsWith("render_controllers/armor_") && ((String)value3.getKey()).endsWith(".json")) {
            try {
               var renderControllers4 = object(object(BedrockAnimationBridge.Json.read((byte[])value3.getValue())).get("render_controllers"));
               Object value5 = renderControllers4.get(text1);
               if (value5 instanceof Map) {
                  return object(value5);
               }
            } catch (Exception error6) {
            }
         }
      }

      return null;
   }

   private static String family(String text0) {
      return text0.replaceFirst("(?i)(?:_?(?:helmet|chestplate|leggings|boots|chest|legs))$", "");
   }

   private static Map<String, Object> object(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> array(Object value0) {
      return value0 instanceof List entries1 ? entries1 : Collections.emptyList();
   }

   private static String string(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static int number(Object value0, int number1) {
      return value0 instanceof Number number2 ? number2.intValue() : number1;
   }

   private record Armor(String identifier, String model, String slot) {
   }

   private record Selection(int column, Integer staticTint) {
   }
}
