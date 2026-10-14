-- ==============================================================================
-- SMART EXPENSES TRACKER - SEED DATA FOR SYSTEM CATEGORIES & DEMO
-- ==============================================================================

-- Shared System Expense Categories
INSERT INTO categories (id, user_id, name, type, icon_key, color_hex) VALUES
('c0000001-0000-0000-0000-000000000001', NULL, 'Food & Dining', 'expense', 'ic_food', '#EF4444'),
('c0000002-0000-0000-0000-000000000002', NULL, 'Travel & Transport', 'expense', 'ic_travel', '#F59E0B'),
('c0000003-0000-0000-0000-000000000003', NULL, 'Shopping', 'expense', 'ic_shopping', '#3B82F6'),
('c0000004-0000-0000-0000-000000000004', NULL, 'Bills & Utilities', 'expense', 'ic_bills', '#8B5CF6'),
('c0000005-0000-0000-0000-000000000005', NULL, 'Entertainment', 'expense', 'ic_entertainment', '#EC4899'),
('c0000006-0000-0000-0000-000000000006', NULL, 'Health & Medical', 'expense', 'ic_health', '#10B981'),
('c0000007-0000-0000-0000-000000000007', NULL, 'Others', 'expense', 'ic_other', '#6B7280')
ON CONFLICT (id) DO NOTHING;

-- Shared System Income Categories
INSERT INTO categories (id, user_id, name, type, icon_key, color_hex) VALUES
('c0000008-0000-0000-0000-000000000008', NULL, 'Salary', 'income', 'ic_salary', '#10B981'),
('c0000009-0000-0000-0000-000000000009', NULL, 'Freelance / Business', 'income', 'ic_freelance', '#06B6D4'),
('c0000010-0000-0000-0000-000000000010', NULL, 'Gifts & Rewards', 'income', 'ic_gift', '#84CC16'),
('c0000011-0000-0000-0000-000000000011', NULL, 'Investment Return', 'income', 'ic_investment', '#14B8A6')
ON CONFLICT (id) DO NOTHING;
