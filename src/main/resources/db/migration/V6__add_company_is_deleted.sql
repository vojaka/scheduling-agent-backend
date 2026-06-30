-- Add is_deleted to companies table
ALTER TABLE companies ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false;
