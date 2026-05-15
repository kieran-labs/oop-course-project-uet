package com.auction.util;

import java.math.BigDecimal;

/** Validation helpers for VND amounts, which are represented as whole-number money values. */
public final class MoneyValidator {

  private MoneyValidator() {}

  public static void requirePositiveIntegerVnd(BigDecimal amount, String fieldName) {
    if (amount == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than 0 VND");
    }
    if (!isIntegerVnd(amount)) {
      throw new IllegalArgumentException(fieldName + " must be an integer VND amount");
    }
  }

  public static boolean isIntegerVnd(BigDecimal amount) {
    return amount != null && amount.stripTrailingZeros().scale() <= 0;
  }

  public static long toIntegerVndExact(BigDecimal amount, String fieldName) {
    if (amount == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (!isIntegerVnd(amount)) {
      throw new IllegalArgumentException(fieldName + " must be an integer VND amount");
    }
    return amount.stripTrailingZeros().longValueExact();
  }
}
