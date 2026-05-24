package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "peerx_account_pref")

class AccountDataStore(private val context: Context) {
    companion object {
        private val KEY_HASH = stringPreferencesKey("hash")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_PHOTO = stringPreferencesKey("photo_base64")
        private val KEY_HIDE_INFO = booleanPreferencesKey("hide_info")
        private val KEY_THEME = booleanPreferencesKey("is_dark_theme")
    }

    fun getAccount(): Flow<Account?> = context.dataStore.data.map { preferences ->
        val hash = preferences[KEY_HASH] ?: return@map null
        val name = preferences[KEY_NAME] ?: return@map null
        val photo = preferences[KEY_PHOTO]
        val hideInfo = preferences[KEY_HIDE_INFO] ?: false
        val isDarkTheme = preferences[KEY_THEME] ?: true
        Account(hash, name, photo, hideInfo, isDarkTheme)
    }

    suspend fun saveAccount(account: Account) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HASH] = account.hash
            preferences[KEY_NAME] = account.name
            if (account.photoBase64 != null) {
                preferences[KEY_PHOTO] = account.photoBase64
            } else {
                preferences.remove(KEY_PHOTO)
            }
            preferences[KEY_HIDE_INFO] = account.hideInfo
            preferences[KEY_THEME] = account.isDarkTheme
        }
    }

    suspend fun updateName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NAME] = name
        }
    }

    suspend fun updatePhoto(base64: String?) {
        context.dataStore.edit { preferences ->
            if (base64 == null) {
                preferences.remove(KEY_PHOTO)
            } else {
                preferences[KEY_PHOTO] = base64
            }
        }
    }

    suspend fun updateHideInfo(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HIDE_INFO] = hide
        }
    }

    suspend fun updateTheme(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = isDark
        }
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
