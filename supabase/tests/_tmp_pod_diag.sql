CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END; $$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000d1';
  v_job UUID;
  v_photo TEXT;
BEGIN
  SELECT id INTO v_job FROM public.delivery_jobs
  WHERE assignee_user_id = v_driver ORDER BY created_at DESC LIMIT 1;
  RAISE NOTICE 'job=% status=%', v_job, (SELECT status FROM public.delivery_jobs WHERE id = v_job);

  v_photo := v_job::text || '/photo.jpg';
  PERFORM public._test_set_auth_uid(v_driver);
  RAISE NOTICE 'uid=%', auth.uid();
  RAISE NOTICE 'has_driver=%', public.has_staff_role(ARRAY['driver']::public.staff_role[]);
  RAISE NOTICE 'path_job=%', public._delivery_pod_job_id_from_path(v_photo);
  RAISE NOTICE 'can_write=%', public._can_write_delivery_pod_object(v_photo);
  RAISE NOTICE 'can_select=%', public._can_select_delivery_pod_object(v_photo);

  -- try insert as postgres (bypasses RLS) — skip
  -- try with role
  BEGIN
    SET LOCAL ROLE authenticated;
    RAISE NOTICE 'as authenticated can_write=%', public._can_write_delivery_pod_object(v_photo);
    INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
    VALUES ('delivery-pods', v_photo || '.diag', v_driver, v_driver::text, '{}'::jsonb);
    RAISE NOTICE 'insert ok';
    RESET ROLE;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'insert err: %', SQLERRM;
    RESET ROLE;
  END;
END $$;
