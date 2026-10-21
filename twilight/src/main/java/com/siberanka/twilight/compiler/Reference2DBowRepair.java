package com.siberanka.twilight.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class Reference2DBowRepair {
   private Reference2DBowRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      int number1 = 0;
      LinkedHashSet identifiers2 = new LinkedHashSet<>();
      Iterator iterator3 = new ArrayList<>(packFiles0.keySet()).iterator();

      while (true) {
         String text4;
         String text63;
         Map<String, Object> mapping6;
         String text8;
         String text9;
         Map<String, Object> mapping11;
         Map<String, Object> mapping12;
         BufferedImage image16;
         LinkedHashMap lookup17;
         LinkedHashMap lookup18;
         ArrayList entries59;
         String text66;
         Map<String, Object> mapping69;
         while (true) {
            Map<String, Object> mapping7;
            Map<String, Object> mapping10;
            while (true) {
               while (true) {
                  if (!iterator3.hasNext()) {
                     if (!identifiers2.isEmpty()) {
                        String text49 = "twilight/geyser_item_mappings.json";
                        byte[] resourceBytes50 = (byte[])packFiles0.get(text49);
                        if (resourceBytes50 != null) {
                           var jsonObject51 = obj(BedrockAnimationBridge.Json.read(resourceBytes50));
                           var itemDefinitions52 = list(obj(jsonObject51.get("items")).get("minecraft:bow"));
                           boolean flag53 = false;

                           for (Object entry56 : itemDefinitions52) {
                              mapping10 = obj(entry56);
                              if (identifiers2.contains(str(mapping10.get("bedrock_identifier")))) {
                                 mapping11 = obj(mapping10.get("bedrock_options"));
                                 if (mapping11.remove("display_handheld") != null) {
                                    flag53 = true;
                                 }
                              }
                           }

                           if (flag53) {
                              byte[] resourceBytes55 = BedrockAnimationBridge.Json.bytes(jsonObject51);
                              if (different(packFiles0, text49, resourceBytes55)) {
                                 packFiles0.put(text49, resourceBytes55);
                                 number1++;
                              }
                           }
                        }
                     }

                     return number1;
                  }

                  text4 = (String)iterator3.next();
                  if (text4.startsWith("attachables/") && text4.endsWith(".json") && !text4.endsWith(".player.json")) {
                     byte[] resourceBytes5 = (byte[])packFiles0.get(text4);
                     if (resourceBytes5 != null) {
                        try {
                           mapping6 = obj(BedrockAnimationBridge.Json.read(resourceBytes5));
                           break;
                        } catch (Exception error42) {
                        }
                     }
                  }
               }

               mapping7 = obj(obj(mapping6.get("minecraft:attachable")).get("description"));
               text8 = str(mapping7.get("identifier"));
               if (text8 != null && text8.startsWith("twilight:")) {
                  text9 = text8.substring(text8.indexOf(58) + 1);
                  if (text9.startsWith("minecraft_bow_")) {
                     mapping10 = obj(mapping7.get("geometry"));
                     mapping11 = obj(mapping7.get("textures"));
                     mapping12 = obj(mapping7.get("animations"));
                     var scripts13 = obj(mapping7.get("scripts"));
                     if (mapping10.size() >= 5
                        && mapping10.containsKey("state_3")
                        && mapping10.containsKey("state_0")
                        && scripts13.containsKey("pre_animation")
                        && scripts13.containsKey("animate")
                        && !packFiles0.containsKey("textures/twilight/" + text9 + "_java_anim_0.png")
                        && !packFiles0.containsKey("twilight/bow-ref-" + text9 + ".json")) {
                        String text14 = str(mapping11.get("default"));
                        if (text14 != null) {
                           byte[] resourceBytes15 = (byte[])packFiles0.get(text14 + ".png");
                           if (resourceBytes15 != null) {
                              try {
                                 image16 = ImageIO.read(new ByteArrayInputStream(resourceBytes15));
                                 break;
                              } catch (Exception error43) {
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (image16 != null && image16.getWidth() == 64 && image16.getHeight() == 16) {
               lookup17 = new LinkedHashMap<>();
               lookup18 = new LinkedHashMap<>();
               boolean flag19 = true;

               for (int number20 = 0; number20 < 4; number20++) {
                  String text21 = "state_" + number20;
                  String text22 = str(mapping10.get(text21));
                  if (text22 == null) {
                     flag19 = false;
                     break;
                  }

                  text66 = null;

                  for (String text25 : packFiles0.keySet()) {
                     if (text25.startsWith("models/entity/") && text25.endsWith(".geo.json") && text25.contains(text9 + "_state_" + number20 + ".geo.json")) {
                        text66 = text25;
                        break;
                     }
                  }

                  if (text66 == null) {
                     flag19 = false;
                     break;
                  }

                  try {
                     mapping69 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text66)));
                  } catch (Exception error44) {
                     flag19 = false;
                     break;
                  }

                  var value70 = list(mapping69.get("minecraft:geometry"));
                  if (value70.size() != 1) {
                     flag19 = false;
                     break;
                  }

                  var jsonObject26 = obj(value70.get(0));
                  if (!text22.equals(obj(jsonObject26.get("description")).get("identifier"))) {
                     flag19 = false;
                     break;
                  }

                  var value27 = list(jsonObject26.get("bones"));
                  if (value27.size() != 1) {
                     flag19 = false;
                     break;
                  }

                  var jsonObject28 = list(obj(value27.get(0)).get("cubes"));
                  if (jsonObject28.size() != 1) {
                     flag19 = false;
                     break;
                  }

                  var jsonObject29 = list(obj(jsonObject28.get(0)).get("size"));
                  if (jsonObject29.size() != 3 || number(jsonObject29.get(0)) < 12.0 || number(jsonObject29.get(1)) < 12.0 || number(jsonObject29.get(2)) > 0.1) {
                     flag19 = false;
                     break;
                  }

                  lookup17.put(number20, text66);
                  lookup18.put(number20, mapping69);
               }

               if (flag19) {
                  entries59 = new ArrayList<>();

                  for (int number60 = 0; number60 < 4; number60++) {
                     String animationName62 = "animations/" + text9 + "_state_" + number60 + ".animation.json";
                     if (!packFiles0.containsKey(animationName62)) {
                        flag19 = false;
                        break;
                     }

                     Map<String, Object> mapping64;
                     try {
                        mapping64 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(animationName62)));
                     } catch (Exception error45) {
                        flag19 = false;
                        break;
                     }

                     mapping69 = obj(mapping64.get("animations"));
                     if (mapping69.size() < 5) {
                        flag19 = false;
                        break;
                     }

                     for (String text73 : List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head")) {
                        if (!mapping69.containsKey(str(mapping12.get("state_" + number60 + "_" + text73)))) {
                           flag19 = false;
                           break;
                        }
                     }

                     if (!flag19) {
                        break;
                     }

                     entries59.add(animationName62);
                  }

                  if (flag19) {
                     var renderControllers61 = list(mapping7.get("render_controllers"));
                     if (renderControllers61.size() == 1 && renderControllers61.get(0) instanceof String) {
                        text63 = (String) renderControllers61.get(0);
                        text66 = "render_controllers/" + text9 + ".render_controllers.json";
                        if (packFiles0.containsKey(text66)) {
                           try {
                              mapping69 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text66)));
                              break;
                           } catch (Exception error46) {
                           }
                        }
                     }
                  }
               }
            }
         }

         var renderControllers72 = obj(obj(mapping69.get("render_controllers")).get(text63));
         if (!renderControllers72.isEmpty() && String.valueOf(renderControllers72.get("geometry")).contains("twilight_geometries")) {
            for (int number74 = 0; number74 < 4; number74++) {
               String text76 = "bow_stage_" + number74;
               String texturePath78 = "textures/twilight/bowstage_" + text9 + "_" + number74;
               BufferedImage image80 = image16.getSubimage(number74 * 16, 0, 16, 16);

               try (ByteArrayOutputStream pngBuffer30 = new ByteArrayOutputStream()) {
                  if (!ImageIO.write(image80, "PNG", pngBuffer30)) {
                     throw new IllegalStateException("PNG encoder unavailable");
                  }

                  byte[] resourceBytes31 = pngBuffer30.toByteArray();
                  if (different(packFiles0, texturePath78 + ".png", resourceBytes31)) {
                     packFiles0.put(texturePath78 + ".png", resourceBytes31);
                     number1++;
                  }
               } catch (Exception error48) {
                  throw new IllegalStateException("Cannot extract bow state " + number74, error48);
               }

               mapping11.put(text76, texturePath78);
               Map mapping82 = (Map)lookup18.get(number74);
               var jsonObject84 = obj(list(mapping82.get("minecraft:geometry")).get(0));
               var description32 = obj(jsonObject84.get("description"));
               description32.put("texture_width", 16);
               description32.put("texture_height", 16);
               description32.put("visible_bounds_width", 4);
               description32.put("visible_bounds_height", 4.5);
               description32.put("visible_bounds_offset", List.of(0, 0.75, 0));
               jsonObject84.put("bones", meshBones(text76, number74));
               mapping82.put("format_version", "1.16.0");
               byte[] resourceBytes33 = BedrockAnimationBridge.Json.bytes(mapping82);
               if (different(packFiles0, (String)lookup17.get(number74), resourceBytes33)) {
                  packFiles0.put((String)lookup17.get(number74), resourceBytes33);
                  number1++;
               }

               String text34 = (String)entries59.get(number74);
               var jsonObject35 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text34)));
               var animations36 = obj(jsonObject35.get("animations"));

               for (String text38 : List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head")) {
                  String text39 = str(mapping12.get("state_" + number74 + "_" + text38));
                  var jsonObject40 = obj(animations36.get(text39));
                  jsonObject40.put("bones", referencePose(text38, number74));
               }

               byte[] resourceBytes85 = BedrockAnimationBridge.Json.bytes(jsonObject35);
               if (different(packFiles0, text34, resourceBytes85)) {
                  packFiles0.put(text34, resourceBytes85);
                  number1++;
               }
            }

            var jsonObject75 = obj(renderControllers72.get("arrays"));
            var textures77 = obj(jsonObject75.get("textures"));
            textures77.put("Array.twilight_bow_stages", List.of("Texture.bow_stage_0", "Texture.bow_stage_1", "Texture.bow_stage_2", "Texture.bow_stage_3"));
            jsonObject75.put("textures", textures77);
            renderControllers72.put("textures", List.of("Array.twilight_bow_stages[v.twilight_model_index]", "Texture.enchanted"));
            byte[] resourceBytes79 = BedrockAnimationBridge.Json.bytes(mapping69);
            if (different(packFiles0, text66, resourceBytes79)) {
               packFiles0.put(text66, resourceBytes79);
               number1++;
            }

            byte[] resourceBytes81 = BedrockAnimationBridge.Json.bytes(mapping6);
            if (different(packFiles0, text4, resourceBytes81)) {
               packFiles0.put(text4, resourceBytes81);
               number1++;
            }

            var value83 = Map.of(
               "reference",
               "Vanilla Bedrock bow rightitem geometry and offsets",
               "states",
               4,
               "format",
               "rightitem_texture_mesh",
               "charge_index",
               "Twilight original, unchanged"
            );
            packFiles0.put("twilight/bow-ref-" + text9 + ".json", BedrockAnimationBridge.Json.bytes(value83));
            number1++;
            identifiers2.add(text8);
         }
      }
   }

   private static List<Object> meshBones(String text0, int number1) {
      LinkedHashMap lookup2 = new LinkedHashMap<>();
      lookup2.put("name", "rightitem");
      LinkedHashMap lookup3 = new LinkedHashMap<>();
      lookup3.put("local_pivot", List.of(6.0, 0.0, 6.0));
      lookup3.put("position", List.of(number1 == 0 ? 2.0 : (number1 == 1 ? 2.0 : 2.01), 1.0, number1 == 0 ? -2.0 : -1.0));
      lookup3.put("rotation", List.of(0.0, -135.0, 90.0));
      lookup3.put("texture", text0);
      lookup2.put("texture_meshes", List.of(lookup3));
      return List.of(lookup2);
   }

   private static Map<String, Object> referencePose(String text0, int number1) {
      LinkedHashMap lookup2 = new LinkedHashMap<>();
      switch (text0) {
         case "first_person_right":
         case "first_person_left":
            if (number1 == 0) {
               lookup2.put("position", List.of(-5.5, -3.0, -3.0));
               lookup2.put("rotation", List.of(38.0, -120.0, -63.0));
            } else {
               lookup2.put("position", List.of(-1.5, 2.5, -4.8));
               lookup2.put("rotation", List.of(-53.0, 8.0, 35.0));
            }
            break;
         case "third_person_right":
         case "third_person_left":
         case "head":
            lookup2.put("position", List.of(0.5, -2.5, 1.0));
            lookup2.put("rotation", List.of(0.0, 0.0, 0.0));
      }

      return Map.of("rightitem", lookup2);
   }

   private static boolean different(Map<String, byte[]> packFiles0, String text1, byte[] resourceBytes2) {
      return !Arrays.equals((byte[])packFiles0.get(text1), resourceBytes2);
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static double number(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : Double.NaN;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }
}
