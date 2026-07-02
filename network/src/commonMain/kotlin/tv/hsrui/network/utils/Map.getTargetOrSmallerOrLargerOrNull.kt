package tv.hsrui.network.utils

fun <K : Enum<K>, V> Map<K, V>.getTargetOrSmallerOrLargerOrNull(targetKey: K): V? {

    if (this.containsKey(targetKey)) return this.getValue(targetKey)

    val smallerKey = this.keys
        .filter { it < targetKey }
        .maxOrNull()

    if (smallerKey != null) return this.getValue(smallerKey)

    val largerKey = this.keys
        .filter { it > targetKey }
        .minOrNull()

    if (largerKey != null) return this.getValue(largerKey)

    return null
}