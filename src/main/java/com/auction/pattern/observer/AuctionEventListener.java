package com.auction.pattern.observer;

import com.auction.dto.BidUpdateMessage;

/**
 * Observer Pattern - Giao diện Lắng nghe sự kiện. Các thành phần muốn nhận thông báo realtime (như
 * WebSocket) sẽ implement interface này.
 */
public interface AuctionEventListener {
  void onBidUpdate(Long auctionId, BidUpdateMessage msg);

  void onTimeExtended(Long auctionId, BidUpdateMessage msg);

  void onAuctionEnd(Long auctionId, BidUpdateMessage msg);
}
