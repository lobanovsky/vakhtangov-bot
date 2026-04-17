import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Dispatcher.perfCommands() {
    command("perfs") {
        val userId = message.from?.id ?: return@command
        val chatId = ChatId.fromId(message.chat.id)

        val performances = try {
            runBlocking { ApiClient.getPerformances(userId) }
        } catch (e: Exception) {
            logger().error("Ошибка в /perfs при вызове API: ${e.message}", e)
            bot.sendMessage(chatId, "⚠️ Ошибка при загрузке спектаклей: ${e.message}")
            return@command
        }

        if (performances.isEmpty()) {
            bot.sendMessage(chatId, "ℹ На данный момент нет доступных спектаклей.")
            return@command
        }

        val buttons = performances.map { perf ->
            val label = if (perf.isSubscribed) "✅ ${perf.title}" else perf.title
            val callbackData = if (perf.isSubscribed) "-perf::${perf.id}" else "+perf::${perf.id}"
            listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
        }

        bot.sendMessage(
            chatId = chatId,
            text = "📜 Выберите спектакли для подписки на уведомления:",
            replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
    }
}

fun Dispatcher.callbackCommands() {
    callbackQuery("perf::") {
        val data = callbackQuery.data
        val userId = callbackQuery.from.id
        val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
        val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

        val subscribe = data.startsWith("+perf::")
        val perfId = data.removePrefix("+perf::").removePrefix("-perf::")

        runBlocking {
            if (subscribe) {
                ApiClient.syncUser(
                    telegramId = userId,
                    firstName = callbackQuery.from.firstName,
                    lastName = callbackQuery.from.lastName,
                    username = callbackQuery.from.username
                )
                ApiClient.subscribe(userId, perfId)
            } else {
                ApiClient.unsubscribe(userId, perfId)
            }

            val performances = ApiClient.getPerformances(userId)
            val buttons = performances.map { perf ->
                val label = if (perf.isSubscribed) "✅ ${perf.title}" else perf.title
                val callbackData = if (perf.isSubscribed) "-perf::${perf.id}" else "+perf::${perf.id}"
                listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
            }

            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
        }
    }
}

fun Dispatcher.statusCommands() {
    command("status") {
        val userId = message.from?.id ?: return@command
        val subscriptions = runBlocking { ApiClient.getUserSubscriptions(userId) }

        if (subscriptions.isEmpty()) {
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли."
            )
        } else {
            val list = subscriptions.joinToString("\n") { "🎭 ${it.performance.title}" }
            bot.sendMessage(ChatId.fromId(message.chat.id), "✅ Вы подписаны на уведомления о билетах:\n$list")
        }
    }

    command("mysubs") {
        val userId = message.from?.id ?: return@command
        val subscriptions = runBlocking { ApiClient.getUserSubscriptions(userId) }

        if (subscriptions.isEmpty()) {
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли."
            )
        } else {
            val list = subscriptions.joinToString("\n") {
                "🎭 <a href=\"${it.performance.url}\">${it.performance.title}</a>"
            }
            bot.sendMessage(ChatId.fromId(message.chat.id), "✅ Ваши подписки:\n$list", parseMode = HTML)
        }
    }
}

//fun Dispatcher.adminCommands() {
//    command("subs") {
//        val details = runBlocking { ApiClient.getAdminSubscriptions() }
//
//        if (details.isEmpty()) {
//            bot.sendMessage(ChatId.fromId(message.chat.id), "ℹ Пока нет подписок на спектакли.")
//            return@command
//        }
//
//        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
//
//        val text = buildString {
//            append("📊 <b>Подписки на спектакли:</b>\n")
//            details.forEach { detail ->
//                append("\n🎭 <b>${detail.performance.title}</b> (${detail.subscribers.size}):\n")
//                detail.subscribers.forEach { sub ->
//                    val userRef = if (sub.username != null) "@${sub.username}" else sub.firstName
//                    val date = runCatching {
//                        LocalDateTime.parse(sub.subscribedAt).format(dateFormatter)
//                    }.getOrElse { sub.subscribedAt }
//                    append("  • $userRef — с $date [${sub.notificationCount} увед.]\n")
//                }
//            }
//        }
//
//        bot.sendMessage(ChatId.fromId(message.chat.id), text, parseMode = HTML)
//    }
//}
