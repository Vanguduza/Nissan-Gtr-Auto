export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  graphql_public: {
    Tables: {
      [_ in never]: never
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      graphql: {
        Args: {
          extensions?: Json
          operationName?: string
          query?: string
          variables?: Json
        }
        Returns: Json
      }
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
  public: {
    Tables: {
      accounting_periods: {
        Row: {
          created_at: string
          id: string
          label: string
          locked_at: string | null
          locked_by: string | null
          period_end: string
          period_start: string
        }
        Insert: {
          created_at?: string
          id?: string
          label: string
          locked_at?: string | null
          locked_by?: string | null
          period_end: string
          period_start: string
        }
        Update: {
          created_at?: string
          id?: string
          label?: string
          locked_at?: string | null
          locked_by?: string | null
          period_end?: string
          period_start?: string
        }
        Relationships: []
      }
      ai_report_deliveries: {
        Row: {
          channel: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at: string
          error: string | null
          id: string
          provider_ref: string | null
          recipient: string
          run_id: string
          sent_at: string | null
          status: Database["public"]["Enums"]["ai_delivery_status"]
        }
        Insert: {
          channel: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at?: string
          error?: string | null
          id?: string
          provider_ref?: string | null
          recipient: string
          run_id: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["ai_delivery_status"]
        }
        Update: {
          channel?: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at?: string
          error?: string | null
          id?: string
          provider_ref?: string | null
          recipient?: string
          run_id?: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["ai_delivery_status"]
        }
        Relationships: [
          {
            foreignKeyName: "ai_report_deliveries_run_id_fkey"
            columns: ["run_id"]
            isOneToOne: false
            referencedRelation: "ai_report_runs"
            referencedColumns: ["id"]
          },
        ]
      }
      ai_report_runs: {
        Row: {
          cadence: Database["public"]["Enums"]["ai_report_cadence"]
          created_at: string
          error: string | null
          finished_at: string | null
          gemini_used: boolean
          id: string
          kpi_json: Json
          narrative: string | null
          period_end: string
          period_start: string
          started_at: string | null
          status: Database["public"]["Enums"]["ai_report_run_status"]
          subscription_id: string | null
        }
        Insert: {
          cadence: Database["public"]["Enums"]["ai_report_cadence"]
          created_at?: string
          error?: string | null
          finished_at?: string | null
          gemini_used?: boolean
          id?: string
          kpi_json?: Json
          narrative?: string | null
          period_end: string
          period_start: string
          started_at?: string | null
          status?: Database["public"]["Enums"]["ai_report_run_status"]
          subscription_id?: string | null
        }
        Update: {
          cadence?: Database["public"]["Enums"]["ai_report_cadence"]
          created_at?: string
          error?: string | null
          finished_at?: string | null
          gemini_used?: boolean
          id?: string
          kpi_json?: Json
          narrative?: string | null
          period_end?: string
          period_start?: string
          started_at?: string | null
          status?: Database["public"]["Enums"]["ai_report_run_status"]
          subscription_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "ai_report_runs_subscription_id_fkey"
            columns: ["subscription_id"]
            isOneToOne: false
            referencedRelation: "ai_report_subscriptions"
            referencedColumns: ["id"]
          },
        ]
      }
      ai_report_subscriptions: {
        Row: {
          active: boolean
          cadence: Database["public"]["Enums"]["ai_report_cadence"]
          channels: Database["public"]["Enums"]["ai_delivery_channel"][]
          created_at: string
          created_by: string | null
          id: string
          include_narrative: boolean
          kpi_set: string
          last_run_at: string | null
          recipient_emails: string[]
          recipient_whatsapp_e164: string[]
          timezone: string
          updated_at: string
        }
        Insert: {
          active?: boolean
          cadence?: Database["public"]["Enums"]["ai_report_cadence"]
          channels?: Database["public"]["Enums"]["ai_delivery_channel"][]
          created_at?: string
          created_by?: string | null
          id?: string
          include_narrative?: boolean
          kpi_set?: string
          last_run_at?: string | null
          recipient_emails?: string[]
          recipient_whatsapp_e164?: string[]
          timezone?: string
          updated_at?: string
        }
        Update: {
          active?: boolean
          cadence?: Database["public"]["Enums"]["ai_report_cadence"]
          channels?: Database["public"]["Enums"]["ai_delivery_channel"][]
          created_at?: string
          created_by?: string | null
          id?: string
          include_narrative?: boolean
          kpi_set?: string
          last_run_at?: string | null
          recipient_emails?: string[]
          recipient_whatsapp_e164?: string[]
          timezone?: string
          updated_at?: string
        }
        Relationships: []
      }
      app_settings: {
        Row: {
          description: string | null
          key: string
          updated_at: string
          value_json: Json
        }
        Insert: {
          description?: string | null
          key: string
          updated_at?: string
          value_json: Json
        }
        Update: {
          description?: string | null
          key?: string
          updated_at?: string
          value_json?: Json
        }
        Relationships: []
      }
      attendance_events: {
        Row: {
          created_at: string
          employee_id: string
          event_type: Database["public"]["Enums"]["attendance_event_type"]
          id: string
          notes: string | null
          occurred_at: string
          recorded_by: string | null
          source: string
        }
        Insert: {
          created_at?: string
          employee_id: string
          event_type: Database["public"]["Enums"]["attendance_event_type"]
          id?: string
          notes?: string | null
          occurred_at?: string
          recorded_by?: string | null
          source?: string
        }
        Update: {
          created_at?: string
          employee_id?: string
          event_type?: Database["public"]["Enums"]["attendance_event_type"]
          id?: string
          notes?: string | null
          occurred_at?: string
          recorded_by?: string | null
          source?: string
        }
        Relationships: [
          {
            foreignKeyName: "attendance_events_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      bank_recon_matches: {
        Row: {
          id: string
          journal_entry_line_id: string
          matched_at: string
          matched_by: string | null
          statement_line_id: string
        }
        Insert: {
          id?: string
          journal_entry_line_id: string
          matched_at?: string
          matched_by?: string | null
          statement_line_id: string
        }
        Update: {
          id?: string
          journal_entry_line_id?: string
          matched_at?: string
          matched_by?: string | null
          statement_line_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "bank_recon_matches_journal_entry_line_id_fkey"
            columns: ["journal_entry_line_id"]
            isOneToOne: false
            referencedRelation: "journal_entry_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "bank_recon_matches_statement_line_id_fkey"
            columns: ["statement_line_id"]
            isOneToOne: false
            referencedRelation: "bank_statement_lines"
            referencedColumns: ["id"]
          },
        ]
      }
      bank_statement_lines: {
        Row: {
          amount: number
          created_at: string
          description: string | null
          id: string
          line_date: string
          statement_id: string
          status: Database["public"]["Enums"]["bank_recon_status"]
        }
        Insert: {
          amount: number
          created_at?: string
          description?: string | null
          id?: string
          line_date: string
          statement_id: string
          status?: Database["public"]["Enums"]["bank_recon_status"]
        }
        Update: {
          amount?: number
          created_at?: string
          description?: string | null
          id?: string
          line_date?: string
          statement_id?: string
          status?: Database["public"]["Enums"]["bank_recon_status"]
        }
        Relationships: [
          {
            foreignKeyName: "bank_statement_lines_statement_id_fkey"
            columns: ["statement_id"]
            isOneToOne: false
            referencedRelation: "bank_statements"
            referencedColumns: ["id"]
          },
        ]
      }
      bank_statements: {
        Row: {
          account_code: string
          closing_balance: number
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          id: string
          opening_balance: number
          statement_date: string
        }
        Insert: {
          account_code: string
          closing_balance?: number
          created_at?: string
          created_by?: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          id?: string
          opening_balance?: number
          statement_date: string
        }
        Update: {
          account_code?: string
          closing_balance?: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          id?: string
          opening_balance?: number
          statement_date?: string
        }
        Relationships: [
          {
            foreignKeyName: "bank_statements_account_code_fkey"
            columns: ["account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
        ]
      }
      chart_of_accounts: {
        Row: {
          account_type: Database["public"]["Enums"]["account_type"]
          code: string
          created_at: string
          is_active: boolean
          name: string
        }
        Insert: {
          account_type: Database["public"]["Enums"]["account_type"]
          code: string
          created_at?: string
          is_active?: boolean
          name: string
        }
        Update: {
          account_type?: Database["public"]["Enums"]["account_type"]
          code?: string
          created_at?: string
          is_active?: boolean
          name?: string
        }
        Relationships: []
      }
      consignment_entries: {
        Row: {
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          document_number: string | null
          exchange_rate_applied: number
          id: string
          journal_entry_id: string | null
          kind: Database["public"]["Enums"]["consignment_kind"]
          notes: string | null
          purpose: Database["public"]["Enums"]["consignment_entry_purpose"]
          reversal_journal_entry_id: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          supplier_id: string | null
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          kind: Database["public"]["Enums"]["consignment_kind"]
          notes?: string | null
          purpose: Database["public"]["Enums"]["consignment_entry_purpose"]
          reversal_journal_entry_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          kind?: Database["public"]["Enums"]["consignment_kind"]
          notes?: string | null
          purpose?: Database["public"]["Enums"]["consignment_entry_purpose"]
          reversal_journal_entry_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id?: string | null
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "consignment_entries_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entries_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entries_reversal_journal_entry_id_fkey"
            columns: ["reversal_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entries_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entries_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      consignment_entry_lines: {
        Row: {
          consignment_entry_id: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          line_no: number
          qty: number
          stock_item_id: string
          unit_cost: number
          unit_price: number
          uom_id: string
        }
        Insert: {
          consignment_entry_id: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no: number
          qty: number
          stock_item_id: string
          unit_cost?: number
          unit_price?: number
          uom_id: string
        }
        Update: {
          consignment_entry_id?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no?: number
          qty?: number
          stock_item_id?: string
          unit_cost?: number
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "consignment_entry_lines_consignment_entry_id_fkey"
            columns: ["consignment_entry_id"]
            isOneToOne: false
            referencedRelation: "consignment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entry_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_entry_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      consignment_stock_levels: {
        Row: {
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          id: string
          kind: Database["public"]["Enums"]["consignment_kind"]
          quantity: number
          stock_item_id: string
          supplier_id: string | null
          unit_cost: number
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          id?: string
          kind: Database["public"]["Enums"]["consignment_kind"]
          quantity?: number
          stock_item_id: string
          supplier_id?: string | null
          unit_cost?: number
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          id?: string
          kind?: Database["public"]["Enums"]["consignment_kind"]
          quantity?: number
          stock_item_id?: string
          supplier_id?: string | null
          unit_cost?: number
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "consignment_stock_levels_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_stock_levels_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_stock_levels_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "consignment_stock_levels_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      contipay_payment_intents: {
        Row: {
          amount: number
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          exchange_rate_applied: number
          external_ref: string
          failure_reason: string | null
          id: string
          last_webhook_at: string | null
          metadata: Json
          method: Database["public"]["Enums"]["contipay_method"]
          payment_entry_id: string | null
          provider_ref: string | null
          settlement_amount: number | null
          settlement_currency:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate: number | null
          status: Database["public"]["Enums"]["contipay_intent_status"]
          updated_at: string
          webhook_payload_hash: string | null
        }
        Insert: {
          amount: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: Json
          method: Database["public"]["Enums"]["contipay_method"]
          payment_entry_id?: string | null
          provider_ref?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["contipay_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
        }
        Update: {
          amount?: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref?: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: Json
          method?: Database["public"]["Enums"]["contipay_method"]
          payment_entry_id?: string | null
          provider_ref?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["contipay_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "contipay_payment_intents_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "contipay_payment_intents_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      contipay_webhook_events: {
        Row: {
          created_at: string
          external_ref: string | null
          id: string
          intent_id: string | null
          payload_hash: string
          processed: boolean
          result_note: string | null
        }
        Insert: {
          created_at?: string
          external_ref?: string | null
          id?: string
          intent_id?: string | null
          payload_hash: string
          processed?: boolean
          result_note?: string | null
        }
        Update: {
          created_at?: string
          external_ref?: string | null
          id?: string
          intent_id?: string | null
          payload_hash?: string
          processed?: boolean
          result_note?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "contipay_webhook_events_intent_id_fkey"
            columns: ["intent_id"]
            isOneToOne: false
            referencedRelation: "contipay_payment_intents"
            referencedColumns: ["id"]
          },
        ]
      }

      chat_messages: {
        Row: {
          body: string
          created_at: string
          id: string
          sender_kind: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id: string
          thread_id: string
        }
        Insert: {
          body: string
          created_at?: string
          id?: string
          sender_kind: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id: string
          thread_id: string
        }
        Update: {
          body?: string
          created_at?: string
          id?: string
          sender_kind?: Database["public"]["Enums"]["chat_sender_kind"]
          sender_user_id?: string
          thread_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_messages_sender_user_id_fkey"
            columns: ["sender_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_messages_thread_id_fkey"
            columns: ["thread_id"]
            isOneToOne: false
            referencedRelation: "chat_threads"
            referencedColumns: ["id"]
          },
        ]
      }
      chat_participants: {
        Row: {
          joined_at: string
          last_read_at: string | null
          role: Database["public"]["Enums"]["chat_participant_role"]
          thread_id: string
          user_id: string
        }
        Insert: {
          joined_at?: string
          last_read_at?: string | null
          role: Database["public"]["Enums"]["chat_participant_role"]
          thread_id: string
          user_id: string
        }
        Update: {
          joined_at?: string
          last_read_at?: string | null
          role?: Database["public"]["Enums"]["chat_participant_role"]
          thread_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_participants_thread_id_fkey"
            columns: ["thread_id"]
            isOneToOne: false
            referencedRelation: "chat_threads"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_participants_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      chat_threads: {
        Row: {
          assigned_at: string | null
          assigned_to: string | null
          closed_at: string | null
          closed_by: string | null
          created_at: string
          customer_id: string | null
          customer_user_id: string
          id: string
          kind: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at: string | null
          status: Database["public"]["Enums"]["chat_thread_status"]
          subject: string | null
          updated_at: string
        }
        Insert: {
          assigned_at?: string | null
          assigned_to?: string | null
          closed_at?: string | null
          closed_by?: string | null
          created_at?: string
          customer_id?: string | null
          customer_user_id: string
          id?: string
          kind?: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at?: string | null
          status?: Database["public"]["Enums"]["chat_thread_status"]
          subject?: string | null
          updated_at?: string
        }
        Update: {
          assigned_at?: string | null
          assigned_to?: string | null
          closed_at?: string | null
          closed_by?: string | null
          created_at?: string
          customer_id?: string | null
          customer_user_id?: string
          id?: string
          kind?: Database["public"]["Enums"]["chat_thread_kind"]
          last_message_at?: string | null
          status?: Database["public"]["Enums"]["chat_thread_status"]
          subject?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "chat_threads_assigned_to_fkey"
            columns: ["assigned_to"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_closed_by_fkey"
            columns: ["closed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "chat_threads_customer_user_id_fkey"
            columns: ["customer_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_garage_vehicles: {
        Row: {
          created_at: string
          customer_id: string
          engine: string | null
          generation: string | null
          id: string
          is_primary: boolean
          make: string | null
          model: string | null
          updated_at: string
          vin: string | null
        }
        Insert: {
          created_at?: string
          customer_id: string
          engine?: string | null
          generation?: string | null
          id?: string
          is_primary?: boolean
          make?: string | null
          model?: string | null
          updated_at?: string
          vin?: string | null
        }
        Update: {
          created_at?: string
          customer_id?: string
          engine?: string | null
          generation?: string | null
          id?: string
          is_primary?: boolean
          make?: string | null
          model?: string | null
          updated_at?: string
          vin?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "customer_garage_vehicles_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_price_overrides: {
        Row: {
          core_charge: number | null
          customer_id: string
          id: string
          stock_item_id: string
          unit_price: number
        }
        Insert: {
          core_charge?: number | null
          customer_id: string
          id?: string
          stock_item_id: string
          unit_price: number
        }
        Update: {
          core_charge?: number | null
          customer_id?: string
          id?: string
          stock_item_id?: string
          unit_price?: number
        }
        Relationships: [
          {
            foreignKeyName: "customer_price_overrides_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_price_overrides_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_product_reviews: {
        Row: {
          body: string
          created_at: string
          customer_id: string
          id: string
          rating: number
          status: Database["public"]["Enums"]["product_review_status"]
          stock_item_id: string
          updated_at: string
        }
        Insert: {
          body?: string
          created_at?: string
          customer_id: string
          id?: string
          rating: number
          status?: Database["public"]["Enums"]["product_review_status"]
          stock_item_id: string
          updated_at?: string
        }
        Update: {
          body?: string
          created_at?: string
          customer_id?: string
          id?: string
          rating?: number
          status?: Database["public"]["Enums"]["product_review_status"]
          stock_item_id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_product_reviews_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_product_reviews_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_receipt_outbox: {
        Row: {
          attempt_count: number
          channel: Database["public"]["Enums"]["receipt_channel"]
          created_at: string
          document_id: string
          document_type: string
          download_url: string | null
          id: string
          last_error: string | null
          pdf_storage_path: string | null
          receipt_pdf_artifact_id: string | null
          recipient: string
          sent_at: string | null
          status: Database["public"]["Enums"]["receipt_outbox_status"]
          summary_body: string
        }
        Insert: {
          attempt_count?: number
          channel: Database["public"]["Enums"]["receipt_channel"]
          created_at?: string
          document_id: string
          document_type: string
          download_url?: string | null
          id?: string
          last_error?: string | null
          pdf_storage_path?: string | null
          receipt_pdf_artifact_id?: string | null
          recipient: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["receipt_outbox_status"]
          summary_body: string
        }
        Update: {
          attempt_count?: number
          channel?: Database["public"]["Enums"]["receipt_channel"]
          created_at?: string
          document_id?: string
          document_type?: string
          download_url?: string | null
          id?: string
          last_error?: string | null
          pdf_storage_path?: string | null
          receipt_pdf_artifact_id?: string | null
          recipient?: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["receipt_outbox_status"]
          summary_body?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_receipt_outbox_document_id_fkey"
            columns: ["document_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_receipt_outbox_receipt_pdf_artifact_id_fkey"
            columns: ["receipt_pdf_artifact_id"]
            isOneToOne: false
            referencedRelation: "receipt_pdf_artifacts"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_wishlist_items: {
        Row: {
          created_at: string
          customer_id: string
          id: string
          stock_item_id: string
        }
        Insert: {
          created_at?: string
          customer_id: string
          id?: string
          stock_item_id: string
        }
        Update: {
          created_at?: string
          customer_id?: string
          id?: string
          stock_item_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_wishlist_items_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_wishlist_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      customers: {
        Row: {
          created_at: string
          credit_hold: boolean
          credit_limit: number
          currency: Database["public"]["Enums"]["currency_code"]
          display_name: string
          email: string | null
          email_receipts: boolean
          id: string
          open_balance: number
          phone_e164: string | null
          price_list_id: string | null
          profile_id: string | null
          sms_receipts: boolean
          updated_at: string
          whatsapp_e164: string | null
          whatsapp_receipts: boolean
        }
        Insert: {
          created_at?: string
          credit_hold?: boolean
          credit_limit?: number
          currency?: Database["public"]["Enums"]["currency_code"]
          display_name: string
          email?: string | null
          email_receipts?: boolean
          id?: string
          open_balance?: number
          phone_e164?: string | null
          price_list_id?: string | null
          profile_id?: string | null
          sms_receipts?: boolean
          updated_at?: string
          whatsapp_e164?: string | null
          whatsapp_receipts?: boolean
        }
        Update: {
          created_at?: string
          credit_hold?: boolean
          credit_limit?: number
          currency?: Database["public"]["Enums"]["currency_code"]
          display_name?: string
          email?: string | null
          email_receipts?: boolean
          id?: string
          open_balance?: number
          phone_e164?: string | null
          price_list_id?: string | null
          profile_id?: string | null
          sms_receipts?: boolean
          updated_at?: string
          whatsapp_e164?: string | null
          whatsapp_receipts?: boolean
        }
        Relationships: [
          {
            foreignKeyName: "customers_price_list_fk"
            columns: ["price_list_id"]
            isOneToOne: false
            referencedRelation: "price_lists"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customers_profile_id_fkey"
            columns: ["profile_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_jobs: {
        Row: {
          assignee_user_id: string | null
          completed_at: string | null
          completed_via: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at: string
          created_by: string | null
          delivery_note_id: string
          dispatched_at: string | null
          document_number: string | null
          dropoff_lat: number | null
          dropoff_lng: number | null
          eta_at: string | null
          eta_seconds: number | null
          eta_source: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at: string | null
          failed_at: string | null
          failure_reason: string | null
          failure_reason_code: Database["public"]["Enums"]["delivery_failure_reason"] | null
          reattempt_of: string | null
          route_sequence: number | null
          id: string
          notes: string | null
          pickup_lat: number | null
          pickup_lng: number | null
          pod_photo_path: string | null
          pod_signature_path: string | null
          status: Database["public"]["Enums"]["delivery_job_status"]
          updated_at: string
        }
        Insert: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at?: string
          created_by?: string | null
          delivery_note_id: string
          dispatched_at?: string | null
          document_number?: string | null
          dropoff_lat?: number | null
          dropoff_lng?: number | null
          eta_at?: string | null
          eta_seconds?: number | null
          eta_source?: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at?: string | null
          failed_at?: string | null
          failure_reason?: string | null
          failure_reason_code?: Database["public"]["Enums"]["delivery_failure_reason"] | null
          reattempt_of?: string | null
          route_sequence?: number | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          status?: Database["public"]["Enums"]["delivery_job_status"]
          updated_at?: string
        }
        Update: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?: Database["public"]["Enums"]["delivery_completed_via"] | null
          created_at?: string
          created_by?: string | null
          delivery_note_id?: string
          dispatched_at?: string | null
          document_number?: string | null
          dropoff_lat?: number | null
          dropoff_lng?: number | null
          eta_at?: string | null
          eta_seconds?: number | null
          eta_source?: Database["public"]["Enums"]["delivery_eta_source"] | null
          eta_updated_at?: string | null
          failed_at?: string | null
          failure_reason?: string | null
          failure_reason_code?: Database["public"]["Enums"]["delivery_failure_reason"] | null
          reattempt_of?: string | null
          route_sequence?: number | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          status?: Database["public"]["Enums"]["delivery_job_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_jobs_delivery_note_id_fkey"
            columns: ["delivery_note_id"]
            isOneToOne: false
            referencedRelation: "delivery_notes"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_jobs_reattempt_of_fkey"
            columns: ["reattempt_of"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_track_tokens: {
        Row: {
          created_at: string
          delivery_job_id: string
          expires_at: string
          id: string
          revoked_at: string | null
          token_hash: string
        }
        Insert: {
          created_at?: string
          delivery_job_id: string
          expires_at: string
          id?: string
          revoked_at?: string | null
          token_hash: string
        }
        Update: {
          created_at?: string
          delivery_job_id?: string
          expires_at?: string
          id?: string
          revoked_at?: string | null
          token_hash?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_track_tokens_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_pod_otps: {
        Row: {
          attempts: number
          code_hash: string
          created_at: string
          delivery_job_id: string
          expires_at: string
          id: string
          max_attempts: number
          verified_at: string | null
        }
        Insert: {
          attempts?: number
          code_hash: string
          created_at?: string
          delivery_job_id: string
          expires_at: string
          id?: string
          max_attempts?: number
          verified_at?: string | null
        }
        Update: {
          attempts?: number
          code_hash?: string
          created_at?: string
          delivery_job_id?: string
          expires_at?: string
          id?: string
          max_attempts?: number
          verified_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "delivery_pod_otps_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
      panic_events: {
        Row: {
          acknowledged_at: string | null
          acknowledged_by: string | null
          created_at: string
          delivery_job_id: string | null
          driver_user_id: string
          id: string
          lat: number | null
          lng: number | null
        }
        Insert: {
          acknowledged_at?: string | null
          acknowledged_by?: string | null
          created_at?: string
          delivery_job_id?: string | null
          driver_user_id: string
          id?: string
          lat?: number | null
          lng?: number | null
        }
        Update: {
          acknowledged_at?: string | null
          acknowledged_by?: string | null
          created_at?: string
          delivery_job_id?: string | null
          driver_user_id?: string
          id?: string
          lat?: number | null
          lng?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "panic_events_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "panic_events_driver_user_id_fkey"
            columns: ["driver_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "panic_events_acknowledged_by_fkey"
            columns: ["acknowledged_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }

      driver_presence: {
        Row: {
          capacity: number
          last_lat: number | null
          last_lng: number | null
          last_seen_at: string | null
          shift_ends_at: string | null
          shift_starts_at: string | null
          status: Database["public"]["Enums"]["driver_presence_status"]
          updated_at: string
          user_id: string
        }
        Insert: {
          capacity?: number
          last_lat?: number | null
          last_lng?: number | null
          last_seen_at?: string | null
          shift_ends_at?: string | null
          shift_starts_at?: string | null
          status?: Database["public"]["Enums"]["driver_presence_status"]
          updated_at?: string
          user_id: string
        }
        Update: {
          capacity?: number
          last_lat?: number | null
          last_lng?: number | null
          last_seen_at?: string | null
          shift_ends_at?: string | null
          shift_starts_at?: string | null
          status?: Database["public"]["Enums"]["driver_presence_status"]
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "driver_presence_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_locations: {
        Row: {
          accuracy_m: number | null
          delivery_job_id: string
          id: string
          ingested_at: string
          lat: number
          lng: number
          recorded_at: string
          source: string
        }
        Insert: {
          accuracy_m?: number | null
          delivery_job_id: string
          id?: string
          ingested_at?: string
          lat: number
          lng: number
          recorded_at: string
          source?: string
        }
        Update: {
          accuracy_m?: number | null
          delivery_job_id?: string
          id?: string
          ingested_at?: string
          lat?: number
          lng?: number
          recorded_at?: string
          source?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_locations_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_note_lines: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_note_id: string
          id: string
          qty: number
          qty_base: number
          sales_invoice_line_id: string
          stock_item_id: string
          unit_cost: number
          uom_id: string
        }
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_note_id: string
          id?: string
          qty: number
          qty_base: number
          sales_invoice_line_id: string
          stock_item_id: string
          unit_cost?: number
          uom_id: string
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_note_id?: string
          id?: string
          qty?: number
          qty_base?: number
          sales_invoice_line_id?: string
          stock_item_id?: string
          unit_cost?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_note_lines_delivery_note_id_fkey"
            columns: ["delivery_note_id"]
            isOneToOne: false
            referencedRelation: "delivery_notes"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_note_lines_sales_invoice_line_id_fkey"
            columns: ["sales_invoice_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_note_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_note_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_notes: {
        Row: {
          cancelled_at: string | null
          cogs_journal_entry_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number
          id: string
          pick_list_id: string | null
          reverse_journal_entry_id: string | null
          sales_invoice_id: string
          status: Database["public"]["Enums"]["delivery_note_status"]
          stock_entry_id: string | null
          submitted_at: string | null
          submitted_by: string | null
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          cancelled_at?: string | null
          cogs_journal_entry_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          pick_list_id?: string | null
          reverse_journal_entry_id?: string | null
          sales_invoice_id: string
          status?: Database["public"]["Enums"]["delivery_note_status"]
          stock_entry_id?: string | null
          submitted_at?: string | null
          submitted_by?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          cancelled_at?: string | null
          cogs_journal_entry_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          pick_list_id?: string | null
          reverse_journal_entry_id?: string | null
          sales_invoice_id?: string
          status?: Database["public"]["Enums"]["delivery_note_status"]
          stock_entry_id?: string | null
          submitted_at?: string | null
          submitted_by?: string | null
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_notes_cogs_journal_entry_id_fkey"
            columns: ["cogs_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_notes_pick_list_id_fkey"
            columns: ["pick_list_id"]
            isOneToOne: false
            referencedRelation: "pick_lists"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_notes_reverse_journal_entry_id_fkey"
            columns: ["reverse_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_notes_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_notes_stock_entry_id_fkey"
            columns: ["stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_notes_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      domain_events: {
        Row: {
          actor_user_id: string | null
          dedupe_key: string
          event_code: string
          id: string
          occurred_at: string
          payload: Json
        }
        Insert: {
          actor_user_id?: string | null
          dedupe_key: string
          event_code: string
          id?: string
          occurred_at?: string
          payload?: Json
        }
        Update: {
          actor_user_id?: string | null
          dedupe_key?: string
          event_code?: string
          id?: string
          occurred_at?: string
          payload?: Json
        }
        Relationships: [
          {
            foreignKeyName: "domain_events_event_code_fkey"
            columns: ["event_code"]
            isOneToOne: false
            referencedRelation: "sms_event_catalog"
            referencedColumns: ["code"]
          },
        ]
      }
      employees: {
        Row: {
          created_at: string
          email: string | null
          employee_code: string
          full_name: string
          hire_date: string | null
          id: string
          phone_e164: string | null
          status: Database["public"]["Enums"]["employee_status"]
          updated_at: string
          user_id: string | null
        }
        Insert: {
          created_at?: string
          email?: string | null
          employee_code: string
          full_name: string
          hire_date?: string | null
          id?: string
          phone_e164?: string | null
          status?: Database["public"]["Enums"]["employee_status"]
          updated_at?: string
          user_id?: string | null
        }
        Update: {
          created_at?: string
          email?: string | null
          employee_code?: string
          full_name?: string
          hire_date?: string | null
          id?: string
          phone_e164?: string | null
          status?: Database["public"]["Enums"]["employee_status"]
          updated_at?: string
          user_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "employees_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      forecast_suggestions: {
        Row: {
          converted_at: string | null
          created_at: string
          horizon_days: number
          id: string
          material_request_id: string | null
          on_hand: number
          reason: Json
          status: Database["public"]["Enums"]["forecast_suggestion_status"]
          stock_item_id: string
          suggested_qty: number
          warehouse_id: string
        }
        Insert: {
          converted_at?: string | null
          created_at?: string
          horizon_days?: number
          id?: string
          material_request_id?: string | null
          on_hand?: number
          reason?: Json
          status?: Database["public"]["Enums"]["forecast_suggestion_status"]
          stock_item_id: string
          suggested_qty: number
          warehouse_id: string
        }
        Update: {
          converted_at?: string | null
          created_at?: string
          horizon_days?: number
          id?: string
          material_request_id?: string | null
          on_hand?: number
          reason?: Json
          status?: Database["public"]["Enums"]["forecast_suggestion_status"]
          stock_item_id?: string
          suggested_qty?: number
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "forecast_suggestions_material_request_id_fkey"
            columns: ["material_request_id"]
            isOneToOne: false
            referencedRelation: "material_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "forecast_suggestions_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "forecast_suggestions_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      goods_receipt_lines: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          goods_receipt_id: string
          id: string
          price_variance_pct: number | null
          purchase_order_line_id: string
          qty: number
          stock_entry_line_id: string | null
          stock_item_id: string
          unit_cost: number
          uom_id: string
        }
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          goods_receipt_id: string
          id?: string
          price_variance_pct?: number | null
          purchase_order_line_id: string
          qty: number
          stock_entry_line_id?: string | null
          stock_item_id: string
          unit_cost: number
          uom_id: string
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          goods_receipt_id?: string
          id?: string
          price_variance_pct?: number | null
          purchase_order_line_id?: string
          qty?: number
          stock_entry_line_id?: string | null
          stock_item_id?: string
          unit_cost?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "goods_receipt_lines_goods_receipt_id_fkey"
            columns: ["goods_receipt_id"]
            isOneToOne: false
            referencedRelation: "goods_receipts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipt_lines_purchase_order_line_id_fkey"
            columns: ["purchase_order_line_id"]
            isOneToOne: false
            referencedRelation: "purchase_order_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipt_lines_stock_entry_line_id_fkey"
            columns: ["stock_entry_line_id"]
            isOneToOne: false
            referencedRelation: "stock_entry_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipt_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipt_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      goods_receipts: {
        Row: {
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          document_number: string | null
          id: string
          notes: string | null
          purchase_order_id: string
          status: Database["public"]["Enums"]["procurement_doc_status"]
          stock_entry_id: string | null
          submitted_at: string | null
          supplier_id: string
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          notes?: string | null
          purchase_order_id: string
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          stock_entry_id?: string | null
          submitted_at?: string | null
          supplier_id: string
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          notes?: string | null
          purchase_order_id?: string
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          stock_entry_id?: string | null
          submitted_at?: string | null
          supplier_id?: string
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "goods_receipts_purchase_order_id_fkey"
            columns: ["purchase_order_id"]
            isOneToOne: false
            referencedRelation: "purchase_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipts_stock_entry_id_fkey"
            columns: ["stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipts_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "goods_receipts_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      inventory_qr_codes: {
        Row: {
          batch_id: string
          generated_at: string
          id: string
          oem_part_number: string
          payload: string | null
          printed_at: string | null
          stock_batch_id: string | null
          stock_entry_line_id: string | null
          stock_item_id: string | null
          valuation_method: Database["public"]["Enums"]["valuation_method"]
        }
        Insert: {
          batch_id: string
          generated_at?: string
          id?: string
          oem_part_number: string
          payload?: string | null
          printed_at?: string | null
          stock_batch_id?: string | null
          stock_entry_line_id?: string | null
          stock_item_id?: string | null
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
        }
        Update: {
          batch_id?: string
          generated_at?: string
          id?: string
          oem_part_number?: string
          payload?: string | null
          printed_at?: string | null
          stock_batch_id?: string | null
          stock_entry_line_id?: string | null
          stock_item_id?: string | null
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
        }
        Relationships: [
          {
            foreignKeyName: "inventory_qr_codes_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_qr_codes_stock_entry_line_id_fkey"
            columns: ["stock_entry_line_id"]
            isOneToOne: false
            referencedRelation: "stock_entry_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_qr_codes_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      item_kit_components: {
        Row: {
          component_item_id: string
          created_at: string
          id: string
          kit_id: string
          qty: number
          uom_id: string
        }
        Insert: {
          component_item_id: string
          created_at?: string
          id?: string
          kit_id: string
          qty: number
          uom_id: string
        }
        Update: {
          component_item_id?: string
          created_at?: string
          id?: string
          kit_id?: string
          qty?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "item_kit_components_component_item_id_fkey"
            columns: ["component_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "item_kit_components_kit_id_fkey"
            columns: ["kit_id"]
            isOneToOne: false
            referencedRelation: "item_kits"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "item_kit_components_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      item_kits: {
        Row: {
          created_at: string
          created_by: string | null
          id: string
          is_active: boolean
          sell_mode: Database["public"]["Enums"]["kit_sell_mode"]
          stock_item_id: string
          updated_at: string
        }
        Insert: {
          created_at?: string
          created_by?: string | null
          id?: string
          is_active?: boolean
          sell_mode?: Database["public"]["Enums"]["kit_sell_mode"]
          stock_item_id: string
          updated_at?: string
        }
        Update: {
          created_at?: string
          created_by?: string | null
          id?: string
          is_active?: boolean
          sell_mode?: Database["public"]["Enums"]["kit_sell_mode"]
          stock_item_id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "item_kits_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: true
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      item_uom_conversions: {
        Row: {
          factor: number
          from_uom_id: string
          id: string
          stock_item_id: string
          to_uom_id: string
        }
        Insert: {
          factor: number
          from_uom_id: string
          id?: string
          stock_item_id: string
          to_uom_id: string
        }
        Update: {
          factor?: number
          from_uom_id?: string
          id?: string
          stock_item_id?: string
          to_uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "item_uom_conversions_from_uom_id_fkey"
            columns: ["from_uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "item_uom_conversions_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "item_uom_conversions_to_uom_id_fkey"
            columns: ["to_uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      journal_entries: {
        Row: {
          currency: Database["public"]["Enums"]["currency_code"]
          description: string | null
          document_number: string | null
          entry_date: string
          exchange_rate_applied: number | null
          id: string
          is_reversal: boolean
          posted_at: string
          posted_by: string | null
          reverses_entry_id: string | null
          status: Database["public"]["Enums"]["journal_status"]
        }
        Insert: {
          currency: Database["public"]["Enums"]["currency_code"]
          description?: string | null
          document_number?: string | null
          entry_date?: string
          exchange_rate_applied?: number | null
          id?: string
          is_reversal?: boolean
          posted_at?: string
          posted_by?: string | null
          reverses_entry_id?: string | null
          status?: Database["public"]["Enums"]["journal_status"]
        }
        Update: {
          currency?: Database["public"]["Enums"]["currency_code"]
          description?: string | null
          document_number?: string | null
          entry_date?: string
          exchange_rate_applied?: number | null
          id?: string
          is_reversal?: boolean
          posted_at?: string
          posted_by?: string | null
          reverses_entry_id?: string | null
          status?: Database["public"]["Enums"]["journal_status"]
        }
        Relationships: [
          {
            foreignKeyName: "journal_entries_reverses_entry_id_fkey"
            columns: ["reverses_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      journal_entry_lines: {
        Row: {
          account_code: string
          credit: number
          currency: Database["public"]["Enums"]["currency_code"]
          debit: number
          id: string
          journal_entry_id: string
        }
        Insert: {
          account_code: string
          credit?: number
          currency: Database["public"]["Enums"]["currency_code"]
          debit?: number
          id?: string
          journal_entry_id: string
        }
        Update: {
          account_code?: string
          credit?: number
          currency?: Database["public"]["Enums"]["currency_code"]
          debit?: number
          id?: string
          journal_entry_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "journal_entry_lines_account_code_fkey"
            columns: ["account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "journal_entry_lines_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      landed_cost_allocations: {
        Row: {
          allocated_amount: number
          created_at: string
          id: string
          landed_cost_voucher_id: string
          prior_unit_cost: number
          qty_on_hand_snapshot: number
          stock_batch_id: string
        }
        Insert: {
          allocated_amount: number
          created_at?: string
          id?: string
          landed_cost_voucher_id: string
          prior_unit_cost: number
          qty_on_hand_snapshot: number
          stock_batch_id: string
        }
        Update: {
          allocated_amount?: number
          created_at?: string
          id?: string
          landed_cost_voucher_id?: string
          prior_unit_cost?: number
          qty_on_hand_snapshot?: number
          stock_batch_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "landed_cost_allocations_landed_cost_voucher_id_fkey"
            columns: ["landed_cost_voucher_id"]
            isOneToOne: false
            referencedRelation: "landed_cost_vouchers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "landed_cost_allocations_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
        ]
      }
      landed_cost_charges: {
        Row: {
          amount: number
          charge_type: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          landed_cost_voucher_id: string
        }
        Insert: {
          amount: number
          charge_type: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          landed_cost_voucher_id: string
        }
        Update: {
          amount?: number
          charge_type?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          landed_cost_voucher_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "landed_cost_charges_landed_cost_voucher_id_fkey"
            columns: ["landed_cost_voucher_id"]
            isOneToOne: false
            referencedRelation: "landed_cost_vouchers"
            referencedColumns: ["id"]
          },
        ]
      }
      landed_cost_vouchers: {
        Row: {
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number
          goods_receipt_id: string | null
          id: string
          journal_entry_id: string | null
          notes: string | null
          reversal_journal_entry_id: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          updated_at: string
        }
        Insert: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          goods_receipt_id?: string | null
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          reversal_journal_entry_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Update: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          goods_receipt_id?: string | null
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          reversal_journal_entry_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "landed_cost_vouchers_goods_receipt_id_fkey"
            columns: ["goods_receipt_id"]
            isOneToOne: false
            referencedRelation: "goods_receipts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "landed_cost_vouchers_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "landed_cost_vouchers_reversal_journal_entry_id_fkey"
            columns: ["reversal_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      loyalty_accounts: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id: string
          points_balance: number
          updated_at: string
        }
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id?: string
          points_balance?: number
          updated_at?: string
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          id?: string
          points_balance?: number
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "loyalty_accounts_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: true
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
        ]
      }
      loyalty_ledger: {
        Row: {
          account_id: string
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string | null
          exchange_rate_applied: number
          id: string
          journal_entry_id: string | null
          money_value: number
          movement: Database["public"]["Enums"]["loyalty_movement"]
          points: number
          points_balance_after: number
          reason: string | null
          reverses_ledger_id: string | null
          sales_invoice_id: string | null
        }
        Insert: {
          account_id: string
          created_at?: string
          created_by?: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          money_value: number
          movement: Database["public"]["Enums"]["loyalty_movement"]
          points: number
          points_balance_after: number
          reason?: string | null
          reverses_ledger_id?: string | null
          sales_invoice_id?: string | null
        }
        Update: {
          account_id?: string
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          money_value?: number
          movement?: Database["public"]["Enums"]["loyalty_movement"]
          points?: number
          points_balance_after?: number
          reason?: string | null
          reverses_ledger_id?: string | null
          sales_invoice_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "loyalty_ledger_account_id_fkey"
            columns: ["account_id"]
            isOneToOne: false
            referencedRelation: "loyalty_accounts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "loyalty_ledger_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "loyalty_ledger_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "loyalty_ledger_reverses_ledger_id_fkey"
            columns: ["reverses_ledger_id"]
            isOneToOne: false
            referencedRelation: "loyalty_ledger"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "loyalty_ledger_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      loyalty_program_settings: {
        Row: {
          currency: Database["public"]["Enums"]["currency_code"]
          id: number
          is_active: boolean
          liability_per_point: number
          points_per_currency_unit: number
          updated_at: string
          updated_by: string | null
        }
        Insert: {
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: number
          is_active?: boolean
          liability_per_point?: number
          points_per_currency_unit?: number
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: number
          is_active?: boolean
          liability_per_point?: number
          points_per_currency_unit?: number
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: []
      }
      manager_sms_preferences: {
        Row: {
          created_at: string
          enabled: boolean
          event_code: string
          id: string
          phone_e164: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          enabled?: boolean
          event_code: string
          id?: string
          phone_e164: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          enabled?: boolean
          event_code?: string
          id?: string
          phone_e164?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "manager_sms_preferences_event_code_fkey"
            columns: ["event_code"]
            isOneToOne: false
            referencedRelation: "sms_event_catalog"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "manager_sms_preferences_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      material_request_lines: {
        Row: {
          created_at: string
          id: string
          line_no: number
          material_request_id: string
          purchase_order_line_id: string | null
          qty: number
          qty_converted: number
          stock_item_id: string
          uom_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          line_no: number
          material_request_id: string
          purchase_order_line_id?: string | null
          qty: number
          qty_converted?: number
          stock_item_id: string
          uom_id: string
        }
        Update: {
          created_at?: string
          id?: string
          line_no?: number
          material_request_id?: string
          purchase_order_line_id?: string | null
          qty?: number
          qty_converted?: number
          stock_item_id?: string
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "material_request_lines_material_request_id_fkey"
            columns: ["material_request_id"]
            isOneToOne: false
            referencedRelation: "material_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "material_request_lines_po_line_fk"
            columns: ["purchase_order_line_id"]
            isOneToOne: false
            referencedRelation: "purchase_order_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "material_request_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "material_request_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      material_requests: {
        Row: {
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          document_number: string | null
          id: string
          needed_by: string | null
          notes: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "material_requests_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      naming_series: {
        Row: {
          current_value: number
          description: string | null
          pad_length: number
          prefix: string
          updated_at: string
        }
        Insert: {
          current_value?: number
          description?: string | null
          pad_length?: number
          prefix: string
          updated_at?: string
        }
        Update: {
          current_value?: number
          description?: string | null
          pad_length?: number
          prefix?: string
          updated_at?: string
        }
        Relationships: []
      }
      oe_cross_refs: {
        Row: {
          brand: string
          created_at: string
          id: string
          oe_number: string
          oem_part_number: string
        }
        Insert: {
          brand: string
          created_at?: string
          id?: string
          oe_number: string
          oem_part_number: string
        }
        Update: {
          brand?: string
          created_at?: string
          id?: string
          oe_number?: string
          oem_part_number?: string
        }
        Relationships: []
      }
      part_fitment: {
        Row: {
          bbox_height: number | null
          bbox_width: number | null
          bbox_x: number | null
          bbox_y: number | null
          chassis_code: string | null
          created_at: string
          diagram_path: string | null
          engine_code: string | null
          id: string
          oem_part_number: string
          pnc_code: string | null
          search_vector: unknown
          superseded_by: string | null
        }
        Insert: {
          bbox_height?: number | null
          bbox_width?: number | null
          bbox_x?: number | null
          bbox_y?: number | null
          chassis_code?: string | null
          created_at?: string
          diagram_path?: string | null
          engine_code?: string | null
          id?: string
          oem_part_number: string
          pnc_code?: string | null
          search_vector?: unknown
          superseded_by?: string | null
        }
        Update: {
          bbox_height?: number | null
          bbox_width?: number | null
          bbox_x?: number | null
          bbox_y?: number | null
          chassis_code?: string | null
          created_at?: string
          diagram_path?: string | null
          engine_code?: string | null
          id?: string
          oem_part_number?: string
          pnc_code?: string | null
          search_vector?: unknown
          superseded_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "part_fitment_pnc_code_fkey"
            columns: ["pnc_code"]
            isOneToOne: false
            referencedRelation: "pnc_categories"
            referencedColumns: ["pnc_code"]
          },
        ]
      }
      payment_allocations: {
        Row: {
          amount: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          exchange_rate_applied: number
          id: string
          payment_entry_id: string
          sales_invoice_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          exchange_rate_applied?: number
          id?: string
          payment_entry_id: string
          sales_invoice_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          exchange_rate_applied?: number
          id?: string
          payment_entry_id?: string
          sales_invoice_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "payment_allocations_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_allocations_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      payment_entries: {
        Row: {
          amount: number
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string | null
          exchange_rate_applied: number
          id: string
          journal_entry_id: string | null
          notes: string | null
          posted_at: string | null
          posted_by: string | null
          reversal_journal_entry_id: string | null
          settlement_amount: number | null
          settlement_currency:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate: number | null
          status: Database["public"]["Enums"]["payment_entry_status"]
          store_credit_issued: number
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at: string
        }
        Insert: {
          amount: number
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string | null
          posted_by?: string | null
          reversal_journal_entry_id?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["payment_entry_status"]
          store_credit_issued?: number
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Update: {
          amount?: number
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string | null
          posted_by?: string | null
          reversal_journal_entry_id?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["payment_entry_status"]
          store_credit_issued?: number
          tender?: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "payment_entries_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_entries_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_entries_reversal_journal_entry_id_fkey"
            columns: ["reversal_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      paynow_payment_intents: {
        Row: {
          amount: number
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          exchange_rate_applied: number
          external_ref: string
          failure_reason: string | null
          id: string
          last_webhook_at: string | null
          metadata: Json
          method: Database["public"]["Enums"]["paynow_method"]
          payment_entry_id: string | null
          provider_ref: string | null
          settlement_amount: number | null
          settlement_currency:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate: number | null
          status: Database["public"]["Enums"]["paynow_intent_status"]
          updated_at: string
          webhook_payload_hash: string | null
        }
        Insert: {
          amount: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: Json
          method: Database["public"]["Enums"]["paynow_method"]
          payment_entry_id?: string | null
          provider_ref?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["paynow_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
        }
        Update: {
          amount?: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref?: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: Json
          method?: Database["public"]["Enums"]["paynow_method"]
          payment_entry_id?: string | null
          provider_ref?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["paynow_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "paynow_payment_intents_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "paynow_payment_intents_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      paynow_webhook_events: {
        Row: {
          created_at: string
          external_ref: string | null
          id: string
          intent_id: string | null
          payload_hash: string
          processed: boolean
          result_note: string | null
        }
        Insert: {
          created_at?: string
          external_ref?: string | null
          id?: string
          intent_id?: string | null
          payload_hash: string
          processed?: boolean
          result_note?: string | null
        }
        Update: {
          created_at?: string
          external_ref?: string | null
          id?: string
          intent_id?: string | null
          payload_hash?: string
          processed?: boolean
          result_note?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "paynow_webhook_events_intent_id_fkey"
            columns: ["intent_id"]
            isOneToOne: false
            referencedRelation: "paynow_payment_intents"
            referencedColumns: ["id"]
          },
        ]
      }
      payroll_deduction_lines: {
        Row: {
          amount: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          label: string
          payroll_line_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          id?: string
          label: string
          payroll_line_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          label?: string
          payroll_line_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "payroll_deduction_lines_payroll_line_id_fkey"
            columns: ["payroll_line_id"]
            isOneToOne: false
            referencedRelation: "payroll_lines"
            referencedColumns: ["id"]
          },
        ]
      }
      payroll_lines: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          deductions_amount: number
          employee_id: string
          exchange_rate: number
          gross_amount: number
          hours_worked: number
          id: string
          line_no: number
          net_amount: number
          pay_type: Database["public"]["Enums"]["salary_pay_type"]
          payroll_run_id: string
          rate_applied: number
          salary_structure_id: string | null
        }
        Insert: {
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          deductions_amount?: number
          employee_id: string
          exchange_rate?: number
          gross_amount: number
          hours_worked?: number
          id?: string
          line_no: number
          net_amount: number
          pay_type: Database["public"]["Enums"]["salary_pay_type"]
          payroll_run_id: string
          rate_applied: number
          salary_structure_id?: string | null
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          deductions_amount?: number
          employee_id?: string
          exchange_rate?: number
          gross_amount?: number
          hours_worked?: number
          id?: string
          line_no?: number
          net_amount?: number
          pay_type?: Database["public"]["Enums"]["salary_pay_type"]
          payroll_run_id?: string
          rate_applied?: number
          salary_structure_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "payroll_lines_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payroll_lines_payroll_run_id_fkey"
            columns: ["payroll_run_id"]
            isOneToOne: false
            referencedRelation: "payroll_runs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payroll_lines_salary_structure_id_fkey"
            columns: ["salary_structure_id"]
            isOneToOne: false
            referencedRelation: "salary_structures"
            referencedColumns: ["id"]
          },
        ]
      }
      payroll_runs: {
        Row: {
          cancel_reason: string | null
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate: number
          id: string
          notes: string | null
          period_end: string
          period_start: string
          status: Database["public"]["Enums"]["payroll_run_status"]
          submitted_at: string | null
          total_deductions: number
          total_gross: number
          total_net: number
          updated_at: string
        }
        Insert: {
          cancel_reason?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate?: number
          id?: string
          notes?: string | null
          period_end: string
          period_start: string
          status?: Database["public"]["Enums"]["payroll_run_status"]
          submitted_at?: string | null
          total_deductions?: number
          total_gross?: number
          total_net?: number
          updated_at?: string
        }
        Update: {
          cancel_reason?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate?: number
          id?: string
          notes?: string | null
          period_end?: string
          period_start?: string
          status?: Database["public"]["Enums"]["payroll_run_status"]
          submitted_at?: string | null
          total_deductions?: number
          total_gross?: number
          total_net?: number
          updated_at?: string
        }
        Relationships: []
      }
      payslips: {
        Row: {
          created_at: string
          employee_id: string
          generated_at: string
          generated_by: string | null
          id: string
          mime_type: string
          payroll_line_id: string
          payroll_run_id: string
          storage_bucket: string
          storage_path: string
        }
        Insert: {
          created_at?: string
          employee_id: string
          generated_at?: string
          generated_by?: string | null
          id?: string
          mime_type?: string
          payroll_line_id: string
          payroll_run_id: string
          storage_bucket?: string
          storage_path: string
        }
        Update: {
          created_at?: string
          employee_id?: string
          generated_at?: string
          generated_by?: string | null
          id?: string
          mime_type?: string
          payroll_line_id?: string
          payroll_run_id?: string
          storage_bucket?: string
          storage_path?: string
        }
        Relationships: [
          {
            foreignKeyName: "payslips_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payslips_payroll_line_id_fkey"
            columns: ["payroll_line_id"]
            isOneToOne: true
            referencedRelation: "payroll_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payslips_payroll_run_id_fkey"
            columns: ["payroll_run_id"]
            isOneToOne: false
            referencedRelation: "payroll_runs"
            referencedColumns: ["id"]
          },
        ]
      }
      pick_list_lines: {
        Row: {
          created_at: string
          id: string
          pick_list_id: string
          qty_base_picked: number
          qty_base_requested: number
          qty_picked: number
          qty_requested: number
          sales_invoice_line_id: string
          stock_item_id: string
          suggested_bin_id: string | null
          uom_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          pick_list_id: string
          qty_base_picked?: number
          qty_base_requested: number
          qty_picked?: number
          qty_requested: number
          sales_invoice_line_id: string
          stock_item_id: string
          suggested_bin_id?: string | null
          uom_id: string
        }
        Update: {
          created_at?: string
          id?: string
          pick_list_id?: string
          qty_base_picked?: number
          qty_base_requested?: number
          qty_picked?: number
          qty_requested?: number
          sales_invoice_line_id?: string
          stock_item_id?: string
          suggested_bin_id?: string | null
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pick_list_lines_pick_list_id_fkey"
            columns: ["pick_list_id"]
            isOneToOne: false
            referencedRelation: "pick_lists"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pick_list_lines_sales_invoice_line_id_fkey"
            columns: ["sales_invoice_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pick_list_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pick_list_lines_suggested_bin_id_fkey"
            columns: ["suggested_bin_id"]
            isOneToOne: false
            referencedRelation: "warehouse_bins"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pick_list_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      pick_lists: {
        Row: {
          cancelled_at: string | null
          confirmed_at: string | null
          created_at: string
          created_by: string | null
          document_number: string | null
          id: string
          sales_invoice_id: string
          status: Database["public"]["Enums"]["pick_list_status"]
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          cancelled_at?: string | null
          confirmed_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          sales_invoice_id: string
          status?: Database["public"]["Enums"]["pick_list_status"]
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          cancelled_at?: string | null
          confirmed_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          sales_invoice_id?: string
          status?: Database["public"]["Enums"]["pick_list_status"]
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pick_lists_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pick_lists_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      pnc_categories: {
        Row: {
          category_name: string
          created_at: string
          pnc_code: string
          search_vector: unknown
          subcategory_name: string | null
        }
        Insert: {
          category_name: string
          created_at?: string
          pnc_code: string
          search_vector?: unknown
          subcategory_name?: string | null
        }
        Update: {
          category_name?: string
          created_at?: string
          pnc_code?: string
          search_vector?: unknown
          subcategory_name?: string | null
        }
        Relationships: []
      }
      pos_cart_lines: {
        Row: {
          cart_id: string
          created_at: string
          id: string
          is_core_charge: boolean
          issues_stock: boolean
          kit_id: string | null
          kit_line_kind: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total: number
          parent_line_id: string | null
          qty: number
          qty_base: number
          qty_fulfilled: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Insert: {
          cart_id: string
          created_at?: string
          id?: string
          is_core_charge?: boolean
          issues_stock?: boolean
          kit_id?: string | null
          kit_line_kind?: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total: number
          parent_line_id?: string | null
          qty: number
          qty_base: number
          qty_fulfilled?: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          cart_id?: string
          created_at?: string
          id?: string
          is_core_charge?: boolean
          issues_stock?: boolean
          kit_id?: string | null
          kit_line_kind?: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total?: number
          parent_line_id?: string | null
          qty?: number
          qty_base?: number
          qty_fulfilled?: number
          stock_item_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_cart_lines_cart_id_fkey"
            columns: ["cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_cart_lines_kit_id_fkey"
            columns: ["kit_id"]
            isOneToOne: false
            referencedRelation: "item_kits"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_cart_lines_parent_line_id_fkey"
            columns: ["parent_line_id"]
            isOneToOne: false
            referencedRelation: "pos_cart_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_cart_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_cart_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_carts: {
        Row: {
          channel: Database["public"]["Enums"]["cart_channel"]
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          document_number: string | null
          exchange_rate_applied: number
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id: string
          status: string
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          channel?: Database["public"]["Enums"]["cart_channel"]
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          status?: string
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          channel?: Database["public"]["Enums"]["cart_channel"]
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          status?: string
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_carts_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_carts_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      price_list_items: {
        Row: {
          core_charge: number
          id: string
          price_list_id: string
          stock_item_id: string
          unit_price: number
        }
        Insert: {
          core_charge?: number
          id?: string
          price_list_id: string
          stock_item_id: string
          unit_price: number
        }
        Update: {
          core_charge?: number
          id?: string
          price_list_id?: string
          stock_item_id?: string
          unit_price?: number
        }
        Relationships: [
          {
            foreignKeyName: "price_list_items_price_list_id_fkey"
            columns: ["price_list_id"]
            isOneToOne: false
            referencedRelation: "price_lists"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "price_list_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
        ]
      }
      price_lists: {
        Row: {
          code: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          is_active: boolean
          is_default: boolean
          name: string
        }
        Insert: {
          code: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          is_default?: boolean
          name: string
        }
        Update: {
          code?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          is_default?: boolean
          name?: string
        }
        Relationships: []
      }
      profiles: {
        Row: {
          created_at: string
          full_name: string | null
          id: string
          is_staff: boolean
          updated_at: string
        }
        Insert: {
          created_at?: string
          full_name?: string | null
          id: string
          is_staff?: boolean
          updated_at?: string
        }
        Update: {
          created_at?: string
          full_name?: string | null
          id?: string
          is_staff?: boolean
          updated_at?: string
        }
        Relationships: []
      }
      purchase_order_lines: {
        Row: {
          blanket_parent_line_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          line_no: number
          material_request_line_id: string | null
          purchase_order_id: string
          qty_ordered: number
          qty_received: number
          qty_released: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Insert: {
          blanket_parent_line_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no: number
          material_request_line_id?: string | null
          purchase_order_id: string
          qty_ordered: number
          qty_received?: number
          qty_released?: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          blanket_parent_line_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no?: number
          material_request_line_id?: string | null
          purchase_order_id?: string
          qty_ordered?: number
          qty_received?: number
          qty_released?: number
          stock_item_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "purchase_order_lines_blanket_parent_line_id_fkey"
            columns: ["blanket_parent_line_id"]
            isOneToOne: false
            referencedRelation: "purchase_order_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_order_lines_material_request_line_id_fkey"
            columns: ["material_request_line_id"]
            isOneToOne: false
            referencedRelation: "material_request_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_order_lines_purchase_order_id_fkey"
            columns: ["purchase_order_id"]
            isOneToOne: false
            referencedRelation: "purchase_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_order_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_order_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      purchase_orders: {
        Row: {
          awarded_quotation_id: string | null
          blanket_max_value: number | null
          blanket_parent_id: string | null
          blanket_value_released: number
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number
          expected_date: string | null
          id: string
          is_blanket: boolean
          material_request_id: string | null
          notes: string | null
          order_date: string
          rfq_id: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          supplier_id: string
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          awarded_quotation_id?: string | null
          blanket_max_value?: number | null
          blanket_parent_id?: string | null
          blanket_value_released?: number
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          expected_date?: string | null
          id?: string
          is_blanket?: boolean
          material_request_id?: string | null
          notes?: string | null
          order_date?: string
          rfq_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id: string
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          awarded_quotation_id?: string | null
          blanket_max_value?: number | null
          blanket_parent_id?: string | null
          blanket_value_released?: number
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          expected_date?: string | null
          id?: string
          is_blanket?: boolean
          material_request_id?: string | null
          notes?: string | null
          order_date?: string
          rfq_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id?: string
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "purchase_orders_awarded_quotation_id_fkey"
            columns: ["awarded_quotation_id"]
            isOneToOne: false
            referencedRelation: "supplier_quotations"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_orders_blanket_parent_id_fkey"
            columns: ["blanket_parent_id"]
            isOneToOne: false
            referencedRelation: "purchase_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_orders_material_request_id_fkey"
            columns: ["material_request_id"]
            isOneToOne: false
            referencedRelation: "material_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_orders_rfq_id_fkey"
            columns: ["rfq_id"]
            isOneToOne: false
            referencedRelation: "rfqs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_orders_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "purchase_orders_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      receipt_pdf_artifacts: {
        Row: {
          byte_size: number | null
          content_sha256: string | null
          document_id: string
          document_type: string
          download_token: string
          download_url: string
          generated_at: string
          generated_by: string | null
          has_fiscal_payload: boolean
          id: string
          pdf_storage_path: string
          signed_url_expires_at: string | null
        }
        Insert: {
          byte_size?: number | null
          content_sha256?: string | null
          document_id: string
          document_type: string
          download_token: string
          download_url: string
          generated_at?: string
          generated_by?: string | null
          has_fiscal_payload?: boolean
          id?: string
          pdf_storage_path: string
          signed_url_expires_at?: string | null
        }
        Update: {
          byte_size?: number | null
          content_sha256?: string | null
          document_id?: string
          document_type?: string
          download_token?: string
          download_url?: string
          generated_at?: string
          generated_by?: string | null
          has_fiscal_payload?: boolean
          id?: string
          pdf_storage_path?: string
          signed_url_expires_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "receipt_pdf_artifacts_document_id_fkey"
            columns: ["document_id"]
            isOneToOne: true
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      rfq_lines: {
        Row: {
          created_at: string
          id: string
          line_no: number
          qty: number
          rfq_id: string
          stock_item_id: string
          uom_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          line_no: number
          qty: number
          rfq_id: string
          stock_item_id: string
          uom_id: string
        }
        Update: {
          created_at?: string
          id?: string
          line_no?: number
          qty?: number
          rfq_id?: string
          stock_item_id?: string
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "rfq_lines_rfq_id_fkey"
            columns: ["rfq_id"]
            isOneToOne: false
            referencedRelation: "rfqs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "rfq_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "rfq_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      rfq_suppliers: {
        Row: {
          id: string
          invited_at: string
          rfq_id: string
          supplier_id: string
        }
        Insert: {
          id?: string
          invited_at?: string
          rfq_id: string
          supplier_id: string
        }
        Update: {
          id?: string
          invited_at?: string
          rfq_id?: string
          supplier_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "rfq_suppliers_rfq_id_fkey"
            columns: ["rfq_id"]
            isOneToOne: false
            referencedRelation: "rfqs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "rfq_suppliers_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
        ]
      }
      rfqs: {
        Row: {
          awarded_quotation_id: string | null
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          document_number: string | null
          id: string
          needed_by: string | null
          notes: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          awarded_quotation_id?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          awarded_quotation_id?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "rfqs_awarded_quotation_fk"
            columns: ["awarded_quotation_id"]
            isOneToOne: false
            referencedRelation: "supplier_quotations"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "rfqs_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      salary_structures: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          effective_from: string
          effective_to: string | null
          employee_id: string
          id: string
          is_active: boolean
          pay_type: Database["public"]["Enums"]["salary_pay_type"]
          rate: number
        }
        Insert: {
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          effective_from: string
          effective_to?: string | null
          employee_id: string
          id?: string
          is_active?: boolean
          pay_type: Database["public"]["Enums"]["salary_pay_type"]
          rate: number
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          effective_from?: string
          effective_to?: string | null
          employee_id?: string
          id?: string
          is_active?: boolean
          pay_type?: Database["public"]["Enums"]["salary_pay_type"]
          rate?: number
        }
        Relationships: [
          {
            foreignKeyName: "salary_structures_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      sales_invoice_lines: {
        Row: {
          created_at: string
          id: string
          invoice_id: string
          is_core_charge: boolean
          issues_stock: boolean
          kit_id: string | null
          kit_line_kind: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total: number
          parent_line_id: string | null
          qty: number
          qty_base: number
          qty_fulfilled: number
          stock_batch_id: string | null
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          invoice_id: string
          is_core_charge?: boolean
          issues_stock?: boolean
          kit_id?: string | null
          kit_line_kind?: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total: number
          parent_line_id?: string | null
          qty: number
          qty_base: number
          qty_fulfilled?: number
          stock_batch_id?: string | null
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          created_at?: string
          id?: string
          invoice_id?: string
          is_core_charge?: boolean
          issues_stock?: boolean
          kit_id?: string | null
          kit_line_kind?: Database["public"]["Enums"]["kit_line_kind"] | null
          line_total?: number
          parent_line_id?: string | null
          qty?: number
          qty_base?: number
          qty_fulfilled?: number
          stock_batch_id?: string | null
          stock_item_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "sales_invoice_lines_invoice_id_fkey"
            columns: ["invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoice_lines_kit_id_fkey"
            columns: ["kit_id"]
            isOneToOne: false
            referencedRelation: "item_kits"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoice_lines_parent_line_id_fkey"
            columns: ["parent_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoice_lines_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoice_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoice_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      sales_invoices: {
        Row: {
          amount_paid: number
          cart_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_email: string | null
          customer_id: string | null
          customer_phone_e164: string | null
          customer_whatsapp_e164: string | null
          doc_type: Database["public"]["Enums"]["sales_doc_type"]
          document_number: string | null
          exchange_rate_applied: number
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id: string
          journal_entry_id: string | null
          posted_at: string | null
          posted_by: string | null
          return_against_id: string | null
          status: Database["public"]["Enums"]["sales_doc_status"]
          subtotal: number
          total: number
          warehouse_id: string
        }
        Insert: {
          amount_paid?: number
          cart_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_email?: string | null
          customer_id?: string | null
          customer_phone_e164?: string | null
          customer_whatsapp_e164?: string | null
          doc_type?: Database["public"]["Enums"]["sales_doc_type"]
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          journal_entry_id?: string | null
          posted_at?: string | null
          posted_by?: string | null
          return_against_id?: string | null
          status?: Database["public"]["Enums"]["sales_doc_status"]
          subtotal?: number
          total?: number
          warehouse_id: string
        }
        Update: {
          amount_paid?: number
          cart_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_email?: string | null
          customer_id?: string | null
          customer_phone_e164?: string | null
          customer_whatsapp_e164?: string | null
          doc_type?: Database["public"]["Enums"]["sales_doc_type"]
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          journal_entry_id?: string | null
          posted_at?: string | null
          posted_by?: string | null
          return_against_id?: string | null
          status?: Database["public"]["Enums"]["sales_doc_status"]
          subtotal?: number
          total?: number
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "sales_invoices_cart_id_fkey"
            columns: ["cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoices_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoices_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoices_return_against_id_fkey"
            columns: ["return_against_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sales_invoices_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      sms_event_catalog: {
        Row: {
          category: string
          code: string
          created_at: string
          description: string
          is_active: boolean
          priority: Database["public"]["Enums"]["sms_event_priority"]
        }
        Insert: {
          category: string
          code: string
          created_at?: string
          description: string
          is_active?: boolean
          priority?: Database["public"]["Enums"]["sms_event_priority"]
        }
        Update: {
          category?: string
          code?: string
          created_at?: string
          description?: string
          is_active?: boolean
          priority?: Database["public"]["Enums"]["sms_event_priority"]
        }
        Relationships: []
      }
      sms_outbox: {
        Row: {
          attempt_count: number
          body: string
          created_at: string
          domain_event_id: string
          event_code: string
          id: string
          last_error: string | null
          phone_e164: string
          provider_message_id: string | null
          recipient_user_id: string
          sent_at: string | null
          status: Database["public"]["Enums"]["sms_outbox_status"]
        }
        Insert: {
          attempt_count?: number
          body: string
          created_at?: string
          domain_event_id: string
          event_code: string
          id?: string
          last_error?: string | null
          phone_e164: string
          provider_message_id?: string | null
          recipient_user_id: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["sms_outbox_status"]
        }
        Update: {
          attempt_count?: number
          body?: string
          created_at?: string
          domain_event_id?: string
          event_code?: string
          id?: string
          last_error?: string | null
          phone_e164?: string
          provider_message_id?: string | null
          recipient_user_id?: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["sms_outbox_status"]
        }
        Relationships: [
          {
            foreignKeyName: "sms_outbox_domain_event_id_fkey"
            columns: ["domain_event_id"]
            isOneToOne: false
            referencedRelation: "domain_events"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "sms_outbox_event_code_fkey"
            columns: ["event_code"]
            isOneToOne: false
            referencedRelation: "sms_event_catalog"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "sms_outbox_recipient_user_id_fkey"
            columns: ["recipient_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_roles: {
        Row: {
          created_at: string
          role: Database["public"]["Enums"]["staff_role"]
          user_id: string
        }
        Insert: {
          created_at?: string
          role: Database["public"]["Enums"]["staff_role"]
          user_id: string
        }
        Update: {
          created_at?: string
          role?: Database["public"]["Enums"]["staff_role"]
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "staff_roles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_batches: {
        Row: {
          batch_code: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          qty_on_hand: number
          received_at: string
          stock_item_id: string
          unit_cost: number
          valuation_method: Database["public"]["Enums"]["valuation_method"]
          warehouse_id: string
        }
        Insert: {
          batch_code: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          qty_on_hand?: number
          received_at?: string
          stock_item_id: string
          unit_cost?: number
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          warehouse_id: string
        }
        Update: {
          batch_code?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          qty_on_hand?: number
          received_at?: string
          stock_item_id?: string
          unit_cost?: number
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "stock_batches_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_batches_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_entries: {
        Row: {
          created_at: string
          created_by: string | null
          document_number: string | null
          entry_type: Database["public"]["Enums"]["stock_entry_type"]
          first_approved_at: string | null
          first_approver_id: string | null
          from_warehouse_id: string | null
          id: string
          notes: string | null
          posted_at: string | null
          second_approved_at: string | null
          second_approver_id: string | null
          status: Database["public"]["Enums"]["stock_entry_status"]
          to_warehouse_id: string | null
        }
        Insert: {
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          entry_type: Database["public"]["Enums"]["stock_entry_type"]
          first_approved_at?: string | null
          first_approver_id?: string | null
          from_warehouse_id?: string | null
          id?: string
          notes?: string | null
          posted_at?: string | null
          second_approved_at?: string | null
          second_approver_id?: string | null
          status?: Database["public"]["Enums"]["stock_entry_status"]
          to_warehouse_id?: string | null
        }
        Update: {
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          entry_type?: Database["public"]["Enums"]["stock_entry_type"]
          first_approved_at?: string | null
          first_approver_id?: string | null
          from_warehouse_id?: string | null
          id?: string
          notes?: string | null
          posted_at?: string | null
          second_approved_at?: string | null
          second_approver_id?: string | null
          status?: Database["public"]["Enums"]["stock_entry_status"]
          to_warehouse_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "stock_entries_from_warehouse_id_fkey"
            columns: ["from_warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_entries_to_warehouse_id_fkey"
            columns: ["to_warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_entry_lines: {
        Row: {
          bin_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"] | null
          id: string
          qty: number
          qty_base: number
          stock_batch_id: string | null
          stock_entry_id: string
          stock_item_id: string
          unit_cost: number | null
          uom_id: string
          valuation_method: Database["public"]["Enums"]["valuation_method"]
        }
        Insert: {
          bin_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"] | null
          id?: string
          qty: number
          qty_base: number
          stock_batch_id?: string | null
          stock_entry_id: string
          stock_item_id: string
          unit_cost?: number | null
          uom_id: string
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
        }
        Update: {
          bin_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"] | null
          id?: string
          qty?: number
          qty_base?: number
          stock_batch_id?: string | null
          stock_entry_id?: string
          stock_item_id?: string
          unit_cost?: number | null
          uom_id?: string
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
        }
        Relationships: [
          {
            foreignKeyName: "stock_entry_lines_bin_id_fkey"
            columns: ["bin_id"]
            isOneToOne: false
            referencedRelation: "warehouse_bins"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_entry_lines_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_entry_lines_stock_entry_id_fkey"
            columns: ["stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_entry_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_entry_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_items: {
        Row: {
          base_uom_id: string | null
          created_at: string
          description: string | null
          id: string
          oem_part_number: string
          reorder_point: number | null
          reorder_qty: number | null
          requires_serial: boolean
        }
        Insert: {
          base_uom_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          oem_part_number: string
          reorder_point?: number | null
          reorder_qty?: number | null
          requires_serial?: boolean
        }
        Update: {
          base_uom_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          oem_part_number?: string
          reorder_point?: number | null
          reorder_qty?: number | null
          requires_serial?: boolean
        }
        Relationships: [
          {
            foreignKeyName: "stock_items_base_uom_id_fkey"
            columns: ["base_uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_levels: {
        Row: {
          bin_id: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          quantity: number
          stock_item_id: string
          unit_cost: number | null
          updated_at: string
          valuation_method: Database["public"]["Enums"]["valuation_method"]
          warehouse_id: string
        }
        Insert: {
          bin_id?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          quantity?: number
          stock_item_id: string
          unit_cost?: number | null
          updated_at?: string
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          warehouse_id: string
        }
        Update: {
          bin_id?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          quantity?: number
          stock_item_id?: string
          unit_cost?: number | null
          updated_at?: string
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "stock_levels_bin_id_fkey"
            columns: ["bin_id"]
            isOneToOne: false
            referencedRelation: "warehouse_bins"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_levels_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_levels_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_reconciliation_lines: {
        Row: {
          counted_qty: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          stock_batch_id: string | null
          stock_item_id: string
          stock_reconciliation_id: string
          system_qty: number
          unit_cost: number
          valuation_method: Database["public"]["Enums"]["valuation_method"]
          variance_qty: number | null
        }
        Insert: {
          counted_qty?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          stock_batch_id?: string | null
          stock_item_id: string
          stock_reconciliation_id: string
          system_qty?: number
          unit_cost?: number
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          variance_qty?: number | null
        }
        Update: {
          counted_qty?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          stock_batch_id?: string | null
          stock_item_id?: string
          stock_reconciliation_id?: string
          system_qty?: number
          unit_cost?: number
          valuation_method?: Database["public"]["Enums"]["valuation_method"]
          variance_qty?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "stock_reconciliation_lines_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_reconciliation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_reconciliation_lines_stock_reconciliation_id_fkey"
            columns: ["stock_reconciliation_id"]
            isOneToOne: false
            referencedRelation: "stock_reconciliations"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_reconciliations: {
        Row: {
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number | null
          first_approved_at: string | null
          first_approver_id: string | null
          id: string
          journal_entry_id: string | null
          notes: string | null
          posted_at: string | null
          reversal_journal_entry_id: string | null
          scope: Database["public"]["Enums"]["stock_reconciliation_scope"]
          second_approved_at: string | null
          second_approver_id: string | null
          status: Database["public"]["Enums"]["stock_entry_status"]
          variance_value_abs: number | null
          warehouse_id: string
        }
        Insert: {
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number | null
          first_approved_at?: string | null
          first_approver_id?: string | null
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string | null
          reversal_journal_entry_id?: string | null
          scope: Database["public"]["Enums"]["stock_reconciliation_scope"]
          second_approved_at?: string | null
          second_approver_id?: string | null
          status?: Database["public"]["Enums"]["stock_entry_status"]
          variance_value_abs?: number | null
          warehouse_id: string
        }
        Update: {
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number | null
          first_approved_at?: string | null
          first_approver_id?: string | null
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string | null
          reversal_journal_entry_id?: string | null
          scope?: Database["public"]["Enums"]["stock_reconciliation_scope"]
          second_approved_at?: string | null
          second_approver_id?: string | null
          status?: Database["public"]["Enums"]["stock_entry_status"]
          variance_value_abs?: number | null
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "stock_reconciliations_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_reconciliations_reversal_journal_entry_id_fkey"
            columns: ["reversal_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_reconciliations_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      stock_serials: {
        Row: {
          created_at: string
          id: string
          serial_number: string
          status: string
          stock_batch_id: string | null
          stock_entry_line_id: string | null
          stock_item_id: string
          warehouse_id: string | null
        }
        Insert: {
          created_at?: string
          id?: string
          serial_number: string
          status?: string
          stock_batch_id?: string | null
          stock_entry_line_id?: string | null
          stock_item_id: string
          warehouse_id?: string | null
        }
        Update: {
          created_at?: string
          id?: string
          serial_number?: string
          status?: string
          stock_batch_id?: string | null
          stock_entry_line_id?: string | null
          stock_item_id?: string
          warehouse_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "stock_serials_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_serials_stock_entry_line_id_fkey"
            columns: ["stock_entry_line_id"]
            isOneToOne: false
            referencedRelation: "stock_entry_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_serials_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_serials_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      store_credit_accounts: {
        Row: {
          balance: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id: string
          updated_at: string
        }
        Insert: {
          balance?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id?: string
          updated_at?: string
        }
        Update: {
          balance?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "store_credit_accounts_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: true
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
        ]
      }
      store_credit_ledger: {
        Row: {
          account_id: string
          amount: number
          balance_after: number
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string | null
          exchange_rate_applied: number
          id: string
          journal_entry_id: string | null
          movement: Database["public"]["Enums"]["store_credit_movement"]
          payment_entry_id: string | null
          reason: string | null
          reverses_ledger_id: string | null
        }
        Insert: {
          account_id: string
          amount: number
          balance_after: number
          created_at?: string
          created_by?: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          movement: Database["public"]["Enums"]["store_credit_movement"]
          payment_entry_id?: string | null
          reason?: string | null
          reverses_ledger_id?: string | null
        }
        Update: {
          account_id?: string
          amount?: number
          balance_after?: number
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string | null
          movement?: Database["public"]["Enums"]["store_credit_movement"]
          payment_entry_id?: string | null
          reason?: string | null
          reverses_ledger_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "store_credit_ledger_account_id_fkey"
            columns: ["account_id"]
            isOneToOne: false
            referencedRelation: "store_credit_accounts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "store_credit_ledger_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "store_credit_ledger_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "store_credit_ledger_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "store_credit_ledger_reverses_ledger_id_fkey"
            columns: ["reverses_ledger_id"]
            isOneToOne: false
            referencedRelation: "store_credit_ledger"
            referencedColumns: ["id"]
          },
        ]
      }
      supplier_quotation_lines: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          line_no: number
          qty: number
          rfq_line_id: string | null
          stock_item_id: string
          supplier_quotation_id: string
          unit_price: number
          uom_id: string
        }
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no: number
          qty: number
          rfq_line_id?: string | null
          stock_item_id: string
          supplier_quotation_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          line_no?: number
          qty?: number
          rfq_line_id?: string | null
          stock_item_id?: string
          supplier_quotation_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "supplier_quotation_lines_rfq_line_id_fkey"
            columns: ["rfq_line_id"]
            isOneToOne: false
            referencedRelation: "rfq_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "supplier_quotation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "supplier_quotation_lines_supplier_quotation_id_fkey"
            columns: ["supplier_quotation_id"]
            isOneToOne: false
            referencedRelation: "supplier_quotations"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "supplier_quotation_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      supplier_quotations: {
        Row: {
          cancelled_at: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number
          id: string
          notes: string | null
          rfq_id: string
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          supplier_id: string
          updated_at: string
          valid_until: string | null
        }
        Insert: {
          cancelled_at?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          rfq_id: string
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id: string
          updated_at?: string
          valid_until?: string | null
        }
        Update: {
          cancelled_at?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          rfq_id?: string
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id?: string
          updated_at?: string
          valid_until?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "supplier_quotations_rfq_id_fkey"
            columns: ["rfq_id"]
            isOneToOne: false
            referencedRelation: "rfqs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "supplier_quotations_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
            referencedColumns: ["id"]
          },
        ]
      }
      suppliers: {
        Row: {
          code: string
          created_at: string
          default_currency: Database["public"]["Enums"]["currency_code"]
          email: string | null
          id: string
          is_active: boolean
          name: string
          phone_e164: string | null
          profile_id: string | null
          updated_at: string
        }
        Insert: {
          code: string
          created_at?: string
          default_currency?: Database["public"]["Enums"]["currency_code"]
          email?: string | null
          id?: string
          is_active?: boolean
          name: string
          phone_e164?: string | null
          profile_id?: string | null
          updated_at?: string
        }
        Update: {
          code?: string
          created_at?: string
          default_currency?: Database["public"]["Enums"]["currency_code"]
          email?: string | null
          id?: string
          is_active?: boolean
          name?: string
          phone_e164?: string | null
          profile_id?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "suppliers_profile_id_fkey"
            columns: ["profile_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      uoms: {
        Row: {
          code: string
          created_at: string
          id: string
          name: string
        }
        Insert: {
          code: string
          created_at?: string
          id?: string
          name: string
        }
        Update: {
          code?: string
          created_at?: string
          id?: string
          name?: string
        }
        Relationships: []
      }
      vehicle_master: {
        Row: {
          chassis_code: string
          created_at: string
          engine_code: string | null
          id: string
          model_variant: string
          production_year: number | null
          search_vector: unknown
          vin_prefix: string | null
        }
        Insert: {
          chassis_code: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant: string
          production_year?: number | null
          search_vector?: unknown
          vin_prefix?: string | null
        }
        Update: {
          chassis_code?: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant?: string
          production_year?: number | null
          search_vector?: unknown
          vin_prefix?: string | null
        }
        Relationships: []
      }
      warehouse_bins: {
        Row: {
          aisle: string | null
          code: string
          created_at: string
          created_by: string | null
          id: string
          is_active: boolean
          name: string
          pick_path_seq: number
          rack: string | null
          shelf: string | null
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          aisle?: string | null
          code: string
          created_at?: string
          created_by?: string | null
          id?: string
          is_active?: boolean
          name: string
          pick_path_seq?: number
          rack?: string | null
          shelf?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          aisle?: string | null
          code?: string
          created_at?: string
          created_by?: string | null
          id?: string
          is_active?: boolean
          name?: string
          pick_path_seq?: number
          rack?: string | null
          shelf?: string | null
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "warehouse_bins_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      warehouses: {
        Row: {
          code: string
          created_at: string
          id: string
          is_active: boolean
          is_quarantine: boolean
          name: string
        }
        Insert: {
          code: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_quarantine?: boolean
          name: string
        }
        Update: {
          code?: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_quarantine?: boolean
          name?: string
        }
        Relationships: []
      }
      warranty_claims: {
        Row: {
          closed_at: string | null
          created_at: string
          created_by: string | null
          credit_note_id: string | null
          currency: Database["public"]["Enums"]["currency_code"] | null
          customer_id: string | null
          decided_at: string | null
          decided_by: string | null
          document_number: string
          id: string
          notes: string | null
          quarantine_stock_entry_id: string | null
          reject_reason: string | null
          replacement_stock_entry_id: string | null
          resolution:
            | Database["public"]["Enums"]["warranty_claim_resolution"]
            | null
          sales_invoice_id: string | null
          status: Database["public"]["Enums"]["warranty_claim_status"]
          stock_batch_id: string | null
          stock_item_id: string | null
          stock_serial_id: string | null
          updated_at: string
        }
        Insert: {
          closed_at?: string | null
          created_at?: string
          created_by?: string | null
          credit_note_id?: string | null
          currency?: Database["public"]["Enums"]["currency_code"] | null
          customer_id?: string | null
          decided_at?: string | null
          decided_by?: string | null
          document_number: string
          id?: string
          notes?: string | null
          quarantine_stock_entry_id?: string | null
          reject_reason?: string | null
          replacement_stock_entry_id?: string | null
          resolution?:
            | Database["public"]["Enums"]["warranty_claim_resolution"]
            | null
          sales_invoice_id?: string | null
          status?: Database["public"]["Enums"]["warranty_claim_status"]
          stock_batch_id?: string | null
          stock_item_id?: string | null
          stock_serial_id?: string | null
          updated_at?: string
        }
        Update: {
          closed_at?: string | null
          created_at?: string
          created_by?: string | null
          credit_note_id?: string | null
          currency?: Database["public"]["Enums"]["currency_code"] | null
          customer_id?: string | null
          decided_at?: string | null
          decided_by?: string | null
          document_number?: string
          id?: string
          notes?: string | null
          quarantine_stock_entry_id?: string | null
          reject_reason?: string | null
          replacement_stock_entry_id?: string | null
          resolution?:
            | Database["public"]["Enums"]["warranty_claim_resolution"]
            | null
          sales_invoice_id?: string | null
          status?: Database["public"]["Enums"]["warranty_claim_status"]
          stock_batch_id?: string | null
          stock_item_id?: string | null
          stock_serial_id?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "warranty_claims_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_quarantine_stock_entry_id_fkey"
            columns: ["quarantine_stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_replacement_stock_entry_id_fkey"
            columns: ["replacement_stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_stock_batch_id_fkey"
            columns: ["stock_batch_id"]
            isOneToOne: false
            referencedRelation: "stock_batches"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "warranty_claims_stock_serial_id_fkey"
            columns: ["stock_serial_id"]
            isOneToOne: false
            referencedRelation: "stock_serials"
            referencedColumns: ["id"]
          },
        ]
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      _adjust_consignment_level: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_delta: number
          p_item: string
          p_kind: Database["public"]["Enums"]["consignment_kind"]
          p_supplier_id: string
          p_unit_cost: number
          p_warehouse: string
        }
        Returns: undefined
      }
      _adjust_stock_level: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_delta: number
          p_item: string
          p_unit_cost: number
          p_valuation: Database["public"]["Enums"]["valuation_method"]
          p_warehouse: string
        }
        Returns: undefined
      }
      _append_loyalty: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate: number
          p_journal_entry_id: string
          p_money_value: number
          p_movement: Database["public"]["Enums"]["loyalty_movement"]
          p_points: number
          p_reason: string
          p_reverses_ledger_id?: string
          p_sales_invoice_id: string
        }
        Returns: string
      }
      _append_store_credit: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate: number
          p_journal_entry_id: string
          p_movement: Database["public"]["Enums"]["store_credit_movement"]
          p_payment_entry_id: string
          p_reason: string
          p_reverses_ledger_id?: string
        }
        Returns: string
      }
      _assert_bin_in_warehouse: {
        Args: { p_bin_id: string; p_warehouse_id: string }
        Returns: undefined
      }
      _assert_ai_analytics_staff: { Args: never; Returns: undefined }
      _assert_customer_owns_invoice: {
        Args: { p_invoice_id: string }
        Returns: {
          amount_paid: number
          cart_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_email: string | null
          customer_id: string | null
          customer_phone_e164: string | null
          customer_whatsapp_e164: string | null
          doc_type: Database["public"]["Enums"]["sales_doc_type"]
          document_number: string | null
          exchange_rate_applied: number
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id: string
          journal_entry_id: string | null
          posted_at: string | null
          posted_by: string | null
          return_against_id: string | null
          status: Database["public"]["Enums"]["sales_doc_status"]
          subtotal: number
          total: number
          warehouse_id: string
        }
        SetofOptions: {
          from: "*"
          to: "sales_invoices"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      _assert_customer_owns_open_cart: {
        Args: { p_cart_id: string }
        Returns: undefined
      }
      _assert_journal_balanced: {
        Args: { p_entry_id: string }
        Returns: undefined
      }
      _assert_procurement_period_open: { Args: never; Returns: undefined }
      _assert_supplier_invited_to_rfq: {
        Args: { p_rfq_id: string; p_supplier_id: string }
        Returns: undefined
      }
      _consume_fifo_batches: {
        Args: { p_item: string; p_qty_base: number; p_warehouse: string }
        Returns: undefined
      }
      _current_customer_id: { Args: never; Returns: string }
      _ensure_loyalty_account: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
        }
        Returns: string
      }
      _ensure_store_credit_account: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
        }
        Returns: string
      }
      _invoice_line_open_qty_base: {
        Args: { p_invoice_line_id: string }
        Returns: number
      }
      _line_usd_equiv: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_rate: number
        }
        Returns: number
      }
      _logistics_begin_rpc: { Args: never; Returns: undefined }
      _logistics_rpc_active: { Args: never; Returns: boolean }
      _loyalty_money_value: { Args: { p_points: number }; Returns: number }
      _loyalty_rpc_active: { Args: never; Returns: boolean }
      _loyalty_rpc_enter: { Args: never; Returns: undefined }
      _payments_rpc_active: { Args: never; Returns: boolean }
      _payments_rpc_enter: { Args: never; Returns: undefined }
      _payroll_begin_rpc: { Args: never; Returns: undefined }
      _payroll_rpc_active: { Args: never; Returns: boolean }
      _post_journal_entry_inventory: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_description: string
          p_entry_date: string
          p_exchange_rate: number
          p_lines: Json
        }
        Returns: string
      }
      _post_stock_reconciliation: {
        Args: { p_reconciliation_id: string }
        Returns: string
      }
      _procurement_begin_rpc: { Args: never; Returns: undefined }
      _procurement_end_rpc: { Args: never; Returns: undefined }
      _procurement_rpc_active: { Args: never; Returns: boolean }
      _recon_assert_single_currency: {
        Args: { p_reconciliation_id: string }
        Returns: undefined
      }
      _recon_begin_rpc: { Args: never; Returns: undefined }
      _recon_compute_variance_value: {
        Args: { p_reconciliation_id: string }
        Returns: number
      }
      _recon_dual_auth_threshold: {
        Args: { p_currency: Database["public"]["Enums"]["currency_code"] }
        Returns: number
      }
      _recon_rpc_active: { Args: never; Returns: boolean }
      _refresh_payroll_run_totals: {
        Args: { p_run_id: string }
        Returns: undefined
      }
      _require_cart_mutate: { Args: { p_cart_id: string }; Returns: undefined }
      _require_consignment_staff: { Args: never; Returns: undefined }
      _require_dispatcher_staff: { Args: never; Returns: undefined }
      _require_hr_staff: { Args: never; Returns: undefined }
      _require_logistics_staff: { Args: never; Returns: undefined }
      _require_loyalty_staff: { Args: never; Returns: undefined }
      _require_payments_staff: { Args: never; Returns: undefined }
      _require_procurement_finance: { Args: never; Returns: undefined }
      _require_procurement_staff: { Args: never; Returns: undefined }
      _require_sales_staff: { Args: never; Returns: undefined }
      _require_warehouse_staff: { Args: never; Returns: undefined }
      _require_warranty_staff: { Args: never; Returns: undefined }
      _reverse_journal_inventory: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
      }
      _storefront_rpc_active: { Args: never; Returns: boolean }
      _storefront_rpc_enter: { Args: never; Returns: undefined }
      _storefront_rpc_exit: { Args: never; Returns: undefined }
      _test_set_auth_uid: { Args: { p_uid: string }; Returns: undefined }
      _test_set_service_role: { Args: { p_uid?: string }; Returns: undefined }
      _warranty_begin_rpc: { Args: never; Returns: undefined }
      _warranty_rpc_active: { Args: never; Returns: boolean }
      add_cart_line: {
        Args: {
          p_cart_id: string
          p_qty: number
          p_stock_item_id: string
          p_uom_id: string
        }
        Returns: string
      }
      add_cart_line_from_qr: {
        Args: { p_cart_id: string; p_qr_payload: string; p_qty?: number }
        Returns: string
      }
      add_consignment_entry_line: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_entry_id: string
          p_qty: number
          p_stock_item_id: string
          p_unit_cost?: number
          p_unit_price?: number
          p_uom_id: string
        }
        Returns: string
      }
      add_customer_cart_line: {
        Args: {
          p_cart_id: string
          p_qty: number
          p_stock_item_id: string
          p_uom_id: string
        }
        Returns: string
      }
      add_customer_wishlist_item: {
        Args: { p_oem_part_number?: string; p_stock_item_id?: string }
        Returns: string
      }
      add_kit_component: {
        Args: {
          p_component_item_id: string
          p_kit_id: string
          p_qty: number
          p_uom_id: string
        }
        Returns: string
      }
      add_payroll_deduction: {
        Args: { p_amount: number; p_label: string; p_payroll_line_id: string }
        Returns: string
      }
      allocate_payment: {
        Args: { p_allocations: Json; p_payment_entry_id: string }
        Returns: string
      }
      approve_stock_reconciliation: {
        Args: { p_reconciliation_id: string }
        Returns: string
      }
      approve_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
      approve_warranty_claim: {
        Args: {
          p_claim_id: string
          p_lines?: Json
          p_replacement_lines?: Json
          p_resolution: Database["public"]["Enums"]["warranty_claim_resolution"]
        }
        Returns: string
      }

      chat_unread_count: { Args: { p_thread_id?: string }; Returns: number }
      claim_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      close_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      mark_chat_thread_read: { Args: { p_thread_id: string }; Returns: undefined }
      post_chat_message: {
        Args: { p_body: string; p_thread_id: string }
        Returns: string
      }
      start_chat_thread: {
        Args: {
          p_body?: string
          p_kind?: Database["public"]["Enums"]["chat_thread_kind"]
          p_subject?: string
        }
        Returns: string
      }
      assign_delivery_job: {
        Args: {
          p_assignee_user_id: string
          p_delivery_job_id: string
          p_override?: boolean
        }
        Returns: string
      }
      assign_staff_role: {
        Args: {
          p_role: Database["public"]["Enums"]["staff_role"]
          p_user_id: string
        }
        Returns: undefined
      }
      attendance_hours_in_period: {
        Args: {
          p_employee_id: string
          p_period_end: string
          p_period_start: string
        }
        Returns: number
      }
      award_quotation_to_po: {
        Args: {
          p_expected_date?: string
          p_notes?: string
          p_supplier_quotation_id: string
        }
        Returns: string
      }
      build_qr_payload: {
        Args: {
          p_batch_code: string
          p_oem: string
          p_valuation: Database["public"]["Enums"]["valuation_method"]
        }
        Returns: string
      }
      cancel_consignment_entry: {
        Args: { p_entry_id: string }
        Returns: string
      }
      cancel_delivery_note: {
        Args: { p_delivery_note_id: string }
        Returns: string
      }
      cancel_goods_receipt: {
        Args: { p_goods_receipt_id: string }
        Returns: string
      }
      cancel_landed_cost_voucher: {
        Args: { p_landed_cost_voucher_id: string; p_notes?: string }
        Returns: string
      }
      cancel_material_request: {
        Args: { p_material_request_id: string; p_notes?: string }
        Returns: string
      }
      cancel_payment_entry: {
        Args: { p_payment_entry_id: string }
        Returns: string
      }
      cancel_payroll_run: {
        Args: { p_payroll_run_id: string; p_reason?: string }
        Returns: string
      }
      cancel_purchase_order: {
        Args: { p_notes?: string; p_purchase_order_id: string }
        Returns: string
      }
      cancel_rfq: {
        Args: { p_notes?: string; p_rfq_id: string }
        Returns: string
      }
      cancel_stock_reconciliation: {
        Args: { p_notes?: string; p_reconciliation_id: string }
        Returns: string
      }
      cancel_supplier_quotation: {
        Args: { p_notes?: string; p_supplier_quotation_id: string }
        Returns: string
      }
      checkout_customer_cart: { Args: { p_cart_id: string }; Returns: string }
      checkout_pos_cart: { Args: { p_cart_id: string }; Returns: string }
      clear_bank_matches: { Args: { p_match_ids: string[] }; Returns: number }
      clock_attendance: {
        Args: {
          p_employee_id: string
          p_event_type: Database["public"]["Enums"]["attendance_event_type"]
          p_notes?: string
          p_occurred_at?: string
        }
        Returns: string
      }
      close_warranty_claim: { Args: { p_claim_id: string }; Returns: string }
      compute_payroll_run: {
        Args: { p_employee_ids?: string[]; p_payroll_run_id: string }
        Returns: string
      }
      confirm_pick_lines: {
        Args: { p_lines: Json; p_pick_list_id: string }
        Returns: string
      }
      convert_material_request_to_po: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_line_ids?: string[]
          p_material_request_id: string
          p_supplier_id: string
        }
        Returns: string
      }
      convert_to_base_uom: {
        Args: { p_from_uom_id: string; p_qty: number; p_stock_item_id: string }
        Returns: number
      }
      create_blanket_purchase_order: {
        Args: {
          p_blanket_max_value: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_expected_date?: string
          p_lines: Json
          p_notes?: string
          p_supplier_id: string
          p_warehouse_id: string
        }
        Returns: string
      }
      create_blanket_release: {
        Args: {
          p_blanket_purchase_order_id: string
          p_lines: Json
          p_notes?: string
        }
        Returns: string
      }
      create_consignment_entry_draft: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id?: string
          p_exchange_rate?: number
          p_kind: Database["public"]["Enums"]["consignment_kind"]
          p_notes?: string
          p_purpose: Database["public"]["Enums"]["consignment_entry_purpose"]
          p_supplier_id?: string
          p_warehouse_id: string
        }
        Returns: string
      }
      create_contipay_intent: {
        Args: {
          p_amount: number
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id?: string
          p_exchange_rate?: number
          p_external_ref: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["contipay_method"]
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
        }
        Returns: string
      }
      create_customer_cart: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          p_warehouse_id: string
        }
        Returns: string
      }
      create_customer_contipay_intent: {
        Args: {
          p_amount?: number
          p_external_ref?: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["contipay_method"]
          p_sales_invoice_id: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
        }
        Returns: string
      }
      create_customer_paynow_intent: {
        Args: {
          p_amount?: number
          p_external_ref?: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["paynow_method"]
          p_sales_invoice_id: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
        }
        Returns: string
      }
      create_delivery_job: {
        Args: {
          p_assignee_user_id?: string
          p_delivery_note_id: string
          p_eta_at?: string
          p_notes?: string
        }
        Returns: string
      }
      create_delivery_note: {
        Args: {
          p_lines: Json
          p_pick_list_id?: string
          p_sales_invoice_id: string
        }
        Returns: string
      }
      create_employee: {
        Args: {
          p_email?: string
          p_employee_code?: string
          p_full_name?: string
          p_hire_date?: string
          p_phone_e164?: string
          p_user_id?: string
        }
        Returns: string
      }
      create_goods_receipt: {
        Args: { p_lines: Json; p_notes?: string; p_purchase_order_id: string }
        Returns: string
      }
      create_item_kit: {
        Args: {
          p_sell_mode?: Database["public"]["Enums"]["kit_sell_mode"]
          p_stock_item_id: string
        }
        Returns: string
      }
      create_journal_draft: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_description: string
          p_entry_date: string
          p_exchange_rate: number
          p_lines: Json
        }
        Returns: string
      }
      create_landed_cost_voucher: {
        Args: {
          p_allocations: Json
          p_charges: Json
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_goods_receipt_id: string
          p_notes?: string
        }
        Returns: string
      }
      create_material_request: {
        Args: {
          p_lines: Json
          p_needed_by: string
          p_notes?: string
          p_warehouse_id: string
        }
        Returns: string
      }
      create_mr_from_forecast: {
        Args: {
          p_needed_by?: string
          p_notes?: string
          p_suggestion_ids: string[]
        }
        Returns: string
      }
      create_payment_entry: {
        Args: {
          p_amount: number
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_notes?: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
          p_tender: Database["public"]["Enums"]["payment_tender"]
        }
        Returns: string
      }
      create_paynow_intent: {
        Args: {
          p_amount: number
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id?: string
          p_exchange_rate?: number
          p_external_ref: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["paynow_method"]
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
        }
        Returns: string
      }
      create_payroll_run: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_notes?: string
          p_period_end: string
          p_period_start: string
        }
        Returns: string
      }
      create_pick_list: {
        Args: { p_lines?: Json; p_sales_invoice_id: string }
        Returns: string
      }
      create_pos_cart:
        | {
            Args: {
              p_currency?: Database["public"]["Enums"]["currency_code"]
              p_customer_id?: string
              p_warehouse_id: string
            }
            Returns: string
          }
        | {
            Args: {
              p_currency?: Database["public"]["Enums"]["currency_code"]
              p_customer_id?: string
              p_fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
              p_warehouse_id: string
            }
            Returns: string
          }
      create_purchase_order: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_expected_date?: string
          p_lines: Json
          p_material_request_id?: string
          p_notes?: string
          p_supplier_id: string
          p_warehouse_id: string
        }
        Returns: string
      }
      create_rfq: {
        Args: {
          p_lines: Json
          p_needed_by: string
          p_notes?: string
          p_supplier_ids: string[]
          p_warehouse_id: string
        }
        Returns: string
      }
      create_stock_reconciliation_draft: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_item_ids?: string[]
          p_notes?: string
          p_scope: Database["public"]["Enums"]["stock_reconciliation_scope"]
          p_warehouse_id: string
        }
        Returns: string
      }
      create_stock_transfer: {
        Args: {
          p_from_warehouse_id: string
          p_lines: Json
          p_notes: string
          p_to_warehouse_id: string
        }
        Returns: string
      }
      create_supplier: {
        Args: {
          p_code: string
          p_default_currency?: Database["public"]["Enums"]["currency_code"]
          p_email?: string
          p_name: string
          p_phone_e164?: string
        }
        Returns: string
      }
      create_warehouse_bin: {
        Args: {
          p_aisle?: string
          p_code: string
          p_name: string
          p_pick_path_seq?: number
          p_rack?: string
          p_shelf?: string
          p_warehouse_id: string
        }
        Returns: string
      }
      current_employee_id: { Args: never; Returns: string }
      current_supplier_id: { Args: never; Returns: string }
      deactivate_warehouse_bin: { Args: { p_bin_id: string }; Returns: string }
      delete_customer_garage_vehicle: {
        Args: { p_id: string }
        Returns: undefined
      }
      drain_sms_outbox_batch: {
        Args: { p_limit?: number; p_stub_success?: boolean }
        Returns: number
      }
      earn_loyalty_from_spend: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_reason?: string
          p_sales_invoice_id?: string
          p_spend_amount: number
        }
        Returns: string
      }
      earn_loyalty_points: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_points: number
          p_reason?: string
          p_sales_invoice_id?: string
        }
        Returns: string
      }
      emit_domain_event: {
        Args: {
          p_actor_user_id?: string
          p_dedupe_key: string
          p_event_code: string
          p_message_body?: string
          p_payload?: Json
        }
        Returns: string
      }
      emit_staff_no_show: {
        Args: { p_employee_id: string; p_window_label?: string }
        Returns: string
      }
      enqueue_customer_receipts: {
        Args: { p_invoice_id: string }
        Returns: number
      }
      expire_loyalty_points: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_points: number
          p_reason?: string
        }
        Returns: string
      }
      export_payslip: { Args: { p_payroll_line_id: string }; Returns: string }
      generate_forecast_suggestions: {
        Args: {
          p_default_reorder_qty?: number
          p_horizon_days?: number
          p_warehouse_id: string
        }
        Returns: number
      }
      finalize_ai_report_run: {
        Args: {
          p_error?: string
          p_gemini_used?: boolean
          p_kpi_json?: Json
          p_narrative?: string
          p_run_id: string
          p_status: Database["public"]["Enums"]["ai_report_run_status"]
        }
        Returns: undefined
      }
      get_customer_order: { Args: { p_invoice_id: string }; Returns: Json }
      get_loyalty_balance: {
        Args: { p_customer_id: string }
        Returns: {
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          estimated_liability: number
          liability_per_point: number
          points_balance: number
        }[]
      }
      get_pick_path_hints: {
        Args: { p_stock_item_ids?: string[]; p_warehouse_id: string }
        Returns: {
          aisle: string
          bin_code: string
          bin_id: string
          bin_name: string
          oem_part_number: string
          pick_path_seq: number
          quantity: number
          rack: string
          shelf: string
          stock_item_id: string
        }[]
      }
      generate_delivery_pod_otp: {
        Args: { p_delivery_job_id: string; p_ttl?: string }
        Returns: string
      }
      verify_delivery_pod_otp: {
        Args: { p_delivery_job_id: string; p_code: string }
        Returns: boolean
      }
      delivery_geofence_suggestion: {
        Args: {
          p_arrive_radius_m?: number
          p_complete_radius_m?: number
          p_delivery_job_id: string
          p_lat: number
          p_lng: number
        }
        Returns: {
          distance_m: number | null
          suggest_arrive: boolean
          suggest_complete: boolean
        }[]
      }
      fail_delivery_job: {
        Args: {
          p_create_reattempt?: boolean
          p_delivery_job_id: string
          p_notes?: string
          p_reason: Database["public"]["Enums"]["delivery_failure_reason"]
        }
        Returns: string
      }
      raise_delivery_panic: {
        Args: {
          p_delivery_job_id?: string
          p_lat?: number
          p_lng?: number
        }
        Returns: string
      }
      optimize_driver_stops: {
        Args: { p_driver_user_id: string }
        Returns: {
          delivery_job_id: string
          distance_m: number | null
          route_sequence: number
        }[]
      }

      get_delivery_track_point: {
        Args: { p_delivery_job_id?: string; p_token?: string }
        Returns: {
          delivery_job_id: string
          eta_at: string | null
          eta_seconds: number | null
          lat: number
          lng: number
          recorded_at: string
          status: Database["public"]["Enums"]["delivery_job_status"]
        }[]
      }
      has_staff_role: {
        Args: { roles: Database["public"]["Enums"]["staff_role"][] }
        Returns: boolean
      }
      ingest_delivery_location: {
        Args: {
          p_accuracy_m?: number
          p_delivery_job_id: string
          p_lat: number
          p_lng: number
          p_recorded_at?: string
        }
        Returns: string
      }
      is_period_locked: { Args: { p_date: string }; Returns: boolean }
      is_staff: { Args: never; Returns: boolean }
      issue_store_credit: {
        Args: {
          p_amount: number
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_debit_account?: string
          p_exchange_rate?: number
          p_reason?: string
        }
        Returns: string
      }
      insert_ai_report_delivery: {
        Args: {
          p_channel: Database["public"]["Enums"]["ai_delivery_channel"]
          p_error?: string
          p_provider_ref?: string
          p_recipient: string
          p_run_id: string
          p_status: Database["public"]["Enums"]["ai_delivery_status"]
        }
        Returns: string
      }
      insert_ai_report_run: {
        Args: {
          p_cadence: Database["public"]["Enums"]["ai_report_cadence"]
          p_period_end: string
          p_period_start: string
          p_subscription_id: string
        }
        Returns: string
      }
      kpi_ar_aging_snapshot: { Args: never; Returns: Json }
      kpi_credit_holds_summary: { Args: never; Returns: Json }
      kpi_inventory_summary: { Args: never; Returns: Json }
      kpi_open_deliveries_summary: { Args: never; Returns: Json }
      kpi_ops_sales_v1: {
        Args: { p_from: string; p_to: string; p_top_limit?: number }
        Returns: Json
      }
      kpi_returns_summary: {
        Args: { p_from: string; p_to: string }
        Returns: Json
      }
      kpi_sales_summary: {
        Args: { p_from: string; p_to: string }
        Returns: Json
      }
      kpi_top_skus: {
        Args: { p_from: string; p_limit?: number; p_to: string }
        Returns: Json
      }
      list_due_ai_report_subscriptions: {
        Args: {
          p_cadence: Database["public"]["Enums"]["ai_report_cadence"]
          p_force?: boolean
          p_now?: string
          p_subscription_id?: string
        }
        Returns: {
          active: boolean
          cadence: Database["public"]["Enums"]["ai_report_cadence"]
          channels: Database["public"]["Enums"]["ai_delivery_channel"][]
          created_at: string
          created_by: string | null
          id: string
          include_narrative: boolean
          kpi_set: string
          last_run_at: string | null
          recipient_emails: string[]
          recipient_whatsapp_e164: string[]
          timezone: string
          updated_at: string
        }[]
        SetofOptions: {
          from: "*"
          to: "ai_report_subscriptions"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      link_supplier_profile: {
        Args: { p_profile_id: string; p_supplier_id: string }
        Returns: undefined
      }
      lock_accounting_period: {
        Args: { p_period_id: string }
        Returns: undefined
      }
      mark_contipay_settled: {
        Args: {
          p_allocations?: Json
          p_external_ref: string
          p_failure_reason?: string
          p_payload_hash: string
          p_provider_ref?: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
          p_success?: boolean
        }
        Returns: string
      }
      mark_paynow_settled: {
        Args: {
          p_allocations?: Json
          p_external_ref: string
          p_failure_reason?: string
          p_payload_hash: string
          p_provider_ref?: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
          p_success?: boolean
        }
        Returns: string
      }
      mark_receipt_pdf_ready: {
        Args: {
          p_byte_size?: number
          p_content_sha256?: string
          p_document_id: string
          p_download_token?: string
          p_expires_at?: string
          p_storage_path: string
        }
        Returns: string
      }
      next_series_value: { Args: { p_prefix: string }; Returns: string }
      open_warranty_claim: {
        Args: {
          p_notes?: string
          p_sales_invoice_id?: string
          p_stock_batch_id?: string
          p_stock_serial_id?: string
        }
        Returns: string
      }
      post_journal: { Args: { p_entry_id: string }; Returns: string }
      post_journal_entry: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_description: string
          p_entry_date: string
          p_exchange_rate: number
          p_lines: Json
        }
        Returns: string
      }
      post_opening_balances: {
        Args: {
          p_as_of: string
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_lines: Json
        }
        Returns: string
      }
      post_payment_entry: {
        Args: { p_payment_entry_id: string }
        Returns: string
      }
      post_return_credit_note: {
        Args: { p_invoice_id: string; p_lines: Json }
        Returns: string
      }
      post_return_to_quarantine: {
        Args: { p_from_warehouse_id: string; p_lines: Json; p_notes: string }
        Returns: string
      }
      post_stock_issue: {
        Args: { p_from_warehouse_id: string; p_lines: Json; p_notes: string }
        Returns: string
      }
      post_stock_receipt: {
        Args: { p_lines: Json; p_notes: string; p_to_warehouse_id: string }
        Returns: string
      }
      process_receipt_outbox_batch: {
        Args: { p_limit?: number; p_stub_success?: boolean }
        Returns: number
      }
      purge_delivery_locations: {
        Args: { p_older_than?: string }
        Returns: number
      }
      redeem_loyalty_points: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_points: number
          p_reason?: string
          p_sales_invoice_id?: string
        }
        Returns: string
      }
      redeem_store_credit: {
        Args: {
          p_amount: number
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
          p_exchange_rate?: number
          p_notes?: string
          p_sales_invoice_id?: string
        }
        Returns: string
      }
      reject_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
      reject_warranty_claim: {
        Args: { p_claim_id: string; p_reason?: string }
        Returns: string
      }
      remove_customer_wishlist_item: {
        Args: {
          p_oem_part_number?: string
          p_stock_item_id?: string
          p_wishlist_id?: string
        }
        Returns: undefined
      }
      remove_kit_component: {
        Args: { p_component_item_id: string; p_kit_id: string }
        Returns: undefined
      }
      report_balance_sheet: {
        Args: {
          p_as_of?: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
        }
        Returns: {
          account_code: string
          account_name: string
          account_type: Database["public"]["Enums"]["account_type"]
          balance: number
          balance_usd: number
        }[]
      }
      report_cash_flow: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_from: string
          p_to: string
        }
        Returns: {
          amount_usd: number
          label: string
          section: string
        }[]
      }
      report_profit_and_loss: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_from: string
          p_to: string
        }
        Returns: {
          account_code: string
          account_name: string
          account_type: Database["public"]["Enums"]["account_type"]
          amount: number
          amount_usd: number
        }[]
      }
      report_trial_balance: {
        Args: {
          p_as_of?: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
        }
        Returns: {
          account_code: string
          account_name: string
          account_type: Database["public"]["Enums"]["account_type"]
          credit: number
          credit_usd: number
          debit: number
          debit_usd: number
        }[]
      }
      resolve_item_price: {
        Args: { p_customer_id: string; p_stock_item_id: string }
        Returns: {
          core_charge: number
          currency: Database["public"]["Enums"]["currency_code"]
          unit_price: number
        }[]
      }
      reverse_journal: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
      }
      reverse_loyalty_movement: {
        Args: { p_ledger_id: string }
        Returns: string
      }
      mint_delivery_track_token: {
        Args: { p_delivery_job_id: string; p_ttl?: string }
        Returns: string
      }
      moderate_customer_product_review: {
        Args: {
          p_review_id: string
          p_status: Database["public"]["Enums"]["product_review_status"]
        }
        Returns: string
      }
      revoke_staff_role: {
        Args: {
          p_role: Database["public"]["Enums"]["staff_role"]
          p_user_id: string
        }
        Returns: undefined
      }
      search_catalog: {
        Args: { p_mode: string; p_query: string }
        Returns: Json
      }
      set_delivery_job_geo: {
        Args: {
          p_delivery_job_id: string
          p_dropoff_lat?: number
          p_dropoff_lng?: number
          p_pickup_lat?: number
          p_pickup_lng?: number
        }
        Returns: string
      }
      set_driver_presence: {
        Args: {
          p_capacity?: number
          p_last_lat?: number
          p_last_lng?: number
          p_shift_ends_at?: string
          p_shift_starts_at?: string
          p_status: Database["public"]["Enums"]["driver_presence_status"]
        }
        Returns: string
      }
      set_loyalty_program_settings: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_is_active?: boolean
          p_liability_per_point?: number
          p_points_per_currency_unit?: number
        }
        Returns: undefined
      }
      set_stock_level_bin: {
        Args: {
          p_bin_id?: string
          p_stock_item_id: string
          p_warehouse_id: string
        }
        Returns: string
      }
      submit_consignment_entry: {
        Args: { p_entry_id: string }
        Returns: string
      }
      submit_customer_product_review: {
        Args: {
          p_body?: string
          p_oem_part_number?: string
          p_rating: number
          p_stock_item_id?: string
        }
        Returns: string
      }
      submit_delivery_note: {
        Args: { p_delivery_note_id: string }
        Returns: string
      }
      submit_goods_receipt: {
        Args: { p_goods_receipt_id: string }
        Returns: string
      }
      submit_landed_cost_voucher: {
        Args: { p_landed_cost_voucher_id: string }
        Returns: string
      }
      submit_material_request: {
        Args: { p_material_request_id: string }
        Returns: string
      }
      submit_payroll_run: {
        Args: { p_payroll_run_id: string }
        Returns: string
      }
      submit_purchase_order: {
        Args: { p_purchase_order_id: string }
        Returns: string
      }
      submit_rfq: { Args: { p_rfq_id: string }; Returns: string }
      submit_stock_reconciliation: {
        Args: { p_reconciliation_id: string }
        Returns: string
      }
      submit_supplier_quotation: {
        Args: { p_supplier_quotation_id: string }
        Returns: string
      }
      submit_delivery_pod: {
        Args: {
          p_delivery_job_id: string
          p_notes?: string
          p_otp_code: string
          p_pod_photo_path: string
          p_pod_signature_path: string
        }
        Returns: string
      }
      suggest_delivery_assignees: {
        Args: { p_delivery_job_id: string; p_limit?: number }
        Returns: {
          capacity: number
          distance_m: number | null
          last_lat: number | null
          last_lng: number | null
          last_seen_at: string | null
          open_jobs: number
          status: Database["public"]["Enums"]["driver_presence_status"]
          user_id: string
        }[]
      }
      update_delivery_job_status: {
        Args: {
          p_delivery_job_id: string
          p_status: Database["public"]["Enums"]["delivery_job_status"]
        }
        Returns: Json
      }
      update_item_kit: {
        Args: {
          p_is_active?: boolean
          p_kit_id: string
          p_sell_mode?: Database["public"]["Enums"]["kit_sell_mode"]
        }
        Returns: string
      }
      update_warehouse_bin: {
        Args: {
          p_aisle?: string
          p_bin_id: string
          p_is_active?: boolean
          p_name?: string
          p_pick_path_seq?: number
          p_rack?: string
          p_shelf?: string
        }
        Returns: string
      }
      upsert_customer_garage_vehicle: {
        Args: {
          p_engine?: string
          p_generation?: string
          p_id?: string
          p_is_primary?: boolean
          p_make?: string
          p_model?: string
          p_vin?: string
        }
        Returns: string
      }
      upsert_salary_structure: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_effective_from: string
          p_effective_to?: string
          p_employee_id: string
          p_pay_type: Database["public"]["Enums"]["salary_pay_type"]
          p_rate: number
        }
        Returns: string
      }
      upsert_stock_reconciliation_lines: {
        Args: { p_lines: Json; p_reconciliation_id: string }
        Returns: number
      }
      upsert_supplier_quotation: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate: number
          p_lines: Json
          p_notes?: string
          p_rfq_id: string
          p_valid_until?: string
        }
        Returns: string
      }
    }
    Enums: {
      account_type: "asset" | "liability" | "equity" | "income" | "expense"
      ai_delivery_channel: "email" | "whatsapp"
      ai_delivery_status: "queued" | "sent" | "failed" | "skipped"
      ai_report_cadence: "daily" | "weekly" | "monthly"
      ai_report_run_status:
        | "pending"
        | "running"
        | "succeeded"
        | "partial"
        | "failed"
      attendance_event_type: "clock_in" | "clock_out"
      bank_recon_status: "open" | "matched" | "cleared"
      cart_channel: "pos" | "storefront"
      chat_participant_role: "customer" | "staff"
      chat_sender_kind: "customer" | "staff" | "system"
      chat_thread_kind: "support" | "parts"
      chat_thread_status: "open" | "assigned" | "closed"
      consignment_entry_purpose:
        | "receive"
        | "return_to_supplier"
        | "place_at_customer"
        | "return_from_customer"
        | "take_ownership"
        | "recognize_sale"
      consignment_kind: "supplier_owned" | "customer_held"
      contipay_intent_status:
        | "pending"
        | "authorized"
        | "settled"
        | "failed"
        | "cancelled"
      contipay_method: "ecocash" | "visa" | "zimswitch"
      currency_code: "USD" | "ZIG"
      delivery_job_status: "pending" | "dispatched" | "completed" | "failed"
      delivery_note_status: "draft" | "submitted" | "cancelled"
      employee_status: "active" | "inactive" | "terminated"
      forecast_suggestion_status: "open" | "converted" | "dismissed"
      fulfillment_mode: "immediate" | "dispatch"
      journal_status: "draft" | "posted"
      kit_line_kind: "header" | "component"
      kit_sell_mode: "stocked" | "explode"
      loyalty_movement: "earn" | "redeem" | "expire" | "reverse"
      payment_entry_status: "draft" | "posted" | "cancelled"
      payment_tender: "cash" | "bank" | "contipay" | "store_credit" | "paynow"
      paynow_intent_status:
        | "pending"
        | "authorized"
        | "settled"
        | "failed"
        | "cancelled"
      paynow_method: "ecocash" | "onemoney" | "innbucks" | "visa"
      payroll_run_status: "draft" | "submitted" | "cancelled"
      pick_list_status: "draft" | "done" | "cancelled"
      procurement_doc_status: "draft" | "submitted" | "cancelled"
      product_review_status: "pending" | "approved" | "rejected"
      receipt_channel: "sms" | "email" | "whatsapp"
      receipt_outbox_status:
        | "pending"
        | "rendering"
        | "sending"
        | "sent"
        | "failed"
        | "cancelled"
      salary_pay_type: "hourly" | "salary"
      sales_doc_status: "draft" | "posted" | "cancelled" | "on_hold"
      sales_doc_type: "invoice" | "credit_note"
      sms_event_priority: "low" | "normal" | "high"
      sms_outbox_status: "pending" | "sending" | "sent" | "failed" | "cancelled"
      delivery_completed_via: "pod" | "manual" | "admin"
      delivery_eta_source: "haversine" | "osrm" | "manual"
      delivery_failure_reason:
        | "customer_absent"
        | "refused"
        | "wrong_address"
        | "damaged"
        | "other"
      driver_presence_status: "available" | "on_duty" | "break" | "offline"
      staff_role:
        | "admin"
        | "finance"
        | "warehouse"
        | "sales"
        | "dispatcher"
        | "hr"
        | "driver"
      stock_entry_status:
        | "draft"
        | "pending_approval"
        | "posted"
        | "cancelled"
        | "rejected"
      stock_entry_type: "receipt" | "transfer" | "issue"
      stock_reconciliation_scope: "full" | "partial"
      store_credit_movement: "issue" | "redeem" | "reverse"
      valuation_method: "FIFO" | "AVG"
      warranty_claim_resolution:
        | "replacement"
        | "credit_note"
        | "return_only"
        | "reject_only"
      warranty_claim_status: "open" | "approved" | "rejected" | "closed"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  graphql_public: {
    Enums: {},
  },
  public: {
    Enums: {
      account_type: ["asset", "liability", "equity", "income", "expense"],
      ai_delivery_channel: ["email", "whatsapp"],
      ai_delivery_status: ["queued", "sent", "failed", "skipped"],
      ai_report_cadence: ["daily", "weekly", "monthly"],
      ai_report_run_status: [
        "pending",
        "running",
        "succeeded",
        "partial",
        "failed",
      ],
      attendance_event_type: ["clock_in", "clock_out"],
      bank_recon_status: ["open", "matched", "cleared"],
      cart_channel: ["pos", "storefront"],
      chat_participant_role: ["customer", "staff"],
      chat_sender_kind: ["customer", "staff", "system"],
      chat_thread_kind: ["support", "parts"],
      chat_thread_status: ["open", "assigned", "closed"],
      consignment_entry_purpose: [
        "receive",
        "return_to_supplier",
        "place_at_customer",
        "return_from_customer",
        "take_ownership",
        "recognize_sale",
      ],
      consignment_kind: ["supplier_owned", "customer_held"],
      contipay_intent_status: [
        "pending",
        "authorized",
        "settled",
        "failed",
        "cancelled",
      ],
      contipay_method: ["ecocash", "visa", "zimswitch"],
      currency_code: ["USD", "ZIG"],
      delivery_job_status: ["pending", "dispatched", "completed", "failed"],
      delivery_note_status: ["draft", "submitted", "cancelled"],
      employee_status: ["active", "inactive", "terminated"],
      forecast_suggestion_status: ["open", "converted", "dismissed"],
      fulfillment_mode: ["immediate", "dispatch"],
      journal_status: ["draft", "posted"],
      kit_line_kind: ["header", "component"],
      kit_sell_mode: ["stocked", "explode"],
      loyalty_movement: ["earn", "redeem", "expire", "reverse"],
      payment_entry_status: ["draft", "posted", "cancelled"],
      payment_tender: ["cash", "bank", "contipay", "store_credit", "paynow"],
      paynow_intent_status: [
        "pending",
        "authorized",
        "settled",
        "failed",
        "cancelled",
      ],
      paynow_method: ["ecocash", "onemoney", "innbucks", "visa"],
      payroll_run_status: ["draft", "submitted", "cancelled"],
      pick_list_status: ["draft", "done", "cancelled"],
      procurement_doc_status: ["draft", "submitted", "cancelled"],
      receipt_channel: ["sms", "email", "whatsapp"],
      receipt_outbox_status: [
        "pending",
        "rendering",
        "sending",
        "sent",
        "failed",
        "cancelled",
      ],
      salary_pay_type: ["hourly", "salary"],
      sales_doc_status: ["draft", "posted", "cancelled", "on_hold"],
      sales_doc_type: ["invoice", "credit_note"],
      sms_event_priority: ["low", "normal", "high"],
      sms_outbox_status: ["pending", "sending", "sent", "failed", "cancelled"],
      delivery_completed_via: ["pod", "manual", "admin"],
      delivery_eta_source: ["haversine", "osrm", "manual"],
      delivery_failure_reason: [
        "customer_absent",
        "refused",
        "wrong_address",
        "damaged",
        "other",
      ],
      driver_presence_status: ["available", "on_duty", "break", "offline"],
      staff_role: [
        "admin",
        "finance",
        "warehouse",
        "sales",
        "dispatcher",
        "hr",
        "driver",
      ],
      stock_entry_status: [
        "draft",
        "pending_approval",
        "posted",
        "cancelled",
        "rejected",
      ],
      stock_entry_type: ["receipt", "transfer", "issue"],
      stock_reconciliation_scope: ["full", "partial"],
      store_credit_movement: ["issue", "redeem", "reverse"],
      valuation_method: ["FIFO", "AVG"],
      warranty_claim_resolution: [
        "replacement",
        "credit_note",
        "return_only",
        "reject_only",
      ],
      warranty_claim_status: ["open", "approved", "rejected", "closed"],
    },
  },
} as const

