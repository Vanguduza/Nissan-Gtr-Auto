SELECT id, name, public FROM storage.buckets WHERE id = 'delivery-pods';

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', p_uid::text, 'role', 'authenticated')::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END; $$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000d1';
  v_job UUID;
  v_photo TEXT;
  v_ok BOOLEAN;
BEGIN
  SELECT id INTO v_job FROM public.delivery_jobs WHERE assignee_user_id = v_driver AND status = 'dispatched' ORDER BY created_at DESC LIMIT 1;
  IF v_job IS NULL THEN
    RAISE NOTICE 'no job — create minimal via admin later';
  ELSE
    v_photo := v_job::text || '/photo.jpg';
    PERFORM public._test_set_auth_uid(v_driver);
    v_ok := public._can_write_delivery_pod_object(v_photo);
    RAISE NOTICE 'can_write=% job=% uid=% has_driver=% assignee=%',
      v_ok, v_job, auth.uid(),
      public.has_staff_role(ARRAY['driver']::public.staff_role[]),
      (SELECT assignee_user_id FROM public.delivery_jobs WHERE id = v_job);
  END IF;
END $$;
