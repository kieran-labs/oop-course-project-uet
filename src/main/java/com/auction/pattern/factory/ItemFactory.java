package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.*;

public class ItemFactory {
  public static Item create(CreateItemRequest req, Long sellerId) {
    switch (req.getCategory().toUpperCase()) {
      case "ELECTRONICS":
        return new Electronics(
            req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "ART":
        return new Art(req.getName(), req.getDescription(), sellerId, req.getCategoryDetail());
      case "VEHICLE":
        int year = Integer.parseInt(req.getCategoryDetail());
        return new Vehicle(req.getName(), req.getDescription(), sellerId, year);
      default:
        throw new IllegalArgumentException(("Loại sản phẩm không được hỗ trợ" + req.getCategory()));
    }
  }
}
