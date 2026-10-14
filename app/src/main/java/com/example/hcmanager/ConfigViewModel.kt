package com.example.hcmanager

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val query = MutableStateFlow("")
    val filter = MutableStateFlow<ConfigStatus?>(null)

    val configs: StateFlow<List<ConfigEntity>> =
        combine(dao.observeAll(), query, filter) { all, q, f ->
            all.filter { c ->
                (f == null || c.status == f.name) &&
                    (q.isBlank() ||
                        c.name.contains(q, ignoreCase = true) ||
                        c.note.contains(q, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observe(id: Long): Flow<ConfigEntity?> = dao.observe(id)

    fun importFile(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val name = queryName(uri)
                    
                    val dir = File(getApplication<Application>().filesDir, "configs").apply { mkdirs() }
                    val safe = name.replace(Regex("[^\\w.\\-]"), "_")
                    val dest = File(dir, "${System.currentTimeMillis()}_$safe")
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                    dao.insert(
                        ConfigEntity(
                            name = name,
                            filePath = dest.absolutePath,
                            sizeBytes = dest.length(),
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    "تم استيراد $name"
                } catch (e: Exception) {
                    "فشل الاستيراد: ${e.message}"
                }
            }
            onResult(msg)
        }
    }

    private fun queryName(uri: Uri): String {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) return it.getString(0) }
        return uri.lastPathSegment ?: "config.hc"
    }

    fun setNote(id: Long, note: String) = viewModelScope.launch { dao.updateNote(id, note) }

    fun setStatus(id: Long, s: ConfigStatus) = viewModelScope.launch { dao.updateStatus(id, s.name) }

    fun delete(c: ConfigEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { File(c.filePath).delete() }
        dao.delete(c)
    }
}
