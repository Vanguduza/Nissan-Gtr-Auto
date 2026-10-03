-- Exported from the hosted project's supabase_migrations.schema_migrations (20260904115025 seed_coa_warehouses).
-- Source of record for what production ran; see supabase/live-history/README.md.

INSERT INTO public.chart_of_accounts (code, name, account_type) VALUES
('1100','Cash & Bank','asset'),('1200','Accounts Receivable','asset'),('1300','Inventory (Main Store)','asset'),('1310','Inventory (Quarantine)','asset'),('2100','Accounts Payable','liability'),('2200','Customer Deposits / Store Credit','liability'),('3100','Owner''s Equity','equity'),('4100','Parts Sales Revenue','income'),('4110','Sales Returns & Allowances','income'),('4200','Core Charge Revenue','income'),('5100','Cost of Goods Sold','expense'),('5200','Payroll Expense','expense'),('5300','Operating Expenses','expense') ON CONFLICT (code) DO NOTHING;
INSERT INTO public.warehouses (code,name,is_quarantine) VALUES ('MAIN','Main Store',false),('QUAR','Quarantine',true) ON CONFLICT (code) DO NOTHING;
