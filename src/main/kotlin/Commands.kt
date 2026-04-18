import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import kotlinx.coroutines.runBlocking

private const val PAGE_SIZE = 70

private fun buildPerformancePage(performances: List<PerformanceDto>, page: Int): InlineKeyboardMarkup {
    val totalPages = (performances.size + PAGE_SIZE - 1) / PAGE_SIZE
    val start = (page - 1) * PAGE_SIZE
    val pagePerfs = performances.subList(start, minOf(start + PAGE_SIZE, performances.size))

    val buttons: MutableList<List<InlineKeyboardButton>> = pagePerfs.map { perf ->
        val label = if (perf.isSubscribed) "✅ ${perf.title}" else perf.title
        val callbackData = if (perf.isSubscribed) "-perf::${perf.id}::$page" else "+perf::${perf.id}::$page"
        listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
    }.toMutableList()

    val navRow = mutableListOf<InlineKeyboardButton>()
    if (page > 1) navRow += InlineKeyboardButton.CallbackData("← Назад", "perfpage::${page - 1}")
    if (page < totalPages) navRow += InlineKeyboardButton.CallbackData("Вперёд →", "perfpage::${page + 1}")
    if (navRow.isNotEmpty()) buttons += listOf(navRow)

    return InlineKeyboardMarkup.create(buttons)
}

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

        bot.sendMessage(
            chatId = chatId,
            text = "📜 Выберите спектакли для подписки на уведомления:",
            replyMarkup = buildPerformancePage(performances, 1)
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
        val parts = data.removePrefix("+").removePrefix("-").split("::")
        val perfId = parts[1]
        val page = parts.getOrNull(2)?.toIntOrNull() ?: 1

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
            val (_, editError) = bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = buildPerformancePage(performances, page)
            )
            if (editError != null) logger().error("Failed to edit markup: ${editError.message}")
        }
    }

    callbackQuery("perfpage::") {
        val data = callbackQuery.data
        val userId = callbackQuery.from.id
        val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
        val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

        val page = data.removePrefix("perfpage::").toIntOrNull() ?: return@callbackQuery

        runBlocking {
            val performances = ApiClient.getPerformances(userId)
            val (_, editError) = bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = buildPerformancePage(performances, page)
            )
            if (editError != null) logger().error("Failed to edit markup: ${editError.message}")
        }
    }
}

fun Dispatcher.statusCommands() {
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
