package com.ultradpi.app

enum class BypassPreset(
    val title: String,
    val description: String,
    val args: Array<String>
) {
    MOBILE_4G(
        "📱 Мобильный 4G (МТС, Билайн, МегаФон)",
        "Быстрый реверс-сплит (-s1 -d1 -a1), оптимизированный под ТСПУ сотовых сетей",
        arrayOf("-s1", "-d1", "-a1")
    ),
    UNIVERSAL_OOB(
        "⭐ Ультра OOB (Работает на Wi-Fi)",
        "Out-Of-Band + TLS Record сплит (-o1 -a1 -r-5+se). Золотой стандарт YouTube 4K",
        arrayOf("-o1", "-a1", "-r-5+se")
    ),
    PC_FAKE_TLS(
        "⚡ Режим ПК (WinWS Fake TLS)",
        "Фейковый пакет ClientHello с низким TTL=5 + сплит (-f-1 -t5 -s1+s -d3+s -a1)",
        arrayOf("-f-1", "-t5", "-s1+s", "-d3+s", "-a1")
    ),
    MULTI_SPLIT(
        "🔥 Мульти-сплит (11 сегментов)",
        "Глубокая фрагментация SNI в обратном порядке против жестких ТСПУ",
        arrayOf("-d1", "-s1+s", "-d3+s", "-s6+s", "-d9+s", "-s12+s", "-d15+s", "-s20+s", "-d25+s", "-s30+s", "-d35+s", "-a1")
    ),
    CUSTOM(
        "🛠️ Своя строка аргументов (Ручной ввод)",
        "Введите любые флаги ciadpi вручную (как на 4PDA или ntc.party)",
        emptyArray()
    );

    companion object {
        fun fromIndex(index: Int): BypassPreset {
            val values = entries
            return if (index in values.indices) values[index] else MOBILE_4G
        }
    }
}
