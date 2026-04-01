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

- Le téléphone est branché en USB au laptop
- Le mode USB est sur **Transfert de fichiers** (pas "Aucun transfert de données")
- Le débogage USB est activé (Paramètres → Options développeur)
- Le daemon ADB est actif sur le laptop

### Commande unique

```bash
# Sur le laptop
adb start-server
docker compose --profile export run --rm lmelp-export
```

Le container `lmelp-export` fait tout automatiquement :

1. Export MongoDB → SQLite (avec données Calibre pour le filtre "Lus")
2. Vérification d'intégrité de la base
3. Push de la base sur le téléphone via ADB
4. Redémarrage de l'app Android

### En cas de problème ADB

Si `adb devices` ne voit pas le téléphone :

```bash
adb kill-server
adb start-server
adb devices
```

Vérifier que le téléphone affiche bien une popup "Autoriser le débogage USB" et la valider.

Voir aussi [docs/dev/build_deploy_apk.md](../dev/build_deploy_apk.md) pour le diagnostic complet.

### test pendant les developpement

#### build lmelp-mobile-export image

```bash
docker build -f Dockerfile.export -t lmelp-mobile-export:local .
```


#### lancement lmelp-mobile-export container

il faut que adb ecoute sur 0.0.0.0

```bash
adb kill-server
adb -a start-server
```

a verifier avec

```bash
❯ ss -tlnp | grep 5037

LISTEN 0      4                       *:5037             *:*    users:(("adb",pid=583910,fd=10))
```

```bash
docker run --rm \
  --network lmelp-stack_lmelp-network \
  --add-host host-gateway:host-gateway \
  -v "/home/guillaume/Calibre Library:/calibre:ro" \
  -e LMELP_MONGO_URI=mongodb://mongo:27017 \
  -e LMELP_CALIBRE_DB=/calibre/metadata.db \
  -e LMELP_CALIBRE_VIRTUAL_LIBRARY=guillaume \
  -e ADB_HOST=host-gateway \
  -e ADB_PORT=5037 \
  lmelp-mobile-export:local
```
