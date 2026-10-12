# Activity Service — сущности и взаимосвязи

Справочник по доменной модели Activity Service: сущности, поля, статусы, связи между собой и связи с другими сервисами Friend Hub.

> Модель v2: структурированные механизмы решений (Suggestion, Poll, Checklist) заменены на **чат активности**. Обсуждение — в чате, решения фиксирует владелец правкой активности.

---

## 1. Контекст: место Activity Service в Friend Hub

```text
User Service (кто? пользователи, теги, друзья)
        │
        │ userId   ← просто id, НЕ FK (другая БД)
        ▼
Activity Service (что / когда / где / с кем делаем? + чат)   ← этот сервис
        │
        │ activityId          ← просто id, НЕ FK (другая БД)
        ▼
Expense Service (деньги, долги, расчёты)
```

Ключевые правила:

- Каждый сервис владеет своей БД (`friendhub_activity`), никаких cross-database queries.
- **Activity = событие/встреча.** Отдельного Event Service нет.
- **Групп в модели Activity Service нет.** Любой пользователь может создать активность и пригласить любых пользователей приложения (по тегу/id) — друзей или нет. Дружба (User Service) — социальная связь, но не ограничение на приглашения.
- Activity Service хранит только **id** чужих сущностей (users) — валидация через REST в User Service.
- Activity Service **не владеет**: пользователями, дружбой, расходами.

---

## 2. Пользовательский флоу

```text
1. Любой юзер создаёт активность
   (title, описание, время, место + приглашает людей
    по тегу/username/id — друзей или любых юзеров приложения)
        │
        ▼
2. Активность создана → чат существует неявно
   (участники приглашены, статусы INVITED)
        │
        ▼
3. Приглашённые вступают (ACCEPTED / MAYBE)
   → получают право писать в чат
        │
        ▼
4. Обсуждение в чате
   (что делаем, во сколько, кто что принесёт)
        │
        ▼
5. Владелец правит активность по итогам обсуждения
   (только owner может менять активность)
        │
        ▼
6. Активность проходит (FINISHED)
   → чат архивируется: read-only, история доступна
   → через 7 дней сообщения удаляются фоновой джобой
```

---

## 3. Карта сущностей

```text
                        User Service (другая БД)
                     ┌──────────────────────────┐
                     │  User (id, tag/username) │
                     └────────────┬─────────────┘
                                  │ creatorId, userId
                                  │ (id-ссылки, без FK)
                                  ▼
┌───────────────────────────────────────────────────────────────┐
│                    ACTIVITY SERVICE                           │
│                                                               │
│  Activity  (корень агрегата)                                  │
│     │                                                         │
│     │ activityId (id-ссылка внутри своей БД)                  │
│     ├──────────────────────┐                                  │
│     ▼                      ▼                                  │
│  ActivityParticipant    ChatMessage                           │
│     │                      │                                  │
│     │ userId               │ authorId                         │
│     │ (User Svc)           │ (User Svc)                        │
│                                                               │
│  Participant — одновременно список доступа к чату             │
│  (userId, invitedBy, authorId — id-ссылки на User Service)    │
└───────────────────────────────────────────────────────────────┘
                                  │
                                  │ activityId (id-ссылка, без FK)
                                  ▼
                        Expense Service (другая БД)
                     ┌──────────────────────────┐
                     │  Expense, ExpenseShare   │
                     │  Balance, Settlement     │
                     └──────────────────────────┘
```

**Activity — корень агрегата.** Обе дочерние сущности не имеют смысла без Activity. **Чат — не отдельная сущность**: его жизненный цикл 1:1 с активностью (создаётся вместе, удаляется вместе), поэтому «чат» — это просто совокупность `ChatMessage` активности.

---

## 4. Сущности

### 4.1. Activity — сама активность / встреча

Корневая сущность. Примеры: «Настолки в субботу», «Поездка в Краков», «День рождения Кати».

| Поле | Тип | Описание |
|---|---|---|
| id | Long | PK, генерируется |
| **creatorId** | Long | **ссылка на User (User Service)**, автор/владелец |
| finishedAt | Instant? | когда перешла в FINISHED/CANCELLED (retention чата — 7 дней) |
| title | String | название |
| description | String | описание |
| type | ActivityType | для фильтрации (см. 4.2) |
| startDateTime | Instant | когда начинается |
| endDateTime | Instant? | когда заканчивается |
| location | String | где встречаемся |
| status | ActivityStatus | жизненный цикл (см. 4.3) |
| createdAt / updatedAt | Instant | аудит |

**Жизненный цикл:**

```text
DRAFT → PLANNED → ACTIVE → FINISHED
   │        │        │
   └────────┴────────┴──→ CANCELLED
```

- `DRAFT` — черновик, ещё не анонсирована
- `PLANNED` — запланирована, идёт сбор участников и обсуждение в чате
- `ACTIVE` — происходит прямо сейчас (чат ещё жив)
- `FINISHED` — завершена → **чат архивируется** (read-only, доступен 7 дней, потом удаляется джобой)
- `CANCELLED` — отменена из любого незавершённого состояния → **чат архивируется** (аналогично)

Переходы — отдельные ручки: `/plan`, `/start`, `/finish`, `/cancel`.

**Правка активности:** только владелец (`creatorId`), только в статусах до `FINISHED`/`CANCELLED`. По итогам обсуждения в чате владелец обновляет title / description / время / место — это и есть механизм «фиксации решений».

### 4.2. ActivityType — enum, без отдельной сущности

```text
BOARD_GAMES, VIDEO_GAMES, MOVIE, RESTAURANT, TRIP,
PARTY, SPORT, WALK, CONCERT, OTHER
```

Нужен только для фильтрации. Отдельная сущность на каждый тип **не создаётся**.

### 4.3. ActivityParticipant — участник активности (RSVP)

| Поле | Тип | Описание |
|---|---|---|
| id | Long | PK |
| **activityId** | Long | ссылка на Activity (своя БД) |
| **userId** | Long | ссылка на User (User Service) |
| status | ParticipantStatus | INVITED / ACCEPTED / DECLINED / MAYBE |
| **invitedBy** | Long | ссылка на User — кто пригласил |
| invitedAt | Instant | |
| respondedAt | Instant? | когда ответил на приглашение |

**Инвариант:** `UNIQUE(activity_id, user_id)` — один пользователь не может быть участником одной активности дважды.

**Кого можно пригласить:** любого пользователя приложения (поиск по тегу/username через User Service). Дружба — не ограничение, а просто удобный источник кандидатов для инвайта. Групп в модели нет.

**Двойная роль:** participant — это одновременно **список доступа к чату**:

| Статус участника | Читать чат | Писать в чат |
|---|---|---|
| INVITED | ✅ (видит, что обсуждают, прежде чем соглашаться) | ❌ |
| ACCEPTED | ✅ | ✅ |
| MAYBE | ✅ | ✅ |
| DECLINED | ❌ (или ✅ — решение, см. открытые вопросы) | ❌ |

### 4.4. ChatMessage — сообщение в чате активности

| Поле | Тип | Описание |
|---|---|---|
| id | Long | PK, монотонно растущий (курсор поллинга) |
| **activityId** | Long | ссылка на Activity |
| **authorId** | Long | ссылка на User (User Service) |
| text | String | текст сообщения |
| createdAt | Instant | |
| deletedAt | Instant? | soft delete — автор удалил своё сообщение |

Правила:

- Писать может участник со статусом `ACCEPTED`/`MAYBE` — **и только пока активность не завершена** (после `FINISHED`/`CANCELLED` чат read-only).
- Читать чат (включая архив) могут участники активности.
- Автор может удалить **своё** сообщение (soft delete, `deletedAt`).
- Редактирование сообщений — не делаем на MVP.
- При переходе активности в `FINISHED`/`CANCELLED` — чат **архивируется** (read-only), сообщения остаются доступными для чтения **7 дней** с момента завершения. Физическое удаление — фоновая джоба (см. 4.5).

**Чата как отдельной таблицы нет:** чат создаётся неявно (первым сообщением) и умирает вместе с активностью. Отдельная сущность `Chat` была бы сущностью без собственной жизни (1:1 с Activity, без своих полей и правил).

### 4.5. Архив чата и retention

Механика архивации:

```text
Activity → FINISHED / CANCELLED
        │
        ▼
чат становится read-only
(проверка в ChatService: статус активности не терминальный)
        │
        ▼
7 дней сообщения доступны для чтения участникам
        │
        ▼
фоновая джоба удаляет сообщения физически
(критерий: activity.status in (FINISHED, CANCELLED)
           AND activity.finishedAt + 7 days < now)
```

Для этого Activity нужен момент завершения:

| Поле Activity | Тип | Описание |
|---|---|---|
| finishedAt | Instant? | когда перешла в FINISHED/CANCELLED (для retention чата) |

Джоба (ежедневно, например `@Scheduled`):

```kotlin
@Scheduled(cron = "0 0 3 * * *")   // каждый день в 3:00
suspend fun purgeExpiredChats() {
    chatMessageRepository.deleteByExpiredActivityRetention(
        retentionDays = 7,
        now = Instant.now(),
    )
}
```

Запрос на удаление (JOIN через activity, без FK):

```sql
DELETE FROM chat_messages m
USING activities a
WHERE m.activity_id = a.id
  AND a.status IN ('FINISHED', 'CANCELLED')
  AND a.finished_at + INTERVAL '7 days' < now()
```

Почему так, а не `deletedAt` на каждом сообщении: дедлайн общий у всего чата (7 дней от завершения активности), а не у каждого сообщения отдельно — меньше записей и нет «частично удалённого» архива.

---

## 5. Сводная таблица связей

| Связь | Механизм | Где живёт ссылка | Валидация |
|---|---|---|---|
| Activity → User (creator) | id-ссылка через сервисную границу | `Activity.creatorId` | из JWT при создании |
| Activity → ActivityParticipant | id-ссылка внутри БД | `Participant.activityId` | в приложении (сервис) + UNIQUE |
| Activity → ChatMessage | id-ссылка внутри БД | `ChatMessage.activityId` | в приложении |
| ChatMessage → User (author) | id-ссылка через сервисную границу | `ChatMessage.authorId` | доступ проверяется через Participant |
| Expense → Activity (обратная) | id-ссылка через сервисную границу | `Expense.activityId` в Expense Service | REST в Activity Service / Kafka-проекция |

**Принцип:**

- **Внутри своей БД** — связь через id-поле в entity + метод репозитория (`findByActivityId`). FK в БД не используем — проверки в приложении; UNIQUE-констрейнты оставляем как страховку от гонок.
- **Через сервисную границу** — только id (Long), без FK и без запросов к чужой БД. Проверки через REST в User Service (например, `GET /internal/users/{tag}` — поиск юзера для инвайта).

---

## 6. Реализация в коде (R2DBC, Kotlin)

### 6.1. Entity — только id-поля, никаких объектных ссылок

```kotlin
@Table("activities")
data class ActivityEntity(
    @Id val id: Long? = null,
    val creatorId: Long,        // User Service — просто Long
    val title: String,
    val description: String,
    val startDateTime: Instant,
    val location: String,
    val status: ActivityStatus,
    val finishedAt: Instant? = null,   // для retention чата (7 дней)
)

@Table("activity_participants")
data class ActivityParticipantEntity(
    @Id val id: Long? = null,
    val activityId: Long,       // своя БД — тоже просто Long
    val userId: Long,
    val status: ParticipantStatus,
    val invitedBy: Long,
    val invitedAt: Instant,
    val respondedAt: Instant? = null,
)

@Table("chat_messages")
data class ChatMessageEntity(
    @Id val id: Long? = null,
    val activityId: Long,
    val authorId: Long,
    val text: String,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
)
```

R2DBC не поддерживает `@OneToMany`/`@ManyToOne` — связь выражается только id-полями и запросами.

### 6.2. Repository — связь выражается методами

```kotlin
interface ActivityRepository : CoroutineCrudRepository<ActivityEntity, Long> {
    fun findByCreatorId(creatorId: Long): Flow<ActivityEntity>
}

interface ParticipantRepository : CoroutineCrudRepository<ActivityParticipantEntity, Long> {
    fun findByActivityId(activityId: Long): Flow<ActivityParticipantEntity>
    suspend fun findByActivityIdAndUserId(activityId: Long, userId: Long): ActivityParticipantEntity?
}

interface ChatMessageRepository : CoroutineCrudRepository<ChatMessageEntity, Long> {
    // поллинг: сообщения после курсора, старые → новые
    fun findByActivityIdAndIdGreaterThanOrderByIdAsc(
        activityId: Long,
        afterMessageId: Long,
    ): Flow<ChatMessageEntity>

    // очистка просроченных архивов (retention 7 дней)
    @Query("""
        DELETE FROM chat_messages m
        USING activities a
        WHERE m.activity_id = a.id
          AND a.status IN ('FINISHED', 'CANCELLED')
          AND a.finished_at + INTERVAL '7 days' < :now
    """)
    suspend fun deleteExpiredArchives(now: Instant): Long
}
```

### 6.3. Service — «связывание», проверки и жизненный цикл чата

```kotlin
@Service
class ChatService(
    private val activityRepository: ActivityRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val participantRepository: ParticipantRepository,
) {
    suspend fun postMessage(activityId: Long, authorId: Long, text: String): ChatMessageEntity {
        // 1. активность ещё живая — архив read-only
        val activity = activityRepository.findById(activityId)
            ?: throw ActivityNotFoundException(activityId)
        if (activity.status in terminalStatuses) {
            throw ChatReadOnlyException(activityId)   // FINISHED/CANCELLED → только читать
        }

        // 2. доступ: автор — участник с правом письма
        val participant = participantRepository.findByActivityIdAndUserId(activityId, authorId)
            ?: throw NotActivityParticipantException(activityId, authorId)
        if (participant.status !in setOf(ParticipantStatus.ACCEPTED, ParticipantStatus.MAYBE)) {
            throw ChatWriteDeniedException(activityId, authorId)
        }

        // 3. запись
        return chatMessageRepository.save(
            ChatMessageEntity(
                activityId = activityId,
                authorId = authorId,
                text = text,
                createdAt = Instant.now(),
            )
        )
    }
}

@Service
class ActivityLifecycleService(
    private val activityRepository: ActivityRepository,
) {
    @Transactional
    suspend fun finish(activityId: Long): ActivityEntity {
        val activity = activityRepository.findById(activityId)
            ?: throw ActivityNotFoundException(activityId)
        // смена статуса + фиксация момента завершения (для retention чата) — атомарно
        return activityRepository.save(
            activity.copy(
                status = ActivityStatus.FINISHED,
                finishedAt = Instant.now(),
            )
        )
    }
}

@Component
class ChatRetentionJob(
    private val chatMessageRepository: ChatMessageRepository,
) {
    // ежедневная очистка просроченных архивов
    @Scheduled(cron = "0 0 3 * * *")
    suspend fun purgeExpiredChats() {
        chatMessageRepository.deleteExpiredArchives(Instant.now())
    }
}
```

### 6.4. Композиция для ответа (вместо JOIN-графа Hibernate)

```kotlin
suspend fun getActivityWithParticipants(id: Long): ActivityResponse {
    val activity = activityRepository.findById(id) ?: throw ActivityNotFoundException(id)
    val participants = participantRepository.findByActivityId(id).toList()
    return ActivityResponse.from(activity, participants)   // DTO наружу, не entity
}
```

Цепочка слоёв: **Controller → Service → Repository** (реализацию репозитория генерирует Spring Data, отдельный адаптер-слой не нужен). Entity наружу не отдаём — только Request/Response DTO.

---

## 7. Миграции Flyway (порядок)

```text
db/migration/
  V1__create_activities.sql
  V2__create_activity_participants.sql
  V3__create_chat_messages.sql
```

Обязательные constraints (не FK — защита от дублей при конкурентных записях):

```sql
UNIQUE (activity_id, user_id)      -- activity_participants
```

Индексы:

```text
activity(creator_id)
activity_participant(activity_id, user_id)
chat_message(activity_id, id)      -- курсор поллинга
```

---

## 8. API

```text
# Activities
POST   /api/v1/activities                            ← создать (чат создаётся неявно)
GET    /api/v1/activities?participant={userId}       ← «мои активности» (через participants)
GET    /api/v1/activities?creator={userId}           ← «созданные мной»
GET    /api/v1/activities/{activityId}
PATCH  /api/v1/activities/{activityId}              ← только owner
DELETE /api/v1/activities/{activityId}              ← только owner

# Lifecycle
POST   /api/v1/activities/{activityId}/plan
POST   /api/v1/activities/{activityId}/start
POST   /api/v1/activities/{activityId}/finish       ← чат → read-only архив (7 дней)
POST   /api/v1/activities/{activityId}/cancel       ← чат → read-only архив (7 дней)

# Participants (RSVP)
POST   /api/v1/activities/{activityId}/participants
GET    /api/v1/activities/{activityId}/participants
DELETE /api/v1/activities/{activityId}/participants/{userId}
POST   /api/v1/activities/{activityId}/accept
POST   /api/v1/activities/{activityId}/decline
POST   /api/v1/activities/{activityId}/maybe

# Chat
GET    /api/v1/activities/{activityId}/chat/messages?afterMessageId={id}   ← поллинг (и чтение архива)
POST   /api/v1/activities/{activityId}/chat/messages                       ← только пока активность живая
DELETE /api/v1/activities/{activityId}/chat/messages/{messageId}           ← только своё
```

Ручек создания/удаления чата **нет** — жизненный цикл чата полностью подчинён активности. GET работает и для архива (7 дней после завершения), POST для завершённой активности → `409/422`.

**Доставка сообщений:** MVP — поллинг с курсором `afterMessageId` (клиент спрашивает «что нового после сообщения N»). Следующий шаг при необходимости — SSE (на MVC + корутинах работает), WebSocket не нужен.

---

## 9. Kafka-события (topic: `friendhub.activity.events`)

```text
ActivityCreated, ActivityUpdated, ActivityPlanned, ActivityStarted,
ActivityFinished, ActivityCancelled

ActivityParticipantAdded, ActivityParticipantAccepted,
ActivityParticipantDeclined, ActivityParticipantRemoved
```

Чат-события (`ChatMessagePosted` и т.п.) **не публикуем** — они не нужны другим сервисам (правило: в Kafka только то, что потенциально нужно другим).

Правила публикации:

- Публикация через **Transactional Outbox**: запись Activity + запись `outbox_event` в одной транзакции, отдельный publisher отправляет в Kafka.
- Потребители — **идемпотентные** (таблица `processed_event`).

---

## 10. Правила доступа

```text
видеть Activity            → участники активности (creator + приглашённые)
создавать Activity         → любой аутентифицированный юзер
приглашать участников      → владелец (позже можно разрешить участникам)
менять Activity            → только владелец (creatorId)
удалять Activity           → только владелец
писать в чат               → участник ACCEPTED / MAYBE, пока активность не завершена
читать чат                 → участник Activity (включая INVITED), включая архив (7 дней)
удалять сообщение          → только автор сообщения
```

---

## 11. Открытые вопросы (решить по ходу)

1. **DECLINED — читать чат?** Сейчас: нет. Альтернатива: читать можно (вдруг передумает). Решение можно отложить до появления UI.
2. **Медиа в чате.** Пока только текст. Картинки/файлы — отдельная задача (S3/MinIO + ссылка в сообщении).
3. **SSE вместо поллинга** — когда появится фронтенд и захочется «живой» чат.
4. **Retention ≠ 7 дней.** Если захочется настраивать — вынести в конфиг (`chat.retention-days`), не хардкодить в запросе.
5. **Кого можно пригласить.** Сейчас: любого юзера приложения. Если позже захочется ограничить (только друзья / только по ссылке-инвайту) — это отдельная проверка в User Service, модель Activity Service не меняется.
6. **FriendGroup в User Service.** Группы больше не используются Activity Service. Решить: оставить FriendGroup в User Service как справочник «компаний» для быстрого инвайта пачкой, или убрать из архитектуры совсем.

---

## 12. Статус реализации (что уже есть в коде)

| Сущность | Entity | Repository | Миграция |
|---|---|---|---|
| Activity | ⚠️ `ActivityEntity` — есть `groupId` (убрать), нет `finishedAt` | ✅ `ActivityRepository` | ❌ |
| ActivityParticipant | ❌ | ❌ | ❌ |
| ChatMessage | ❌ | ❌ | ❌ |

Следующий шаг по MVP: `ActivityParticipant` — entity + repository + миграция `V2__create_activity_participants.sql` + ручки participants/accept/decline. Затем `ChatMessage` + миграция `V3__create_chat_messages.sql`.
