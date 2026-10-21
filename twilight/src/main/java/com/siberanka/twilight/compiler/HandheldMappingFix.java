package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import java.io.IOException;

public final class HandheldMappingFix {
   private HandheldMappingFix() {
   }

   public static boolean displayHandheld(ResolvedJavaModel resolvedJavaModel0, ResourceIndex resourceIndex1, ItemCandidate itemCandidate2, VanillaAssetCache vanillaAssetCache3) {
      if (resolvedJavaModel0.handheld()) {
         return true;
      }

      if (!resolvedJavaModel0.isThreeDimensional()) {
         return false;
      }

      String text4 = itemCandidate2.baseItem();
      if (text4 != null && !text4.isBlank()) {
         int separatorIndex5 = text4.indexOf(58);
         String suffix6 = separatorIndex5 < 0 ? "minecraft" : text4.substring(0, separatorIndex5);
         String suffix7 = separatorIndex5 < 0 ? text4 : text4.substring(separatorIndex5 + 1);
         if (suffix7.startsWith("item/")) {
            suffix7 = suffix7.substring(5);
         }

         try {
            return new JavaModelResolver(resourceIndex1, vanillaAssetCache3).resolve(suffix6 + ":item/" + suffix7, true).handheld();
         } catch (IOException | RuntimeException error9) {
            return false;
         }
      } else {
         return false;
      }
   }
}
