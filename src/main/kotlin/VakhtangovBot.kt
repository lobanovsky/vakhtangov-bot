import WebScraper.scrapeSchedule
import WebScraper.updatePerformancesDatabase
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.entities.BotCommand
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.logging.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

fun main() {
    val token = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("Переменная окружения TELEGRAM_BOT_TOKEN не задана!")
    val devMode = System.getenv("DEV_MODE")?.toIntOrNull() ?: 0
    val dbName = System.getenv("DB_NAME") ?: "./data/vakhtangov-bot.db"

    initDatabase(dbName)
    updatePerformancesDatabase()

    val bot = bot {
        this.token = token
        logLevel = LogLevel.Error
        logger().info("Nation is running...")

        dispatch {
            perfCommands()
            callbackCommands()
            statusCommands()
            adminCommands()
            statsCommands()
        }
    }

    if (devMode == 1) {
        logger().info("Запущен в режиме разработки.")
    } else {
        logger().info("Запущен в боевом режиме.")
        startScheduleNotifier(bot)
    }

    bot.setMyCommands(
        listOf(
            BotCommand("perfs", "Список спектаклей"),
            BotCommand("status", "Мои подписки"),
            BotCommand("mysubs", "Мои подписки со ссылками"),
        )
    )

    bot.startPolling()
}

fun startScheduleNotifier(bot: Bot) {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val semaphore = Semaphore(2)
        while (isActive) {
            val performances = getPerformancesWithSubscribers()
                .map { Performance(it.first, it.second, it.third) }

            val tasks = performances.map { p ->
                async {
                    semaphore.withPermit {
                        try {
                            bot.checkTickets(p)
                        } catch (e: Exception) {
                            println("Ошибка при проверке ${p.title}: ${e.message}")
                        }
                    }
                }
            }
            tasks.awaitAll()
            delay(Random.nextLong(5 * 1000L, 30 * 1000L))
        }
    }
}

fun Bot.checkTickets(performance: Performance) {
    logger().info("Проверяем доступные билеты на [${performance.title}]...")
    val schedule = scrapeSchedule(performance)

    val availableSchedules = schedule.filter { it.ticketsAvailable }
    val message =
        if (availableSchedules.isNotEmpty()) "[${performance.title}] Найдены доступные билеты на спектакль." else "[${performance.title}] Нет доступных билетов на спектакль."
    logger().info(message)

    if (availableSchedules.isNotEmpty()) {
        val message = availableSchedules.joinToString("") {
            "\n • Дата: ${it.date}, Время: ${it.time}"
        }
        getSubscribersForPerformance(performance.id).forEach { userId ->
            val result = sendMessage(
                chatId = ChatId.fromId(userId),
                text = "🔔<b>Доступны билеты на [${performance.title}]:</b> $message <a href=\"${performance.url}\">Купить</a>",
                parseMode = HTML
            )
            if (result.isSuccess) {
                incrementNotificationCount(userId)
            }
        }
    }
}
