package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;

public final class JavaGuiIconSelectionBridge {
   private static final String MAPPING = "twilight/geyser_item_mappings.json";

   private JavaGuiIconSelectionBridge() {
   }

   public static int install(ResourceIndex resourceIndex0, Map<String, byte[]> packFiles1) {
      return apply(packFiles1, var1x -> resourceIndex0.find(var1x).map(var0xx -> {
         try {
            return var0xx.readBytes();
         } catch (IOException error2) {
            return null;
         }
      }).orElse(null));
   }

   static int apply(Map<String, byte[]> packFiles0, Function<String, byte[]> functionStringbyte1) {
      if (!packFiles0.containsKey("twilight/geyser_item_mappings.json")) {
         return 0;
      }

      Map mapping2;
      try {
         mapping2 = map(BedrockAnimationBridge.Json.read((byte[])packFiles0.get("twilight/geyser_item_mappings.json")));
      } catch (Exception error23) {
         return 0;
      }

      var itemDefinitions3 = map(mapping2.get("items"));
      if (itemDefinitions3 == null) {
         return 0;
      }

      int number4 = 0;

      for (var value6 : itemDefinitions3.entrySet()) {
         Object entry8 = value6.getValue();
         if (entry8 instanceof List) {
            for (Object entry9 : (List)entry8) {
               var value10 = map(entry9);
               if (value10 != null && "definition".equals(value10.get("type"))) {
                  String modelName11 = str(value10.get("model"));
                  String identifier12 = str(value10.get("bedrock_identifier"));
                  if (modelName11 != null && identifier12 != null && identifier12.startsWith("twilight:")) {
                     String suffix13 = identifier12.substring(identifier12.indexOf(58) + 1);
                     String texturePath14 = "textures/twilight/" + suffix13 + "_icon.png";
                     if (packFiles0.containsKey(texturePath14)) {
                        try {
                           byte[] resourceBytes15 = (byte[])functionStringbyte1.apply(JavaItemDefinitionResolver.itemPath(modelName11));
                           if (resourceBytes15 != null) {
                              var parsedJson16 = map(BedrockAnimationBridge.Json.read(resourceBytes15));
                              var value17 = map(parsedJson16.get("model"));
                              String text18 = findGuiModel(value17);
                              if (text18 != null) {
                                 String text19 = resolveFlatSprite(text18, functionStringbyte1, new HashSet<>(), 0);
                                 if (text19 != null) {
                                    String text20 = JavaModelResolver.texturePath(text19);
                                    byte[] resourceBytes21 = (byte[])functionStringbyte1.apply(text20);
                                    if (resourceBytes21 == null && text19.startsWith("_")) {
                                       resourceBytes21 = (byte[])functionStringbyte1.apply(JavaModelResolver.texturePath(text19.substring(1)));
                                    }

                                    if (resourceBytes21 != null && resourceBytes21.length >= 20 && (resourceBytes21[0] & 255) == 137 && !Arrays.equals(resourceBytes21, (byte[])packFiles0.get(texturePath14))) {
                                       packFiles0.put(texturePath14, resourceBytes21);
                                       number4++;
                                    }
                                 }
                              }
                           }
                        } catch (Exception error22) {
                        }
                     }
                  }
               }
            }
         }
      }

      return number4;
   }

   private static String findGuiModel(Map<String, Object> mapping0) {
      if (mapping0 == null) {
         return null;
      }

      String text1 = str(mapping0.get("type"));
      if (text1 != null && text1.endsWith("select") && "display_context".equals(stripNamespace(str(mapping0.get("property"))))) {
         Object value3 = mapping0.get("cases");
         if (value3 instanceof List) {
            for (Object value4 : (List)value3) {
               var value5 = map(value4);
               if (value5 != null) {
                  Object value6 = value5.get("when");
                  boolean flag7 = "gui".equals(value6) || value6 instanceof List entries8 && entries8.contains("gui");
                  if (flag7) {
                     return modelId(map(value5.get("model")));
                  }
               }
            }
         }
      }

      return null;
   }

   private static String modelId(Map<String, Object> mapping0) {
      if (mapping0 == null) {
         return null;
      }

      String text1 = str(mapping0.get("type"));
      return text1 != null && !text1.endsWith("model") ? null : str(mapping0.get("model"));
   }

   private static String resolveFlatSprite(String text0, Function<String, byte[]> functionStringbyte1, Set<String> identifiers2, int number3) {
      if (number3 <= 12 && identifiers2.add(text0)) {
         byte[] resourceBytes4 = (byte[])functionStringbyte1.apply(JavaModelResolver.modelPath(text0));
         if (resourceBytes4 == null) {
            return null;
         }

         var parsedJson5 = map(BedrockAnimationBridge.Json.read(resourceBytes4));
         if (parsedJson5 == null) {
            return null;
         }

         var textures6 = map(parsedJson5.get("textures"));
         String text7 = textures6 == null ? null : str(textures6.get("layer0"));
         String text8 = str(parsedJson5.get("parent"));
         if (text7 != null && !text7.startsWith("#")) {
            return qualify(text7, text0);
         }

         if (text7 != null && text7.startsWith("#") && textures6 != null) {
            String suffix9 = str(textures6.get(text7.substring(1)));
            if (suffix9 != null && !suffix9.startsWith("#")) {
               return qualify(suffix9, text0);
            }
         }

         return text8 != null && !text8.startsWith("builtin/") && !text8.startsWith("minecraft:builtin/")
            ? resolveFlatSprite(qualify(text8, text0), functionStringbyte1, identifiers2, number3 + 1)
            : null;
      } else {
         return null;
      }
   }

   private static String qualify(String text0, String text1) {
      return text0.indexOf(58) >= 0 ? text0 : text1.substring(0, text1.indexOf(58)) + ":" + text0;
   }

   private static String stripNamespace(String text0) {
      if (text0 == null) {
         return null;
      }

      int separatorIndex1 = text0.indexOf(58);
      return separatorIndex1 < 0 ? text0 : text0.substring(separatorIndex1 + 1);
   }

   private static Map<String, Object> map(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static String str(Object value0) {
      return value0 instanceof String text1 ? text1 : null;
   }
}
