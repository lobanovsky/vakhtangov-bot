import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode.HTML

fun Dispatcher.perfCommands(
    performancesCommandName: String,
) {
    command(performancesCommandName) {
        val performances = getAllPerformances()
        if (performances.isEmpty()) {
            bot.sendMessage(ChatId.fromId(message.chat.id), "ℹ На данный момент нет доступных спектаклей.")
        } else {
            val listText = performances.joinToString("\n") { "🎭 ${it.second} - <a href=\"${it.third}\">Подробнее</a>" }
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "📜 Список доступных спектаклей:\n$listText",
                parseMode = HTML
            )
        }
    }
}


fun Dispatcher.subscriptionCommands(
    subscribeCommandName: String,
    unsubscribeCommandName: String,
    statusCommandName: String,
    subscribesCommandName: String
) {
    // Подписаться
    command(subscribeCommandName) {
        val userId = message.from?.id ?: return@command
        val firstName = message.from?.firstName ?: "Без имени"
        val lastName = message.from?.lastName
        val username = message.from?.username

        if (isSubscribed(userId)) {
            bot.sendMessage(ChatId.fromId(userId), "ℹ Вы уже подписаны.")
            logger().info("Пользователь $firstName $lastName (ID: $userId) уже подписан.")
        } else {
            addSubscriber(userId, firstName, lastName, username)
            bot.sendMessage(ChatId.fromId(userId), "✅ Вы подписались на уведомления!")
            logger().info("Пользователь $firstName $lastName (ID: $userId) подписался на уведомления.")
        }
    }
    // Отписаться
    command(unsubscribeCommandName) {
        val userId = message.from?.id ?: return@command
        val firstName = message.from?.firstName ?: "Без имени"
        val lastName = message.from?.lastName
        if (isSubscribed(userId)) {
            removeSubscriber(userId)
            bot.sendMessage(ChatId.fromId(userId), "❌ Вы отписались от уведомлений.")
            logger().info("Пользователь $firstName $lastName (ID: $userId) отписался от уведомлений.")
        } else {
            bot.sendMessage(ChatId.fromId(userId), "ℹ Вы не были подписаны.")
        }
    }
    // Проверка статуса подписки
    command(statusCommandName) {
        val userId = message.from?.id ?: return@command
        val statusMessage = if (isSubscribed(userId)) {
            "✅ Вы подписаны на уведомления."
        } else {
            "❌ Вы НЕ подписаны на уведомления."
        }
        bot.sendMessage(ChatId.fromId(userId), statusMessage)
    }
    // Список всех подписчиков
    command(subscribesCommandName) {
        val subscribersList = getAllSubscribers()
        if (subscribersList.isEmpty()) {
            bot.sendMessage(ChatId.fromId(message.chat.id), "ℹ Пока нет подписчиков.")
        } else {
            val listText = subscribersList.joinToString("\n") { "${it.second} (ID: ${it.first})" }
            bot.sendMessage(ChatId.fromId(message.chat.id), "📜 Список подписчиков:\n$listText")
        }
    }
}