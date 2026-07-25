-- Phase 1: In-app live chat (threads + messages + participants + Realtime)
-- Plan: docs/plans/2026-07-25-in-app-live-chat.md
-- ADR: docs/decisions/2026-07-25-in-app-live-chat.md
-- Exclusions: no ZIMRA / payroll tax; auth customers only (no guest threads)

CREATE TYPE public.chat_thread_kind AS ENUM ('support', 'parts');
CREATE TYPE public.chat_thread_status AS ENUM ('open', 'assigned', 'closed');
CREATE TYPE public.chat_sender_kind AS ENUM ('customer', 'staff', 'system');
CREATE TYPE public.chat_participant_role AS ENUM ('customer', 'staff');

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
CREATE TABLE public.chat_threads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  customer_id UUID REFERENCES public.customers (id) ON DELETE SET NULL,
  kind public.chat_thread_kind NOT NULL DEFAULT 'support',
  status public.chat_thread_status NOT NULL DEFAULT 'open',
  subject TEXT,
  assigned_to UUID REFERENCES public.profiles (id) ON DELETE SET NULL,
  assigned_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  closed_by UUID REFERENCES public.profiles (id) ON DELETE SET NULL,
  last_message_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chat_threads_assigned_requires_assignee CHECK (
    (status = 'assigned' AND assigned_to IS NOT NULL)
    OR (status <> 'assigned')
  ),
  CONSTRAINT chat_threads_closed_has_closed_at CHECK (
    (status = 'closed' AND closed_at IS NOT NULL)
    OR (status <> 'closed')
  )
);

CREATE INDEX chat_threads_customer_user_idx
  ON public.chat_threads (customer_user_id, updated_at DESC);
CREATE INDEX chat_threads_status_idx
  ON public.chat_threads (status, last_message_at DESC NULLS LAST);
CREATE INDEX chat_threads_assigned_to_idx
  ON public.chat_threads (assigned_to, status)
  WHERE assigned_to IS NOT NULL;

CREATE TABLE public.chat_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  thread_id UUID NOT NULL REFERENCES public.chat_threads (id) ON DELETE CASCADE,
  sender_user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE RESTRICT,
  sender_kind public.chat_sender_kind NOT NULL,
  body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX chat_messages_thread_created_idx
  ON public.chat_messages (thread_id, created_at);

CREATE TABLE public.chat_participants (
  thread_id UUID NOT NULL REFERENCES public.chat_threads (id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  role public.chat_participant_role NOT NULL,
  last_read_at TIMESTAMPTZ,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (thread_id, user_id)
);

CREATE INDEX chat_participants_user_idx
  ON public.chat_participants (user_id);

COMMENT ON TABLE public.chat_threads IS
  'Customer support/parts chat threads. Auth customers only; staff claim via assigned_to.';
COMMENT ON TABLE public.chat_messages IS
  'Append-only chat messages. Client UPDATE/DELETE denied.';
COMMENT ON TABLE public.chat_participants IS
  'Thread membership + last_read_at for unread counts.';

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._chat_staff_roles()
RETURNS public.staff_role[]
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT ARRAY['admin', 'sales', 'warehouse']::public.staff_role[];
$$;

CREATE OR REPLACE FUNCTION public._is_chat_staff()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR public.has_staff_role(public._chat_staff_roles());
$$;

CREATE OR REPLACE FUNCTION public._can_select_chat_thread(p_thread public.chat_threads)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR p_thread.customer_user_id = auth.uid()
    OR (
      public.has_staff_role(public._chat_staff_roles())
      AND (
        p_thread.status IN ('open', 'assigned')
        OR p_thread.assigned_to = auth.uid()
        OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
      )
    );
$$;

CREATE OR REPLACE FUNCTION public.chat_threads_touch_updated()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_threads_touch_updated_trg ON public.chat_threads;
CREATE TRIGGER chat_threads_touch_updated_trg
  BEFORE UPDATE ON public.chat_threads
  FOR EACH ROW
  EXECUTE PROCEDURE public.chat_threads_touch_updated();

CREATE OR REPLACE FUNCTION public.chat_messages_after_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.chat_threads
  SET
    last_message_at = NEW.created_at,
    updated_at = now()
  WHERE id = NEW.thread_id;

  INSERT INTO public.chat_participants (thread_id, user_id, role, last_read_at)
  VALUES (
    NEW.thread_id,
    NEW.sender_user_id,
    CASE
      WHEN NEW.sender_kind = 'staff' THEN 'staff'::public.chat_participant_role
      ELSE 'customer'::public.chat_participant_role
    END,
    NEW.created_at
  )
  ON CONFLICT (thread_id, user_id) DO UPDATE
  SET last_read_at = EXCLUDED.last_read_at;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_messages_after_insert_trg ON public.chat_messages;
CREATE TRIGGER chat_messages_after_insert_trg
  AFTER INSERT ON public.chat_messages
  FOR EACH ROW
  EXECUTE PROCEDURE public.chat_messages_after_insert();

-- Block client mutation of messages (append-only)
CREATE OR REPLACE FUNCTION public.chat_messages_immutable_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'chat_messages are immutable';
  END IF;
  IF TG_OP = 'DELETE' AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'chat_messages cannot be deleted by clients';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_messages_immutable_guard_trg ON public.chat_messages;
CREATE TRIGGER chat_messages_immutable_guard_trg
  BEFORE UPDATE OR DELETE ON public.chat_messages
  FOR EACH ROW
  EXECUTE PROCEDURE public.chat_messages_immutable_guard();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.chat_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_participants ENABLE ROW LEVEL SECURITY;

-- Threads: customers own; staff see open/assigned (+ own closed / admin all closed)
CREATE POLICY chat_threads_select ON public.chat_threads
  FOR SELECT TO authenticated
  USING (public._can_select_chat_thread(chat_threads));

CREATE POLICY chat_threads_customer_insert ON public.chat_threads
  FOR INSERT TO authenticated
  WITH CHECK (
    customer_user_id = auth.uid()
    AND NOT public.is_staff()
    AND status = 'open'
    AND assigned_to IS NULL
    AND closed_at IS NULL
    AND closed_by IS NULL
    AND (customer_id IS NULL OR customer_id = public._current_customer_id())
  );

CREATE POLICY chat_threads_customer_update ON public.chat_threads
  FOR UPDATE TO authenticated
  USING (customer_user_id = auth.uid() AND NOT public.is_staff())
  WITH CHECK (
    customer_user_id = auth.uid()
    AND NOT public.is_staff()
    -- customers may only close their own thread (or leave metadata alone)
    AND assigned_to IS NOT DISTINCT FROM (
      SELECT t.assigned_to FROM public.chat_threads t WHERE t.id = chat_threads.id
    )
    AND kind = (SELECT t.kind FROM public.chat_threads t WHERE t.id = chat_threads.id)
    AND customer_id IS NOT DISTINCT FROM (
      SELECT t.customer_id FROM public.chat_threads t WHERE t.id = chat_threads.id
    )
  );

CREATE POLICY chat_threads_staff_update ON public.chat_threads
  FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(public._chat_staff_roles())
    AND public._can_select_chat_thread(chat_threads)
  )
  WITH CHECK (
    public.has_staff_role(public._chat_staff_roles())
    AND customer_user_id = (
      SELECT t.customer_user_id FROM public.chat_threads t WHERE t.id = id
    )
    AND kind = (SELECT t.kind FROM public.chat_threads t WHERE t.id = id)
  );

-- Messages
CREATE POLICY chat_messages_select ON public.chat_messages
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.chat_threads t
      WHERE t.id = chat_messages.thread_id
        AND public._can_select_chat_thread(t)
    )
  );

CREATE POLICY chat_messages_customer_insert ON public.chat_messages
  FOR INSERT TO authenticated
  WITH CHECK (
    sender_user_id = auth.uid()
    AND sender_kind = 'customer'
    AND NOT public.is_staff()
    AND EXISTS (
      SELECT 1
      FROM public.chat_threads t
      WHERE t.id = thread_id
        AND t.customer_user_id = auth.uid()
        AND t.status IN ('open', 'assigned')
    )
  );

CREATE POLICY chat_messages_staff_insert ON public.chat_messages
  FOR INSERT TO authenticated
  WITH CHECK (
    sender_user_id = auth.uid()
    AND sender_kind = 'staff'
    AND public.has_staff_role(public._chat_staff_roles())
    AND EXISTS (
      SELECT 1
      FROM public.chat_threads t
      WHERE t.id = thread_id
        AND public._can_select_chat_thread(t)
        AND t.status IN ('open', 'assigned')
    )
  );

-- Participants (unread)
CREATE POLICY chat_participants_select ON public.chat_participants
  FOR SELECT TO authenticated
  USING (
    user_id = auth.uid()
    OR EXISTS (
      SELECT 1
      FROM public.chat_threads t
      WHERE t.id = chat_participants.thread_id
        AND public._can_select_chat_thread(t)
    )
  );

CREATE POLICY chat_participants_insert_own ON public.chat_participants
  FOR INSERT TO authenticated
  WITH CHECK (
    user_id = auth.uid()
    AND (
      (
        role = 'customer'
        AND NOT public.is_staff()
        AND EXISTS (
          SELECT 1 FROM public.chat_threads t
          WHERE t.id = thread_id AND t.customer_user_id = auth.uid()
        )
      )
      OR (
        role = 'staff'
        AND public.has_staff_role(public._chat_staff_roles())
        AND EXISTS (
          SELECT 1 FROM public.chat_threads t
          WHERE t.id = thread_id AND public._can_select_chat_thread(t)
        )
      )
    )
  );

CREATE POLICY chat_participants_update_own ON public.chat_participants
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (
    user_id = auth.uid()
    AND role = (SELECT p.role FROM public.chat_participants p
                WHERE p.thread_id = chat_participants.thread_id
                  AND p.user_id = auth.uid())
  );

-- ---------------------------------------------------------------------------
-- Grants (RLS still applies; service_role for optional notify edge)
-- ---------------------------------------------------------------------------
REVOKE ALL ON TABLE public.chat_threads FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.chat_messages FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.chat_participants FROM PUBLIC, anon;

GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_threads TO authenticated;
GRANT SELECT, INSERT ON TABLE public.chat_messages TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_participants TO authenticated;

GRANT ALL ON TABLE public.chat_threads TO service_role;
GRANT ALL ON TABLE public.chat_messages TO service_role;
GRANT ALL ON TABLE public.chat_participants TO service_role;

REVOKE ALL ON FUNCTION public._chat_staff_roles() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._is_chat_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_select_chat_thread(public.chat_threads) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._chat_staff_roles() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._is_chat_staff() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_select_chat_thread(public.chat_threads)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- RPCs: start / claim / close / mark read / post message
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.start_chat_thread(
  p_kind public.chat_thread_kind DEFAULT 'support',
  p_subject TEXT DEFAULT NULL,
  p_body TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_thread_id UUID;
  v_customer_id UUID;
  v_body TEXT := NULLIF(btrim(COALESCE(p_body, '')), '');
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;
  IF public.is_staff() THEN
    RAISE EXCEPTION 'staff cannot start customer chat threads via this RPC';
  END IF;

  v_customer_id := public._current_customer_id();

  INSERT INTO public.chat_threads (
    customer_user_id, customer_id, kind, status, subject, last_message_at
  ) VALUES (
    auth.uid(),
    v_customer_id,
    COALESCE(p_kind, 'support'),
    'open',
    NULLIF(btrim(COALESCE(p_subject, '')), ''),
    CASE WHEN v_body IS NOT NULL THEN now() ELSE NULL END
  )
  RETURNING id INTO v_thread_id;

  INSERT INTO public.chat_participants (thread_id, user_id, role, last_read_at)
  VALUES (v_thread_id, auth.uid(), 'customer', now());

  IF v_body IS NOT NULL THEN
    INSERT INTO public.chat_messages (thread_id, sender_user_id, sender_kind, body)
    VALUES (v_thread_id, auth.uid(), 'customer', v_body);
  END IF;

  RETURN v_thread_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.claim_chat_thread(p_thread_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.has_staff_role(public._chat_staff_roles()) THEN
    RAISE EXCEPTION 'admin, sales, or warehouse role required to claim chat';
  END IF;

  UPDATE public.chat_threads
  SET
    status = 'assigned',
    assigned_to = auth.uid(),
    assigned_at = now(),
    updated_at = now()
  WHERE id = p_thread_id
    AND status IN ('open', 'assigned');

  IF NOT FOUND THEN
    RAISE EXCEPTION 'thread not found or not claimable';
  END IF;

  INSERT INTO public.chat_participants (thread_id, user_id, role, last_read_at)
  VALUES (p_thread_id, auth.uid(), 'staff', now())
  ON CONFLICT (thread_id, user_id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.close_chat_thread(p_thread_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.chat_threads;
BEGIN
  SELECT * INTO v_row FROM public.chat_threads WHERE id = p_thread_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'thread not found';
  END IF;

  IF NOT (
    v_row.customer_user_id = auth.uid()
    OR public.has_staff_role(public._chat_staff_roles())
  ) THEN
    RAISE EXCEPTION 'not allowed to close this thread';
  END IF;

  IF v_row.status = 'closed' THEN
    RETURN;
  END IF;

  UPDATE public.chat_threads
  SET
    status = 'closed',
    closed_at = now(),
    closed_by = auth.uid(),
    updated_at = now()
  WHERE id = p_thread_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_chat_thread_read(p_thread_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_role public.chat_participant_role;
  v_row public.chat_threads;
BEGIN
  SELECT * INTO v_row FROM public.chat_threads WHERE id = p_thread_id;
  IF NOT FOUND OR NOT public._can_select_chat_thread(v_row) THEN
    RAISE EXCEPTION 'thread not found or not visible';
  END IF;

  IF v_row.customer_user_id = auth.uid() THEN
    v_role := 'customer';
  ELSIF public.has_staff_role(public._chat_staff_roles()) THEN
    v_role := 'staff';
  ELSE
    RAISE EXCEPTION 'not a participant';
  END IF;

  INSERT INTO public.chat_participants (thread_id, user_id, role, last_read_at)
  VALUES (p_thread_id, auth.uid(), v_role, now())
  ON CONFLICT (thread_id, user_id) DO UPDATE
  SET last_read_at = now();
END;
$$;

CREATE OR REPLACE FUNCTION public.post_chat_message(
  p_thread_id UUID,
  p_body TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.chat_threads;
  v_kind public.chat_sender_kind;
  v_id UUID;
  v_body TEXT := NULLIF(btrim(COALESCE(p_body, '')), '');
BEGIN
  IF v_body IS NULL THEN
    RAISE EXCEPTION 'message body required';
  END IF;

  SELECT * INTO v_row FROM public.chat_threads WHERE id = p_thread_id;
  IF NOT FOUND OR NOT public._can_select_chat_thread(v_row) THEN
    RAISE EXCEPTION 'thread not found or not visible';
  END IF;
  IF v_row.status = 'closed' THEN
    RAISE EXCEPTION 'thread is closed';
  END IF;

  IF v_row.customer_user_id = auth.uid() AND NOT public.is_staff() THEN
    v_kind := 'customer';
  ELSIF public.has_staff_role(public._chat_staff_roles()) THEN
    v_kind := 'staff';
  ELSE
    RAISE EXCEPTION 'not allowed to post';
  END IF;

  INSERT INTO public.chat_messages (thread_id, sender_user_id, sender_kind, body)
  VALUES (p_thread_id, auth.uid(), v_kind, v_body)
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.chat_unread_count(p_thread_id UUID DEFAULT NULL)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COUNT(*)::bigint
  FROM public.chat_messages m
  JOIN public.chat_threads t ON t.id = m.thread_id
  LEFT JOIN public.chat_participants p
    ON p.thread_id = m.thread_id AND p.user_id = auth.uid()
  WHERE public._can_select_chat_thread(t)
    AND (p_thread_id IS NULL OR m.thread_id = p_thread_id)
    AND m.sender_user_id IS DISTINCT FROM auth.uid()
    AND (p.last_read_at IS NULL OR m.created_at > p.last_read_at);
$$;

REVOKE ALL ON FUNCTION public.start_chat_thread(public.chat_thread_kind, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.claim_chat_thread(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.close_chat_thread(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.mark_chat_thread_read(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_chat_message(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.chat_unread_count(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.start_chat_thread(public.chat_thread_kind, TEXT, TEXT)
  TO authenticated;
GRANT EXECUTE ON FUNCTION public.claim_chat_thread(UUID)
  TO authenticated;
GRANT EXECUTE ON FUNCTION public.close_chat_thread(UUID)
  TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_chat_thread_read(UUID)
  TO authenticated;
GRANT EXECUTE ON FUNCTION public.post_chat_message(UUID, TEXT)
  TO authenticated;
GRANT EXECUTE ON FUNCTION public.chat_unread_count(UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Realtime (RLS filters channel auth — same pattern as delivery_locations)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    BEGIN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_threads;
    EXCEPTION
      WHEN duplicate_object THEN NULL;
    END;
    BEGIN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_messages;
    EXCEPTION
      WHEN duplicate_object THEN NULL;
    END;
    BEGIN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_participants;
    EXCEPTION
      WHEN duplicate_object THEN NULL;
    END;
  END IF;
END;
$$;
