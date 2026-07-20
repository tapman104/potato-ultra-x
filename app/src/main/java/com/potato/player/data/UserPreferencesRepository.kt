package com.potato.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val SUB_SCALE    = doublePreferencesKey("sub_scale")
        val SUB_POS      = intPreferencesKey("sub_pos")
        val AUTO_ROTATION = booleanPreferencesKey("auto_rotation")
    }

    val subScaleFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[SUB_SCALE] ?: 1.0
    }

    val subPosFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SUB_POS] ?: 100
    }

    val autoRotationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ROTATION] ?: false
    }

    suspend fun setSubScale(scale: Double) {
        context.dataStore.edit { preferences ->
            preferences[SUB_SCALE] = scale
        }
    }

    suspend fun setSubPos(pos: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUB_POS] = pos
        }
    }

    suspend fun setAutoRotation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ROTATION] = enabled
        }
    }
}
