# Имена полей SessionData - часть формата, в котором лежат данные старых версий:
# migrateFromSharedPrefsIfNeeded() читает их через Gson по именам, так что
# переименование полей молча превратило бы сессии пользователя в пустые.
# Само имя класса не важно - Gson получает его как SessionData::class.java.
-keepclassmembers class com.datools.qrchecker.model.SessionData {
    <fields>;
}

# List<String> внутри SessionData и TypeToken в миграции базы разрешаются по
# generic-сигнатуре, которую R8 иначе выбрасывает.
-keepattributes Signature
