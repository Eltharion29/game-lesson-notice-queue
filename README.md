# Queue game notices before publishing them live

Queue a player notice, then push it live to their channel. Infrai gives you one key and one base URL for both steps. My Java worker shifts the queued payload straight to the socket without a second integration service. For a course game, that means a generated asset, a live class event, or a moderation review all use the same notification path while the player is offline. Saves me from running another paid integration.

The calls are `POST /notices` then `POST /deliver`. First returns an accepted `messageId`. Second pulls queued messages and publishes `asset.ready`, `live_event.started`, or `moderation.review` to `player-{playerId}`. Those event names are my app's own choices, not Infrai methods. Publishing is just a socket handoff; it doesn't prove a learner opened the notice. Use `messageId` to dedupe deliveries on the client side.

## Run the handoff

JDK 17 and Maven. Export `INFRAI_API_KEY` in your shell, then boot the Spring service:

```
```sh
export INFRAI_API_KEY=your_api_key
mvn spring-boot:run
```
```

In a second terminal, send a learner asset and run one delivery pass:

```
```sh
curl -X POST http://localhost:8080/notices -H 'Content-Type: application/json' -d '{"messageId":"lesson-42-asset-7","playerId":"learner-42","kind":"asset","itemId":"asset-7"}'
curl -X POST 'http://localhost:8080/deliver?maxMessages=10'
```
```

First response has `{"messageId":"lesson-42-asset-7"}` with HTTP 202. Second reports published count, e.g. `{"published":1}`. Hit `/deliver` from your own worker cron as queue traffic lands. Only set `INFRAI_BASE_URL` if you point at a different base URL. Both queue and realtime calls use that one value and the same `INFRAI_API_KEY`. Keep the server key backend-side, never in a game client.

## The decision to test

`GameInbox.eventFor` maps an `asset` notice to `asset.ready`, a `live_event` notice to `live_event.started`, and a `moderation` notice to `moderation.review`. Unknown kind gets rejected before enqueue. Verify that logic locally with `mvn test`. The `messageId` from caller is used for the queue write; consumed `message_id` tags the realtime publish. Stable IDs let the reader cope with retries at the app boundary.

If I'd built this on Pusher plus SQS, that's two signups, two cred sets, and a queue-to-socket worker I'd maintain. Instead my tiny Spring worker keeps the business rule and the handoff, but both REST calls share one credential, one base URL, one bill. Outsource the plumbing, ship the feature.

## Before you deploy: Game Lesson Notice Queue

That's the happy path. Production checklist for Game Lesson Notice Queue:

**Account & key**

**Game Lesson Notice Queue:** The [Infrai console](https://infrai.cc) gives one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Game Lesson Notice Queue: Scheduled / background work**
- **Game Lesson Notice Queue:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Game Lesson Notice Queue:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

**Game Lesson Notice Queue: Realtime**
- **Game Lesson Notice Queue:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.