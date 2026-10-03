-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905141210 hot_path_transaction_indexes_v1).
-- Source of record for what production ran; see supabase/live-history/README.md.

create index if not exists commerce_orders_settled_payment_idx
  on public.commerce_orders(settled_payment_entry_id)
  where settled_payment_entry_id is not null;

create index if not exists commerce_orders_warehouse_state_idx
  on public.commerce_orders(warehouse_id, state, updated_at desc);

create index if not exists commerce_manual_payment_requests_payment_entry_idx
  on public.commerce_manual_payment_requests(payment_entry_id)
  where payment_entry_id is not null;

create index if not exists commerce_payment_exceptions_payment_entry_idx
  on public.commerce_payment_exceptions(payment_entry_id)
  where payment_entry_id is not null;

create index if not exists paynow_intents_payment_entry_idx
  on public.paynow_payment_intents(payment_entry_id)
  where payment_entry_id is not null;
create index if not exists ecocash_intents_payment_entry_idx
  on public.ecocash_payment_intents(payment_entry_id)
  where payment_entry_id is not null;
create index if not exists contipay_intents_payment_entry_idx
  on public.contipay_payment_intents(payment_entry_id)
  where payment_entry_id is not null;

create index if not exists paynow_webhook_events_intent_created_idx
  on public.paynow_webhook_events(intent_id, created_at desc)
  where intent_id is not null;
create index if not exists ecocash_webhook_events_intent_created_idx
  on public.ecocash_webhook_events(intent_id, created_at desc)
  where intent_id is not null;
create index if not exists contipay_webhook_events_intent_created_idx
  on public.contipay_webhook_events(intent_id, created_at desc)
  where intent_id is not null;

create index if not exists return_refund_requests_customer_created_idx
  on public.return_refund_requests(customer_id, created_at desc);
create index if not exists return_refund_requests_source_invoice_created_idx
  on public.return_refund_requests(source_invoice_id, created_at desc);
create index if not exists return_refund_requests_original_payment_idx
  on public.return_refund_requests(original_payment_entry_id)
  where original_payment_entry_id is not null;

create index if not exists sales_invoices_cart_idx
  on public.sales_invoices(cart_id)
  where cart_id is not null;
create index if not exists sales_invoices_customer_posted_idx
  on public.sales_invoices(customer_id, posted_at desc)
  where customer_id is not null;
create index if not exists sales_invoices_return_against_idx
  on public.sales_invoices(return_against_id)
  where return_against_id is not null;
create index if not exists sales_invoices_warehouse_status_posted_idx
  on public.sales_invoices(warehouse_id, status, posted_at desc);
create index if not exists sales_invoices_doc_status_posted_idx
  on public.sales_invoices(doc_type, status, posted_at desc);

create index if not exists delivery_notes_pick_list_idx
  on public.delivery_notes(pick_list_id)
  where pick_list_id is not null;
create index if not exists delivery_notes_warehouse_status_idx
  on public.delivery_notes(warehouse_id, status, updated_at desc);
create index if not exists pick_lists_warehouse_status_idx
  on public.pick_lists(warehouse_id, status, updated_at desc);

create index if not exists customer_receipt_outbox_work_idx
  on public.customer_receipt_outbox(status, claimed_at, created_at)
  where status in ('pending','rendering','sending','failed');

create index if not exists customer_receipt_outbox_pdf_artifact_idx
  on public.customer_receipt_outbox(receipt_pdf_artifact_id)
  where receipt_pdf_artifact_id is not null;
