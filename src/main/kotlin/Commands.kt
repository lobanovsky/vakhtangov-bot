import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
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
            bot.sendMessage(chatId, "⚠️ Ошибка при загрузке спектаклей: ${e.message}", replyMarkup = menuKeyboard())
            return@command
        }

        if (performances.isEmpty()) {
            bot.sendMessage(chatId, "ℹ На данный момент нет доступных спектаклей.", replyMarkup = menuKeyboard())
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
                "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли.",
                replyMarkup = menuKeyboard()
            )
        } else {
            val list = subscriptions.joinToString("\n") {
                "🎭 <a href=\"${it.performance.url}\">${it.performance.title}</a>"
            }
            bot.sendMessage(ChatId.fromId(message.chat.id), "✅ Ваши подписки:\n$list", parseMode = HTML, replyMarkup = menuKeyboard())
        }
    }
}

private const val INFO_TEXT = """Сервис «Билеты в продаже»

Боты уведомляют, если билеты есть в продаже на выбранные спектакли.

🎭 Боты:
1. Театр Наций — @nations_ticket_bot
2. РАМТ — @ramt_ticket_bot
3. Мастерская Петра Фоменко — @fomenkiru_bot
4. Театр им. Вахтангова — @vakhtangov_ticket_bot
5. Театр Ленсовета — @lensov_ticket_bot

Поддержка: e.lobanovsky@ya.ru / @e_lobanovsky
Создано в «Бюро Лобановского» — https://lobanovsky.ru"""

private const val PAYMENT_TEXT = """Стоимость: 1000₽ за 6 месяцев за все боты

Перевод по номеру телефона: +7-926-793-63-63
Банк: Т-Банк или Сбер
В комментарии укажите ваш Telegram-username.

Поддержка: e.lobanovsky@ya.ru / @e_lobanovsky"""

private fun menuKeyboard() = KeyboardReplyMarkup(
    keyboard = listOf(
        listOf(KeyboardButton("📋 Моя подписка")),
        listOf(KeyboardButton("ℹ️ Информация"), KeyboardButton("💳 Оплата"))
    ),
    resizeKeyboard = true
)

fun Dispatcher.startCommands() {
    command("start") {
        val user = message.from ?: return@command
        runBlocking {
            ApiClient.syncUser(
                telegramId = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username
            )
        }
        bot.sendMessage(
            chatId = ChatId.fromId(message.chat.id),
            text = "Выберите действие:",
            replyMarkup = menuKeyboard()
        )
    }
}

fun Dispatcher.menuCommands() {
    text("📋 Моя подписка") {
        val userId = message.from?.id ?: return@text
        val chatId = ChatId.fromId(message.chat.id)
        val status = try {
            runBlocking { ApiClient.getPaidSubscription(userId) }
        } catch (e: Exception) {
            logger().error("Ошибка при получении подписки: ${e.message}", e)
            bot.sendMessage(chatId, "⚠️ Ошибка при получении данных о подписке", replyMarkup = menuKeyboard())
            return@text
        }
        if (status.hasActiveSubscription) {
            val sub = status.subscription!!
            val text = buildString {
                appendLine("✅ У вас активная подписка")
                appendLine("📅 Начало: ${sub.startDate}")
                appendLine("📅 Окончание: ${sub.endDate}")
                appendLine("💰 Стоимость: ${sub.amountPaid}₽")
                if (!sub.comment.isNullOrBlank()) appendLine("💬 ${sub.comment}")
            }
            bot.sendMessage(chatId, text.trim(), replyMarkup = menuKeyboard())
        } else {
            bot.sendMessage(chatId, "❌ У вас нет активной подписки\n\nДля подключения — кнопка 💳 Оплата", replyMarkup = menuKeyboard())
        }
    }

    text("ℹ️ Информация") {
        bot.sendMessage(ChatId.fromId(message.chat.id), INFO_TEXT, replyMarkup = menuKeyboard())
    }

    text("💳 Оплата") {
        bot.sendMessage(ChatId.fromId(message.chat.id), PAYMENT_TEXT, replyMarkup = menuKeyboard())
    }
}
