package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.imageio.ImageIO;

public final class DynamicWeaponTextureAnimationBridge {
   private static final int MAX_PERIOD = 240;
   private static final int MAX_ANIMATED_AREA = 4194304;

   private static String worldFrameIndex(int number0) {
      return "Array.twilight_dynamic_frames[math.mod(math.floor(q.time_stamp), " + number0 + ")]";
   }

   private static int replaceLocalTextureClock(Map<String, Object> mapping0, String text1) {
      if (mapping0.get("textures") instanceof List entries3 && !entries3.isEmpty() && entries3.get(0) instanceof String text4) {
         String text13 = text1 + "[math.mod(math.floor(q.anim_time * 20), ";
         if (text4.startsWith(text13) && text4.endsWith(")]")) {
            int number6;
            try {
               number6 = Integer.parseInt(text4.substring(text13.length(), text4.length() - 2));
            } catch (NumberFormatException error12) {
               return 0;
            }

            if (number6 >= 2 && number6 <= 240) {
               var value7 = map(mapping0.get("arrays"));
               var textures8 = value7 == null ? null : map(value7.get("textures"));
               if ((textures8 == null ? null : textures8.get(text1)) instanceof List entries10 && entries10.size() == number6) {
                  var value11 = new ArrayList<>(entries3);
                  value11.set(0, text1 + "[math.mod(math.floor(q.time_stamp), " + number6 + ")]");
                  mapping0.put("textures", value11);
                  return 1;
               } else {
                  return 0;
               }
            } else {
               return 0;
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static int updatePreviousVersionClock(Map<String, Object> mapping0) {
      return replaceLocalTextureClock(mapping0, "Array.twilight_dynamic_frames");
   }

   private static int updateChargedVariantClock(String text0, Map<String, byte[]> packFiles1) {
      String animationName2 = "render_controllers/" + text0 + "_java_animation.json";
      byte[] resourceBytes3 = (byte[])packFiles1.get(animationName2);
      if (resourceBytes3 == null) {
         return 0;
      }

      try {
         var parsedJson4 = map(BedrockAnimationBridge.Json.read(resourceBytes3));
         var renderControllers5 = parsedJson4 == null ? null : map(parsedJson4.get("render_controllers"));
         if (renderControllers5 != null && renderControllers5.size() == 1) {
            var value6 = map(renderControllers5.values().iterator().next());
            if (value6 != null && replaceLocalTextureClock(value6, "Array.twilight_java_frames") != 0) {
               packFiles1.put(animationName2, BedrockAnimationBridge.Json.bytes(parsedJson4));
               return 1;
            } else {
               return 0;
            }
         } else {
            return 0;
         }
      } catch (Exception error7) {
         return 0;
      }
   }

   private DynamicWeaponTextureAnimationBridge() {
   }

   public static int install(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      int number3 = 0;
      JavaModelResolver javaModelResolver4 = new JavaModelResolver(resourceIndex0);

      for (ItemCandidate itemCandidate6 : entries1) {
         if ("minecraft:crossbow".equals(itemCandidate6.baseItem()) || "minecraft:bow".equals(itemCandidate6.baseItem())) {
            String text7 = BedrockPackCompiler.bedrockIdentifier(itemCandidate6);
            String suffix8 = text7.substring(text7.indexOf(58) + 1);
            number3 += updateChargedVariantClock(suffix8, packFiles2);
            if (itemCandidate6.nativeStateModels().size() >= 4) {
               String texturePath9 = "textures/twilight/" + suffix8 + ".png";
               String attachablePath10 = "attachables/" + suffix8 + ".json";
               String controllerPath11 = "render_controllers/" + suffix8 + ".render_controllers.json";
               if (packFiles2.containsKey(texturePath9) && packFiles2.containsKey(attachablePath10) && packFiles2.containsKey(controllerPath11)) {
                  try {
                     var parsedJson12 = map(BedrockAnimationBridge.Json.read((byte[])packFiles2.get(attachablePath10)));
                     var attachable13 = map(map(parsedJson12.get("minecraft:attachable")).get("description"));
                     var geometry14 = map(attachable13.get("geometry"));
                     if (geometry14 != null && geometry14.containsKey("state_3")) {
                        LinkedHashSet<String> identifiers15 = new LinkedHashSet<>();

                        for (String text17 : itemCandidate6.nativeStateModels().subList(0, 4)) {
                           identifiers15.addAll(usedTextures(javaModelResolver4.resolve(text17)));
                        }

                        TextureSet identifiers39 = TextureSet.atlas(resourceIndex0, List.copyOf(identifiers15));
                        BufferedImage image40 = ImageIO.read(new ByteArrayInputStream((byte[])packFiles2.get(texturePath9)));
                        BufferedImage image18 = ImageIO.read(new ByteArrayInputStream(identifiers39.png()));
                        if (image40 != null && image18 != null && pixelsEqual(image40, image18) && (long)image40.getWidth() * image40.getHeight() <= 4194304L) {
                           LinkedHashMap<TextureSet.Region, Frames> lookup19 = new LinkedHashMap<>();
                           int number20 = 1;

                           for (String text22 : identifiers15) {
                              DynamicWeaponTextureAnimationBridge.Frames dynamicWeaponTextureAnimationBridgeFrames23 = load(resourceIndex0, text22);
                              if (dynamicWeaponTextureAnimationBridgeFrames23 != null && dynamicWeaponTextureAnimationBridgeFrames23.images.size() >= 2) {
                                 TextureSet.Region textureSetRegion24 = identifiers39.region(text22);
                                 if (textureSetRegion24.width() == dynamicWeaponTextureAnimationBridgeFrames23.width && textureSetRegion24.height() == dynamicWeaponTextureAnimationBridgeFrames23.height && pixelRegionMatches(image40, textureSetRegion24, dynamicWeaponTextureAnimationBridgeFrames23.images.get(0))) {
                                    lookup19.put(textureSetRegion24, dynamicWeaponTextureAnimationBridgeFrames23);
                                    number20 = lcm(number20, dynamicWeaponTextureAnimationBridgeFrames23.duration);
                                    if (number20 > 240) {
                                       lookup19.clear();
                                       break;
                                    }
                                 }
                              }
                           }

                           if (!lookup19.isEmpty() && number20 >= 2) {
                              var textures41 = map(attachable13.get("textures"));
                              if (textures41 != null) {
                                 var parsedJson42 = map(BedrockAnimationBridge.Json.read((byte[])packFiles2.get(controllerPath11)));
                                 var renderControllers43 = map(parsedJson42.get("render_controllers"));
                                 if (renderControllers43 != null && renderControllers43.size() == 1) {
                                    var value44 = map(renderControllers43.values().iterator().next());
                                    if (value44 != null && value44.get("textures") instanceof List entries25 && !entries25.isEmpty()) {
                                       if (updatePreviousVersionClock(value44) != 0) {
                                          packFiles2.put(controllerPath11, BedrockAnimationBridge.Json.bytes(parsedJson42));
                                          number3++;
                                       } else if ("Texture.default".equals(entries25.get(0))) {
                                          ArrayList entries45 = new ArrayList<>();
                                          HashMap lookup27 = new HashMap<>();
                                          int number28 = 0;

                                          for (int number29 = 0; number29 < number20; number29++) {
                                             BufferedImage image30 = new BufferedImage(image40.getWidth(), image40.getHeight(), 2);
                                             Graphics2D graphicsD31 = image30.createGraphics();
                                             graphicsD31.drawImage(image40, 0, 0, null);

                                             for (var value33 : lookup19.entrySet()) {
                                                TextureSet.Region textureSetRegion34 = (TextureSet.Region)value33.getKey();
                                                DynamicWeaponTextureAnimationBridge.Frames dynamicWeaponTextureAnimationBridgeFrames35 = (DynamicWeaponTextureAnimationBridge.Frames)value33.getValue();
                                                int number36 = number29 % dynamicWeaponTextureAnimationBridgeFrames35.duration;
                                                int number37 = dynamicWeaponTextureAnimationBridgeFrames35.timeline[number36];
                                                graphicsD31.drawImage(dynamicWeaponTextureAnimationBridgeFrames35.images.get(number37), textureSetRegion34.x(), textureSetRegion34.y(), null);
                                             }

                                             graphicsD31.dispose();
                                             byte[] resourceBytes49 = TextureSet.png(image30);
                                             String text50 = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(resourceBytes49));
                                             String text51 = (String)lookup27.get(text50);
                                             if (text51 == null) {
                                                text51 = "java_anim_" + number28++;
                                                lookup27.put(text50, text51);
                                                textures41.put(text51, "textures/twilight/" + suffix8 + "_" + text51);
                                                packFiles2.put("textures/twilight/" + suffix8 + "_" + text51 + ".png", resourceBytes49);
                                             }

                                             entries45.add("Texture." + text51);
                                          }

                                          if (number28 >= 2) {
                                             var value46 = map(value44.get("arrays"));
                                             if (value46 == null) {
                                                value46 = new LinkedHashMap<>();
                                                value44.put("arrays", value46);
                                             }

                                             var textures47 = map(value46.get("textures"));
                                             if (textures47 == null) {
                                                textures47 = new LinkedHashMap<>();
                                                value46.put("textures", textures47);
                                             }

                                             textures47.put("Array.twilight_dynamic_frames", entries45);
                                             var value48 = new ArrayList<>(entries25);
                                             value48.set(0, worldFrameIndex(number20));
                                             value44.put("textures", value48);
                                             packFiles2.put(attachablePath10, BedrockAnimationBridge.Json.bytes(parsedJson12));
                                             packFiles2.put(controllerPath11, BedrockAnimationBridge.Json.bytes(parsedJson42));
                                             number3++;
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  } catch (Exception error38) {
                  }
               }
            }
         }
      }

      return number3;
   }

   private static Set<String> usedTextures(ResolvedJavaModel resolvedJavaModel0) throws IOException {
      LinkedHashSet identifiers1 = new LinkedHashSet<>();
      if (!resolvedJavaModel0.parts().isEmpty()) {
         for (ResolvedJavaModel resolvedJavaModel14 : resolvedJavaModel0.parts()) {
            identifiers1.addAll(usedTextures(resolvedJavaModel14));
         }

         return identifiers1;
      } else {
         if (BedrockAnimationBridge.Json.read(BedrockPackCompiler.geometryElements(resolvedJavaModel0).toString().getBytes(StandardCharsets.UTF_8)) instanceof List entries3) {
            for (Object value5 : entries3) {
               var value6 = map(value5);
               if (value6 != null) {
                  var value7 = map(value6.get("faces"));
                  if (value7 != null) {
                     for (Object value9 : value7.values()) {
                        var value10 = map(value9);
                        if (value10 != null && value10.get("texture") instanceof String text12) {
                           if (text12.startsWith("#")) {
                              identifiers1.add(resolvedJavaModel0.textures().getOrDefault(text12.substring(1), "minecraft:missingno"));
                           } else {
                              identifiers1.add(JavaModelResolver.qualified(text12, "minecraft"));
                           }
                        }
                     }
                  }
               }
            }
         }

         if (identifiers1.isEmpty()) {
            identifiers1.addAll(BedrockPackCompiler.flatLayers(resolvedJavaModel0));
         }

         return identifiers1;
      }
   }

   private static DynamicWeaponTextureAnimationBridge.Frames load(ResourceIndex resourceIndex0, String text1) throws Exception {
      String text2 = JavaModelResolver.texturePath(TextureSet.spriteTexture(resourceIndex0, text1));
      byte[] resourceBytes3 = read(resourceIndex0, text2);
      byte[] resourceBytes4 = read(resourceIndex0, text2 + ".mcmeta");
      if (resourceBytes3 != null && resourceBytes4 != null) {
         BufferedImage image5 = ImageIO.read(new ByteArrayInputStream(resourceBytes3));
         if (image5 != null && image5.getWidth() > 0 && image5.getHeight() >= 2 * image5.getWidth() && image5.getHeight() % image5.getWidth() == 0) {
            var parsedJson6 = map(BedrockAnimationBridge.Json.read(resourceBytes4));
            var value7 = parsedJson6 == null ? null : map(parsedJson6.get("animation"));
            if (value7 == null) {
               return null;
            }

            int number8 = image5.getWidth();
            int number9 = image5.getHeight() / number8;
            if (number9 > 120) {
               return null;
            }

            int number10 = num(value7.get("frametime"), 1);
            if (number10 >= 1 && number10 <= 1000) {
               ArrayList entries11 = new ArrayList<>();
               ArrayList<Integer> arrayListInteger12 = new ArrayList<>();
               Object value14 = value7.get("frames");
               if (value14 instanceof List) {
                  for (Object value15 : (List)value14) {
                     if (value15 instanceof Number number16) {
                        entries11.add(number16.intValue());
                        arrayListInteger12.add(number10);
                     } else {
                        var value17 = map(value15);
                        if (value17 == null) {
                           return null;
                        }

                        entries11.add(num(value17.get("index"), -1));
                        arrayListInteger12.add(num(value17.get("time"), number10));
                     }
                  }
               } else {
                  for (int number20 = 0; number20 < number9; number20++) {
                     entries11.add(number20);
                     arrayListInteger12.add(number10);
                  }
               }

               if (entries11.isEmpty()) {
                  return null;
               }

               int number18 = 0;

               for (int number21 = 0; number21 < entries11.size(); number21++) {
                  if ((Integer)entries11.get(number21) < 0 || (Integer)entries11.get(number21) >= number9 || (Integer)arrayListInteger12.get(number21) < 1 || (Integer)arrayListInteger12.get(number21) > 240) {
                     return null;
                  }

                  number18 += arrayListInteger12.get(number21);
                  if (number18 > 240) {
                     return null;
                  }
               }

               int[] values22 = new int[number18];
               int number23 = 0;

               for (int number24 = 0; number24 < entries11.size(); number24++) {
                  for (int number26 = 0; number26 < arrayListInteger12.get(number24); number26++) {
                     values22[number23++] = (Integer)entries11.get(number24);
                  }
               }

               ArrayList entries25 = new ArrayList<>();

               for (int number27 = 0; number27 < number9; number27++) {
                  entries25.add(image5.getSubimage(0, number27 * number8, number8, number8));
               }

               return new DynamicWeaponTextureAnimationBridge.Frames(number8, number8, entries25, values22, number18);
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static byte[] read(ResourceIndex resourceIndex0, String text1) {
      try {
         return resourceIndex0.find(text1).isPresent() ? resourceIndex0.find(text1).get().readBytes() : null;
      } catch (Exception error3) {
         return null;
      }
   }

   private static boolean pixelRegionMatches(BufferedImage image0, TextureSet.Region textureSetRegion1, BufferedImage image2) {
      if (textureSetRegion1.x() + textureSetRegion1.width() <= image0.getWidth() && textureSetRegion1.y() + textureSetRegion1.height() <= image0.getHeight()) {
         for (int number3 = 0; number3 < textureSetRegion1.height(); number3++) {
            for (int number4 = 0; number4 < textureSetRegion1.width(); number4++) {
               if (image0.getRGB(number4 + textureSetRegion1.x(), number3 + textureSetRegion1.y()) != image2.getRGB(number4, number3)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean pixelsEqual(BufferedImage image0, BufferedImage image1) {
      if (image0.getWidth() == image1.getWidth() && image0.getHeight() == image1.getHeight()) {
         for (int number2 = 0; number2 < image0.getHeight(); number2++) {
            for (int number3 = 0; number3 < image0.getWidth(); number3++) {
               if (image0.getRGB(number3, number2) != image1.getRGB(number3, number2)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static int lcm(int number0, int number1) {
      return number0 / gcd(number0, number1) * number1;
   }

   private static int gcd(int number0, int number1) {
      while (number1 != 0) {
         int number2 = number0 % number1;
         number0 = number1;
         number1 = number2;
      }

      return number0;
   }

   private static Map<String, Object> map(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static int num(Object value0, int number1) {
      return value0 instanceof Number number2 ? number2.intValue() : number1;
   }

   private record Frames(int width, int height, List<BufferedImage> images, int[] timeline, int duration) {
   }
}
