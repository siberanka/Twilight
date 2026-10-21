package com.siberanka.twilight.compiler;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class WindowsBuildPublishRepair {
   private WindowsBuildPublishRepair() {
   }

   public static Path publish(Path path0, Path path1, CopyOption[] values2) throws IOException {
      IOException error3 = null;

      for (int number4 = 0; number4 < 8; number4++) {
         try {
            return Files.move(path0, path1, values2);
         } catch (IOException error9) {
            error3 = error9;
            if (error9 instanceof FileSystemException) {
               if (number4 < 7) {
                  try {
                     Thread.sleep(150L * (number4 + 1));
                  } catch (InterruptedException error8) {
                     Thread.currentThread().interrupt();
                     break;
                  }
               }
               continue;
            }
            break;
         }
      }

      Path path10 = path1.resolveSibling("previous");

      try {
         if (!Files.exists(path1) && Files.exists(path10)) {
            Files.move(path10, path1);
         }
      } catch (IOException error7) {
         error3.addSuppressed(error7);
      }

      throw error3;
   }

   public static void cleanupOldBuild(Path path0) throws IOException {
      if (Files.exists(path0)) {
         List<Path> entries1;
         try (Stream<Path> streamPath2 = Files.walk(path0)) {
            entries1 = streamPath2.sorted((var0x, var1x) -> Integer.compare(var1x.getNameCount(), var0x.getNameCount())).toList();
         }

         for (Path path3 : entries1) {
            try {
               Files.deleteIfExists(path3);
            } catch (FileSystemException error6) {
               return;
            }
         }
      }
   }
}
