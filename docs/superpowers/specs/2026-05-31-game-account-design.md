# Game Account Service Migration Design

## 1. Overview

This design migrates the demo `game-account-service` into the current project as a first-class microservice and aligns it with the project's current architecture, message contracts, storage conventions, and frontend structure.

The target service will keep the demo's full business scope:

- Game account binding and unbinding
- System resource management for characters, skins, items, and sign-in rewards
- User-owned game assets
- Daily sign-in and reward claiming
- Resource delivery after shop order payment
- User-facing pages and admin-facing management pages in the existing frontend

The migration will follow these storage rules:

- MySQL stores all business metadata, relationship data, state data, and transactional records
- MongoDB stores only rich detail documents such as lore, long descriptions, media lists, and usage details

## 2. Confirmed Product Decisions

- One community user can bind only one game account at a time
- Rebinding is allowed
- Binding history is not retained
- The demo feature set is migrated in full
- System resource management pages are added into the existing frontend as admin pages

## 3. Goals

- Preserve all useful demo capabilities while removing demo-level shortcuts
- Use MySQL as the source of truth for metadata and state
- Use MongoDB only for detail content that is large or presentation-oriented
- Align payment delivery with the current `shop-service` Kafka message contract
- Make binding, delivery, sign-in, and inventory updates concurrency-safe and idempotent
- Integrate with the existing gateway, auth, Nacos, Kafka, and frontend conventions already used in this project

## 4. Non-Goals

- No binding history table
- No separate admin frontend project
- No MongoDB-based metadata storage
- No cross-service rewrite of shop or user domains beyond the changes required for integration
- No general resource model that collapses characters, skins, and items into one shared table for this iteration

## 5. Recommended Architecture

The selected approach is a mixed optimized migration:

- Resource definitions remain split by domain type for clarity and management convenience
- Account-owned resources remain split where ownership semantics differ
- MySQL is the authoritative store for resource metadata and user state
- MongoDB stores only details for characters, skins, and items
- `shop-service` publishes paid-order messages and `game-account-service` performs delivery

This balances migration risk, future maintainability, and compatibility with the current shop implementation.

## 6. Service Responsibilities

`game-account-service` will own four responsibility groups.

### 6.1 Binding

- Bind a community user to a game account
- Rebind to a different game account
- Unbind the current game account
- Expose the current bound game account and summary

### 6.2 System Resources

- Manage character metadata
- Manage skin metadata
- Manage item metadata
- Manage sign-in reward rules
- Manage detail documents in MongoDB

### 6.3 Account Assets and Sign-In

- Query owned characters
- Query owned skins
- Query owned items
- Query system catalog and ownership status
- Perform daily sign-in
- Query sign-in status and reward configuration

### 6.4 Shop Delivery

- Consume paid-order messages from Kafka
- Resolve the currently bound game account
- Deliver characters, skins, or items into account assets
- Record delivery result and support idempotent re-consumption

## 7. Data Model

## 7.1 MySQL Tables

### `t_game_account`

Stores core game account metadata.

Suggested fields:

- `id`
- `account_no`
- `name`
- `level`
- `gold`
- `diamond`
- `current_season_rank`
- `history_season_rank`
- `status`
- `version`
- `create_time`
- `update_time`

Notes:

- `version` supports optimistic locking for currency-like updates and other mutable account state
- `account_no` is the public-facing game account identifier if a distinct display ID is needed

### `t_user_game_bind`

Stores the active one-to-one binding between a community user and a game account.

Suggested fields:

- `id`
- `user_id`
- `game_account_id`
- `create_time`
- `update_time`

Constraints:

- Unique index on `user_id`
- Unique index on `game_account_id`

Notes:

- This table is the source of truth for binding
- If the user domain keeps a `gameAccount` display field, that field is treated as a synchronized cache, not the binding source of truth

### `t_game_character`

Character metadata.

Suggested fields:

- `id`
- `character_code`
- `name`
- `title`
- `rarity`
- `hero_class`
- `icon_url`
- `cover_url`
- `status`
- `sort_order`
- `create_time`
- `update_time`

### `t_game_skin`

Skin metadata.

Suggested fields:

- `id`
- `skin_code`
- `character_code`
- `name`
- `rarity`
- `icon_url`
- `cover_url`
- `status`
- `sort_order`
- `create_time`
- `update_time`

### `t_game_item`

Item metadata.

Suggested fields:

- `id`
- `item_code`
- `name`
- `item_type`
- `rarity`
- `icon_url`
- `cover_url`
- `status`
- `sort_order`
- `stack_limit`
- `create_time`
- `update_time`

### `t_sign_in_reward`

Reward configuration for sign-in.

Suggested fields:

- `id`
- `day_index`
- `reward_type`
- `business_code`
- `quantity`
- `reward_name`
- `status`
- `create_time`
- `update_time`

Notes:

- `reward_type` maps to character, skin, item, gold, diamond, or future types
- `business_code` points to the resource code when the reward is resource-based

### `t_account_character`

Owned character relation.

Suggested fields:

- `id`
- `game_account_id`
- `character_code`
- `acquired_type`
- `source_order_no`
- `create_time`
- `update_time`

Constraints:

- Unique index on `(game_account_id, character_code)`

### `t_account_skin`

Owned skin relation.

Suggested fields:

- `id`
- `game_account_id`
- `skin_code`
- `acquired_type`
- `source_order_no`
- `create_time`
- `update_time`

Constraints:

- Unique index on `(game_account_id, skin_code)`

### `t_account_item`

Owned stackable items.

Suggested fields:

- `id`
- `game_account_id`
- `item_code`
- `quantity`
- `acquired_type`
- `last_source_order_no`
- `version`
- `create_time`
- `update_time`

Constraints:

- Unique index on `(game_account_id, item_code)`

Notes:

- `quantity` is incremented atomically
- `version` can support optimistic updates where needed

### `t_sign_in_record`

Monthly sign-in state per account.

Suggested fields:

- `id`
- `game_account_id`
- `year_month`
- `sign_bits`
- `sign_count`
- `consecutive_days`
- `last_sign_in_date`
- `create_time`
- `update_time`

Constraints:

- Unique index on `(game_account_id, year_month)`

Notes:

- `sign_bits` can still be retained for compactness
- Additional explicit counters make the record more readable and easier to query

### `t_game_delivery_record`

Idempotent delivery transaction table for paid shop orders.

Suggested fields:

- `id`
- `order_no`
- `user_id`
- `game_account_id`
- `product_type`
- `business_id`
- `quantity`
- `status`
- `fail_reason`
- `create_time`
- `update_time`

Constraints:

- Unique index on `order_no`

Notes:

- Prevents duplicate delivery on Kafka redelivery or consumer restart
- Makes failed deliveries visible and repairable

## 7.2 MongoDB Collections

### `character_detail`

- `characterCode`
- `story`
- `skills`
- `gallery`
- `voiceLines`
- `extendedMeta`

### `skin_detail`

- `skinCode`
- `story`
- `features`
- `gallery`
- `effects`
- `extendedMeta`

### `item_detail`

- `itemCode`
- `description`
- `usageGuide`
- `gallery`
- `extendedMeta`

Notes:

- Mongo content is presentation-oriented only
- Business decisions must not depend solely on MongoDB availability

## 8. Integration with Existing Services

## 8.1 user-service

The user domain may continue exposing `gameAccount` as a user-facing field if needed, but the actual binding authority is `t_user_game_bind`.

When binding changes:

- `game-account-service` updates its own binding table in a transaction
- If user profile caching needs synchronization, expose a service-level update path instead of relying on the user table as the source of truth

## 8.2 shop-service

`shop-service` remains the ordering and payment owner.

After payment success, it publishes the current project message:

- Topic: `SHOP_ORDER_PAID_TOPIC`
- Message: `ShopOrderPaidMessage`
- Key fields: `orderNo`, `userId`, `productType`, `businessId`, `quantity`

`game-account-service` consumes this message and performs idempotent delivery.

## 8.3 gateway

`game-account-service` is added to gateway route configuration.

Public pages are minimized. Most user endpoints require JWT login and active session validation through the current gateway and auth flow.

## 9. Core Execution Flows

## 9.1 Bind

1. User submits game account identifier
2. Service validates target account existence and status
3. Service starts transaction
4. Service inserts or updates `t_user_game_bind`
5. Unique constraints on `user_id` and `game_account_id` enforce one-to-one binding
6. If duplicate-key conflict occurs, the service translates it into a business error

## 9.2 Rebind

1. User requests rebind to another game account
2. Service validates target account
3. Service updates the existing row in `t_user_game_bind`
4. Any synchronized user-profile field is updated after or within the transaction depending on existing project conventions

No history record is retained.

## 9.3 Unbind

1. User requests unbind
2. Service deletes the active row in `t_user_game_bind`
3. Any synchronized cache field is cleared if needed

## 9.4 Daily Sign-In

1. Resolve bound game account
2. Load or create the monthly row in `t_sign_in_record`
3. Lock by account and month inside the transaction
4. Check whether today is already signed
5. If not signed, update `sign_bits`, `sign_count`, `consecutive_days`, and `last_sign_in_date`
6. Deliver the configured reward inside the same local transaction
7. Return updated sign-in state and reward summary

## 9.5 Shop Delivery

1. Kafka consumer receives paid-order message
2. Service attempts to insert `t_game_delivery_record`
3. If insert conflicts on `order_no`, treat as already processed and return success
4. Resolve user's currently bound game account
5. If no bound account exists, mark delivery record as failed with explicit reason
6. Deliver by resource type
7. Mark delivery record as success

## 10. Concurrency and Idempotency

## 10.1 Binding Safety

Use database uniqueness, not application-layer check-then-insert logic, as the final concurrency guarantee.

Required constraints:

- Unique `user_id`
- Unique `game_account_id`

This prevents two users binding the same game account and prevents one user binding multiple accounts at once.

## 10.2 Delivery Idempotency

`t_game_delivery_record.order_no` is the idempotency key.

Behavior:

- First successful insert means this order is being processed for the first time
- Duplicate insert means this order has already been handled

This prevents duplicate rewards under retries, Kafka rebalance, consumer restarts, or network redelivery.

## 10.3 Owned Character and Skin Safety

Characters and skins use unique ownership tables.

If the same asset is delivered twice:

- Duplicate ownership insert is either treated as idempotent success or transformed into a clear business rule based on product policy

Recommended behavior:

- Treat duplicate delivery as idempotent success if the order is the duplicate
- Reject duplicate acquisition only on direct business actions that are not retry scenarios

## 10.4 Stackable Item Safety

Items use atomic quantity accumulation on `(game_account_id, item_code)`.

Recommended pattern:

- Insert on first acquire
- On existing row, execute in-place quantity increment

This avoids lost updates under concurrent delivery or sign-in reward claims.

## 10.5 Sign-In Safety

Sign-in runs in a transaction scoped to the current account and month record.

Duplicate clicks in the same day must not issue the reward twice.

The transaction checks today's sign bit before reward issuance.

## 10.6 Deletion Safety in Admin Pages

Resources that have already been acquired or referenced by shop items should not be hard-deleted.

Recommended behavior:

- Disable via `status`
- Preserve historical compatibility with owned assets and past orders

## 11. API Design

## 11.1 Binding APIs

- `POST /game-account/bind`
- `PUT /game-account/rebind`
- `DELETE /game-account/unbind`
- `GET /game-account/me`

## 11.2 User Asset APIs

- `GET /game-account/assets/characters`
- `GET /game-account/assets/skins`
- `GET /game-account/assets/items`
- `POST /game-account/sign-in`
- `GET /game-account/sign-in/status`
- `GET /game-account/sign-in/rewards`

## 11.3 Catalog APIs

- `GET /game-account/resources/characters`
- `GET /game-account/resources/skins`
- `GET /game-account/resources/items`
- `GET /game-account/resources/character/{code}`
- `GET /game-account/resources/skin/{code}`
- `GET /game-account/resources/item/{code}`

Catalog list responses should support ownership markers so the frontend can render "owned" and "not owned" states without client-side full joins.

## 11.4 Admin APIs

- `POST /game-account/admin/characters`
- `PUT /game-account/admin/characters`
- `POST /game-account/admin/skins`
- `PUT /game-account/admin/skins`
- `POST /game-account/admin/items`
- `PUT /game-account/admin/items`
- `POST /game-account/admin/sign-in-rewards`
- `PUT /game-account/admin/sign-in-rewards`
- `POST /game-account/admin/character-detail`
- `POST /game-account/admin/skin-detail`
- `POST /game-account/admin/item-detail`

List and delete or disable endpoints are also required for each admin-managed resource.

## 12. Frontend Design

## 12.1 User Pages

### Profile Integration

The existing profile page adds a game-account card:

- Current binding summary
- Bind button when unbound
- Rebind button when bound
- Unbind button

### Game Asset Module

Add a game account module with:

- My Characters
- My Skins
- My Items
- Sign-In Center

The page style follows the current project visual language and card-based layout.

### Resource Catalog

Add browsable catalog pages for:

- Character gallery
- Skin gallery
- Item gallery

Detail pages render MongoDB detail content plus MySQL summary metadata.

## 12.2 Admin Pages

Add an admin module inside the same frontend, similar to the current audit area.

Suggested route:

- `/app/admin/game-resources`

Suggested tabs:

- Character Management
- Skin Management
- Item Management
- Sign-In Reward Management

Each tab supports:

- Pagination
- Search
- Status filtering
- Create
- Edit
- Disable
- Detail editing where applicable

## 12.3 Shop Integration in Frontend

For game-asset products:

- Shop item detail shows which bound game account will receive the asset
- If no game account is bound, the buy action is blocked before order submission
- Payment success page can show the target game account and delivery state

## 13. Error Handling

The service must return clear business errors for:

- Game account not found
- Game account disabled
- Game account already bound by another user
- Current user has no bound game account
- User already signed in today
- Resource not found
- Resource disabled
- Order delivery failed because no game account is bound

Failed Kafka deliveries must be persisted in MySQL and surfaced to logs and later repair tooling rather than silently dropped.

## 14. Observability

Recommended logging and operational visibility:

- Binding success and conflict logs
- Sign-in success and duplicate-attempt logs
- Delivery success logs by `order_no`
- Delivery failure logs with explicit reason

The delivery table itself acts as a business audit source.

## 15. Testing Strategy

## 15.1 Backend

- Binding tests for first bind, rebind, unbind, duplicate bind, and concurrent bind conflict
- Asset delivery tests for character, skin, and item paths
- Kafka message idempotency tests using repeated same-order input
- Sign-in tests for first sign-in, duplicate same-day sign-in, cross-day consecutive sign-in, and reward issuance
- Admin resource tests for create, update, disable, and detail document persistence

## 15.2 Frontend

- Profile binding and rebinding flow
- Shop purchase guard when no game account is bound
- User asset list rendering
- Sign-in center state transitions
- Admin resource list, edit form, and detail edit flow

## 16. Rollout Notes

- Create the MySQL schema before enabling the service in gateway routes
- Seed at least a small initial resource set for characters, skins, items, and sign-in rewards
- Ensure Kafka topic and consumer group configuration match current project conventions
- Align `shop-service` product type and `businessId` mapping with the new game resource tables

## 17. Open Implementation Notes

- Prefer keeping public display ID and database primary key separate if gameplay later needs shareable account numbers
- If `user-service` still stores `gameAccount`, treat it as a synchronized display/cache field only
- If admin deletion is requested for already referenced resources, convert the action into disable rather than physical delete

## 18. Final Recommendation

Implement the migration with split MySQL metadata tables, MongoDB detail collections, idempotent Kafka-based delivery, transaction-protected sign-in, and a unified frontend that contains both player-facing pages and admin resource management pages.

This approach preserves the demo feature set while matching the current project's architecture, operational model, and concurrency requirements.
