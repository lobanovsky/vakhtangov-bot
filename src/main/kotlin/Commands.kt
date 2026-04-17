import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.types.TelegramBotResult
import kotlinx.coroutines.runBlocking

private const val TELEGRAM_CALLBACK_DATA_LIMIT = 64
private const val PERFS_PAGE_SIZE = 138

private data class PerfKeyboardDiagnostics(
    val buttonCount: Int,
    val maxCallbackLength: Int,
    val invalidIds: List<String>,
    val page: Int,
    val totalPages: Int
)

private data class PerfKeyboardBuildResult(
    val buttons: List<List<InlineKeyboardButton>>,
    val diagnostics: PerfKeyboardDiagnostics
)

fun Dispatcher.perfCommands() {
    command("perfs") {
        val userId = message.from?.id ?: return@command
        val chatId = ChatId.fromId(message.chat.id)
        logger().info("Получена команда /perfs: telegramId={}, chatId={}", userId, message.chat.id)

        val performances = try {
            runBlocking { ApiClient.getPerformances(userId) }
        } catch (e: Exception) {
            logger().error("Ошибка в /perfs при вызове API: ${e.message}", e)
            bot.sendMessage(chatId, "⚠️ Ошибка при загрузке спектаклей: ${e.message}")
            return@command
        }

        logger().info("Команда /perfs: API вернул {} спектаклей для telegramId={}", performances.size, userId)

        if (performances.isEmpty()) {
            bot.sendMessage(chatId, "ℹ На данный момент нет доступных спектаклей.")
            return@command
        }

        val keyboard = buildPerformanceKeyboard(performances, page = 0)
        logKeyboardDiagnostics("/perfs", userId, keyboard.diagnostics)

        val sendResult = bot.sendMessage(
            chatId = chatId,
            text = buildPerfsMessageText(keyboard.diagnostics),
            replyMarkup = InlineKeyboardMarkup.create(keyboard.buttons)
        )

        logTelegramResult(
            operation = "/perfs sendMessage",
            telegramId = userId,
            payloadSummary = buildPayloadSummary(keyboard.diagnostics),
            result = sendResult
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
        val payload = data.removePrefix("+perf::").removePrefix("-perf::")
        val payloadParts = payload.split("::", limit = 2)
        val currentPage = if (payloadParts.size == 2) payloadParts[0].toIntOrNull() ?: 0 else 0
        val perfId = if (payloadParts.size == 2) payloadParts[1] else payload
        logger().info(
            "Получен callback perf:: telegramId={}, chatId={}, messageId={}, subscribe={}, page={}, perfId={}",
            userId, chatId, messageId, subscribe, currentPage, perfId
        )

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
            logger().info(
                "Callback perf:: telegramId={} получил {} спектаклей после обновления подписки",
                userId, performances.size
            )
            val keyboard = buildPerformanceKeyboard(performances, page = currentPage)
            logKeyboardDiagnostics("callback perf::", userId, keyboard.diagnostics)

            val editResult = bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = InlineKeyboardMarkup.create(keyboard.buttons)
            )

            logEditReplyMarkupResult(
                operation = "callback perf:: editMessageReplyMarkup",
                telegramId = userId,
                payloadSummary = "${buildPayloadSummary(keyboard.diagnostics)}, messageId=$messageId",
                result = editResult
            )
        }
    }

    callbackQuery("page::") {
        val userId = callbackQuery.from.id
        val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
        val messageId = callbackQuery.message?.messageId ?: return@callbackQuery
        val requestedPage = callbackQuery.data.removePrefix("page::").toIntOrNull() ?: 0

        logger().info(
            "Получен callback page:: telegramId={}, chatId={}, messageId={}, requestedPage={}",
            userId, chatId, messageId, requestedPage
        )

        runBlocking {
            val performances = ApiClient.getPerformances(userId)
            logger().info(
                "Callback page:: telegramId={} получил {} спектаклей для страницы {}",
                userId, performances.size, requestedPage
            )
            val keyboard = buildPerformanceKeyboard(performances, page = requestedPage)
            logKeyboardDiagnostics("callback page::", userId, keyboard.diagnostics)

            val editTextResult = bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId.toLong(),
                text = buildPerfsMessageText(keyboard.diagnostics),
                replyMarkup = InlineKeyboardMarkup.create(keyboard.buttons)
            )

            logEditMessageResult(
                operation = "callback page:: editMessageText",
                telegramId = userId,
                payloadSummary = "${buildPayloadSummary(keyboard.diagnostics)}, messageId=$messageId",
                result = editTextResult
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

private fun buildPerformanceKeyboard(
    performances: List<PerformanceDto>,
    page: Int
): PerfKeyboardBuildResult {
    var maxCallbackLength = 0
    val invalidIds = mutableListOf<String>()
    val totalPages = ((performances.size - 1) / PERFS_PAGE_SIZE) + 1
    val safePage = page.coerceIn(0, maxOf(totalPages - 1, 0))
    val pageItems = performances
        .drop(safePage * PERFS_PAGE_SIZE)
        .take(PERFS_PAGE_SIZE)

    val buttons = pageItems.map { perf ->
        val label = if (perf.isSubscribed) "✅ ${perf.title}" else perf.title
        val callbackData = if (perf.isSubscribed) "-perf::$safePage::${perf.id}" else "+perf::$safePage::${perf.id}"
        maxCallbackLength = maxOf(maxCallbackLength, callbackData.length)
        if (callbackData.length > TELEGRAM_CALLBACK_DATA_LIMIT) {
            invalidIds += perf.id
        }
        listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
    }

    val navigationButtons = buildList {
        if (safePage > 0) {
            add(InlineKeyboardButton.CallbackData("⬅️ Назад", "page::${safePage - 1}"))
        }
        if (safePage < totalPages - 1) {
            add(InlineKeyboardButton.CallbackData("Вперёд ➡️", "page::${safePage + 1}"))
        }
    }

    val allButtons = if (navigationButtons.isEmpty()) buttons else buttons + listOf(navigationButtons)

    return PerfKeyboardBuildResult(
        buttons = allButtons,
        diagnostics = PerfKeyboardDiagnostics(
            buttonCount = buttons.size,
            maxCallbackLength = maxCallbackLength,
            invalidIds = invalidIds,
            page = safePage,
            totalPages = totalPages
        )
    )
}

private fun logKeyboardDiagnostics(context: String, telegramId: Long, diagnostics: PerfKeyboardDiagnostics) {
    if (diagnostics.invalidIds.isEmpty()) {
        logger().info(
            "{}: telegramId={}, buttons={}, page={}/{}, maxCallbackLength={}",
            context,
            telegramId,
            diagnostics.buttonCount,
            diagnostics.page + 1,
            diagnostics.totalPages,
            diagnostics.maxCallbackLength
        )
        return
    }

    val invalidPreview = diagnostics.invalidIds.joinToString(", ") { it.take(24) }
    logger().warn(
        "{}: telegramId={}, buttons={}, page={}/{}, maxCallbackLength={}, callback_data > {} для id=[{}]",
        context,
        telegramId,
        diagnostics.buttonCount,
        diagnostics.page + 1,
        diagnostics.totalPages,
        diagnostics.maxCallbackLength,
        TELEGRAM_CALLBACK_DATA_LIMIT,
        invalidPreview
    )
}

private fun logTelegramResult(
    operation: String,
    telegramId: Long,
    payloadSummary: String,
    result: TelegramBotResult<*>
) {
    result.onSuccess { response ->
        val messageId = (response as? com.github.kotlintelegrambot.entities.Message)?.messageId
        logger().info(
            "{} success: telegramId={}, payload={}, messageId={}",
            operation, telegramId, payloadSummary, messageId
        )
    }.onError { error ->
        when (error) {
            is TelegramBotResult.Error.TelegramApi -> logger().error(
                "{} failed: telegramId={}, payload={}, telegramErrorCode={}, description={}",
                operation, telegramId, payloadSummary, error.errorCode, error.description
            )

            is TelegramBotResult.Error.HttpError -> logger().error(
                "{} failed: telegramId={}, payload={}, httpCode={}, description={}",
                operation, telegramId, payloadSummary, error.httpCode, error.description
            )

            is TelegramBotResult.Error.InvalidResponse -> logger().error(
                "{} failed: telegramId={}, payload={}, httpCode={}, status={}, body={}",
                operation, telegramId, payloadSummary, error.httpCode, error.httpStatusMessage, error.body
            )

            is TelegramBotResult.Error.Unknown -> logger().error(
                "{} failed: telegramId={}, payload={}, exception={}",
                operation, telegramId, payloadSummary, error.exception.message, error.exception
            )
        }
    }
}

private fun logEditReplyMarkupResult(
    operation: String,
    telegramId: Long,
    payloadSummary: String,
    result: Pair<*, Exception?>
) {
    val exception = result.second
    if (exception != null) {
        logger().error(
            "{} failed: telegramId={}, payload={}, exception={}",
            operation, telegramId, payloadSummary, exception.message, exception
        )
        return
    }

    logger().info(
        "{} success: telegramId={}, payload={}",
        operation, telegramId, payloadSummary
    )
}

private fun logEditMessageResult(
    operation: String,
    telegramId: Long,
    payloadSummary: String,
    result: Pair<*, Exception?>
) = logEditReplyMarkupResult(operation, telegramId, payloadSummary, result)

private fun buildPerfsMessageText(diagnostics: PerfKeyboardDiagnostics): String =
    "📜 Выберите спектакли для подписки на уведомления:\nСтраница ${diagnostics.page + 1} из ${diagnostics.totalPages}"

private fun buildPayloadSummary(diagnostics: PerfKeyboardDiagnostics): String =
    "buttons=${diagnostics.buttonCount}, page=${diagnostics.page + 1}/${diagnostics.totalPages}, maxCallbackLength=${diagnostics.maxCallbackLength}"
