# Horloge Météo – horloge, alarmes et widget e-ink (Bigme HiBreak S N&B)

## L'application

- **Heure du lieu choisi** en très grand (HH:mm:ss), avec la date en toutes lettres.
  Si le lieu est dans un autre fuseau horaire, l'heure de l'appareil est rappelée en dessous.
- **Météo résumée** (toucher pour actualiser).
- **Suivi de position (option)** : le lieu, la météo et l'heure locale suivent vos déplacements
  (position relue à chaque actualisation de la météo ; demande la localisation « Toujours autoriser »).
- **Alarmes** : ajout, modification, suppression, activation par interrupteur.
  - heure, jours de répétition (L M M J V S D, ou une seule fois), nom ;
  - **son au choix** : sonnerie du système, ou n'importe quel fichier audio (MP3…) du stockage,
    avec bouton « Écouter » ;
  - vibration optionnelle ;
  - quand elle sonne : écran plein (même verrouillé) avec **Arrêter** / **Répéter dans 10 min**,
    arrêt automatique après 10 min ; les alarmes sont reprogrammées après redémarrage.

## Le widget

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
- **Profil sonore** en toutes lettres sous la date (Sonnerie activée / Vibreur / Muet), avec une touche
  pour passer au mode suivant (nécessite l'accès « Ne pas déranger », demandé au premier appui).
- **Toucher la zone météo** → actualisation manuelle (« Actualisation… » s'affiche).
- **Toucher l'heure** → ouvre l'application (horloge et alarmes).
- Actualisation automatique : 15 min, 30 min, 1 h (défaut), 3 h ou manuelle uniquement.
- Météo fournie par [Open-Meteo](https://open-meteo.com) (gratuit, sans compte ni clé d'API).
- Aucune animation, aucun gris : uniquement noir et blanc pour limiter le ghosting.

## Installation

1. Télécharger l'APK (lien direct, toujours la dernière version) :
   https://github.com/mdeberly/time/releases/download/apk/HorlogeMeteo.apk
   (ou compiler : `./gradlew assembleRelease`).
2. Copier l'APK sur la HiBreak et l'installer (autoriser les sources inconnues).
3. Appui long sur l'écran d'accueil → **Widgets** → « Heure, date et météo » → placer en 4 × 2.
4. L'écran de réglages s'ouvre : rechercher une ville (ou « Utiliser ma position actuelle »).
5. Au premier réglage d'alarme, autoriser les notifications (nécessaire pour l'écran d'alarme).

> Sur les liseuses Bigme, pensez à exclure l'application de l'optimisation de batterie
> si l'actualisation automatique ne se fait pas en arrière-plan.

## Technique

- Kotlin, minSdk 26, targetSdk 35, aucune dépendance hors AndroidX WorkManager.
- `ClockWeatherWidget` : AppWidgetProvider (rafraîchissement manuel, alarme de minuit).
- `WeatherWorker` : WorkManager, télécharge la météo et redessine le widget.
- `WidgetRenderer` : construit les `RemoteViews`.
- `MainActivity` : horloge du lieu + liste des alarmes.
- `AlarmEditActivity`, `AlarmScheduler` (AlarmManager.setAlarmClock), `AlarmReceiver`,
  `AlarmService` (son en boucle, service au premier plan), `AlarmRingActivity`.
- `SettingsActivity` : choix du lieu et de la fréquence (sert aussi d'écran de configuration).
