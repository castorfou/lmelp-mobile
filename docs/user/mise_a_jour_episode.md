a la diffusion d'une nouvelle emission, j'ai pas mal de boulot :

> depuis **lmelp-frontoffice** : detection de la nouvelle emission (rafraichir Episodes) - cela telecharge l'enregistrement audio
>
> creation de la transcription (je fais ca manuellement depuis une machine **GPU avec whisper**), et copie dans docker-lmelp/data/audios
>
> depuis **lmelp-frontoffice**: Telecharger la transcription (cela va charger le fichier de transcription) (gestion de cache)
>
> depuis **lmelp-backoffice** - frontend : dans les pages suivantes
>
> - **Generation de l'avis critique** : (avec un llm) depuis la transcription, cela cree un summary pour cet episode
> - **Livres et auteurs** : extrait les livres/auteurs de l'episode sur la base du summary. metdonnees babelio (il faut au prealable lancer le VPN) pour corriger les titres/auteurs/editeurs
> - **Identification des critiques** : si necessaire pour un nouveau critique
> - **Liaison babelio** : pour lier aux pages auteurs / livres / url de la couverture
> - **Emissions** : visu du resultat de l'emission structuree : toutes les oeuvres doivent etre identifiees, notees


a l'issue de tout cela la base de donnees a ete enrichie avec ce nouvel episode

## Passer la nouvelle base vers l'application mobile

Le logiciel lmelp-mobile ne change pas, seule la base de données évolue.

### Pré-requis

- adb est installe
- Le téléphone est branché en USB au laptop
- Le mode USB est sur **Transfert de fichiers** (pas "Aucun transfert de données")
- Le débogage USB est activé (Paramètres → Options développeur)
- La stack docker-lmelp est démarrée (en suivant [Déploiement avec Portainer](https://castorfou.github.io/docker-lmelp/user/portainer/), les conteneurs `lmelp-mongo` et `lmelp-export` doivent tourner)

### Commande unique

```bash
lmelp-update-mobile
```

Le script `scripts/lmelp-update-mobile.sh` (à copier une fois sur le laptop dans `~/bin/`) fait tout :

1. Redémarre le daemon ADB en mode réseau (`0.0.0.0:5037`)
2. Vérifie qu'un téléphone est connecté
3. Vérifie que le container `lmelp-export` tourne
4. Lance l'export MongoDB → SQLite (avec données Calibre pour le filtre "Lus")
5. Vérifie l'intégrité de la base
6. Pousse la base sur le téléphone via ADB
7. Redémarre l'app Android

### Installation du script (une seule fois)

```bash
cp scripts/lmelp-update-mobile.sh ~/bin/lmelp-update-mobile
chmod +x ~/bin/lmelp-update-mobile
```

### En cas de problème ADB

Si `adb devices` ne voit pas le téléphone :

- Vérifier que le mode USB est sur **Transfert de fichiers**
- Valider la popup "Autoriser le débogage USB" sur le téléphone
- En dernier recours : `adb kill-server && adb -a start-server`

Voir aussi [docs/dev/build_deploy_apk.md](../dev/build_deploy_apk.md) pour le diagnostic complet.
