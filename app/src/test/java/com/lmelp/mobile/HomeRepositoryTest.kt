package com.lmelp.mobile

import android.content.SharedPreferences
import android.content.res.AssetManager
import com.lmelp.mobile.data.repository.HomeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour HomeRepository (JVM, sans dépendance Android).
 * Couvre extractCouvertureUrl et clearCouverturesCache.
 */
class HomeRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /** Crée un repository dont le cache JSON est dans un dossier temporaire JVM. */
    private fun buildRepository(assetsJson: String? = null): HomeRepository {
        val ctx = mock<android.content.Context>()
        // getExternalFilesDir(null) → dossier temporaire JVM (pas d'Android requis)
        whenever(ctx.getExternalFilesDir(null)).thenReturn(tmpFolder.root)
        whenever(ctx.filesDir).thenReturn(tmpFolder.root)
        // SharedPreferences vide pour la purge legacy
        val emptyPrefs = mock<SharedPreferences>()
        whenever(emptyPrefs.all).thenReturn(emptyMap())
        whenever(ctx.getSharedPreferences(any(), any())).thenReturn(emptyPrefs)
        // AssetManager mocké pour le fallback assets/
        val assets = mock<AssetManager>()
        if (assetsJson != null) {
            whenever(assets.open("couvertures_cache.json"))
                .thenReturn(assetsJson.byteInputStream())
        } else {
            whenever(assets.open("couvertures_cache.json"))
                .thenThrow(java.io.FileNotFoundException("couvertures_cache.json"))
        }
        whenever(ctx.assets).thenReturn(assets)
        return HomeRepository(
            metadataDao = mock(),
            emissionsDao = mock(),
            palmaresDao = mock(),
            livresDao = mock(),
            recommendationsDao = mock(),
            onKindleDao = mock(),
            context = ctx
        )
    }

    // ── extractCouvertureUrl ──────────────────────────────────────────────────

    @Test
    fun `extractCouvertureUrl retourne URL absolue Babelio depuis chemin relatif`() {
        val repo = buildRepository()
        val html = """<img src="/couv/CVT_livre-test_9782070413867.jpg" />"""
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://www.babelio.com/couv/CVT_livre-test_9782070413867.jpg", result)
    }

    @Test
    fun `extractCouvertureUrl retourne URL Amazon`() {
        val repo = buildRepository()
        val html = """<img src="https://m.media-amazon.com/images/I/51Abc123.jpg" />"""
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://m.media-amazon.com/images/I/51Abc123.jpg", result)
    }

    @Test
    fun `extractCouvertureUrl retourne URL Babelio absolue`() {
        val repo = buildRepository()
        val html = """<img src="https://www.babelio.com/couv/CVT_mon-livre_123.jpg" />"""
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://www.babelio.com/couv/CVT_mon-livre_123.jpg", result)
    }

    @Test
    fun `extractCouvertureUrl retourne null si HTML vide`() {
        val repo = buildRepository()
        val result = repo.extractCouvertureUrl("")
        assertNull(result)
    }

    @Test
    fun `extractCouvertureUrl retourne null si aucun pattern reconnu`() {
        val repo = buildRepository()
        val html = """<p>Aucune image ici</p>"""
        val result = repo.extractCouvertureUrl(html)
        assertNull(result)
    }

    @Test
    fun `extractCouvertureUrl retourne ogimage en priorite`() {
        val repo = buildRepository()
        val html = """
            <meta property="og:image" content="https://www.babelio.com/couv/CVT_Hors-champ_8582.jpg"/>
            <img src="/couv/CVT_autre-livre_999.jpg" />
            <img src="https://m.media-amazon.com/images/I/51Abc.jpg" />
        """.trimIndent()
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://www.babelio.com/couv/CVT_Hors-champ_8582.jpg", result)
    }

    @Test
    fun `extractCouvertureUrl retourne ogimage forme alternative`() {
        val repo = buildRepository()
        val html = """<meta content="https://www.babelio.com/couv/CVT_Eva_2053.jpg" property="og:image"/>"""
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://www.babelio.com/couv/CVT_Eva_2053.jpg", result)
    }

    @Test
    fun `extractCouvertureUrl fallback sur CVT relatif si pas de ogimage`() {
        val repo = buildRepository()
        val html = """
            <img src="https://m.media-amazon.com/images/I/51Abc.jpg" />
            <img src="/couv/CVT_babelio.jpg" />
        """.trimIndent()
        val result = repo.extractCouvertureUrl(html)
        assertEquals("https://www.babelio.com/couv/CVT_babelio.jpg", result)
    }

    // ── clearCouverturesCache ─────────────────────────────────────────────────

    @Test
    fun `clearCouverturesCache supprime le fichier JSON de cache`() {
        val repo = buildRepository()
        // Crée le fichier de cache manuellement pour vérifier qu'il est supprimé
        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        cacheFile.writeText("{\"123\":\"https://example.com/image.jpg\"}")

        repo.clearCouverturesCache()

        assertFalse("Le fichier cache doit être supprimé", cacheFile.exists())
    }

    // ── invalidation de version ────────────────────────────────────────────────

    @Test
    fun `getCachedCouverture ignore un cache sans version et le supprime`() {
        // Simule un cache JSON issu d'une ancienne version de l'app (pas de _version)
        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        val urlBabelio = "https://www.babelio.com/livres/Lafon-Hors-champ/1928054"
        val wrongImageUrl = "http://ecx.images-amazon.com/images/I/51Y5TPTTPRL._SX95_.jpg"
        cacheFile.writeText("""{"$urlBabelio":"$wrongImageUrl"}""")

        val repo = buildRepository()
        val result = repo.getCachedCouverture(urlBabelio)

        // Le cache sans version doit être ignoré → null (pas la mauvaise URL)
        assertNull("Une entrée de cache sans _version doit être ignorée", result)
        assertFalse("Le fichier cache obsolète doit être supprimé", cacheFile.exists())
    }

    @Test
    fun `getCachedCouverture utilise un cache avec la bonne version`() {
        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        val urlBabelio = "https://www.babelio.com/livres/Lafon-Hors-champ/1928054"
        val correctImageUrl = "https://www.babelio.com/couv/CVT_Hors-champ_8582.jpg"
        cacheFile.writeText("""{"_version":2,"$urlBabelio":"$correctImageUrl"}""")

        val repo = buildRepository()
        val result = repo.getCachedCouverture(urlBabelio)

        assertEquals("Une entrée de cache avec la bonne version doit être retournée", correctImageUrl, result)
    }

    // ── validation du titre de page Babelio ───────────────────────────────────

    @Test
    fun `pageTitleMatchesLivre retourne true si le titre du livre est dans la page`() {
        val repo = buildRepository()
        // La page Babelio retourne "Combats de filles - Rita Bullwinkel - Babelio"
        val pageTitle = "Combats de filles - Rita Bullwinkel - Babelio"
        assertTrue("Le titre du livre doit être reconnu dans le titre de la page",
            repo.pageTitleMatchesLivre(pageTitle, "Combats de filles"))
    }

    @Test
    fun `pageTitleMatchesLivre retourne false si c'est une mauvaise page`() {
        val repo = buildRepository()
        // Babelio a renvoyé la page d'un autre livre
        val pageTitle = "La Famille Vador - Intégrale - Babelio"
        assertFalse("Un titre de page qui ne correspond pas doit être rejeté",
            repo.pageTitleMatchesLivre(pageTitle, "Combats de filles"))
    }

    @Test
    fun `pageTitleMatchesLivre est insensible a la casse et aux accents`() {
        val repo = buildRepository()
        val pageTitle = "Hors champ - Marie-Hlne Lafon - Babelio" // Babelio omet parfois les accents
        assertTrue(repo.pageTitleMatchesLivre(pageTitle, "Hors champ"))
    }

    @Test
    fun `pageTitleMatchesLivre retourne true pour titre tres court type Eva`() {
        val repo = buildRepository()
        val pageTitle = "Eva - Simon Liberati - Babelio"
        assertTrue(repo.pageTitleMatchesLivre(pageTitle, "Eva"))
    }

    @Test
    fun `pageTitleMatchesLivre retourne true pour titre avec mots courts Le nom sur le mur`() {
        val repo = buildRepository()
        val pageTitle = "Le Nom sur le mur - Hervé Le Tellier - Babelio"
        assertTrue(repo.pageTitleMatchesLivre(pageTitle, "Le nom sur le mur"))
    }

    @Test
    fun `pageTitleMatchesLivre retourne true si au moins un mot long du titre correspond`() {
        val repo = buildRepository()
        // Titre avec article : "Les Belles Promesses" → "Promesses" suffit
        val pageTitle = "Les Belles Promesses - Pierre Lemaitre - Babelio"
        assertTrue(repo.pageTitleMatchesLivre(pageTitle, "Les Belles Promesses"))
    }

    // ── fallback assets/ ──────────────────────────────────────────────────────

    @Test
    fun `getCachedCouverture utilise le cache embarque dans assets si pas de fichier externe`() {
        // Pas de fichier externe dans tmpFolder → le repo doit lire depuis assets/
        val urlBabelio = "https://www.babelio.com/livres/Boyer-Le-lievre/1313246"
        val imageUrl = "https://www.babelio.com/couv/CVT_Le-lievre_1313.jpg"
        val assetsJson = """{"_version":2,"$urlBabelio":"$imageUrl"}"""

        val repo = buildRepository(assetsJson = assetsJson)
        val result = repo.getCachedCouverture(urlBabelio)

        assertEquals("Le cache assets/ doit être utilisé en l'absence de fichier externe", imageUrl, result)
    }

    @Test
    fun `getCachedCouverture prefere le fichier externe au cache assets`() {
        // Le fichier externe existe → il doit prendre le dessus sur assets/
        val urlBabelio = "https://www.babelio.com/livres/Boyer-Le-lievre/1313246"
        val externalUrl = "https://www.babelio.com/couv/CVT_Le-lievre-externe_9999.jpg"
        val assetsUrl   = "https://www.babelio.com/couv/CVT_Le-lievre-assets_1313.jpg"

        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        cacheFile.writeText("""{"_version":2,"$urlBabelio":"$externalUrl"}""")

        val assetsJson = """{"_version":2,"$urlBabelio":"$assetsUrl"}"""
        val repo = buildRepository(assetsJson = assetsJson)
        val result = repo.getCachedCouverture(urlBabelio)

        assertEquals("Le fichier externe doit avoir priorité sur assets/", externalUrl, result)
    }

    @Test
    fun `getCachedCouverture accepte les URLs media-amazon dans le cache`() {
        // Les URLs m.media-amazon.com sont stables (CDN actuel) — acceptées dans le cache
        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        val urlBabelio = "https://www.babelio.com/livres/Kefi-Quatre-jours-sans-ma-mere/1866745"
        val amazonUrl = "https://m.media-amazon.com/images/I/41uSEaWdbEL._SX195_.jpg"
        cacheFile.writeText("""{"_version":2,"$urlBabelio":"$amazonUrl"}""")

        val repo = buildRepository()
        val result = repo.getCachedCouverture(urlBabelio)

        assertEquals("Une URL m.media-amazon.com doit être acceptée dans le cache", amazonUrl, result)
    }

    @Test
    fun `getCachedCouverture ignore les URLs amazon CDN instable ecx`() {
        // Les URLs ecx.images-amazon.com sont l'ancien CDN Amazon instable — ignorées
        val cacheFile = java.io.File(tmpFolder.root, "couvertures_cache.json")
        val urlBabelio = "https://www.babelio.com/livres/Bullwinkel-Combats-de-filles/1708135"
        val amazonUrl = "http://ecx.images-amazon.com/images/I/51dZWcCX7vL._SX95_.jpg"
        cacheFile.writeText("""{"_version":2,"$urlBabelio":"$amazonUrl"}""")

        val repo = buildRepository()
        val result = repo.getCachedCouverture(urlBabelio)

        assertNull("Une URL ecx.images-amazon.com ne doit pas être acceptée", result)
    }
}
