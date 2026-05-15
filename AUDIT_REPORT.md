# AUDIT_REPORT.md

> Static audit of the `oop-course-project-uet` online auction system. No production / test / build code was modified. All findings have a corresponding task in `FIX_TASKS.md`.
> Gradle verification (`./gradlew clean test check jacocoTestReport`) was **not run** by the auditor: the audit environment has no JDK 21, no network access to Maven Central, and no embedded PostgreSQL binary. All findings are derived from code inspection. Every fix task lists the Gradle command the implementer must run.

## 1. Final Verdict

**Would this pass a strict Big Tech review? No.**

The architecture is clean for a first-year project: layered DAO/service/controller, Strategy + State + Observer patterns wired sensibly, BCrypt + JWT auth, Flyway migrations, `SELECT ... FOR UPDATE` used in the bid path. The codebase is readable, well-commented, and consistent.

But several findings violate the project's own explicit business rules and would cause concrete bugs in production-style use:

- The bid endpoint **lets SELLERs bid on other sellers' auctions** even though the spec says only BIDDERs bid (`BidController.java:46-48`). README also disagrees with the code.
- **Single-product, single-active-auction is not enforced anywhere.** The same item can be listed in multiple OPEN/RUNNING auctions concurrently. There is no `items.status` column.
- The **autobid creation endpoint** (`POST /api/auctions/{id}/auto-bid`, defined inline in `App.java:345-404`) violates ~6 explicit spec rules: it doesn't place an initial bid, doesn't freeze balance, lets the current leader create an autobid, lets a user update an existing ACTIVE config, checks balance against `maxBid` not the accepted bid amount, and skips the "no ACTIVE config already exists" rule.
- **Autobid chains are not atomic.** Each chained bid opens its own transaction; mid-chain failures leave partial state committed and are silently logged (no FAILED/EXHAUSTED notification).
- **Settlement uses stale in-memory state.** `AuctionScheduler` loads the auction outside the transaction, then settles based on that snapshot. A bid that commits between load and SETTLING-claim makes the scheduler settle to the wrong winner at the wrong price.
- **Money-correctness bug in fallback path:** `UPDATE users SET reserved_balance = 0 WHERE id = winnerId` wipes the winner's reservations across **all** their other live auctions, not just this one.
- **Hard-delete of auctions destroys bid history** and does not release reserved balance on the leading bidder.
- **Hardcoded JWT secret fallback** committed to the repo: anyone can forge ADMIN tokens.
- The **notification read/unread persistence bug** the audit prompt called out is real and is in `NotificationStore.markAllRead()`: it writes to a single global JVM preference key and never calls the server. After logout/login on the same machine, everything appears unread again — and one user's read count affects another's on a shared machine.
- The "strict CI" story is overstated. CI never runs `check`, so **SpotBugs (configured at effort=MAX) never actually executes**. There is no `jacocoTestCoverageVerification` task — coverage thresholds are unenforced. CI auto-runs `spotlessApply` and pushes to `main` with `permissions: contents: write`, bypassing review.

The code is much closer to a strong undergraduate submission than to a production-grade auction system. Fixing the High-severity items below is achievable in a focused pass without large refactors.

## 2. Scores (out of 10)

- **Overall: 5.2**
- Correctness (business logic): **4** — single-active-auction, autobid creation, SETTLING accepting bids, role on bid endpoint
- Auction business logic: **5** — lifecycle is mostly right; hard-delete + admin cancel destroy or skip balance steps
- Money / wallet / frozen balance safety: **5** — reservation exists, SELECT FOR UPDATE used, but `GREATEST(... ,0)` hides accounting bugs and one fallback wipes all reservations
- Concurrency / transaction safety: **5** — bid path is solid; scheduler settlement races with bids
- Autobid correctness: **3** — creation endpoint violates 6 rules; chain is not atomic; no FAILED notification; cap is 10 not 100
- Auth / security / authorization: **5** — JWT enforced at middleware, but secret fallback is committed; SELLER can bid; admin user-delete is broken-by-FK
- DB / JDBI / schema: **6** — no SQL injection seen, FKs sane (mostly), but `DECIMAL(15,2)` for money, no `items.status`, no wallet ledger, no `IF NOT EXISTS` consistency
- API design / error handling: **6** — DTOs and ErrorResponse exist, but inline JDBI in App.java for notifications, BigDecimal money in DTOs
- WebSocket / realtime: **6** — auth via query-string JWT works, IDOR blocked on `/ws/user/{id}`, but token expiration not re-checked
- JavaFX client quality: **6** — clean MVC, REST + WS clients exist; notification state lives in `Preferences` instead of the server
- Notification consistency: **4** — `markAllRead` never hits the server; cancel-notification insert outside cancellation transaction
- Test quality: **4** — 130+ tests but only 1 concurrency test (same-price ties), no authorization/role tests, no chain race tests, no overcommit tests
- CI / static analysis: **4** — `check` not run, SpotBugs unused, no JaCoCo enforcement, CI auto-commits to `main`
- Documentation / fresh clone readiness: **7** — README is large and largely correct, but disagrees with code on CI steps and on the BID role policy
- Repo hygiene: **8** — `.gitignore` is thorough; `.env` not committed; gradle wrapper tracked; some dead methods exist

## 3. Executive Summary

**Main strengths**

- `BidService.placeBid` is the strongest part of the codebase: `SELECT ... FOR UPDATE` on auction and bidder, release-old + freeze-new in one transaction, outbid notification inserted in the same transaction, WebSocket emitted only after commit, anti-snipe handled inside the lock.
- `RunningState` correctly rejects `amount <= currentPrice`, seller self-bidding, and leader self-bidding.
- `approveDeposit`, `rejectDeposit`, `approveReset`, `rejectReset` all use `findByIdForUpdate` + status-transition guard in a transaction.
- Forgot-password generates a random 12-char temp password (better than the "reset to 123456" simplification the prompt anticipated).
- Registration explicitly blocks `role=ADMIN` defensively (`UserService.java:90-92`).
- IDOR blocked on `/ws/user/{id}` by comparing token `userId` against path `id`.
- Patterns are real, not decorative-only, except `BidStrategy` interface (see findings).

**Main risks**

- Money safety in the settlement and admin-action paths.
- Autobid creation flow is essentially a separate, simpler implementation that contradicts the rest of the autobid pipeline.
- Authorization on the bid endpoint and absence of single-active-auction make it possible to manipulate market state legitimately via the API.

**Most dangerous areas (top 5, in priority order)**

1. `App.java` autobid endpoints (`/api/auctions/{id}/auto-bid` POST/DELETE) — direct DAO access, no spec compliance.
2. `AuctionScheduler.settleAndClose` — stale-snapshot race with live bids; `reserved_balance = 0` blast-radius.
3. `AuctionDao.hardDelete` and the admin endpoint that calls it.
4. `BidController` + `AuctionController` role policy and missing single-active-auction in `AuctionService.create`.
5. Client `NotificationStore` + server-side `mark-all-read` never being called from the client.

**What must be fixed first**

The Phase-1 task order in `FIX_TASKS.md` is: secret fallback → bid role → single-active-auction → settlement race → reserved_balance fallback → autobid creation rewrite → autobid chain atomicity → SETTLING bid rejection → notification persistence → hard-delete safety. After that, CI / SpotBugs / JaCoCo enforcement.

## 4. Project Facts Found

Discovered by inspection of the merged source dump (extracted to a tree before audit). The file set is the same one listed in the merged `<code_index>`.

**Build / runtime**

- Java 21 toolchain pinned in `build.gradle.kts` (`languageVersion = JavaLanguageVersion.of(21)`); `actions/setup-java@v4` with `'21'` in CI; README's "Tech stack" calls out Java 21. Consistent.
- Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Wrapper committed.
- Javalin 6.4.0, JDBI 3.45.4, HikariCP 6.2.1, PostgreSQL JDBC 42.7.4, embedded-postgres 2.0.7, Flyway 10.15.0, Jackson 2.18.2, JavaFX 21 via `org.openjfx.javafxplugin`.
- JWT via `com.auth0:java-jwt:4.4.0`. BCrypt via `at.favre.lib:bcrypt:0.10.2` (cost 12).
- JUnit 5.11.4 (BOM), Mockito 5.15.2.
- Plugins wired but **not all enforced**: `checkstyle` (10.21.1, `isIgnoreFailures = false`, runs in CI), `spotbugs` (effort MAX, reportLevel HIGH, `ignoreFailures = false`, **not invoked by CI**), `jacoco` (HTML+XML reports, **no verification task**), `spotless` (googleJavaFormat 1.25.2), `shadow` 8.1.1 (`shadowJar` for server + `shadowClient` for FX).
- Shutdown via local-only `/internal/shutdown` endpoint guarded by IP allowlist + random 32-byte token file under `data/server.token`. Reasonable for a local demo.

**Database**

- Embedded PostgreSQL at runtime, data under `data/postgres`; explicit PID file + shutdown hook in `DatabaseConfig.java`. CI uses a postgres:16 service container instead (different code path).
- Flyway migrations V1–V13 in `src/main/resources/db/migration/`. Initial schema: `users`, `items`, `auctions`, `bid_transactions`, `auto_bid_configs`. Later additions: `balance` (V3), `deposit_requests` (V4, ON DELETE CASCADE user), `password_reset_requests` (V5/V13), `notifications` (V6, ON DELETE CASCADE user), `increment_amount` (V7), `auctions.seller_id` (V8, added retroactively), `SETTLING` status (V9), legacy column repairs (V10/V11), `reserved_balance` (V12).
- **No `items.status` column anywhere.**
- Money columns are `DECIMAL(15,2)` throughout (`starting_price`, `current_price`, `bid_transactions.amount`, `auto_bid_configs.max_bid`, `auto_bid_configs.increment_amount`, `users.balance`, `users.reserved_balance`, `deposit_requests.amount`). Code uses `BigDecimal`. No app-layer scale enforcement; the spec wants integer VND.

**Auth / routes / WS**

- `JwtMiddleware` registered as `app.before("/api/*", JwtMiddleware::handle)`. Public: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/forgot-password`, `GET /api/health`. Semi-public (soft-parse): `GET /api/items*`, `GET /api/auctions*`. Everything else requires a Bearer token.
- HMAC-256 JWT, 24-hour expiration, claims `userId`, `username`, `role`.
- WebSocket: `/ws/auction/{id}` and `/ws/user/{id}`, JWT in `?token=` query param. Auction WS broadcasts `BID_UPDATE` / `TIME_EXTENDED` / `AUCTION_ENDED` / `BALANCE_UPDATED`. WS messages are output-only (no state-changing client→server WS messages).
- `requireAdmin(ctx)` static helper in `App.java` derives role from JWT context attribute, not body. Used on all `/api/admin/*` routes. `requireRole(ctx, "SELLER")` helpers in controllers do the same.

**Tests**

- Test classes present: `JwtUtilTest`, `DatabaseConfigTest`, `AuctionDaoTest`, `AutoBidConfigDaoTest`, `BidTransactionDaoTest`, `ItemDaoTest`, `UserDaoTest`, 5 exception tests, `ModelTest`, `AuctionServiceTest` (45 `@Test`), `BidServiceTest` (67 `@Test`), `BidServiceConcurrencyTest` (1 `@Test`), `UserServiceTest` (19 `@Test`), `SetupTest`. Total >130 test methods.
- The only concurrency test (`BidServiceConcurrencyTest.concurrentBids_onlyOneWins`) fires 10 threads bidding the **same** amount and asserts exactly one succeeds. It does not test the autobid chain, freeze/release race, settlement-vs-bid race, or overcommit prevention.

## 5. Findings by Severity

Locations cite the extracted file at original line numbers.

### [High] H-AUTH-01 — Hardcoded JWT secret fallback committed to the repo

- ID: H-AUTH-01
- Module: Auth / Config
- Location: `src/main/java/com/auction/config/JwtUtil.java:12-20`
  - Class `JwtUtil`, static initializer
  - `SECRET_KEY = (envSecret != null && !envSecret.isBlank()) ? envSecret : "auction-secret-key-dev";`
- Evidence: The fallback string `"auction-secret-key-dev"` is 22 ASCII bytes, hard-coded, and publicly visible to anyone who reads the source. The signing algorithm is HMAC-256.
- Problem: Anyone who clones the repo and knows the fallback is in use can forge tokens for any `userId` and `role`, including `ADMIN`. There is also no minimum-length validation if `JWT_SECRET` is set — a one-byte env var would be accepted.
- Why it matters: Token forgery breaks every authorization check in the system. Admin-only routes, ownership checks, and IDOR protection on `/ws/user/{id}` all depend on JWT integrity.
- Expected behavior: No default fallback. On startup, if `JWT_SECRET` is missing or shorter than 32 bytes, fail fast with a clear error message instead of using a hardcoded default. README must explain `JWT_SECRET` is required.
- Suggested fix: throw `IllegalStateException` when `JWT_SECRET` is null/blank or < 32 bytes; document in README and add `.env.example`.
- Related task: H-AUTH-01

### [High] H-AUTH-02 — `POST /api/auctions/{id}/bid` allows SELLER role

- ID: H-AUTH-02
- Module: Authorization / Bid
- Location: `src/main/java/com/auction/controller/BidController.java:42-58`
  - Method `register` lambda for `POST /api/auctions/{id}/bid`
  - `if (!"BIDDER".equals(role) && !"SELLER".equals(role)) { throw new UnauthorizedException(...); }`
- Evidence: The role check explicitly **includes** SELLER. The inline comment in the file even states this is intentional ("SELLER không được tự bid phiên của chính mình nhưng vẫn được bid phiên của người khác"). The README's structure section claims `POST /api/auctions/{id}/bid (manual, role: BIDDER)` — README and code disagree.
- Problem: Spec is unambiguous: "Only BIDDER role can bid. Seller cannot bid. Seller does not have bidding functionality." A SELLER account can manually bid on any auction other than their own.
- Why it matters: A seller can collude with another seller, log in as seller and bid on the other seller's auctions to drive prices up, etc. This is the textbook bid-manipulation attack the role separation exists to prevent.
- Expected behavior: Reject any non-BIDDER caller with 401/403. `RunningState`'s "seller != self" check is a second line of defense, not a replacement.
- Suggested fix: change the condition to `if (!"BIDDER".equals(role))`. Update README. Add a regression unit test.
- Related task: H-AUTH-02

### [High] H-AUCTION-01 — Single-product, single-active-auction is not enforced

- ID: H-AUCTION-01
- Module: Auction / Item
- Location: `src/main/java/com/auction/service/AuctionService.java:123-152` (`create`); item model and schema lack a `status` column entirely
- Evidence: `AuctionService.create` checks `req.getItemId()`, validates `startingPrice > 0` and `endTime > startTime`, verifies item ownership against `sellerId`, then inserts. It never consults `auctionDao.findByItemId(itemId)` (which exists at `AuctionDao.java:358-366`) to check whether the item is in another OPEN/RUNNING auction. `items` table has no `status` column.
- Problem: The same single item can be listed in N parallel OPEN/RUNNING auctions. Two of them could end and both award the "winner" — both bidders' balances would be debited, both `auction.status` would become PAID, but only one physical item exists.
- Why it matters: This is the most basic invariant in a one-item-per-listing auction model.
- Expected behavior: On `create`, reject if `auctionDao.findActiveByItemId(itemId)` returns any row with status in (OPEN, RUNNING, SETTLING). Additionally introduce `items.status` (AVAILABLE / IN_AUCTION / SOLD / REMOVED), flip to IN_AUCTION on auction insert and to SOLD on settle-to-PAID.
- Suggested fix: see H-AUCTION-01.
- Related task: H-AUCTION-01

### [High] H-AUCTION-02 — Settlement uses stale snapshot, races with live bids

- ID: H-AUCTION-02
- Module: Auction lifecycle / Concurrency
- Location:
  - `src/main/java/com/auction/service/AuctionScheduler.java:195-210` (`runningToFinished` loads outside transaction)
  - `src/main/java/com/auction/service/AuctionScheduler.java:216-341` (`settleAndClose`)
- Evidence: `runningToFinished` does `auctionDao.findById(id)` outside any transaction, then passes that `Auction` object to `settleAndClose`. Inside `settleAndClose`, the SETTLING claim runs `UPDATE auctions SET status='SETTLING' WHERE id=:id AND status='RUNNING'`. PostgreSQL READ COMMITTED will wait on any concurrent transaction holding the row, but once the claim succeeds, the in-memory `auction` object still holds whatever `currentPrice` and `leadingBidderId` were at the original `findById` — possibly stale by one or more bids. The code then uses `auction.getLeadingBidderId()` and `auction.getCurrentPrice()` to compute the payout.
- Problem: A bid that commits between the scheduler's `findById` and its SETTLING claim is invisible to the settlement. The scheduler will settle to the previous leader at the previous price; the most-recent bidder loses despite winning.
- Why it matters: Wrong winner, wrong price — High by definition.
- Expected behavior: After the SETTLING claim succeeds, re-fetch the row inside the transaction (with `SELECT ... FOR UPDATE` or the `auctionDao.findByIdForUpdate` helper). Also re-check `end_time` to honour anti-snipe extensions; spec demands the settlement transaction be the single source of truth.
- Suggested fix: see H-AUCTION-02.
- Related task: H-AUCTION-02

### [High] H-AUCTION-03 — Anti-snipe extension can be ignored by scheduler

- ID: H-AUCTION-03
- Module: Auction lifecycle
- Location: `src/main/java/com/auction/service/AuctionScheduler.java:195-210` and the SETTLING claim at lines 222-235 (`WHERE id=:id AND status='RUNNING'`)
- Evidence: `findExpiredAuctionIds("RUNNING", now)` returns IDs where `status='RUNNING' AND end_time <= now`. Between that query and `settleAndClose`, a bid in the last 30 seconds can call `auction.setEndTime(... + 60s)` and commit. The scheduler's SETTLING claim only checks status, not end_time.
- Problem: An auction extended by anti-snipe can be settled prematurely.
- Why it matters: Bidders rely on the extension; settling early invalidates the contract.
- Expected behavior: Add `AND end_time <= :now` to the SETTLING claim.
- Suggested fix: extend the WHERE clause.
- Related task: H-AUCTION-03

### [High] H-AUCTION-04 — SETTLING state still accepts bids

- ID: H-AUCTION-04
- Module: Auction state machine
- Location: `src/main/java/com/auction/service/AuctionService.java:328-336` (`getState`)
- Evidence: `case RUNNING, SETTLING -> AuctionStates.RUNNING;` — `RunningState.placeBid` accepts bids without distinguishing SETTLING. The whole point of V9's SETTLING state is to lock the auction during payout.
- Problem: A bid placed in the SETTLING window would lock the auction row (FOR UPDATE) after the SETTLING UPDATE has committed but before settlement finishes, and could either succeed (placing a bid on a settling auction) or get scrambled with the in-progress balance moves.
- Why it matters: Intermediate state without enforcement is a footgun.
- Expected behavior: A dedicated `SettlingState` that rejects all `placeBid` calls.
- Suggested fix: add `SettlingState`, route SETTLING to it in `AuctionService.getState`.
- Related task: H-AUCTION-04

### [High] H-WALLET-01 — Insufficient-balance fallback wipes reservations on other auctions

- ID: H-WALLET-01
- Module: Money / Wallet
- Location: `src/main/java/com/auction/service/AuctionScheduler.java:302-318`
- Evidence: When the winner's `balance < price` at settlement time, the code runs `UPDATE users SET reserved_balance = 0 WHERE id = winnerId` and demotes the auction to FINISHED.
- Problem: A bidder leading multiple auctions has `reserved_balance` = sum of their leading prices across all auctions. Setting it to 0 unreserves all of them at once, even though the other auctions are still active and still rely on those reservations. Other auctions will then settle with miscalculated availability.
- Why it matters: Cross-auction money-correctness violation.
- Expected behavior: Decrease `reserved_balance` by exactly `price` for this auction, asserting non-negative (`SET reserved_balance = reserved_balance - :price WHERE id=:userId AND reserved_balance >= :price`, raise if no row affected). Never blanket-zero.
- Suggested fix: see H-WALLET-01.
- Related task: H-WALLET-01

### [High] H-WALLET-02 — `releaseReservedBalanceInTransaction` uses GREATEST(...,0)

- ID: H-WALLET-02
- Module: Money / Wallet
- Location: `src/main/java/com/auction/dao/UserDao.java:368-384`
- Evidence: `UPDATE users SET reserved_balance = GREATEST(reserved_balance - :amount, 0) WHERE id=:userId`.
- Problem: If `amount > reserved_balance` (which should never happen — it's a bug if it does), the operation silently clamps to 0 and **hides** the underlying accounting error. The same pattern appears in `AuctionScheduler.settleAndClose` for the success branch (`reserved_balance = GREATEST(reserved_balance - :price, 0)`).
- Why it matters: Reservation accounting is a subtle area where silent clamping is exactly the wrong defensive coding choice. Detecting a discrepancy means a bug somewhere — the system should refuse to commit and log/raise, not paper over.
- Expected behavior: `SET reserved_balance = reserved_balance - :amount WHERE id = :userId AND reserved_balance >= :amount`; throw on 0 rows affected.
- Suggested fix: see H-WALLET-02. Apply to both DAO method and the scheduler's success branch.
- Related task: H-WALLET-02

### [High] H-AUTOBID-01 — Autobid creation endpoint violates ~6 spec rules

- ID: H-AUTOBID-01
- Module: Autobid
- Location: `src/main/java/com/auction/App.java:345-404` (`POST /api/auctions/{id}/auto-bid`)
- Evidence: This endpoint bypasses `BidService` entirely and goes straight to `AutoBidConfigDao` + `UserDao`. Compared to the spec:
  1. No immediate initial bid (spec: "If bidder is not leading, autobid immediately attempts to place: current_highest_price + step_amount").
  2. No balance freeze for the accepted bid (spec: "Only accepted bids freeze money. Do NOT freeze full max_price at autobid creation").
  3. Balance check is against `maxBid` (line 387), not the accepted bid amount.
  4. No "bidder is not current highest bidder" check (spec: "Highest bidder cannot create autobid").
  5. No "no ACTIVE autobid already exists" check — instead the code at line 391-398 **updates** the existing config (spec: "Do not allow direct update of ACTIVE autobid config. Stop old autobid; create new autobid.").
  6. No "step_amount integer VND" enforcement (BigDecimal accepted as-is).
- Problem: Whole autobid contract is broken on creation. A bidder can fire off autobids that "exist" but never act, can update max/increment on the fly, can set up an autobid while leading, etc.
- Why it matters: Autobid is one of the system's headline features; this endpoint guts it.
- Expected behavior: Move the logic into `BidService.createAutoBid(auctionId, bidderId, maxBid, step)` that:
  1. Locks the auction row, asserts RUNNING.
  2. Asserts no ACTIVE config exists for this (auction, bidder).
  3. Asserts the bidder is not the current leader.
  4. Computes `nextAmount = currentPrice + step`. If `nextAmount > maxBid`, persist config as EXHAUSTED, send a notification with `failure_reason = MAX_PRICE_TOO_LOW`, return.
  5. If `nextAmount <= maxBid`, check `availableBalance >= nextAmount`. If insufficient, persist FAILED with reason `INSUFFICIENT_BALANCE`, notify.
  6. Otherwise: persist ACTIVE, then call the chain logic to place that first bid and freeze the accepted amount, all in the same transaction.
- Suggested fix: see H-AUTOBID-01. This is the largest task.
- Related task: H-AUTOBID-01

### [High] H-AUTOBID-02 — Autobid chain is not atomic and swallows failures

- ID: H-AUTOBID-02
- Module: Autobid / Concurrency
- Location:
  - `src/main/java/com/auction/service/BidService.java:182-186, 223-233` (`triggerAutoBid` runs after the manual-bid commit, calls back into `placeBid`)
  - `src/main/java/com/auction/pattern/strategy/AutoBidStrategy.java:141-250` (`executeAll`)
- Evidence:
  1. `BidService.placeBid` commits, then calls `triggerAutoBid` outside the transaction. Each chained `executor.execute` calls back into `BidService.placeBid` which opens its **own** transaction. Steps 1..N-1 are persistently committed regardless of step N's outcome — the chain is not atomic.
  2. The `try/catch` at `AutoBidStrategy.java:213-218` and `:242-244` swallows exceptions with `LOG.warn`, returning normally. No FAILED/EXHAUSTED notification is sent to the user as the spec requires.
  3. `MAX_AUTO_BIDS_PER_TRIGGER = 10`; spec recommends 100 or a calculated safe bound. Reaching the cap only logs a warning — doesn't roll back or notify.
- Problem: Mid-chain failure leaves the order book inconsistent and silent. Chain length is unrealistically capped. Failed autobid bidders are never told their autobid failed.
- Why it matters: The chain is the autobid feature's correctness boundary. Partial commits + silent failures are how money systems lose money.
- Expected behavior: Wrap the entire chain (`triggerAutoBid` + chained bids) in a single `jdbi.inTransaction`. Define `MAX_AUTO_BIDS_PER_TRIGGER = 100` (or chain depth × 2, etc.). On chain failure, roll back the entire chain (including the originating manual bid? — debatable; conservative answer: roll back only the chain, but emit a clear server error). Persist FAILED/EXHAUSTED with explicit `failure_reason` and emit notifications after commit.
- Suggested fix: see H-AUTOBID-02.
- Related task: H-AUTOBID-02

### [High] H-AUTOBID-03 — Bidder with ACTIVE autobid can still place manual bid

- ID: H-AUTOBID-03
- Module: Autobid / Bid
- Location: `src/main/java/com/auction/service/BidService.java:89-192` and `src/main/java/com/auction/pattern/state/RunningState.java:78-99`
- Evidence: Neither `BidService.placeBid` nor `RunningState.placeBid` consults `autoBidConfigDao` to check whether the calling bidder already has an ACTIVE config on this auction.
- Problem: Spec: "If bidder has ACTIVE autobid in an auction, they cannot place manual bid in that same auction." Server doesn't enforce.
- Why it matters: Allows a bidder to circumvent their own max_price by manually bidding higher, defeating the autobid budget intent.
- Expected behavior: Inside the placeBid transaction (after locking the auction row but before validating amount), check `autoBidConfigDao.findActiveByAuctionAndBidder(auctionId, bidderId)`; if present and active, reject with `InvalidBidException`.
- Suggested fix: see H-AUTOBID-03.
- Related task: H-AUTOBID-03

### [High] H-AUTOBID-04 — DELETE auto-bid has no model status; "STOPPED" not represented

- ID: H-AUTOBID-04
- Module: Autobid
- Location:
  - `src/main/java/com/auction/App.java:406-423` (DELETE endpoint sets `active=false` only)
  - `src/main/java/com/auction/model/AutoBidConfig.java` — there is no `status` enum; only a boolean `active`.
- Evidence: The model collapses ACTIVE / STOPPED / EXHAUSTED / FAILED into one boolean `active`. The spec calls for four explicit statuses and a `failure_reason` to drive UI and notifications. The current DELETE behavior is indistinguishable from EXHAUSTED in the database.
- Problem: UI cannot show why an autobid is inactive; notifications cannot say "EXHAUSTED" vs "FAILED" vs "STOPPED".
- Why it matters: Spec is explicit on statuses and on notifications including the reason.
- Expected behavior: Add `status` (enum) and `failure_reason` (text/enum, nullable) to `auto_bid_configs`. Migrate `active=true → status='ACTIVE'`, `active=false → status='STOPPED'` (default). Update DAO + service + endpoint + DTO. UI uses `status` directly.
- Suggested fix: see H-AUTOBID-04.
- Related task: H-AUTOBID-04

### [High] H-ADMIN-01 — Hard-delete destroys bid history and leaks reservations

- ID: H-ADMIN-01
- Module: Admin / Auction
- Location:
  - `src/main/java/com/auction/App.java:297-304` (route)
  - `src/main/java/com/auction/service/AuctionService.java:297-307` (`hardDelete`)
  - `src/main/java/com/auction/dao/AuctionDao.java:520-534` (`hardDelete` body)
- Evidence: `hardDelete` deletes `auto_bid_configs`, then `bid_transactions`, then the auction row, in one transaction. No release of `reserved_balance` for the leading bidder. No prevention when auction has been PAID.
- Problem: Two bugs at once:
  1. Hard-deleting a RUNNING auction that has a leading bidder leaves that bidder's `reserved_balance` permanently allocated against an auction that no longer exists.
  2. Hard-deleting a PAID auction destroys the only audit trail of money movement.
- Why it matters: Data loss + leaked frozen balance are both High by definition.
- Expected behavior: Remove `hardDelete` from the public API. The admin "delete" path should soft-delete only (set status CANCELED), which already exists and which already releases reserved balance for RUNNING auctions in `persistCanceledAuction`. If hard-delete must exist for OPEN auctions with zero bids, gate it explicitly on `status='OPEN' AND NOT EXISTS (SELECT 1 FROM bid_transactions WHERE auction_id = :id)`.
- Suggested fix: see H-ADMIN-01.
- Related task: H-ADMIN-01

### [High] H-NOTIFICATION-01 — Client `mark-as-read` never persisted to server

- ID: H-NOTIFICATION-01
- Module: Notification
- Location:
  - `src/main/java/com/auction/util/NotificationStore.java:45, 130-133, 154-158, 160-163`
  - Server endpoints `PATCH /api/notifications/{id}/read` (`App.java:455-474`) and `PATCH /api/notifications/mark-all-read` (`App.java:478-496`) exist but the client never calls them.
- Evidence:
  - Notifications are stored client-side as `ObservableList<String>` — message strings, no `id`, no `is_read`, no user binding.
  - `markAllRead()` writes `notifications_read_count` to `Preferences.userNodeForPackage(NotificationStore.class)`. That preference is global per OS user, **not** per logged-in app user.
  - `clear()` (called on logout) zeros the preference key. On next login, `refreshUnreadCount()` reads `savedReadCount = 0` and treats every refetched notification as unread.
  - `getNotifications` returns a list of strings; the UI has no way to map them back to a server-side `notification.id`.
- Problem: Exactly the bug the audit prompt flagged. Marks-as-read are ephemeral; they reset on logout/login; on a shared machine they leak across users.
- Why it matters: It's a stated requirement bug and an IDOR-shaped privacy issue.
- Expected behavior:
  - Server is the source of truth for `is_read`.
  - Client fetches notifications with their server `id` and `is_read` flag (the server endpoint already returns them in the inline JDBI query at `App.java:432-450`).
  - `markAllRead()` calls `PATCH /api/notifications/mark-all-read` and only after a 2xx response updates local UI state. Single-notification clicks call `PATCH /api/notifications/{id}/read`.
  - Remove `Preferences` storage entirely.
- Suggested fix: see H-NOTIFICATION-01.
- Related task: H-NOTIFICATION-01

### [High] H-CI-01 — CI never runs `check`; SpotBugs configured but unused

- ID: H-CI-01
- Module: CI / Static analysis
- Location: `.github/workflows/ci.yml:107-117`, `build.gradle.kts:304-316`
- Evidence: CI step 8 runs only `./gradlew checkstyleMain checkstyleTest`; step 9 runs `./gradlew test`; step 10 runs `./gradlew jacocoTestReport`. `check`, `build`, and `spotbugsMain` are never invoked. README claims the pipeline runs `spotlessCheck → checkstyleMain → test → jacocoTestReport`, but step 7 is actually `./gradlew spotlessApply` (not `spotlessCheck`).
- Problem: SpotBugs, despite `effort = MAX`, `reportLevel = HIGH`, `ignoreFailures = false`, never executes. The most useful static-analysis tool in the build is dead.
- Why it matters: False confidence — the build appears to enforce SpotBugs, but PR builds will never report SpotBugs violations.
- Expected behavior: CI runs `./gradlew clean test check jacocoTestReport`. `check` already aggregates `checkstyleMain`, `checkstyleTest`, `spotbugsMain`, `spotbugsTest`. Remove the duplicate `checkstyleMain checkstyleTest` step.
- Suggested fix: see H-CI-01.
- Related task: H-CI-01

### [High] H-CI-02 — CI uses `spotlessApply` + git push to `main` with broad write perms

- ID: H-CI-02
- Module: CI / Repo hygiene / Security
- Location: `.github/workflows/ci.yml:9-11, 94-105`
- Evidence: `permissions: contents: write`. Step 7 runs `./gradlew spotlessApply` (which mutates source). Step "Commit formatting changes (if any)" runs `git config ... && git add . && git commit -m "style: apply Spotless formatting [skip ci]" && git push`.
- Problem:
  1. CI mutates source files and pushes to `main` outside of PRs/review. `[skip ci]` then skips the test step on the formatting commit, so the formatting commit is unverified.
  2. `contents: write` is wider than necessary.
  3. If anyone forgets to install the pre-commit hook, CI fixes their style and pushes — encouraging skipping the local hook.
- Why it matters: Bypasses code review for changes to source.
- Expected behavior: Run `./gradlew spotlessCheck` (read-only) and fail the build if it would change anything. Drop the auto-commit step. Restrict `permissions` to the minimum needed (only `contents: read` for non-formatting jobs).
- Suggested fix: see H-CI-02.
- Related task: H-CI-02

### [Medium] M-CI-03 — No JaCoCo coverage threshold

- ID: M-CI-03
- Module: CI / Test quality
- Location: `build.gradle.kts:246-253`
- Evidence: `jacocoTestReport` is configured for HTML+XML output, but there is no `jacocoTestCoverageVerification` task with `violationRules`. README mentions coverage; nothing enforces it.
- Problem: Coverage drops are invisible. Tests can be deleted without failing CI.
- Expected behavior: Add a `jacocoTestCoverageVerification` task wired into `check` with at minimum a line+branch threshold (start at the current actual coverage minus a few %, ratchet up later).
- Related task: M-CI-03

### [Medium] M-CI-04 — CI duplicates schema setup that Flyway should own

- ID: M-CI-04
- Module: CI / DB
- Location: `.github/workflows/ci.yml:68-92`
- Evidence: The CI "Init DB schema" step manually applies V1, V2, V3 with `psql`, then later steps run tests. The app uses Flyway to manage migrations; tests should pick up V1..V13 automatically once a DataSource is wired. Manually applying only V1..V3 also means anything in V4..V13 that tests depend on (notifications table, password_reset_requests, reserved_balance, SETTLING status) relies on Flyway running afterward.
- Problem: Drift between manual schema and Flyway; CI breaks every time a migration is added unless someone updates the workflow.
- Expected behavior: Drop the manual `psql -f V*.sql` block. Let Flyway own schema. Optionally drop and recreate `auction_test` only; Flyway will do the rest.
- Related task: M-CI-04

### [Medium] M-MONEY-01 — Money is `BigDecimal(15,2)` end-to-end, spec demands integer VND

- ID: M-MONEY-01
- Module: Money / Schema
- Location: All migration files; `BigDecimal` types throughout models, DTOs, services
- Evidence: Every money column is `DECIMAL(15,2)`. Models, DTOs, DAOs, services all use `BigDecimal`. No `setScale(0, ROUND_UNNECESSARY)` enforcement in services. Notification messages use `price.longValue()` which silently truncates.
- Problem: Spec wants integer VND. Decimals are accepted on the wire and stored. `longValue()` truncates inconsistently in notification text. Format/parse round-trips can introduce non-integer values.
- Expected behavior: Either migrate to `BIGINT` columns + `long` types (preferred), or enforce `BigDecimal(scale=0)` at validation time and `setScale(0)` in services. Notification messages use `String.format("%,d", ...)` only after assert.
- Related task: M-MONEY-01

### [Medium] M-AUTH-03 — Password change doesn't invalidate JWT; no token revocation

- ID: M-AUTH-03
- Module: Auth
- Location: `src/main/java/com/auction/service/UserService.java:192-212`
- Evidence: After successful `changePassword`, the old JWT (24h lifetime) remains valid.
- Problem: A leaked token survives password rotation.
- Expected behavior: Either include a `password_version` claim in the JWT that the middleware compares against the DB, or add a per-user `tokens_issued_after` field and reject tokens issued earlier.
- Related task: M-AUTH-03

### [Medium] M-WS-01 — WebSocket token expiration not re-checked

- ID: M-WS-01
- Module: WebSocket
- Location: `src/main/java/com/auction/controller/AuctionWebSocketHandler.java:93-124, 210-239`
- Evidence: JWT is verified once on connect. The session can stay open for hours; if the JWT expires, the user keeps receiving private balance updates on `/ws/user/{id}`.
- Problem: Spec asks "Does token expiration affect WebSocket?"; answer is currently no.
- Expected behavior: Periodically re-verify the token (e.g., check `exp` claim on the original `DecodedJWT` and close the session when reached), or require the client to reconnect on token rotation.
- Related task: M-WS-01

### [Medium] M-DEAD-CODE-01 — Dead methods that bypass settlement

- ID: M-DEAD-CODE-01
- Module: DB / Auction
- Location: `src/main/java/com/auction/dao/AuctionDao.java:548-583` (`closeExpiredAuctions`, `startScheduledAuctions`)
- Evidence: Both methods exist and unconditionally do plain `UPDATE auctions SET status='FINISHED'/'RUNNING'` without any settlement, payout, or reservation handling. They appear to be unused (the scheduler uses `findDueAuctionIds` + `atomicTransition` instead).
- Problem: Landmine. If anyone calls `closeExpiredAuctions()`, RUNNING auctions move to FINISHED with no payout, no notification, no reserved_balance release — a leading bidder keeps their balance frozen forever.
- Expected behavior: Remove both methods.
- Related task: M-DEAD-CODE-01

### [Medium] M-ITEM-01 — `items` table has no `status`; no item lifecycle

- ID: M-ITEM-01
- Module: Item / DB
- Location: `src/main/resources/db/migration/V1__initial_schema.sql:30-41`, `Item.java`, `ItemDao.java`
- Evidence: No status column anywhere. The model and DAO don't expose one.
- Problem: Items have no SOLD state. Combined with H-AUCTION-01, the "single active auction per item" rule has no schema anchor.
- Expected behavior: Add `items.status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','IN_AUCTION','SOLD','REMOVED'))`. Set IN_AUCTION on auction insert, SOLD on settle-to-PAID, AVAILABLE on cancel-without-bids.
- Related task: M-ITEM-01

### [Medium] M-NOTIFICATION-02 — Cancel notification inserted outside cancellation transaction

- ID: M-NOTIFICATION-02
- Module: Notification
- Location: `src/main/java/com/auction/service/AuctionService.java:338-367`
- Evidence: `persistCanceledAuction` opens a transaction for the status change + reserved balance release. `notifyLeadingBidderIfCanceled` runs after, using `jdbi.useHandle` (its own connection / no shared transaction) to INSERT the notification.
- Problem: The cancellation can commit, then the notification insert can fail (DB transient error, etc.), and there is no compensation.
- Expected behavior: Insert the notification in the same transaction; emit the WebSocket event after commit.
- Related task: M-NOTIFICATION-02

### [Medium] M-WALLET-03 — No wallet/transaction ledger

- ID: M-WALLET-03
- Module: Money
- Evidence: There is no table recording individual balance moves (deposit approval, freeze, release, payout, win consumption). All audit comes from joining `bid_transactions` + `deposit_requests` + manual log inspection.
- Problem: Spec recommends a ledger for auditability. Bugs in the freeze/release path leave no forensic trail beyond logback output.
- Expected behavior: Add a `wallet_transactions` table (id, user_id, auction_id nullable, kind enum [DEPOSIT, FREEZE, RELEASE, WIN_CONSUME, SELLER_PAYOUT], amount, created_at, ref_id). Insert one row per balance move inside the relevant transaction.
- Related task: M-WALLET-03

### [Medium] M-TEST-01 — Concurrency tests cover only same-price ties

- ID: M-TEST-01
- Module: Tests
- Location: `src/test/java/com/auction/service/BidServiceConcurrencyTest.java`
- Evidence: One test, 10 threads, all bid the same amount. Asserts exactly one wins and exactly one bid_transactions row. Does not test escalating bids, autobid chain races, freeze/release races, settlement-vs-bid races, or overcommit prevention across multiple auctions for the same bidder.
- Problem: Concurrency confidence is overstated.
- Expected behavior: Add tests as listed in §7.
- Related task: M-TEST-01

### [Medium] M-TEST-02 — No authorization tests

- ID: M-TEST-02
- Module: Tests / Security
- Evidence: There are no HTTP-level tests asserting that:
  - A SELLER token can't bid (the actual bug in H-AUTH-02 would have been caught immediately).
  - A bidder can't stop another bidder's autobid.
  - A user can't mark another user's notification as read.
  - A non-admin can't hit `/api/admin/*` routes.
  - A SELLER can't edit or cancel another seller's auction.
- Expected behavior: Add a test harness that boots Javalin, issues JWTs of each role, and asserts 401/403 on cross-role attempts.
- Related task: M-TEST-02

### [Medium] M-TEST-03 — No notification persistence test

- ID: M-TEST-03
- Module: Tests
- Evidence: No test asserts that `is_read=true` persists across a refetch, or that one user's read state doesn't affect another's.
- Expected behavior: Integration test that calls `PATCH /api/notifications/{id}/read`, then re-fetches `/api/notifications`, asserts `is_read=true`.
- Related task: M-TEST-03

### [Medium] M-DB-01 — FKs aren't ON DELETE-aware; admin delete user crashes silently

- ID: M-DB-01
- Module: DB
- Location: V1 schema, `UserDao.delete`
- Evidence: `items.seller_id`, `auctions.item_id`, `auctions.leading_bidder_id`, `auctions.seller_id`, `bid_transactions.auction_id`, `bid_transactions.bidder_id`, `auto_bid_configs.auction_id`, `auto_bid_configs.bidder_id` all reference `users(id)` / `auctions(id)` / `items(id)` with no ON DELETE clause. The `UserDao.java:392-413` Javadoc claims ON DELETE CASCADE — the schema does **not** have it.
- Problem: `DELETE /api/admin/users/{id}` will throw an FK violation for any user with bids/items/auctions; the global handler returns 500. The Javadoc is misleading.
- Expected behavior: Either soft-delete users (preferred — preserves history) by adding `users.is_active` and filtering at login, or explicitly disallow deletion of users with non-trivial history with a 409 response. Update the Javadoc either way.
- Related task: M-DB-01

### [Medium] M-API-01 — Notifications endpoints implemented inline in `App.java`

- ID: M-API-01
- Module: API design
- Location: `App.java:427-496`
- Evidence: Three notification endpoints use inline JDBI queries directly in `App.java`. There's no `NotificationController` or `NotificationService`. Compare with the well-factored `AuctionController` / `AuctionService` split elsewhere.
- Problem: Hard to test, hard to evolve, violates the layering used everywhere else.
- Expected behavior: Extract `NotificationService` + `NotificationDao` + `NotificationController` matching the layering used by other resources.
- Related task: M-API-01

### [Medium] M-DOC-01 — README disagrees with code on CI and role policy

- ID: M-DOC-01
- Module: Docs
- Location: `README.md:71, 999, 1046`
- Evidence:
  - README: "POST /api/auctions/{id}/bid (manual, role: BIDDER)". Code: BIDDER **and** SELLER allowed.
  - README: "Steps: spotlessCheck → checkstyleMain → test → jacocoTestReport". CI: `spotlessApply` (not check) and pushes a commit; `check` is never run.
- Problem: Onboarding readers will be misled.
- Expected behavior: After H-AUTH-02 and H-CI-01/02 are fixed, README will be accurate. If those fixes are deferred, fix the README to describe reality.
- Related task: M-DOC-01

### [Medium] M-DOC-02 — Business-rules / ERD docs missing

- ID: M-DOC-02
- Module: Docs
- Evidence: README is operational-ops focused. There is no concise document listing the business rules (bid > current, first bid > starting, leader cannot rebid, autobid statuses, single-active-auction, freeze/release semantics) or an ERD.
- Expected behavior: Add `docs/BUSINESS_RULES.md` and `docs/SCHEMA.md` with a table-by-table summary or an ERD image.
- Related task: M-DOC-02

### [Low] L-CORS-01 — CORS allowlist contains a TODO and a port that doesn't exist

- ID: L-CORS-01
- Module: API
- Location: `App.java:528-534`
- Evidence: `cors.addRule(it -> it.allowHost("localhost:3000", "localhost:8080"));`. The FX client doesn't go through CORS at all; the JavaFX HTTP client isn't a browser. The 3000 port isn't used by anything in the project.
- Problem: Dead config + a TODO.
- Expected behavior: Drop the rule for now; if a browser client appears later, add the right hosts.
- Related task: L-CORS-01

### [Low] L-LOG-01 — Bid placement logs `bidder=` userId at INFO

- ID: L-LOG-01
- Module: Logging
- Location: `BidService.java:166-171`
- Evidence: Logs userId/amount at INFO on every bid. For a busy auction, this is noisy. No PII risk (no usernames/passwords).
- Expected behavior: Demote to DEBUG; keep aggregate metrics if needed.
- Related task: L-LOG-01

### [Low] L-STRATEGY-01 — `BidStrategy` interface is decorative

- ID: L-STRATEGY-01
- Module: Patterns
- Location: `pattern/strategy/BidStrategy.java`, `ManualBidStrategy.java`, `AutoBidStrategy.java`
- Evidence: `BidService.placeBid` calls `auctionService.getState(auction).placeBid(...)` (the State pattern). It does **not** dispatch through any `BidStrategy` instance. `ManualBidStrategy.execute(...)` is never called from production code (search confirms no caller). The Strategy class hierarchy duplicates checks already done in `RunningState.placeBid`.
- Problem: Pattern exists in the file structure but not in the runtime flow.
- Expected behavior: Either wire it in (BidService receives a `BidStrategy`, picks Manual vs Auto, delegates), or delete it and rely on State. Either way, document the choice.
- Related task: L-STRATEGY-01

### [Low] L-NAMING-01 — Status name OPEN vs spec's SCHEDULED, etc.

- ID: L-NAMING-01
- Module: Auction state
- Evidence: V1 uses `OPEN`, `RUNNING`, `FINISHED`, `PAID`, `CANCELED`. Spec recommends `SCHEDULED`, `RUNNING`, `ENDED_NO_BID`, `PAID`, `CANCELLED`. The current names are usable but ambiguous (OPEN sounds like "accepting bids" but means "scheduled").
- Expected behavior: Document the mapping in README. Renaming is large and risky; not worth it for a student project. Document instead.
- Related task: L-NAMING-01

## 6. Module-by-Module Audit

**Auth / JWT / Authorization** — Tasks: H-AUTH-01, H-AUTH-02, M-AUTH-03. Middleware classification (public / semi-public / protected) is clear. `requireAdmin` / `requireRole` derive from JWT, not body — good. Token forge possible via H-AUTH-01. Role policy on bid endpoint wrong via H-AUTH-02.

**User / Admin / Seller / Bidder** — Roles are clean classes with an `instanceof Admin` block in `register` to prevent self-promotion. Admin user-deletion broken-by-FK (M-DB-01). No per-user audit log of admin actions (covered loosely by M-WALLET-03).

**Auction lifecycle** — Tasks: H-AUCTION-01, H-AUCTION-02, H-AUCTION-03, H-AUCTION-04, H-ADMIN-01. State pattern itself is correctly modeled but routes SETTLING to RUNNING. Scheduler scans every 5s with `scheduleAtFixedRate`, single thread — fine for a demo. Anti-snipe is in BidService inside the FOR-UPDATE lock — good. Settlement race is the headline bug.

**Manual bid** — Tasks: H-AUTH-02, H-AUTOBID-03. `BidService.placeBid` is the strongest function in the codebase; the only fix is the missing "no ACTIVE autobid" check.

**Wallet / Frozen balance / Payment** — Tasks: H-WALLET-01, H-WALLET-02, M-MONEY-01, M-WALLET-03. The `reserved_balance` design is correct. Implementation has the `GREATEST(...,0)` softening and the catastrophic "wipe all reservations" fallback.

**Autobid** — Tasks: H-AUTOBID-01..04. The most broken module. The DAO is fine; the creation endpoint, the chain, and the status model are all incorrect vs spec.

**Notification** — Tasks: H-NOTIFICATION-01, M-NOTIFICATION-02, M-API-01. Schema is fine. Server-side `is_read` is a working flag the client never uses.

**Chart / Bid history** — `GET /api/auctions/{id}/bids` exists and returns `List<BidTransaction>`. The transaction includes `auto_bid` flag and `created_at`. Chart should be sourced from this. Not deeply audited (no obvious bugs); see M-TEST-03 for a regression test that this list returns both MANUAL and AUTOBID rows.

**WebSocket** — Task: M-WS-01. Auth on connect works. No state-changing client→server messages. Events emitted after commit. Token expiration not re-checked.

**JavaFX UI** — Task: H-NOTIFICATION-01. Architecture is layered. Only audited the notification + REST plumbing in depth. JavaFX controllers run blocking REST calls via `RestClient`; need to confirm they all run off the FX thread — not a High finding here pending further inspection.

**Database / JDBI** — Tasks: M-DB-01, M-ITEM-01, M-DEAD-CODE-01. Queries use named parameters, no concatenation, no injection seen. Migrations are idempotent (`IF NOT EXISTS`). Missing item status. Missing wallet ledger. FKs lack ON DELETE.

**Tests** — Tasks: M-TEST-01, M-TEST-02, M-TEST-03. 130+ tests but the wrong shape for this domain.

**CI / Build / Static analysis** — Tasks: H-CI-01, H-CI-02, M-CI-03, M-CI-04. Tooling chosen well, not actually wired into the verifier.

**Docs / Fresh clone** — Tasks: M-DOC-01, M-DOC-02. README is honest about most things and large; mismatches are listed.

**Repo hygiene** — `.gitignore` is thorough; `.env` is not committed; gradle wrapper jar tracked; embedded postgres data directory ignored. Solid.

## 7. Test Gap Analysis

**Unit tests to add**

- `BidService` rejects when caller is not BIDDER (role check) — once H-AUTH-02 lands.
- `BidService` rejects when caller has an ACTIVE autobid on the auction — once H-AUTOBID-03 lands.
- `AuctionService.create` rejects when item is already in an active/scheduled auction — once H-AUCTION-01 lands.
- `AutoBidConfig.canBidAt` and `getNextBidAmount` boundary cases (`currentPrice + increment == maxBid` should be allowed; `+1` over should be EXHAUSTED).
- `UserDao.releaseReservedBalanceInTransaction` raises when amount > reserved (post H-WALLET-02).
- `SettlingState.placeBid` always rejects (post H-AUCTION-04).

**Integration tests to add**

- `POST /api/auctions/{id}/auto-bid` with bidder not leading: returns 201 AND a `BidTransaction` row appears AND `reserved_balance` increased by the accepted amount.
- `POST /api/auctions/{id}/auto-bid` when leader: 400 with reason `BIDDER_ALREADY_HIGHEST`.
- `POST /api/auctions/{id}/auto-bid` twice without stopping: second returns 409 `ACTIVE_AUTOBID_EXISTS`.
- `DELETE /api/auctions/{id}/auto-bid` then re-create works.
- `PATCH /api/notifications/{id}/read` persists across a refetch.
- `PATCH /api/notifications/{id}/read` on someone else's notification: WHERE clause filters by user, no row affected (asserted via `is_read` still false on the original owner's view).
- Admin hard-delete of auction with bids: rejected (post H-ADMIN-01).

**Concurrency tests to add**

- 5 threads, escalating bids (1M, 2M, 3M, 4M, 5M): exactly 5 succeed in some serialized order; final `currentPrice = 5M`; `leading_bidder_id` matches the 5M bidder; reservation invariant holds.
- 1 manual bid that triggers 3 autobids (3 ACTIVE configs at maxBid 5M, 10M, 8M, step 100K): chain produces exactly 3 chained `bid_transactions` rows with `auto_bid=true`; final `leading_bidder_id` is the 10M-config holder; reservation = final accepted bid amount for that bidder.
- 2 bidders with `balance=1M` each, 2 active auctions: A leads auction 1 at 600K, B leads auction 2 at 600K; A tries to bid 500K on auction 2 — must be rejected because `availableBalance = 1M - 600K = 400K`.
- Scheduler settlement runs concurrently with a last-second bid: bid commits before SETTLING claim → winner is the bidder; bid attempted after SETTLING → rejected (post H-AUCTION-04).

**Security / Authorization tests to add**

- SELLER token on `POST /api/auctions/{id}/bid`: 401/403.
- BIDDER token on `POST /api/auctions`: 401/403.
- BIDDER A's token on `DELETE /api/auctions/{id}/auto-bid` for BIDDER B's auction: 401/403 (currently the endpoint filters by `bidderId` from JWT — needs an explicit test).
- USER A's token on `PATCH /api/notifications/{id}/read` for USER B's notification id: no rows updated.
- Anonymous user on `/ws/user/{id}`: connection rejected (already enforced; assert it).

**UI / Manual QA (see also FIX_TASKS.md)**

- Notification mark-as-read survives logout/login on the same machine.
- Notification read state doesn't leak between two user sessions on the same OS user.

**Regression**

- Smoke test for the `seedAdminIfNeeded` path: when starting against an empty DB, the `admin/123456` account exists, has role ADMIN, and password hash verifies.

## 8. Risk Register

| Risk | Impact | Likelihood | Severity | Related task |
|---|---|---|---|---|
| Forged ADMIN JWT via hardcoded fallback | Total compromise | High (anyone with source) | High | H-AUTH-01 |
| SELLER manipulates competitors' auctions via bid endpoint | Market manipulation | High | High | H-AUTH-02 |
| Same item in multiple active auctions | Wrong settlement, double-spend by sellers | Medium | High | H-AUCTION-01 |
| Wrong winner at wrong price due to settle race | Money + trust loss | Medium (timing window) | High | H-AUCTION-02 |
| Anti-snipe extension ignored by scheduler | Snipe-bid users unfairly outbid | Medium | High | H-AUCTION-03 |
| Bids accepted on SETTLING auctions | Inconsistent state | Low (small window) | High | H-AUCTION-04 |
| Reservations wiped across other auctions on settlement failure | Cross-auction money corruption | Medium | High | H-WALLET-01 |
| `GREATEST(...,0)` hides reservation bugs | Slow money drift | High (long-lived) | High | H-WALLET-02 |
| Autobid creation never places initial bid / never freezes balance | Feature broken; balance over-committable | High | High | H-AUTOBID-01 |
| Autobid chain partial commit + silent failure | Inconsistent prices, silent user-facing failure | Medium | High | H-AUTOBID-02 |
| Bidder manual-bids past their own autobid budget | Budget bypass | Medium | High | H-AUTOBID-03 |
| Autobid status STOPPED/EXHAUSTED/FAILED indistinguishable | User confusion, no failure_reason | High | High | H-AUTOBID-04 |
| Admin hard-delete destroys bid history, leaks frozen balance | Data loss, ghost reservation | Medium | High | H-ADMIN-01 |
| Notification mark-as-read never persists, leaks across users | UX bug + privacy | High | High | H-NOTIFICATION-01 |
| SpotBugs never runs in CI | False sense of security | Certain | High | H-CI-01 |
| CI auto-commits to `main`, bypasses review | Repo integrity | Medium | High | H-CI-02 |
| Coverage drops invisible | Test rot | High | Medium | M-CI-03 |
| Manual CI schema setup drifts from Flyway | Build break on new migration | Medium | Medium | M-CI-04 |
| `BigDecimal` decimals on money | Rounding bugs, format drift | Low | Medium | M-MONEY-01 |
| JWT survives password change | Account theft window | Low | Medium | M-AUTH-03 |
| Long-lived WS sessions with expired tokens | Stale access | Low | Medium | M-WS-01 |
| Dead `closeExpiredAuctions` / `startScheduledAuctions` | Landmine | Low | Medium | M-DEAD-CODE-01 |
| No `items.status` | Schema can't represent SOLD | Medium | Medium | M-ITEM-01 |
| Cancel notification outside cancellation transaction | Inconsistent notifications | Low | Medium | M-NOTIFICATION-02 |
| No wallet ledger | Bugs undebuggable | Medium | Medium | M-WALLET-03 |
| 1 same-price concurrency test | Race coverage weak | High | Medium | M-TEST-01 |
| No authorization tests | Regressions invisible | High | Medium | M-TEST-02 |
| No notification persistence test | The flagged bug stays | High | Medium | M-TEST-03 |
| FK ON DELETE not specified, admin delete 500s | Bad UX, misleading docs | Medium | Medium | M-DB-01 |
| Notifications API in App.java | Hard to evolve / test | Medium | Medium | M-API-01 |
| README disagrees with code | Onboarding pain | High | Medium | M-DOC-01 |
| Business-rules + ERD docs missing | Onboarding pain | Medium | Medium | M-DOC-02 |
| CORS allows port 3000 with no client | Dead config | Low | Low | L-CORS-01 |
| INFO-level bid logs | Noise | Medium | Low | L-LOG-01 |
| `BidStrategy` interface decorative | Confusing | Low | Low | L-STRATEGY-01 |
| Status naming ambiguous | Mild confusion | Low | Low | L-NAMING-01 |

## 9. Known Accepted Trade-offs

These were explicitly listed as acceptable simplifications in the audit prompt; no fix tasks are created for them unless a concrete bug exists outside their accepted scope.

- **Forgot-password manual-approval flow.** Implementation is actually stronger than the prompt anticipated (random 12-char base64url temp password rather than `123456`). The new hash uses BCrypt cost 12, identical to registration. No bug outside the accepted scope.
- **Local-demo HTTP (no HTTPS).** Accepted. CORS rule for `localhost:8080` is fine; the leftover `localhost:3000` is mentioned in L-CORS-01.
- **No real payment gateway / no withdrawal.** Accepted; deposit approval is the only money-in path. The lack of a wallet ledger is captured in M-WALLET-03 because it concerns auditability of *all* internal moves (freeze/release/payout), not the missing gateway.
- **No automated JavaFX UI tests.** Accepted; manual QA checklist is in `FIX_TASKS.md`.
- **No production cloud deployment.** Accepted.
- **No email reset token.** Accepted.
- **No production observability / Docker.** Accepted.

## 10. Recommended Roadmap Summary

**Phase 1 — High severity (12 tasks)**

Execution order in `FIX_TASKS.md`'s "Recommended Execution Order" section. After Phase 1 the system meets the explicit business-rule bar in the prompt.

**Phase 2 — Medium severity (13 tasks)**

Test gaps, money representation, schema cleanup, JaCoCo enforcement, README sync, wallet ledger, dead-code removal, etc. Most are independent and parallelizable; only M-ITEM-01 has interactions with H-AUCTION-01 (do M-ITEM-01 first if both are in the same PR).

**Phase 3 — Low severity (4 tasks)**

Cleanup and clarity. Safe to bundle.

End-of-each-phase verification: `./gradlew clean test check jacocoTestReport`.
