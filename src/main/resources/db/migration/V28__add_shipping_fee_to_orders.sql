-- Add shipping_fee to orders_master
IF COL_LENGTH('orders_master','shipping_fee') IS NULL
BEGIN
ALTER TABLE orders_master ADD shipping_fee FLOAT NOT NULL CONSTRAINT DF_orders_shipping_fee DEFAULT 0;
END;