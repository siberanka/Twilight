package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ConvertedFirstPersonPatch {
   private ConvertedFirstPersonPatch() {
   }

   public static JsonObject rewriteGeometry(JsonObject jsonObject0, ResolvedJavaModel resolvedJavaModel1) {
      if (resolvedJavaModel1.isThreeDimensional() && resolvedJavaModel1.parts().isEmpty()) {
         try {
            JsonArray jsonArray2 = jsonObject0.getAsJsonArray("minecraft:geometry");
            if (jsonArray2 != null && jsonArray2.size() != 0) {
               JsonObject jsonObject3 = jsonArray2.get(0).getAsJsonObject();
               JsonArray jsonArray4 = jsonObject3.getAsJsonArray("bones");
               if (jsonArray4 != null && jsonArray4.size() == 1) {
                  JsonObject jsonObject5 = jsonArray4.get(0).getAsJsonObject();
                  if (!jsonObject5.has("cubes")) {
                     return jsonObject0;
                  }

                  JsonArray jsonArray6 = new JsonArray();
                  JsonElement jsonElement7 = jsonObject5.has("pivot") ? jsonObject5.get("pivot").deepCopy() : numberArray(0.0, 8.0, 0.0);
                  JsonElement jsonElement8 = jsonObject5.has("binding") ? jsonObject5.get("binding").deepCopy() : null;
                  JsonObject jsonObject9 = new JsonObject();
                  jsonObject9.addProperty("name", "bone");
                  if (jsonElement8 != null) {
                     jsonObject9.add("binding", jsonElement8);
                  }

                  jsonObject9.add("pivot", jsonElement7.deepCopy());
                  jsonArray6.add(jsonObject9);
                  JsonObject jsonObject10 = new JsonObject();
                  jsonObject10.addProperty("name", "bone_x");
                  jsonObject10.addProperty("parent", "bone");
                  jsonObject10.add("pivot", jsonElement7.deepCopy());
                  jsonArray6.add(jsonObject10);
                  JsonObject jsonObject11 = new JsonObject();
                  jsonObject11.addProperty("name", "bone_y");
                  jsonObject11.addProperty("parent", "bone_x");
                  jsonObject11.add("pivot", jsonElement7.deepCopy());
                  jsonArray6.add(jsonObject11);
                  JsonObject jsonObject12 = jsonObject5.deepCopy().getAsJsonObject();
                  jsonObject12.addProperty("name", "bone_z");
                  jsonObject12.addProperty("parent", "bone_y");
                  jsonObject12.remove("binding");
                  JsonElement jsonElement13 = jsonObject12.remove("cubes");
                  jsonArray6.add(jsonObject12);
                  JsonObject jsonObject14 = new JsonObject();
                  jsonObject14.addProperty("name", "bone_basis");
                  jsonObject14.addProperty("parent", "bone_z");
                  jsonObject14.add("pivot", jsonElement7.deepCopy());
                  jsonObject14.add("cubes", jsonElement13);
                  jsonArray6.add(jsonObject14);

                  for (int number15 = 1; number15 < jsonArray4.size(); number15++) {
                     jsonArray6.add(jsonArray4.get(number15).deepCopy());
                  }

                  jsonObject3.add("bones", jsonArray6);
                  return jsonObject0;
               } else {
                  return jsonObject0;
               }
            } else {
               return jsonObject0;
            }
         } catch (Throwable error16) {
            return jsonObject0;
         }
      } else {
         return jsonObject0;
      }
   }

   public static JsonObject rewriteAnimations(String text0, JsonObject jsonObject1, JsonObject jsonObject2) {
      try {
         JsonObject jsonObject3 = jsonObject2.getAsJsonObject("animations");
         if (jsonObject3 == null) {
            return jsonObject2;
         }

         JsonObject jsonObject4 = transform(jsonObject1, "firstperson_righthand", null);
         JsonObject jsonObject5 = mirror(transform(jsonObject1, "firstperson_lefthand", jsonObject4));
         jsonObject3.add("animation.twilight." + text0 + ".first_person_right", firstPersonAnimation(jsonObject4, false));
         jsonObject3.add("animation.twilight." + text0 + ".first_person_left", firstPersonAnimation(jsonObject5, true));
         return jsonObject2;
      } catch (Throwable error6) {
         return jsonObject2;
      }
   }

   private static JsonObject firstPersonAnimation(JsonObject jsonObject0, boolean flag1) {
      double[] values2 = vector(jsonObject0, "rotation", 0.0, 0.0, 0.0);
      double[] values3 = vector(jsonObject0, "translation", 0.0, 0.0, 0.0);
      double[] values4 = vector(jsonObject0, "scale", 1.0, 1.0, 1.0);
      JsonObject jsonObject5 = new JsonObject();
      jsonObject5.addProperty("loop", true);
      JsonObject jsonObject6 = new JsonObject();
      jsonObject6.add("bone", basePose(flag1));
      jsonObject6.add("bone_x", xPose(values2, values3, values4));
      jsonObject6.add("bone_y", yPose(values2));
      jsonObject6.add("bone_z", zPose(values2));
      JsonObject jsonObject7 = new JsonObject();
      jsonObject7.add("rotation", numberArray(0.0, 180.0, 0.0));
      jsonObject6.add("bone_basis", jsonObject7);
      jsonObject5.add("bones", jsonObject6);
      return jsonObject5;
   }

   private static JsonObject basePose(boolean flag0) {
      JsonObject jsonObject1 = new JsonObject();
      if (flag0) {
         jsonObject1.add("rotation", numberArray(180.0, 0.0, 180.0));
         jsonObject1.add("position", numberArray(-18.5, 18.3, 15.0));
         jsonObject1.add("scale", numberArray(1.2, 1.2, 1.2));
      } else {
         jsonObject1.add("rotation", numberArray(90.0, 60.0, -40.0));
         jsonObject1.add("position", numberArray(0.0, 15.0, 2.0));
         jsonObject1.add("scale", numberArray(1.4, 1.4, 1.4));
      }

      return jsonObject1;
   }

   private static JsonObject xPose(double[] values0, double[] values1, double[] values2) {
      JsonObject jsonObject3 = new JsonObject();
      jsonObject3.add("rotation", numberArray(-values0[0], 0.0, 0.0));
      jsonObject3.add("position", numberArray(-values1[0], values1[1], values1[2]));
      jsonObject3.add("scale", numberArray(values2[0], values2[1], values2[2]));
      return jsonObject3;
   }

   private static JsonObject yPose(double[] values0) {
      JsonObject jsonObject1 = new JsonObject();
      jsonObject1.add("rotation", numberArray(0.0, -values0[1], 0.0));
      return jsonObject1;
   }

   private static JsonObject zPose(double[] values0) {
      JsonObject jsonObject1 = new JsonObject();
      jsonObject1.add("rotation", numberArray(0.0, 0.0, values0[2]));
      return jsonObject1;
   }

   private static JsonObject transform(JsonObject jsonObject0, String text1, JsonObject jsonObject2) {
      if (jsonObject0 == null) {
         return jsonObject2 == null ? new JsonObject() : jsonObject2.deepCopy().getAsJsonObject();
      } else if (jsonObject0.has(text1) && jsonObject0.get(text1).isJsonObject()) {
         return jsonObject0.getAsJsonObject(text1).deepCopy().getAsJsonObject();
      } else {
         return jsonObject2 == null ? new JsonObject() : jsonObject2.deepCopy().getAsJsonObject();
      }
   }

   private static JsonObject mirror(JsonObject jsonObject0) {
      JsonObject jsonObject1 = jsonObject0.deepCopy().getAsJsonObject();
      double[] values2 = vector(jsonObject0, "translation", 0.0, 0.0, 0.0);
      double[] values3 = vector(jsonObject0, "rotation", 0.0, 0.0, 0.0);
      jsonObject1.add("translation", numberArray(-values2[0], values2[1], values2[2]));
      jsonObject1.add("rotation", numberArray(values3[0], -values3[1], -values3[2]));
      return jsonObject1;
   }

   private static double[] vector(JsonObject jsonObject0, String text1, double value2, double value4, double value6) {
      double[] values8 = new double[]{value2, value4, value6};
      if (jsonObject0 != null && jsonObject0.has(text1)) {
         JsonElement jsonElement9 = jsonObject0.get(text1);
         if (!jsonElement9.isJsonArray()) {
            return values8;
         }

         JsonArray jsonArray10 = jsonElement9.getAsJsonArray();
         return new double[]{
            jsonArray10.size() > 0 ? jsonArray10.get(0).getAsDouble() : value2,
            jsonArray10.size() > 1 ? jsonArray10.get(1).getAsDouble() : value4,
            jsonArray10.size() > 2 ? jsonArray10.get(2).getAsDouble() : value6
         };
      } else {
         return values8;
      }
   }

   private static JsonArray numberArray(double value0, double value2, double value4) {
      JsonArray jsonArray6 = new JsonArray();
      jsonArray6.add(value0);
      jsonArray6.add(value2);
      jsonArray6.add(value4);
      return jsonArray6;
   }
}
