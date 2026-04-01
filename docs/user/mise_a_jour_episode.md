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

Ensuite il faut passer cette nouvelle base de donnees vers l'application mobile (meme si le logiciel lmelp-mobile ne change pas)

C'est l'objet de [[deploiement] faciliter la mise a jour de la base de donnees #81](https://github.com/castorfou/lmelp-mobile/issues/81)
