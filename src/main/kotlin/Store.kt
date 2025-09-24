import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.ISqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table.autoIncrement
import org.jetbrains.exposed.sql.Table.nullable
import org.jetbrains.exposed.sql.Table.references
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

// Таблица подписчиков
object Subscribers : Table() {
    val userId = Table.long("user_id")
    val firstName = Table.varchar("first_name", 50)
    val lastName = Table.varchar("last_name", 50).nullable()
    val username = Table.varchar("user_name", 50).nullable()
}

// Таблица спектаклей
object Performances : Table() {
    val id = Table.integer("id").autoIncrement()
    val title = Table.varchar("title", 255)
    val url = Table.varchar("url", 255)

    override val primaryKey = Table.PrimaryKey(id)
}

// Таблица подписок пользователей на спектакли
object UserPerformanceSubscriptions : Table() {
    val userId = Table.long("user_id").references(Subscribers.userId)
    val performanceId = Table.integer("performance_id").references(Performances.id)

    override val primaryKey = Table.PrimaryKey(userId, performanceId)
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
}

// Добавить подписчика
fun addSubscriber(userId: Long, firstName: String, lastName: String?, username: String?) {
    transaction {
        Subscribers.insert {
            it[Subscribers.userId] = userId
            it[Subscribers.firstName] = firstName
            it[Subscribers.lastName] = lastName
            it[Subscribers.username] = username
        }
    }
}

// Удалить подписчика
fun removeSubscriber(userId: Long) {
    transaction {
        Subscribers.deleteWhere { Subscribers.userId eq userId }
    }
}

fun isSubscribed(userId: Long): Boolean {
    return transaction {
        val query = Subscribers.selectAll()
        query.forEach {
            val existId = it[Subscribers.userId]
            if (existId == userId) return@transaction true
        }
        return@transaction false
    }
}

// Получить всех подписчиков
fun getAllSubscribers(): List<Pair<Long, String>> {
    return transaction {
        Subscribers.selectAll().map {
            val fullName = if (it[Subscribers.lastName] != null) {
                "${it[Subscribers.firstName]} ${it[Subscribers.lastName]}"
            } else {
                it[Subscribers.firstName]
            }
            it[Subscribers.userId] to fullName
        }
    }
}

// Функции для работы со спектаклями

// Добавить спектакль
fun addPerformance(title: String, url: String): Int {
    return transaction {
        val id = Performances.insert {
            it[Performances.title] = title
            it[Performances.url] = url
        } get Performances.id
        id
    }
}

// Получить все спектакли
fun getAllPerformances(): List<Triple<Int, String, String>> {
    return transaction {
        Performances.selectAll().map {
            Triple(it[Performances.id], it[Performances.title], it[Performances.url])
        }
    }
}

// Очистить таблицу спектаклей
fun clearPerformances() {
    transaction {
        Performances.deleteAll()
    }
}

// Функции для работы с подписками на спектакли

// Подписать пользователя на спектакль
fun subscribeUserToPerformance(userId: Long, performanceId: Int) {
    transaction {
        // Проверяем, существует ли уже такая подписка
        val query = UserPerformanceSubscriptions.selectAll()
        var exists = false
        query.forEach {
            if (it[UserPerformanceSubscriptions.userId] == userId &&
                it[UserPerformanceSubscriptions.performanceId] == performanceId
            ) {
                exists = true
                return@forEach
            }
        }

        if (!exists) {
            UserPerformanceSubscriptions.insert {
                it[UserPerformanceSubscriptions.userId] = userId
                it[UserPerformanceSubscriptions.performanceId] = performanceId
            }
        }
    }
}

// Отписать пользователя от спектакля
fun unsubscribeUserFromPerformance(userId: Long, performanceId: Int) {
    transaction {
        UserPerformanceSubscriptions.deleteWhere {
            (UserPerformanceSubscriptions.userId eq userId) and
                    (UserPerformanceSubscriptions.performanceId eq performanceId)
        }
    }
}

// Проверить, подписан ли пользователь на спектакль
fun isUserSubscribedToPerformance(userId: Long, performanceId: Int): Boolean {
    return transaction {
        val query = UserPerformanceSubscriptions.selectAll()
        query.forEach {
            if (it[UserPerformanceSubscriptions.userId] == userId &&
                it[UserPerformanceSubscriptions.performanceId] == performanceId
            ) {
                return@transaction true
            }
        }
        return@transaction false
    }
}

// Получить все спектакли, на которые подписан пользователь
fun getUserSubscribedPerformances(userId: Long): List<Triple<Int, String, String>> {
    return transaction {
        val result = mutableListOf<Triple<Int, String, String>>()
        val subscriptions = UserPerformanceSubscriptions.selectAll()
        val performanceIds = mutableListOf<Int>()

        // Собираем ID спектаклей, на которые подписан пользователь
        subscriptions.forEach {
            if (it[UserPerformanceSubscriptions.userId] == userId) {
                performanceIds.add(it[UserPerformanceSubscriptions.performanceId])
            }
        }

        // Получаем информацию о каждом спектакле
        if (performanceIds.isNotEmpty()) {
            val performances = Performances.selectAll()
            performances.forEach {
                val perfId = it[Performances.id]
                if (perfId in performanceIds) {
                    result.add(
                        Triple(
                            perfId,
                            it[Performances.title],
                            it[Performances.url]
                        )
                    )
                }
            }
        }

        result
    }
}
