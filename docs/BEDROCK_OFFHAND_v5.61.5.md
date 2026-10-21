# Twilight v5.61.5 : restriction offhand Bedrock, mappings UNIQUEMENT

Ce correctif est construit sur le **projet Gradle v5.61.3**.

La règle d'offhand est appliquée uniquement à la génération du mapping Geyser :

- Pour les définitions custom dont le matériau Java de base n'est pas autorisé dans l'offhand de Bedrock, `bedrock_options.allow_offhand = false`.
- Les matériaux autorisés conservent leur comportement Geyser natif, sans ajouter de `true` inutile.
- La source est **la clé de l'item Java de base** dans `items` (`minecraft:paper`, `minecraft:shield`…), jamais le nom, la texture ou la géométrie de l'item custom.
- Aucune intervention sur les slots de Bukkit, aucun listener Paper, aucun timer, aucun `setItemInOffHand`, aucun déplacement/dépôt d'item.
- Les joueurs Java ne sont pas concernés. Les items vanilla non custom sont laissés à la gestion normale de Geyser/Bedrock.

Matériaux autorisés pour les définitions custom : `shield`, `totem_of_undying`, `arrow`, `tipped_arrow`, `firework_rocket`, `filled_map`, `nautilus_shell`. Cette liste concerne uniquement les **matériaux Java de base**.

## Sources et contrôle

Seules deux sources Java sont impliquées :
1. `BedrockPackCompiler.java`, ajoute `BedrockOffhandPolicy.apply(packFiles)` avant la minification finale.
2. `BedrockOffhandPolicy.java`, écrit/retire `allow_offhand` selon le matériau de base.

`BedrockOffhandPolicyTest.java` vérifie les matériaux, les cas custom trompeurs, les mappings `legacy` et `definition`, la préservation des autres options, l'idempotence et l'absence de modification des textures.

Aucun fichier `BedrockOffhandListener.java` ni `BedrockOffhandListenerTest.java` n'est présent, et aucune inscription `registerEvents()` n'a été ajoutée au plugin.

## Déploiement

Compiler avec `gradlew.bat :twilight:build :twilight:test --no-daemon` sur ton ordinateur. Remplacer le JAR puis **régénérer et redéployer le mapping Geyser** et redémarrer Geyser : un ancien mapping ne recevra pas `allow_offhand: false` automatiquement.

Documentation officielle Geyser : https://geysermc.org/wiki/geyser/custom-items/ (option `allow_offhand`, défaut `true`).

**Limite :** ce correctif ne prétend pas corriger des mouvements d'inventaire mal traduits côté serveur ; l'objectif demandé est exclusivement de définir la règle côté client via les mappings Geyser.
