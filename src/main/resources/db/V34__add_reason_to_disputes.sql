ALTER TABLE disputes ADD COLUMN reason TEXT;

UPDATE disputes SET reason = 'unspecified' WHERE reason IS NULL;

ALTER TABLE disputes ALTER COLUMN reason SET NOT NULL;
