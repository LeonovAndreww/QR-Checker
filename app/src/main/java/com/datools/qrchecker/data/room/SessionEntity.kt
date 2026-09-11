package com.datools.qrchecker.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Когда сессия заведена. Ноль у заведённых до появления этого столбца. */
    val createdAt: Long = 0,
    /**
     * Когда её последний раз открывали. По этому полю список и упорядочен: то, чем
     * занимаются сейчас, должно быть сверху, а не то, что завели раньше всех.
     */
    val openedAt: Long = 0,
    /**
     * Сессия собирает коды, а не сверяет их со списком.
     *
     * У обычной сессии список приходит из документа, и код вне списка - это ошибка
     * поставки. У собирающей списка изначально нет: она заводится пустой, и каждый новый
     * код в неё дописывается. Единственное, что она ловит, - повторы.
     */
    val collecting: Boolean = false
)
