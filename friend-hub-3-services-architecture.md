# Friend Hub — архитектура и взаимодействие сервисов

## 1. Назначение проекта

**Friend Hub** — pet-project на микросервисной архитектуре для компании друзей.

Идея приложения — дать одной или нескольким компаниям друзей единое место для повседневных совместных сценариев:

- создавать группы друзей;
- добавлять участников;
- организовывать совместные активности;
- указывать дату, время и место;
- приглашать участников;
- подтверждать участие;
- предлагать варианты того, чем заняться;
- голосовать;
- вести чек-листы;
- учитывать совместные расходы;
- автоматически рассчитывать взаимные долги;
- фиксировать выполненные переводы;
- хранить историю активностей.

Проект должен быть полезен не только как учебный backend, но и как приложение, которым можно реально пользоваться.

---

# 2. Основная архитектурная идея

Friend Hub состоит из трёх бизнес-сервисов:

```text
User Service
→ пользователи, дружба, группы

Activity Service
→ события / встречи / совместные активности

Expense Service
→ расходы и взаиморасчёты
```

Отдельного Event Service нет.

В текущей модели:

> **Activity — это и есть событие.**

Пользователь создаёт не абстрактный Event, к которому потом привязывает Activity, а сразу конкретную совместную активность.

Примеры:

```text
"Настолки в субботу"

"Идём в кино"

"Поездка в Краков"

"Шашлыки"

"День рождения Кати"

"Смотрим финал чемпионата"

"Идём в ресторан"
```

---

# 3. Главные bounded contexts

## User Service

Главный вопрос:

> Кто пользуется Friend Hub, с кем он дружит и в каких группах состоит?

## Activity Service

Главный вопрос:

> Что, когда, где и с кем мы собираемся делать?

## Expense Service

Главный вопрос:

> Кто за что заплатил и кто кому теперь должен?

---

# 4. Общая архитектура

```text
                           ┌───────────────────────┐
                           │ Web / Mobile /        │
                           │ Telegram Bot          │
                           └───────────┬───────────┘
                                       │
                                    HTTPS
                                       │
                           ┌───────────▼───────────┐
                           │      API Gateway      │
                           │                       │
                           │ routing               │
                           │ auth context          │
                           │ rate limiting         │
                           │ CORS                  │
                           └───────┬───────┬───────┘
                                   │       │
                   ┌───────────────┘       └──────────────┐
                   │                                      │
                   ▼                                      ▼

          ┌──────────────────┐                  ┌───────────────────┐
          │   User Service   │                  │ Activity Service  │
          │                  │                  │                   │
          │ users            │                  │ activities        │
          │ profiles         │                  │ participants      │
          │ friendships      │                  │ suggestions       │
          │ groups           │                  │ polls             │
          │ memberships      │                  │ checklist         │
          └────────┬─────────┘                  │ lifecycle         │
                   │                            └─────────┬─────────┘
                   ▼                                      │
              PostgreSQL                                  ▼
                                                    PostgreSQL


                             ┌────────────────────┐
                             │  Expense Service   │
                             │                    │
                             │ expenses           │
                             │ expense shares     │
                             │ balances           │
                             │ settlements        │
                             │ payments           │
                             └─────────┬──────────┘
                                       │
                                       ▼
                                  PostgreSQL


                                ┌───────────┐
                                │   Kafka   │
                                └─────┬─────┘
                                      │
              ┌───────────────────────┼────────────────────────┐
              │                       │                        │
              ▼                       ▼                        ▼
     friendhub.user.events  friendhub.activity.events  friendhub.expense.events


                           ┌───────────────────────┐
                           │       Keycloak        │
                           │ OAuth2 / OIDC / JWT   │
                           └───────────────────────┘


                           ┌───────────────────────┐
                           │        Redis          │
                           │ cache / temporary     │
                           │ data / rate limits    │
                           └───────────────────────┘


                           ┌───────────────────────┐
                           │ Observability stack   │
                           │ Prometheus            │
                           │ Grafana               │
                           │ OpenTelemetry         │
                           └───────────────────────┘
```

---

# 5. Основные архитектурные правила

## Rule 1. Каждый сервис владеет своей БД

```text
User Service     → friendhub_user
Activity Service → friendhub_activity
Expense Service  → friendhub_expense
```

## Rule 2. Никаких cross-database queries

Нельзя:

```text
Activity Service
       │
       ▼
friendhub_user DB
```

или:

```text
Expense Service
       │
       ▼
friendhub_activity DB
```

Разрешённые способы взаимодействия:

```text
REST
Kafka
```

## Rule 3. REST — команда или запрос

Когда нужен немедленный ответ:

```text
создать Activity
получить участников
добавить Expense
получить balances
```

## Rule 4. Kafka — факт, который уже произошёл

Примеры:

```text
GroupMemberAdded
ActivityCreated
ActivityFinished
ExpenseCreated
SettlementPaid
```

## Rule 5. Бизнес-логика принадлежит сервису-владельцу

Например:

```text
кто состоит в группе
→ User Service

кто участвует в Activity
→ Activity Service

кто кому должен
→ Expense Service
```

---

# 6. User Service

## 6.1. Ответственность

User Service отвечает за постоянную социальную структуру Friend Hub.

Он управляет:

- пользователями;
- профилями;
- дружбой;
- заявками в друзья;
- группами друзей;
- членством в группах;
- ролями внутри групп;
- блокировками;
- базовыми пользовательскими настройками.

User Service не отвечает за:

- логин и пароль;
- JWT;
- активности;
- участников конкретной активности;
- расходы.

---

# 7. Authentication и User Service

Authentication выполняет Keycloak.

Разделение:

```text
Keycloak
→ кто пользователь?

User Service
→ кто этот пользователь внутри Friend Hub?
```

Пример JWT:

```json
{
  "sub": "auth-user-uuid",
  "preferred_username": "alex"
}
```

User Service хранит:

```text
User
 ├── id
 ├── authId
 ├── username
 ├── displayName
 ├── avatarUrl
 ├── birthDate
 ├── status
 ├── createdAt
 └── updatedAt
```

---

# 8. Friendship

```text
Friendship
 ├── id
 ├── requesterId
 ├── addresseeId
 ├── status
 ├── createdAt
 └── updatedAt
```

Статусы:

```text
PENDING
ACCEPTED
DECLINED
BLOCKED
```

Пример:

```text
Алекс отправляет заявку Диме

PENDING

Дима принимает

ACCEPTED
```

---

# 9. Friend Group

```text
FriendGroup
 ├── id
 ├── name
 ├── description
 ├── avatarUrl
 ├── ownerId
 ├── createdAt
 └── updatedAt
```

Примеры групп:

```text
Настольщики
Универ
Работа
Поездки
```

---

# 10. Group Member

```text
GroupMember
 ├── groupId
 ├── userId
 ├── role
 ├── status
 ├── invitedBy
 ├── joinedAt
 └── updatedAt
```

Роли:

```text
OWNER
ADMIN
MEMBER
```

Статусы:

```text
INVITED
ACTIVE
LEFT
REMOVED
```

---

# 11. User Service API

## Profile

```text
GET    /api/v1/users/me
PATCH  /api/v1/users/me

GET    /api/v1/users/{userId}
```

## Friends

```text
POST   /api/v1/friends/requests
GET    /api/v1/friends
GET    /api/v1/friends/requests

POST   /api/v1/friends/requests/{requestId}/accept
POST   /api/v1/friends/requests/{requestId}/decline

DELETE /api/v1/friends/{userId}
```

## Groups

```text
POST   /api/v1/groups
GET    /api/v1/groups
GET    /api/v1/groups/{groupId}
PATCH  /api/v1/groups/{groupId}
DELETE /api/v1/groups/{groupId}
```

## Group Members

```text
POST   /api/v1/groups/{groupId}/members
GET    /api/v1/groups/{groupId}/members

PATCH  /api/v1/groups/{groupId}/members/{userId}
DELETE /api/v1/groups/{groupId}/members/{userId}
```

---

# 12. User Service Events

Kafka topic:

```text
friendhub.user.events
```

События:

```text
UserCreated
UserUpdated
UserDisabled

FriendRequestCreated
FriendshipCreated
FriendshipRemoved

GroupCreated
GroupUpdated
GroupDeleted

GroupMemberAdded
GroupMemberUpdated
GroupMemberRemoved
```

---

# 13. Activity Service

## 13.1. Главная роль

Activity Service — центральный бизнес-сервис Friend Hub.

Он отвечает за:

- создание совместной активности;
- описание;
- тип;
- дату;
- время;
- место;
- участников;
- приглашения;
- RSVP;
- предложения участников;
- голосования;
- чек-листы;
- статус;
- жизненный цикл;
- историю.

Activity одновременно является встречей / событием.

---

# 14. Activity

```text
Activity
 ├── id
 ├── groupId
 ├── creatorId
 │
 ├── title
 ├── description
 ├── type
 │
 ├── startDateTime
 ├── endDateTime
 ├── location
 │
 ├── status
 │
 ├── createdAt
 └── updatedAt
```

Пример:

```text
id = activity-123

groupId = group-board-games

creatorId = alex

title = "Настолки в субботу"

description =
"Предлагаю сыграть в Nemesis.
Если нас будет мало — можем в The Gang."

type = BOARD_GAMES

startDateTime = 2026-10-17 18:00

location = "У Алекса"

status = PLANNED
```

---

# 15. Activity Type

Тип нужен прежде всего для фильтрации.

Например:

```text
BOARD_GAMES
VIDEO_GAMES
MOVIE
RESTAURANT
TRIP
PARTY
SPORT
WALK
CONCERT
OTHER
```

Не нужно создавать отдельную сущность для каждого типа.

Например:

```text
type = MOVIE

title = "Идём на фильм"

description = "Хочу в IMAX"
```

или:

```text
type = TRIP

title = "Краков на выходные"

description = "Выезжаем утром в субботу"
```

---

# 16. Activity lifecycle

Статусы:

```text
DRAFT
PLANNED
ACTIVE
FINISHED
CANCELLED
```

Основной flow:

```text
DRAFT
  ↓
PLANNED
  ↓
ACTIVE
  ↓
FINISHED
```

Возможная отмена:

```text
DRAFT   → CANCELLED
PLANNED → CANCELLED
ACTIVE  → CANCELLED
```

---

# 17. Activity Participant

```text
ActivityParticipant
 ├── activityId
 ├── userId
 ├── status
 ├── invitedBy
 ├── invitedAt
 └── respondedAt
```

Статусы:

```text
INVITED
ACCEPTED
DECLINED
MAYBE
```

Важно различать:

```text
GroupMember
```

и:

```text
ActivityParticipant
```

Пример:

```text
В группе 10 человек.

На конкретную Activity идут 5.
```

---

# 18. Activity Suggestion

Activity Suggestion — предложение внутри активности.

Например Activity:

```text
Настолки в субботу
```

Участники предлагают:

```text
Алекс:
Nemesis Retaliation

Дима:
The Gang

Катя:
Dune Imperium
```

Модель:

```text
ActivitySuggestion
 ├── id
 ├── activityId
 ├── authorId
 ├── text
 ├── status
 ├── createdAt
 └── updatedAt
```

Статусы:

```text
ACTIVE
SELECTED
REJECTED
DELETED
```

---

# 19. Suggestion Vote

```text
SuggestionVote
 ├── suggestionId
 ├── userId
 ├── vote
 └── createdAt
```

На MVP:

```text
vote = true / false
```

Позже:

```text
LIKE
DISLIKE
STRONG_LIKE
```

---

# 20. Universal Poll

Помимо ActivitySuggestion можно использовать универсальный Poll.

Примеры:

```text
Когда собираемся?

18:00
19:00
20:00
```

```text
Где встречаемся?

У Алекса
У Димы
В антикафе
```

```text
Что заказываем?

Пицца
Суши
Бургеры
```

---

# 21. Poll model

```text
Poll
 ├── id
 ├── activityId
 ├── question
 ├── multipleChoice
 ├── status
 ├── createdBy
 ├── createdAt
 └── closesAt
```

```text
PollOption
 ├── id
 ├── pollId
 ├── value
 └── orderIndex
```

```text
PollVote
 ├── pollId
 ├── optionId
 ├── userId
 └── createdAt
```

---

# 22. Checklist

```text
ActivityChecklistItem
 ├── id
 ├── activityId
 ├── text
 ├── assignedUserId
 ├── completed
 ├── createdBy
 ├── createdAt
 └── completedAt
```

Пример:

```text
✅ Алекс — заказать пиццу
⬜ Дима — принести Nemesis
⬜ Катя — купить напитки
```

---

# 23. Activity Service API

## Activities

```text
POST   /api/v1/groups/{groupId}/activities
GET    /api/v1/groups/{groupId}/activities

GET    /api/v1/activities/{activityId}
PATCH  /api/v1/activities/{activityId}
DELETE /api/v1/activities/{activityId}
```

Пример создания:

```json
{
  "title": "Настолки в субботу",
  "description": "Хочу сыграть в Nemesis",
  "type": "BOARD_GAMES",
  "startDateTime": "2026-10-17T18:00:00",
  "location": "У Алекса"
}
```

---

# 24. Activity Participants API

```text
POST   /api/v1/activities/{activityId}/participants
GET    /api/v1/activities/{activityId}/participants

DELETE /api/v1/activities/{activityId}/participants/{userId}

POST   /api/v1/activities/{activityId}/accept
POST   /api/v1/activities/{activityId}/decline
POST   /api/v1/activities/{activityId}/maybe
```

---

# 25. Suggestions API

```text
POST /api/v1/activities/{activityId}/suggestions
GET  /api/v1/activities/{activityId}/suggestions

PATCH  /api/v1/suggestions/{suggestionId}
DELETE /api/v1/suggestions/{suggestionId}

POST /api/v1/suggestions/{suggestionId}/vote
```

---

# 26. Poll API

```text
POST /api/v1/activities/{activityId}/polls
GET  /api/v1/activities/{activityId}/polls

GET  /api/v1/polls/{pollId}

POST /api/v1/polls/{pollId}/votes
```

---

# 27. Checklist API

```text
POST   /api/v1/activities/{activityId}/checklist
GET    /api/v1/activities/{activityId}/checklist

PATCH  /api/v1/activities/{activityId}/checklist/{itemId}
DELETE /api/v1/activities/{activityId}/checklist/{itemId}
```

---

# 28. Lifecycle API

```text
POST /api/v1/activities/{activityId}/plan
POST /api/v1/activities/{activityId}/start
POST /api/v1/activities/{activityId}/finish
POST /api/v1/activities/{activityId}/cancel
```

---

# 29. Activity Service Events

Kafka topic:

```text
friendhub.activity.events
```

Основные события:

```text
ActivityCreated
ActivityUpdated
ActivityPlanned
ActivityStarted
ActivityFinished
ActivityCancelled

ActivityParticipantAdded
ActivityParticipantAccepted
ActivityParticipantDeclined
ActivityParticipantRemoved

ActivitySuggestionCreated
ActivitySuggestionSelected

PollCreated
PollClosed

ChecklistItemCreated
ChecklistItemCompleted
```

Необязательно публиковать каждый мелкий доменный факт в Kafka.

В Kafka должны попадать только события, которые потенциально нужны другим сервисам.

---

# 30. Expense Service

## 30.1. Ответственность

Expense Service отвечает за финансовую сторону Activity.

Он знает:

- какие расходы были в Activity;
- кто заплатил;
- за кого заплатил;
- как разделить сумму;
- итоговые балансы;
- кто кому должен;
- какие settlements уже выполнены.

---

# 31. Expense

```text
Expense
 ├── id
 ├── activityId
 ├── description
 ├── amount
 ├── currency
 ├── paidByUserId
 ├── createdByUserId
 ├── splitType
 ├── createdAt
 └── updatedAt
```

Пример:

```text
Activity:
Настолки

Expense:
Пицца

amount = 3600 RUB
paidByUserId = alex
```

---

# 32. Expense Share

```text
ExpenseShare
 ├── expenseId
 ├── userId
 ├── amount
 └── shareValue
```

---

# 33. Split Types

## EQUAL

```text
Пицца = 3600

4 человека

900
900
900
900
```

## EXACT

```text
Алекс = 1200
Дима  = 800
Катя  = 1000
```

## PERCENTAGE

```text
Алекс = 50%
Дима  = 30%
Катя  = 20%
```

## SHARES

```text
Алекс = 2 доли
Дима  = 1 доля
Катя  = 1 доля
```

---

# 34. Balance

Пример:

```text
Алекс заплатил 5000
Дима заплатил 1000
Катя заплатила 0
```

При этом фактическая доля каждого:

```text
Алекс 2000
Дима  2000
Катя  2000
```

Balance:

```text
Алекс +3000
Дима  -1000
Катя  -2000
```

---

# 35. Settlement

После расчёта:

```text
Катя → Алекс 2000
Дима → Алекс 1000
```

Модель:

```text
Settlement
 ├── id
 ├── activityId
 ├── fromUserId
 ├── toUserId
 ├── amount
 ├── currency
 ├── status
 ├── createdAt
 └── updatedAt
```

Статусы:

```text
PENDING
PAID
CONFIRMED
CANCELLED
```

---

# 36. Settlement workflow

```text
PENDING
   │
   │ пользователь отметил перевод
   ▼
PAID
   │
   │ получатель подтвердил
   ▼
CONFIRMED
```

---

# 37. Минимизация переводов

Expense Service может иметь отдельный алгоритм оптимизации settlements.

Цели:

- сохранить итоговые балансы;
- уменьшить число переводов;
- убрать лишние промежуточные операции.

Пример:

```text
A должен B
B должен C
C должен D
```

может быть сведено к меньшему количеству переводов.

---

# 38. Expense Service API

## Expenses

```text
POST   /api/v1/activities/{activityId}/expenses
GET    /api/v1/activities/{activityId}/expenses

GET    /api/v1/expenses/{expenseId}
PATCH  /api/v1/expenses/{expenseId}
DELETE /api/v1/expenses/{expenseId}
```

## Balances

```text
GET /api/v1/activities/{activityId}/balances
```

## Settlements

```text
GET /api/v1/activities/{activityId}/settlements

POST /api/v1/settlements/{settlementId}/pay
POST /api/v1/settlements/{settlementId}/confirm
```

---

# 39. Expense Service Events

Kafka topic:

```text
friendhub.expense.events
```

События:

```text
ExpenseCreated
ExpenseUpdated
ExpenseDeleted

SettlementCreated
SettlementPaid
SettlementConfirmed
```

---

# 40. Ownership данных

| Данные | Владелец |
|---|---|
| User | User Service |
| User Profile | User Service |
| Friendship | User Service |
| FriendGroup | User Service |
| GroupMember | User Service |
| Activity | Activity Service |
| ActivityParticipant | Activity Service |
| ActivitySuggestion | Activity Service |
| SuggestionVote | Activity Service |
| Poll | Activity Service |
| PollOption | Activity Service |
| PollVote | Activity Service |
| ChecklistItem | Activity Service |
| Expense | Expense Service |
| ExpenseShare | Expense Service |
| Balance | Expense Service |
| Settlement | Expense Service |

---

# 41. Связи между доменами

Логически:

```text
User
  │
  ▼
FriendGroup
  │
  ▼
Activity
  │
  ▼
Expense
```

Но это не означает цепочку прямых DB-зависимостей.

---

# 42. Создание Activity: полный flow

Пользователь вызывает:

```text
POST /api/v1/groups/{groupId}/activities
```

Activity Service должен проверить:

- существует ли группа;
- состоит ли пользователь в группе;
- разрешено ли ему создавать Activity.

На первом этапе:

```text
Activity Service
       │
       │ REST
       ▼
User Service
```

Например internal endpoint:

```text
GET /internal/groups/{groupId}/members/{userId}
```

Ответ:

```json
{
  "member": true,
  "role": "MEMBER"
}
```

После проверки Activity Service:

```text
BEGIN

INSERT Activity

COMMIT
```

и публикует:

```text
ActivityCreated
```

---

# 43. Добавление участника Activity

Пользователь:

```text
POST /activities/{activityId}/participants
```

Activity Service может проверить через User Service:

```text
является ли приглашённый user участником FriendGroup
```

После успешного добавления:

```text
ActivityParticipantAdded
```

---

# 44. Создание Expense

Пользователь вызывает:

```text
POST /activities/{activityId}/expenses
```

Expense Service должен понимать:

- Activity существует;
- пользователь имеет доступ к Activity;
- участники split действительно относятся к Activity.

На раннем этапе можно использовать REST:

```text
Expense Service
      │
      ▼
Activity Service
```

Например:

```text
GET /internal/activities/{activityId}
GET /internal/activities/{activityId}/participants
```

---

# 45. Как уменьшить REST-зависимости

Когда появится Kafka, Expense Service может хранить локальные projection.

Например:

```text
ActivityReference
 ├── activityId
 ├── groupId
 └── status
```

```text
ActivityParticipantReference
 ├── activityId
 ├── userId
 └── status
```

Эти таблицы обновляются событиями:

```text
ActivityCreated
ActivityParticipantAdded
ActivityParticipantAccepted
ActivityParticipantRemoved
ActivityFinished
ActivityCancelled
```

Тогда Expense Service не обязан делать HTTP-запрос в Activity Service при каждой операции.

---

# 46. Source of Truth

Важно различать source of truth и projection.

Например:

```text
Activity Service
```

является source of truth для:

```text
Activity
ActivityParticipant
```

Expense Service может иметь копию:

```text
ActivityReference
```

но не имеет права считать её основной записью.

---

# 47. REST vs Kafka

## REST

Использовать для:

```text
создать группу

создать Activity

получить Activity

добавить участника

добавить Expense

получить balances

отметить Settlement как paid
```

## Kafka

Использовать для:

```text
GroupMemberAdded

ActivityCreated

ActivityParticipantAdded

ActivityFinished

ExpenseCreated

SettlementPaid
```

---

# 48. Kafka topology

Основные topics:

```text
friendhub.user.events

friendhub.activity.events

friendhub.expense.events
```

---

# 49. Kafka event envelope

Желательно использовать общий envelope.

```json
{
  "eventId": "UUID",
  "eventType": "ActivityCreated",
  "eventVersion": 1,
  "occurredAt": "2026-10-09T20:00:00Z",
  "producer": "activity-service",
  "correlationId": "UUID",
  "payload": {
  }
}
```

Поля:

```text
eventId
→ уникальный ID сообщения

eventType
→ тип события

eventVersion
→ версия schema

occurredAt
→ когда произошло

producer
→ какой сервис создал

correlationId
→ связь с исходным запросом

payload
→ бизнес-данные
```

---

# 50. Пример ActivityCreated

```json
{
  "eventId": "1a2b3c",
  "eventType": "ActivityCreated",
  "eventVersion": 1,
  "occurredAt": "2026-10-17T14:00:00Z",
  "producer": "activity-service",
  "correlationId": "req-123",
  "payload": {
    "activityId": "activity-123",
    "groupId": "group-123",
    "creatorId": "user-123",
    "title": "Настолки в субботу",
    "status": "PLANNED"
  }
}
```

---

# 51. Transactional Outbox

Проблемный вариант:

```kotlin
activityRepository.save(activity)
kafkaTemplate.send(...)
```

Возможна ситуация:

```text
PostgreSQL commit ✅

Kafka send ❌
```

Получится Activity, о которой другие сервисы не узнали.

Решение — Transactional Outbox.

В одной транзакции:

```text
BEGIN

INSERT INTO activity ...

INSERT INTO outbox_event ...

COMMIT
```

Таблица:

```text
outbox_event
 ├── id
 ├── aggregateId
 ├── eventType
 ├── eventVersion
 ├── payload
 ├── createdAt
 ├── publishedAt
 └── retryCount
```

Отдельный publisher:

```text
outbox_event
      │
      ▼
    Kafka
```

---

# 52. Idempotent Consumer

Kafka может доставлять события повторно.

Например:

```text
ActivityFinished
ActivityFinished
```

Consumer не должен два раза выполнять один бизнес-effect.

Можно хранить:

```text
processed_event
 ├── eventId
 ├── consumerName
 └── processedAt
```

Проверка:

```text
событие уже обработано?

YES
→ skip

NO
→ обработать
→ записать processed_event
```

---

# 53. Retry и DLQ

Если Kafka consumer не может обработать сообщение:

```text
message
   │
   ▼
consumer
   │
 error
   ▼
retry
   │
 repeated error
   ▼
DLQ
```

Например:

```text
friendhub.activity.events.dlq
```

---

# 54. PostgreSQL

Для локальной разработки можно использовать один PostgreSQL server.

Внутри:

```text
friendhub_user
friendhub_activity
friendhub_expense
```

---

# 55. Подключения сервисов

```text
User Service

jdbc:postgresql://localhost:5432/friendhub_user
```

```text
Activity Service

jdbc:postgresql://localhost:5432/friendhub_activity
```

```text
Expense Service

jdbc:postgresql://localhost:5432/friendhub_expense
```

---

# 56. Flyway

Каждый сервис имеет собственные миграции.

Например:

```text
user-service/
src/main/resources/db/migration/

V1__create_users.sql
V2__create_friendships.sql
V3__create_groups.sql
```

Activity:

```text
V1__create_activities.sql
V2__create_activity_participants.sql
V3__create_suggestions.sql
V4__create_polls.sql
V5__create_checklist.sql
```

Expense:

```text
V1__create_expenses.sql
V2__create_expense_shares.sql
V3__create_settlements.sql
```

---

# 57. Hibernate

Hibernate не должен самостоятельно менять production schema.

Рекомендуемая настройка:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

То есть:

```text
Flyway
→ создаёт и изменяет schema

Hibernate
→ проверяет её соответствие entity
```

---

# 58. Важные database constraints

Примеры.

User Service:

```text
UNIQUE(auth_id)

UNIQUE(group_id, user_id)
```

Activity Service:

```text
UNIQUE(activity_id, user_id)
```

для Participant.

Для votes:

```text
UNIQUE(suggestion_id, user_id)
```

или:

```text
UNIQUE(poll_id, option_id, user_id)
```

в зависимости от модели.

Expense Service:

```text
amount > 0
```

и:

```text
UNIQUE(...)
```

для бизнес-инвариантов, где это необходимо.

---

# 59. Индексы

User Service:

```text
friendship(requester_id, addressee_id)

group_member(group_id, user_id)
```

Activity Service:

```text
activity(group_id, start_at)

activity_participant(activity_id, user_id)

activity_suggestion(activity_id)

poll(activity_id)
```

Expense Service:

```text
expense(activity_id)

expense_share(expense_id, user_id)

settlement(activity_id, status)
```

---

# 60. API Gateway

Gateway нужен позже.

Пример routing:

```text
/api/v1/users/**
→ user-service

/api/v1/friends/**
→ user-service

/api/v1/groups/**
→ user-service

/api/v1/activities/**
→ activity-service

/api/v1/polls/**
→ activity-service

/api/v1/suggestions/**
→ activity-service

/api/v1/expenses/**
→ expense-service

/api/v1/settlements/**
→ expense-service
```

На MVP можно работать без Gateway:

```text
User Service     :8081

Activity Service :8082

Expense Service  :8083
```

---

# 61. Security

Keycloak отвечает за:

```text
registration
login
password
access token
refresh token
OIDC
```

Backend сервисы являются:

```text
OAuth2 Resource Server
```

Клиент передаёт:

```text
Authorization: Bearer <JWT>
```

---

# 62. Authorization rules

Примеры.

User Service:

```text
только OWNER/ADMIN
может приглашать людей в группу
```

Activity Service:

```text
Activity видят только участники соответствующей группы
```

```text
голосовать может только участник Activity
```

```text
менять Activity может creator или admin
```

Expense Service:

```text
добавить Expense может участник Activity
```

```text
подтвердить Settlement может его получатель
```

---

# 63. Redis

Redis не обязателен на старте.

Возможные сценарии:

```text
cache

rate limiting

temporary invite tokens

frequently requested lists

distributed locks
```

Не надо добавлять Redis только потому, что он часто используется в backend.

---

# 64. Observability

Каждый сервис должен иметь:

```text
logs
metrics
traces
health checks
```

---

# 65. Logging

Структурированный log:

```json
{
  "timestamp": "...",
  "level": "INFO",
  "service": "activity-service",
  "traceId": "...",
  "activityId": "...",
  "message": "Activity created"
}
```

---

# 66. Metrics

Примеры технических:

```text
http_server_requests
jvm_memory
hikari_connections
kafka_consumer_lag
```

Бизнесовые:

```text
activity_created_total

activity_finished_total

expense_created_total

settlement_created_total
```

---

# 67. Tracing

Пример request flow:

```text
Client
  │
  ▼
Gateway
  │
  ▼
Activity Service
  │
  ▼
User Service
```

Один trace:

```text
traceId = abc123
```

Если Activity Service затем публикует Kafka event, correlation context желательно передать дальше.

---

# 68. Health checks

Каждый сервис:

```text
/actuator/health
```

Позже Kubernetes:

```text
readinessProbe
livenessProbe
```

---

# 69. Кодовая архитектура сервисов

Рекомендуемый подход:

```text
hexagonal architecture
```

или:

```text
ports and adapters
```

Пример Activity Service:

```text
src/main/kotlin/com/friendhub/activity/

domain/
    model/
        Activity.kt
        ActivityStatus.kt
        ActivityParticipant.kt
        ActivitySuggestion.kt

    repository/
        ActivityRepository.kt

application/
    command/
        CreateActivityCommand.kt
        AddParticipantCommand.kt

    service/
        CreateActivityService.kt
        AddParticipantService.kt

adapter/
    in/
        web/
            ActivityController.kt
            ActivityRequest.kt
            ActivityResponse.kt

        kafka/
            UserEventConsumer.kt

    out/
        persistence/
            ActivityJpaEntity.kt
            ActivityJpaRepository.kt
            ActivityRepositoryAdapter.kt

        kafka/
            ActivityPublisher.kt

configuration/
    SecurityConfiguration.kt
    KafkaConfiguration.kt
```

Главный принцип:

```text
domain
```

не должен зависеть от:

```text
Spring
Hibernate
Kafka
HTTP
```

---

# 70. DTO vs Entity

Не использовать:

```text
JPA Entity
```

как:

```text
HTTP Request / Response
```

Правильнее:

```text
Request DTO
   ↓
Application Command
   ↓
Domain Model
   ↓
Persistence Entity
```

---

# 71. Ошибки API

Единый формат:

```json
{
  "code": "ACTIVITY_NOT_FOUND",
  "message": "Activity not found",
  "traceId": "abc123",
  "details": {}
}
```

HTTP:

```text
400 — malformed request

401 — unauthenticated

403 — forbidden

404 — not found

409 — conflict

422 — business validation

500 — internal error
```

---

# 72. Concurrency

Для mutable entities можно использовать optimistic locking.

Например:

```text
@Version
```

для:

```text
Activity
Expense
Settlement
```

Полезно, если два человека одновременно редактируют одну запись.

---

# 73. Transactions

ACID transaction ограничена одним сервисом.

Например:

```text
Activity Service
+
friendhub_activity DB
```

Можно:

```text
BEGIN
update Activity
insert Outbox
COMMIT
```

Нельзя делать единую ACID-транзакцию:

```text
User DB
+
Activity DB
+
Expense DB
```

Для межсервисных изменений:

```text
eventual consistency
```

---

# 74. Testing strategy

## Unit tests

Проверяют:

```text
business rules
state transitions
split calculations
settlement algorithms
permissions
```

Без Spring Context.

## Repository tests

Использовать PostgreSQL Testcontainers.

## Integration tests

Поднимать:

```text
Spring Boot
PostgreSQL
Kafka
```

## Contract tests

Проверять:

```text
REST contracts
Kafka event contracts
```

## E2E

Пример:

```text
create group

→ create Activity

→ add participants

→ add suggestions

→ vote

→ add Expense

→ finish Activity

→ calculate Settlement
```

---

# 75. Docker Compose

Локально:

```text
postgres
kafka
keycloak

user-service
activity-service
expense-service
```

Позже:

```text
redis
prometheus
grafana
```

---

# 76. Kubernetes

Каждый сервис:

```text
Deployment
Service
ConfigMap
Secret
```

Для внешнего доступа:

```text
Ingress
```

Health:

```text
readinessProbe
livenessProbe
```

Масштабирование:

```text
HPA
```

---

# 77. Структура monorepo

Рекомендуемый вариант:

```text
friendhub/

services/
    user-service/
    activity-service/
    expense-service/

infrastructure/
    docker/
    kafka/
    keycloak/
    redis/
    prometheus/
    grafana/
    kubernetes/
    helm/

docs/
    architecture.md
```

---

# 78. Локальные порты

```text
API Gateway       8080

User Service      8081

Activity Service  8082

Expense Service   8083

Keycloak          8090

PostgreSQL        5432

Kafka             9092

Redis             6379
```

Gateway и Redis необязательны на MVP.

---

# 79. MVP

## User Service

Сделать:

```text
User
FriendGroup
GroupMember
```

Минимальные операции:

```text
создать профиль

создать группу

добавить участника

получить группу
```

## Activity Service

Сделать:

```text
Activity
ActivityParticipant
Suggestion
Poll
Checklist
```

Минимальные операции:

```text
создать Activity

добавить участников

accept/decline

создать suggestion

проголосовать

finish Activity
```

## Expense Service

Сделать:

```text
Expense
ExpenseShare
Balance
Settlement
```

Минимальные операции:

```text
добавить Expense

EQUAL split

посчитать balances

получить settlements
```

---

# 80. Что не нужно на MVP

Пока не нужны:

```text
Redis

Kubernetes

OpenTelemetry

Saga

DLQ

сложные recommendations

WebSocket

сложный RBAC

полная оптимизация settlements
```

---

# 81. Roadmap

## Этап 1

Три независимых REST-сервиса:

```text
Spring Boot
Kotlin
PostgreSQL
Flyway
Validation
Tests
```

## Этап 2

Добавить:

```text
Keycloak
JWT
Spring Security
```

## Этап 3

Добавить:

```text
Kafka
Domain Events
Transactional Outbox
Idempotent Consumers
```

## Этап 4

Добавить:

```text
Retry
DLQ
Timeouts
Optimistic Locking
Resilience
```

## Этап 5

Добавить при необходимости:

```text
Redis
```

## Этап 6

Observability:

```text
Actuator
Micrometer
Prometheus
Grafana
OpenTelemetry
```

## Этап 7

Deployment:

```text
Docker Compose
→ Kubernetes
```

## Этап 8

CI/CD:

```text
compile
test
integration test
build image
scan
push
deploy
```

---

# 82. Полный пользовательский flow

```text
Пользователь входит
        │
        ▼
    User Service
        │
        ▼
   создаёт группу
        │
        ▼
 добавляет друзей
        │
        ▼
  Activity Service
        │
        ▼
 создаёт Activity
        │
        ├───────────────┐
        │               │
        ▼               ▼
 добавляет          участники
 участников         предлагают варианты
        │               │
        └──────┬────────┘
               ▼
            голосуют
               │
               ▼
        Activity начинается
               │
               ▼
        Expense Service
               │
         ┌─────┼──────┐
         ▼     ▼      ▼
       еда   такси   билеты
               │
               ▼
       ActivityFinished
               │
               ▼
          balances
               │
               ▼
         settlements
```

---

# 83. Пример Kafka flow

```text
User Service

GroupCreated
     │
     ▼
Kafka
```

```text
Activity Service

ActivityCreated
ActivityParticipantAdded
ActivityFinished
     │
     ▼
Kafka
```

```text
Expense Service

ExpenseCreated
SettlementCreated
SettlementPaid
     │
     ▼
Kafka
```

---

# 84. Сценарий: настолки

Группа:

```text
Настольщики
```

Activity:

```text
Настолки в субботу
```

Description:

```text
Хочу Nemesis, но можно The Gang
```

Participants:

```text
Алекс   ACCEPTED
Дима    ACCEPTED
Катя    ACCEPTED
Миша    MAYBE
```

Suggestions:

```text
Nemesis
The Gang
Dune Imperium
```

Poll:

```text
Во сколько?

18:00
19:00
20:00
```

Checklist:

```text
Алекс — заказать еду
Дима — принести Nemesis
Катя — купить напитки
```

Expenses:

```text
Пицца 3600
Такси 1800
Напитки 1500
```

После Activity:

```text
balances
settlements
```

---

# 85. Сценарий: поездка

Activity:

```text
Краков на выходные
```

Type:

```text
TRIP
```

Description:

```text
Хотим выехать утром в субботу.
Нужно выбрать жильё.
```

Suggestions:

```text
Отель A
Апартаменты B
Hostel C
```

Poll:

```text
Когда выезжаем?
```

Checklist:

```text
забронировать жильё
купить билеты
составить маршрут
```

Expenses:

```text
отель
билеты
такси
еда
```

Архитектура остаётся той же.

---

# 86. Сценарий: кино

Activity:

```text
Идём в кино
```

Suggestions:

```text
Dune
Alien
Tron
```

Poll:

```text
18:00
20:30
22:00
```

Expense Service:

```text
билеты
такси
еда
```

---

# 87. Почему Event Service больше не нужен

Раньше модель была:

```text
Event
  ↓
Activity
```

Но в пользовательском сценарии эти сущности практически совпадали.

Появлялись искусственные вопросы:

```text
где заканчивается Event?

где начинается Activity?

кто хранит участников?

кто хранит дату?

кто хранит голосования?
```

После объединения:

```text
Activity
```

имеет:

```text
что делаем
когда
где
с кем
```

Это проще и для пользователя, и для кода.

---

# 88. Почему Activity Service не должен хранить группы

Группа существует долго:

```text
месяцы
годы
```

Activity:

```text
имеет конкретный жизненный цикл
```

Поэтому:

```text
FriendGroup
```

остаётся в User Service.

Activity только хранит:

```text
groupId
```

---

# 89. Почему Expense Service остаётся отдельным

Расчёт расходов имеет самостоятельную бизнес-логику:

```text
split
balances
settlements
payments
```

Его удобно развивать независимо.

Например позже:

```text
multi-currency
exchange rates
partial payments
debt history
```

Это не должно усложнять Activity Service.

---

# 90. Финальная модель

```text
USER DOMAIN
│
├── User
├── Friendship
├── FriendGroup
└── GroupMember
        │
        │ groupId
        ▼

ACTIVITY DOMAIN
│
├── Activity
├── ActivityParticipant
├── ActivitySuggestion
├── SuggestionVote
├── Poll
├── PollOption
├── PollVote
└── ChecklistItem
        │
        │ activityId
        ▼

EXPENSE DOMAIN
│
├── Expense
├── ExpenseShare
├── Balance
└── Settlement
```

---

# 91. Итоговая ответственность сервисов

## User Service

```text
кто?
с кем?
в какой группе?
с какой ролью?
```

## Activity Service

```text
что делаем?
когда?
где?
кто участвует?
что предлагает?
за что голосуем?
что нужно сделать?
в каком состоянии Activity?
```

## Expense Service

```text
кто заплатил?
за кого?
сколько?
какой баланс?
кто кому должен?
перевод уже выполнен?
```

---

# 92. Главный архитектурный принцип Friend Hub

Если возникает вопрос:

> В какой сервис должна попасть новая функция?

Нужно спросить:

```text
это про пользователя / отношения / группу?
→ User Service

это про совместное событие / встречу / активность?
→ Activity Service

это про деньги?
→ Expense Service
```

Эта трёхсервисная модель является основной архитектурой Friend Hub.
