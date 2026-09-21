package com.wangye.tftbox.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wangye.tftbox.TftApp
import com.wangye.tftbox.data.Lineup
import com.wangye.tftbox.data.LineupRepository
import com.wangye.tftbox.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : ViewModel() {

    private val repository: LineupRepository = (app as TftApp).repository

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val lineups: StateFlow<List<Lineup>> =
        combine(repository.observeAll(), query) { list, q -> filterList(list, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 外观 ────────────────────────────────────────────────────
    // 主题要包在最外层 MaterialTheme 上，所以状态提到这里，
    // 设置页改完立刻全局生效，不用重启。

    private val _themeMode = MutableStateFlow(Prefs.themeMode(app))
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _paletteId = MutableStateFlow(Prefs.paletteId(app))
    val paletteId: StateFlow<String> = _paletteId.asStateFlow()

    fun setThemeMode(id: String) {
        Prefs.setThemeMode(app, id)
        _themeMode.value = id
    }

    fun setPaletteId(id: String) {
        Prefs.setPaletteId(app, id)
        _paletteId.value = id
    }

    // ── 数据 ────────────────────────────────────────────────────

    fun setQuery(value: String) {
        query.value = value
    }

    /** 新增（id == 0）或更新 */
    fun save(lineup: Lineup, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (lineup.id == 0L) repository.insert(lineup) else repository.update(lineup)
            onDone()
        }
    }

    fun delete(lineup: Lineup) {
        viewModelScope.launch { repository.delete(lineup) }
    }

    fun toggleFavorite(lineup: Lineup) {
        viewModelScope.launch { repository.update(lineup.copy(favorite = !lineup.favorite)) }
    }

    suspend fun existsByCode(code: String): Boolean = repository.findByCode(code) != null

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(application) }
        }

        private fun filterList(list: List<Lineup>, q: String): List<Lineup> {
            if (q.isBlank()) return list
            return list.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.tags.contains(q, ignoreCase = true) ||
                    it.season.contains(q, ignoreCase = true) ||
                    it.note.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true)
            }
        }
    }
}
