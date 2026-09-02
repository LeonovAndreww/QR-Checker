package com.datools.qrchecker.model

data class SessionData(
    val id: String,
    val name: String,
    val codes: List<String>,
    val scannedCodes: List<String>,
    /**
     * Когда каждый код отметили, в миллисекундах.
     *
     * Обнуляемое и с умолчанием, потому что сюда же Gson разбирает сессии, сохранённые
     * версиями до появления времени: у них этого поля в JSON нет, и объект собирается
     * без вызова конструктора, так что умолчание Kotlin туда не подставится.
     */
    val scanTimes: Map<String, Long>? = null
)
