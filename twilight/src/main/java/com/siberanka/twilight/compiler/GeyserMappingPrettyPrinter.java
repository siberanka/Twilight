package com.siberanka.twilight.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public final class GeyserMappingPrettyPrinter {
   private GeyserMappingPrettyPrinter() {
   }

   public static Path write(Path path0, byte[] resourceBytes1, OpenOption... values2) throws IOException {
      return Files.write(path0, pretty(resourceBytes1), values2);
   }

   public static byte[] pretty(byte[] resourceBytes0) {
      if (resourceBytes0 == null) {
         throw new IllegalArgumentException("Missing mapping JSON");
      }

      String text1 = new String(resourceBytes0, StandardCharsets.UTF_8);
      StringBuilder builder2 = new StringBuilder(text1.length() + text1.length() / 3);
      boolean flag3 = false;
      boolean flag4 = false;
      int number5 = 0;

      for (int number6 = 0; number6 < text1.length(); number6++) {
         char char7 = text1.charAt(number6);
         if (flag3) {
            builder2.append(char7);
            if (flag4) {
               flag4 = false;
            } else if (char7 == '\\') {
               flag4 = true;
            } else if (char7 == '"') {
               flag3 = false;
            }
         } else if (char7 == '"') {
            builder2.append(char7);
            flag3 = true;
         } else if (char7 == '{' || char7 == '[') {
            builder2.append(char7);
            int number10 = number6 + 1;

            while (number10 < text1.length() && Character.isWhitespace(text1.charAt(number10))) {
               number10++;
            }

            if (number10 < text1.length() && text1.charAt(number10) != (char7 == '{' ? 125 : 93)) {
               newline(builder2, ++number5);
            }
         } else if (char7 == '}' || char7 == ']') {
            int number8 = number6 - 1;

            while (number8 >= 0 && Character.isWhitespace(text1.charAt(number8))) {
               number8--;
            }

            boolean flag9 = number8 >= 0 && text1.charAt(number8) == (char7 == '}' ? 123 : 91);
            if (!flag9) {
               number5 = Math.max(0, number5 - 1);
               newline(builder2, number5);
            }

            builder2.append(char7);
         } else if (char7 == ',') {
            builder2.append(char7);
            newline(builder2, number5);
         } else if (char7 == ':') {
            builder2.append(": ");
         } else if (!Character.isWhitespace(char7)) {
            builder2.append(char7);
         }
      }

      if (!flag3 && number5 == 0) {
         builder2.append('\n');
         return builder2.toString().getBytes(StandardCharsets.UTF_8);
      } else {
         throw new IllegalArgumentException("Malformed JSON mapping");
      }
   }

   private static void newline(StringBuilder builder0, int number1) {
      builder0.append('\n');

      for (int number2 = 0; number2 < number1; number2++) {
         builder0.append("  ");
      }
   }
}
