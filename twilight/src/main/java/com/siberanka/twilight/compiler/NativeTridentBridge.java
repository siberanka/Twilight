package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NativeTridentBridge {
   private static final String MAPPINGS = "twilight/geyser_item_mappings.json";
   private static final String CHARGE = "variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);";

   private NativeTridentBridge() {
   }

   public static void apply(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 != null) {
         Map<String, Object> mapping2;
         try {
            mapping2 = obj(BedrockAnimationBridge.Json.read(resourceBytes1));
         } catch (RuntimeException error12) {
            return;
         }

         var itemDefinitions3 = obj(mapping2.get("items"));
         if (itemDefinitions3 != null) {
            var value4 = arr(itemDefinitions3.get("minecraft:trident"));
            if (value4 != null) {
               boolean flag5 = false;

               for (Object entry7 : value4) {
                  var jsonObject8 = obj(entry7);
                  if (jsonObject8 != null) {
                     String identifier9 = str(jsonObject8.get("bedrock_identifier"));
                     if (identifier9 != null && identifier9.startsWith("twilight:")) {
                        identifier9 = identifier9.substring("twilight:".length());
                        if (identifier9.matches("[a-z0-9_.-]+")) {
                           try {
                              if (rewrite(packFiles0, jsonObject8, identifier9)) {
                                 flag5 = true;
                              }
                           } catch (RuntimeException error11) {
                           }
                        }
                     }
                  }
               }

               if (flag5) {
                  packFiles0.put("twilight/geyser_item_mappings.json", BedrockAnimationBridge.Json.bytes(mapping2));
               }
            }
         }
      }
   }

   private static boolean rewrite(Map<String, byte[]> packFiles0, Map<String, Object> mapping1, String text2) {
      String geometryName3 = "models/entity/geometry." + text2 + ".geo.json";
      String attachablePath4 = "attachables/" + text2 + ".json";
      String animationName5 = "animations/" + text2 + ".animation.json";
      if (packFiles0.containsKey(geometryName3) && packFiles0.containsKey(attachablePath4)) {
         var value6 = parse((byte[])packFiles0.get(geometryName3));
         var value7 = arr(value6.get("minecraft:geometry"));
         if (value7 != null && value7.size() == 1) {
            var jsonObject8 = obj(value7.get(0));
            if (jsonObject8 == null) {
               return false;
            }

            var value9 = arr(jsonObject8.get("bones"));
            if (value9 != null && !value9.isEmpty()) {
               var value10 = parse((byte[])packFiles0.get(attachablePath4));
               var attachable11 = obj(value10.get("minecraft:attachable"));
               var description12 = attachable11 == null ? null : obj(attachable11.get("description"));
               if (description12 != null && ("twilight:" + text2).equals(str(description12.get("identifier")))) {
                  var animations13 = obj(description12.get("animations"));
                  String text14 = animations13 == null ? null : str(animations13.get("head"));
                  var value15 = packFiles0.containsKey(animationName5) ? parse((byte[])packFiles0.get(animationName5)) : null;
                  var animations16 = value15 == null ? null : obj(value15.get("animations"));
                  Object value17 = text14 != null && animations16 != null ? animations16.get(text14) : null;
                  String text18 = null;
                  if (value9.size() == 1) {
                     var jsonObject19 = obj(value9.get(0));
                     if (jsonObject19 == null) {
                        return false;
                     }

                     text18 = str(jsonObject19.get("name"));
                     jsonObject19.put("name", "pole");
                     jsonObject19.put("binding", "q.item_slot_to_bone_name(c.item_slot)");
                  } else {
                     Map mapping24 = null;

                     for (Object value21 : value9) {
                        var jsonObject22 = obj(value21);
                        if (jsonObject22 == null) {
                           return false;
                        }

                        if ("pole".equals(str(jsonObject22.get("name")))) {
                           if (mapping24 != null || jsonObject22.containsKey("parent")) {
                              return false;
                           }

                           mapping24 = jsonObject22;
                        }
                     }

                     if (mapping24 == null) {
                        mapping24 = new LinkedHashMap<>();
                        mapping24.put("name", "pole");
                        mapping24.put("pivot", List.of(0, 24, 0));
                     }

                     mapping24.put("binding", "q.item_slot_to_bone_name(c.item_slot)");
                     ArrayList entries27 = new ArrayList<>();
                     entries27.add(mapping24);

                     for (Object value32 : value9) {
                        var jsonObject23 = obj(value32);
                        if (jsonObject23 != mapping24) {
                           if (!jsonObject23.containsKey("parent")) {
                              jsonObject23.put("parent", "pole");
                           }

                           jsonObject23.remove("binding");
                           entries27.add(jsonObject23);
                        }
                     }

                     jsonObject8.put("bones", entries27);
                  }

                  if (value17 != null && text18 != null && !"pole".equals(text18)) {
                     var jsonObject25 = obj(value17);
                     var jsonObject28 = jsonObject25 == null ? null : obj(jsonObject25.get("bones"));
                     if (jsonObject28 != null && jsonObject28.containsKey(text18)) {
                        jsonObject28.put("pole", jsonObject28.remove(text18));
                     }
                  }

                  LinkedHashMap lookup26 = new LinkedHashMap<>();
                  lookup26.put("wield", "controller.animation.trident.wield");
                  lookup26.put("wield_first_person", "animation.trident.wield_first_person");
                  lookup26.put("wield_first_person_raise", "animation.trident.wield_first_person_raise");
                  lookup26.put("wield_first_person_raise_shake", "animation.trident.wield_first_person_raise_shake");
                  lookup26.put("wield_first_person_riptide", "animation.trident.wield_first_person_riptide");
                  lookup26.put("wield_third_person", "animation.trident.wield_third_person");
                  lookup26.put("wield_third_person_raise", "animation.trident.wield_third_person_raise");
                  ArrayList entries29 = new ArrayList<>();
                  entries29.add("wield");
                  if (value17 != null) {
                     lookup26.put("head", text14);
                     entries29.add(Map.of("head", "c.item_slot == 'head'"));
                  }

                  description12.put("animations", lookup26);
                  LinkedHashMap lookup31 = new LinkedHashMap<>();
                  lookup31.put(
                     "pre_animation",
                     List.of(
                        "variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);"
                     )
                  );
                  lookup31.put("animate", entries29);
                  description12.put("scripts", lookup31);
                  var bedrockOptions33 = obj(mapping1.get("bedrock_options"));
                  if (bedrockOptions33 == null) {
                     bedrockOptions33 = new LinkedHashMap<>();
                  }

                  bedrockOptions33.put("display_handheld", Boolean.TRUE);
                  mapping1.put("bedrock_options", bedrockOptions33);
                  packFiles0.put(geometryName3, BedrockAnimationBridge.Json.bytes(value6));
                  packFiles0.put(attachablePath4, BedrockAnimationBridge.Json.bytes(value10));
                  if (value15 != null && value17 != null) {
                     packFiles0.put(animationName5, BedrockAnimationBridge.Json.bytes(value15));
                  }

                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map ? (Map)value0 : null;
   }

   private static List<Object> arr(Object value0) {
      return value0 instanceof List ? (List)value0 : null;
   }

   private static String str(Object value0) {
      return value0 instanceof String ? (String)value0 : null;
   }

   private static Map<String, Object> parse(byte[] resourceBytes0) {
      var jsonObject1 = obj(BedrockAnimationBridge.Json.read(resourceBytes0));
      if (jsonObject1 == null) {
         throw new IllegalArgumentException("Expected JSON object");
      } else {
         return jsonObject1;
      }
   }
}
