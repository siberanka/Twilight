package com.siberanka.twilight.integration.text;

public final class SafeUiProfile {
   private SafeUiProfile() {
   }

   public static Object get(Object value0) {
      if (value0 == null) {
         return null;
      }

      try {
         return value0.getClass().getMethod("getUiProfile").invoke(value0);
      } catch (ReflectiveOperationException | LinkageError | SecurityException error2) {
         return null;
      }
   }
}
