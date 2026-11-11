package com.parcha.spoilnot.core.firebase

/**
 * The fields of [after] whose values differ from [before]: what an edit writes, so it never puts back a field another
 * member changed meanwhile (after v1, DESIGN 9.4).
 */
fun changedFields(before: Map<String, Any?>, after: Map<String, Any?>): Map<String, Any?> =
    after.filter { (key, value) -> !before.containsKey(key) || before[key] != value }
