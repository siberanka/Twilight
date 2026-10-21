package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class FirstPersonSwingBridge {
   private FirstPersonSwingBridge() {
   }

   public static void install(Map<String, byte[]> packFiles0, JsonObject jsonObject1) {
      try {
         JsonObject jsonObject2 = jsonObject1.getAsJsonObject("items");
         if (jsonObject2 != null) {
            ArrayList<String> arrayListString3 = new ArrayList<>();

            for (var value5 : jsonObject2.entrySet()) {
               if (((JsonElement)value5.getValue()).isJsonArray()) {
                  for (JsonElement jsonElement7 : ((JsonElement)value5.getValue()).getAsJsonArray()) {
                     if (jsonElement7.isJsonObject()) {
                        JsonObject jsonObject8 = jsonElement7.getAsJsonObject();
                        JsonObject jsonObject9 = jsonObject8.getAsJsonObject("bedrock_options");
                        if (jsonObject9 != null && jsonObject9.has("display_handheld") && jsonObject9.get("display_handheld").getAsBoolean() && jsonObject8.has("bedrock_identifier")) {
                           String bedrockIdentifier10 = jsonObject8.get("bedrock_identifier").getAsString();
                           int separatorIndex11 = bedrockIdentifier10.indexOf(58);
                           if (separatorIndex11 >= 1 && separatorIndex11 != bedrockIdentifier10.length() - 1 && bedrockIdentifier10.matches("[a-z0-9_:.\\-]+")) {
                              String suffix12 = bedrockIdentifier10.substring(separatorIndex11 + 1);
                              byte[] resourceBytes13 = (byte[])packFiles0.get("models/entity/geometry." + suffix12 + ".geo.json");
                              if (resourceBytes13 != null) {
                                 String text14 = new String(resourceBytes13, StandardCharsets.UTF_8);
                                 if (text14.contains("\"bone_basis\"")) {
                                    arrayListString3.add(bedrockIdentifier10);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (!arrayListString3.isEmpty()) {
               var value19 = arrayListString3.stream().distinct().sorted().toList();
               StringBuilder builder20 = new StringBuilder("q.is_item_name_any('slot.weapon.mainhand', 0");

               for (String text23 : value19) {
                  builder20.append(", '").append(text23).append("'");
               }

               builder20.append(')');
               String text22 = builder20.toString();

               JsonObject jsonObject24;
               try (InputStream inputStream25 = FirstPersonSwingBridge.class.getResourceAsStream("/twilight/swing_reference.json")) {
                  if (inputStream25 == null) {
                     return;
                  }

                  jsonObject24 = JsonParser.parseString(new String(inputStream25.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
               }

               JsonObject jsonObject26 = jsonObject24.getAsJsonObject("reference");
               JsonObject jsonObject27 = jsonObject24.getAsJsonObject("vanilla");
               JsonObject jsonObject28 = new JsonObject();

               for (String text31 : List.of("position", "rotation")) {
                  JsonArray jsonArray33 = new JsonArray();
                  JsonArray jsonArray35 = jsonObject26.getAsJsonArray(text31);
                  JsonArray jsonArray37 = jsonObject27.getAsJsonArray(text31);

                  for (int number15 = 0; number15 < 3; number15++) {
                     jsonArray33.add("(" + text22 + ") ? (" + jsonArray35.get(number15).getAsString() + ") : (" + jsonArray37.get(number15).getAsString() + ")");
                  }

                  jsonObject28.add(text31, jsonArray33);
               }

               JsonObject jsonObject30 = new JsonObject();
               jsonObject30.add("rightarm", jsonObject28);
               JsonObject jsonObject32 = new JsonObject();
               jsonObject32.addProperty("loop", true);
               jsonObject32.add("bones", jsonObject30);
               JsonObject jsonObject34 = new JsonObject();
               jsonObject34.add("animation.player.first_person.attack_rotation", jsonObject32);
               JsonObject jsonObject36 = new JsonObject();
               jsonObject36.addProperty("format_version", "1.8.0");
               jsonObject36.add("animations", jsonObject34);
               packFiles0.put("animations/twilight_first_person_swing.animation.json", jsonObject36.toString().getBytes(StandardCharsets.UTF_8));
            }
         }
      } catch (Exception error18) {
      }
   }
}
