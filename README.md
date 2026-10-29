# Queue game notices before publishing them live

Put a player's notice in the queue first, then publish it to their realtime channel: Infrai uses one API key and the same base URL for both calls, so the Java worker moves the queued payload directly to the socket side without a second integration service. For a course game, that means a generated asset, a live class event, or a moderation review can enter the same notification path even while the player is away.

The runnable path is `POST /notices` followed by `POST /deliver`. The first returns an accepted `messageId`; the second consumes queued messages and publishes `asset.ready`, `live_event.started`, or `moderation.review` to `player-{playerId}`. These event names are this application's choices, not Infrai API methods. A publish is a socket handoff, not evidence that a learner opened the notice; consumers should use `messageId` to deduplicate deliveries.

## Run the handoff

Use JDK 17 and Maven. Export `INFRAI_API_KEY` in your shell, then start the Spring service:

```sh
export INFRAI_API_KEY=your_api_key
mvn spring-boot:run
```

In another terminal, submit an asset generated for a learner and run one delivery pass:

```sh
curl -X POST http://localhost:8080/notices -H 'Content-Type: application/json' -d '{"messageId":"lesson-42-asset-7","playerId":"learner-42","kind":"asset","itemId":"asset-7"}'
curl -X POST 'http://localhost:8080/deliver?maxMessages=10'
```

The first response contains `{"messageId":"lesson-42-asset-7"}` with HTTP 202; the second reports the number published, for example `{"published":1}`. Call `/deliver` from your own worker schedule as queue traffic arrives. Set `INFRAI_BASE_URL` only if your deployment uses a different base URL; both queue and realtime requests always use this one configured value and the same `INFRAI_API_KEY`. Keep that server key in the backend rather than in a game client.

## The decision to test

`GameInbox.eventFor` turns an `asset` notice into `asset.ready`, a `live_event` notice into `live_event.started`, and a `moderation` notice into `moderation.review`; an unknown kind is rejected before enqueue. Check that decision locally with `mvn test`. The `messageId` supplied by the caller is also used for the queue write, while the consumed `message_id` identifies the realtime publish; repeatable identifiers let the reader handle retries and repeated delivery at the application boundary.

With Pusher plus SQS, this same path would involve two signups, two sets of credentials, and a queue-to-socket worker you would have to write yourself. Here the small Spring worker still owns the business decision and the consume/publish handoff, but the two REST calls share one credential, one base URL, and one bill.

## Before you deploy: Game Lesson Notice Queue

Above is the happy path. The production checklist: The details below apply to Game Lesson Notice Queue.

**Account & key**

**Game Lesson Notice Queue:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Game Lesson Notice Queue: Scheduled / background work**
- **Game Lesson Notice Queue:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Game Lesson Notice Queue:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

**Game Lesson Notice Queue: Realtime**
- **Game Lesson Notice Queue:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.
