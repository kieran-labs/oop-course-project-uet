-- ============================================================
-- Migration V3: Thêm cột balance vào bảng users
-- Mô tả : Bổ sung số dư tài khoản (ví nội bộ) cho người dùng.
--          Số dư được dùng để thanh toán khi thắng đấu giá
--          và được nạp thông qua deposit_requests (V4).
-- Mặc định: 0 — tất cả tài khoản hiện tại bắt đầu với số dư trống.
-- ============================================================

ALTER TABLE users
    ADD COLUMN balance DECIMAL(15,2) NOT NULL DEFAULT 0;  -- Đơn vị: VND (hoặc đơn vị tiền tệ hệ thống)
