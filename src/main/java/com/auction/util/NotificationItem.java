package com.auction.util;

import java.time.LocalDateTime;

/**
 * Immutable value object representing a single notification in the client store.
 *
 * <p>{@code id} is null for client-only (WebSocket-generated) notifications that have not been
 * persisted to the server's {@code notifications} table. Server-loaded notifications always carry a
 * non-null {@code id}.
 *
 * <p>{@code read} is the only mutable field — it is flipped to {@code true} when the store receives
 * confirmation from the server that the notification has been marked read.
 */
public class NotificationItem {

  private final Long id;
  private final String message;
  private final String type;
  private boolean read;
  private final LocalDateTime createdAt;

  public NotificationItem(
      Long id, String message, String type, boolean read, LocalDateTime createdAt) {
    this.id = id;
    this.message = message;
    this.type = type;
    this.read = read;
    this.createdAt = createdAt;
  }

  /** Creates a client-only notification (no server id, unread, timestamped now). */
  public static NotificationItem clientOnly(String message) {
    return new NotificationItem(null, message, null, false, LocalDateTime.now());
  }

  public Long getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }

  public String getType() {
    return type;
  }

  public boolean isRead() {
    return read;
  }

  public void setRead(boolean read) {
    this.read = read;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
