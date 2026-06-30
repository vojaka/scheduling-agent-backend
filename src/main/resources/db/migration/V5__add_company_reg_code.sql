-- Add reg_code to companies table
ALTER TABLE companies ADD COLUMN IF NOT EXISTS reg_code TEXT;
