import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

// Таблица подписчиков
object Subscribers : Table() {
    val userId = long("user_id")
    val firstName = varchar("first_name", 50)
    val lastName = varchar("last_name", 50).nullable()
    val username = varchar("user_name", 50).nullable()
    val subscriptionDate = datetime("subscription_date")
    val isActive = bool("is_active").default(true) // Флаг активности подписки
    val unsubscriptionDate = datetime("unsubscription_date").nullable() // Дата отписки
    val notificationCount = integer("notification_count").default(0)

    override val primaryKey = PrimaryKey(userId)
}

// Таблица спектаклей
object Performances : Table() {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val url = varchar("url", 255)
    val scene = varchar("scene", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

// Таблица подписок пользователей на спектакли
object UserPerformanceSubscriptions : Table() {
    val userId = long("user_id").references(Subscribers.userId)
    val performanceId = integer("performance_id").references(Performances.id)
    val subscribedAt = datetime("subscribed_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(userId, performanceId)
}

// Инициализация базы данных
fun initDatabase(dbName: String) {
    Database.connect("jdbc:sqlite:$dbName", "org.sqlite.JDBC")
    transaction {
        // Создаём таблицы, если их нет
        SchemaUtils.create(Subscribers)
        SchemaUtils.create(Performances)
        SchemaUtils.create(UserPerformanceSubscriptions)
    }

    // Запускаем все миграции
    DatabaseMigrations.runAllMigrations()
}

// Добавить подписчика (или реактивировать существующего)
fun addSubscriber(userId: Long, firstName: String, lastName: String?, username: String?) {
    transaction {
        // Проверяем, существует ли пользователь
        val existingUser = Subscribers.selectAll()
            .where { Subscribers.userId eq userId }
            .firstOrNull()

        if (existingUser != null) {
            // Если пользователь существует, реактивируем его
            Subscribers.update({ Subscribers.userId eq userId }) {
                it[isActive] = true
                it[unsubscriptionDate] = null
                it[Subscribers.firstName] = firstName
                it[Subscribers.lastName] = lastName
                it[Subscribers.username] = username
                it[subscriptionDate] = LocalDateTime.now()
            }
        } else {
            // Если пользователя нет, создаем нового
            Subscribers.insert {
                it[Subscribers.userId] = userId
                it[Subscribers.firstName] = firstName
                it[Subscribers.lastName] = lastName
                it[Subscribers.username] = username
                it[subscriptionDate] = LocalDateTime.now()
                it[isActive] = true
                it[unsubscriptionDate] = null
            }
        }
    }
}

// Отписать подписчика (soft delete)
fun removeSubscriber(userId: Long) {
    transaction {
        Subscribers.update({ Subscribers.userId eq userId }) {
            it[isActive] = false
            it[unsubscriptionDate] = LocalDateTime.now()
        }
    }
}

// Проверить, подписан ли пользователь (активен ли)
fun isSubscribed(userId: Long): Boolean {
    return transaction {
        Subscribers.selectAll()
            .where { (Subscribers.userId eq userId) and (Subscribers.isActive eq true) }
            .count() > 0
    }
}

// Получить всех активных подписчиков
fun getAllSubscribers(): List<Pair<Long, String>> {
    return transaction {
        Subscribers.selectAll()
            .where { Subscribers.isActive eq true }
            .map {
                val fullName = if (it[Subscribers.lastName] != null) {
                    "${it[Subscribers.firstName]} ${it[Subscribers.lastName]}"
                } else {
                    it[Subscribers.firstName]
                }
                it[Subscribers.userId] to fullName
            }
    }
}

// Получить всех активных подписчиков с датой подписки
fun getAllSubscribersWithDate(): List<Triple<Long, String, LocalDateTime>> {
    return transaction {
        Subscribers.selectAll()
            .where { Subscribers.isActive eq true }
            .map {
                val fullName = if (it[Subscribers.lastName] != null) {
                    "${it[Subscribers.firstName]} ${it[Subscribers.lastName]}"
                } else {
                    it[Subscribers.firstName]
                }
                Triple(
                    it[Subscribers.userId],
                    fullName,
                    it[Subscribers.subscriptionDate]
                )
            }
    }
}

// Получить дату подписки пользователя
fun getSubscriptionDate(userId: Long): LocalDateTime? {
    return transaction {
        Subscribers.selectAll()
            .where { Subscribers.userId eq userId }
            .map { it[Subscribers.subscriptionDate] }
            .firstOrNull()
    }
}

// Получить дату отписки пользователя
fun getUnsubscriptionDate(userId: Long): LocalDateTime? {
    return transaction {
        Subscribers.selectAll()
            .where { Subscribers.userId eq userId }
            .map { it[Subscribers.unsubscriptionDate] }
            .firstOrNull()
    }
}

fun incrementNotificationCount(userId: Long) {
    transaction {
        exec("UPDATE Subscribers SET notification_count = notification_count + 1 WHERE user_id = $userId")
    }
}

// ========== СТАТИСТИЧЕСКИЕ ФУНКЦИИ ==========

// Получить всех подписчиков (включая неактивных) для статистики
fun getAllSubscribersForStats(): List<SubscriberStats> {
    return transaction {
        Subscribers.selectAll().map {
            SubscriberStats(
                userId = it[Subscribers.userId],
                fullName = if (it[Subscribers.lastName] != null) {
                    "${it[Subscribers.firstName]} ${it[Subscribers.lastName]}"
                } else {
                    it[Subscribers.firstName]
                },
                subscriptionDate = it[Subscribers.subscriptionDate],
                isActive = it[Subscribers.isActive],
                unsubscriptionDate = it[Subscribers.unsubscriptionDate]
            )
        }
    }
}

// Data class для статистики
data class SubscriberStats(
    val userId: Long,
    val fullName: String,
    val subscriptionDate: LocalDateTime,
    val isActive: Boolean,
    val unsubscriptionDate: LocalDateTime?
)

// Получить статистику подписчиков
fun getSubscriptionStats(): SubscriptionStatistics {
    return transaction {
        val all = Subscribers.selectAll()
        val active = all.count { it[Subscribers.isActive] }
        val inactive = all.count { !it[Subscribers.isActive] }
        val total = all.count()

        SubscriptionStatistics(
            totalSubscribers = total.toInt(),
            activeSubscribers = active,
            inactiveSubscribers = inactive
        )
    }
}

data class SubscriptionStatistics(
    val totalSubscribers: Int,
    val activeSubscribers: Int,
    val inactiveSubscribers: Int
)

// Функции для работы со спектаклями
fun addPerformance(title: String, url: String): Int {
    return transaction {
        val id = Performances.insert {
            it[Performances.title] = title
            it[Performances.url] = url
        } get Performances.id
        id
    }
}

fun upsertPerformance(title: String, url: String, scene: String?): Int {
    return transaction {
        val existing = Performances.selectAll()
            .where { Performances.url eq url }
            .firstOrNull()

        if (existing != null) {
            Performances.update({ Performances.url eq url }) {
                it[Performances.title] = title
            }
            existing[Performances.id]
        } else {
            Performances.insert {
                it[Performances.title] = title
                it[Performances.url] = url
                it[Performances.scene] = scene
            } get Performances.id
        }
    }
}

fun getAllPerformances(): List<Triple<Int, String, String>> {
    return transaction {
        Performances.selectAll().map {
            Triple(it[Performances.id], it[Performances.title], it[Performances.url])
        }
    }
}

fun clearPerformances() {
    transaction {
        Performances.deleteAll()
    }
}

fun ensureSubscriberExists(userId: Long, firstName: String, lastName: String?, username: String?) {
    transaction {
        val exists = Subscribers.selectAll()
            .where { Subscribers.userId eq userId }
            .count() > 0

        if (!exists) {
            Subscribers.insert {
                it[Subscribers.userId] = userId
                it[Subscribers.firstName] = firstName
                it[Subscribers.lastName] = lastName
                it[Subscribers.username] = username
                it[subscriptionDate] = LocalDateTime.now()
                it[isActive] = true
                it[unsubscriptionDate] = null
            }
        }
    }
}

fun getSubscribersForPerformance(performanceId: Int): List<Long> {
    return transaction {
        (UserPerformanceSubscriptions innerJoin Subscribers)
            .selectAll()
            .where {
                (UserPerformanceSubscriptions.performanceId eq performanceId) and
                        (Subscribers.isActive eq true)
            }
            .map { it[UserPerformanceSubscriptions.userId] }
    }
}

fun getPerformancesWithSubscribers(): List<Triple<Int, String, String>> {
    return transaction {
        (Performances innerJoin UserPerformanceSubscriptions)
            .select(Performances.id, Performances.title, Performances.url)
            .withDistinct()
            .map {
                Triple(it[Performances.id], it[Performances.title], it[Performances.url])
            }
    }
}

// Функции для работы с подписками на спектакли (без изменений)
fun subscribeUserToPerformance(userId: Long, performanceId: Int) {
    transaction {
        val exists = UserPerformanceSubscriptions.selectAll()
            .where {
                (UserPerformanceSubscriptions.userId eq userId) and
                        (UserPerformanceSubscriptions.performanceId eq performanceId)
            }
            .count() > 0

        if (!exists) {
            UserPerformanceSubscriptions.insert {
                it[UserPerformanceSubscriptions.userId] = userId
                it[UserPerformanceSubscriptions.performanceId] = performanceId
                it[UserPerformanceSubscriptions.subscribedAt] = LocalDateTime.now()
            }
        }
    }
}

fun unsubscribeUserFromPerformance(userId: Long, performanceId: Int) {
    transaction {
        UserPerformanceSubscriptions.deleteWhere {
            (UserPerformanceSubscriptions.userId eq userId) and
                    (UserPerformanceSubscriptions.performanceId eq performanceId)
        }
    }
}

fun isUserSubscribedToPerformance(userId: Long, performanceId: Int): Boolean {
    return transaction {
        UserPerformanceSubscriptions.selectAll()
            .where {
                (UserPerformanceSubscriptions.userId eq userId) and
                        (UserPerformanceSubscriptions.performanceId eq performanceId)
            }
            .count() > 0
    }
}

data class PerformanceSubscriberInfo(
    val performanceTitle: String,
    val subscriberName: String,
    val username: String?,
    val subscriptionDate: LocalDateTime,
    val notificationCount: Int,
)

fun getPerformanceSubscriptionDetails(): List<PerformanceSubscriberInfo> {
    return transaction {
        (UserPerformanceSubscriptions innerJoin Performances innerJoin Subscribers)
            .selectAll()
            .map {
                val fullName = if (it[Subscribers.lastName] != null) {
                    "${it[Subscribers.firstName]} ${it[Subscribers.lastName]}"
                } else {
                    it[Subscribers.firstName]
                }
                PerformanceSubscriberInfo(
                    performanceTitle = it[Performances.title],
                    subscriberName = fullName,
                    username = it[Subscribers.username],
                    subscriptionDate = it[UserPerformanceSubscriptions.subscribedAt],
                    notificationCount = it[Subscribers.notificationCount],
                )
            }
    }
}

fun getUserSubscribedPerformances(userId: Long): List<Triple<Int, String, String>> {
    return transaction {
        (UserPerformanceSubscriptions innerJoin Performances)
            .selectAll()
            .where { UserPerformanceSubscriptions.userId eq userId }
            .map {
                Triple(
                    it[Performances.id],
                    it[Performances.title],
                    it[Performances.url]
                )
            }
    }
}