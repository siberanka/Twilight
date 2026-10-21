package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.imageio.ImageIO;

public final class BedrockAnimationBridge {
   private static final int MAX_PERIOD = 512;
   private static final int MAX_ATLAS_PIXELS = 4194304;

   private BedrockAnimationBridge() {
   }

   public static void repair(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      sanitizeGeyserMappings(packFiles2);

      for (String text4 : new ArrayList<>(packFiles2.keySet())) {
         if (text4.startsWith("attachables/") && text4.endsWith(".json")) {
            try {
               var jsonObject5 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles2.get(text4)));
               var attachable6 = obj(jsonObject5.get("minecraft:attachable"));
               var description7 = obj(attachable6.get("description"));
               var value8 = objOrNull(description7.get("item"));
               if (value8 != null && description7.get("identifier") instanceof String text10 && !value8.containsKey(text10)) {
                  description7.remove("item");
                  packFiles2.put(text4, BedrockAnimationBridge.Json.bytes(jsonObject5));
               }
            } catch (RuntimeException error35) {
            }
         }
      }

      BedrockArmorBridge.repair(resourceIndex0, entries1, packFiles2);
      JavaModelResolver javaModelResolver39 = new JavaModelResolver(resourceIndex0);

      for (ItemCandidate itemCandidate41 : entries1) {
         try {
            String text42 = BedrockPackCompiler.bedrockIdentifier(itemCandidate41);
            String suffix43 = text42.substring(text42.indexOf(58) + 1);
            String attachablePath44 = "attachables/" + suffix43 + ".json";
            String texturePath45 = "textures/twilight/" + suffix43 + ".png";
            byte[] resourceBytes46 = (byte[])packFiles2.get(attachablePath44);
            byte[] resourceBytes11 = (byte[])packFiles2.get(texturePath45);
            if (resourceBytes46 != null && resourceBytes11 != null) {
               var jsonObject12 = obj(BedrockAnimationBridge.Json.read(resourceBytes46));
               var attachable13 = obj(obj(jsonObject12.get("minecraft:attachable")).get("description"));
               var renderControllers14 = list(attachable13.get("render_controllers"));
               if (renderControllers14.size() == 1 && "controller.render.item_default".equals(renderControllers14.get(0))) {
                  boolean flag15 = itemCandidate41.customModelData().isPresent() || !itemCandidate41.visualModels().isEmpty();
                  ResolvedJavaModel resolvedJavaModel16 = javaModelResolver39.resolveAll(itemCandidate41.visualModels(), flag15);
                  LinkedHashMap<String, Frames> lookup17 = new LinkedHashMap<>();

                  for (String text19 : resolvedJavaModel16.textures().values()) {
                     String text20 = TextureSet.spriteTexture(resourceIndex0, text19);
                     String text21 = JavaModelResolver.texturePath(text20);
                     if (!lookup17.containsKey(text21) && resourceIndex0.find(text21 + ".mcmeta").isPresent() && resourceIndex0.find(text21).isPresent()) {
                        BedrockAnimationBridge.Frames bedrockAnimationBridgeFrames22 = readFrames(resourceIndex0, text21);
                        if (bedrockAnimationBridgeFrames22 != null && bedrockAnimationBridgeFrames22.period > 1) {
                           lookup17.put(text21, bedrockAnimationBridgeFrames22);
                        }
                     }
                  }

                  if (!lookup17.isEmpty()) {
                     BufferedImage image47 = decode(resourceBytes11);
                     if ((long)image47.getWidth() * image47.getHeight() <= 4194304L) {
                        ArrayList<Region> arrayListRegion48 = new ArrayList<>();
                        long number49 = 1L;

                        for (BedrockAnimationBridge.Frames bedrockAnimationBridgeFrames23 : lookup17.values()) {
                           int[] values24 = locate(image47, bedrockAnimationBridgeFrames23.first());
                           if (values24 != null) {
                              arrayListRegion48.add(new BedrockAnimationBridge.Region(bedrockAnimationBridgeFrames23, values24[0], values24[1]));
                              number49 = lcm(number49, bedrockAnimationBridgeFrames23.period);
                              if (number49 > 512L) {
                                 break;
                              }
                           }
                        }

                        if (!arrayListRegion48.isEmpty() && number49 <= 512L) {
                           var textures51 = obj(attachable13.get("textures"));
                           LinkedHashMap lookup52 = new LinkedHashMap<>();
                           ArrayList entries53 = new ArrayList<>();

                           for (int number25 = 0; number25 < (int)number49; number25++) {
                              BufferedImage image26 = new BufferedImage(image47.getWidth(), image47.getHeight(), 2);
                              Graphics2D graphicsD27 = image26.createGraphics();

                              try {
                                 graphicsD27.drawImage(image47, 0, 0, null);

                                 for (BedrockAnimationBridge.Region bedrockAnimationBridgeRegion29 : arrayListRegion48) {
                                    graphicsD27.drawImage(bedrockAnimationBridgeRegion29.frames.at(number25), bedrockAnimationBridgeRegion29.x, bedrockAnimationBridgeRegion29.y, null);
                                 }
                              } finally {
                                 graphicsD27.dispose();
                              }

                              String text57 = imageSignature(image26);
                              String text59 = (String)lookup52.get(text57);
                              if (text59 == null) {
                                 text59 = "java_anim_" + lookup52.size();
                                 lookup52.put(text57, text59);
                                 String texturePath30 = "textures/twilight/" + suffix43 + "_" + text59;
                                 packFiles2.put(texturePath30 + ".png", encode(image26));
                                 textures51.put(text59, texturePath30);
                              }

                              entries53.add("Texture." + text59);
                           }

                           String text54 = "controller.render.twilight." + suffix43 + "_java_animation";
                           String text55 = "Array.twilight_java_frames";
                           LinkedHashMap lookup56 = new LinkedHashMap<>();
                           lookup56.put("*", "variable.is_enchanted ? Material.enchanted : Material.default");
                           LinkedHashMap lookup58 = new LinkedHashMap<>();
                           lookup58.put("arrays", Map.of("textures", Map.of(text55, entries53)));
                           lookup58.put("geometry", "Geometry.default");
                           lookup58.put("materials", List.of(lookup56));
                           lookup58.put("textures", List.of(text55 + "[math.mod(math.floor(q.anim_time * 20), " + number49 + ")]"));
                           LinkedHashMap lookup60 = new LinkedHashMap<>();
                           lookup60.put("format_version", "1.8.0");
                           lookup60.put("render_controllers", Map.of(text54, lookup58));
                           packFiles2.put("render_controllers/" + suffix43 + "_java_animation.json", BedrockAnimationBridge.Json.bytes(lookup60));
                           attachable13.put("render_controllers", List.of(text54));
                           packFiles2.put(attachablePath44, BedrockAnimationBridge.Json.bytes(jsonObject12));
                        }
                     }
                  }
               }
            }
         } catch (JavaModelResolver.MissingModelException error37) {
         } catch (Exception error38) {
         }
      }
   }

   static void sanitizeGeyserMappings(Map<String, byte[]> packFiles0) {
      GeyserConditionPredicateRepair.sanitize(packFiles0);
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 != null) {
         try {
            var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
            var itemDefinitions3 = obj(jsonObject2.get("items"));
            var value4 = Set.of("charge_type", "trim_material", "context_dimension", "custom_model_data");
            boolean flag5 = false;

            for (var value7 : itemDefinitions3.entrySet()) {
               var value8 = list(value7.getValue());
               if (value8.removeIf(var1x -> hasUnsupportedMatch(obj(var1x).get("predicate"), value4))) {
                  flag5 = true;
               }
            }

            if (flag5) {
               packFiles0.put("twilight/geyser_item_mappings.json", BedrockAnimationBridge.Json.bytes(jsonObject2));
            }
         } catch (RuntimeException error9) {
         }
      }
   }

   private static boolean hasUnsupportedMatch(Object value0, Set<String> identifiers1) {
      if (value0 instanceof List) {
         for (Object value9 : (List)value0) {
            if (hasUnsupportedMatch(value9, identifiers1)) {
               return true;
            }
         }

         return false;
      } else {
         if (value0 instanceof Map mapping2) {
            if ("match".equals(mapping2.get("type")) && mapping2.get("property") instanceof String text3 && !identifiers1.contains(text3)) {
               return true;
            }

            for (Object value8 : mapping2.values()) {
               if ((value8 instanceof Map || value8 instanceof List) && hasUnsupportedMatch(value8, identifiers1)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static BedrockAnimationBridge.Frames readFrames(ResourceIndex resourceIndex0, String text1) throws IOException {
      var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceIndex0.find(text1 + ".mcmeta").orElseThrow().readBytes()));
      var value3 = objOrNull(jsonObject2.get("animation"));
      if (value3 == null) {
         return null;
      }

      BufferedImage image4 = decode(resourceIndex0.find(text1).orElseThrow().readBytes());
      int number5 = number(value3.get("width"), -1);
      int number6 = number(value3.get("height"), -1);
      if (number5 < 0 && number6 < 0) {
         number5 = number6 = Math.min(image4.getWidth(), image4.getHeight());
      } else {
         if (number5 < 0) {
            number5 = image4.getWidth();
         }

         if (number6 < 0) {
            number6 = image4.getHeight();
         }
      }

      if (number5 > 0 && number6 > 0 && image4.getWidth() % number5 == 0 && image4.getHeight() % number6 == 0) {
         int number7 = image4.getWidth() / number5;
         int number8 = number7 * (image4.getHeight() / number6);
         if (number8 > 4096) {
            return null;
         }

         ArrayList entries9 = new ArrayList<>();

         for (int number10 = 0; number10 < number8; number10++) {
            entries9.add(image4.getSubimage(number10 % number7 * number5, number10 / number7 * number6, number5, number6));
         }

         int number19 = number(value3.get("frametime"), 1);
         if (number19 < 1) {
            return null;
         }

         ArrayList entries11 = new ArrayList<>();
         ArrayList<Integer> arrayListInteger12 = new ArrayList<>();
         var value13 = listOrNull(value3.get("frames"));
         if (value13 == null) {
            for (int number14 = 0; number14 < number8; number14++) {
               entries11.add(number14);
               arrayListInteger12.add(number19);
            }
         } else {
            for (Object value15 : value13) {
               var value16 = objOrNull(value15);
               int number17 = value16 == null ? number(value15, -1) : number(value16.get("index"), -1);
               int number18 = value16 == null ? number19 : number(value16.get("time"), number19);
               if (number17 < 0 || number17 >= number8 || number18 < 1) {
                  return null;
               }

               entries11.add(number17);
               arrayListInteger12.add(number18);
            }
         }

         int number21 = 0;

         for (int number23 : arrayListInteger12) {
            number21 += number23;
            if (number21 > 100000) {
               return null;
            }
         }

         return entries11.isEmpty() ? null : new BedrockAnimationBridge.Frames(entries9, entries11, arrayListInteger12, number21);
      } else {
         return null;
      }
   }

   private static int[] locate(BufferedImage image0, BufferedImage image1) {
      if (image1.getWidth() <= image0.getWidth() && image1.getHeight() <= image0.getHeight()) {
         for (int number2 = 0; number2 <= image0.getWidth() - image1.getWidth(); number2++) {
            boolean flag3 = true;

            for (int number4 = 0; number4 < image1.getHeight() && flag3; number4++) {
               for (int number5 = 0; number5 < image1.getWidth(); number5++) {
                  if (image1.getRGB(number5, number4) != image0.getRGB(number2 + number5, number4)) {
                     flag3 = false;
                     break;
                  }
               }
            }

            if (flag3) {
               return new int[]{number2, 0};
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static long lcm(long number0, long number2) {
      long number4 = number0;
      long number6 = number2;

      while (number6 != 0L) {
         long number8 = number4 % number6;
         number4 = number6;
         number6 = number8;
      }

      return number0 / number4 * number2;
   }

   private static BufferedImage decode(byte[] resourceBytes0) throws IOException {
      BufferedImage image1 = ImageIO.read(new ByteArrayInputStream(resourceBytes0));
      if (image1 == null) {
         throw new IOException("Invalid PNG");
      } else {
         return image1;
      }
   }

   private static byte[] encode(BufferedImage image0) throws IOException {
      ByteArrayOutputStream pngBuffer1 = new ByteArrayOutputStream();
      if (!ImageIO.write(image0, "PNG", pngBuffer1)) {
         throw new IOException("PNG writer unavailable");
      } else {
         return pngBuffer1.toByteArray();
      }
   }

   private static String imageSignature(BufferedImage image0) {
      try {
         MessageDigest messageDigest1 = MessageDigest.getInstance("SHA-256");

         for (int number2 = 0; number2 < image0.getHeight(); number2++) {
            for (int number3 = 0; number3 < image0.getWidth(); number3++) {
               int number4 = image0.getRGB(number3, number2);
               messageDigest1.update((byte)(number4 >>> 24));
               messageDigest1.update((byte)(number4 >>> 16));
               messageDigest1.update((byte)(number4 >>> 8));
               messageDigest1.update((byte)number4);
            }
         }

         return HexFormat.of().formatHex(messageDigest1.digest());
      } catch (NoSuchAlgorithmException error5) {
         throw new IllegalStateException(error5);
      }
   }

   static Map<String, Object> obj(Object value0) {
      return (Map<String, Object>)value0;
   }

   private static Map<String, Object> objOrNull(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static List<Object> list(Object value0) {
      return (List<Object>)value0;
   }

   private static List<Object> listOrNull(Object value0) {
      return value0 instanceof List ? (List)value0 : null;
   }

   private static int number(Object value0, int number1) {
      return value0 instanceof Number ? ((Number)value0).intValue() : number1;
   }

   private record Frames(List<BufferedImage> tiles, List<Integer> order, List<Integer> time, int period) {
      BufferedImage first() {
         return this.tiles.get((Integer)this.order.getFirst());
      }

      BufferedImage at(int number1) {
         int number2 = number1 % this.period;

         for (int number3 = 0; number3 < this.order.size(); number3++) {
            if (number2 < this.time.get(number3)) {
               return this.tiles.get(this.order.get(number3));
            }

            number2 -= this.time.get(number3);
         }

         return this.first();
      }
   }

   static final class Json {
      private final String text;
      private int pos;

      private Json(String text1) {
         this.text = text1;
      }

      static Object read(byte[] resourceBytes0) {
         BedrockAnimationBridge.Json bedrockAnimationBridgeJson1 = new BedrockAnimationBridge.Json(new String(resourceBytes0, StandardCharsets.UTF_8));
         Object value2 = bedrockAnimationBridgeJson1.value();
         bedrockAnimationBridgeJson1.skip();
         if (bedrockAnimationBridgeJson1.pos != bedrockAnimationBridgeJson1.text.length()) {
            throw new IllegalArgumentException("Trailing JSON");
         } else {
            return value2;
         }
      }

      static byte[] bytes(Object value0) {
         StringBuilder builder1 = new StringBuilder();
         write(value0, builder1);
         return builder1.toString().getBytes(StandardCharsets.UTF_8);
      }

      void skip() {
         while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
         }
      }

      char next() {
         return this.text.charAt(this.pos++);
      }

      Object value() {
         this.skip();
         char char1 = this.next();
         if (char1 == '{') {
            LinkedHashMap lookup7 = new LinkedHashMap<>();
            this.skip();
            if (this.text.charAt(this.pos) == '}') {
               this.pos++;
               return lookup7;
            }

            char char4;
            do {
               this.skip();
               String text9 = this.string();
               this.skip();
               if (this.next() != ':') {
                  throw new IllegalArgumentException("JSON key");
               }

               lookup7.put(text9, this.value());
               this.skip();
               char4 = this.next();
               if (char4 == '}') {
                  return lookup7;
               }
            } while (char4 == ',');

            throw new IllegalArgumentException("JSON object");
         } else if (char1 == '[') {
            ArrayList entries6 = new ArrayList<>();
            this.skip();
            if (this.text.charAt(this.pos) == ']') {
               this.pos++;
               return entries6;
            }

            char char8;
            do {
               entries6.add(this.value());
               this.skip();
               char8 = this.next();
               if (char8 == ']') {
                  return entries6;
               }
            } while (char8 == ',');

            throw new IllegalArgumentException("JSON array");
         } else {
            if (char1 == '"') {
               this.pos--;
               return this.string();
            }

            if (char1 == 't') {
               this.literal("rue");
               return true;
            }

            if (char1 == 'f') {
               this.literal("alse");
               return false;
            }

            if (char1 == 'n') {
               this.literal("ull");
               return null;
            }

            int number2 = this.pos - 1;

            while (this.pos < this.text.length() && "0123456789+-.eE".indexOf(this.text.charAt(this.pos)) >= 0) {
               this.pos++;
            }

            if (number2 == this.pos) {
               throw new IllegalArgumentException("JSON token");
            }

            String suffix3 = this.text.substring(number2, this.pos);
            if (suffix3.indexOf(46) < 0 && suffix3.indexOf(101) < 0 && suffix3.indexOf(69) < 0) {
               try {
                  return Long.parseLong(suffix3);
               } catch (NumberFormatException error5) {
               }
            }

            return Double.parseDouble(suffix3);
         }
      }

      void literal(String text1) {
         if (!this.text.startsWith(text1, this.pos)) {
            throw new IllegalArgumentException("JSON literal");
         }

         this.pos = this.pos + text1.length();
      }

      String string() {
         if (this.next() != '"') {
            throw new IllegalArgumentException("JSON quote");
         }

         StringBuilder builder1 = new StringBuilder();

         while (true) {
            char char2 = this.next();
            if (char2 == '"') {
               return builder1.toString();
            }

            if (char2 != '\\') {
               builder1.append(char2);
            } else {
               char2 = this.next();
               switch (char2) {
                  case 'b':
                     builder1.append('\b');
                     break;
                  case 'c':
                  case 'd':
                  case 'e':
                  case 'g':
                  case 'h':
                  case 'i':
                  case 'j':
                  case 'k':
                  case 'l':
                  case 'm':
                  case 'o':
                  case 'p':
                  case 'q':
                  case 's':
                  default:
                     builder1.append(char2);
                     break;
                  case 'f':
                     builder1.append('\f');
                     break;
                  case 'n':
                     builder1.append('\n');
                     break;
                  case 'r':
                     builder1.append('\r');
                     break;
                  case 't':
                     builder1.append('\t');
                     break;
                  case 'u':
                     int number3 = Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16);
                     builder1.append((char)number3);
                     this.pos += 4;
               }
            }
         }
      }

      static void write(Object value0, StringBuilder builder1) {
         if (value0 == null) {
            builder1.append("null");
         } else if (value0 instanceof String text7) {
            builder1.append('"');

            for (int number9 = 0; number9 < text7.length(); number9++) {
               char char11 = text7.charAt(number9);
               switch (char11) {
                  case '\t':
                     builder1.append("\\t");
                     break;
                  case '\n':
                     builder1.append("\\n");
                     break;
                  case '\r':
                     builder1.append("\\r");
                     break;
                  case '"':
                     builder1.append("\\\"");
                     break;
                  case '\\':
                     builder1.append("\\\\");
                     break;
                  default:
                     if (char11 < ' ') {
                        builder1.append(String.format("\\u%04x", Integer.valueOf(char11)));
                     } else {
                        builder1.append(char11);
                     }
               }
            }

            builder1.append('"');
         } else if (value0 instanceof Map mapping6) {
            builder1.append('{');
            boolean flag8 = true;

            for (var value12 : ((Map<?, ?>) mapping6).entrySet()) {
               if (!flag8) {
                  builder1.append(',');
               }

               flag8 = false;
               write(String.valueOf(value12.getKey()), builder1);
               builder1.append(':');
               write(value12.getValue(), builder1);
            }

            builder1.append('}');
         } else if (value0 instanceof Collection collection2) {
            builder1.append('[');
            boolean flag3 = true;

            for (Object value5 : collection2) {
               if (!flag3) {
                  builder1.append(',');
               }

               flag3 = false;
               write(value5, builder1);
            }

            builder1.append(']');
         } else {
            builder1.append(String.valueOf(value0));
         }
      }
   }

   private record Region(BedrockAnimationBridge.Frames frames, int x, int y) {
   }
}
