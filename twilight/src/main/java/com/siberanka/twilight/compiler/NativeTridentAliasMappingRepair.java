package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NativeTridentAliasMappingRepair {
   private NativeTridentAliasMappingRepair() {
   }

   public static int apply(Map<String, byte[]> packFiles0, ResourceIndex resourceIndex1) {
      return resourceIndex1 == null ? 0 : apply(packFiles0, var1x -> {
         var value2 = resourceIndex1.find(var1x);
         if (value2.isEmpty()) {
            for (String text4 : resourceIndex1.paths()) {
               if (text4.endsWith("/" + var1x)) {
                  value2 = resourceIndex1.find(text4);
                  if (value2.isPresent()) {
                     break;
                  }
               }
            }
         }

         return value2.isPresent() ? ((ResourceIndex.Asset)value2.get()).readBytes() : null;
      });
   }

   public static int apply(Map<String, byte[]> packFiles0, NativeTridentAliasMappingRepair.AssetReader nativeTridentAliasMappingRepairAssetReader1) {
      if (packFiles0 != null && nativeTridentAliasMappingRepairAssetReader1 != null) {
         String text2 = "twilight/geyser_item_mappings.json";
         byte[] resourceBytes3 = (byte[])packFiles0.get(text2);
         if (resourceBytes3 == null) {
            return 0;
         }

         Map<String, Object> mapping4;
         try {
            mapping4 = obj(BedrockAnimationBridge.Json.read(resourceBytes3));
         } catch (RuntimeException error15) {
            return 0;
         }

         var itemDefinitions5 = obj(mapping4.get("items"));
         var value6 = arr(itemDefinitions5.get("minecraft:trident"));
         if (value6.isEmpty()) {
            return 0;
         }

         var value7 = findStockTridentModel(nativeTridentAliasMappingRepairAssetReader1);
         if (value7 == null) {
            return 0;
         }

         int number8 = 0;
         Iterator iterator9 = value6.iterator();

         while (iterator9.hasNext()) {
            var jsonObject10 = obj(iterator9.next());
            if ("legacy".equals(jsonObject10.get("type"))) {
               String identifier11 = str(jsonObject10.get("bedrock_identifier"));
               if (identifier11 != null
                  && identifier11.startsWith("twilight:")
                  && jsonObject10.get("custom_model_data") instanceof Number number13
                  && isStockAlias(value7, number13.intValue())) {
                  String suffix14 = identifier11.substring(identifier11.indexOf(58) + 1);
                  if (!packFiles0.containsKey("models/entity/geometry." + suffix14 + ".geo.json")) {
                     iterator9.remove();
                     packFiles0.remove("attachables/" + suffix14 + ".json");
                     number8++;
                  }
               }
            }
         }

         if (number8 != 0) {
            packFiles0.put(text2, BedrockAnimationBridge.Json.bytes(mapping4));
         }

         return number8;
      } else {
         return 0;
      }
   }

   private static Map<String, Object> findStockTridentModel(NativeTridentAliasMappingRepair.AssetReader nativeTridentAliasMappingRepairAssetReader0) {
      try {
         byte[] resourceBytes1 = nativeTridentAliasMappingRepairAssetReader0.read("assets/minecraft/models/item/trident.json");
         if (resourceBytes1 == null) {
            return null;
         } else {
            var jsonObject2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
            String text3 = str(jsonObject2.get("parent"));
            String texturePath4 = str(obj(jsonObject2.get("textures")).get("layer0"));
            if (!"minecraft:item/generated".equals(text3) && !"item/generated".equals(text3)) {
               return null;
            } else if (!"_minecraft:item/trident".equals(texturePath4) && !"minecraft:item/trident".equals(texturePath4) && !"item/trident".equals(texturePath4)) {
               return null;
            } else {
               return arr(jsonObject2.get("elements")).isEmpty() && obj(jsonObject2.get("display")).isEmpty() ? jsonObject2 : null;
            }
         }
      } catch (Exception error5) {
         return null;
      }
   }

   private static boolean isStockAlias(Map<String, Object> mapping0, int number1) {
      for (Object value3 : arr(mapping0.get("overrides"))) {
         var jsonObject4 = obj(value3);
         var jsonObject5 = obj(jsonObject4.get("predicate"));
         if (jsonObject5.get("custom_model_data") instanceof Number number7 && number7.intValue() == number1 && !jsonObject5.containsKey("throwing")) {
            String modelName8 = str(jsonObject4.get("model"));
            if ("item/trident".equals(modelName8) || "minecraft:item/trident".equals(modelName8)) {
               return true;
            }
         }
      }

      return false;
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : new LinkedHashMap<>();
   }

   private static List<Object> arr(Object value0) {
      return value0 instanceof List entries1 ? entries1 : List.of();
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }

   @FunctionalInterface
   public interface AssetReader {
      byte[] read(String text1) throws IOException;
   }
}
