# Cores Libretro à Ajouter

## 🎯 Objectif
Ajouter les cores manquants pour supporter plus de consoles en NATIF

## 📊 Status Actuel - 9 Cores Présents

| Core | Console | Status | Fichier |
|------|---------|--------|---------|
| PCSX ReARMed | PSX | ✅ Fonctionne | `pcsx_rearmed_libretro_android.so` |
| PPSSPP | PSP | ✅ Fonctionne | `ppsspp_libretro_android.so` |
| Parallel N64 | N64 | ✅ Fonctionne | `parallel_n64_libretro_android.so` |
| Snes9x | SNES | ✅ Fonctionne | `snes9x_libretro_android.so` |
| FCEUmm | NES | ✅ Fonctionne | `fceumm_libretro_android.so` |
| mGBA | GBA | ✅ Fonctionne | `libmgba_libretro_android.so` |
| Gambatte | GB/GBC | ✅ Fonctionne | `gambatte_libretro_android.so` |
| Handy | Lynx | ❌ Ne fonctionne pas | `handy_libretro_android.so` |
| Genesis Plus GX | Genesis/SegaCD | ✅ Fonctionne | `genesis_plus_gx_libretro_android.so` |

**Genesis Plus GX supporte aussi:**
- Master System ✅
- Game Gear ✅

## 🚀 Cores à Télécharger (Priorité)

### Haute Priorité (Consoles Populaires)

| Console | Core | Buildbot URL |
|---------|------|--------------|
| Atari 2600 | Stella2014 | `stella2014_libretro_android.so` |
| Master System | Genesis Plus GX | ✅ Déjà présent |
| Game Gear | Genesis Plus GX | ✅ Déjà présent |
| Sega 32X | PicoDrive | `picodrive_libretro_android.so` |
| Neo Geo Pocket | Beetle NeoPop | `mednafen_ngp_libretro_android.so` |
| WonderSwan | Beetle Cygne | `mednafen_wswan_libretro_android.so` |
| Virtual Boy | Beetle VB | `beetle_vb_libretro_android.so` |
| Arcade | MAME 2003 Plus | `mame2003_plus_libretro_android.so` |

### Priorité Moyenne

| Console | Core | Buildbot URL |
|---------|------|--------------|
| Atari 5200 | A5200 | `a5200_libretro_android.so` |
| Atari 7800 | ProSystem | `prosystem_libretro_android.so` |
| PC Engine | Beetle PCE | `mednafen_pce_libretro_android.so` |
| Sega Saturn | Beetle Saturn | `mednafen_saturn_libretro_android.so` |
| Nintendo DS | melonDS | `melonds_libretro_android.so` |

### Priorité Basse (Lourds/Expérimentaux)

| Console | Core | Buildbot URL |
|---------|------|--------------|
| Atari Jaguar | Virtual Jaguar | `virtualjaguar_libretro_android.so` |
| 3DO | Opera | `opera_libretro_android.so` |
| Dreamcast | Flycast | `flycast_libretro_android.so` |

## 📥 Source des Cores

**Buildbot Libretro (Nightly Builds):**
```
https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/
```

## 📝 Procédure d'Ajout

Pour chaque core:

1. **Télécharger depuis buildbot:**
   ```bash
   # Exemple pour Stella2014
   curl -O https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/stella2014_libretro_android.so.zip
   ```

2. **Extraire et copier:**
   ```bash
   unzip stella2014_libretro_android.so.zip
   cp stella2014_libretro_android.so app/src/main/jniLibs/arm64-v8a/
   ```

3. **Mettre à jour NativeComposeEmulatorActivity.kt:**
   ```kotlin
   "atari2600", "atari" -> "stella2014_libretro_android.so"
   ```

4. **Tester avec une ROM**

## ⚠️ Notes Importantes

- Tous les cores ne fonctionnent pas forcément sur tous les appareils
- Certains cores nécessitent des BIOS (PSX, GBA, NDS, PSP)
- Les cores lourds (Saturn, Dreamcast) peuvent avoir des problèmes de performance
- Tester chaque core avec une ROM connue avant de déployer

## 🔧 Debugging Lynx

**Le core Handy est présent mais ne fonctionne pas. Vérifier:**
1. Version du core (peut-être obsolète)
2. Format des ROMs (.lnx)
3. Logs Libretro pour erreurs spécifiques
4. Tester avec une ROM connue comme "California Games"

---

*Document créé le 20 octobre 2025*

