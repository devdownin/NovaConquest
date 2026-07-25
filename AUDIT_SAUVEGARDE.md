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
| S4 | 🟡 Faible | Atomicité | Fenêtre `delete()` → `renameTo()` : un crash exactement entre les deux perd le slot 1 (slots 2/3 intacts) |
| S5 | 🟡 Faible | Versionnage | Aucune migration montante : une sauvegarde plus **ancienne** est acceptée telle quelle (OK tant que `CURRENT_VERSION` vaut 1) |

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

## 4. Signalés, non modifiés

- **S4 — Fenêtre d'écriture non atomique.** `saveGame` fait `file1.delete()` puis
  `tmp.renameTo(file1)`. Un crash exactement entre les deux perd le slot 1 (les slots 2 et 3
  restent valides, et `loadLatestGame` sait y retomber — d'autant mieux depuis S1). Le `delete()`
  préalable est nécessaire car `File.renameTo` ne remplace pas la cible sur toutes les plateformes.
  Une correction complète passerait par `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)` (API 26+,
  compatible avec `minSdk` 26).
- **S5 — Pas de migration montante.** `decode` rejette une sauvegarde **plus récente**
  (`version > CURRENT_VERSION`) mais accepte toute version antérieure sans transformation. C'est
  correct tant que `CURRENT_VERSION` vaut 1 ; dès qu'un changement de schéma non rétrocompatible
  sera nécessaire, il faudra incrémenter la version **et** ajouter une étape de migration.

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
