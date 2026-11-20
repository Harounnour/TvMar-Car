# tvMar Car — Android Auto + application téléphone

- **Android Auto** : application média (Media3). Arborescence : القرآن الكريم (tous les récitateurs, groupés par lettre, toutes les riwayat) / إذاعات القرآن / الإذاعات المغربية.
- **Téléphone** : interface RTL (fond dégradé + motif géométrique islamique), recherche, mini-lecteur.
- Données chargées en ligne puis mises en cache : API mp3quran.net (récitateurs, radios Coran) et Radio-Browser (radios du Maroc, pays=MA). Secours hors-ligne : 4 récitateurs.
- Choisir une sourate met toute la riwaya en file (enchaînement automatique).

## Construire
Android Studio → ouvrir le dossier → Sync → Build APK → installer sur le téléphone.

## Tester
- Voiture : USB → tvMar Car dans les applications média.
- Hors Play Store : Android Auto > Paramètres > 10 tapes sur « Version » > Paramètres développeur > « Sources inconnues ».
- Sans voiture : Desktop Head Unit (SDK Manager).

Note : Android Auto impose son propre style (listes/lecteur standards) : fond et design personnalisés uniquement sur le téléphone.
