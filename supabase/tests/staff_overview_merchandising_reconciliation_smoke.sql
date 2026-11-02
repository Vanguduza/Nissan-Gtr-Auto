-- Native RPC authority, finance-only search, home-rail uniqueness and pin/carousel independence.
BEGIN;
SELECT set_config('request.jwt.claim.role','service_role',true);
SELECT set_config('request.jwt.claims','{"role":"service_role"}',true);

CREATE OR REPLACE FUNCTION public._test_staff_overview_auth(p_uid UUID,p_role TEXT DEFAULT 'authenticated')
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub',COALESCE(p_uid::text,''),true);
  PERFORM set_config('request.jwt.claim.role',p_role,true);
  PERFORM set_config('request.jwt.claims',json_build_object('sub',p_uid::text,'role',p_role)::text,true);
END;
$$;

DO $$
DECLARE
  v_fin UUID:='f3333333-3333-4333-8333-333333333331';
  v_sales UUID:='f3333333-3333-4333-8333-333333333332';
  v_warehouse UUID:='f3333333-3333-4333-8333-333333333333';
  v_admin UUID:='f3333333-3333-4333-8333-333333333334';
  v_customer UUID:='f3333333-3333-4333-8333-333333333335';
  v_grade UUID;v_manager_grade UUID;v_hr_role UUID;v_user UUID;v_modules JSONB;v_wh UUID;
  v_item UUID;v_unpriced UUID;v_default UUID;v_retail UUID;v_invoice UUID;v_journal UUID;
  v_result JSONB;v_hits JSONB;v_denied BOOLEAN;v_count INT;v_case INT;
BEGIN
  INSERT INTO auth.users(id,aud,role,email,raw_app_meta_data,raw_user_meta_data,created_at,updated_at)
  VALUES(v_fin,'authenticated','authenticated','overview-finance@gtr.local','{}','{}',now(),now()),
    (v_sales,'authenticated','authenticated','overview-sales@gtr.local','{}','{}',now(),now()),
    (v_warehouse,'authenticated','authenticated','overview-warehouse@gtr.local','{}','{}',now(),now()),
    (v_admin,'authenticated','authenticated','overview-admin@gtr.local','{}','{}',now(),now()),
    (v_customer,'authenticated','authenticated','overview-customer@gtr.local','{}','{}',now(),now());
  INSERT INTO public.profiles(id,full_name,is_staff)
  VALUES(v_fin,'Overview Finance',true),(v_sales,'Overview Sales',true),(v_warehouse,'Overview Warehouse',true),
    (v_admin,'Overview Admin',true),(v_customer,'Overview Customer',false)
  ON CONFLICT(id) DO UPDATE SET is_staff=EXCLUDED.is_staff;
  INSERT INTO public.staff_roles(user_id,role) VALUES(v_fin,'finance'),(v_sales,'sales'),(v_warehouse,'warehouse'),(v_admin,'admin');
  SELECT id INTO v_grade FROM public.hr_grades WHERE code='C1';
  IF v_grade IS NULL THEN
    INSERT INTO public.hr_grades(code,title,sort_order) VALUES('C1','Counter staff',40) RETURNING id INTO v_grade;
  END IF;
  FOR v_user,v_modules IN SELECT * FROM (VALUES(v_fin,'["finance"]'::jsonb),(v_sales,'["pos"]'::jsonb),(v_warehouse,'["warehouse"]'::jsonb)) x(uid,modules)
  LOOP
    INSERT INTO public.hr_roles(title,grade_id,module_access,is_active) VALUES('Overview '||v_user::text,v_grade,v_modules,true) RETURNING id INTO v_hr_role;
    INSERT INTO public.employees(user_id,employee_code,full_name,grade_id,hr_role_id,status)
      VALUES(v_user,'REC-OV-'||right(v_user::text,3),'Overview Employee',v_grade,v_hr_role,'active');
  END LOOP;
  SELECT id INTO STRICT v_wh FROM public.warehouses WHERE is_active AND NOT is_quarantine ORDER BY code LIMIT 1;
  v_journal:=public.post_journal_entry(CURRENT_DATE,'Overview rollback fixture','USD',1,
    '[{"account_code":"1100","debit":1,"credit":0},{"account_code":"5300","debit":0,"credit":1}]'::jsonb);
  INSERT INTO public.sales_invoices(warehouse_id,doc_type,status,document_number,currency,exchange_rate_applied,subtotal,total,amount_paid,journal_entry_id,posted_at)
  VALUES(v_wh,'invoice','posted','REC-OV-INVOICE-UNIQUE','USD',1,1,1,1,v_journal,now()) RETURNING id INTO v_invoice;

  PERFORM public._test_staff_overview_auth(v_fin);
  v_result:=public.get_staff_overview_dashboard(now()-interval '1 day',now()+interval '1 day');
  IF (v_result#>>'{permissions,finance}')::boolean IS DISTINCT FROM true
     OR (v_result#>>'{permissions,customers}')::boolean IS DISTINCT FROM false
     OR (v_result#>>'{permissions,inventory}')::boolean IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'finance-only module isolation wrong: %',v_result->'permissions';
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(v_result->'sales_trend') point WHERE point->>'date'!~ '^\d{4}-\d{2}-\d{2}$') THEN
    RAISE EXCEPTION 'sales trend date is not a civil business date';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(v_result->'recent_orders') ord WHERE ord->>'id'=v_invoice::text AND ord->>'journal_entry_id'=v_journal::text) THEN
    RAISE EXCEPTION 'recent invoice is missing its actual journal destination';
  END IF;
  v_hits:=public.search_staff_overview('REC-OV-INVOICE-UNIQUE',12);
  IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(v_hits) h WHERE h->>'kind'='order'
      AND h->>'destination'='/staff/finance/transactions/'||v_journal::text) THEN
    RAISE EXCEPTION 'finance-only user cannot search authorized invoice: %',v_hits;
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(v_hits) h WHERE h->>'kind'='customer') THEN RAISE EXCEPTION 'CRM data leaked into finance-only search'; END IF;

  PERFORM public._test_staff_overview_auth(v_sales);
  IF public.is_pos_approver() THEN RAISE EXCEPTION 'cashier fixture unexpectedly has manager approval authority'; END IF;
  v_result:=public.get_staff_overview_dashboard(now()-interval '1 day',now()+interval '1 day');
  IF (v_result#>>'{permissions,commercial}')::boolean IS DISTINCT FROM false OR (v_result#>>'{permissions,finance}')::boolean IS DISTINCT FROM false
     OR v_result#>'{summary,sales}'<>'null'::jsonb OR jsonb_array_length(v_result->'recent_orders')<>0 THEN
    RAISE EXCEPTION 'cashier acquired commercial/financial overview: %',v_result;
  END IF;
  v_denied:=false;
  BEGIN PERFORM public.list_staff_product_pages(NULL,10); EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'sales with POS-only modules acquired CRM merchandising'; END IF;
  SELECT id INTO v_manager_grade FROM public.hr_grades WHERE code='A1';
  IF v_manager_grade IS NULL THEN
    INSERT INTO public.hr_grades(code,title,sort_order) VALUES('A1','Senior manager',1) RETURNING id INTO v_manager_grade;
  END IF;
  UPDATE public.employees SET grade_id=v_manager_grade WHERE user_id=v_sales;
  UPDATE public.hr_roles SET module_access='["analytics"]'::jsonb WHERE id=(SELECT hr_role_id FROM public.employees WHERE user_id=v_sales);
  IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'analytics manager fixture lacks approval authority'; END IF;
  v_result:=public.get_staff_overview_dashboard(now()-interval '1 day',now()+interval '1 day');
  IF (v_result#>>'{permissions,commercial}')::boolean IS DISTINCT FROM true
     OR (v_result#>>'{permissions,finance}')::boolean IS DISTINCT FROM false
     OR jsonb_array_length(v_result->'sales_trend')=0 THEN
    RAISE EXCEPTION 'assigned analytics manager cannot read commercial overview without finance authority';
  END IF;

  PERFORM public._test_staff_overview_auth(v_warehouse);
  v_result:=public.get_staff_overview_dashboard(now()-interval '1 day',now()+interval '1 day');
  IF (v_result#>>'{permissions,inventory}')::boolean IS DISTINCT FROM true
     OR (v_result#>>'{permissions,commercial}')::boolean IS DISTINCT FROM false
     OR (v_result#>>'{permissions,finance}')::boolean IS DISTINCT FROM false THEN RAISE EXCEPTION 'warehouse overview authority wrong'; END IF;

  PERFORM public._test_staff_overview_auth(v_admin);
  FOR v_case IN 1..2 LOOP
    v_denied:=false;
    BEGIN
      IF v_case=1 THEN PERFORM public.get_staff_overview_dashboard(now(),now()-interval '1 hour');
      ELSE PERFORM public.get_staff_overview_dashboard(now()-interval '367 days',now()); END IF;
    EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
    IF NOT v_denied THEN RAISE EXCEPTION 'invalid overview interval accepted, case %',v_case; END IF;
  END LOOP;
  INSERT INTO public.stock_items(oem_part_number,description) VALUES('REC-OV-PIN-1','Recovery service filter') RETURNING id INTO v_item;
  INSERT INTO public.stock_items(oem_part_number,description) VALUES('REC-OV-PIN-2','Recovery unpriced item') RETURNING id INTO v_unpriced;
  INSERT INTO public.stock_levels(stock_item_id,warehouse_id,quantity) VALUES(v_item,v_wh,5),(v_unpriced,v_wh,5);
  -- Separate active default and RETAIL lists must still yield one card per rail.
  UPDATE public.price_lists SET is_default=false WHERE is_default;
  INSERT INTO public.price_lists(code,name,currency,is_default,is_active) VALUES('REC-OV-DEFAULT','Recovery default','USD',true,true) RETURNING id INTO v_default;
  SELECT id INTO STRICT v_retail FROM public.price_lists WHERE code='RETAIL';
  UPDATE public.price_lists SET is_active=true WHERE id=v_retail;
  INSERT INTO public.price_list_items(price_list_id,stock_item_id,unit_price,core_charge) VALUES(v_retail,v_item,99,0),(v_default,v_item,12,0);
  INSERT INTO public.stock_item_shop_merch(stock_item_id,home_carousel_rank,home_carousel_caption) VALUES(v_item,4,'Preserved caption');
  PERFORM public.upsert_staff_product_page(v_item,12,'none',0,NULL,true,false,true,8::smallint);
  IF NOT EXISTS(SELECT 1 FROM public.stock_item_shop_merch WHERE stock_item_id=v_item AND pin_featured AND NOT pin_movers AND pin_newest AND pin_sort=8 AND home_carousel_rank=4 AND home_carousel_caption='Preserved caption') THEN
    RAISE EXCEPTION 'pin save changed independent carousel or rail flags';
  END IF;
  SELECT count(*) INTO v_count FROM public.list_staff_product_pages('REC-OV-PIN-1',20) WHERE stock_item_id=v_item;
  IF v_count<>1 THEN RAISE EXCEPTION 'staff product list contains duplicate retail prices'; END IF;
  INSERT INTO public.stock_item_shop_merch(stock_item_id,pin_featured,pin_movers,pin_newest) VALUES(v_unpriced,true,true,true);
  PERFORM public._test_staff_overview_auth(NULL,'anon');
  SELECT count(*) INTO v_count FROM public.list_storefront_home_rails(48) WHERE stock_item_id=v_item AND unit_price=12 AND oem_part_number IS NULL;
  IF v_count<>3 THEN RAISE EXCEPTION 'anonymous rail cards lack canonical stock IDs, hide-OEM or default-price uniqueness: %',v_count; END IF;
  IF EXISTS(SELECT 1 FROM public.list_storefront_home_rails(48) WHERE stock_item_id=v_unpriced) THEN RAISE EXCEPTION 'unpriced manual pin bypassed shop eligibility'; END IF;

  PERFORM public._test_staff_overview_auth(v_customer);
  v_denied:=false;
  BEGIN PERFORM public.get_staff_overview_dashboard(now()-interval '1 day',now()); EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'non-staff read staff overview'; END IF;
  v_denied:=false;
  BEGIN PERFORM public.search_staff_overview('REC-OV-INVOICE-UNIQUE',12); EXCEPTION WHEN OTHERS THEN v_denied:=true; END;
  IF NOT v_denied THEN RAISE EXCEPTION 'non-staff searched staff records'; END IF;
  RAISE NOTICE 'staff overview and merchandising reconciliation smoke passed';
END;
$$;
ROLLBACK;
