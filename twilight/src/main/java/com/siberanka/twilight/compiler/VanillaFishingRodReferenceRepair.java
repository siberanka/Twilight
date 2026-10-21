package com.siberanka.twilight.compiler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.imageio.ImageIO;

public final class VanillaFishingRodReferenceRepair {
   private VanillaFishingRodReferenceRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && packFiles0.containsKey("twilight/geyser_item_mappings.json")) {
         Map<String, Object> mapping1;
         try {
            mapping1 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/geyser_item_mappings.json")));
         } catch (Exception error29) {
            return 0;
         }

         var itemDefinitions2 = list(obj(mapping1.get("items")).get("minecraft:fishing_rod"));
         LinkedHashMap<Integer, List<Rod>> lookup3 = new LinkedHashMap<>();

         for (Object entry5 : itemDefinitions2) {
            var jsonObject6 = obj(entry5);
            String identifier7 = str(jsonObject6.get("bedrock_identifier"));
            if (identifier7 != null && identifier7.startsWith("twilight:")) {
               String suffix8 = identifier7.substring(identifier7.indexOf(58) + 1);
               if (jsonObject6.get("custom_model_data") instanceof Number number10 && has16x16Texture(packFiles0, "textures/twilight/" + suffix8 + ".png")) {
                  var jsonObject11 = obj(jsonObject6.get("predicate"));
                  boolean flag12 = "fishing_rod_cast".equals(str(jsonObject11.get("property")));
                  lookup3.computeIfAbsent(number10.intValue(), var0x -> new ArrayList<>())
                     .add(new VanillaFishingRodReferenceRepair.Rod(identifier7, suffix8, number10.intValue(), flag12));
               }
            }
         }

         LinkedHashMap<Integer, List<Rod>> lookup31 = new LinkedHashMap<>();

         for (var value34 : lookup3.entrySet()) {
            List<Rod> entries38 = value34.getValue();
            if (entries38.size() == 2
               && ((VanillaFishingRodReferenceRepair.Rod)entries38.get(0)).cast != ((VanillaFishingRodReferenceRepair.Rod)entries38.get(1)).cast
               && !entries38.stream().anyMatch(var1x -> hasComplexExistingGeometry(packFiles0, var1x.stem))) {
               lookup31.put((Integer)value34.getKey(), entries38);
            }
         }

         if (lookup31.isEmpty()) {
            return 0;
         }

         VanillaFishingRodReferenceRepair.Template vanillaFishingRodReferenceRepairTemplate33 = findTemplate(packFiles0, lookup31);
         if (vanillaFishingRodReferenceRepairTemplate33 == null) {
            return 0;
         }

         int number35 = 0;

         label119:
         for (List<Rod> entries40 : lookup31.values()) {
            Iterator iterator41 = entries40.iterator();

            while (true) {
               String text13;
               Map<String, Object> mapping17;
               Map<String, Object> mapping18;
               Map<String, Object> mapping19;
               VanillaFishingRodReferenceRepair.Rod vanillaFishingRodReferenceRepairRod42;
               String text43;
               String text44;
               while (true) {
                  if (!iterator41.hasNext()) {
                     continue label119;
                  }

                  vanillaFishingRodReferenceRepairRod42 = (VanillaFishingRodReferenceRepair.Rod)iterator41.next();
                  text43 = "attachables/" + vanillaFishingRodReferenceRepairRod42.stem + ".json";
                  text44 = "models/entity/geometry." + vanillaFishingRodReferenceRepairRod42.stem + ".geo.json";
                  text13 = "animations/" + vanillaFishingRodReferenceRepairRod42.stem + ".animation.json";
                  byte[] resourceBytes14 = (byte[])packFiles0.get(text43);
                  if (resourceBytes14 == null) {
                     resourceBytes14 = substitute(vanillaFishingRodReferenceRepairTemplate33.attachable, vanillaFishingRodReferenceRepairTemplate33.stem, vanillaFishingRodReferenceRepairRod42.stem);
                  }

                  byte[] resourceBytes15 = (byte[])packFiles0.get(text44);
                  if (resourceBytes15 == null) {
                     resourceBytes15 = substitute(vanillaFishingRodReferenceRepairTemplate33.geometry, vanillaFishingRodReferenceRepairTemplate33.stem, vanillaFishingRodReferenceRepairRod42.stem);
                  }

                  byte[] resourceBytes16 = (byte[])packFiles0.get(text13);
                  if (resourceBytes16 == null) {
                     resourceBytes16 = substitute(vanillaFishingRodReferenceRepairTemplate33.animation, vanillaFishingRodReferenceRepairTemplate33.stem, vanillaFishingRodReferenceRepairRod42.stem);
                  }

                  if (isFlatSingleQuad(resourceBytes15)) {
                     try {
                        mapping17 = obj(BedrockAnimationBridge.Json.read(resourceBytes14));
                        mapping18 = obj(BedrockAnimationBridge.Json.read(resourceBytes15));
                        mapping19 = obj(BedrockAnimationBridge.Json.read(resourceBytes16));
                        break;
                     } catch (RuntimeException error30) {
                     }
                  }
               }

               var attachable20 = obj(obj(mapping17.get("minecraft:attachable")).get("description"));
               if (vanillaFishingRodReferenceRepairRod42.id.equals(attachable20.get("identifier"))) {
                  var animations21 = obj(attachable20.get("animations"));
                  var animations22 = obj(mapping19.get("animations"));
                  if (hasFiveAliases(animations21, animations22)) {
                     var value23 = list(mapping18.get("minecraft:geometry"));
                     if (value23.size() == 1) {
                        var jsonObject24 = obj(value23.get(0));
                        var description25 = obj(jsonObject24.get("description"));
                        if (Objects.equals(description25.get("identifier"), obj(attachable20.get("geometry")).get("default"))) {
                           jsonObject24.put("bones", referenceBones());
                           description25.put("texture_width", 16);
                           description25.put("texture_height", 16);
                           description25.put("visible_bounds_width", 4);
                           description25.put("visible_bounds_height", 4.5);
                           description25.put("visible_bounds_offset", List.of(0, 0.75, 0));
                           mapping18.put("format_version", "1.21.0");

                           for (String text27 : List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head")) {
                              var jsonObject28 = obj(animations22.get(str(animations21.get(text27))));
                              jsonObject28.put("loop", true);
                              jsonObject28.put("bones", referencePose(text27));
                           }

                           number35 += putIfDifferent(packFiles0, text43, BedrockAnimationBridge.Json.bytes(mapping17));
                           number35 += putIfDifferent(packFiles0, text44, BedrockAnimationBridge.Json.bytes(mapping18));
                           number35 += putIfDifferent(packFiles0, text13, BedrockAnimationBridge.Json.bytes(mapping19));
                        }
                     }
                  }
               }
            }
         }

         return number35;
      } else {
         return 0;
      }
   }

   private static VanillaFishingRodReferenceRepair.Template findTemplate(
      Map<String, byte[]> packFiles0, Map<Integer, List<VanillaFishingRodReferenceRepair.Rod>> mapping1
   ) {
      for (List<Rod> entries3 : mapping1.values()) {
         for (VanillaFishingRodReferenceRepair.Rod vanillaFishingRodReferenceRepairRod5 : entries3) {
            byte[] resourceBytes6 = (byte[])packFiles0.get("attachables/" + vanillaFishingRodReferenceRepairRod5.stem + ".json");
            byte[] resourceBytes7 = (byte[])packFiles0.get("models/entity/geometry." + vanillaFishingRodReferenceRepairRod5.stem + ".geo.json");
            byte[] resourceBytes8 = (byte[])packFiles0.get("animations/" + vanillaFishingRodReferenceRepairRod5.stem + ".animation.json");
            if (resourceBytes6 != null && resourceBytes7 != null && resourceBytes8 != null && isFlatSingleQuad(resourceBytes7)) {
               var attachable9 = obj(obj(BedrockAnimationBridge.Json.read(resourceBytes6)).get("minecraft:attachable"));
               if (vanillaFishingRodReferenceRepairRod5.id.equals(obj(attachable9.get("description")).get("identifier"))) {
                  return new VanillaFishingRodReferenceRepair.Template(vanillaFishingRodReferenceRepairRod5.stem, resourceBytes6, resourceBytes7, resourceBytes8);
               }
            }
         }
      }

      return null;
   }

   private static List<Object> referenceBones() {
      LinkedHashMap lookup0 = new LinkedHashMap<>();
      lookup0.put("name", "s_mc");
      lookup0.put("binding", "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)");
      lookup0.put("pivot", List.of(0, 8, 0));
      LinkedHashMap lookup1 = new LinkedHashMap<>();
      lookup1.put("name", "s_mc_x");
      lookup1.put("parent", "s_mc");
      lookup1.put("pivot", List.of(0, 8, 0));
      LinkedHashMap lookup2 = new LinkedHashMap<>();
      lookup2.put("name", "s_mc_y");
      lookup2.put("parent", "s_mc_x");
      lookup2.put("pivot", List.of(0, 8, 0));
      LinkedHashMap lookup3 = new LinkedHashMap<>();
      lookup3.put("name", "s_mc_z");
      lookup3.put("parent", "s_mc_y");
      lookup3.put("pivot", List.of(0, 8, 0));
      lookup3.put(
         "texture_meshes",
         List.of(
            Map.of(
               "texture",
               "default",
               "position",
               List.<Integer>of(0, 8, 0),
               "rotation",
               List.<Integer>of(90, 0, -180),
               "local_pivot",
               List.of(8, 0.5, 8)
            )
         )
      );
      return List.of(lookup0, lookup1, lookup2, lookup3);
   }

   private static Map<String, Object> referencePose(String text0) {
      LinkedHashMap lookup1 = new LinkedHashMap<>();
      LinkedHashMap lookup2 = new LinkedHashMap<>();
      LinkedHashMap lookup3 = new LinkedHashMap<>();
      LinkedHashMap lookup4 = new LinkedHashMap<>();
      LinkedHashMap lookup5 = new LinkedHashMap<>();
      switch (text0) {
         case "first_person_right":
         case "first_person_left":
            boolean flag8 = text0.equals("first_person_right");
            lookup2.put("rotation", flag8 ? List.of(90, 60, -40) : List.of(180, 0, 180));
            lookup2.put("position", flag8 ? List.of(0, 15, 2) : List.of(-18.5, 18.3, 15));
            lookup2.put("scale", flag8 ? 1.4 : 1.2);
            lookup3.put("position", List.of(0, 1.6, 0.8));
            lookup3.put("scale", 0.68);
            lookup4.put("rotation", List.of(0, flag8 ? -90 : -90, 0));
            lookup5.put("rotation", List.of(0, 0, 25));
            break;
         case "third_person_right":
         case "third_person_left":
            lookup2.put("rotation", List.of(90, 0, 0));
            lookup2.put("position", List.of(0, 13, -3));
            lookup3.put("position", List.of(0, 4, 2.5));
            lookup3.put("scale", 0.85);
            lookup4.put("rotation", List.of(0, -90, 0));
            lookup5.put("rotation", List.of(0, 0, 55));
            break;
         case "head":
            lookup2.put("position", List.of(0, 19.9, 0));
            lookup3.put("scale", 0.625);
      }

      lookup1.put("s_mc", lookup2);
      lookup1.put("s_mc_x", lookup3);
      lookup1.put("s_mc_y", lookup4);
      lookup1.put("s_mc_z", lookup5);
      return lookup1;
   }

   private static boolean hasFiveAliases(Map<String, Object> mapping0, Map<String, Object> mapping1) {
      for (String text3 : List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head")) {
         if (!mapping1.containsKey(str(mapping0.get(text3)))) {
            return false;
         }
      }

      return true;
   }

   private static boolean hasComplexExistingGeometry(Map<String, byte[]> packFiles0, String text1) {
      for (String text3 : packFiles0.keySet()) {
         if (text3.startsWith("textures/twilight/" + text1 + "_java_anim_")) {
            return true;
         }
      }

      byte[] resourceBytes4 = (byte[])packFiles0.get("models/entity/geometry." + text1 + ".geo.json");
      return resourceBytes4 != null && !isFlatSingleQuad(resourceBytes4);
   }

   private static boolean isFlatSingleQuad(byte[] resourceBytes0) {
      try {
         var jsonObject1 = list(obj(BedrockAnimationBridge.Json.read(resourceBytes0)).get("minecraft:geometry"));
         if (jsonObject1.size() != 1) {
            return false;
         }

         var jsonObject2 = list(obj(jsonObject1.get(0)).get("bones"));
         if (jsonObject2.size() != 1) {
            return false;
         }

         var jsonObject3 = list(obj(jsonObject2.get(0)).get("cubes"));
         if (jsonObject3.size() != 1) {
            return false;
         }

         var jsonObject4 = list(obj(jsonObject3.get(0)).get("size"));
         return jsonObject4.size() == 3 && num(jsonObject4.get(0)) >= 12.0 && num(jsonObject4.get(1)) >= 12.0 && num(jsonObject4.get(2)) < 0.05;
      } catch (RuntimeException error5) {
         return false;
      }
   }

   private static boolean has16x16Texture(Map<String, byte[]> packFiles0, String text1) {
      try {
         byte[] resourceBytes2 = (byte[])packFiles0.get(text1);
         if (resourceBytes2 == null) {
            return false;
         }

         BufferedImage image3 = ImageIO.read(new ByteArrayInputStream(resourceBytes2));
         return image3 != null && image3.getWidth() == 16 && image3.getHeight() == 16;
      } catch (Exception error4) {
         return false;
      }
   }

   private static int putIfDifferent(Map<String, byte[]> packFiles0, String text1, byte[] resourceBytes2) {
      if (Arrays.equals((byte[])packFiles0.get(text1), resourceBytes2)) {
         return 0;
      }

      packFiles0.put(text1, resourceBytes2);
      return 1;
   }

   private static byte[] substitute(byte[] resourceBytes0, String text1, String text2) {
      return new String(resourceBytes0, StandardCharsets.UTF_8).replace(text1, text2).getBytes(StandardCharsets.UTF_8);
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Collections.emptyMap();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : Collections.emptyList();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static double num(Object value0) {
      return value0 instanceof Number number1 ? number1.doubleValue() : Double.NaN;
   }

   private record Rod(String id, String stem, int cmd, boolean cast) {
   }

   private record Template(String stem, byte[] attachable, byte[] geometry, byte[] animation) {
   }
}
