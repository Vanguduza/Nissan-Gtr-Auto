-- Retired (owner decision 2026-10-02: no demo vehicles or demo stock in any environment).
--
-- This migration came with the locked customer-app lineage and seeded demo vehicles
-- (Navara D40, X-Trail T31, GT-R R35, Almera N16) with demo stocked parts so the old
-- customer cascade had data. The apps now read the published full catalogue
-- (list_customer_vehicle_master over catalog_r2_vehicle_master) and real shop stock, so the
-- seed is no longer applied anywhere. It was never applied on the hosted project. Kept as a
-- no-op so the migration history stays continuous.

SELECT 1;
