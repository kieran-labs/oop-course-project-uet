-- ============================================================
-- Migration V1: Initial Schema
-- Mô tả : Tạo toàn bộ cấu trúc bảng cốt lõi cho hệ thống đấu giá.
--          Bao gồm: người dùng, mặt hàng, phiên đấu giá,
--          lịch sử đặt giá và cấu hình đặt giá tự động.
-- ============================================================

-- ------------------------------------------------------------
-- Bảng users: Lưu thông tin tài khoản người dùng.
-- role phân biệt 3 loại: BIDDER (người đặt giá), SELLER (người bán),
-- ADMIN (quản trị viên hệ thống).
-- ------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,           -- Mật khẩu đã được hash (bcrypt)
    email           VARCHAR(100) UNIQUE NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('BIDDER', 'SELLER', 'ADMIN')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Bảng items: Lưu thông tin mặt hàng đưa ra đấu giá.
-- Mỗi mặt hàng thuộc về một seller (người bán).
-- Các trường brand, artist, year là tuỳ chọn, dùng tuỳ theo category.
--   - ELECTRONICS: dùng brand + year
--   - ART        : dùng artist + year
--   - VEHICLE    : dùng brand + year
-- ------------------------------------------------------------
CREATE TABLE items (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('ELECTRONICS', 'ART', 'VEHICLE')),
    brand           VARCHAR(100),                    -- Dùng cho ELECTRONICS, VEHICLE
    artist          VARCHAR(100),                    -- Dùng cho ART
    year            INTEGER,                         -- Năm sản xuất / sáng tác
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Bảng auctions: Quản lý các phiên đấu giá.
-- Vòng đời trạng thái:
--   OPEN → RUNNING → FINISHED → PAID
--                 ↘ CANCELED
-- current_price được cập nhật mỗi khi có bid mới thắng.
-- leading_bidder_id trỏ đến người đang dẫn đầu (NULL nếu chưa có bid nào).
-- ------------------------------------------------------------
CREATE TABLE auctions (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             BIGINT NOT NULL REFERENCES items(id),
    starting_price      DECIMAL(15,2) NOT NULL,
    current_price       DECIMAL(15,2) NOT NULL,      -- Giá hiện tại cao nhất
    leading_bidder_id   BIGINT REFERENCES users(id), -- NULL nếu chưa có ai đặt giá
    start_time          TIMESTAMP NOT NULL,
    end_time            TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Bảng bid_transactions: Lịch sử toàn bộ lượt đặt giá.
-- Mỗi bản ghi là một lần đặt giá của một user trong một phiên đấu giá.
-- auto_bid = TRUE nghĩa là lượt đặt này được kích hoạt tự động
-- bởi hệ thống (dựa trên auto_bid_configs), không phải do người dùng thao tác trực tiếp.
-- ------------------------------------------------------------
CREATE TABLE bid_transactions (
    id              BIGSERIAL PRIMARY KEY,
    auction_id      BIGINT NOT NULL REFERENCES auctions(id),
    bidder_id       BIGINT NOT NULL REFERENCES users(id),
    amount          DECIMAL(15,2) NOT NULL,
    auto_bid        BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE: đặt giá tự động
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Bảng auto_bid_configs: Cấu hình đặt giá tự động theo từng phiên.
-- Mỗi user chỉ được có một cấu hình duy nhất cho mỗi phiên đấu giá (UNIQUE).
-- Hệ thống sẽ tự động đặt giá thay user (tăng thêm increment_amount mỗi lần)
-- cho đến khi đạt max_bid hoặc thắng đấu giá.
-- active = FALSE: cấu hình bị vô hiệu hoá (user đã tắt hoặc đã đạt max_bid).
-- ------------------------------------------------------------
CREATE TABLE auto_bid_configs (
    id                  BIGSERIAL PRIMARY KEY,
    auction_id          BIGINT NOT NULL REFERENCES auctions(id),
    bidder_id           BIGINT NOT NULL REFERENCES users(id),
    max_bid             DECIMAL(15,2) NOT NULL,       -- Mức giá tối đa user chịu trả
    increment_amount    DECIMAL(15,2) NOT NULL,       -- Bước giá tăng mỗi lần auto-bid
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    registered_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (auction_id, bidder_id)                    -- Mỗi user chỉ có 1 config/phiên
);

-- ------------------------------------------------------------
-- Indexes: Tăng tốc các truy vấn phổ biến
-- ------------------------------------------------------------
CREATE INDEX idx_auctions_status        ON auctions(status);          -- Lọc phiên theo trạng thái
CREATE INDEX idx_bid_transactions_auction ON bid_transactions(auction_id); -- Lịch sử bid theo phiên
CREATE INDEX idx_items_seller           ON items(seller_id);          -- Hàng hoá theo seller
