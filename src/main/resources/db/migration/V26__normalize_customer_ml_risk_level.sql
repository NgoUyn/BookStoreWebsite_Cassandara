-- ============================================================================
-- V26: Chuẩn hoá risk_level trong bảng customer_ml
-- ============================================================================
-- Lý do: Python ML API trả về risk_level dạng "CRITICAL (Nguy cấp)" nhưng
-- frontend chỉ hiểu dạng code thuần (LOW/MEDIUM/HIGH/CRITICAL).
-- Migration này clean up dữ liệu cũ để đồng bộ với code mới.
-- ============================================================================

-- Chuẩn hoá: bỏ phần tiếng Việt trong ngoặc đơn
UPDATE customer_ml
SET risk_level = 'LOW'
WHERE risk_level LIKE 'LOW%' AND risk_level != 'LOW';

UPDATE customer_ml
SET risk_level = 'MEDIUM'
WHERE risk_level LIKE 'MEDIUM%' AND risk_level != 'MEDIUM';

UPDATE customer_ml
SET risk_level = 'HIGH'
WHERE risk_level LIKE 'HIGH%' AND risk_level != 'HIGH';

UPDATE customer_ml
SET risk_level = 'CRITICAL'
WHERE risk_level LIKE 'CRITICAL%' AND risk_level != 'CRITICAL';

PRINT 'Đã chuẩn hoá risk_level trong bảng customer_ml';
GO

-- ============================================================================
-- END V26
-- ============================================================================
