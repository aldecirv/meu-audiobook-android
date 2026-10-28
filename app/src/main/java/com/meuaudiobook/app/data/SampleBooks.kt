package com.meuaudiobook.app.data

/**
 * Catálogo inicial: obras em domínio público gravadas pelo LibriVox.
 * O áudio é transmitido diretamente do Archive.org.
 */
object SampleBooks {

    private const val DOM_URL = "https://archive.org/download/dom_casmurro_2102_librivox/"
    private const val ALIENISTA_URL = "https://archive.org/download/o_alienista_0904_librivox/"

    val books: List<Book> = listOf(
        Book(
            id = "dom-casmurro",
            title = "Dom Casmurro",
            author = "Machado de Assis",
            description = "Bentinho narra sua história e o amor por Capitu — gravação em domínio público (LibriVox).",
            color = 0xFF1F6F6B.toInt(),
            builtIn = true,
            chapters = listOf(
                Chapter("Parte 1", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_01_assis_64kb.mp3", 1191000L),
                Chapter("Parte 2", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_02_assis_64kb.mp3", 985000L),
                Chapter("Parte 3", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_03_assis_64kb.mp3", 1454000L),
                Chapter("Parte 4", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_04_assis_64kb.mp3", 1374000L),
                Chapter("Parte 5", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_05_assis_64kb.mp3", 977000L),
                Chapter("Parte 6", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_06_assis_64kb.mp3", 954000L),
                Chapter("Parte 7", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_07_assis_64kb.mp3", 1901000L),
                Chapter("Parte 8", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_08_assis_64kb.mp3", 957000L),
                Chapter("Parte 9", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_09_assis_64kb.mp3", 1341000L),
                Chapter("Parte 10", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_10_assis_64kb.mp3", 719000L),
                Chapter("Parte 11", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_11_assis_64kb.mp3", 1107000L),
                Chapter("Parte 12", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_12_assis_64kb.mp3", 1073000L),
                Chapter("Parte 13", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_13_assis_64kb.mp3", 1518000L),
                Chapter("Parte 14", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_14_assis_64kb.mp3", 1245000L),
                Chapter("Parte 15", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_15_assis_64kb.mp3", 791000L),
                Chapter("Parte 16", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_16_assis_64kb.mp3", 1083000L),
                Chapter("Parte 17", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_17_assis_64kb.mp3", 844000L),
                Chapter("Parte 18", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_18_assis_64kb.mp3", 919000L),
                Chapter("Parte 19", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_19_assis_64kb.mp3", 1224000L),
                Chapter("Parte 20", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_20_assis_64kb.mp3", 941000L),
                Chapter("Parte 21", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_21_assis_64kb.mp3", 881000L),
                Chapter("Parte 22", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_22_assis_64kb.mp3", 1138000L),
                Chapter("Parte 23", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_23_assis_64kb.mp3", 827000L),
                Chapter("Parte 24", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_24_assis_64kb.mp3", 1039000L),
                Chapter("Parte 25", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_25_assis_64kb.mp3", 511000L),
                Chapter("Parte 26", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_26_assis_64kb.mp3", 816000L),
                Chapter("Parte 27", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_27_assis_64kb.mp3", 771000L),
                Chapter("Parte 28", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_28_assis_64kb.mp3", 734000L),
                Chapter("Parte 29", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_29_assis_64kb.mp3", 910000L),
                Chapter("Parte 30", "https://archive.org/download/dom_casmurro_2102_librivox/domcasmurro_30_assis_64kb.mp3", 296000L),
            )
        ),
        Book(
            id = "o-alienista",
            title = "O Alienista",
            author = "Machado de Assis",
            description = "O dr. Simão Bacamarte e a Casa Verde — gravação em domínio público (LibriVox).",
            color = 0xFF6B4E9B.toInt(),
            builtIn = true,
            chapters = listOf(
                Chapter("Parte 1", "https://archive.org/download/o_alienista_0904_librivox/oalienista_1_machadodeassis_64kb.mp3", 1027000L),
                Chapter("Parte 2", "https://archive.org/download/o_alienista_0904_librivox/oalienista_2_machadodeassis_64kb.mp3", 799000L),
                Chapter("Parte 3", "https://archive.org/download/o_alienista_0904_librivox/oalienista_3_machadodeassis_64kb.mp3", 1415000L),
                Chapter("Parte 4", "https://archive.org/download/o_alienista_0904_librivox/oalienista_4_machadodeassis_64kb.mp3", 690000L),
                Chapter("Parte 5", "https://archive.org/download/o_alienista_0904_librivox/oalienista_5_machadodeassis_64kb.mp3", 697000L),
                Chapter("Parte 6", "https://archive.org/download/o_alienista_0904_librivox/oalienista_6_machadodeassis_64kb.mp3", 861000L),
                Chapter("Parte 7", "https://archive.org/download/o_alienista_0904_librivox/oalienista_7_machadodeassis_64kb.mp3", 180000L),
                Chapter("Parte 8", "https://archive.org/download/o_alienista_0904_librivox/oalienista_8_machadodeassis_64kb.mp3", 729000L),
                Chapter("Parte 9", "https://archive.org/download/o_alienista_0904_librivox/oalienista_9_machadodeassis_64kb.mp3", 724000L),
            )
        )
    )
}
