package com.parcha.spoilnot.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList

/**
 * A list of picks kept across rotation, theme and font changes and process death (after v1, REL-09). [T] must be
 * savable in a Bundle (a string, number, boolean or enum).
 */
@Composable
fun <T : Any> rememberSaveableList(vararg initial: T): SnapshotStateList<T> =
    rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        initial.toList().toMutableStateList()
    }

/** The same for choices by key, such as a sheet's lines (after v1, REL-09). */
@Composable
fun <K : Any, V : Any> rememberSaveableMap(): SnapshotStateMap<K, V> = rememberSaveable(
    saver = listSaver<SnapshotStateMap<K, V>, Any>(
        save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { saved ->
            @Suppress("UNCHECKED_CAST")
            mutableStateMapOf<K, V>().apply { saved.chunked(2).forEach { (k, v) -> put(k as K, v as V) } }
        },
    ),
) { mutableStateMapOf() }
