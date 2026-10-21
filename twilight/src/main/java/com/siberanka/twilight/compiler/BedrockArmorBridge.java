package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

final class BedrockArmorBridge {
   static final int INTERPOLATION_STEPS = 16;

   static int repair(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      if (resourceIndex0 == null) {
         return 0;
      }

      try {
         var value3 = protocols(resourceIndex0);
         if (value3.isEmpty()) {
            return 0;
         }

         TreeMap lookup4 = new TreeMap<>();

         for (String text6 : resourceIndex0.paths().stream().sorted().toList()) {
            if (text6.endsWith(".png") && (text6.contains("/textures/entity/equipment/") || text6.contains("/textures/models/armor/"))) {
               BufferedImage image7 = PngImages.decodeLenient(resourceIndex0.find(text6).orElseThrow().readBytes());
               if (image7 != null) {
                  for (BedrockArmorBridge.Protocol bedrockArmorBridgeProtocol9 : value3) {
                     if (image7.getWidth() == bedrockArmorBridgeProtocol9.width && image7.getHeight() == bedrockArmorBridgeProtocol9.height && image7.getRGB(0, 1) == -1 && image7.getRGB(7, 7) == 0) {
                        lookup4.put(text6, readAtlas(text6, image7, bedrockArmorBridgeProtocol9));
                     }
                  }
               }
            }
         }

         int number21 = 0;
         ArrayList entries22 = new ArrayList<>();
         HashSet identifiers23 = new HashSet<>();

         for (ItemCandidate itemCandidate25 : entries1) {
            String text10 = itemCandidate25.baseItem();
            String text11 = slot(text10);
            if (text11 != null) {
               String suffix12 = text10.substring(text10.indexOf(58) + 1, text10.length() - text11.length() - 1);
               String suffix13 = text10.substring(0, text10.indexOf(58));
               String text14 = text11.equals("leggings") ? "humanoid_leggings" : "humanoid";
               String texturePath15 = "assets/" + suffix13 + "/textures/entity/equipment/" + text14 + "/" + suffix12 + ".png";
               BedrockArmorBridge.Atlas bedrockArmorBridgeAtlas16 = (BedrockArmorBridge.Atlas)lookup4.get(texturePath15);
               if (bedrockArmorBridgeAtlas16 == null) {
                  bedrockArmorBridgeAtlas16 = (BedrockArmorBridge.Atlas)lookup4.get(
                     "assets/" + suffix13 + "/textures/models/armor/" + suffix12 + "_layer_" + (text11.equals("leggings") ? 2 : 1) + ".png"
                  );
               }

               if (bedrockArmorBridgeAtlas16 != null) {
                  String text17 = BedrockPackCompiler.bedrockIdentifier(itemCandidate25);
                  String suffix18 = text17.substring(text17.indexOf(58) + 1);
                  if (packFiles2.containsKey("attachables/" + suffix18 + ".json")) {
                     String suffix19 = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(resourceIndex0.find(bedrockArmorBridgeAtlas16.path).orElseThrow().readBytes()))
                        .substring(0, 16);
                     if (identifiers23.add(suffix19)) {
                        writeFrames(bedrockArmorBridgeAtlas16, suffix19, packFiles2);
                     }

                     writeAttachable(bedrockArmorBridgeAtlas16, suffix19, text17, suffix18, text11, packFiles2);
                     entries22.add(
                        Map.of(
                           "identifier",
                           text17,
                           "model",
                           itemCandidate25.mappingModel(),
                           "slot",
                           text11,
                           "atlas",
                           bedrockArmorBridgeAtlas16.path,
                           "selector",
                           "equipped item dye RGB; unmatched colors use column 0"
                        )
                     );
                     number21++;
                  }
               }
            }
         }

         if (number21 > 0) {
            packFiles2.put(
               "materials/twilight_armor.material",
               json(
                  Map.of(
                     "materials",
                     Map.of(
                        "version",
                        "1.0.0",
                        "twilight_armor_emissive_enchanted:entity_emissive_alpha",
                        Map.<String, List<String>>of("+defines", List.of("GLINT"))
                     )
                  )
               )
            );
            packFiles2.put(
               "twilight/armor-conversion.json",
               json(
                  Map.of(
                     "version",
                     517,
                     "items",
                     entries22,
                     "interpolation_steps",
                     16,
                     "clock",
                     "(q.time_stamp mod 24000) / 24000 * shader V2 * animation speed",
                     "runtime_validation_required",
                     true
                  )
               )
            );
         }

         return number21;
      } catch (Exception error20) {
         throw new IllegalStateException("Cannot translate Java shader armor atlas", error20);
      }
   }

   static List<BedrockArmorBridge.Protocol> protocols(ResourceIndex resourceIndex0) throws IOException {
      LinkedHashSet identifiers1 = new LinkedHashSet<>();

      for (String text3 : resourceIndex0.paths().stream().sorted().toList()) {
         if (text3.endsWith(".fsh") && text3.contains("/shaders/")) {
            String text4 = resourceIndex0.find(text3).orElseThrow().readUtf8();
            if (text4.contains("START_CUSTOM_ARMORS") && text4.contains("tint == customColor") && text4.contains("V1 * 4")) {
               int number5 = define(text4, "TEX_WIDTH");
               int number6 = define(text4, "TEX_HEIGHT");
               int number7 = define(text4, "V1");
               int number8 = define(text4, "V2");
               if (number7 >= 8
                  && number7 <= 256
                  && number5 >= 8 * number7
                  && number5 <= 8192
                  && number6 >= 2 * number7
                  && number6 <= 8192
                  && number5 % (4 * number7) == 0
                  && number6 % (2 * number7) == 0
                  && number8 > 0) {
                  identifiers1.add(new BedrockArmorBridge.Protocol(number5, number6, number7, number8));
               }
            }
         }
      }

      return List.copyOf(identifiers1);
   }

   static int define(String text0, String text1) {
      Matcher matcher2 = Pattern.compile("(?m)^\\s*#define\\s+" + Pattern.quote(text1) + "\\s+(\\d+)\\b").matcher(text0);
      return matcher2.find() ? Integer.parseInt(matcher2.group(1)) : 0;
   }

   static BedrockArmorBridge.Atlas readAtlas(String text0, BufferedImage image1, BedrockArmorBridge.Protocol bedrockArmorBridgeProtocol2) {
      ArrayList entries3 = new ArrayList<>();
      entries3.add(new BedrockArmorBridge.Column(0, -1, 1, 0, false, 0, true));

      for (int number4 = 1; number4 < image1.getWidth() / (4 * bedrockArmorBridgeProtocol2.unit); number4++) {
         int number5 = number4 * 4 * bedrockArmorBridgeProtocol2.unit;
         int number6 = image1.getRGB(number5, 0);
         int number7 = image1.getRGB(number5 + 1, 0);
         int number8 = image1.getRGB(number5 + 2, 0);
         if (number6 >>> 24 == 255) {
            int number9 = Math.max(1, channel(number7, 16));
            int number10 = channel(number7, 8);
            int number11 = channel(number8, 16);
            if (number9 > image1.getHeight() / (2 * bedrockArmorBridgeProtocol2.unit)) {
               throw new IllegalArgumentException("Atlas frame count exceeds height: " + text0 + " column " + number4);
            }

            if (number11 == 1 && number5 + 8 * bedrockArmorBridgeProtocol2.unit > image1.getWidth()) {
               throw new IllegalArgumentException("Missing emissive mask: " + text0);
            }

            entries3.add(new BedrockArmorBridge.Column(number4, number6 & 16777215, number9, number10, channel(number7, 0) > 0, number11, channel(number8, 8) == 1));
         }
      }

      return new BedrockArmorBridge.Atlas(text0, image1, bedrockArmorBridgeProtocol2, List.copyOf(entries3));
   }

   static int channel(int number0, int number1) {
      return (number0 >>> number1 & 0xFF) * (number0 >>> 24) / 255;
   }

   static int selectColumn(BedrockArmorBridge.Atlas bedrockArmorBridgeAtlas0, int number1) {
      for (BedrockArmorBridge.Column bedrockArmorBridgeColumn3 : bedrockArmorBridgeAtlas0.columns) {
         if (bedrockArmorBridgeColumn3.index != 0 && bedrockArmorBridgeColumn3.rgb == (number1 & 16777215)) {
            return bedrockArmorBridgeColumn3.index;
         }
      }

      return 0;
   }

   static String slot(String text0) {
      for (String text2 : List.of("helmet", "chestplate", "leggings", "boots")) {
         if (text0 != null && text0.endsWith("_" + text2)) {
            return text2;
         }
      }

      return null;
   }

   static int slotIndex(String text0) {
      return switch (text0) {
         case "helmet" -> 0;
         case "chestplate" -> 1;
         case "leggings" -> 2;
         default -> 3;
      };
   }

   static int samples(BedrockArmorBridge.Column bedrockArmorBridgeColumn0) {
      return bedrockArmorBridgeColumn0.frames * (bedrockArmorBridgeColumn0.interpolate && bedrockArmorBridgeColumn0.frames > 1 ? 16 : 1);
   }

   static String stem(String text0, BedrockArmorBridge.Column bedrockArmorBridgeColumn1, int number2) {
      return "textures/twilight/armor/" + text0 + "_c" + bedrockArmorBridgeColumn1.index + "_" + number2;
   }

   static BufferedImage frame(BedrockArmorBridge.Atlas bedrockArmorBridgeAtlas0, BedrockArmorBridge.Column bedrockArmorBridgeColumn1, int number2) {
      int number3 = 4 * bedrockArmorBridgeAtlas0.protocol.unit;
      int number4 = 2 * bedrockArmorBridgeAtlas0.protocol.unit;
      int number5 = bedrockArmorBridgeColumn1.interpolate && bedrockArmorBridgeColumn1.frames > 1 ? 16 : 1;
      int number6 = number2 / number5;
      int number7 = (number6 + 1) % bedrockArmorBridgeColumn1.frames;
      int number8 = number2 % number5;
      BufferedImage image9 = new BufferedImage(number3, number4, 2);

      for (int number10 = 0; number10 < number4; number10++) {
         for (int number11 = 0; number11 < number3; number11++) {
            int number12 = bedrockArmorBridgeAtlas0.image.getRGB(bedrockArmorBridgeColumn1.index * number3 + number11, number6 * number4 + number10);
            int number13 = bedrockArmorBridgeAtlas0.image.getRGB(bedrockArmorBridgeColumn1.index * number3 + number11, number7 * number4 + number10);
            int number14 = 0;

            for (byte number15 = 0; number15 <= 24; number15 += 8) {
               number14 |= ((number12 >>> number15 & 0xFF) * (number5 - number8) + (number13 >>> number15 & 0xFF) * number8) / number5 << number15;
            }

            image9.setRGB(number11, number10, number14);
         }
      }

      return image9;
   }

   static void writeFrames(BedrockArmorBridge.Atlas bedrockArmorBridgeAtlas0, String text1, Map<String, byte[]> packFiles2) throws IOException {
      ArrayList entries3 = new ArrayList<>();

      for (BedrockArmorBridge.Column bedrockArmorBridgeColumn5 : bedrockArmorBridgeAtlas0.columns) {
         for (int number6 = 0; number6 < samples(bedrockArmorBridgeColumn5); number6++) {
            BufferedImage image7 = frame(bedrockArmorBridgeAtlas0, bedrockArmorBridgeColumn5, number6);
            packFiles2.put(stem(text1, bedrockArmorBridgeColumn5, number6) + ".png", png(image7));
            if (bedrockArmorBridgeColumn5.emissive > 0) {
               BufferedImage image8 = new BufferedImage(image7.getWidth(), image7.getHeight(), 2);
               int number9 = number6 / (bedrockArmorBridgeColumn5.interpolate && bedrockArmorBridgeColumn5.frames > 1 ? 16 : 1);

               for (int number10 = 0; number10 < image7.getHeight(); number10++) {
                  for (int number11 = 0; number11 < image7.getWidth(); number11++) {
                     int number12 = image7.getRGB(number11, number10);
                     int number13 = bedrockArmorBridgeColumn5.emissive > 1
                        ? 255
                        : bedrockArmorBridgeAtlas0.image.getRGB((bedrockArmorBridgeColumn5.index + 1) * image7.getWidth() + number11, number9 * image7.getHeight() + number10) >>> 24;
                     int number14 = number12 >>> 24 < 26 ? 0 : (number13 > 0 ? 1 : 255);
                     if (number13 > 0 && number13 < 255) {
                        number12 = multiply(number12, number13, number13, number13);
                     }

                     image8.setRGB(number11, number10, number14 << 24 | number12 & 16777215);
                  }
               }

               packFiles2.put(stem(text1, bedrockArmorBridgeColumn5, number6) + "_emissive.tga", tga(image8));
            }
         }

         entries3.add(
            Map.of(
               "column",
               bedrockArmorBridgeColumn5.index,
               "color",
               bedrockArmorBridgeColumn5.rgb,
               "source_frames",
               bedrockArmorBridgeColumn5.frames,
               "speed",
               bedrockArmorBridgeColumn5.speed,
               "interpolate",
               bedrockArmorBridgeColumn5.interpolate,
               "samples",
               samples(bedrockArmorBridgeColumn5),
               "emissive",
               bedrockArmorBridgeColumn5.emissive,
               "tint",
               bedrockArmorBridgeColumn5.tint
            )
         );
      }

      packFiles2.put(
         "twilight/armor-atlas-" + text1 + ".json",
         json(
            Map.of(
               "source",
               bedrockArmorBridgeAtlas0.path,
               "frame_width",
               4 * bedrockArmorBridgeAtlas0.protocol.unit,
               "frame_height",
               2 * bedrockArmorBridgeAtlas0.protocol.unit,
               "clock_scale",
               bedrockArmorBridgeAtlas0.protocol.clockScale,
               "columns",
               entries3
            )
         )
      );
   }

   static void writeAttachable(BedrockArmorBridge.Atlas bedrockArmorBridgeAtlas0, String text1, String text2, String text3, String text4, Map<String, byte[]> packFiles5) {
      int number6 = slotIndex(text4);
      String text7 = "math.floor(q.armor_color_slot("
         + number6
         + ",0)*255+0.5)*65536 + math.floor(q.armor_color_slot("
         + number6
         + ",1)*255+0.5)*256 + math.floor(q.armor_color_slot("
         + number6
         + ",2)*255+0.5)";
      String text8 = "v.twilight_armor_rgb = v.is_dyed ? (" + text7 + ") : 10511680;";
      String text9 = "0";

      for (BedrockArmorBridge.Column bedrockArmorBridgeColumn11 : bedrockArmorBridgeAtlas0.columns.reversed()) {
         if (bedrockArmorBridgeColumn11.index != 0) {
            text9 = "v.twilight_armor_rgb == " + bedrockArmorBridgeColumn11.rgb + " ? " + bedrockArmorBridgeColumn11.index + " : (" + text9 + ")";
         }
      }

      String text21 = text8 + "v.twilight_armor_column = " + text9 + ";";
      LinkedHashMap lookup22 = new LinkedHashMap<>();
      LinkedHashMap lookup12 = new LinkedHashMap<>();
      ArrayList entries13 = new ArrayList<>();
      lookup22.put("default", stem(text1, (BedrockArmorBridge.Column)bedrockArmorBridgeAtlas0.columns.getFirst(), 0));
      lookup22.put("enchanted", "textures/misc/enchanted_actor_glint");

      for (BedrockArmorBridge.Column bedrockArmorBridgeColumn15 : bedrockArmorBridgeAtlas0.columns) {
         ArrayList entries16 = new ArrayList<>();

         for (int number17 = 0; number17 < samples(bedrockArmorBridgeColumn15); number17++) {
            String text18 = "c" + bedrockArmorBridgeColumn15.index + "_f" + number17;
            lookup22.put(text18, stem(text1, bedrockArmorBridgeColumn15, number17) + (bedrockArmorBridgeColumn15.emissive > 0 ? "_emissive" : ""));
            entries16.add("Texture." + text18);
         }

         String text26 = "controller.render.twilight.armor." + text1 + "." + number6 + ".c" + bedrockArmorBridgeColumn15.index;
         LinkedHashMap lookup28 = new LinkedHashMap<>();
         lookup28.put("arrays", Map.of("textures", Map.of("Array.frames", entries16)));
         lookup28.put("geometry", "Geometry.default");
         lookup28.put(
            "materials",
            List.of(
               Map.of(
                  "*",
                  bedrockArmorBridgeColumn15.emissive > 0 ? "v.is_enchanted ? Material.glow_enchanted : Material.glow" : "v.is_enchanted ? Material.enchanted : Material.default"
               )
            )
         );
         int number19 = bedrockArmorBridgeColumn15.interpolate && bedrockArmorBridgeColumn15.frames > 1 ? 16 : 1;
         String text20 = samples(bedrockArmorBridgeColumn15) != 1 && bedrockArmorBridgeColumn15.speed != 0
            ? "math.mod(math.floor(math.mod(q.time_stamp, 24000) * "
               + bedrockArmorBridgeAtlas0.protocol.clockScale
               + " * "
               + bedrockArmorBridgeColumn15.speed
               + " * "
               + number19
               + " / 24000), "
               + samples(bedrockArmorBridgeColumn15)
               + ")"
            : "0";
         lookup28.put("textures", List.of("Array.frames[" + text20 + "]", "Texture.enchanted"));
         if (bedrockArmorBridgeColumn15.tint) {
            lookup28.put(
               "color",
               Map.of(
                  "r",
                  "v.is_dyed ? q.armor_color_slot(" + number6 + ",0) : 0.6274509804",
                  "g",
                  "v.is_dyed ? q.armor_color_slot(" + number6 + ",1) : 0.3960784314",
                  "b",
                  "v.is_dyed ? q.armor_color_slot(" + number6 + ",2) : 0.2509803922",
                  "a",
                  1
               )
            );
         }

         lookup12.put(text26, lookup28);
         entries13.add(Map.of(text26, "v.twilight_armor_column == " + bedrockArmorBridgeColumn15.index));
      }

      packFiles5.put("render_controllers/armor_" + text1 + "_" + number6 + ".json", json(Map.of("format_version", "1.8.0", "render_controllers", lookup12)));

      for (boolean flag24 : List.of(false, true)) {
         LinkedHashMap lookup25 = new LinkedHashMap<>();
         lookup25.put("identifier", text2 + (flag24 ? ".player" : ""));
         lookup25.put("item", Map.of(text2, "q.owner_identifier " + (flag24 ? "==" : "!=") + " 'minecraft:player'"));
         lookup25.put(
            "materials",
            Map.of("default", "armor", "enchanted", "armor_enchanted", "glow", "entity_emissive_alpha", "glow_enchanted", "twilight_armor_emissive_enchanted")
         );
         lookup25.put("textures", lookup22);
         lookup25.put("geometry", Map.of("default", "geometry." + (flag24 ? "player" : "humanoid") + ".armor." + text4));

         String text27 = switch (text4) {
            case "helmet" -> "helmet";
            case "chestplate" -> "chest";
            case "leggings" -> "leg";
            default -> "boot";
         };
         LinkedHashMap lookup30 = new LinkedHashMap<>();
         lookup30.put("parent_setup", "v." + text27 + "_layer_visible = 0.0;");
         lookup30.put("pre_animation", List.of(text21));
         if (flag24) {
            lookup30.put("animate", List.of("offset"));
            lookup25.put("animations", Map.of("offset", "animation.armor." + text4 + ".offset"));
         }

         lookup25.put("scripts", lookup30);
         lookup25.put("render_controllers", entries13);
         packFiles5.put(
            "attachables/" + text3 + (flag24 ? ".player" : "") + ".json",
            json(Map.of("format_version", "1.21.30", "minecraft:attachable", Map.<String, LinkedHashMap>of("description", lookup25)))
         );
      }
   }

   static int multiply(int number0, int number1, int number2, int number3) {
      return number0 & 0xFF000000 | (number0 >>> 16 & 0xFF) * number1 / 255 << 16 | (number0 >>> 8 & 0xFF) * number2 / 255 << 8 | (number0 & 0xFF) * number3 / 255;
   }

   static byte[] tga(BufferedImage image0) {
      int number1 = image0.getWidth();
      int number2 = image0.getHeight();
      byte[] resourceBytes3 = new byte[18 + number1 * number2 * 4];
      resourceBytes3[2] = 2;
      resourceBytes3[12] = (byte)number1;
      resourceBytes3[13] = (byte)(number1 >>> 8);
      resourceBytes3[14] = (byte)number2;
      resourceBytes3[15] = (byte)(number2 >>> 8);
      resourceBytes3[16] = 32;
      resourceBytes3[17] = 40;
      int number4 = 18;

      for (int number5 = 0; number5 < number2; number5++) {
         for (int number6 = 0; number6 < number1; number6++) {
            int number7 = image0.getRGB(number6, number5);
            resourceBytes3[number4++] = (byte)number7;
            resourceBytes3[number4++] = (byte)(number7 >>> 8);
            resourceBytes3[number4++] = (byte)(number7 >>> 16);
            resourceBytes3[number4++] = (byte)(number7 >>> 24);
         }
      }

      return resourceBytes3;
   }

   static byte[] png(BufferedImage image0) throws IOException {
      ByteArrayOutputStream pngBuffer1 = new ByteArrayOutputStream();
      ImageIO.write(image0, "PNG", pngBuffer1);
      return pngBuffer1.toByteArray();
   }

   static byte[] json(Object value0) {
      return BedrockAnimationBridge.Json.bytes(value0);
   }

   record Atlas(String path, BufferedImage image, BedrockArmorBridge.Protocol protocol, List<BedrockArmorBridge.Column> columns) {
   }

   record Column(int index, int rgb, int frames, int speed, boolean interpolate, int emissive, boolean tint) {
   }

   record Protocol(int width, int height, int unit, int clockScale) {
   }
}
