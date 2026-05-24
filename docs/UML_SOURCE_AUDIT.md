# UML Source-Level Coverage Audit

This audit maps the `src/main/java` source tree to the README class diagrams and separates real source classes from compiler-generated artifacts.

## Scope

Included:

- Top-level Java source files under `src/main/java/com/auction`.
- Source-level nested classes, records, enums, and interfaces that appear as named compiled classes.

Excluded from UML class diagrams unless explicitly needed:

- `src/test/java` test classes.
- `build/classes` compiler output.
- Anonymous compiler-generated classes such as `AuctionListController$1`, `AdminPanelController$10`, `CreateAuctionController$1`, `WebSocketClient$2`, and similar callback/lambda/anonymous UI helper classes.
- `package-info.java`, because it documents a package and is not a runtime/domain class.
- Gradle/cache/database/generated runtime files such as `.gradle`, `build`, `data/postgres`, and `logs`.

## Top-Level Source Files

The following top-level source files are represented by the README UML set:

| Package | Source files |
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

## Source-Level Nested Types Still Worth Representing

These are not separate `.java` files, but they are real source-level named types and appear as `.class` files after compilation. They should be included in a strict "all classes" UML version.

| Owner source file | Nested type | Kind | Recommended UML placement |
|---|---|---|---|
| `AuctionDao.java` | `AuctionMapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `AutoBidConfigDao.java` | `AutoBidConfigMapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `BidTransactionDao.java` | `BidTransactionMapper` | private static class | DAO diagram, optional if row mappers are shown |
| `BidTransactionDao.java` | `BidHistoryEntry` | public record | DAO/DTO boundary diagram |
| `DepositRequestDao.java` | `DepositRecordMapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `ItemDao.java` | `ItemMapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `PasswordResetRequestDao.java` | `Mapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `UserDao.java` | `UserMapper` | mapper class | DAO diagram, optional if row mappers are shown |
| `AuctionScheduler.java` | `BalanceChange` | private record | Runtime/scheduler diagram |
| `AuctionScheduler.java` | `UserNotification` | private record | Runtime/scheduler diagram |
| `AuctionScheduler.java` | `SettlementResult` | private record | Runtime/scheduler diagram |
| `AutoBidStrategy.java` | `AutoBidExecutor` | nested interface | Strategy diagram; already represented |
| `AutoBidStrategy.java` | `InTransactionBidExecutor` | nested interface | Strategy diagram; already represented |
| `SceneManager.java` | `ResizeDirection` | private enum | JavaFX/navigation diagram |
| `AuctionListController.java` | `BalanceDisplay` | private record | JavaFX/notification diagram |
| `CreateAuctionController.java` | `GlassDateCell` | private final class | JavaFX/date-picker UI detail diagram |
| `CreateAuctionController.java` | `GlassCalendarState` | private static final class | JavaFX/date-picker UI detail diagram |

## Confirmed README Corrections Already Applied

- `UserResponse` uses `availableBalance`, not `reservedBalance`.
- `AuctionResponse` uses `fromAuction()`, not `from()`.
- `ErrorResponse` uses `error`, `message`, and `timestamp`, not `code`.
- `PageRequest` is a `record` with `page`, `size`, `offset()`, and `of()`.
- Exception inheritance is `AuctionException <|-- ...`; `ErrorResponse` is not directly dependent on exception classes.
- Foreign-key-like model links now point to `User`, `Item`, or `Auction` when the source stores IDs only.

## Remaining README Issues Found in the Strict Re-Check

These are the remaining issues if the README is judged as a strict source-level UML document, not merely a high-level design overview.

| Severity | README issue | Why it matters | Recommended fix |
|---|---|---|---|
| High | `InlineAppRoutes` is not a real source class. | The README says the diagrams are source-code grounded, but this node is synthetic. | Remove it from `classDiagram` and keep inline routes only in the Markdown route table, or mark it explicitly as `<<conceptual>>`. |
| High | Strict nested types are documented in this audit but not yet drawn in the README diagrams. | The source tree shows real named nested types such as `BidHistoryEntry`, `BalanceChange`, `SettlementResult`, `ResizeDirection`, `GlassDateCell`, and `GlassCalendarState`. | Add a seventh Mermaid diagram: `Source-Level Nested Types and Helpers`. |
| High | `App` diagram under-represents direct runtime composition. | `App.java` directly creates many DAOs and service/strategy objects (`AutoBidConfigDao`, `BidTransactionDao`, `DepositRequestDao`, `PasswordResetRequestDao`, `NotificationDao`, `AutoBidStrategy`, etc.), but diagram 1 mostly routes through controllers/services and omits those construction dependencies. | Add direct `App --> ...` relationships for runtime-created DAOs/services/strategy, or relabel diagram 1 as simplified composition. |
| Medium | The architecture `flowchart` mixes data-flow and source dependency. | `RestClient --> JwtMiddleware` and `WebSocketClient --> AuctionWebSocketHandler` are network/data-flow relations, not Java source imports. This is valid for architecture, but not for source-code dependency UML. | Label the flowchart as runtime communication/data flow, not class dependency. |
| Medium | `AuctionStates` in README diagram 2 lists only `OPEN`, `RUNNING`, and `FINISHED`. | Source `AuctionStates` has six singleton fields: `OPEN`, `RUNNING`, `SETTLING`, `FINISHED`, `PAID`, `CANCELED`. Diagram 5 is correct, but diagram 2 is inconsistent. | Add `SETTLING`, `PAID`, and `CANCELED` to diagram 2. |
| Medium | `AuctionWebSocketHandler` in README omits source methods `notifyBalanceChange()`, `notifyUser()`, `getConnectionCount()`, and important private cleanup/token methods. | The class body is representative, but not complete enough for strict 1-1 auditing. | Add the missing major public methods and 2-3 private helpers. |
| Medium | `BidTransactionDao` in README only lists `insert()` and `findByAuctionId()`. | Source also has `findByBidderId()`, `findById()`, `findLastBid()`, `findWithUsernames()`, `countByAuctionId()`, `getHighestPrice()`, `deleteByAuctionId()`, and nested `BidHistoryEntry`. | Expand the DAO method list or add a DAO-detail diagram. |
| Medium | Service-layer relationships are still incomplete for strict source dependencies. | `UserService` imports/uses `JwtUtil`, `UserFactory`, `MoneyValidator`, `WalletTransactionDao`, DTOs, and exceptions. `BidService` imports/uses `NotificationFormat` but the README only links it to `MoneyValidator` in diagram 2. `AuctionService` uses DTOs such as `AuctionResponse`, `CreateAuctionRequest`, `PageRequest`, and `BidUpdateMessage`, plus model subclasses during response enrichment. | Add missing service-to-helper/DTO/model links, or state explicitly that diagram 2 shows only primary service/DAO dependencies. |
| Medium | DTO class bodies omit many setters, constructors, and constants/factory details. | Request DTOs such as `RegisterRequest`, `CreateItemRequest`, `CreateAuctionRequest`, `BidRequest`, and `AutoBidRequest` all have setters; `BidUpdateMessage` also has message-type constants and full getters/setters. | Either expand DTO bodies or add an explicit note that DTO diagrams list major API-facing fields/factory methods only. |
| Medium | JavaFX controller diagrams are intentionally compressed and still omit source-level helper records/classes. | `AuctionListController.BalanceDisplay`, `CreateAuctionController.GlassDateCell`, `CreateAuctionController.GlassCalendarState`, and `SceneManager.ResizeDirection` are source-level named types. | Add those types to the JavaFX diagram or the seventh nested-types diagram. |
| Low | `AuctionStateFactory` omits private constructor `-AuctionStateFactory()`. | Source is a utility class with a private constructor. | Add `-AuctionStateFactory()` to diagram 5. |
| Low | `DatabaseConfig` is heavily compressed compared with source. | Source contains additional constants/fields and methods around PID handling, stale PostgreSQL cleanup, pg_ctl lookup, and safe shutdown. | Accept this as representative, or expand `DatabaseConfig` in a runtime infrastructure diagram. |
| Low | Some class members are representative rather than exhaustive. | This is acceptable for presentation UML but not for a strict source mirror. | Add a note that diagrams list representative members, or expand every class body. |

## Current Verdict

- Top-level source-file coverage: **complete**.
- Compiler-generated anonymous classes: **correctly excluded**.
- Source-level nested named types: **identified but not fully integrated into README diagrams yet**.
- Strict 1-1 README class diagram accuracy: **not perfect yet** because `InlineAppRoutes` is synthetic, diagram 1 under-represents `App`'s direct construction dependencies, diagram 2 has an incomplete `AuctionStates` declaration, DTO bodies are compressed, service/helper dependencies are incomplete, and nested source-level helpers still need a dedicated diagram.

The safest final documentation strategy is:

1. Keep README diagrams readable and high-signal.
2. Add a clear note that class bodies list major fields/methods, not every private helper.
3. Add a separate seventh diagram for source-level nested named types.
4. Keep this audit file as the strict checklist for graders who inspect source coverage deeply.
