package com.ultradpi.app

enum class BypassPreset(
    val title: String,
    val description: String,
    val args: Array<String>
) {
    UNIVERSAL(
        "⭐ Универсальный (Multi-Split + Fake QUIC)",
        "Нарезка SNI + фейковые QUIC пакеты (по умолчанию для 4G и Wi-Fi)",
        arrayOf(
            "-Ku", "-a3", "-An",
            "-Kt,h",
            "-d1", "-s0+s", "-d3+s", "-s6+s", "-d9+s", "-s12+s", "-d15+s", "-s20+s", "-d25+s", "-s30+s", "-d35+s",
            "-An"
        )
    ),
    PC_MODE(
        "⚡ Режим ПК (Fake TLS www.google.com)",
        "Аналог алгоритма UltraDPI WinWS на ПК с подменой SNI",
        arrayOf(
            "-n", "www.google.com",
            "-f-1", "-t5",
            "-s1+s", "-d3+s",
            "-Ku", "-a3", "-An"
        )
    ),
    MOBILE_OOB(
        "🛡️ Мобильный OOB (МТС / Билайн / МегаФон)",
        "Out-Of-Band байт обхода для операторов сотовой связи",
        arrayOf(
            "-o1+s", "-d1", "-s3+s",
            "-Ku", "-a3", "-An"
        )
    ),
    AGGRESSIVE(
        "🚀 Агрессивный сплит (Tele2 / Ростелеком)",
        "Глубокий сдвиг фейков и усиленная фрагментация",
        arrayOf(
            "-d1+s", "-O1", "-s29+s", "-t5",
            "-Ku", "-a5", "-An"
        )
    );

    companion object {
        fun fromIndex(index: Int): BypassPreset {
            val values = entries
            return if (index in values.indices) values[index] else UNIVERSAL
        }
    }
}
