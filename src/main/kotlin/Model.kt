import kotlinx.serialization.Serializable

@Serializable
data class PerformanceDto(
    val id: String,
    val title: String,
    val url: String,
    val scene: String? = null,
    val isSubscribed: Boolean = false
)

@Serializable
data class PerformanceRef(
    val id: String,
    val theatreId: String? = null,
    val title: String,
    val url: String,
    val scene: String? = null
)

@Serializable
data class TheatreRef(
    val id: String,
    val slug: String,
    val name: String,
    val websiteUrl: String
)

@Serializable
data class SubscriptionDto(
    val id: String,
    val performance: PerformanceRef,
    val theatre: TheatreRef,
    val subscribedAt: String,
    val notificationCount: Int
)

@Serializable
data class UserTheatreSubscriptionsDto(
    val theatre: TheatreRef,
    val subscriptions: List<SubscriptionDto>
)

@Serializable
data class PendingNotificationDto(
    val id: String,
    val telegramId: Long,
    val performanceTitle: String,
    val performanceUrl: String,
    val theatreSlug: String,
    val scheduleSummary: String,
    val createdAt: String
)

@Serializable
data class PaidSubscriptionStatusDto(
    val hasActiveSubscription: Boolean,
    val subscription: PaidSubscriptionDto? = null
)

@Serializable
data class PaidSubscriptionDto(
    val id: String,
    val startDate: String,
    val endDate: String,
    val amountPaid: Int,
    val comment: String? = null,
    val isActive: Boolean,
    val createdBy: String,
    val createdAt: String
)

@Serializable
data class SyncUserRequest(
    val telegramId: Long,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null
)

@Serializable
data class SubscriptionRequest(
    val telegramId: Long,
    val performanceId: String
)
