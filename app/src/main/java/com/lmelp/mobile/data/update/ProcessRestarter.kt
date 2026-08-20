package com.lmelp.mobile.data.update

/**
 * Ferme le process de l'app après un remplacement réussi de lmelp.db (issue #118).
 *
 * Nécessaire car LmelpDatabase garde un singleton Room en mémoire (INSTANCE) : après avoir
 * remplacé le fichier physique, seul un redémarrage complet du process garantit qu'aucun état
 * en mémoire (StateFlow des repositories, connexions DAO) ne reste incohérent avec le nouveau
 * fichier. Équivalent Kotlin du am force-stop du script bash legacy
 * (scripts/docker_export_and_push.sh).
 *
 * ⚠️ Ne tente PAS de relance automatique : le pattern AlarmManager + PendingIntent.getActivity
 * pour relancer une Activity depuis une alarme échoue silencieusement sur Android 12+ à cause
 * des restrictions de lancement d'activité en arrière-plan (confirmé en test manuel sur Android
 * 17/API 37). L'utilisateur doit rouvrir l'app lui-même — voir message affiché avant fermeture
 * dans AboutScreen (formatUpdateStateLabel, état DataUpdateState.Restarting).
 */
object ProcessRestarter {
    /** Délai avant fermeture du process, laissant le temps d'afficher un message à l'utilisateur. */
    const val RESTART_DELAY_MS = 2000L

    fun closeApp() {
        Runtime.getRuntime().exit(0)
    }
}
