package eu.kanade.domain.source.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val uiPreferences: UiPreferences,
    private val extensionManager: ExtensionManager,
) {
    fun await(sourceId: Long?): Boolean {
        // If history tab is disabled, treat as incognito mode (no history recording)
//        if (!uiPreferences.showHistoryTab().get()) return true
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        val extensionPackage = extensionManager.getExtensionPackage(sourceId) ?: return false

        return extensionPackage in sourcePreferences.incognitoExtensions().get()
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()
//        if (sourceId == null) {
//            return combine(
//                basePreferences.incognitoMode().changes(),
//                uiPreferences.showHistoryTab().changes(),
//            ) { incognito, showHistory ->
//                incognito || !showHistory
//            }.distinctUntilChanged()
//        }

        return combine(
            basePreferences.incognitoMode().changes(),
//            uiPreferences.showHistoryTab().changes(),
            sourcePreferences.incognitoExtensions().changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
        ) { incognito, incognitoExtensions, extensionPackage ->
            incognito || (extensionPackage in incognitoExtensions)
        }
            .distinctUntilChanged()
    }
}
