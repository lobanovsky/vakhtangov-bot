import com.github.kotlintelegrambot.entities.Message

// Enum класс для типов сообщений
enum class MessageType(val description: String) {
    TEXT("Текстовое сообщение"),
    PHOTO("Фото"),
    VIDEO("Видео"),
    DOCUMENT("Документ"),
    AUDIO("Аудио"),
    VOICE("Голосовое сообщение"),
    STICKER("Стикер"),
    CONTACT("Контакт"),
    LOCATION("Локация"),
    POLL("Опрос"),
    UNKNOWN("Неизвестный тип сообщения");

    companion object {
        // Функция для определения типа сообщения на основе содержимого
        fun fromMessage(message: Message): MessageType {
            return when {
                message.text != null -> TEXT
                message.photo != null -> PHOTO
                message.video != null -> VIDEO
                message.document != null -> DOCUMENT
                message.audio != null -> AUDIO
                message.voice != null -> VOICE
                message.sticker != null -> STICKER
                message.contact != null -> CONTACT
                message.location != null -> LOCATION
                message.poll != null -> POLL
                else -> UNKNOWN
            }
        }
    }
}
