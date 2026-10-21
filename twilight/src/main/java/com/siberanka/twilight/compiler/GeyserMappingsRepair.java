package com.siberanka.twilight.compiler;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GeyserMappingsRepair {
   private GeyserMappingsRepair() {
   }

   public static JsonObject clean(JsonObject jsonObject0) {
      byte[] resourceBytes1 = jsonObject0.toString().getBytes(StandardCharsets.UTF_8);
      HashMap lookup2 = new HashMap<>();
      lookup2.put("twilight/geyser_item_mappings.json", resourceBytes1);
      BedrockAnimationBridge.sanitizeGeyserMappings(lookup2);
      byte[] resourceBytes3 = (byte[])lookup2.get("twilight/geyser_item_mappings.json");
      if (Arrays.equals(resourceBytes1, resourceBytes3)) {
         return jsonObject0;
      }

      try {
         Class class4 = Class.forName("com.google.gson.JsonParser");
         Object value5 = class4.getMethod("parseString", String.class).invoke(null, new String(resourceBytes3, StandardCharsets.UTF_8));
         Method method6 = value5.getClass().getMethod("getAsJsonObject");
         return (JsonObject)method6.invoke(value5);
      } catch (ReflectiveOperationException error7) {
         throw new IllegalStateException("Cannot rebuild sanitized Geyser mappings", error7);
      }
   }

   public static int validatedDefinitionCount(JsonObject jsonObject0, int number1) {
      var jsonObject2 = BedrockAnimationBridge.obj(BedrockAnimationBridge.Json.read(jsonObject0.toString().getBytes(StandardCharsets.UTF_8)));
      var itemDefinitions3 = BedrockAnimationBridge.obj(jsonObject2.get("items"));
      int number4 = 0;

      for (Object value6 : itemDefinitions3.values()) {
         number4 += ((List)value6).size();
      }

      if (number4 > number1) {
         throw new IllegalStateException("Generated more mappings than converted items");
      } else {
         return number4;
      }
   }
}
