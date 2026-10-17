package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.parcha.spoilnot.feature.auth.domain.ProfileDefaults
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit

/** The signed-in member's preferred units, provided by the app shell from their profile. */
val LocalUnits = compositionLocalOf { ProfileDefaults.UNITS }

/** Unit choices in the preferred order (DESIGN 5.6: metric or imperial defaults for new products). */
@Composable
fun unitMenu(): List<QuantityUnit> = QuantityUnit.menu(imperialFirst = LocalUnits.current == Units.Imperial)
