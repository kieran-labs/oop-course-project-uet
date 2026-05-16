package com.auction.util;

/**
 * Helper to wrap dynamic entity references inside notification text with delimiters the JavaFX
 * client recognises and colour-codes.
 *
 * <ul>
 *   <li>Usernames are wrapped in U+00AB / U+00BB ({@code «alice»}) and rendered in blue.
 *   <li>Auction display names are wrapped in {@code [...]} (square brackets) and rendered in brown.
 * </ul>
 *
 * <p>Both delimiters are safe because they never appear in legitimate Vietnamese product text or
 * usernames (validated at registration), and the same convention is recognised by the offline
 * notifications loaded from the server.
 */
public final class NotificationFormat {

  /** Opening guillemet that flags the start of a user name segment. */
  public static final char USER_OPEN = '«';

  /** Closing guillemet that flags the end of a user name segment. */
  public static final char USER_CLOSE = '»';

  private NotificationFormat() {}

  /** Wrap a username so the client renders it as a blue chip. */
  public static String user(String username) {
    String safe = username != null && !username.isBlank() ? username : "Người dùng";
    return USER_OPEN + safe + USER_CLOSE;
  }

  /**
   * Wrap an auction's display name (item name) so the client renders it as a brown chip.
   *
   * <p>Important UI contract: auction/session names are always displayed in square brackets. If the
   * real item name is unknown, the fallback ID is still wrapped, e.g. {@code [#4]}. Never return a
   * bare {@code #4}, otherwise the notification renderer may mistake {@code #4 VND} for a money
   * amount.
   */
  public static String auctionName(Long auctionId, String itemName) {
    String safe = itemName != null && !itemName.isBlank() ? itemName.trim() : "#" + auctionId;
    return "[" + safe + "]";
  }
}
