# Horloge Météo – widget e-ink (Bigme HiBreak S N&B)

Widget Android **4 × 2** noir sur fond blanc, pensé pour écran e-ink :

```
┌──────────────────────────────────────┬───────────┐
│                                      │  ☁  12°   │
│              14:32                   │ Ressenti 9°│
│                                      │ ↓5°  ↑14° │
│        Lundi 16 novembre 2026        │   Lyon    │
│                                      │ Màj 14:30 ⟳│
└──────────────────────────────────────┴───────────┘
        ≈ 3 colonnes                      1 colonne
```

- **Heure** (HH:mm) en grand, mise à jour chaque minute par le système (`TextClock`).
- **Date** en toutes lettres, redessinée juste après minuit.
- **Météo** : grande icône, température réelle et ressentie, mini/maxi du jour, lieu,
  heure de dernière actualisation.
- **Toucher la zone météo** → actualisation manuelle (« Actualisation… » s'affiche).
- **Toucher l'heure** → écran de réglages.
- Actualisation automatique : 15 min, 30 min, 1 h (défaut), 3 h ou manuelle uniquement.
- Météo fournie par [Open-Meteo](https://open-meteo.com) (gratuit, sans compte ni clé d'API).
- Aucune animation, aucun gris : uniquement noir et blanc pour limiter le ghosting.

## Installation

1. Récupérer l'APK : onglet **Actions** du dépôt GitHub → dernier build → artefact
   `HorlogeMeteo-apk` (ou compiler : `./gradlew assembleRelease`).
2. Copier l'APK sur la HiBreak et l'installer (autoriser les sources inconnues).
3. Appui long sur l'écran d'accueil → **Widgets** → « Heure, date et météo » → placer en 4 × 2.
4. L'écran de réglages s'ouvre : rechercher une ville (ou « Utiliser ma position actuelle »).

> Sur les liseuses Bigme, pensez à exclure l'application de l'optimisation de batterie
> si l'actualisation automatique ne se fait pas en arrière-plan.

## Technique

- Kotlin, minSdk 26, targetSdk 35, aucune dépendance hors AndroidX WorkManager.
- `ClockWeatherWidget` : AppWidgetProvider (rafraîchissement manuel, alarme de minuit).
- `WeatherWorker` : WorkManager, télécharge la météo et redessine le widget.
- `WidgetRenderer` : construit les `RemoteViews`.
- `SettingsActivity` : choix du lieu et de la fréquence (sert aussi d'écran de configuration).
