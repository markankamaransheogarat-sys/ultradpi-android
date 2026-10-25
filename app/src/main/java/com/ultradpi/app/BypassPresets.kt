package com.ultradpi.app

enum class BypassPreset(
    val title: String,
    val description: String,
    val args: Array<String>
) {
    UNIVERSAL(
        "⭐ Ультра OOB (Быстрый старт)",
        "Out-Of-Band + TLS Record сплит. Золотой стандарт для YouTube в 4K (МТС, Билайн, МегаФон, Tele2)",
        arrayOf("-o1", "-a1", "-r-5+se")
    ),
    MULTI_SPLIT(
        "🔥 Мульти-сплит (11 сегментов)",
        "Глубокая фрагментация SNI в обратном порядке против жестких ТСПУ (Ростелеком, Дом.ru)",
        arrayOf("-d1", "-s1+s", "-d3+s", "-s6+s", "-d9+s", "-s12+s", "-d15+s", "-s20+s", "-d25+s", "-s30+s", "-d35+s", "-a1")
    ),
    PC_MODE(
        "⚡ Режим ПК (WinWS Fake TLS)",
        "Фейковый пакет ClientHello с низким TTL=5 + сплит (как в ultradpi.exe на компьютере)",
        arrayOf("-f-1", "-t5", "-s1+s", "-d3+s", "-a1")
    ),
    CLASSIC_DISORDER(
        "🛡️ Чистый реверс (Classic Disorder)",
        "Проверенный реверсивный сплит на 1-м байте и SNI. Минимальная задержка",
        arrayOf("-d1", "-s1+s", "-a1")
    );

    companion object {
        fun fromIndex(index: Int): BypassPreset {
            val values = entries
            return if (index in values.indices) values[index] else UNIVERSAL
        }
    }
}
