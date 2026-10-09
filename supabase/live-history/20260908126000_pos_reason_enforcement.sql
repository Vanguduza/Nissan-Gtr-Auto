-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908126000 pos_reason_enforcement).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Enforce controlled POS reasons below the UI/RPC layer.
CREATE OR REPLACE FUNCTION private.assert_pos_reason(p_action TEXT,p_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_requires_notes BOOLEAN;
BEGIN
 SELECT r.requires_notes INTO v_requires_notes
 FROM public.pos_approval_reason_codes r
 WHERE r.action=p_action AND r.code=p_code AND r.is_active;
 IF NOT FOUND THEN RAISE EXCEPTION 'valid active reason code required for %',p_action; END IF;
 IF v_requires_notes AND trim(COALESCE(p_notes,''))='' THEN
  RAISE EXCEPTION 'supporting notes required for reason %',p_code;
 END IF;
END $$;

REVOKE ALL ON FUNCTION private.assert_pos_reason(TEXT,TEXT,TEXT) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.validate_pos_return_reason()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 PERFORM private.assert_pos_reason('return_post',NEW.reason_code,NEW.notes);
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_pos_return_reason ON public.pos_return_cases;

CREATE TRIGGER trg_validate_pos_return_reason
 BEFORE INSERT OR UPDATE OF reason_code,notes ON public.pos_return_cases
 FOR EACH ROW EXECUTE FUNCTION private.validate_pos_return_reason();

CREATE OR REPLACE FUNCTION private.validate_pos_core_reason()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 PERFORM private.assert_pos_reason('core_return',NEW.reason_code,NEW.notes);
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_pos_core_reason ON public.pos_core_returns;

CREATE TRIGGER trg_validate_pos_core_reason
 BEFORE INSERT OR UPDATE OF reason_code,notes ON public.pos_core_returns
 FOR EACH ROW EXECUTE FUNCTION private.validate_pos_core_reason();

CREATE OR REPLACE FUNCTION private.validate_pos_cash_movement_reason()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NEW.kind<>'cash_in' THEN
  PERFORM private.assert_pos_reason('cash_out',NEW.reason_code,NEW.notes);
 END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_pos_cash_movement_reason ON public.pos_till_cash_movements;

CREATE TRIGGER trg_validate_pos_cash_movement_reason
 BEFORE INSERT OR UPDATE OF reason_code,notes,kind ON public.pos_till_cash_movements
 FOR EACH ROW EXECUTE FUNCTION private.validate_pos_cash_movement_reason();

CREATE OR REPLACE FUNCTION private.validate_pos_till_variance_reason()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NEW.variance IS NOT NULL AND abs(NEW.variance)>0.009
    AND NEW.status IN('variance_pending','closed') THEN
  PERFORM private.assert_pos_reason('till_variance',NEW.variance_reason_code,NEW.close_notes);
 END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_pos_till_variance_reason ON public.pos_till_sessions;

CREATE TRIGGER trg_validate_pos_till_variance_reason
 BEFORE UPDATE OF variance,variance_reason_code,close_notes,status ON public.pos_till_sessions
 FOR EACH ROW EXECUTE FUNCTION private.validate_pos_till_variance_reason();

CREATE OR REPLACE FUNCTION private.validate_pos_audit_reason()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_requires_notes BOOLEAN;
BEGIN
 IF NEW.reason_code IS NULL OR NEW.approval_policy_action IS NULL THEN RETURN NEW; END IF;
 SELECT r.requires_notes INTO v_requires_notes
 FROM public.pos_approval_reason_codes r
 WHERE r.action=NEW.approval_policy_action AND r.code=NEW.reason_code AND r.is_active;
 IF NOT FOUND THEN RAISE EXCEPTION 'invalid governed reason % for %',NEW.reason_code,NEW.approval_policy_action; END IF;
 IF v_requires_notes AND (
   trim(COALESCE(NEW.notes,''))='' OR trim(COALESCE(NEW.notes,''))=NEW.reason_code
 ) THEN RAISE EXCEPTION 'supporting notes required for governed reason %',NEW.reason_code; END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_pos_audit_reason ON public.pos_action_audit;

CREATE TRIGGER trg_validate_pos_audit_reason
 BEFORE UPDATE OF reason_code,approval_policy_action,notes ON public.pos_action_audit
 FOR EACH ROW EXECUTE FUNCTION private.validate_pos_audit_reason();

REVOKE ALL ON FUNCTION private.validate_pos_return_reason(),private.validate_pos_core_reason(),
 private.validate_pos_cash_movement_reason(),private.validate_pos_till_variance_reason(),
 private.validate_pos_audit_reason() FROM PUBLIC,anon,authenticated;
