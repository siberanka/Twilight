package com.siberanka.twilight.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ArmorItemComponents {
   private static final Map<Object, ArmorItemComponents> ACTIVE = Collections.synchronizedMap(new IdentityHashMap<>());
   private final Object owner;
   private final Logger logger;
   private final Map<Object, Object[]> originals = new IdentityHashMap<>();
   private Object registrar;

   private ArmorItemComponents(Object value1, Logger logger2) {
      this.owner = value1;
      this.logger = logger2;
   }

   public static void start(Object value0) {
      try {
         Logger logger1 = (Logger)value0.getClass().getMethod("getLogger").invoke(value0);
         ArmorItemComponents armorItemComponents2 = new ArmorItemComponents(value0, logger1);
         Class class3 = Class.forName("org.geysermc.geyser.api.event.EventRegistrar");
         armorItemComponents2.registrar = class3.getMethod("of", Object.class).invoke(null, armorItemComponents2);
         Class class4 = Class.forName("com.siberanka.twilight.geyser.GeyserEvents");
         Method method5 = class4.getMethod("subscribe", class3, Class.class, Consumer.class);
         Consumer consumer6 = var1x -> armorItemComponents2.apply();

         for (String text8 : List.of("GeyserPostInitializeEvent", "GeyserPostReloadEvent")) {
            method5.invoke(null, armorItemComponents2.registrar, Class.forName("org.geysermc.geyser.api.event.lifecycle." + text8), consumer6);
         }

         ACTIVE.put(value0, armorItemComponents2);
         armorItemComponents2.apply();
      } catch (ClassNotFoundException error9) {
      } catch (Exception error10) {
         Logger.getLogger("Twilight").log(Level.WARNING, "Armor dye integration could not start; dynamic armor icons need a compatible local Geyser.", error10);
      }
   }

   public static void stop(Object value0) {
      ArmorItemComponents armorItemComponents1 = ACTIVE.remove(value0);
      if (armorItemComponents1 != null) {
         try {
            Class class2 = Class.forName("org.geysermc.geyser.api.event.EventRegistrar");
            Class.forName("com.siberanka.twilight.geyser.GeyserEvents").getMethod("unregisterAll", class2).invoke(null, armorItemComponents1.registrar);
            synchronized (armorItemComponents1) {
               for (var value5 : armorItemComponents1.originals.entrySet()) {
                  Field field6 = field(value5.getKey().getClass(), "componentData");
                  if (field6.get(value5.getKey()) == ((Object[])value5.getValue())[1]) {
                     field6.set(value5.getKey(), ((Object[])value5.getValue())[0]);
                  }
               }

               armorItemComponents1.originals.clear();
            }
         } catch (Exception error9) {
            armorItemComponents1.logger.log(Level.WARNING, "Armor dye integration could not be restored", error9);
         }
      }
   }

   private synchronized void apply() {
      try {
         Method method1 = this.owner.getClass().getDeclaredMethod("servedPack");
         method1.setAccessible(true);
         Path path2 = (Path)method1.invoke(this.owner);
         if (Files.isRegularFile(path2)) {
            JsonObject jsonObject3;
            try (ZipFile zipFile4 = new ZipFile(path2.toFile())) {
               ZipEntry zipEntry5 = zipFile4.getEntry("twilight/armor-icons.json");
               if (zipEntry5 == null) {
                  return;
               }

               if (zipEntry5.getSize() < 0L || zipEntry5.getSize() > 2000000L) {
                  throw new IllegalArgumentException("Oversized armor metadata");
               }

               try (
                  InputStream inputStream6 = zipFile4.getInputStream(zipEntry5);
                  InputStreamReader inputStreamReader7 = new InputStreamReader(inputStream6, StandardCharsets.UTF_8);
               ) {
                  jsonObject3 = JsonParser.parseReader(inputStreamReader7).getAsJsonObject().getAsJsonObject("runtime_items");
               }
            }

            Object entry24 = Class.forName("org.geysermc.geyser.registry.Registries").getField("ITEMS").get(null);
            Map mapping25 = (Map)entry24.getClass().getMethod("get").invoke(entry24);
            int number26 = 0;

            for (Object entry8 : mapping25.values()) {
               Map mapping9 = (Map)entry8.getClass().getMethod("getItemDefinitions").invoke(entry8);

               for (Object entry11 : mapping9.values()) {
                  String text12 = (String)entry11.getClass().getMethod("getIdentifier").invoke(entry11);
                  if (jsonObject3.has(text12) && text12.startsWith("twilight:")) {
                     Field field13 = field(entry11.getClass(), "componentData");
                     Object entry14 = field13.get(entry11);
                     if (entry14 instanceof Map) {
                        JsonObject jsonObject15 = jsonObject3.getAsJsonObject(text12);
                        Object entry16 = patchData(entry14, jsonObject15);
                        if (entry16 != entry14) {
                           this.originals.putIfAbsent(entry11, new Object[]{entry14, entry16});
                           this.originals.get(entry11)[1] = entry16;
                           field13.set(entry11, entry16);
                           number26++;
                        }
                     }
                  }
               }
            }

            if (number26 > 0) {
               this.logger.info("Armor icons: enabled Java dye layers in " + number26 + " Bedrock item definitions (all protocol versions).");
            }
         }
      } catch (Exception error23) {
         this.logger
            .log(
               Level.WARNING,
               "Armor dye integration unavailable for this Geyser build; default icons remain available. Dynamic dye colors are not verified.",
               error23
            );
      }
   }

   static Object patchData(Object value0, JsonObject jsonObject1) throws ReflectiveOperationException {
      var value2 = copy(value0);
      var value3 = copy(value2.get("components"));
      var value4 = copy(value3.get("item_properties"));
      var value5 = copy(value4.get("minecraft:icon"));
      var textures6 = copy(value5.get("textures"));
      String text7 = jsonObject1.get("default_icon").getAsString();
      if (!Objects.equals(textures6.get("default"), text7)) {
         return value0;
      }

      String text8 = jsonObject1.has("dyed_icon") ? jsonObject1.get("dyed_icon").getAsString() : text7;
      if (Objects.equals(textures6.get("dyed"), text8) && value3.containsKey("minecraft:dyeable")) {
         return value0;
      }

      textures6.put("dyed", text8);
      value5.put("textures", nbt(textures6));
      value4.put("minecraft:icon", nbt(value5));
      value3.put("item_properties", nbt(value4));
      value3.putIfAbsent("minecraft:dyeable", nbt(Map.of()));
      value2.put("components", nbt(value3));
      return nbt(value2);
   }

   static Field field(Class<?> class0, String text1) throws NoSuchFieldException {
      for (Class class2 = class0; class2 != null; class2 = class2.getSuperclass()) {
         try {
            Field field3 = class2.getDeclaredField(text1);
            field3.setAccessible(true);
            return field3;
         } catch (NoSuchFieldException error4) {
         }
      }

      throw new NoSuchFieldException(text1);
   }

   static Map<String, Object> copy(Object value0) {
      return value0 instanceof Map ? new LinkedHashMap<>((Map<? extends String, ? extends Object>)value0) : new LinkedHashMap<>();
   }

   static Object nbt(Map<String, ?> mapping0) throws ReflectiveOperationException {
      return Class.forName("org.cloudburstmc.nbt.NbtMap").getMethod("fromMap", Map.class).invoke(null, mapping0);
   }
}
