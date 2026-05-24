-- ============================================================
-- Migration V12: Thêm cột reserved_balance vào users
-- Mô tả : Theo dõi số tiền đang bị tạm giữ (freeze) bởi các lượt
--          đặt giá đang dẫn đầu. Cơ chế này ngăn user dùng cùng
--          một số dư khả dụng để dẫn đầu nhiều phiên đấu giá cùng lúc.
--
--         Luồng vận hành hiện tại:
--           - balance là tổng số dư ví đã nạp, chưa trừ tiền giữ chỗ.
--           - reserved_balance là phần balance đang bị khóa bởi các lượt
--             bid đang dẫn đầu.
--           - available_balance = balance - reserved_balance.
--           - Khi user dẫn đầu phiên: reserved_balance += amount (FREEZE).
--           - Khi bị vượt qua:       reserved_balance -= amount (RELEASE).
--           - Khi thắng đấu giá:     balance -= amount và
--                                    reserved_balance -= amount (WIN_CONSUME).
--
--         Bất biến được duy trì:
--           0 <= reserved_balance <= balance
--           available_balance = balance - reserved_balance
-- ============================================================

-- Thêm cột reserved_balance (idempotent — an toàn khi chạy lại)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reserved_balance DECIMAL(15,2) NOT NULL DEFAULT 0;
