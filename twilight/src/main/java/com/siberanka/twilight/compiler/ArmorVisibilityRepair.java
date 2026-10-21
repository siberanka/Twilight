package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class ArmorVisibilityRepair {
   private ArmorVisibilityRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/armor-conversion.json");
      if (resourceBytes1 == null) {
         return 0;
      }

      Map<String, Object> mapping2;
      try {
         mapping2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
      } catch (Exception error36) {
         return 0;
      }

      if (!(mapping2.get("items") instanceof List entries4)) {
         return 0;
      } else {
         HashMap<String, Map<Integer, Integer>> lookup5 = new HashMap<>();

         for (String text7 : new ArrayList<>(packFiles0.keySet())) {
            if (text7.startsWith("twilight/armor-atlas-") && text7.endsWith(".json")) {
               String suffix8 = text7.substring("twilight/armor-atlas-".length(), text7.length() - 5);
               LinkedHashMap<Integer, Integer> lookup9 = new LinkedHashMap<>();

               try {
                  var jsonObject10 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text7)));
                  Object entry12 = jsonObject10.get("columns");
                  if (entry12 instanceof List) {
                     for (Object entry13 : (List)entry12) {
                        var jsonObject14 = obj(entry13);
                        int columnIndex15 = number(jsonObject14.get("column"), -1);
                        int colorRgb16 = number(jsonObject14.get("color"), -1);
                        if (columnIndex15 >= 0) {
                           lookup9.put(columnIndex15, colorRgb16);
                        }
                     }

                     lookup5.put(suffix8, lookup9);
                  }
               } catch (Exception error40) {
               }
            }
         }

         int number41 = 0;
         LinkedHashSet<String> identifiers42 = new LinkedHashSet<>();

         for (Object entry45 : entries4) {
            var jsonObject47 = obj(entry45);
            String identifier49 = str(jsonObject47.get("identifier"));
            String slotName52 = str(jsonObject47.get("slot"));
            if (identifier49 != null && slotName52 != null && identifier49.indexOf(58) >= 0) {
               byte number54 = switch (slotName52) {
                  case "helmet" -> 0;
                  case "chestplate" -> 1;
                  case "leggings" -> 2;
                  case "boots" -> 3;
                  default -> -1;
               };
               if (number54 >= 0) {
                  String suffix58 = identifier49.substring(identifier49.indexOf(58) + 1);

                  for (String text63 : List.of("attachables/" + suffix58 + ".json", "attachables/" + suffix58 + ".player.json")) {
                     byte[] resourceBytes17 = (byte[])packFiles0.get(text63);
                     if (resourceBytes17 != null) {
                        try {
                           var jsonObject18 = obj(BedrockAnimationBridge.Json.read(resourceBytes17));
                           var attachable19 = obj(jsonObject18.get("minecraft:attachable"));
                           var description20 = obj(attachable19.get("description"));
                           if (description20.get("render_controllers") instanceof List entries22 && !entries22.isEmpty()) {
                              LinkedHashMap<Integer, String> lookup23 = new LinkedHashMap<>();
                              String text24 = null;
                              String text25 = null;

                              for (Object entry27 : entries22) {
                                 if (entry27 instanceof Map mapping28 && mapping28.size() == 1) {
                                    String text29 = str(mapping28.keySet().iterator().next());
                                    int separatorIndex30 = text29.lastIndexOf(".c");
                                    if (separatorIndex30 >= 0) {
                                       int number31;
                                       try {
                                          number31 = Integer.parseInt(text29.substring(separatorIndex30 + 2));
                                       } catch (NumberFormatException error38) {
                                          continue;
                                       }

                                       lookup23.put(number31, text29);
                                       String[] segments32 = text29.split("\\.");
                                       if (segments32.length >= 7) {
                                          text25 = segments32[4];
                                          text24 = "render_controllers/armor_" + text25 + "_" + number54 + ".json";
                                       }
                                    }
                                 }
                              }

                              if (text25 != null && lookup23.containsKey(0)) {
                                 Map<Integer, Integer> mapping69 = lookup5.get(text25);
                                 if (mapping69 != null && !mapping69.isEmpty()) {
                                    String text70 = "(math.floor(q.armor_color_slot("
                                       + number54
                                       + ",0)*255+0.5)*65536+math.floor(q.armor_color_slot("
                                       + number54
                                       + ",1)*255+0.5)*256+math.floor(q.armor_color_slot("
                                       + number54
                                       + ",2)*255+0.5))";
                                    ArrayList entries71 = new ArrayList<>();

                                    for (int number74 : lookup23.keySet()) {
                                       if (number74 != 0) {
                                          Integer number76 = (Integer)mapping69.get(number74);
                                          if (number76 != null && number76 >= 0) {
                                             entries71.add("v.twilight_armor_rgb==" + (number76 & 16777215));
                                          }
                                       }
                                    }

                                    String text73 = entries71.isEmpty() ? "1.0" : "!(" + String.join(" || ", entries71) + ")";
                                    ArrayList entries75 = new ArrayList<>();

                                    for (var value79 : lookup23.entrySet()) {
                                       int number33 = (Integer)value79.getKey();
                                       int number34 = mapping69.getOrDefault(number33, -1);
                                       String text35 = number33 == 0 ? text73 : (number34 < 0 ? "0.0" : "v.twilight_armor_rgb==" + (number34 & 16777215));
                                       entries75.add(Collections.singletonMap((String)value79.getValue(), text35));
                                    }

                                    description20.put("render_controllers", entries75);
                                    var scripts78 = obj(description20.get("scripts"));
                                    scripts78.put("pre_animation", List.of("v.twilight_armor_rgb=" + text70 + ";"));
                                    jsonObject18.put("format_version", text63.endsWith(".player.json") ? "1.10.0" : "1.8.0");
                                    byte[] resourceBytes80 = BedrockAnimationBridge.Json.bytes(jsonObject18);
                                    if (!Arrays.equals(resourceBytes17, resourceBytes80)) {
                                       packFiles0.put(text63, resourceBytes80);
                                       number41++;
                                    }

                                    if (text24 != null) {
                                       identifiers42.add(text24);
                                    }
                                 }
                              }
                           }
                        } catch (Exception error39) {
                        }
                     }
                  }
               }
            }
         }

         for (String text46 : identifiers42) {
            byte[] resourceBytes48 = (byte[])packFiles0.get(text46);
            if (resourceBytes48 != null) {
               try {
                  var jsonObject50 = obj(BedrockAnimationBridge.Json.read(resourceBytes48));
                  var renderControllers53 = obj(jsonObject50.get("render_controllers"));

                  for (var value59 : renderControllers53.entrySet()) {
                     var jsonObject62 = obj(value59.getValue());
                     String[] segments64 = ((String)value59.getKey()).split("\\.");
                     if (segments64.length >= 7) {
                        int number65 = Integer.parseInt(segments64[5]);
                        int number66 = Integer.parseInt(segments64[6].substring(1));
                        if (jsonObject62.containsKey("color")) {
                           String text67 = segments64[4];
                           int number68 = lookup5.getOrDefault(text67, Map.of()).getOrDefault(number66, -1);
                           if (number66 == 0) {
                              jsonObject62.put("color", Map.of("r", 1, "g", 1, "b", 1, "a", 1));
                           } else if (number68 >= 0) {
                              jsonObject62.put(
                                 "color", Map.of("r", (number68 >> 16 & 0xFF) / 255.0, "g", (number68 >> 8 & 0xFF) / 255.0, "b", (number68 & 0xFF) / 255.0, "a", 1)
                              );
                           }
                        }
                     }
                  }

                  byte[] resourceBytes56 = BedrockAnimationBridge.Json.bytes(jsonObject50);
                  if (!Arrays.equals(resourceBytes48, resourceBytes56)) {
                     packFiles0.put(text46, resourceBytes56);
                     number41++;
                  }
               } catch (Exception error37) {
               }
            }
         }

         return number41;
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : new LinkedHashMap<>();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static int number(Object value0, int number1) {
      return value0 instanceof Number number2 ? number2.intValue() : number1;
   }
}
