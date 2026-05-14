package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.auction.config.DatabaseConfig;
import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dao.UserDao;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.pattern.observer.AuctionEventManager;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionSchedulerSettlementTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;
  private static AuctionDao auctionDao;
  private static AuctionScheduler scheduler;

  private User seller;
  private User bidder;

  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }

    userDao = new UserDao(jdbi);
    itemDao = new ItemDao(jdbi);
    auctionDao = new AuctionDao(jdbi);
    scheduler = new AuctionScheduler(auctionDao, userDao, itemDao, new AuctionEventManager(), jdbi);
  }

  @BeforeEach
  void init() {
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    seller = userDao.insert(new Seller("scheduler_seller", "hash", "seller@test.com"));

    Bidder testBidder = new Bidder("scheduler_bidder", "hash", "bidder@test.com");
    testBidder.setBalance(new BigDecimal("500000"));
    bidder = userDao.insert(testBidder);
  }

  @Test
  @DisplayName("Settlement fallback chỉ release đúng reservation của phiên hiện tại")
  void insufficientBalanceFallbackOnlyReleasesCurrentAuctionReservation() throws Exception {
    reserveBidderBalance(new BigDecimal("1000000"));
    Auction auctionA = createRunningAuction("Item A", new BigDecimal("600000"));
    Auction auctionB = createRunningAuction("Item B", new BigDecimal("400000"));

    invokeSettleAndClose(auctionA);

    User foundBidder = userDao.findById(bidder.getId()).orElseThrow();
    Auction foundAuctionA = auctionDao.findById(auctionA.getId()).orElseThrow();
    Auction foundAuctionB = auctionDao.findById(auctionB.getId()).orElseThrow();

    assertEquals(0, new BigDecimal("400000").compareTo(foundBidder.getReservedBalance()));
    assertEquals(AuctionStatus.FINISHED, foundAuctionA.getStatus());
    assertEquals(AuctionStatus.RUNNING, foundAuctionB.getStatus());
  }

  private void reserveBidderBalance(BigDecimal amount) {
    jdbi.useTransaction(
        handle -> userDao.updateReservedBalanceInTransaction(handle, bidder.getId(), amount));
  }

  private Auction createRunningAuction(String itemName, BigDecimal currentPrice) {
    Item item = itemDao.insert(new Item(itemName, "Settlement test item", seller.getId(), "ART"));
    Auction auction =
        new Auction(
            item.getId(),
            currentPrice,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusMinutes(1));
    auction.setSellerId(seller.getId());
    auction.setCurrentPrice(currentPrice);
    auction.setLeadingBidderId(bidder.getId());
    auction.setStatus(AuctionStatus.RUNNING);
    return auctionDao.insert(auction);
  }

  private void invokeSettleAndClose(Auction auction) throws Exception {
    Method settleAndClose =
        AuctionScheduler.class.getDeclaredMethod("settleAndClose", Auction.class);
    settleAndClose.setAccessible(true);
    settleAndClose.invoke(scheduler, auction);
  }
}
