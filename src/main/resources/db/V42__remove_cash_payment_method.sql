ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check CHECK (payment_method IN ('MPESA', 'CARD'));

ALTER TABLE payment_records DROP CONSTRAINT IF EXISTS payment_records_payment_method_check;
ALTER TABLE payment_records ADD CONSTRAINT payment_records_payment_method_check CHECK (payment_method IN ('MPESA', 'CARD'));
