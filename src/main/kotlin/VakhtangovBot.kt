import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.entities.BotCommand
import com.github.kotlintelegrambot.logging.LogLevel

fun main() {
    val token = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("Переменная окружения TELEGRAM_BOT_TOKEN не задана!")
    val devMode = System.getenv("DEV_MODE")?.toIntOrNull() ?: 0

    val bot = bot {
        this.token = token
        logLevel = LogLevel.Error

        dispatch {
            startCommands()
            menuCommands()
            perfCommands()
            callbackCommands()
            statusCommands()
        }
    }

    if (devMode == 1) {
        logger().info("Запущен в режиме разработки (webhook-сервер не запускается).")
    } else {
        logger().info("Запущен в боевом режиме.")
        startWebhookServer(bot)
    }

    bot.setMyCommands(
        listOf(
            BotCommand("perfs", "Список спектаклей"),
            BotCommand("mysubs", "Мои подписки со ссылками"),
        )
    )

    bot.startPolling()
}
