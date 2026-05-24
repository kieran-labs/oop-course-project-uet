# UML Source-Level Coverage Audit

This audit maps the `src/main/java` source tree to the README class diagrams and separates real source classes from compiler-generated artifacts.

## Audit Basis

This pass used the complete merged source file supplied for review and the current `README.md` on `main`. The goal is not only package-level coverage, but factual agreement between source declarations and the Mermaid UML nodes where the README claims to represent source-level classes.

## Scope

Included:

- Top-level Java source files under `src/main/java/com/auction`.
- Source-level nested classes, records, enums, and interfaces that appear as named compiled classes.
- README Mermaid `classDiagram` declarations and relation endpoints.

Excluded from strict UML class coverage:

- `src/test/java` test classes.
- `build/classes` compiler output.
- Anonymous compiler-generated classes such as `AuctionListController$1`, `AdminPanelController$10`, `CreateAuctionController$1`, `WebSocketClient$2`, and similar callback/lambda/anonymous UI helper classes.
- `package-info.java`, because it documents a package and is not a runtime/domain class.
- Gradle/cache/database/generated runtime files such as `.gradle`, `build`, `data/postgres`, and `logs`.

## Top-Level Source-File Coverage

Top-level source-file coverage remains complete at design level.

| Package | Source files represented by README UML |
|---|---|
| `com.auction` | `AdminSeeder.java`, `App.java`, `ClientApp.java`, `Launcher.java` |
| `config` | `DatabaseConfig.java`, `JwtUtil.java` |
| `middleware` | `JwtMiddleware.java` |
| `controller` | `AuctionController.java`, `AuctionWebSocketHandler.java`, `AuthController.java`, `BidController.java`, `ItemController.java`, `NotificationController.java` |
| `dao` | `AuctionDao.java`, `AutoBidConfigDao.java`, `BidTransactionDao.java`, `DepositRequestDao.java`, `ItemDao.java`, `NotificationDao.java`, `PasswordResetRequestDao.java`, `UserDao.java`, `WalletTransactionDao.java` |
| `dto` | `AuctionResponse.java`, `AutoBidRequest.java`, `BidRequest.java`, `BidUpdateMessage.java`, `ChangePasswordRequest.java`, `CreateAuctionRequest.java`, `CreateItemRequest.java`, `DepositRequest.java`, `ErrorResponse.java`, `ForgotPasswordRequest.java`, `LoginRequest.java`, `PageRequest.java`, `RegisterRequest.java`, `UserResponse.java` |
| `exception` | `AuctionClosedException.java`, `AuctionException.java`, `DuplicateException.java`, `InvalidBidException.java`, `NotFoundException.java`, `UnauthorizedException.java` |
| `model` | `Admin.java`, `Art.java`, `Auction.java`, `AuctionStatus.java`, `AutoBidConfig.java`, `AutoBidFailureReason.java`, `AutoBidStatus.java`, `Bidder.java`, `BidTransaction.java`, `DepositRecord.java`, `Electronics.java`, `Entity.java`, `Item.java`, `PasswordResetRecord.java`, `Seller.java`, `User.java`, `Vehicle.java` |
| `pattern/factory` | `AuctionStateFactory.java`, `ItemFactory.java`, `UserFactory.java` |
| `pattern/observer` | `AuctionEventListener.java`, `AuctionEventManager.java`, `WebSocketObserver.java` |
| `pattern/state` | `AuctionState.java`, `AuctionStates.java`, `CanceledState.java`, `FinishedState.java`, `OpenState.java`, `PaidState.java`, `RunningState.java`, `SettlingState.java` |
| `pattern/strategy` | `AutoBidStrategy.java` |
| `service` | `AuctionScheduler.java`, `AuctionService.java`, `BidService.java`, `ItemService.java`, `NotificationService.java`, `PasswordResetService.java`, `UserService.java` |
| `ui/controller` | `AdminPanelController.java`, `AuctionDetailController.java`, `AuctionListController.java`, `ChangePasswordController.java`, `CreateAuctionController.java`, `CreateItemController.java`, `DepositController.java`, `ForgotPasswordController.java`, `LoginController.java`, `ProfileController.java`, `RegisterController.java`, `WelcomeController.java` |
| `ui/util` | `Navigable.java`, `SceneManager.java` |
| `util` | `BackgroundBidWatcher.java`, `MoneyValidator.java`, `NotificationFormat.java`, `NotificationItem.java`, `NotificationStore.java`, `RestClient.java`, `UserBalanceWatcher.java`, `WebSocketClient.java` |

## Confirmed Correct High-Level Fixes Already Present

- `InlineAppRoutes` is no longer represented as a real source class in README UML.
- The architecture flowchart is explicitly labeled as runtime communication/data-flow, not strict Java import graph.
- `App.java` runtime composition includes direct creation/dependency links to the main DAOs, services, `AutoBidStrategy`, controllers, WebSocket handler, and scheduler.
- `AuctionStates` consistently lists all six singleton states: `OPEN`, `RUNNING`, `SETTLING`, `FINISHED`, `PAID`, `CANCELED`.
- `AuctionWebSocketHandler` includes the major public WebSocket/notification methods and representative cleanup/token helpers.
- `AuctionStateFactory` includes its private constructor.
- `UserResponse`, `AuctionResponse`, `ErrorResponse`, `PageRequest`, and exception inheritance are corrected at design level.
- Foreign-key-like model links point to `User`, `Item`, or `Auction` when source stores IDs only.

## Residual Factual Mismatches Found in This Strict Pass

The following are not merely stylistic omissions. They are factual mismatches between the current README Mermaid declarations and the supplied source code.

| Severity | README location | Current README declaration | Source truth | Required fix |
|---|---|---|---|---|
| High | Diagram 7, `BidHistoryEntry` | Declared with flattened fields `id`, `auctionId`, `bidderId`, `bidderUsername`, `amount`, `autoBid`, `createdAt`. | Source declares `public record BidHistoryEntry(BidTransaction transaction, String username)` and exposes shortcut methods such as `getAuctionId()`, `getBidderId()`, `getAmount()`, `isAutoBid()`, `getCreatedAt()`. | Replace fields with `-transaction`, `-username`, then list the shortcut methods. |
| High | Diagram 7, `BalanceDisplay` | Declared as `-balance`, `-availableBalance`. | Source declares `private record BalanceDisplay(String text, String color)`. | Replace fields with `-text`, `-color`. |
| High | Diagram 7, `GlassCalendarState` | Declared as `<<record>>` with `visibleMonth`, `selectedDate`. | Source declares `private static final class GlassCalendarState` with `hoveredCell`, `hoverProgress`, `hoverTimeline`, and `refreshAll()`. | Change from record to nested class and replace fields/methods. |
| Medium | Diagram 7, `GlassDateCell` | Lists only `picker`, `state`, `updateItem()`. | Source also stores `shadow` and has private constructor plus `refreshAppearance()`. | Add `-shadow`, `-GlassDateCell()`, `-refreshAppearance()`. |
| Medium | Diagram 4, `BidUpdateMessage` constants | Constants are written with private visibility marker `-TYPE_*`. | Source constants are `public static final String TYPE_*`. | Change the six constant markers from `-TYPE_*` to `+TYPE_*`. |
| Medium | Diagram 2 and 7, `BidHistoryEntry` duplication | `BidHistoryEntry` appears with the same flattened-field shape in service/DAO and nested helper diagrams. | Same source truth as above: the record stores `transaction` and `username`, not flattened fields. | Apply the same correction in both appearances. |

## Recommended README Patch Snippets

### Correct `BidHistoryEntry`

```mermaid
class BidHistoryEntry {
    <<record>>
    -transaction
    -username
    +getAuctionId()
    +getBidderId()
    +getAmount()
    +isAutoBid()
    +getCreatedAt()
}
```

### Correct `BalanceDisplay`

```mermaid
class BalanceDisplay {
    <<record>>
    -text
    -color
}
```

### Correct `GlassDateCell` and `GlassCalendarState`

```mermaid
class GlassDateCell {
    <<nested class>>
    -picker
    -state
    -shadow
    -GlassDateCell()
    +updateItem()
    -refreshAppearance()
}

class GlassCalendarState {
    <<nested class>>
    -hoveredCell
    -hoverProgress
    -hoverTimeline
    -refreshAll()
}
```

### Correct `BidUpdateMessage` constants visibility

```mermaid
class BidUpdateMessage {
    +TYPE_BID_UPDATE
    +TYPE_TIME_EXTENDED
    +TYPE_AUCTION_ENDED
    +TYPE_AUTO_BID_TRIGGERED
    +TYPE_BALANCE_UPDATED
    +TYPE_USER_NOTIFICATION
}
```

## Current Verdict

- Top-level source-file coverage: **complete**.
- Source-level named nested type coverage: **present but not yet fully accurate** because of the mismatches above.
- Compiler-generated anonymous classes: **correctly excluded**.
- Main residual problem: **README diagram 7 and `BidUpdateMessage` constant visibility need one more patch**.
- No new missing top-level classes were found in this strict pass.
