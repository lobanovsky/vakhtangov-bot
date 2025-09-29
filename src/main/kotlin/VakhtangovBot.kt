import WebScraper.scrapeSchedule
import WebScraper.updatePerformancesDatabase
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.entities.BotCommand
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

fun main() {
    val token = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("Переменная окружения TELEGRAM_BOT_TOKEN не задана!")
    val dbName = System.getenv("DB_NAME") ?: "./data/nations-bot.db"

    initDatabase(dbName)
    updatePerformancesDatabase()

    val subscribeCommandName = "subscribe"
    val unsubscribeCommandName = "unsubscribe"
    val statusCommandName = "status"
    val subscribesCommandName = "subs"
    val performancesCommandName = "perfs"

    val bot = bot {
        this.token = token
        logger().info("Vakhatangov-bot is running...")

        dispatch {
            subscriptionCommands(subscribeCommandName, unsubscribeCommandName, statusCommandName, subscribesCommandName)
            perfCommands(performancesCommandName)
        }
    }

    val urls = setOf(
//        "https://vakhtangov.ru/show/beg/"
    )
    val performances = getAllPerformances()
        .map {
            Performance(
                id = it.id,
                title = it.title,
                url = it.url,
                scene = it.scene
            )
        }.filter { it.url in urls }

    startScheduleNotifier(bot, performances)

    // Устанавливаем список команд, чтобы они отображались в Telegram
    bot.setMyCommands(
        listOf(
            BotCommand(subscribeCommandName, "Подписаться на уведомления"),
            BotCommand(unsubscribeCommandName, "Отписаться от уведомлений"),
            BotCommand(statusCommandName, "Проверить статус подписки"),
            BotCommand(performancesCommandName, "Список спектаклей"),
            BotCommand(subscribesCommandName, "Получить список подписчиков"),
        )
    )

    bot.startPolling()
}

fun startScheduleNotifier(bot: Bot, performance: List<Performance>) {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val semaphore = Semaphore(2) // максимум 2 одновременных задачи
        while (isActive) {
            val tasks = performance.map { p ->
                async {
                    semaphore.withPermit {
                        try {
                            bot.checkTickets(p, "7 октября, вторник, 19:00")
                        } catch (e: Exception) {
                            println("Ошибка при проверке ${p.title}: ${e.message}")
                        }
                    }
                }
            }
            tasks.awaitAll() // ждем завершения всех проверок
            delay(Random.nextLong(5 * 1000L, 30 * 1000L)) // от 5 до 30 секунд ожидания
        }
    }
}

fun Bot.checkTickets(performance: Performance, desiredDate: String) {
    logger().info("Проверяем доступные билеты на [${performance.title}], дату: $desiredDate ...")

    val schedule = scrapeSchedule(performance)

    // Фильтруем только на нужную дату
    val matchingSchedules = schedule.filter {
        "${it.date}, ${it.dayOfWeek}, ${it.time}" == desiredDate
    }

    if (matchingSchedules.isEmpty()) {
        logger().info("[${performance.title}] На дату $desiredDate спектакль не найден.")
        return
    }

    // Фильтруем по доступности билетов
    val availableSchedules = matchingSchedules.filter { it.ticketsAvailable }

    if (availableSchedules.isEmpty()) {
        val msg = "[${performance.title}] На дату $desiredDate билеты недоступны."
        logger().info(msg)
        return
    }

    // Если есть доступные билеты → формируем сообщение
    val message = availableSchedules.joinToString("") {
        "\n • Дата: ${it.date}, День недели: ${it.dayOfWeek}, Время: ${it.time}"
    }

    val text = "🔔<b>Доступны билеты на [${performance.title}]:</b> $message <a href=\"${performance.url}\">Купить</a>"

    logger().info("[${performance.title}] Найдены доступные билеты на $desiredDate")

    // Отправляем сообщение каждому подписчику
    getAllSubscribers().forEach { (userId, _) ->
        sendMessage(
            chatId = ChatId.fromId(userId),
            text = text,
            parseMode = HTML
        )
    }
}