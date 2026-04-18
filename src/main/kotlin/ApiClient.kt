import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiClient {
    private val backendUrl = System.getenv("BACKEND_URL") ?: "http://localhost:8080"
    private val apiKey = System.getenv("VAKHTANGOV_API_KEY") ?: "vakhtangov-secret"
    private const val theatreSlug = "vakhtangov"

    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getPerformances(telegramId: Long? = null): List<PerformanceDto> {
        return http.get("$backendUrl/api/theatres/$theatreSlug/performances") {
            bearerAuth(apiKey)
            if (telegramId != null) parameter("telegramId", telegramId)
        }.body()
    }

    suspend fun syncUser(telegramId: Long, firstName: String, lastName: String?, username: String?) {
        http.post("$backendUrl/api/users/sync") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(SyncUserRequest(telegramId, firstName, lastName, username))
        }
    }

    suspend fun subscribe(telegramId: Long, performanceId: String) {
        http.post("$backendUrl/api/subscriptions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(SubscriptionRequest(telegramId, performanceId))
        }
    }

    suspend fun unsubscribe(telegramId: Long, performanceId: String) {
        http.delete("$backendUrl/api/subscriptions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(SubscriptionRequest(telegramId, performanceId))
        }
    }

    suspend fun getUserSubscriptions(telegramId: Long): List<SubscriptionDto> {
        val grouped: List<UserTheatreSubscriptionsDto> = http.get("$backendUrl/api/users/$telegramId/subscriptions") {
            bearerAuth(apiKey)
        }.body()
        return grouped.flatMap { it.subscriptions }
    }

    suspend fun getAdminSubscriptions(): List<SubscriptionDetailDto> {
        return http.get("$backendUrl/api/admin/theatres/$theatreSlug/subscriptions") {
            bearerAuth(apiKey)
        }.body()
    }

    suspend fun ackNotification(notificationId: String) {
        http.post("$backendUrl/api/notifications/$notificationId/ack") {
            bearerAuth(apiKey)
        }
    }
}
