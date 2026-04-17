import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun startWebhookServer(bot: Bot) {
    val webhookSecret = System.getenv("WEBHOOK_SECRET") ?: "webhook-secret"
    val webhookPort = System.getenv("WEBHOOK_PORT")?.toIntOrNull() ?: 8081

    embeddedServer(Netty, port = webhookPort) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            post("/webhook/notifications") {
                val secret = call.request.header("X-Webhook-Secret")
                if (secret != webhookSecret) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val notification = try {
                    call.receive<PendingNotificationDto>()
                } catch (e: Exception) {
                    logger().error("Ошибка парсинга webhook: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        sendTicketNotification(bot, notification)
                        ApiClient.ackNotification(notification.id)
                    } catch (e: Exception) {
                        logger().error("Ошибка обработки уведомления ${notification.id}: ${e.message}")
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }.start(wait = false)

    logger().info("Webhook-сервер запущен на порту $webhookPort")
}

private fun sendTicketNotification(bot: Bot, notification: PendingNotificationDto) {
    val text = "🔔<b>Доступны билеты на [${notification.performanceTitle}]:</b>\n" +
            "${notification.scheduleSummary}\n" +
            "<a href=\"${notification.performanceUrl}\">Купить</a>"

    bot.sendMessage(
        chatId = ChatId.fromId(notification.telegramId),
        text = text,
        parseMode = HTML
    )
}
