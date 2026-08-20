-- Add voucher fields to sub_orders table
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sub_orders') AND name = 'voucher_code')
BEGIN
    ALTER TABLE sub_orders ADD voucher_code NVARCHAR(50) NULL;
END;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sub_orders') AND name = 'voucher_discount')
BEGIN
    ALTER TABLE sub_orders ADD voucher_discount FLOAT DEFAULT 0;
END;

-- Add original_total_amount to orders_master
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('orders_master') AND name = 'original_total_amount')
BEGIN
    ALTER TABLE orders_master ADD original_total_amount FLOAT NULL;
END;
