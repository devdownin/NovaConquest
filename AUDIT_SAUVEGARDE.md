# Audit — Sauvegarde & sérialisation

> Portée : `SaveManager` (rotation 3 slots, quarantaine), `SavedGameSnapshotCodec`
> (encodage/décodage, versionnage), `SaveRepository`, la compatibilité des modèles
> `@Serializable` de `:core:domain`, et le chemin d'auto-sauvegarde du `GameViewModel`.
>
> Motivation : `CLAUDE.md` avertit qu'il **n'existe aucune couche de migration** — or les audits
> précédents de cette série ont ajouté des champs sérialisés (`GameMap.seed`,
> `ResearchProgress.costPaid`). Cet audit vérifie que les sauvegardes existantes survivent.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Tout le code concerné est en `:core:engine` / `:core:domain`, donc **entièrement couvert par la CI**.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **S1** | 🟠 **Moyen — ✅ corrigé** | Perte d'accès | `hasSavedGame()` ne testait que le slot 1 : après mise en quarantaine de ce slot, « RESUME COMMAND » devenait inerte alors que les slots 2/3 étaient chargeables |
| **S2** | 🟠 **Moyen — ✅ corrigé** | Silence | Les échecs d'écriture étaient **avalés** (`printStackTrace`) : le joueur croyait sa partie sauvegardée |
| S3 | 🟢 **Vérifié — ✅ testé** | Migration | Les champs ajoutés récemment (`seed`, `costPaid`) ont bien des valeurs par défaut → anciennes sauvegardes toujours chargeables (désormais **prouvé par test**) |
| S4 | 🟡 Faible — ✅ corrigé | Atomicité | Fenêtre `delete()` → `renameTo()` supprimée : le slot 1 est remplacé par un **déplacement atomique** |
| S5 | 🟡 Faible — ✅ corrigé | Versionnage | Couche de **migration montante** ajoutée (`SaveMigrations`), avec garde-fou testé contre un futur incrément de version sans migration |

---

## 2. Bugs corrigés

### S1 — 🟠 Sauvegardes récupérables rendues inaccessibles  ✅ **corrigé**

```kotlin
override fun hasSavedGame(): Boolean = File(saveDirectory, "autosave_1.json").exists()
```

`loadLatestGame()` sait pourtant récupérer une partie depuis **n'importe lequel** des 3 slots — et
lorsqu'il rencontre un slot 1 corrompu, il le met en quarantaine, ce qui **déplace le fichier**
(`file.renameTo(quarantine)`). Enchaînement du bug :

1. Le slot 1 se corrompt (crash pendant l'écriture, disque plein…).
2. Le joueur lance « RESUME » → chargement **réussi** depuis le slot 2, slot 1 mis en quarantaine.
3. Au lancement suivant, `hasSavedGame()` ne trouve plus `autosave_1.json` → **false**.
4. Le menu grise « RESUME COMMAND » (`MainMenuScreen` : `onClick = if (hasSavedGame) … else {}`) —
   le joueur croit sa progression perdue, alors que les slots 2/3 sont parfaitement chargeables.

**Correctif** — `hasSavedGame()` teste les **trois** slots, et redevient donc cohérent avec ce que
`loadLatestGame()` est capable de restaurer.
**Test** : `hasSavedGameStaysTrueAfterSlot1IsQuarantined`.

### S2 — 🟠 Échecs d'auto-sauvegarde silencieux  ✅ **corrigé**

`saveGame` retournait `Unit` et absorbait toute exception dans un `printStackTrace()`. Disque
plein, permissions refusées, stockage indisponible : **aucun signal**. Le joueur continue à jouer
en croyant chaque fin de tour sauvegardée, et découvre la perte au redémarrage.

**Correctif** — `SaveRepository.saveGame` renvoie désormais `Boolean` (`false` si l'écriture
échoue, y compris quand le renommage final échoue). `GameViewModel` remonte l'échec sur le canal
de notifications existant : « ÉCHEC DE LA SAUVEGARDE AUTOMATIQUE » (en rouge).
**Tests** : `saveGameReportsSuccess`, `saveGameReportsFailureWhenDirectoryIsUnusable`.

## 3. Vérification de compatibilité (S3) — ✅ **testé**

Le risque documenté dans `CLAUDE.md` est réel : sans couche de migration, **tout champ
`@Serializable` sans valeur par défaut casse chaque sauvegarde existante**. Les deux champs
ajoutés durant cette série d'audits sont conformes :

| Champ ajouté | Défaut | Sauvegardes existantes |
|--------------|--------|------------------------|
| `GameMap.seed` | `0L` | ✅ chargent, seed = 0 |
| `ResearchProgress.costPaid` | `0` | ✅ chargent, remboursement d'annulation = 0 |

Ce n'était jusqu'ici qu'une **promesse non vérifiée**. `SavedGameSnapshotCodecTest` la teste
désormais réellement : il encode un état, **retire les nouvelles clés du JSON** pour simuler une
sauvegarde ancienne, et vérifie que le décodage réussit avec les valeurs par défaut. Sont aussi
couverts : clés inconnues d'une version plus récente ignorées, rejet d'une sauvegarde de version
future (`SaveVersionException`), et non-persistance des champs `@Transient`
(`visibleHexes`, `lastCombatEvent`).

> ℹ️ Conséquence fonctionnelle mineure et acceptée : une recherche en cours dans une **ancienne**
> sauvegarde a `costPaid = 0`, donc son annulation ne rembourse rien. Aucune perte de crédits
> (ils avaient déjà été débités), et le cas disparaît à la recherche suivante.

### S4 — 🟡 Écriture du slot 1 rendue atomique  ✅ **corrigé**

`saveGame` faisait `file1.delete()` puis `tmp.renameTo(file1)` : entre les deux, le slot 1
n'existait **nulle part**. Un crash (ou une mort du process par l'OS) pile dans cette fenêtre
perdait la sauvegarde la plus récente. Le `delete()` préalable était nécessaire parce que
`File.renameTo` ne remplace pas une cible existante sur toutes les plateformes.

**Correctif** — remplacement par `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)`, disponible depuis
l'API 26 (soit exactement notre `minSdk`) : le slot 1 passe de l'ancien au nouveau contenu en une
seule opération du système de fichiers, sans état intermédiaire. Si un système de fichiers exotique
ne sait pas faire d'échange atomique (`AtomicMoveNotSupportedException`), on retombe sur un
remplacement simple plutôt que d'échouer la sauvegarde.
**Tests** : `saveLeavesNoTempFileBehind`, `repeatedSavesKeepSlot1Loadable`.

> Reste hors périmètre : aucun `fsync` n'est forcé, donc une coupure d'alimentation brutale peut
> encore perdre des données encore en cache d'écriture de l'OS. Les slots 2/3 servent de filet.

### S5 — 🟡 Couche de migration montante  ✅ **corrigé**

`decode` rejetait bien une sauvegarde **plus récente** (`version > CURRENT_VERSION`), mais acceptait
toute version antérieure **sans transformation** : il n'existait aucun endroit où écrire une
migration. Tant que `CURRENT_VERSION` vaut 1 c'est sans conséquence — le piège est le jour où
quelqu'un incrémente la version pour un changement cassant : les anciennes sauvegardes seraient
alors désérialisées telles quelles, avec des champs manquants ou mal interprétés.

**Correctif** — nouveau `SaveMigrations` :

- `SaveMigration(fromVersion, apply)` décrit **une** étape `N → N+1` opérant sur le **JSON brut**
  (et non sur `GameState`) : une vieille sauvegarde peut ne pas correspondre du tout aux data
  classes actuelles, et au moment de la désérialisation il est déjà trop tard pour la réparer.
- `migrate(root, from, to, steps)` enchaîne les étapes et **estampille** la version résultante.
  Une étape manquante lève `SaveVersionException` — que `SaveManager` traite déjà comme « ignorer
  ce slot sans le mettre en quarantaine », ce qui est le bon comportement pour un fichier intact
  mais illisible par ce build.
- `decode` lit désormais la version dans le JSON brut **avant** de désérialiser, et applique la
  chaîne. Une sauvegarde **sans clé `version`** (antérieure au versionnage) est traitée comme le
  schéma le plus ancien plutôt que supposée à jour.

`SaveMigrations.ALL` est volontairement **vide** : aucun schéma ancien n'existe encore. Pour livrer
la première vraie migration : incrémenter `CURRENT_VERSION`, ajouter une `SaveMigration` dont le
`fromVersion` est la version précédente, et la couvrir par un test nourri de vrai JSON ancien.

**Tests** — `SaveMigrationsTest` valide le mécanisme avec une chaîne **factice** (la seule façon de
le prouver tant que `ALL` est vide) : application des étapes dans l'ordre, estampillage de la
version finale, non-copie quand il n'y a rien à faire, et `SaveVersionException` sur chaîne
incomplète. `productionChainIsCompleteUpToCurrentVersion` est le garde-fou : trivialement vert
aujourd'hui, il **échouera** dès qu'on incrémentera `CURRENT_VERSION` en oubliant la migration
correspondante. Côté codec, `saveWithoutVersionKeyIsTreatedAsOldestSchema` couvre le cas
pré-versionnage.

## 4. Limites restantes (signalées, non modifiées)

- **Durabilité** : aucun `fsync` n'est forcé après écriture. Le déplacement atomique (S4) garantit
  qu'on ne voit jamais un slot 1 à moitié écrit, mais une coupure d'alimentation brutale peut
  encore perdre des données restées en cache d'écriture de l'OS. Les slots 2/3 servent de filet.
- **Rotation non transactionnelle** : les copies 2→3 puis 1→2 précèdent l'écriture du nouveau
  slot 1. Un crash au milieu peut laisser deux slots identiques — sans perte de données, mais
  l'historique est momentanément moins profond.
- **Pas de sauvegarde manuelle** : seul l'auto-save de fin de tour existe (3 slots en anneau) ;
  ni emplacement nommé, ni export.

## 5. Ce qui fonctionne bien

- **Tampon circulaire à 3 slots** : rotation 2→3, 1→2, nouveau→1 — une corruption n'emporte pas
  tout l'historique.
- **Quarantaine plutôt que suppression** : un fichier illisible est déplacé dans
  `saves/quarantine/` (horodaté), jamais détruit — diagnostic possible a posteriori.
- **Distinction corruption / version future** : une sauvegarde écrite par une version plus récente
  n'est **pas** mise en quarantaine ; elle est ignorée et un message explicite est remonté.
- **Auto-sauvegarde après résolution** : l'observateur du compteur de tours capture l'état
  **après** la boucle d'IA asynchrone (et non l'instantané pré-tour).
- **Vision recalculée au chargement** : `visibleHexes` est `@Transient` et `reduce(LoadGame)`
  appelle `updateVision` — pas de brouillard incohérent après restauration.
