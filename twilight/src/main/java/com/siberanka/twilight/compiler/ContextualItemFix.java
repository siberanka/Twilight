package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ContextualItemFix {
   private ContextualItemFix() {
   }

   public static void apply(
      ResourceIndex resourceIndex0,
      JavaModelResolver javaModelResolver1,
      List<ItemCandidate> entries2,
      VanillaAssetCache vanillaAssetCache3,
      Map<String, byte[]> packFiles4,
      JsonObject jsonObject5,
      JsonObject jsonObject6,
      List<String> entries7
   ) {
      for (ItemCandidate itemCandidate9 : entries2) {
         try {
            var value10 = contextModels(resourceIndex0, itemCandidate9.mappingModel());
            if (!value10.isEmpty()) {
               ContextualItemFix.ContextModels contextualItemFixContextModels11 = (ContextualItemFix.ContextModels)value10.get();
               if (itemCandidate9.visualModels().contains(contextualItemFixContextModels11.handModel)) {
                  String text12 = BedrockPackCompiler.bedrockIdentifier(itemCandidate9);
                  String suffix13 = text12.substring(text12.indexOf(58) + 1);
                  String text14 = "twilight." + suffix13;
                  String texturePath15 = "textures/twilight/" + suffix13 + "_icon";
                  if (jsonObject5.has(text14) && jsonObject6.has(itemCandidate9.baseItem()) && hasMapping(jsonObject6.getAsJsonArray(itemCandidate9.baseItem()), text12)) {
                     ResolvedJavaModel resolvedJavaModel16 = javaModelResolver1.resolve(contextualItemFixContextModels11.guiModel, true);
                     byte[] resourceBytes17;
                     if (resolvedJavaModel16.isThreeDimensional()) {
                        TextureSet identifiers18 = TextureSet.atlas(resourceIndex0, List.copyOf(resolvedJavaModel16.textures().values()), vanillaAssetCache3);
                        resourceBytes17 = TextureSet.png(GuiIconRenderer.render(resolvedJavaModel16, identifiers18, BedrockPackCompiler.geometryElements(resolvedJavaModel16)));
                     } else {
                        var value23 = BedrockPackCompiler.flatLayers(resolvedJavaModel16);
                        if (value23.isEmpty()) {
                           continue;
                        }

                        resourceBytes17 = TextureSet.png(TextureSet.layeredIcon(resourceIndex0, value23, vanillaAssetCache3));
                     }

                     packFiles4.put(texturePath15 + ".png", resourceBytes17);
                     jsonObject5.getAsJsonObject(text14).addProperty("textures", texturePath15);

                     for (JsonElement jsonElement19 : jsonObject6.getAsJsonArray(itemCandidate9.baseItem())) {
                        if (jsonElement19.isJsonObject()) {
                           JsonObject jsonObject20 = jsonElement19.getAsJsonObject();
                           if (text12.equals(jsonObject20.get("bedrock_identifier").getAsString())) {
                              JsonObject jsonObject21 = jsonObject20.getAsJsonObject("bedrock_options");
                              if (jsonObject21 == null) {
                                 jsonObject21 = new JsonObject();
                                 jsonObject20.add("bedrock_options", jsonObject21);
                              }

                              jsonObject21.addProperty("display_handheld", true);
                           }
                        }
                     }
                  }
               }
            }
         } catch (IOException | RuntimeException error22) {
            entries7.add("Contextual GUI item " + itemCandidate9.mappingModel() + ": kept existing icon (" + error22.getMessage() + ")");
         }
      }
   }

   private static boolean hasMapping(JsonArray jsonArray0, String text1) {
      for (JsonElement jsonElement3 : jsonArray0) {
         if (jsonElement3.isJsonObject()
            && jsonElement3.getAsJsonObject().has("bedrock_identifier")
            && text1.equals(jsonElement3.getAsJsonObject().get("bedrock_identifier").getAsString())) {
            return true;
         }
      }

      return false;
   }

   private static Optional<ContextualItemFix.ContextModels> contextModels(ResourceIndex resourceIndex0, String text1) throws IOException {
      var value2 = resourceIndex0.find(JavaItemDefinitionResolver.itemPath(text1));
      if (value2.isEmpty()) {
         return Optional.empty();
      }

      JsonObject jsonObject3 = JsonParser.parseString(((ResourceIndex.Asset)value2.get()).readUtf8()).getAsJsonObject();
      return jsonObject3.has("model") && jsonObject3.get("model").isJsonObject()
         ? findContext(jsonObject3.getAsJsonObject("model"), JavaModelResolver.namespace(text1), 0)
         : Optional.empty();
   }

   private static Optional<ContextualItemFix.ContextModels> findContext(JsonObject jsonObject0, String text1, int number2) {
      if (number2 <= 32 && jsonObject0 != null) {
         String text3 = jsonObject0.has("type") ? jsonObject0.get("type").getAsString() : "minecraft:model";
         if (!text3.contains(":")) {
            text3 = "minecraft:" + text3;
         }

         if (text3.equals("minecraft:select") && jsonObject0.has("property")) {
            String text4 = jsonObject0.get("property").getAsString();
            if (text4.equals("display_context") || text4.equals("minecraft:display_context")) {
               JsonObject jsonObject5 = jsonObject0.getAsJsonObject("fallback");
               String text6 = leafModel(jsonObject5, text1, number2 + 1);
               JsonArray jsonArray7 = jsonObject0.getAsJsonArray("cases");
               if (text6 != null && jsonArray7 != null) {
                  for (JsonElement jsonElement9 : jsonArray7) {
                     JsonObject jsonObject10 = jsonElement9.getAsJsonObject();
                     if (jsonObject10.has("when") && includesGui(jsonObject10.get("when"))) {
                        String modelName11 = leafModel(jsonObject10.getAsJsonObject("model"), text1, number2 + 1);
                        if (modelName11 != null && !modelName11.equals(text6)) {
                           return Optional.of(new ContextualItemFix.ContextModels(modelName11, text6));
                        }
                     }
                  }
               }
            }
         }

         for (String text14 : List.of("fallback", "on_true", "on_false")) {
            if (jsonObject0.has(text14) && jsonObject0.get(text14).isJsonObject()) {
               var value16 = findContext(jsonObject0.getAsJsonObject(text14), text1, number2 + 1);
               if (value16.isPresent()) {
                  return value16;
               }
            }
         }

         for (String text15 : List.of("entries", "cases", "models")) {
            JsonArray jsonArray17 = jsonObject0.getAsJsonArray(text15);
            if (jsonArray17 != null) {
               for (JsonElement jsonElement19 : jsonArray17) {
                  if (jsonElement19.isJsonObject()) {
                     JsonObject jsonObject20 = jsonElement19.getAsJsonObject();
                     JsonObject jsonObject21 = jsonObject20.has("model") && jsonObject20.get("model").isJsonObject() ? jsonObject20.getAsJsonObject("model") : jsonObject20;
                     var value22 = findContext(jsonObject21, text1, number2 + 1);
                     if (value22.isPresent()) {
                        return value22;
                     }
                  }
               }
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   private static boolean includesGui(JsonElement jsonElement0) {
      if (jsonElement0.isJsonArray()) {
         for (JsonElement jsonElement2 : jsonElement0.getAsJsonArray()) {
            if ("gui".equals(jsonElement2.getAsString())) {
               return true;
            }
         }
      } else if (jsonElement0.isJsonPrimitive()) {
         return "gui".equals(jsonElement0.getAsString());
      }

      return false;
   }

   private static String leafModel(JsonObject jsonObject0, String text1, int number2) {
      if (jsonObject0 != null && number2 <= 32) {
         String text3 = jsonObject0.has("type") ? jsonObject0.get("type").getAsString() : "minecraft:model";
         if (!text3.contains(":")) {
            text3 = "minecraft:" + text3;
         }

         if (text3.equals("minecraft:model") && jsonObject0.has("model") && jsonObject0.get("model").isJsonPrimitive()) {
            return JavaModelResolver.qualified(jsonObject0.get("model").getAsString(), text1);
         }

         JsonObject jsonObject4 = jsonObject0.getAsJsonObject("fallback");
         return jsonObject4 == null ? null : leafModel(jsonObject4, text1, number2 + 1);
      } else {
         return null;
      }
   }

   private record ContextModels(String guiModel, String handModel) {
   }
}
