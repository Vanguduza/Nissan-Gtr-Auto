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
  -- use newest job for this driver regardless of status; flip to dispatched via GUC
  SELECT id INTO v_job FROM public.delivery_jobs
  WHERE assignee_user_id = v_driver ORDER BY created_at DESC LIMIT 1;

  PERFORM set_config('app.logistics_rpc', '1', true);
  UPDATE public.delivery_jobs SET status = 'dispatched', completed_at = NULL, completed_via = NULL, updated_at = now()
  WHERE id = v_job;
  PERFORM set_config('app.logistics_rpc', '0', true);

  v_photo := v_job::text || '/photo-diag2.jpg';
  PERFORM public._test_set_auth_uid(v_driver);
  RAISE NOTICE 'status=% can_write=%',
    (SELECT status FROM public.delivery_jobs WHERE id = v_job),
    public._can_write_delivery_pod_object(v_photo);

  SET LOCAL ROLE authenticated;
  RAISE NOTICE 'role can_write=%', public._can_write_delivery_pod_object(v_photo);
  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES ('delivery-pods', v_photo, auth.uid(), auth.uid()::text, '{}'::jsonb);
  RAISE NOTICE 'insert ok id path=%', v_photo;
  RESET ROLE;
END $$;
