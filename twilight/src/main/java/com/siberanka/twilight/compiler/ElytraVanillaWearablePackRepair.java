package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public final class ElytraVanillaWearablePackRepair {
   private static final String MARKER = "twilight/elytra-wearer-variants.json";

   private ElytraVanillaWearablePackRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0, ResourceIndex resourceIndex1) {
      if (packFiles0 != null && resourceIndex1 != null && !packFiles0.containsKey("twilight/elytra-wearer-variants.json")) {
         byte[] resourceBytes2 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
         if (resourceBytes2 == null) {
            return 0;
         }

         Map<String, Object> mapping3;
         try {
            mapping3 = obj(BedrockAnimationBridge.Json.read(resourceBytes2));
         } catch (RuntimeException error25) {
            return 0;
         }

         var itemDefinitions4 = list(obj(mapping3.get("items")).get("minecraft:elytra"));
         if (itemDefinitions4.isEmpty()) {
            return 0;
         }

         TreeSet identifiers5 = new TreeSet<>();

         for (Object entry7 : itemDefinitions4) {
            String modelName8 = str(obj(entry7).get("model"));
            if (modelName8 != null && !modelName8.isBlank()) {
               identifiers5.add(modelName8);
            }
         }

         if (identifiers5.isEmpty()) {
            return 0;
         }

         LinkedHashMap lookup27 = new LinkedHashMap<>();
         lookup27.put("default", "textures/models/armor/elytra");
         lookup27.put("enchanted", "textures/misc/enchanted_actor_glint");
         ArrayList entries28 = new ArrayList<>();
         entries28.add("Texture.default");
         ArrayList entries29 = new ArrayList<>();
         LinkedHashMap lookup9 = new LinkedHashMap<>();
         int number10 = 0;
         Iterator iterator11 = identifiers5.iterator();

         while (true) {
            String text12;
            String text17;
            byte[] resourceBytes37;
            while (true) {
               if (!iterator11.hasNext()) {
                  if (number10 == 0) {
                     return 0;
                  }

                  LinkedHashMap lookup30 = new LinkedHashMap<>();
                  lookup30.put("identifier", "minecraft:elytra");
                  lookup30.put("materials", Map.of("default", "elytra", "enchanted", "elytra_glint"));
                  lookup30.put("textures", lookup27);
                  lookup30.put("geometry", Map.of("default", "geometry.elytra"));
                  lookup30.put(
                     "animations",
                     Map.of(
                        "default_controller",
                        "controller.animation.elytra.default",
                        "default",
                        "animation.elytra.default",
                        "gliding",
                        "animation.elytra.gliding",
                        "sneaking",
                        "animation.elytra.sneaking",
                        "sleeping",
                        "animation.elytra.sleeping",
                        "swimming",
                        "animation.elytra.swimming"
                     )
                  );
                  lookup30.put("scripts", Map.of("parent_setup", "variable.chest_layer_visible = 0.0;", "animate", List.<String>of("default_controller")));
                  lookup30.put("render_controllers", List.of("controller.render.twilight_elytra_variant"));
                  LinkedHashMap lookup31 = new LinkedHashMap<>();
                  lookup31.put("format_version", "1.10.0");
                  lookup31.put("minecraft:attachable", Map.of("description", lookup30));
                  LinkedHashMap lookup32 = new LinkedHashMap<>();
                  lookup32.put("arrays", Map.of("textures", Map.of("Array.elytra_skins", entries28)));
                  lookup32.put("geometry", "Geometry.default");
                  lookup32.put("materials", List.of(Map.of("*", "Material.default")));
                  lookup32.put("textures", List.of("Array.elytra_skins[math.clamp(context.owning_entity->query.variant, 0, " + number10 + ")]"));
                  var renderControllers33 = Map.of(
                     "format_version", "1.8.0", "render_controllers", Map.<String, LinkedHashMap>of("controller.render.twilight_elytra_variant", lookup32)
                  );
                  LinkedHashMap lookup34 = new LinkedHashMap<>();
                  lookup34.put("version", 1);
                  lookup34.put("default_variant", 0);
                  lookup34.put("variants", entries29);
                  lookup34.put("behavior", "vanilla_elytra_while_equipped");
                  packFiles0.putAll(lookup9);
                  packFiles0.put("attachables/twilight_vanilla_elytra.json", BedrockAnimationBridge.Json.bytes(lookup31));
                  packFiles0.put("render_controllers/twilight_elytra_variant.json", BedrockAnimationBridge.Json.bytes(renderControllers33));
                  packFiles0.put("twilight/elytra-wearer-variants.json", BedrockAnimationBridge.Json.bytes(lookup34));
                  return 3 + lookup9.size();
               }

               text12 = (String)iterator11.next();
               String[] segments13 = text12.split(":", 2);
               if (segments13.length == 2) {
                  String text14 = segments13[0];
                  String suffix15 = segments13[1].substring(segments13[1].lastIndexOf(47) + 1);
                  String text16 = "assets/" + text14 + "/equipment/" + suffix15 + ".json";
                  text17 = text14 + ":" + suffix15;

                  try {
                     var value18 = resourceIndex1.find(text16);
                     if (value18.isPresent()) {
                        var jsonObject19 = obj(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value18.get()).readBytes()));
                        var jsonObject20 = list(obj(jsonObject19.get("layers")).get("wings"));
                        if (!jsonObject20.isEmpty()) {
                           String texturePath21 = str(obj(jsonObject20.get(0)).get("texture"));
                           if (texturePath21 != null) {
                              text17 = texturePath21;
                           }
                        }
                     }
                  } catch (Exception error24) {
                  }

                  String[] segments35 = text17.split(":", 2);
                  if (segments35.length == 2) {
                     String texturePath36 = "assets/" + segments35[0] + "/textures/entity/equipment/wings/" + segments35[1] + ".png";

                     try {
                        var value38 = resourceIndex1.find(texturePath36);
                        if (!value38.isEmpty()) {
                           resourceBytes37 = ((ResourceIndex.Asset)value38.get()).readBytes();
                           break;
                        }
                     } catch (IOException error26) {
                     }
                  }
               }
            }

            if (resourceBytes37.length >= 24 && resourceBytes37[0] == -119 && resourceBytes37[1] == 80 && resourceBytes37[2] == 78 && resourceBytes37[3] == 71) {
               int number39 = (resourceBytes37[16] & 255) << 24 | (resourceBytes37[17] & 255) << 16 | (resourceBytes37[18] & 255) << 8 | resourceBytes37[19] & 255;
               int number22 = (resourceBytes37[20] & 255) << 24 | (resourceBytes37[21] & 255) << 16 | (resourceBytes37[22] & 255) << 8 | resourceBytes37[23] & 255;
               if (number39 == 64 && number22 == 32) {
                  String texturePath23 = "textures/twilight/elytra/wings_" + ++number10;
                  if (packFiles0.containsKey(texturePath23 + ".png")) {
                     return 0;
                  }

                  lookup9.put(texturePath23 + ".png", resourceBytes37);
                  lookup27.put("v" + number10, texturePath23);
                  entries28.add("Texture.v" + number10);
                  entries29.add(Map.of("variant", number10, "item_model", text12, "wings_source", text17));
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Map.of();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
