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
        "https://vakhtangov.ru/show/beg/"
    )
    val performances = getAllPerformances()
        .map { Performance(title = it.title, url = it.url) }
        .filter { it.url in urls }

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