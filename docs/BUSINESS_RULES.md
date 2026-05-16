# Business Rules

This document describes the business rules implemented by the current Java source code.

## Roles

| Role | Permissions |
|---|---|
| ADMIN | Approve/reject deposit requests; approve/reject password reset requests; list/delete users except self; soft-cancel `OPEN`/`RUNNING` auctions; hard-delete auctions through the admin route |
| SELLER | Create/edit/delete own items; create/edit own auctions; cancel own auction only while it is `OPEN` |
| BIDDER | Request deposits; place manual bids; create/stop own auto-bid configurations; view personal notifications |

- Roles are mutually exclusive and stored in `users.role`.
- Sellers and Admins cannot place manual bids.
- A seller cannot bid on their own auction even if they somehow reach the bidding service path.

---

## Bidding

### Manual Bid

- Only `BIDDER` accounts may call the manual-bid endpoint.
- The auction must be in `RUNNING` status.
- The bid amount must be a positive integer VND value.
- The bid amount must strictly exceed `auctions.current_price`.
- The current highest bidder (`auctions.leading_bidder_id`) cannot place another bid while they remain the leader.
- A bidder cannot place a manual bid on an auction for which they already have an `ACTIVE` auto-bid config; they must stop the auto-bid first.
- The bidder must have enough available balance for the bid amount.
- When a bid becomes the leader, the previous leader's reserved balance is released and the new leader's bid amount is reserved.

### Seller Self-Bid

- The seller of an auction (`auctions.seller_id`) is blocked from bidding in that auction.
- This is enforced in the running-state bid validation path.

### Anti-Sniping

- If a bid is placed with fewer than 30 seconds remaining, `auctions.end_time` is extended by 60 seconds.
- A `TIME_EXTENDED` WebSocket event is queued and sent after the transaction commits.

---

## Auto-Bid

Auto-bid lets a bidder set a maximum price. The system can place bids on the bidder's behalf according to `increment_amount` and the bidder's available balance.

### Configuration fields

| Field | Meaning |
|---|---|
| `max_bid` | Maximum price the user is willing to pay |
| `increment_amount` | Step size for each automatic bid |
| `active` | Legacy compatibility flag; `status` is the main state field |
| `status` | `ACTIVE` / `STOPPED` / `EXHAUSTED` / `FAILED` |
| `failure_reason` | May contain `MAX_PRICE_TOO_LOW` or `INSUFFICIENT_BALANCE` when the service records a failed/exhausted config. The enum also defines additional values for future/edge states. |
| `registered_at` | FIFO ordering timestamp for the auto-bid chain |

### Rules

- Only one config is stored per `(auction_id, bidder_id)` pair by a UNIQUE constraint.
- Auto-bid can only be created while the auction is `RUNNING`; otherwise the request is rejected.
- The bidder must not already be the current leader; otherwise the request is rejected.
- If the bidder already has an active config for the auction, the request is rejected before creating a second active config.
- On creation, the initial bid amount is `current_price + increment_amount`.
- If the initial bid exceeds `max_bid`, the config is stored as `EXHAUSTED` with `MAX_PRICE_TOO_LOW` and no bid is placed.
- If the bidder's available balance is insufficient for the initial bid, the config is stored as `FAILED` with `INSUFFICIENT_BALANCE` and no bid is placed.
- If the initial bid is valid, it is placed immediately as an auto-bid, the bid is persisted, and the bidder's reserved balance is updated.
- The auto-bid chain runs in the same transaction as the triggering bid path and is capped at 100 auto-bids per trigger.
- Auto-bids are ordered by `registered_at` (FIFO) for deterministic fairness.
- During a chain, configs that can no longer bid above the current price transition to `EXHAUSTED`; configs with insufficient available balance transition to `FAILED`.

---

## Auction Lifecycle

```text
OPEN ──► RUNNING ──► SETTLING ──► PAID
                  └──────────────► FINISHED

OPEN ──► CANCELED       seller or admin soft-cancel
RUNNING ──► CANCELED    admin soft-cancel only
```

Admin hard-delete is a separate route that removes the auction row and related `wallet_transactions`, `auto_bid_configs`, and `bid_transactions`; it is not a status transition.

### Status reference

| Status | Stored value | Plain-English meaning | Terminal? |
|---|---|---|---|
| `OPEN` | `"OPEN"` | Created, not yet started. `start_time` has not been reached. Bids are not accepted. Seller may still edit or cancel. | No |
| `RUNNING` | `"RUNNING"` | Actively accepting bids. Scheduler transitions from `OPEN` once `start_time <= now`. | No |
| `SETTLING` | `"SETTLING"` | Settlement is being claimed and processed by the scheduler. Client-facing operations are blocked by state rules. | No |
| `PAID` | `"PAID"` | Successful settlement: winner's funds are deducted and seller is credited. | Yes |
| `FINISHED` | `"FINISHED"` | Ended without a completed sale: either no bids existed, or the leading bidder could not complete settlement. | Yes |
| `CANCELED` | `"CANCELED"` | Soft-cancelled auction. Seller can reach this only from `OPEN`; admin can soft-cancel `OPEN` or `RUNNING`. | Yes |

> **Common confusion**
>
> - `OPEN` does **not** mean "open for bidding." It means the auction exists but `start_time` is in the future. Use `RUNNING` to check whether bids are accepted.
> - `FINISHED` does **not** mean "successfully paid." A completed sale ends in `PAID`.
> - The enum value in code is `CANCELED` (with one L and a D), not `CANCEL`.

### Cancel / delete rules

- A seller may soft-cancel only their own auction while it is `OPEN`.
- A seller cannot cancel a `RUNNING` auction.
- An admin may soft-cancel an `OPEN` or `RUNNING` auction.
- For ended/canceled auctions, admin hard-delete is handled through the admin delete route rather than by changing status again.
- When a `RUNNING` auction with a leader is soft-cancelled, the leader's reserved balance is released with a `CANCEL_RELEASE` wallet transaction.

### One auction per item

- Auction creation requires the item to belong to the seller.
- Auction creation requires the item status to be `AVAILABLE`.
- Creating an auction marks the item as `IN_AUCTION`.
- The service also rejects creation when an item already has an `OPEN` or `RUNNING` auction, or when a paid auction already exists for that item.

---

## Wallet & Reserved Balance

Each user has two balance fields:

| Field | Meaning |
|---|---|
| `balance` | Total wallet balance recorded for the user |
| `reserved_balance` | Portion of the balance currently locked by active leading bids |

**Effective available balance = `balance - reserved_balance`**

Balance movements are recorded in the `wallet_transactions` ledger:

| `kind` | Trigger | Effect |
|---|---|---|
| `DEPOSIT` | Admin approves deposit request | `balance += amount` |
| `FREEZE` | Bid is placed and user becomes leader | `reserved_balance += amount` |
| `RELEASE` | User is outbid, or settlement fails because the leader cannot pay | `reserved_balance -= amount` |
| `WIN_CONSUME` | Settlement — winner pays | `balance -= amount`, `reserved_balance -= amount` |
| `SELLER_PAYOUT` | Settlement — seller receives proceeds | seller `balance += amount` |
| `CANCEL_RELEASE` | Running auction is cancelled with a leading bidder | `reserved_balance -= amount` |

---

## Settlement

The scheduler periodically processes auctions whose `end_time` has passed:

1. The auction is atomically claimed by moving `RUNNING -> SETTLING` if it is still due for settlement.
2. If there are no bids, the auction becomes `FINISHED`; no money moves.
3. If a winner exists, the scheduler checks the winner's wallet balance and reserved balance path:
   - **Sufficient funds / reservation:** winner is charged (`WIN_CONSUME`), seller is credited (`SELLER_PAYOUT`), and the auction becomes `PAID`.
   - **Insufficient funds:** the winner's reserved balance is released (`RELEASE`) and the auction becomes `FINISHED`.
4. Result notifications are inserted in the same settlement transaction and pushed to online users after the transaction completes.

---

## Notifications

| Type | Trigger |
|---|---|
| `OUTBID` | User was outbid |
| `SELLER_BID_RECEIVED` | Seller's auction received a manual or auto bid |
| `AUTOBID_FAILED` | Auto-bid failed because of insufficient balance |
| `AUTOBID_EXHAUSTED` | Auto-bid could not continue because the next bid would exceed `max_bid` |
| `AUCTION_RESULT` | Auction ended; sent to the seller and distinct bidders for the auction result |
| `AUCTION_WON` | Winner successfully paid during settlement |
| `SELLER_PAYOUT` | Seller received the settlement payout |
| `AUCTION_CANCELED` | Auction was soft-cancelled |
| `AUCTION_DELETED` | Auction was hard-deleted by admin |
| `BALANCE_UPDATED` | Deposit request was approved or rejected |

- Each notification has an `is_read` flag (default `false`).
- Users can mark one notification or all their notifications as read.
- Online users receive real-time messages through `/ws/user/{id}`.
- Persisted notification rows allow offline users to see history when they reconnect or reload notifications.
- Password reset approval currently returns the generated temporary password in the admin response; it does not insert a user notification row.

---

## Bid History & Charts

- Every normal bid path inserts a `bid_transactions` row for manual bids and auto-bids.
- `bid_transactions.auto_bid` distinguishes system-placed bids from user-initiated bids.
- The full history is retrievable via `BidService.getBidHistory(auctionId)` and is used by the UI for price-over-time charts.
- Bid history rows are not updated by normal bidding flow. Admin hard-delete of an auction removes related bid rows as part of the cleanup required by foreign-key constraints.
