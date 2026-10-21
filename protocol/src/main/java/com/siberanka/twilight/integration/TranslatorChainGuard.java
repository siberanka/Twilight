package com.siberanka.twilight.integration;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class TranslatorChainGuard {
   private TranslatorChainGuard() {
   }

   public static boolean contains(Object value0, Object value1) {
      IdentityHashMap identityHashMap2 = new IdentityHashMap();

      for (int number3 = 0; value0 != null && number3 < 32; number3++) {
         if (value0 == value1 || identityHashMap2.put(value0, Boolean.TRUE) != null) {
            return true;
         }

         String text4 = value0.getClass().getName();
         if (!text4.startsWith("com.siberanka.twilight.")) {
            return false;
         }

         try {
            Field field5 = value0.getClass().getDeclaredField("original");
            field5.setAccessible(true);
            value0 = field5.get(value0);
         } catch (ReflectiveOperationException | SecurityException error6) {
            return false;
         }
      }

      return value0 != null;
   }
}
