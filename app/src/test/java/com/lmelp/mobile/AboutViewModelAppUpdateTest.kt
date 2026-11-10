package com.lmelp.mobile

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.model.AppUpdateCheckResult
import com.lmelp.mobile.data.model.AppUpdateState
import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.HttpStatusException
import com.lmelp.mobile.data.repository.AppUpdateRepository
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.update.ApkInstallOutcome
import com.lmelp.mobile.viewmodel.AboutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

/**
 * Partie « nouvelle version de l'appli » d'AboutViewModel (issue #162).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelAppUpdateTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val release = AppRelease(
        tag = "v1.3.0",
        prerelease = false,
        draft = false,
        apkUrl = "https://example.org/lmelp-mobile-v1.3.0.apk",
        apkSha256 = null,
        htmlUrl = "https://github.com/castorfou/lmelp-mobile/releases/tag/v1.3.0"
    )

    private val downloadDir = File("unused")
    private val apkFile = File("lmelp-mobile-v1.3.0.apk")

    private suspend fun viewModel(appRepo: AppUpdateRepository): AboutViewModel {
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getDbInfo()).thenReturn(DbInfoUi("—", "—", "—", "—", "—"))
        val dataRepo = mock<DataUpdateRepository>()
        whenever(dataRepo.lastCheckResult).thenReturn(MutableStateFlow(UpdateCheckResult.UpToDate))
        whenever(dataRepo.checkForUpdate()).thenReturn(UpdateCheckResult.UpToDate)
        return AboutViewModel(metadataRepository, dataRepo, appRepo)
    }

    private fun appRepo(lastResult: AppUpdateCheckResult = AppUpdateCheckResult.UpToDate): AppUpdateRepository {
        val repo = mock<AppUpdateRepository>()
        whenever(repo.lastCheckResult).thenReturn(MutableStateFlow(lastResult))
        return repo
    }

    @Test
    fun `le resultat du check silencieux au lancement est reflete sans nouvel appel`() = runTest {
        val vm = viewModel(appRepo(AppUpdateCheckResult.UpdateAvailable(release)))
        advanceUntilIdle()
        assertEquals(AppUpdateState.UpdateAvailable(release), vm.appUpdateState.value)
    }

    @Test
    fun `sans nouvelle version, l'etat reste Idle`() = runTest {
        val vm = viewModel(appRepo())
        advanceUntilIdle()
        assertEquals(AppUpdateState.Idle, vm.appUpdateState.value)
    }

    @Test
    fun `le check manuel verifie aussi la version de l'appli`() = runTest {
        val repo = appRepo()
        whenever(repo.checkForUpdate()).thenReturn(AppUpdateCheckResult.UpdateAvailable(release))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.checkForUpdateManually()
        advanceUntilIdle()

        assertEquals(AppUpdateState.UpdateAvailable(release), vm.appUpdateState.value)
    }

    @Test
    fun `installer telecharge l'APK puis lance l'installateur`() = runTest {
        val repo = appRepo(AppUpdateCheckResult.UpdateAvailable(release))
        whenever(repo.downloadApk(any(), any())).thenReturn(apkFile)
        val vm = viewModel(repo)
        advanceUntilIdle()
        var installed: File? = null

        vm.installAppUpdate(downloadDir) { file ->
            installed = file
            ApkInstallOutcome.InstallerLaunched
        }
        advanceUntilIdle()

        assertEquals(apkFile, installed)
        assertEquals(AppUpdateState.Installing(release), vm.appUpdateState.value)
    }

    @Test
    fun `sans autorisation d'installer, l'etat demande l'autorisation`() = runTest {
        val repo = appRepo(AppUpdateCheckResult.UpdateAvailable(release))
        whenever(repo.downloadApk(any(), any())).thenReturn(apkFile)
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.installAppUpdate(downloadDir) { ApkInstallOutcome.NeedsPermission }
        advanceUntilIdle()

        assertEquals(AppUpdateState.NeedsPermission(release), vm.appUpdateState.value)
    }

    @Test
    fun `un echec de telechargement donne une Error qui garde la release pour reessayer`() = runTest {
        val repo = appRepo(AppUpdateCheckResult.UpdateAvailable(release))
        whenever(repo.downloadApk(any(), any())).thenAnswer { throw IOException("HTTP 500") }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.installAppUpdate(downloadDir) { ApkInstallOutcome.InstallerLaunched }
        advanceUntilIdle()

        val state = vm.appUpdateState.value
        assertTrue(state is AppUpdateState.Error)
        assertEquals(release, (state as AppUpdateState.Error).release)
    }

    @Test
    fun `GitHub indisponible au telechargement donne un message clair sans code HTTP brut`() = runTest {
        val repo = appRepo(AppUpdateCheckResult.UpdateAvailable(release))
        whenever(repo.downloadApk(any(), any())).thenAnswer {
            throw HttpStatusException(504, "Échec du téléchargement de l'APK : HTTP 504")
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.installAppUpdate(downloadDir) { ApkInstallOutcome.InstallerLaunched }
        advanceUntilIdle()

        val message = (vm.appUpdateState.value as AppUpdateState.Error).message
        assertTrue(message, message.contains("momentanément indisponible"))
        assertTrue(message, !message.contains("HTTP 504"))
    }

    @Test
    fun `installer sans version disponible ne fait rien`() = runTest {
        val vm = viewModel(appRepo())
        advanceUntilIdle()
        var called = false

        vm.installAppUpdate(downloadDir) {
            called = true
            ApkInstallOutcome.InstallerLaunched
        }
        advanceUntilIdle()

        assertEquals(false, called)
        assertEquals(AppUpdateState.Idle, vm.appUpdateState.value)
    }
}
