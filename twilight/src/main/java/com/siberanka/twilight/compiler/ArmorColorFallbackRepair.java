package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import javax.imageio.ImageIO;

public final class ArmorColorFallbackRepair {
   private ArmorColorFallbackRepair() {
   }

   public static int apply(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      byte[] resourceBytes3 = (byte[])packFiles2.get("twilight/armor-conversion.json");
      if (resourceBytes3 == null) {
         return 0;
      }

      var jsonObject4 = obj(BedrockAnimationBridge.Json.read(resourceBytes3));
      var itemDefinitions5 = list(jsonObject4.get("items"));
      if (itemDefinitions5.isEmpty()) {
         return 0;
      }

      HashMap<String, List<Column>> lookup6 = new HashMap<>();

      for (String text8 : packFiles2.keySet()) {
         if (text8.startsWith("twilight/armor-atlas-") && text8.endsWith(".json")) {
            String suffix9 = text8.substring("twilight/armor-atlas-".length(), text8.length() - 5);
            ArrayList<Column> arrayListColumn10 = new ArrayList<>();

            try {
               for (Object entry12 : list(obj(BedrockAnimationBridge.Json.read((byte[])packFiles2.get(text8))).get("columns"))) {
                  var jsonObject13 = obj(entry12);
                  int number14 = asInt(jsonObject13.get("column"), -1);
                  int number15 = asInt(jsonObject13.get("color"), -1);
                  if (number14 >= 0) {
                     String texturePath16 = "textures/twilight/armor/" + suffix9 + "_c" + number14 + "_0.png";
                     float[] values17 = imageAppearance((byte[])packFiles2.get(texturePath16));
                     if (values17 != null) {
                        arrayListColumn10.add(new ArmorColorFallbackRepair.Column(number14, number15, values17[0], values17[1]));
                     }
                  }
               }
            } catch (Exception error40) {
            }

            if (!arrayListColumn10.isEmpty()) {
               lookup6.put(suffix9, arrayListColumn10);
            }
         }
      }

      if (lookup6.isEmpty()) {
         return 0;
      }

      var value41 = readItemTints(resourceIndex0);
      LinkedHashMap<String, Choice> lookup42 = new LinkedHashMap<>();
      HashMap<String, String> lookup43 = new HashMap<>();
      HashMap<String, List<Choice>> lookup44 = new HashMap<>();
      ArrayList<Item> arrayListItem45 = new ArrayList<>();

      for (Object entry48 : itemDefinitions5) {
         var jsonObject53 = obj(entry48);
         String identifier58 = str(jsonObject53.get("identifier"));
         String modelName62 = str(jsonObject53.get("model"));
         String slotName66 = str(jsonObject53.get("slot"));
         String text18 = str(jsonObject53.get("atlas"));
         if (identifier58 != null && modelName62 != null && slotName66 != null && text18 != null && identifier58.startsWith("twilight:") && identifier58.indexOf(58) >= 0) {
            String text19 = atlasForSource(packFiles2, text18);
            if (text19 != null) {
               ArmorColorFallbackRepair.Item armorColorFallbackRepairItem20 = new ArmorColorFallbackRepair.Item(identifier58, modelName62, slotName66, text19, text18);
               arrayListItem45.add(armorColorFallbackRepairItem20);
               String text21 = familyName(modelName62);
               lookup43.put(identifier58, text21);
               String suffix22 = identifier58.substring(identifier58.indexOf(58) + 1);
               float[] values23 = imageAppearance((byte[])packFiles2.get("textures/twilight/" + suffix22 + "_icon.png"));
               ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice24 = null;
               if (values23 != null && values23[1] >= 0.26F && values23[2] >= 0.14F) {
                  armorColorFallbackRepairChoice24 = matchAppearance(values23, (List<ArmorColorFallbackRepair.Column>)lookup6.get(text19), "icon", true);
               }

               Integer number25 = (Integer)value41.get(slotName66 + "|" + modelName62);
               if ((armorColorFallbackRepairChoice24 == null || armorColorFallbackRepairChoice24.quality < 0.58) && number25 != null) {
                  ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice26 = matchExplicitMarker(number25 & 16777215, (List<ArmorColorFallbackRepair.Column>)lookup6.get(text19));
                  if (armorColorFallbackRepairChoice26 != null && (armorColorFallbackRepairChoice24 == null || armorColorFallbackRepairChoice26.quality > armorColorFallbackRepairChoice24.quality + 0.12)) {
                     armorColorFallbackRepairChoice24 = armorColorFallbackRepairChoice26;
                  }
               }

               if (armorColorFallbackRepairChoice24 != null && armorColorFallbackRepairChoice24.quality >= 0.46) {
                  lookup42.put(identifier58, armorColorFallbackRepairChoice24);
                  if (armorColorFallbackRepairChoice24.quality >= 0.6) {
                     lookup44.computeIfAbsent(text21, var0x -> new ArrayList<>()).add(armorColorFallbackRepairChoice24);
                  }
               }
            }
         }
      }

      LinkedHashMap<String, List<Item>> lookup47 = new LinkedHashMap<>();

      for (ArmorColorFallbackRepair.Item armorColorFallbackRepairItem54 : arrayListItem45) {
         lookup47.computeIfAbsent((String)lookup43.get(armorColorFallbackRepairItem54.id), var0x -> new ArrayList<>()).add(armorColorFallbackRepairItem54);
      }

      for (var value55 : lookup47.entrySet()) {
         if ((value55.getValue()).size() >= 3) {
            HashMap<Integer, Double> lookup59 = new HashMap<>();
            HashMap<Integer, Integer> lookup63 = new HashMap<>();

            for (ArmorColorFallbackRepair.Item armorColorFallbackRepairItem71 : value55.getValue()) {
               ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice76 = (ArmorColorFallbackRepair.Choice)lookup42.get(armorColorFallbackRepairItem71.id);
               if (armorColorFallbackRepairChoice76 != null && !(armorColorFallbackRepairChoice76.quality < 0.6)) {
                  lookup59.merge(armorColorFallbackRepairChoice76.column, armorColorFallbackRepairChoice76.quality * ("icon".equals(armorColorFallbackRepairChoice76.evidence) ? 2.0 : 0.65), Double::sum);
                  lookup63.merge(armorColorFallbackRepairChoice76.column, 1, Integer::sum);
               }
            }

            int number68 = -1;
            int number72 = 0;
            double value77 = -1.0;

            for (var value85 : lookup59.entrySet()) {
               int number89 = lookup63.getOrDefault(value85.getKey(), 0);
               if ((Double)value85.getValue() > value77) {
                  number72 = number89;
                  value77 = (Double)value85.getValue();
                  number68 = (Integer)value85.getKey();
               }
            }

            if (number68 >= 0 && number72 >= 2) {
               int number83 = 0;

               for (var value90 : lookup63.entrySet()) {
                  if ((Integer)value90.getKey() != number68) {
                     number83 = Math.max(number83, (Integer)value90.getValue());
                  }
               }

               double value87 = 0.0;

               for (var value95 : lookup59.entrySet()) {
                  if ((Integer)value95.getKey() != number68) {
                     value87 = Math.max(value87, (Double)value95.getValue());
                  }
               }

               if (!(value77 < value87 * 1.15)) {
                  for (ArmorColorFallbackRepair.Item armorColorFallbackRepairItem96 : value55.getValue()) {
                     lookup42.put(armorColorFallbackRepairItem96.id, new ArmorColorFallbackRepair.Choice(number68, 0.72, "consistent_armor_family"));
                  }
               }
            }
         }
      }

      for (ArmorColorFallbackRepair.Item armorColorFallbackRepairItem56 : arrayListItem45) {
         ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice60 = (ArmorColorFallbackRepair.Choice)lookup42.get(armorColorFallbackRepairItem56.id);
         if (armorColorFallbackRepairChoice60 == null || !(armorColorFallbackRepairChoice60.quality >= 0.64)) {
            var value64 = lookup44.getOrDefault(lookup43.get(armorColorFallbackRepairItem56.id), List.of());
            if (!value64.isEmpty()) {
               HashMap<Integer, Double> lookup69 = new HashMap<>();

               for (ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice78 : value64) {
                  lookup69.merge(armorColorFallbackRepairChoice78.column, armorColorFallbackRepairChoice78.quality, Double::sum);
               }

               var value74 = new ArrayList<>(lookup69.entrySet());
               value74.sort((var0x, var1x) -> Double.compare((Double)var1x.getValue(), (Double)var0x.getValue()));
               if (value74.size() <= 1 || !((Double)((Entry)value74.get(0)).getValue() < 1.4 * (Double)((Entry)value74.get(1)).getValue())) {
                  int number79 = (Integer)((Entry)value74.get(0)).getKey();
                  if (armorColorFallbackRepairChoice60 == null || armorColorFallbackRepairChoice60.quality < 0.55) {
                     lookup42.put(armorColorFallbackRepairItem56.id, new ArmorColorFallbackRepair.Choice(number79, 0.59, "related_model"));
                  }
               }
            }
         }
      }

      int number52 = 0;
      ArrayList entries57 = new ArrayList<>();

      for (ArmorColorFallbackRepair.Item armorColorFallbackRepairItem65 : arrayListItem45) {
         ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice70 = (ArmorColorFallbackRepair.Choice)lookup42.get(armorColorFallbackRepairItem65.id);
         if (armorColorFallbackRepairChoice70 != null && armorColorFallbackRepairChoice70.column != 0) {
            String suffix75 = armorColorFallbackRepairItem65.id.substring(armorColorFallbackRepairItem65.id.indexOf(58) + 1);

            for (String text81 : List.of("attachables/" + suffix75 + ".json", "attachables/" + suffix75 + ".player.json")) {
               byte[] resourceBytes84 = (byte[])packFiles2.get(text81);
               if (resourceBytes84 != null) {
                  try {
                     var jsonObject88 = obj(BedrockAnimationBridge.Json.read(resourceBytes84));
                     var attachable91 = obj(obj(jsonObject88.get("minecraft:attachable")).get("description"));
                     var renderControllers94 = list(attachable91.get("render_controllers"));
                     if (!renderControllers94.isEmpty()) {
                        LinkedHashMap<Integer, String> lookup97 = new LinkedHashMap<>();

                        for (Object entry27 : renderControllers94) {
                           var jsonObject28 = obj(entry27);
                           if (jsonObject28.size() == 1) {
                              String text29 = (String)jsonObject28.keySet().iterator().next();
                              int separatorIndex30 = text29.lastIndexOf(".c");
                              if (separatorIndex30 >= 0) {
                                 try {
                                    lookup97.put(Integer.parseInt(text29.substring(separatorIndex30 + 2)), text29);
                                 } catch (Exception error37) {
                                 }
                              }
                           }
                        }

                        if (lookup97.containsKey(armorColorFallbackRepairChoice70.column) && lookup97.containsKey(0)) {
                           ArrayList entries99 = new ArrayList<>();

                           for (var value102 : lookup97.entrySet()) {
                              if ((Integer)value102.getKey() != 0) {
                                 for (ArmorColorFallbackRepair.Column armorColorFallbackRepairColumn107 : lookup6.get(armorColorFallbackRepairItem65.atlasHash)) {
                                    if (armorColorFallbackRepairColumn107.id == (Integer)value102.getKey() && armorColorFallbackRepairColumn107.dyeRgb >= 0) {
                                       entries99.add("v.twilight_armor_rgb==" + (armorColorFallbackRepairColumn107.dyeRgb & 16777215));
                                    }
                                 }
                              }
                           }

                           String text101 = entries99.isEmpty() ? "1.0" : "!(" + String.join(" || ", entries99) + ")";
                           ArrayList entries103 = new ArrayList<>();

                           for (Object entry108 : renderControllers94) {
                              var jsonObject31 = obj(entry108);
                              if (jsonObject31.size() != 1) {
                                 entries103.add(entry108);
                              } else {
                                 String text32 = (String)jsonObject31.keySet().iterator().next();
                                 int separatorIndex33 = text32.lastIndexOf(".c");
                                 if (separatorIndex33 < 0) {
                                    entries103.add(entry108);
                                 } else {
                                    int number34;
                                    try {
                                       number34 = Integer.parseInt(text32.substring(separatorIndex33 + 2));
                                    } catch (Exception error38) {
                                       entries103.add(entry108);
                                       continue;
                                    }

                                    Object entry35 = jsonObject31.get(text32);
                                    String text36 = entry35 instanceof String ? (String)entry35 : "0.0";
                                    if (number34 == 0) {
                                       text36 = "0.0";
                                    } else if (number34 == armorColorFallbackRepairChoice70.column && !text36.contains(text101)) {
                                       text36 = "(" + text36 + ") || (" + text101 + ")";
                                    }

                                    entries103.add(Collections.singletonMap(text32, text36));
                                 }
                              }
                           }

                           attachable91.put("render_controllers", entries103);
                           byte[] resourceBytes106 = BedrockAnimationBridge.Json.bytes(jsonObject88);
                           if (!Arrays.equals(resourceBytes84, resourceBytes106)) {
                              packFiles2.put(text81, resourceBytes106);
                              number52++;
                           }
                        }
                     }
                  } catch (Exception error39) {
                  }
               }
            }

            entries57.add(
               Map.of(
                  "identifier",
                  armorColorFallbackRepairItem65.id,
                  "model",
                  armorColorFallbackRepairItem65.model,
                  "fallback_column",
                  armorColorFallbackRepairChoice70.column,
                  "evidence",
                  armorColorFallbackRepairChoice70.evidence,
                  "confidence",
                  Math.round(armorColorFallbackRepairChoice70.quality * 100.0)
               )
            );
         }
      }

      packFiles2.put(
         "twilight/armor-fallback-report.json",
         BedrockAnimationBridge.Json.bytes(
            Map.of("selected", entries57, "total_armor", arrayListItem45.size(), "strategy", "Java resource pack only; runtime armor dye still takes precedence")
         )
      );
      return number52;
   }

   private static Map<String, Integer> readItemTints(ResourceIndex resourceIndex0) {
      HashMap lookup1 = new HashMap<>();
      if (resourceIndex0 == null) {
         return lookup1;
      }

      for (String text3 : List.of("helmet", "chestplate", "leggings", "boots")) {
         String text4 = "assets/minecraft/items/leather_" + text3 + ".json";

         try {
            var value5 = resourceIndex0.find(text4);
            if (!value5.isEmpty()) {
               var jsonObject6 = obj(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value5.get()).readBytes()));
               var jsonObject7 = obj(jsonObject6.get("model"));

               for (Object value9 : list(jsonObject7.get("entries"))) {
                  var jsonObject10 = obj(value9);
                  var jsonObject11 = obj(jsonObject10.get("model"));
                  String modelName12 = str(jsonObject11.get("model"));
                  if (modelName12 != null) {
                     var value13 = list(jsonObject11.get("tints"));
                     if (!value13.isEmpty() && obj(value13.get(0)).get("default") instanceof Number number15) {
                        lookup1.put(text3 + "|" + modelName12, number15.intValue());
                     }
                  }
               }
            }
         } catch (Exception error16) {
         }
      }

      return lookup1;
   }

   private static String atlasForSource(Map<String, byte[]> packFiles0, String text1) {
      for (String text3 : packFiles0.keySet()) {
         if (text3.startsWith("twilight/armor-atlas-") && text3.endsWith(".json")) {
            try {
               if (text1.equals(str(obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text3))).get("source")))) {
                  return text3.substring("twilight/armor-atlas-".length(), text3.length() - 5);
               }
            } catch (Exception error5) {
            }
         }
      }

      return null;
   }

   private static String familyName(String text0) {
      String text1 = text0.toLowerCase(Locale.ROOT);
      return text1.replaceAll("(?i)(?:_?(?:helmet|chestplate|chest|leggings|legs|boots))$", "");
   }

   private static ArmorColorFallbackRepair.Choice matchExplicitMarker(int number0, List<ArmorColorFallbackRepair.Column> entries1) {
      if (entries1 == null) {
         return null;
      }

      float[] values2 = colorAppearance(number0);
      ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice3 = null;

      for (ArmorColorFallbackRepair.Column armorColorFallbackRepairColumn5 : entries1) {
         if (armorColorFallbackRepairColumn5.id != 0 && armorColorFallbackRepairColumn5.dyeRgb >= 0) {
            int number6 = armorColorFallbackRepairColumn5.dyeRgb >> 16 & 0xFF;
            int number7 = armorColorFallbackRepairColumn5.dyeRgb >> 8 & 0xFF;
            int number8 = armorColorFallbackRepairColumn5.dyeRgb & 0xFF;
            int number9 = (number0 >> 16 & 0xFF) - number6;
            int number10 = (number0 >> 8 & 0xFF) - number7;
            int number11 = (number0 & 0xFF) - number8;
            double value12 = Math.sqrt(number9 * number9 + number10 * number10 + number11 * number11);
            if (!(value12 > 78.0)) {
               float value14 = armorColorFallbackRepairColumn5.hue;
               if (armorColorFallbackRepairColumn5.id == 1) {
                  value14 = colorAppearance(armorColorFallbackRepairColumn5.dyeRgb)[0];
               }

               float value15 = Math.abs(values2[0] - value14);
               value15 = Math.min(value15, 1.0F - value15);
               if (!(value15 > 0.13F)) {
                  double value16 = 0.67 + (1.0 - value12 / 78.0) * 0.16;
                  if (armorColorFallbackRepairChoice3 == null || value16 > armorColorFallbackRepairChoice3.quality) {
                     armorColorFallbackRepairChoice3 = new ArmorColorFallbackRepair.Choice(armorColorFallbackRepairColumn5.id, value16, "java_shader_marker");
                  }
               }
            }
         }
      }

      return armorColorFallbackRepairChoice3;
   }

   private static ArmorColorFallbackRepair.Choice matchAppearance(float[] values0, List<ArmorColorFallbackRepair.Column> entries1, String text2, boolean flag3) {
      if (values0 != null && entries1 != null) {
         ArmorColorFallbackRepair.Choice armorColorFallbackRepairChoice4 = null;

         for (ArmorColorFallbackRepair.Column armorColorFallbackRepairColumn6 : entries1) {
            if (armorColorFallbackRepairColumn6.id != 0) {
               float value7 = armorColorFallbackRepairColumn6.hue;
               float value8 = armorColorFallbackRepairColumn6.saturation;
               if (armorColorFallbackRepairColumn6.id == 1 && armorColorFallbackRepairColumn6.dyeRgb >= 0) {
                  float[] values9 = colorAppearance(armorColorFallbackRepairColumn6.dyeRgb);
                  value7 = values9[0];
                  value8 = values9[1];
               }

               float value14 = Math.abs(value7 - values0[0]);
               value14 = Math.min(value14, 1.0F - value14);
               double value10 = value14 * 2.5 + Math.abs(value8 - values0[1]) * 0.25;
               if (flag3 && armorColorFallbackRepairColumn6.id == 1 && values0[1] > 0.35) {
                  value10 += 0.15;
               }

               double value12 = Math.max(0.0, 1.0 - value10 / 0.7);
               if (armorColorFallbackRepairChoice4 == null || value12 > armorColorFallbackRepairChoice4.quality) {
                  armorColorFallbackRepairChoice4 = new ArmorColorFallbackRepair.Choice(armorColorFallbackRepairColumn6.id, value12, text2);
               }
            }
         }

         return armorColorFallbackRepairChoice4;
      } else {
         return null;
      }
   }

   private static float[] colorAppearance(int number0) {
      float[] values1 = Color.RGBtoHSB(number0 >> 16 & 0xFF, number0 >> 8 & 0xFF, number0 & 0xFF, null);
      return new float[]{values1[0], values1[1], 1.0F};
   }

   private static float[] imageAppearance(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         return null;
      }

      try {
         BufferedImage image1 = ImageIO.read(new ByteArrayInputStream(resourceBytes0));
         if (image1 == null) {
            return null;
         }

         double value2 = 0.0;
         double value4 = 0.0;
         double value6 = 0.0;
         double value8 = 0.0;
         double value10 = 0.0;

         for (int number12 = 0; number12 < image1.getHeight(); number12++) {
            for (int number13 = 0; number13 < image1.getWidth(); number13++) {
               int number14 = image1.getRGB(number13, number12);
               int number15 = number14 >>> 24;
               if (number15 >= 90) {
                  int number16 = number14 >> 16 & 0xFF;
                  int number17 = number14 >> 8 & 0xFF;
                  int number18 = number14 & 0xFF;
                  if (Math.max(number16, Math.max(number17, number18)) >= 25) {
                     float[] values19 = Color.RGBtoHSB(number16, number17, number18, null);
                     double value20 = number15 / 255.0 * values19[1] * Math.min(1.0, values19[2] * 2.0F);
                     value2 += Math.cos(values19[0] * 2.0F * Math.PI) * value20;
                     value4 += Math.sin(values19[0] * 2.0F * Math.PI) * value20;
                     value6 += value20;
                     value8 += values19[1] * (number15 / 255.0);
                     value10 += number15 / 255.0;
                  }
               }
            }
         }

         if (value10 == 0.0) {
            return null;
         }

         float value23 = value6 > 0.01 ? (float)(Math.atan2(value4, value2) / (Math.PI * 2)) : 0.0F;
         if (value23 < 0.0F) {
            value23++;
         }

         return new float[]{value23, (float)(value8 / value10), (float)(value6 / value10)};
      } catch (Exception error22) {
         return null;
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static int asInt(Object value0, int number1) {
      return value0 instanceof Number number2 ? number2.intValue() : number1;
   }

   private record Choice(int column, double quality, String evidence) {
   }

   private record Column(int id, int dyeRgb, float hue, float saturation) {
   }

   private record Item(String id, String model, String slot, String atlasHash, String path) {
   }
}
