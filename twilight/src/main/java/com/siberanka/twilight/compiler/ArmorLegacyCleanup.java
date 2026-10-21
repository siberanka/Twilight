package com.siberanka.twilight.compiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class ArmorLegacyCleanup {
   private static final String OLD_PREFIX = "render_controllers/af_";
   private static final String OLD_IDENTIFIER = "controller.render.twilight.armor.fixed.";
   private static final List<String> OLD_REPORTS = List.of(
      "twilight/armor-fallback-report.json", "twilight/armor-fixed-columns.json", "twilight/armor-baked-report.json"
   );

   private ArmorLegacyCleanup() {
   }

   public static int apply(Map<String, byte[]> packFiles0) {
      if (packFiles0 != null && !packFiles0.isEmpty()) {
         int number1 = 0;
         ArrayList entries2 = new ArrayList<>(packFiles0.keySet());
         Iterator iterator3 = entries2.iterator();

         while (true) {
            String text4;
            Map<String, Object> mapping6;
            while (true) {
               if (!iterator3.hasNext()) {
                  for (String text18 : OLD_REPORTS) {
                     if (packFiles0.remove(text18) != null) {
                        number1++;
                     }
                  }

                  return number1;
               }

               text4 = (String)iterator3.next();
               if (text4.startsWith("render_controllers/af_") && text4.endsWith(".json")) {
                  byte[] resourceBytes5 = (byte[])packFiles0.get(text4);
                  if (resourceBytes5 != null) {
                     try {
                        mapping6 = obj(BedrockAnimationBridge.Json.read(resourceBytes5));
                        break;
                     } catch (RuntimeException error16) {
                     }
                  }
               }
            }

            var renderControllers7 = obj(mapping6.get("render_controllers"));
            if (!renderControllers7.isEmpty()) {
               boolean flag8 = renderControllers7.keySet().stream().allMatch(var0x -> var0x.startsWith("controller.render.twilight.armor.fixed."));
               if (flag8) {
                  boolean flag9 = false;

                  for (String text11 : renderControllers7.keySet()) {
                     byte[] resourceBytes12 = ("\"" + text11 + "\"").getBytes(StandardCharsets.UTF_8);

                     for (var value14 : packFiles0.entrySet()) {
                        String text15 = (String)value14.getKey();
                        if (!text15.equals(text4) && text15.endsWith(".json") && !OLD_REPORTS.contains(text15) && contains((byte[])value14.getValue(), resourceBytes12)) {
                           flag9 = true;
                           break;
                        }
                     }

                     if (flag9) {
                        break;
                     }
                  }

                  if (!flag9 && packFiles0.remove(text4) != null) {
                     number1++;
                  }
               }
            }
         }
      } else {
         return 0;
      }
   }

   private static boolean contains(byte[] resourceBytes0, byte[] resourceBytes1) {
      if (resourceBytes0 != null && resourceBytes1.length != 0 && resourceBytes0.length >= resourceBytes1.length) {
         label28:
         for (int number2 = 0; number2 <= resourceBytes0.length - resourceBytes1.length; number2++) {
            for (int number3 = 0; number3 < resourceBytes1.length; number3++) {
               if (resourceBytes0[number2 + number3] != resourceBytes1[number3]) {
                  continue label28;
               }
            }

            return true;
         }

         return false;
      } else {
         return false;
      }
   }

   private static Map<String, Object> obj(Object value0) {
      return value0 instanceof Map mapping1 ? mapping1 : Map.of();
   }
}
