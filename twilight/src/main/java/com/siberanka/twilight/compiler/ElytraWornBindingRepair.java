package com.siberanka.twilight.compiler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ElytraWornBindingRepair {
   private static final String ELYTRA = "attachables/twilight_vanilla_elytra.json";
   private static final String REGISTRY = "twilight/elytra-wearer-variants.json";
   private static final String MAPPINGS = "twilight/geyser_item_mappings.json";
   private static final String CONTROLLER = "render_controllers/twilight_elytra_variant.json";

   private ElytraWornBindingRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null
         && packFiles0.containsKey("attachables/twilight_vanilla_elytra.json")
         && packFiles0.containsKey("twilight/elytra-wearer-variants.json")
         && packFiles0.containsKey("twilight/geyser_item_mappings.json")
         && packFiles0.containsKey("render_controllers/twilight_elytra_variant.json")) {
         Map<String, Object> mapping1;
         Map<String, Object> mapping2;
         Map<String, Object> mapping3;
         try {
            mapping1 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/elytra-wearer-variants.json")));
            mapping2 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/geyser_item_mappings.json")));
            mapping3 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("attachables/twilight_vanilla_elytra.json")));
         } catch (RuntimeException error22) {
            return 0;
         }

         var attachable4 = obj(obj(mapping3.get("minecraft:attachable")).get("description"));
         if (!"minecraft:elytra".equals(attachable4.get("identifier"))) {
            return 0;
         }

         if (!"geometry.elytra".equals(obj(attachable4.get("geometry")).get("default"))) {
            return 0;
         }

         if (!"controller.animation.elytra.default".equals(obj(attachable4.get("animations")).get("default_controller"))) {
            return 0;
         }

         LinkedHashMap lookup5 = new LinkedHashMap<>();

         for (Object entry7 : list(mapping1.get("variants"))) {
            var jsonObject8 = obj(entry7);
            String modelName9 = str(jsonObject8.get("item_model"));
            if (modelName9 != null && jsonObject8.get("variant") instanceof Number number11 && number11.intValue() > 0 && number11.intValue() <= 255) {
               lookup5.put(modelName9, number11.intValue());
            }
         }

         if (lookup5.isEmpty()) {
            return 0;
         }

         int number23 = 0;

         for (Object entry26 : list(obj(mapping2.get("items")).get("minecraft:elytra"))) {
            var jsonObject28 = obj(entry26);
            String modelName30 = str(jsonObject28.get("model"));
            String identifier32 = str(jsonObject28.get("bedrock_identifier"));
            Integer number12 = (Integer)lookup5.get(modelName30);
            if (number12 != null && identifier32 != null && identifier32.startsWith("twilight:")) {
               String texturePath13 = "textures/twilight/elytra/wings_" + number12;
               if (packFiles0.containsKey(texturePath13 + ".png")) {
                  var value14 = deepCopy(mapping3);
                  var attachable15 = obj(obj(value14.get("minecraft:attachable")).get("description"));
                  attachable15.put("identifier", identifier32);
                  var scripts16 = obj(attachable15.get("scripts"));
                  ArrayList entries17 = new ArrayList<>(list(scripts16.get("pre_animation")));
                  entries17.removeIf(var0x -> var0x instanceof String var1x && var1x.contains("twilight_elytra_worn_variant"));
                  if (entries17.isEmpty()) {
                     scripts16.remove("pre_animation");
                  } else {
                     scripts16.put("pre_animation", entries17);
                  }

                  var textures18 = obj(attachable15.get("textures"));
                  textures18.put("default", texturePath13);
                  attachable15.put("render_controllers", List.of("controller.render.armor"));
                  String attachablePath19 = "attachables/te_" + sha(identifier32) + ".json";
                  byte[] resourceBytes20 = BedrockAnimationBridge.Json.bytes(value14);
                  if (!Arrays.equals(resourceBytes20, (byte[])packFiles0.get(attachablePath19))) {
                     packFiles0.put(attachablePath19, resourceBytes20);
                     number23++;
                  }
               }
            }
         }

         var scripts25 = obj(attachable4.get("scripts"));
         ArrayList entries27 = new ArrayList<>(list(scripts25.get("pre_animation")));
         String text29 = "v.twilight_elytra_worn_variant";
         String text31 = text29 + " = math.clamp(c.owning_entity->q.variant, 0.0, " + lookup5.size() + ".0);";
         if (!entries27.contains(text31)) {
            entries27.add(text31);
            scripts25.put("pre_animation", entries27);
            byte[] resourceBytes33 = BedrockAnimationBridge.Json.bytes(mapping3);
            if (!Arrays.equals(resourceBytes33, (byte[])packFiles0.get("attachables/twilight_vanilla_elytra.json"))) {
               packFiles0.put("attachables/twilight_vanilla_elytra.json", resourceBytes33);
               number23++;
            }
         }

         Map<String, Object> mapping34;
         try {
            mapping34 = obj(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("render_controllers/twilight_elytra_variant.json")));
         } catch (RuntimeException error21) {
            return number23;
         }

         var renderControllers35 = obj(mapping34.get("render_controllers"));
         var jsonObject36 = obj(renderControllers35.get("controller.render.twilight_elytra_variant"));
         if (!jsonObject36.isEmpty()) {
            var textures37 = list(jsonObject36.get("textures"));
            var value38 = List.of("Array.elytra_skins[" + text29 + "]", "Texture.enchanted");
            if (!textures37.equals(value38)) {
               jsonObject36.put("textures", value38);
               jsonObject36.put("materials", List.of(Map.of("*", "v.is_enchanted ? Material.enchanted : Material.default")));
               byte[] resourceBytes39 = BedrockAnimationBridge.Json.bytes(mapping34);
               if (!Arrays.equals(resourceBytes39, (byte[])packFiles0.get("render_controllers/twilight_elytra_variant.json"))) {
                  packFiles0.put("render_controllers/twilight_elytra_variant.json", resourceBytes39);
                  number23++;
               }
            }
         }

         return number23;
      } else {
         return 0;
      }
   }

   private static Map<String, Object> deepCopy(Map<String, Object> mapping0) {
      return obj(BedrockAnimationBridge.Json.read(BedrockAnimationBridge.Json.bytes(mapping0)));
   }

   private static String sha(String text0) {
      try {
         byte[] resourceBytes1 = MessageDigest.getInstance("SHA-256").digest(text0.getBytes(StandardCharsets.UTF_8));
         StringBuilder builder2 = new StringBuilder();

         for (int number3 = 0; number3 < 7; number3++) {
            builder2.append(String.format("%02x", resourceBytes1[number3] & 255));
         }

         return builder2.toString();
      } catch (Exception error4) {
         throw new IllegalStateException(error4);
      }
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }
}
