package com.siberanka.twilight.compiler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GeyserConditionPredicateRepair {
   private static final String MAPPING_PATH = "twilight/geyser_item_mappings.json";
   private static final Set<String> SUPPORTED_CONDITIONS = Set.of("broken", "damaged", "custom_model_data", "has_component", "fishing_rod_cast");

   private GeyserConditionPredicateRepair() {
   }

   public static void sanitize(Map<String, byte[]> packFiles0) {
      byte[] resourceBytes1 = (byte[])packFiles0.get("twilight/geyser_item_mappings.json");
      if (resourceBytes1 != null) {
         Object value2;
         try {
            value2 = BedrockAnimationBridge.Json.read(resourceBytes1);
         } catch (RuntimeException error16) {
            return;
         }

         if (value2 instanceof Map mapping3) {
            if (mapping3.get("items") instanceof Map mapping5) {
               boolean flag6 = false;

               for (Object value8 : mapping5.values()) {
                  if (value8 instanceof List entries9) {
                     var value10 = entries9;
                     Iterator iterator11 = value10.iterator();

                     while (iterator11.hasNext()) {
                        if (iterator11.next() instanceof Map mapping13) {
                           var value14 = mapping13;
                           Object value15 = value14.get("predicate");
                           if (containsUnsupportedCondition(value15)) {
                              if (isRestingUsingItem(value15) && !hasEquivalentDefault(value10, value14)) {
                                 value14.remove("predicate");
                                 value14.remove("priority");
                              } else {
                                 iterator11.remove();
                              }

                              flag6 = true;
                           }
                        }
                     }
                  }
               }

               if (flag6) {
                  packFiles0.put("twilight/geyser_item_mappings.json", BedrockAnimationBridge.Json.bytes(value2));
               }
            }
         }
      }
   }

   private static boolean isRestingUsingItem(Object value0) {
      return !(value0 instanceof Map mapping1)
         ? false
         : "condition".equals(mapping1.get("type")) && "using_item".equals(mapping1.get("property")) && Boolean.FALSE.equals(mapping1.get("expected"));
   }

   private static boolean hasEquivalentDefault(List<Object> entries0, Map<String, Object> mapping1) {
      for (Object value3 : entries0) {
         if (value3 != mapping1
            && value3 instanceof Map mapping4
            && !mapping4.containsKey("predicate")
            && Objects.equals(mapping1.get("type"), mapping4.get("type"))
            && Objects.equals(mapping1.get("model"), mapping4.get("model"))
            && Objects.equals(mapping1.get("custom_model_data"), mapping4.get("custom_model_data"))) {
            return true;
         }
      }

      return false;
   }

   private static boolean containsUnsupportedCondition(Object value0) {
      if (value0 instanceof List) {
         for (Object value4 : (List)value0) {
            if (containsUnsupportedCondition(value4)) {
               return true;
            }
         }
      } else if (value0 instanceof Map mapping2) {
         if ("condition".equals(mapping2.get("type"))) {
            Object value5 = mapping2.get("property");
            if (!(value5 instanceof String) || !SUPPORTED_CONDITIONS.contains(value5)) {
               return true;
            }
         }

         for (Object value7 : mapping2.values()) {
            if ((value7 instanceof Map || value7 instanceof List) && containsUnsupportedCondition(value7)) {
               return true;
            }
         }
      }

      return false;
   }
}
