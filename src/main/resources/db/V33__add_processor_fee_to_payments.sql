-- Add processor fee tracking to payments table
-- Records the fee charged by payment gateways (M-Pesa, card processors, PayPal)
ALTER TABLE payments ADD COLUMN processor_fee DECIMAL(10,2) DEFAULT 0;
ALTER TABLE payments ADD COLUMN processor_fee_percentage DECIMAL(5,2) DEFAULT 0;
