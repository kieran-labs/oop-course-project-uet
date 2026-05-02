package com.auction.pattern.observer;

import com.auction.dto.BidUpdateMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer Pattern - Subject (Trạm phát sóng). Quản lý danh sách người nghe theo từng phiên đấu
 * giá.
 */
public class AuctionEventManager {
  // Dùng cấu trúc dữ liệu an toàn cho đa luồng (Thread-safe)
  private final Map<Long, List<AuctionEventListener>> listeners = new ConcurrentHashMap<>();

  // Đăng ký nghe một phiên cụ thể
  public void subscribe(Long auctionId, AuctionEventListener listener) {
    listeners.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>()).add(listener);
  }

  // Bỏ đăng ký
  public void unsubscribe(Long auctionId, AuctionEventListener listener) {
    List<AuctionEventListener> list = listeners.get(auctionId);
    if (list != null) {
      list.remove(listener);
    }
  }

  // Phát thông báo có người đặt giá mới
  public void notifyBidUpdate(Long auctionId, BidUpdateMessage msg) {
    List<AuctionEventListener> list = listeners.get(auctionId);
    if (list != null) {
      for (AuctionEventListener listener : list) {
        listener.onBidUpdate(auctionId, msg);
      }
    }
  }

  // Phát thông báo gia hạn thời gian (Anti-sniping)
  public void notifyTimeExtended(Long auctionId, BidUpdateMessage msg) {
    List<AuctionEventListener> list = listeners.get(auctionId);
    if (list != null) {
      for (AuctionEventListener listener : list) {
        listener.onTimeExtended(auctionId, msg);
      }
    }
  }
}
