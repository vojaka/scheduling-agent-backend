-- Drop the old constraint if it exists. By default it is named offerings_status_check.
ALTER TABLE offerings DROP CONSTRAINT IF EXISTS offerings_status_check;

-- Add the new check constraint to support Active, Inactive, Draft, and Archive.
ALTER TABLE offerings ADD CONSTRAINT offerings_status_check CHECK (status IN ('Active', 'Inactive', 'Draft', 'Archive'));
