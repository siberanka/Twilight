package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BowDrawRuntimeFix {
   private BowDrawRuntimeFix() {
   }

   public static void repair(ResourceIndex resourceIndex0, List<ItemCandidate> entries1, Map<String, byte[]> packFiles2) {
      for (ItemCandidate itemCandidate4 : entries1) {
         try {
            if (itemCandidate4.nativeStateModels().size() >= 4) {
               String text5 = BedrockPackCompiler.bedrockIdentifier(itemCandidate4);
               String suffix6 = text5.substring(text5.indexOf(58) + 1);
               String attachablePath7 = "attachables/" + suffix6 + ".json";
               byte[] resourceBytes8 = (byte[])packFiles2.get(attachablePath7);
               if (resourceBytes8 != null) {
                  var parsedJson9 = map(BedrockAnimationBridge.Json.read(resourceBytes8));
                  var attachable10 = map(map(parsedJson9.get("minecraft:attachable")).get("description"));
                  var scripts11 = map(attachable10.get("scripts"));
                  var animationScripts12 = list(scripts11.get("pre_animation"));
                  if (animationScripts12 != null && !animationScripts12.isEmpty()) {
                     String text13 = String.valueOf(animationScripts12.get(0));
                     if (text13.contains("q.item_in_use_duration") && text13.contains("v.twilight_model_index")) {
                        var geometry14 = map(attachable10.get("geometry"));
                        if (geometry14 != null && geometry14.containsKey("state_3")) {
                           var value15 = authoredThresholds(resourceIndex0, itemCandidate4);
                           if (value15.size() < 2) {
                              value15 = existingThresholds(text13);
                           }

                           if (value15.size() >= 2) {
                              double value16 = (Double)value15.get(value15.size() - 2);
                              double value18 = (Double)value15.get(value15.size() - 1);
                              if (value16 > 0.0 && value16 < value18 && value18 <= 60.0) {
                                 String text20 = "q.main_hand_item_use_duration";
                                 String text21 = "q.main_hand_item_max_duration";
                                 String text22 = "math.max(0.0, " + text21 + " - (" + text20 + " - q.frame_alpha + 1.0))";
                                 String text23 = "(" + text20 + " > 0.0 && " + text21 + " > 0.0)";
                                 String text24 = "v.twilight_model_index = "
                                    + text23
                                    + " ? ("
                                    + text22
                                    + " >= "
                                    + fmt(value18 * 20.0)
                                    + " ? 3 : ("
                                    + text22
                                    + " >= "
                                    + fmt(value16 * 20.0)
                                    + " ? 2 : 1)) : 0;";
                                 if (!text13.equals(text24)) {
                                    animationScripts12.set(0, text24);
                                    scripts11.put("pre_animation", animationScripts12);
                                    packFiles2.put(attachablePath7, BedrockAnimationBridge.Json.bytes(parsedJson9));
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         } catch (RuntimeException error25) {
         }
      }
   }

   private static List<Double> authoredThresholds(ResourceIndex resourceIndex0, ItemCandidate itemCandidate1) {
      if (!itemCandidate1.customModelData().isPresent()) {
         return List.of();
      }

      String text2 = itemCandidate1.baseItem();
      String[] segments3 = text2.split(":", 2);
      String text4 = segments3.length == 2 ? segments3[0] : "minecraft";
      String text5 = segments3.length == 2 ? segments3[1] : segments3[0];
      String modelName6 = "assets/" + text4 + "/models/item/" + text5 + ".json";
      var value7 = resourceIndex0.find(modelName6);
      if (value7.isEmpty()) {
         return List.of();
      }

      try {
         var parsedJson8 = map(BedrockAnimationBridge.Json.read(((ResourceIndex.Asset)value7.get()).readBytes()));
         var value9 = list(parsedJson8.get("overrides"));
         if (value9 == null) {
            return List.of();
         }

         TreeSet identifiers10 = new TreeSet<>();

         for (Object value12 : value9) {
            var value13 = map(value12);
            if (value13 != null) {
               var value14 = map(value13.get("predicate"));
               if (value14 != null
                  && value14.get("custom_model_data") instanceof Number number15
                  && number15.intValue() == itemCandidate1.customModelData().getAsInt()
                  && value14.get("pull") instanceof Number number21
                  && value14.get("pulling") instanceof Number number22
                  && !(number22.doubleValue() < 0.5)) {
                  double value23 = number21.doubleValue();
                  if (Double.isFinite(value23) && value23 > 0.0 && value23 <= 1.0) {
                     identifiers10.add(value23);
                  }
               }
            }
         }

         return new ArrayList<>(identifiers10);
      } catch (Exception error20) {
         return List.of();
      }
   }

   private static List<Double> existingThresholds(String text0) {
      TreeSet identifiers1 = new TreeSet<>();
      Matcher matcher2 = Pattern.compile(">=\\s*(\\d+(?:\\.\\d+)?)").matcher(text0);

      while (matcher2.find()) {
         double value3 = Double.parseDouble(matcher2.group(1));
         if (value3 > 0.0 && value3 <= 60.0) {
            identifiers1.add(value3);
         }
      }

      return new ArrayList<>(identifiers1);
   }

   private static String fmt(double value0) {
      return BigDecimal.valueOf(value0).stripTrailingZeros().toPlainString();
   }

   private static Map<String, Object> map(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static List<Object> list(Object value0) {
      return value0 instanceof List ? (List)value0 : null;
   }
}
