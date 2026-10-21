package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.source.ResourceIndex;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class JavaItemTextureRepair {
   public static int apply(ResourceIndex resourceIndex0, List<?> entries1, Map<String, byte[]> packFiles2) {
      return apply(resourceIndex0, entries1, packFiles2, null);
   }

   public static int apply(ResourceIndex resourceIndex0, List<?> entries1, Map<String, byte[]> packFiles2, VanillaAssetCache vanillaAssetCache3) {
      byte[] resourceBytes4 = (byte[])packFiles2.get("twilight/geyser_item_mappings.json");
      if (resourceBytes4 == null) {
         return 0;
      }

      var jsonObject5 = obj(BedrockAnimationBridge.Json.read(resourceBytes4));
      var jsonObject6 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles2.get("textures/item_texture.json")));
      var jsonObject7 = obj(jsonObject6.get("texture_data"));
      TreeMap lookup8 = new TreeMap<>();
      ArrayList entries9 = new ArrayList<>();
      int number10 = 0;

      for (Object entry12 : list(obj(jsonObject5.get("items")).get("minecraft:bow"))) {
         obj(obj(entry12).get("bedrock_options")).put("display_handheld", true);
      }

      JavaModelResolver javaModelResolver40 = new JavaModelResolver(resourceIndex0, vanillaAssetCache3);

      for (Object entry13 : entries1) {
         if (entry13 instanceof ItemCandidate itemCandidate14 && BedrockArmorBridge.slot(itemCandidate14.baseItem()) != null) {
            String text15 = BedrockPackCompiler.bedrockIdentifier(itemCandidate14);
            String suffix16 = text15.substring(text15.indexOf(58) + 1);
            String texturePath17 = "textures/twilight/" + suffix16;
            if (packFiles2.containsKey(texturePath17 + ".png")) {
               try {
                  ResolvedJavaModel resolvedJavaModel18 = javaModelResolver40.resolveAll(itemCandidate14.visualModels(), true);
                  if (!resolvedJavaModel18.isThreeDimensional()) {
                     var value19 = tints(resourceIndex0, itemCandidate14);
                     ArrayList<BufferedImage> arrayListBufferedImage20 = new ArrayList<>();
                     ArrayList entries21 = new ArrayList<>();
                     ArrayList<Boolean> arrayListBoolean22 = new ArrayList<>();

                     for (int number23 = 0; number23 < 32; number23++) {
                        String text24 = resolvedJavaModel18.textures().get("layer" + number23);
                        if (text24 == null) {
                           if (number23 != 0) {
                              break;
                           }
                        } else {
                           arrayListBufferedImage20.add(rawFrame(resourceIndex0, text24, vanillaAssetCache3));
                           var jsonObject25 = number23 < value19.size() ? obj(value19.get(number23)) : Map.of();
                           String text26 = strip(str(jsonObject25.get("type")));
                           entries21.add(color(jsonObject25.get(text26.equals("constant") ? "value" : "default")));
                           arrayListBoolean22.add(text26.equals("dye"));
                        }
                     }

                     if (!arrayListBufferedImage20.isEmpty()) {
                        int number42 = arrayListBufferedImage20.stream().mapToInt(BufferedImage::getWidth).max().orElseThrow();
                        int number43 = arrayListBufferedImage20.stream().mapToInt(BufferedImage::getHeight).max().orElseThrow();
                        BufferedImage image44 = new BufferedImage(number42, number43, 2);
                        BufferedImage image45 = new BufferedImage(number42, number43, 2);
                        boolean flag27 = false;

                        for (int number28 = 0; number28 < number43; number28++) {
                           for (int number29 = 0; number29 < number42; number29++) {
                              int number30 = 0;
                              int number31 = 0;
                              byte number32 = 0;

                              for (int number33 = 0; number33 < arrayListBufferedImage20.size(); number33++) {
                                 BufferedImage image34 = (BufferedImage)arrayListBufferedImage20.get(number33);
                                 int number35 = image34.getRGB(number29 * image34.getWidth() / number42, number28 * image34.getHeight() / number43);
                                 int number36 = number35 >>> 24;
                                 if (number36 != 0) {
                                    int number37 = (Integer)entries21.get(number33);
                                    int number38 = BedrockArmorBridge.multiply(number35, number37 >>> 16 & 0xFF, number37 >>> 8 & 0xFF, number37 & 0xFF);
                                    number30 = over(number30, number38);
                                    if (number36 != 255 && (Boolean)arrayListBoolean22.get(number33)) {
                                       throw new IOException("Translucent dye layer needs a separate renderer: " + itemCandidate14.mappingModel());
                                    }

                                    number31 = over(number31, arrayListBoolean22.get(number33) ? number35 : number38);
                                    number32 = (byte)(arrayListBoolean22.get(number33) ? 255 : 3);
                                    if ((Boolean)arrayListBoolean22.get(number33)) {
                                       flag27 = true;
                                    }
                                 }
                              }

                              image44.setRGB(number29, number28, number30);
                              image45.setRGB(number29, number28, (number31 >>> 24 == 0 ? 0 : number32 << 24) | number31 & 16777215);
                           }
                        }

                        packFiles2.put(texturePath17 + ".png", BedrockArmorBridge.png(image44));
                        LinkedHashMap lookup46 = new LinkedHashMap<>();
                        lookup46.put("default_icon", "twilight." + suffix16);
                        String text47 = "twilight." + suffix16 + "_dyed";
                        packFiles2.put(texturePath17 + "_dyed.tga", BedrockArmorBridge.tga(image45));
                        jsonObject7.put(text47, Map.of("textures", texturePath17 + "_dyed"));
                        lookup46.put("dyed_icon", text47);
                        lookup8.put(text15, lookup46);
                        entries9.add(Map.of("identifier", text15, "model", itemCandidate14.mappingModel(), "layers", arrayListBufferedImage20.size(), "runtime_dye", flag27, "tints", value19));
                        number10++;
                     }
                  }
               } catch (IOException error39) {
                  throw new IllegalStateException("Java armor icon conversion failed: " + itemCandidate14.mappingModel(), error39);
               }
            }
         }
      }

      packFiles2.put("twilight/geyser_item_mappings.json", BedrockArmorBridge.json(jsonObject5));
      packFiles2.put("textures/item_texture.json", BedrockArmorBridge.json(jsonObject6));
      packFiles2.put("twilight/armor-icons.json", BedrockArmorBridge.json(Map.of("version", 517, "runtime_items", lookup8, "items", entries9)));
      return number10;
   }

   static BufferedImage rawFrame(ResourceIndex resourceIndex0, String text1, VanillaAssetCache vanillaAssetCache2) throws IOException {
      String text3 = JavaModelResolver.texturePath(TextureSet.spriteTexture(resourceIndex0, text1));
      var value4 = resourceIndex0.find(text3);
      byte[] resourceBytes5 = value4.isPresent() ? ((ResourceIndex.Asset)value4.get()).readBytes() : (vanillaAssetCache2 == null ? null : vanillaAssetCache2.readTexture(text3).orElse(null));
      if (resourceBytes5 == null) {
         throw new IOException("Missing armor icon layer: " + text3);
      }

      BufferedImage image6 = PngImages.decodeLenient(resourceBytes5);
      byte[] resourceBytes7 = resourceIndex0.find(text3 + ".mcmeta").isPresent() ? resourceIndex0.find(text3 + ".mcmeta").orElseThrow().readBytes() : null;
      if (resourceBytes7 == null && value4.isEmpty() && vanillaAssetCache2 != null) {
         resourceBytes7 = vanillaAssetCache2.readTexture(text3 + ".mcmeta").orElse(null);
      }

      if (resourceBytes7 == null) {
         return image6;
      }

      var jsonObject8 = obj(obj(BedrockAnimationBridge.Json.read(resourceBytes7)).get("animation"));
      if (jsonObject8.isEmpty()) {
         return image6;
      }

      int number9 = jsonObject8.get("width") instanceof Number number10 ? number10.intValue() : -1;
      int number15 = jsonObject8.get("height") instanceof Number number16 ? number16.intValue() : -1;
      if (number9 < 0 && number15 < 0) {
         number9 = number15 = Math.min(image6.getWidth(), image6.getHeight());
      } else {
         if (number9 < 0) {
            number9 = image6.getWidth();
         }

         if (number15 < 0) {
            number15 = image6.getHeight();
         }
      }

      if (number9 >= 1 && number15 >= 1 && image6.getWidth() % number9 == 0 && image6.getHeight() % number15 == 0) {
         var value17 = list(jsonObject8.get("frames"));
         Object value18 = value17.isEmpty() ? 0 : value17.getFirst();
         int number13 = value18 instanceof Number number14 ? number14.intValue() : ((Number)obj(value18).get("index")).intValue();
         int number19 = image6.getWidth() / number9;
         if (number13 >= 0 && number13 < number19 * (image6.getHeight() / number15)) {
            return image6.getSubimage(number13 % number19 * number9, number13 / number19 * number15, number9, number15);
         } else {
            throw new IOException("Invalid icon animation frame: " + text3);
         }
      } else {
         throw new IOException("Invalid icon animation size: " + text3);
      }
   }

   public static JsonObject requireEngine(JsonObject jsonObject0, Map<String, byte[]> packFiles1) {
      if (packFiles1.containsKey("twilight/armor-conversion.json")) {
         JsonArray jsonArray2 = new JsonArray();
         jsonArray2.add(1);
         jsonArray2.add(21);
         jsonArray2.add(30);
         jsonObject0.getAsJsonObject("header").add("min_engine_version", jsonArray2);
      }

      return jsonObject0;
   }

   static List<Object> tints(ResourceIndex resourceIndex0, ItemCandidate itemCandidate1) throws IOException {
      String text2 = itemCandidate1.baseItem();
      var value3 = resourceIndex0.find("assets/" + text2.substring(0, text2.indexOf(58)) + "/items/" + text2.substring(text2.indexOf(58) + 1) + ".json");
      if (value3.isEmpty()) {
         return List.of();
      }

      var jsonObject4 = obj(obj(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value3.get()).readBytes())).get("model"));
      if (strip(str(jsonObject4.get("type"))).equals("range_dispatch")
         && strip(str(jsonObject4.get("property"))).equals("custom_model_data")
         && itemCandidate1.customModelData().isPresent()) {
         int number5 = itemCandidate1.customModelData().getAsInt();
         double value6 = Double.NEGATIVE_INFINITY;
         var jsonObject8 = obj(jsonObject4.get("fallback"));

         for (Object value10 : list(jsonObject4.get("entries"))) {
            var jsonObject11 = obj(value10);
            if (jsonObject11.get("threshold") instanceof Number number13 && number13.doubleValue() <= number5 && number13.doubleValue() >= value6) {
               value6 = number13.doubleValue();
               jsonObject8 = obj(jsonObject11.get("model"));
            }
         }

         jsonObject4 = jsonObject8;
      }

      return findTints(jsonObject4, itemCandidate1.mappingModel(), 0);
   }

   static List<Object> findTints(Map<String, Object> mapping0, String text1, int number2) {
      if (number2 <= 32 && !mapping0.isEmpty()) {
         if (mapping0.get("model") instanceof String text3 && qualify(text3).equals(qualify(text1))) {
            return list(mapping0.get("tints"));
         } else {
            if (mapping0.containsKey("fallback")) {
               var jsonObject9 = findTints(obj(mapping0.get("fallback")), text1, number2 + 1);
               if (!jsonObject9.isEmpty()) {
                  return jsonObject9;
               }
            }

            for (String text11 : List.of("cases", "entries", "models")) {
               for (Object value6 : list(mapping0.get(text11))) {
                  var jsonObject7 = obj(value6);
                  var jsonObject8 = findTints(jsonObject7.get("model") instanceof Map ? obj(jsonObject7.get("model")) : jsonObject7, text1, number2 + 1);
                  if (!jsonObject8.isEmpty()) {
                     return jsonObject8;
                  }
               }
            }

            return List.of();
         }
      } else {
         return List.of();
      }
   }

   static String qualify(String text0) {
      return text0.contains(":") ? text0 : "minecraft:" + text0;
   }

   static int color(Object value0) {
      if (value0 instanceof Number number2) {
         return number2.intValue() & 16777215;
      } else {
         return value0 instanceof List entries1 && entries1.size() == 3
            ? (int)(((Number)entries1.get(0)).doubleValue() * 255.0) << 16
               | (int)(((Number)entries1.get(1)).doubleValue() * 255.0) << 8
               | (int)(((Number)entries1.get(2)).doubleValue() * 255.0)
            : 16777215;
      }
   }

   static int over(int number0, int number1) {
      int number2 = number1 >>> 24;
      if (number2 == 255) {
         return number1;
      }

      if (number2 == 0) {
         return number0;
      }

      int number3 = number0 >>> 24;
      int number4 = number2 + (number3 * (255 - number2) + 127) / 255;
      if (number4 == 0) {
         return 0;
      }

      int number5 = number4 << 24;

      for (byte number6 = 0; number6 <= 16; number6 += 8) {
         number5 |= ((number1 >>> number6 & 0xFF) * number2 + (number0 >>> number6 & 0xFF) * number3 * (255 - number2) / 255 + number4 / 2) / number4 << number6;
      }

      return number5;
   }

   static BufferedImage multiplyRgb(BufferedImage image0, int number1) {
      BufferedImage image2 = new BufferedImage(image0.getWidth(), image0.getHeight(), 2);

      for (int number3 = 0; number3 < image2.getHeight(); number3++) {
         for (int number4 = 0; number4 < image2.getWidth(); number4++) {
            image2.setRGB(number4, number3, BedrockArmorBridge.multiply(image0.getRGB(number4, number3), number1 >>> 16 & 0xFF, number1 >>> 8 & 0xFF, number1 & 0xFF));
         }
      }

      return image2;
   }

   static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : Map.of();
   }

   static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : List.of();
   }

   static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : "";
   }

   static String strip(String text0) {
      return text0.substring(text0.indexOf(58) + 1);
   }
}
