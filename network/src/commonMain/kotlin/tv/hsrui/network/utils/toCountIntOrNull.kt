package tv.hsrui.network.utils

fun String.toCountIntOrNull(): Int? =
    if (endsWith("万")) removeSuffix("万").toFloatOrNull()?.let { (it * 10000).toInt() }
    else if (endsWith("亿")) removeSuffix("亿").toFloatOrNull()?.let { (it * 100000000).toInt() }
    else toIntOrNull()