package com.siberanka.twilight.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public final class AnimatedAtlasVisibilityRepair {
   private static final String REPORT = "twilight/animated-uv-visibility-repair.json";
   private static final String ENTITY = "entity/twilight_display.entity.json";
   private static final String DISPLAY_RC = "render_controllers/twilight_display.json";
   private static final String INDEX = "twilight/display-index.json";
   private static final String DISPLAY_ID = "controller.render.twilight.display";
   private static final String APPEARANCE = "math.floor(q.property('twilight:appearance')/9)";
   private static final Pattern HELD_EXPR = Pattern.compile(
      "^Array\\.twilight_java_frames\\[math\\.mod\\(math\\.floor\\(q\\.anim_time \\* 20\\), (\\d+)\\)\\]$"
   );
   private static final Pattern FRAME = Pattern.compile("Texture\\.java_anim_(\\d+)");

   private AnimatedAtlasVisibilityRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.containsKey("twilight/animated-uv-visibility-repair.json")) {
         byte[] resourceBytes1 = (byte[])packFiles0.get("entity/twilight_display.entity.json");
         byte[] resourceBytes2 = (byte[])packFiles0.get("render_controllers/twilight_display.json");
         byte[] resourceBytes3 = (byte[])packFiles0.get("twilight/display-index.json");
         if (resourceBytes1 != null && resourceBytes2 != null && resourceBytes3 != null) {
            Map<String, Object> mapping4;
            Map<String, Object> mapping5;
            Map<String, Object> mapping6;
            try {
               mapping4 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
               mapping5 = obj(BedrockAnimationBridge.Json.read(resourceBytes2));
               mapping6 = obj(BedrockAnimationBridge.Json.read(resourceBytes3));
            } catch (RuntimeException error36) {
               return 0;
            }

            var description7 = obj(obj(mapping4.get("minecraft:client_entity")).get("description"));
            if (!"twilight:item_display".equals(description7.get("identifier"))) {
               return 0;
            }

            var renderControllers8 = list(description7.get("render_controllers"));
            if (renderControllers8.size() == 1 && "controller.render.twilight.display".equals(renderControllers8.get(0))) {
               var renderControllers9 = obj(obj(mapping5.get("render_controllers")).get("controller.render.twilight.display"));
               var textures10 = list(obj(obj(renderControllers9.get("arrays")).get("textures")).get("Array.textures"));
               var textures11 = list(renderControllers9.get("textures"));
               if (textures11.size() == 1 && "Array.textures[math.floor(q.property('twilight:appearance')/9)]".equals(textures11.get(0)) && !textures10.isEmpty()) {
                  var textures12 = obj(description7.get("textures"));
                  var geometry13 = obj(description7.get("geometry"));
                  var animationScripts14 = list(obj(description7.get("scripts")).get("pre_animation"));
                  if (animationScripts14.isEmpty()) {
                     return 0;
                  }

                  ArrayList<Candidate> arrayListCandidate15 = new ArrayList<>();
                  int number16 = 0;
                  int number17 = 0;

                  for (String text19 : new TreeSet<>(packFiles0.keySet())) {
                     if (text19.startsWith("render_controllers/") && text19.endsWith("_java_animation.json")) {
                        number16++;
                        AnimatedAtlasVisibilityRepair.Candidate animatedAtlasVisibilityRepairCandidate20 = inspect(packFiles0, text19, mapping6, textures12, geometry13, textures10.size());
                        if (animatedAtlasVisibilityRepairCandidate20 != null) {
                           arrayListCandidate15.add(animatedAtlasVisibilityRepairCandidate20);
                           number17++;
                        }
                     }
                  }

                  if (arrayListCandidate15.isEmpty()) {
                     return 0;
                  }

                  var value37 = new ArrayList<>(textures10);
                  var value38 = new LinkedHashMap<>(textures12);
                  LinkedHashMap lookup39 = new LinkedHashMap<>();
                  LinkedHashMap lookup21 = new LinkedHashMap<>();
                  LinkedHashMap lookup22 = new LinkedHashMap<>();
                  var value23 = new ArrayList<>(animationScripts14);
                  value23.add("variable.twilight_uv_frame = math.floor(q.property('twilight:appearance')/9);");
                  ArrayList entries24 = new ArrayList<>();

                  for (AnimatedAtlasVisibilityRepair.Candidate animatedAtlasVisibilityRepairCandidate26 : arrayListCandidate15) {
                     var attachable27 = obj(obj(animatedAtlasVisibilityRepairCandidate26.attach.get("minecraft:attachable")).get("description"));
                     var textures28 = obj(attachable27.get("textures"));

                     for (int number29 = 0; number29 < animatedAtlasVisibilityRepairCandidate26.frames.size(); number29++) {
                        String text30 = "java_anim_" + number29;
                        String texturePath31 = "textures/twilight/a/" + animatedAtlasVisibilityRepairCandidate26.sid + "_" + number29;
                        if (packFiles0.containsKey(texturePath31 + ".png")) {
                           return 0;
                        }

                        lookup21.put(texturePath31 + ".png", png(rebake(animatedAtlasVisibilityRepairCandidate26.frames.get(number29), animatedAtlasVisibilityRepairCandidate26.source, animatedAtlasVisibilityRepairCandidate26.destination)));
                        textures28.put(text30, texturePath31);
                     }

                     lookup22.put(animatedAtlasVisibilityRepairCandidate26.attachPath, BedrockAnimationBridge.Json.bytes(animatedAtlasVisibilityRepairCandidate26.attach));
                     var textures42 = list(animatedAtlasVisibilityRepairCandidate26.heldDef.get("textures"));
                     textures42.set(0, ((String)textures42.get(0)).replace("q.anim_time", "q.life_time"));
                     lookup39.put(animatedAtlasVisibilityRepairCandidate26.rcPath, BedrockAnimationBridge.Json.bytes(animatedAtlasVisibilityRepairCandidate26.heldRc));
                     int entryCount43 = value37.size();

                     for (Object entry32 : animatedAtlasVisibilityRepairCandidate26.sequence) {
                        Matcher matcher33 = FRAME.matcher((String)entry32);
                        if (!matcher33.matches()) {
                           throw new IllegalStateException("invalid sequence");
                        }

                        int number34 = Integer.parseInt(matcher33.group(1));
                        String text35 = "twilight_uv_" + animatedAtlasVisibilityRepairCandidate26.index + "_" + number34;
                        value38.put(text35, "textures/twilight/a/" + animatedAtlasVisibilityRepairCandidate26.sid + "_" + number34);
                        value37.add("Texture." + text35);
                     }

                     value23.add(
                        "variable.twilight_uv_frame = (math.floor(q.property('twilight:appearance')/9) == "
                           + animatedAtlasVisibilityRepairCandidate26.index
                           + ") ? ("
                           + entryCount43
                           + " + math.mod(math.floor(q.life_time * 20), "
                           + animatedAtlasVisibilityRepairCandidate26.sequence.size()
                           + ")) : variable.twilight_uv_frame;"
                     );
                     LinkedHashMap lookup45 = new LinkedHashMap<>();
                     lookup45.put("id", animatedAtlasVisibilityRepairCandidate26.id);
                     lookup45.put("display_index", animatedAtlasVisibilityRepairCandidate26.index);
                     lookup45.put("source_uv", animatedAtlasVisibilityRepairCandidate26.source.toString());
                     lookup45.put("target_uv", animatedAtlasVisibilityRepairCandidate26.destination.toString());
                     lookup45.put("unique_frames", animatedAtlasVisibilityRepairCandidate26.frames.size());
                     lookup45.put("frame_ticks", animatedAtlasVisibilityRepairCandidate26.sequence.size());
                     lookup45.put("changing_pixels", animatedAtlasVisibilityRepairCandidate26.changed);
                     lookup45.put("changing_pixels_covered", animatedAtlasVisibilityRepairCandidate26.covered);
                     entries24.add(lookup45);
                  }

                  var scripts40 = obj(description7.get("scripts"));
                  scripts40.put("pre_animation", value23);
                  description7.put("textures", value38);
                  obj(obj(renderControllers9.get("arrays")).get("textures")).put("Array.textures", value37);
                  textures11.set(0, "Array.textures[variable.twilight_uv_frame]");
                  packFiles0.putAll(lookup21);
                  packFiles0.putAll(lookup22);
                  packFiles0.putAll(lookup39);
                  packFiles0.put("entity/twilight_display.entity.json", BedrockAnimationBridge.Json.bytes(mapping4));
                  packFiles0.put("render_controllers/twilight_display.json", BedrockAnimationBridge.Json.bytes(mapping5));
                  LinkedHashMap lookup41 = new LinkedHashMap<>();
                  lookup41.put("algorithm", "visible_uv_pixels_v1");
                  lookup41.put("scanned_animated_models", number16);
                  lookup41.put("models_repaired", number17);
                  lookup41.put("minimum_invisible_fraction", 0.95);
                  lookup41.put("models", entries24);
                  lookup41.put("other_animations_changed", false);
                  packFiles0.put("twilight/animated-uv-visibility-repair.json", BedrockAnimationBridge.Json.bytes(lookup41));
                  return lookup21.size() + lookup22.size() + lookup39.size() + 3;
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

   private static AnimatedAtlasVisibilityRepair.Candidate inspect(
      Map<String, byte[]> packFiles0, String text1, Map<String, Object> mapping2, Map<String, Object> mapping3, Map<String, Object> mapping4, int number5
   ) {
      String animationName6 = text1.substring("render_controllers/".length(), text1.length() - "_java_animation.json".length());
      String text7 = "twilight:" + animationName6;
      if (!(mapping2.get(text7) instanceof Number number9)) {
         return null;
      } else {
         int number10 = number9.intValue();
         if (number10 >= 0 && number10 < number5 && mapping3.containsKey("v" + number10) && mapping4.containsKey("v" + number10)) {
            String attachablePath11 = "attachables/" + animationName6 + ".json";
            String geometryName12 = "models/entity/geometry." + animationName6 + ".geo.json";
            if (packFiles0.containsKey(attachablePath11) && packFiles0.containsKey(geometryName12)) {
               try {
                  var jsonObject13 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(attachablePath11)));
                  var attachable14 = obj(obj(jsonObject13.get("minecraft:attachable")).get("description"));
                  if (!text7.equals(attachable14.get("identifier"))) {
                     return null;
                  }

                  String geometryName15 = (String)obj(attachable14.get("geometry")).get("default");
                  var jsonObject16 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(geometryName12)));
                  var value17 = list(jsonObject16.get("minecraft:geometry"));
                  if (value17.size() != 1) {
                     return null;
                  }

                  var jsonObject18 = obj(value17.get(0));
                  var description19 = obj(jsonObject18.get("description"));
                  if (!Objects.equals(geometryName15, description19.get("identifier"))) {
                     return null;
                  }

                  String modelName20 = "models/entity/display." + animationName6 + ".geo.json";
                  if (!packFiles0.containsKey(modelName20)) {
                     return null;
                  }

                  var jsonObject21 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(modelName20)));
                  var value22 = list(jsonObject21.get("minecraft:geometry"));
                  if (value22.size() != 1) {
                     return null;
                  }

                  var jsonObject23 = obj(value22.get(0));
                  if (!Objects.equals(mapping4.get("v" + number10), obj(jsonObject23.get("description")).get("identifier"))) {
                     return null;
                  }

                  var jsonObject24 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get(text1)));
                  var renderControllers25 = obj(jsonObject24.get("render_controllers"));
                  if (renderControllers25.size() != 1) {
                     return null;
                  }

                  var jsonObject26 = obj(renderControllers25.values().iterator().next());
                  var textures27 = list(jsonObject26.get("textures"));
                  if (textures27.size() == 1 && textures27.get(0) instanceof String text28) {
                     Matcher matcher61 = HELD_EXPR.matcher(text28);
                     if (!matcher61.matches()) {
                        return null;
                     }

                     var textures30 = list(obj(obj(jsonObject26.get("arrays")).get("textures")).get("Array.twilight_java_frames"));
                     if (textures30.size() == Integer.parseInt(matcher61.group(1)) && textures30.size() >= 2 && textures30.size() <= 5000) {
                        var textures31 = obj(attachable14.get("textures"));
                        TreeMap<Integer, BufferedImage> lookup32 = new TreeMap<>();

                        for (Object value34 : textures30) {
                           if (value34 instanceof String text35) {
                              Matcher matcher36 = FRAME.matcher(text35);
                              if (!matcher36.matches()) {
                                 return null;
                              }

                              int number37 = Integer.parseInt(matcher36.group(1));
                              if (number37 > 256) {
                                 return null;
                              }

                              if (lookup32.containsKey(number37)) {
                                 continue;
                              }

                              String text38 = (String)textures31.get("java_anim_" + number37);
                              if (text38 != null && packFiles0.containsKey(text38 + ".png")) {
                                 BufferedImage image39 = ImageIO.read(new ByteArrayInputStream((byte[])packFiles0.get(text38 + ".png")));
                                 if (image39 != null && image39.getWidth() <= 2048 && image39.getHeight() <= 2048) {
                                    if (((Number)description19.get("texture_width")).intValue() == image39.getWidth()
                                       && ((Number)description19.get("texture_height")).intValue() == image39.getHeight()) {
                                       lookup32.put(number37, image39);
                                       continue;
                                    }

                                    return null;
                                 }

                                 return null;
                              }

                              return null;
                           }

                           return null;
                        }

                        if (lookup32.size() < 2) {
                           return null;
                        }

                        int number62 = (Integer)lookup32.lastKey();
                        if (number62 != lookup32.size() - 1) {
                           return null;
                        }

                        var value63 = new ArrayList<>(lookup32.values());
                        int number64 = ((BufferedImage)value63.get(0)).getWidth();
                        int number65 = ((BufferedImage)value63.get(0)).getHeight();

                        for (BufferedImage image68 : value63) {
                           if (image68.getWidth() != number64 || image68.getHeight() != number65) {
                              return null;
                           }
                        }

                        boolean[] values67 = new boolean[number64 * number65];
                        HashSet<Rect> identifiers69 = new HashSet<>();

                        for (Object value40 : list(jsonObject18.get("bones"))) {
                           for (Object value42 : list(obj(value40).get("cubes"))) {
                              Object value43 = obj(value42).get("uv");
                              if (value43 instanceof Map) {
                                 for (Object value45 : obj(value43).values()) {
                                    var jsonObject46 = obj(value45);
                                    var value47 = list(jsonObject46.get("uv"));
                                    var value48 = list(jsonObject46.get("uv_size"));
                                    if (value47.size() == 2 && value48.size() == 2) {
                                       double value49 = number(value47.get(0));
                                       double value51 = number(value47.get(1));
                                       double value53 = value49 + number(value48.get(0));
                                       double value55 = value51 + number(value48.get(1));
                                       AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect57 = new AnimatedAtlasVisibilityRepair.Rect(
                                          (int)Math.round(Math.min(value49, value53)),
                                          (int)Math.round(Math.min(value51, value55)),
                                          (int)Math.round(Math.max(value49, value53)),
                                          (int)Math.round(Math.max(value51, value55))
                                       );
                                       if (animatedAtlasVisibilityRepairRect57.x0 >= 0
                                          && animatedAtlasVisibilityRepairRect57.y0 >= 0
                                          && animatedAtlasVisibilityRepairRect57.x1 <= number64
                                          && animatedAtlasVisibilityRepairRect57.y1 <= number65
                                          && animatedAtlasVisibilityRepairRect57.width() != 0
                                          && animatedAtlasVisibilityRepairRect57.height() != 0) {
                                          identifiers69.add(animatedAtlasVisibilityRepairRect57);

                                          for (int number58 = animatedAtlasVisibilityRepairRect57.y0; number58 < animatedAtlasVisibilityRepairRect57.y1; number58++) {
                                             for (int number59 = animatedAtlasVisibilityRepairRect57.x0; number59 < animatedAtlasVisibilityRepairRect57.x1; number59++) {
                                                values67[number58 * number64 + number59] = true;
                                             }
                                          }
                                          continue;
                                       }

                                       return null;
                                    }

                                    return null;
                                 }
                              }
                           }
                        }

                        HashSet identifiers71 = new HashSet<>();

                        for (Object value74 : list(jsonObject23.get("bones"))) {
                           for (Object value78 : list(obj(value74).get("cubes"))) {
                              Object value80 = obj(value78).get("uv");
                              if (value80 instanceof Map) {
                                 for (Object value84 : obj(value80).values()) {
                                    var jsonObject86 = obj(value84);
                                    var value89 = list(jsonObject86.get("uv"));
                                    var value92 = list(jsonObject86.get("uv_size"));
                                    if (value89.size() != 2 || value92.size() != 2) {
                                       return null;
                                    }

                                    double value50 = number(value89.get(0));
                                    double value52 = number(value89.get(1));
                                    double value54 = value50 + number(value92.get(0));
                                    double value56 = value52 + number(value92.get(1));
                                    identifiers71.add(
                                       new AnimatedAtlasVisibilityRepair.Rect(
                                          (int)Math.round(Math.min(value50, value54)),
                                          (int)Math.round(Math.min(value52, value56)),
                                          (int)Math.round(Math.max(value50, value54)),
                                          (int)Math.round(Math.max(value52, value56))
                                       )
                                    );
                                 }
                              }
                           }
                        }

                        if (!identifiers69.equals(identifiers71)) {
                           return null;
                        }

                        BufferedImage image73 = (BufferedImage)value63.get(0);
                        int number75 = number64;
                        int number77 = number65;
                        int number79 = 0;
                        int number81 = 0;
                        int number83 = 0;
                        int number85 = 0;

                        for (int number87 = 0; number87 < number65; number87++) {
                           for (int number90 = 0; number90 < number64; number90++) {
                              int number93 = image73.getRGB(number90, number87);
                              boolean flag96 = false;

                              for (int number99 = 1; number99 < value63.size(); number99++) {
                                 if (((BufferedImage)value63.get(number99)).getRGB(number90, number87) != number93) {
                                    flag96 = true;
                                    break;
                                 }
                              }

                              if (flag96) {
                                 number83++;
                                 if (values67[number87 * number64 + number90]) {
                                    number85++;
                                 }

                                 number75 = Math.min(number75, number90);
                                 number77 = Math.min(number77, number87);
                                 number79 = Math.max(number79, number90 + 1);
                                 number81 = Math.max(number81, number87 + 1);
                              }
                           }
                        }

                        if (number83 >= 10 && !(number83 * 0.05 < number85) && number79 > number75 && number81 > number77) {
                           AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect88 = new AnimatedAtlasVisibilityRepair.Rect(number75, number77, number79, number81);
                           LinkedHashSet identifiers91 = new LinkedHashSet<>();

                           for (AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect97 : identifiers69) {
                              if (animatedAtlasVisibilityRepairRect97.y0 == animatedAtlasVisibilityRepairRect88.y0
                                 && animatedAtlasVisibilityRepairRect97.width() >= animatedAtlasVisibilityRepairRect88.width()
                                 && animatedAtlasVisibilityRepairRect97.width() <= animatedAtlasVisibilityRepairRect88.width() + 2
                                 && animatedAtlasVisibilityRepairRect97.height() >= animatedAtlasVisibilityRepairRect88.height()
                                 && animatedAtlasVisibilityRepairRect97.height() <= animatedAtlasVisibilityRepairRect88.height() + 2
                                 && !animatedAtlasVisibilityRepairRect97.intersects(animatedAtlasVisibilityRepairRect88)) {
                                 boolean flag100 = true;

                                 for (BufferedImage image102 : value63) {
                                    for (int number103 = animatedAtlasVisibilityRepairRect97.y0; number103 < animatedAtlasVisibilityRepairRect97.y1 && flag100; number103++) {
                                       for (int number104 = animatedAtlasVisibilityRepairRect97.x0; number104 < animatedAtlasVisibilityRepairRect97.x1; number104++) {
                                          if ((image102.getRGB(number104, number103) >>> 24 & 0xFF) != 0) {
                                             flag100 = false;
                                             break;
                                          }
                                       }
                                    }

                                    if (!flag100) {
                                       break;
                                    }
                                 }

                                 if (flag100) {
                                    identifiers91.add(animatedAtlasVisibilityRepairRect97);
                                 }
                              }
                           }

                           if (identifiers91.size() != 1) {
                              return null;
                           }

                           AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect95 = (AnimatedAtlasVisibilityRepair.Rect)identifiers91.iterator().next();
                           AnimatedAtlasVisibilityRepair.Candidate animatedAtlasVisibilityRepairCandidate98 = new AnimatedAtlasVisibilityRepair.Candidate();
                           animatedAtlasVisibilityRepairCandidate98.id = text7;
                           animatedAtlasVisibilityRepairCandidate98.sid = animationName6;
                           animatedAtlasVisibilityRepairCandidate98.index = number10;
                           animatedAtlasVisibilityRepairCandidate98.attachPath = attachablePath11;
                           animatedAtlasVisibilityRepairCandidate98.rcPath = text1;
                           animatedAtlasVisibilityRepairCandidate98.attach = jsonObject13;
                           animatedAtlasVisibilityRepairCandidate98.heldRc = jsonObject24;
                           animatedAtlasVisibilityRepairCandidate98.heldDef = jsonObject26;
                           animatedAtlasVisibilityRepairCandidate98.frames = value63;
                           animatedAtlasVisibilityRepairCandidate98.sequence = textures30;
                           animatedAtlasVisibilityRepairCandidate98.source = animatedAtlasVisibilityRepairRect88;
                           animatedAtlasVisibilityRepairCandidate98.destination = animatedAtlasVisibilityRepairRect95;
                           animatedAtlasVisibilityRepairCandidate98.changed = number83;
                           animatedAtlasVisibilityRepairCandidate98.covered = number85;
                           return animatedAtlasVisibilityRepairCandidate98;
                        } else {
                           return null;
                        }
                     } else {
                        return null;
                     }
                  } else {
                     return null;
                  }
               } catch (Exception error60) {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private static BufferedImage rebake(BufferedImage image0, AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect1, AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect2) {
      BufferedImage image3 = new BufferedImage(image0.getWidth(), image0.getHeight(), 2);

      for (int number4 = 0; number4 < image0.getHeight(); number4++) {
         for (int number5 = 0; number5 < image0.getWidth(); number5++) {
            image3.setRGB(number5, number4, image0.getRGB(number5, number4));
         }
      }

      for (int number6 = 0; number6 < animatedAtlasVisibilityRepairRect1.height(); number6++) {
         for (int number7 = 0; number7 < animatedAtlasVisibilityRepairRect1.width(); number7++) {
            image3.setRGB(animatedAtlasVisibilityRepairRect2.x0 + number7, animatedAtlasVisibilityRepairRect2.y0 + number6, image0.getRGB(animatedAtlasVisibilityRepairRect1.x0 + number7, animatedAtlasVisibilityRepairRect1.y0 + number6));
         }
      }

      return image3;
   }

   private static byte[] png(BufferedImage image0) {
      try (ByteArrayOutputStream pngBuffer1 = new ByteArrayOutputStream()) {
         if (!ImageIO.write(image0, "png", pngBuffer1)) {
            throw new IOException("PNG encoder unavailable");
         } else {
            return pngBuffer1.toByteArray();
         }
      } catch (IOException error6) {
         throw new IllegalStateException(error6);
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : new ArrayList<>();
   }

   private static double number(Object value0) {
      return ((Number)value0).doubleValue();
   }

   private static class Candidate {
      String sid;
      String id;
      String attachPath;
      String rcPath;
      int index;
      int changed;
      int covered;
      AnimatedAtlasVisibilityRepair.Rect source;
      AnimatedAtlasVisibilityRepair.Rect destination;
      Map<String, Object> attach;
      Map<String, Object> heldRc;
      Map<String, Object> heldDef;
      List<BufferedImage> frames;
      List<Object> sequence;
   }

   private record Rect(int x0, int y0, int x1, int y1) {
      int width() {
         return this.x1 - this.x0;
      }

      int height() {
         return this.y1 - this.y0;
      }

      boolean intersects(AnimatedAtlasVisibilityRepair.Rect animatedAtlasVisibilityRepairRect1) {
         return this.x0 < animatedAtlasVisibilityRepairRect1.x1 && this.x1 > animatedAtlasVisibilityRepairRect1.x0 && this.y0 < animatedAtlasVisibilityRepairRect1.y1 && this.y1 > animatedAtlasVisibilityRepairRect1.y0;
      }

      @Override
      public String toString() {
         return "[" + this.x0 + "," + this.y0 + "," + this.x1 + "," + this.y1 + "]";
      }
   }
}
