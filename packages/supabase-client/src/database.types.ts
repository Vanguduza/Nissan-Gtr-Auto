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
      account_period_balances: {
        Row: {
          account_code: string
          closed_at: string | null
          closed_by: string | null
          closing_balance: number | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          notes: string | null
          opened_at: string
          opened_by: string | null
          opening_balance: number
          period_end: string
          period_start: string
          physical_count: number | null
          status: Database["public"]["Enums"]["account_period_status"]
          variance: number | null
        }
        ComputedFields: never
        Insert: {
          account_code: string
          closed_at?: string | null
          closed_by?: string | null
          closing_balance?: number | null
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          opened_at?: string
          opened_by?: string | null
          opening_balance?: number
          period_end: string
          period_start: string
          physical_count?: number | null
          status?: Database["public"]["Enums"]["account_period_status"]
          variance?: number | null
        }
        Update: {
          account_code?: string
          closed_at?: string | null
          closed_by?: string | null
          closing_balance?: number | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          opened_at?: string
          opened_by?: string | null
          opening_balance?: number
          period_end?: string
          period_start?: string
          physical_count?: number | null
          status?: Database["public"]["Enums"]["account_period_status"]
          variance?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "account_period_balances_account_code_fkey"
            columns: ["account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
        ]
      }
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
        ComputedFields: never
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
      ai_promo_deliveries: {
        Row: {
          body_preview: string | null
          channel: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at: string
          customer_id: string
          error: string | null
          gemini_used: boolean
          id: string
          oem_skus: string[]
          provider_ref: string | null
          recipient: string
          run_id: string
          sent_at: string | null
          status: Database["public"]["Enums"]["ai_delivery_status"]
          vehicle_label: string | null
        }
        ComputedFields: never
        Insert: {
          body_preview?: string | null
          channel: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at?: string
          customer_id: string
          error?: string | null
          gemini_used?: boolean
          id?: string
          oem_skus?: string[]
          provider_ref?: string | null
          recipient: string
          run_id: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["ai_delivery_status"]
          vehicle_label?: string | null
        }
        Update: {
          body_preview?: string | null
          channel?: Database["public"]["Enums"]["ai_delivery_channel"]
          created_at?: string
          customer_id?: string
          error?: string | null
          gemini_used?: boolean
          id?: string
          oem_skus?: string[]
          provider_ref?: string | null
          recipient?: string
          run_id?: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["ai_delivery_status"]
          vehicle_label?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "ai_promo_deliveries_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "ai_promo_deliveries_run_id_fkey"
            columns: ["run_id"]
            isOneToOne: false
            referencedRelation: "ai_promo_runs"
            referencedColumns: ["id"]
          },
        ]
      }
      ai_promo_runs: {
        Row: {
          candidates_considered: number
          created_at: string
          error: string | null
          finished_at: string | null
          gemini_used: boolean
          id: string
          messages_queued: number
          started_at: string | null
          status: Database["public"]["Enums"]["ai_promo_run_status"]
        }
        ComputedFields: never
        Insert: {
          candidates_considered?: number
          created_at?: string
          error?: string | null
          finished_at?: string | null
          gemini_used?: boolean
          id?: string
          messages_queued?: number
          started_at?: string | null
          status?: Database["public"]["Enums"]["ai_promo_run_status"]
        }
        Update: {
          candidates_considered?: number
          created_at?: string
          error?: string | null
          finished_at?: string | null
          gemini_used?: boolean
          id?: string
          messages_queued?: number
          started_at?: string | null
          status?: Database["public"]["Enums"]["ai_promo_run_status"]
        }
        Relationships: []
      }
      ai_promo_settings: {
        Row: {
          active: boolean
          channels: Database["public"]["Enums"]["ai_delivery_channel"][]
          cooldown_days: number
          id: string
          inactivity_days: number
          include_llm_copy: boolean
          max_skus_per_message: number
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          active?: boolean
          channels?: Database["public"]["Enums"]["ai_delivery_channel"][]
          cooldown_days?: number
          id?: string
          inactivity_days?: number
          include_llm_copy?: boolean
          max_skus_per_message?: number
          updated_at?: string
        }
        Update: {
          active?: boolean
          channels?: Database["public"]["Enums"]["ai_delivery_channel"][]
          cooldown_days?: number
          id?: string
          inactivity_days?: number
          include_llm_copy?: boolean
          max_skus_per_message?: number
          updated_at?: string
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
        ComputedFields: never
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
          kpi_json: NonNullable<Json>
          narrative: string | null
          period_end: string
          period_start: string
          started_at: string | null
          status: Database["public"]["Enums"]["ai_report_run_status"]
          subscription_id: string | null
        }
        ComputedFields: never
        Insert: {
          cadence: Database["public"]["Enums"]["ai_report_cadence"]
          created_at?: string
          error?: string | null
          finished_at?: string | null
          gemini_used?: boolean
          id?: string
          kpi_json?: NonNullable<Json>
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
          kpi_json?: NonNullable<Json>
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
        ComputedFields: never
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
      ai_worker_schedules: {
        Row: {
          body_json: NonNullable<Json>
          cadence: string
          created_at: string
          cron_expr: string
          edge_path: string
          id: string
          is_active: boolean
          last_run_at: string | null
          notes: string | null
          updated_at: string
          worker_key: string
        }
        ComputedFields: never
        Insert: {
          body_json?: NonNullable<Json>
          cadence: string
          created_at?: string
          cron_expr: string
          edge_path: string
          id?: string
          is_active?: boolean
          last_run_at?: string | null
          notes?: string | null
          updated_at?: string
          worker_key: string
        }
        Update: {
          body_json?: NonNullable<Json>
          cadence?: string
          created_at?: string
          cron_expr?: string
          edge_path?: string
          id?: string
          is_active?: boolean
          last_run_at?: string | null
          notes?: string | null
          updated_at?: string
          worker_key?: string
        }
        Relationships: []
      }
      app_settings: {
        Row: {
          description: string | null
          key: string
          updated_at: string
          value_json: NonNullable<Json>
        }
        ComputedFields: never
        Insert: {
          description?: string | null
          key: string
          updated_at?: string
          value_json: NonNullable<Json>
        }
        Update: {
          description?: string | null
          key?: string
          updated_at?: string
          value_json?: NonNullable<Json>
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
        ComputedFields: never
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
      auth_otp_challenges: {
        Row: {
          attempt_count: number
          channel: string
          code_hash: string
          consumed_at: string | null
          created_at: string
          expires_at: string
          id: string
          identifier: string
        }
        ComputedFields: never
        Insert: {
          attempt_count?: number
          channel: string
          code_hash: string
          consumed_at?: string | null
          created_at?: string
          expires_at: string
          id?: string
          identifier: string
        }
        Update: {
          attempt_count?: number
          channel?: string
          code_hash?: string
          consumed_at?: string | null
          created_at?: string
          expires_at?: string
          id?: string
          identifier?: string
        }
        Relationships: []
      }
      auth_otp_proofs: {
        Row: {
          consumed_at: string | null
          created_at: string
          email: string | null
          expires_at: string
          id: string
          phone_e164: string | null
        }
        ComputedFields: never
        Insert: {
          consumed_at?: string | null
          created_at?: string
          email?: string | null
          expires_at: string
          id?: string
          phone_e164?: string | null
        }
        Update: {
          consumed_at?: string | null
          created_at?: string
          email?: string | null
          expires_at?: string
          id?: string
          phone_e164?: string | null
        }
        Relationships: []
      }
      auth_request_rate_limits: {
        Row: {
          key_hash: string
          request_count: number
          scope: string
          updated_at: string
          window_started_at: string
        }
        ComputedFields: never
        Insert: {
          key_hash: string
          request_count?: number
          scope: string
          updated_at?: string
          window_started_at?: string
        }
        Update: {
          key_hash?: string
          request_count?: number
          scope?: string
          updated_at?: string
          window_started_at?: string
        }
        Relationships: []
      }
      bank_recon_matches: {
        Row: {
          id: string
          journal_entry_line_id: string
          matched_at: string
          matched_by: string | null
          statement_line_id: string
        }
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
      business_document_profile: {
        Row: {
          address_line1: string | null
          address_line2: string | null
          city: string | null
          country: string | null
          domain: string
          email: string | null
          legal_name: string
          phone_e164: string | null
          registration_number: string | null
          singleton: boolean
          trading_name: string
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          address_line1?: string | null
          address_line2?: string | null
          city?: string | null
          country?: string | null
          domain?: string
          email?: string | null
          legal_name?: string
          phone_e164?: string | null
          registration_number?: string | null
          singleton?: boolean
          trading_name?: string
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          address_line1?: string | null
          address_line2?: string | null
          city?: string | null
          country?: string | null
          domain?: string
          email?: string | null
          legal_name?: string
          phone_e164?: string | null
          registration_number?: string | null
          singleton?: boolean
          trading_name?: string
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: []
      }
      canonical_staff_accounts: {
        Row: {
          created_at: string
          email: string
          full_name: string
          is_active: boolean
          role: Database["public"]["Enums"]["staff_role"]
          updated_at: string
          user_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          email: string
          full_name: string
          is_active?: boolean
          role: Database["public"]["Enums"]["staff_role"]
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          email?: string
          full_name?: string
          is_active?: boolean
          role?: Database["public"]["Enums"]["staff_role"]
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      catalog_diagram_parts: {
        Row: {
          bbox_height: number | null
          bbox_width: number | null
          bbox_x: number | null
          bbox_y: number | null
          callout_ref: string | null
          created_at: string
          description: string | null
          diagram_path: string | null
          diagram_slug: string
          external_item_id: string | null
          id: string
          itemslist_id: string
          maker_slug: string
          model_slug: string
          oem_part_number: string
          quantity: string | null
          section_slug: string
          variant_slug: string
        }
        ComputedFields: never
        Insert: {
          bbox_height?: number | null
          bbox_width?: number | null
          bbox_x?: number | null
          bbox_y?: number | null
          callout_ref?: string | null
          created_at?: string
          description?: string | null
          diagram_path?: string | null
          diagram_slug: string
          external_item_id?: string | null
          id?: string
          itemslist_id: string
          maker_slug: string
          model_slug: string
          oem_part_number: string
          quantity?: string | null
          section_slug: string
          variant_slug: string
        }
        Update: {
          bbox_height?: number | null
          bbox_width?: number | null
          bbox_x?: number | null
          bbox_y?: number | null
          callout_ref?: string | null
          created_at?: string
          description?: string | null
          diagram_path?: string | null
          diagram_slug?: string
          external_item_id?: string | null
          id?: string
          itemslist_id?: string
          maker_slug?: string
          model_slug?: string
          oem_part_number?: string
          quantity?: string | null
          section_slug?: string
          variant_slug?: string
        }
        Relationships: []
      }
      catalog_diagrams: {
        Row: {
          created_at: string
          diagram_kind: string | null
          hotspot_count: number | null
          id: string
          image_height: number | null
          image_url: string | null
          image_width: number | null
          maker_slug: string
          model_slug: string
          section_slug: string
          slug: string
          source_url: string | null
          storage_path: string | null
          title: string
          variant_slug: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          diagram_kind?: string | null
          hotspot_count?: number | null
          id?: string
          image_height?: number | null
          image_url?: string | null
          image_width?: number | null
          maker_slug: string
          model_slug: string
          section_slug: string
          slug: string
          source_url?: string | null
          storage_path?: string | null
          title: string
          variant_slug: string
        }
        Update: {
          created_at?: string
          diagram_kind?: string | null
          hotspot_count?: number | null
          id?: string
          image_height?: number | null
          image_url?: string | null
          image_width?: number | null
          maker_slug?: string
          model_slug?: string
          section_slug?: string
          slug?: string
          source_url?: string | null
          storage_path?: string | null
          title?: string
          variant_slug?: string
        }
        Relationships: []
      }
      catalog_makers: {
        Row: {
          created_at: string
          name: string
          slug: string
          sort_order: number
          source: string | null
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          name: string
          slug: string
          sort_order?: number
          source?: string | null
        }
        Update: {
          created_at?: string
          name?: string
          slug?: string
          sort_order?: number
          source?: string | null
        }
        Relationships: []
      }
      catalog_meili_sync_state: {
        Row: {
          document_count: number
          id: number
          index_uid: string
          last_full_sync_at: string | null
          meili_task_uid: number | null
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          document_count?: number
          id?: number
          index_uid?: string
          last_full_sync_at?: string | null
          meili_task_uid?: number | null
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          document_count?: number
          id?: number
          index_uid?: string
          last_full_sync_at?: string | null
          meili_task_uid?: number | null
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: []
      }
      catalog_models: {
        Row: {
          body_type: string | null
          created_at: string
          display_name: string
          id: string
          maker_slug: string
          slug: string
          sort_key: string
          source_url: string | null
          year_end: number | null
          year_start: number | null
        }
        ComputedFields: never
        Insert: {
          body_type?: string | null
          created_at?: string
          display_name: string
          id?: string
          maker_slug: string
          slug: string
          sort_key: string
          source_url?: string | null
          year_end?: number | null
          year_start?: number | null
        }
        Update: {
          body_type?: string | null
          created_at?: string
          display_name?: string
          id?: string
          maker_slug?: string
          slug?: string
          sort_key?: string
          source_url?: string | null
          year_end?: number | null
          year_start?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "catalog_models_maker_slug_fkey"
            columns: ["maker_slug"]
            isOneToOne: false
            referencedRelation: "catalog_makers"
            referencedColumns: ["slug"]
          },
        ]
      }
      catalog_offline_device_grants: {
        Row: {
          app_flavor: string
          device_key_sha256: string
          first_granted_at: string
          id: string
          last_granted_at: string
          release_id: string
          revoked_at: string | null
          user_id: string
        }
        ComputedFields: never
        Insert: {
          app_flavor: string
          device_key_sha256: string
          first_granted_at?: string
          id?: string
          last_granted_at?: string
          release_id: string
          revoked_at?: string | null
          user_id: string
        }
        Update: {
          app_flavor?: string
          device_key_sha256?: string
          first_granted_at?: string
          id?: string
          last_granted_at?: string
          release_id?: string
          revoked_at?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "catalog_offline_device_grants_release_id_fkey"
            columns: ["release_id"]
            isOneToOne: false
            referencedRelation: "catalog_offline_releases"
            referencedColumns: ["id"]
          },
        ]
      }
      catalog_offline_releases: {
        Row: {
          created_at: string
          diagram_count: number
          encrypted_sha256: string
          encrypted_size_bytes: number
          encryption_format: string
          fitment_count: number
          generated_at: string
          id: string
          image_count: number
          is_current: boolean
          key_secret_name: string
          maker_slug: string
          published_at: string | null
          r2_object_key: string
          schema_version: number
          section_count: number
          source_release: string | null
          sqlite_page_size: number
          vehicle_count: number
          version: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          diagram_count?: number
          encrypted_sha256: string
          encrypted_size_bytes: number
          encryption_format?: string
          fitment_count?: number
          generated_at?: string
          id?: string
          image_count?: number
          is_current?: boolean
          key_secret_name: string
          maker_slug: string
          published_at?: string | null
          r2_object_key: string
          schema_version: number
          section_count?: number
          source_release?: string | null
          sqlite_page_size?: number
          vehicle_count?: number
          version: string
        }
        Update: {
          created_at?: string
          diagram_count?: number
          encrypted_sha256?: string
          encrypted_size_bytes?: number
          encryption_format?: string
          fitment_count?: number
          generated_at?: string
          id?: string
          image_count?: number
          is_current?: boolean
          key_secret_name?: string
          maker_slug?: string
          published_at?: string | null
          r2_object_key?: string
          schema_version?: number
          section_count?: number
          source_release?: string | null
          sqlite_page_size?: number
          vehicle_count?: number
          version?: string
        }
        Relationships: []
      }
      catalog_r2_release_inventory_work: {
        Row: {
          created_at: string
          kind: string
          prefix: string
          request_id: number
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          kind: string
          prefix: string
          request_id: number
        }
        Update: {
          created_at?: string
          kind?: string
          prefix?: string
          request_id?: number
        }
        Relationships: []
      }
      catalog_r2_serving_objects: {
        Row: {
          bytes: number
          content_encoding: string | null
          content_type: string
          created_at: string
          id: string
          maker_slug: string
          metadata: NonNullable<Json>
          object_key: string
          object_kind: string
          release_id: string
          row_count: number
          scope_key: string
          sha256: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          bytes?: number
          content_encoding?: string | null
          content_type?: string
          created_at?: string
          id?: string
          maker_slug: string
          metadata?: NonNullable<Json>
          object_key: string
          object_kind: string
          release_id: string
          row_count?: number
          scope_key: string
          sha256?: string | null
          updated_at?: string
        }
        Update: {
          bytes?: number
          content_encoding?: string | null
          content_type?: string
          created_at?: string
          id?: string
          maker_slug?: string
          metadata?: NonNullable<Json>
          object_key?: string
          object_kind?: string
          release_id?: string
          row_count?: number
          scope_key?: string
          sha256?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "catalog_r2_serving_objects_release_id_fkey"
            columns: ["release_id"]
            isOneToOne: false
            referencedRelation: "catalog_releases"
            referencedColumns: ["id"]
          },
        ]
      }
      catalog_r2_vehicle_master: {
        Row: {
          chassis_code: string
          created_at: string
          engine_code: string
          maker_slug: string
          model: string
          r2_scope_key: string
          sales_region: string | null
          source_release_version: string
          updated_at: string
          year_end: number | null
          year_start: number | null
        }
        ComputedFields: never
        Insert: {
          chassis_code: string
          created_at?: string
          engine_code: string
          maker_slug?: string
          model: string
          r2_scope_key: string
          sales_region?: string | null
          source_release_version?: string
          updated_at?: string
          year_end?: number | null
          year_start?: number | null
        }
        Update: {
          chassis_code?: string
          created_at?: string
          engine_code?: string
          maker_slug?: string
          model?: string
          r2_scope_key?: string
          sales_region?: string | null
          source_release_version?: string
          updated_at?: string
          year_end?: number | null
          year_start?: number | null
        }
        Relationships: []
      }
      catalog_releases: {
        Row: {
          bucket_name: string
          bundle_object_key: string | null
          bundle_sha256: string | null
          bundle_size_bytes: number
          created_at: string
          diagram_count: number
          id: string
          is_current: boolean
          maker_slug: string
          published_at: string | null
          updated_at: string
          version: string
        }
        ComputedFields: never
        Insert: {
          bucket_name: string
          bundle_object_key?: string | null
          bundle_sha256?: string | null
          bundle_size_bytes?: number
          created_at?: string
          diagram_count?: number
          id?: string
          is_current?: boolean
          maker_slug: string
          published_at?: string | null
          updated_at?: string
          version: string
        }
        Update: {
          bucket_name?: string
          bundle_object_key?: string | null
          bundle_sha256?: string | null
          bundle_size_bytes?: number
          created_at?: string
          diagram_count?: number
          id?: string
          is_current?: boolean
          maker_slug?: string
          published_at?: string | null
          updated_at?: string
          version?: string
        }
        Relationships: []
      }
      catalog_sections: {
        Row: {
          assembly_group_id: string | null
          created_at: string
          id: string
          maker_slug: string
          model_slug: string
          name: string
          slug: string
          sort_order: number
          source_url: string | null
          thumbnail_url: string | null
          variant_slug: string
        }
        ComputedFields: never
        Insert: {
          assembly_group_id?: string | null
          created_at?: string
          id?: string
          maker_slug: string
          model_slug: string
          name: string
          slug: string
          sort_order?: number
          source_url?: string | null
          thumbnail_url?: string | null
          variant_slug: string
        }
        Update: {
          assembly_group_id?: string | null
          created_at?: string
          id?: string
          maker_slug?: string
          model_slug?: string
          name?: string
          slug?: string
          sort_order?: number
          source_url?: string | null
          thumbnail_url?: string | null
          variant_slug?: string
        }
        Relationships: []
      }
      catalog_variants: {
        Row: {
          chassis_code: string
          created_at: string
          engine_code: string | null
          external_data_id: string | null
          frame: string | null
          grade: string | null
          id: string
          maker_slug: string
          model_slug: string
          sales_region: string | null
          slug: string
          source_url: string | null
          year_end: number | null
          year_label: string | null
          year_start: number | null
        }
        ComputedFields: never
        Insert: {
          chassis_code: string
          created_at?: string
          engine_code?: string | null
          external_data_id?: string | null
          frame?: string | null
          grade?: string | null
          id?: string
          maker_slug: string
          model_slug: string
          sales_region?: string | null
          slug: string
          source_url?: string | null
          year_end?: number | null
          year_label?: string | null
          year_start?: number | null
        }
        Update: {
          chassis_code?: string
          created_at?: string
          engine_code?: string | null
          external_data_id?: string | null
          frame?: string | null
          grade?: string | null
          id?: string
          maker_slug?: string
          model_slug?: string
          sales_region?: string | null
          slug?: string
          source_url?: string | null
          year_end?: number | null
          year_label?: string | null
          year_start?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "catalog_variants_maker_slug_fkey"
            columns: ["maker_slug"]
            isOneToOne: false
            referencedRelation: "catalog_makers"
            referencedColumns: ["slug"]
          },
        ]
      }
      chart_of_accounts: {
        Row: {
          account_type: Database["public"]["Enums"]["account_type"]
          code: string
          created_at: string
          display_name: string
          is_active: boolean
          name: string
        }
        ComputedFields: never
        Insert: {
          account_type: Database["public"]["Enums"]["account_type"]
          code: string
          created_at?: string
          display_name: string
          is_active?: boolean
          name: string
        }
        Update: {
          account_type?: Database["public"]["Enums"]["account_type"]
          code?: string
          created_at?: string
          display_name?: string
          is_active?: boolean
          name?: string
        }
        Relationships: []
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
        ComputedFields: never
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
        ComputedFields: never
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
          _can_select_chat_thread: boolean | null
        }
        ComputedFields: "_can_select_chat_thread"
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
      commerce_manual_payment_requests: {
        Row: {
          commerce_order_id: string
          completed_at: string | null
          created_at: string
          id: string
          payment_entry_id: string | null
          reference: string | null
          tender: Database["public"]["Enums"]["payment_tender"]
        }
        ComputedFields: never
        Insert: {
          commerce_order_id: string
          completed_at?: string | null
          created_at?: string
          id: string
          payment_entry_id?: string | null
          reference?: string | null
          tender: Database["public"]["Enums"]["payment_tender"]
        }
        Update: {
          commerce_order_id?: string
          completed_at?: string | null
          created_at?: string
          id?: string
          payment_entry_id?: string | null
          reference?: string | null
          tender?: Database["public"]["Enums"]["payment_tender"]
        }
        Relationships: [
          {
            foreignKeyName: "commerce_manual_payment_requests_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_manual_payment_requests_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      commerce_orders: {
        Row: {
          active_payment_intent_id: string | null
          active_payment_provider: string | null
          cart_id: string
          checkout_request_id: string
          checkout_snapshot: NonNullable<Json>
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          exchange_rate_applied: number
          finalized_at: string | null
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id: string
          payment_exception: string | null
          reservation_expires_at: string | null
          sales_invoice_id: string | null
          settled_payment_entry_id: string | null
          settled_provider: string | null
          settled_provider_ref: string | null
          state: Database["public"]["Enums"]["commerce_order_state"]
          subtotal: number
          total: number
          updated_at: string
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          active_payment_intent_id?: string | null
          active_payment_provider?: string | null
          cart_id: string
          checkout_request_id: string
          checkout_snapshot: NonNullable<Json>
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          exchange_rate_applied?: number
          finalized_at?: string | null
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          payment_exception?: string | null
          reservation_expires_at?: string | null
          sales_invoice_id?: string | null
          settled_payment_entry_id?: string | null
          settled_provider?: string | null
          settled_provider_ref?: string | null
          state?: Database["public"]["Enums"]["commerce_order_state"]
          subtotal: number
          total: number
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          active_payment_intent_id?: string | null
          active_payment_provider?: string | null
          cart_id?: string
          checkout_request_id?: string
          checkout_snapshot?: NonNullable<Json>
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          exchange_rate_applied?: number
          finalized_at?: string | null
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          payment_exception?: string | null
          reservation_expires_at?: string | null
          sales_invoice_id?: string | null
          settled_payment_entry_id?: string | null
          settled_provider?: string | null
          settled_provider_ref?: string | null
          state?: Database["public"]["Enums"]["commerce_order_state"]
          subtotal?: number
          total?: number
          updated_at?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "commerce_orders_cart_id_fkey"
            columns: ["cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_orders_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_orders_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_orders_settled_payment_entry_id_fkey"
            columns: ["settled_payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_orders_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      commerce_outbox: {
        Row: {
          aggregate_id: string
          aggregate_type: string
          attempts: number
          created_at: string
          delivered_at: string | null
          event_key: string
          event_type: string
          id: string
          last_error: string | null
          next_attempt_at: string | null
          payload: NonNullable<Json>
          state: Database["public"]["Enums"]["commerce_outbox_state"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          aggregate_id: string
          aggregate_type?: string
          attempts?: number
          created_at?: string
          delivered_at?: string | null
          event_key: string
          event_type: string
          id?: string
          last_error?: string | null
          next_attempt_at?: string | null
          payload?: NonNullable<Json>
          state?: Database["public"]["Enums"]["commerce_outbox_state"]
          updated_at?: string
        }
        Update: {
          aggregate_id?: string
          aggregate_type?: string
          attempts?: number
          created_at?: string
          delivered_at?: string | null
          event_key?: string
          event_type?: string
          id?: string
          last_error?: string | null
          next_attempt_at?: string | null
          payload?: NonNullable<Json>
          state?: Database["public"]["Enums"]["commerce_outbox_state"]
          updated_at?: string
        }
        Relationships: []
      }
      commerce_payment_exceptions: {
        Row: {
          amount: number
          commerce_order_id: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          detail: string | null
          exception_code: string
          id: string
          payment_entry_id: string | null
          provider: string
          provider_intent_id: string | null
          provider_ref: string | null
          resolution: string | null
          resolved_at: string | null
        }
        ComputedFields: never
        Insert: {
          amount: number
          commerce_order_id: string
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          detail?: string | null
          exception_code: string
          id?: string
          payment_entry_id?: string | null
          provider: string
          provider_intent_id?: string | null
          provider_ref?: string | null
          resolution?: string | null
          resolved_at?: string | null
        }
        Update: {
          amount?: number
          commerce_order_id?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          detail?: string | null
          exception_code?: string
          id?: string
          payment_entry_id?: string | null
          provider?: string
          provider_intent_id?: string | null
          provider_ref?: string | null
          resolution?: string | null
          resolved_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "commerce_payment_exceptions_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "commerce_payment_exceptions_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "consignment_entry_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
            foreignKeyName: "consignment_stock_levels_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          commerce_order_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          exchange_rate_applied: number
          external_ref: string
          failure_reason: string | null
          id: string
          last_webhook_at: string | null
          metadata: NonNullable<Json>
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
        ComputedFields: never
        Insert: {
          amount: number
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
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
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref?: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
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
            foreignKeyName: "contipay_payment_intents_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
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
        ComputedFields: never
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
      customer_addresses: {
        Row: {
          city: string | null
          country: string
          created_at: string
          customer_id: string
          id: string
          is_default: boolean
          label: string
          line1: string
          line2: string | null
          postal_code: string | null
          province: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          city?: string | null
          country?: string
          created_at?: string
          customer_id: string
          id?: string
          is_default?: boolean
          label?: string
          line1: string
          line2?: string | null
          postal_code?: string | null
          province?: string | null
          updated_at?: string
        }
        Update: {
          city?: string | null
          country?: string
          created_at?: string
          customer_id?: string
          id?: string
          is_default?: boolean
          label?: string
          line1?: string
          line2?: string | null
          postal_code?: string | null
          province?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_addresses_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_compare_items: {
        Row: {
          created_at: string
          customer_id: string
          id: string
          stock_item_id: string
        }
        ComputedFields: never
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
            foreignKeyName: "customer_compare_items_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_compare_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_compare_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      customer_garage_vehicles: {
        Row: {
          chassis_code: string | null
          created_at: string
          customer_id: string
          engine: string | null
          generation: string | null
          id: string
          is_primary: boolean
          make: string | null
          model: string | null
          model_slug: string | null
          updated_at: string
          vin: string | null
        }
        ComputedFields: never
        Insert: {
          chassis_code?: string | null
          created_at?: string
          customer_id: string
          engine?: string | null
          generation?: string | null
          id?: string
          is_primary?: boolean
          make?: string | null
          model?: string | null
          model_slug?: string | null
          updated_at?: string
          vin?: string | null
        }
        Update: {
          chassis_code?: string | null
          created_at?: string
          customer_id?: string
          engine?: string | null
          generation?: string | null
          id?: string
          is_primary?: boolean
          make?: string | null
          model?: string | null
          model_slug?: string | null
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
        ComputedFields: never
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
          {
            foreignKeyName: "customer_price_overrides_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      customer_product_review_photos: {
        Row: {
          created_at: string
          id: string
          review_id: string
          sort_order: number
          storage_path: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          id?: string
          review_id: string
          sort_order?: number
          storage_path: string
        }
        Update: {
          created_at?: string
          id?: string
          review_id?: string
          sort_order?: number
          storage_path?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_product_review_photos_review_id_fkey"
            columns: ["review_id"]
            isOneToOne: false
            referencedRelation: "customer_product_reviews"
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
        ComputedFields: never
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
          {
            foreignKeyName: "customer_product_reviews_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      customer_receipt_outbox: {
        Row: {
          attempt_count: number
          channel: Database["public"]["Enums"]["receipt_channel"]
          claimed_at: string | null
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
        ComputedFields: never
        Insert: {
          attempt_count?: number
          channel: Database["public"]["Enums"]["receipt_channel"]
          claimed_at?: string | null
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
          claimed_at?: string | null
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
      customer_return_request_lines: {
        Row: {
          cost_total_basis: number
          created_at: string
          id: string
          line_total_snapshot: number
          qty: number
          qty_base: number
          return_request_id: string
          source_invoice_line_id: string
          stock_item_id: string
          unit_cost_basis: number
          unit_price_snapshot: number
          uom_id: string
        }
        ComputedFields: never
        Insert: {
          cost_total_basis: number
          created_at?: string
          id?: string
          line_total_snapshot: number
          qty: number
          qty_base: number
          return_request_id: string
          source_invoice_line_id: string
          stock_item_id: string
          unit_cost_basis: number
          unit_price_snapshot: number
          uom_id: string
        }
        Update: {
          cost_total_basis?: number
          created_at?: string
          id?: string
          line_total_snapshot?: number
          qty?: number
          qty_base?: number
          return_request_id?: string
          source_invoice_line_id?: string
          stock_item_id?: string
          unit_cost_basis?: number
          unit_price_snapshot?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_return_request_lines_return_request_id_fkey"
            columns: ["return_request_id"]
            isOneToOne: false
            referencedRelation: "customer_return_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_return_request_lines_source_invoice_line_id_fkey"
            columns: ["source_invoice_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_return_request_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_return_request_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "customer_return_request_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_return_requests: {
        Row: {
          ar_credit_amount: number
          created_at: string
          credit_note_id: string | null
          customer_id: string
          id: string
          preferred_resolution: Database["public"]["Enums"]["return_resolution_method"]
          reason: string | null
          received_at: string | null
          received_by: string | null
          refund_due_amount: number
          requested_at: string
          requested_by: string | null
          review_notes: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          source_invoice_id: string
          status: Database["public"]["Enums"]["customer_return_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          ar_credit_amount?: number
          created_at?: string
          credit_note_id?: string | null
          customer_id: string
          id?: string
          preferred_resolution?: Database["public"]["Enums"]["return_resolution_method"]
          reason?: string | null
          received_at?: string | null
          received_by?: string | null
          refund_due_amount?: number
          requested_at?: string
          requested_by?: string | null
          review_notes?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          source_invoice_id: string
          status?: Database["public"]["Enums"]["customer_return_status"]
          updated_at?: string
        }
        Update: {
          ar_credit_amount?: number
          created_at?: string
          credit_note_id?: string | null
          customer_id?: string
          id?: string
          preferred_resolution?: Database["public"]["Enums"]["return_resolution_method"]
          reason?: string | null
          received_at?: string | null
          received_by?: string | null
          refund_due_amount?: number
          requested_at?: string
          requested_by?: string | null
          review_notes?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          source_invoice_id?: string
          status?: Database["public"]["Enums"]["customer_return_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "customer_return_requests_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: true
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_return_requests_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_return_requests_source_invoice_id_fkey"
            columns: ["source_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_suspensions: {
        Row: {
          customer_id: string
          findings: NonNullable<Json>
          id: string
          lift_reason: string | null
          lifted_at: string | null
          lifted_by: string | null
          reason: string
          source: string
          status: string
          suspended_at: string
          suspended_by: string | null
        }
        ComputedFields: never
        Insert: {
          customer_id: string
          findings?: NonNullable<Json>
          id?: string
          lift_reason?: string | null
          lifted_at?: string | null
          lifted_by?: string | null
          reason: string
          source: string
          status: string
          suspended_at?: string
          suspended_by?: string | null
        }
        Update: {
          customer_id?: string
          findings?: NonNullable<Json>
          id?: string
          lift_reason?: string | null
          lifted_at?: string | null
          lifted_by?: string | null
          reason?: string
          source?: string
          status?: string
          suspended_at?: string
          suspended_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "customer_suspensions_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
        ]
      }
      customer_wishlist_items: {
        Row: {
          created_at: string
          customer_id: string
          id: string
          notify_when_in_stock: boolean
          stock_item_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          customer_id: string
          id?: string
          notify_when_in_stock?: boolean
          stock_item_id: string
        }
        Update: {
          created_at?: string
          customer_id?: string
          id?: string
          notify_when_in_stock?: boolean
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
          {
            foreignKeyName: "customer_wishlist_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      customers: {
        Row: {
          business_name: string | null
          created_at: string
          credit_hold: boolean
          credit_limit: number
          credit_limit_minor: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_kind: string
          display_name: string
          email: string | null
          email_receipts: boolean
          id: string
          last_promotional_message_at: string | null
          marketing_opt_in: boolean
          open_balance: number
          open_balance_minor: number | null
          phone_e164: string | null
          price_list_id: string | null
          profile_id: string | null
          sms_receipts: boolean
          updated_at: string
          whatsapp_e164: string | null
          whatsapp_receipts: boolean
        }
        ComputedFields: never
        Insert: {
          business_name?: string | null
          created_at?: string
          credit_hold?: boolean
          credit_limit?: number
          credit_limit_minor?: number | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_kind?: string
          display_name: string
          email?: string | null
          email_receipts?: boolean
          id?: string
          last_promotional_message_at?: string | null
          marketing_opt_in?: boolean
          open_balance?: number
          open_balance_minor?: number | null
          phone_e164?: string | null
          price_list_id?: string | null
          profile_id?: string | null
          sms_receipts?: boolean
          updated_at?: string
          whatsapp_e164?: string | null
          whatsapp_receipts?: boolean
        }
        Update: {
          business_name?: string | null
          created_at?: string
          credit_hold?: boolean
          credit_limit?: number
          credit_limit_minor?: number | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_kind?: string
          display_name?: string
          email?: string | null
          email_receipts?: boolean
          id?: string
          last_promotional_message_at?: string | null
          marketing_opt_in?: boolean
          open_balance?: number
          open_balance_minor?: number | null
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
      daily_exchange_rates: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          notes: string | null
          rate: number
          rate_date: string
          set_by: string | null
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          rate: number
          rate_date?: string
          set_by?: string | null
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          rate?: number
          rate_date?: string
          set_by?: string | null
        }
        Relationships: []
      }
      delivery_balance_approvals: {
        Row: {
          amount: number
          basis: string
          created_at: string
          credit_limit: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          decided_at: string | null
          decided_by: string | null
          decision_note: string | null
          delivery_job_id: string
          exposure: number | null
          id: string
          reason: string
          requested_by: string
          sales_invoice_id: string
          status: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          basis: string
          created_at?: string
          credit_limit?: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          decided_at?: string | null
          decided_by?: string | null
          decision_note?: string | null
          delivery_job_id: string
          exposure?: number | null
          id?: string
          reason: string
          requested_by?: string
          sales_invoice_id: string
          status: string
          updated_at?: string
        }
        Update: {
          amount?: number
          basis?: string
          created_at?: string
          credit_limit?: number | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          decided_at?: string | null
          decided_by?: string | null
          decision_note?: string | null
          delivery_job_id?: string
          exposure?: number | null
          id?: string
          reason?: string
          requested_by?: string
          sales_invoice_id?: string
          status?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_balance_approvals_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_balance_approvals_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_balance_approvals_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_cash_collections: {
        Row: {
          amount: number
          collected_at: string
          collected_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string
          handin_id: string | null
          id: string
          notes: string | null
          payment_entry_id: string
          request_id: string
          sales_invoice_id: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          collected_at?: string
          collected_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string
          handin_id?: string | null
          id?: string
          notes?: string | null
          payment_entry_id: string
          request_id: string
          sales_invoice_id: string
        }
        Update: {
          amount?: number
          collected_at?: string
          collected_by?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_job_id?: string
          handin_id?: string | null
          id?: string
          notes?: string | null
          payment_entry_id?: string
          request_id?: string
          sales_invoice_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "delivery_cash_collections_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_cash_collections_handin_id_fkey"
            columns: ["handin_id"]
            isOneToOne: false
            referencedRelation: "driver_cash_handins"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_cash_collections_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: true
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "delivery_cash_collections_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      delivery_jobs: {
        Row: {
          assignee_user_id: string | null
          completed_at: string | null
          completed_via:
            | Database["public"]["Enums"]["delivery_completed_via"]
            | null
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
          failure_reason_code:
            | Database["public"]["Enums"]["delivery_failure_reason"]
            | null
          id: string
          notes: string | null
          pickup_lat: number | null
          pickup_lng: number | null
          pod_photo_path: string | null
          pod_signature_path: string | null
          reattempt_of: string | null
          route_sequence: number | null
          status: Database["public"]["Enums"]["delivery_job_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?:
            | Database["public"]["Enums"]["delivery_completed_via"]
            | null
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
          failure_reason_code?:
            | Database["public"]["Enums"]["delivery_failure_reason"]
            | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          reattempt_of?: string | null
          route_sequence?: number | null
          status?: Database["public"]["Enums"]["delivery_job_status"]
          updated_at?: string
        }
        Update: {
          assignee_user_id?: string | null
          completed_at?: string | null
          completed_via?:
            | Database["public"]["Enums"]["delivery_completed_via"]
            | null
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
          failure_reason_code?:
            | Database["public"]["Enums"]["delivery_failure_reason"]
            | null
          id?: string
          notes?: string | null
          pickup_lat?: number | null
          pickup_lng?: number | null
          pod_photo_path?: string | null
          pod_signature_path?: string | null
          reattempt_of?: string | null
          route_sequence?: number | null
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "delivery_note_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
        ComputedFields: never
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
      delivery_track_tokens: {
        Row: {
          created_at: string
          delivery_job_id: string
          expires_at: string
          id: string
          revoked_at: string | null
          token_hash: string
        }
        ComputedFields: never
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
      domain_events: {
        Row: {
          actor_user_id: string | null
          dedupe_key: string
          event_code: string
          id: string
          occurred_at: string
          payload: NonNullable<Json>
        }
        ComputedFields: never
        Insert: {
          actor_user_id?: string | null
          dedupe_key: string
          event_code: string
          id?: string
          occurred_at?: string
          payload?: NonNullable<Json>
        }
        Update: {
          actor_user_id?: string | null
          dedupe_key?: string
          event_code?: string
          id?: string
          occurred_at?: string
          payload?: NonNullable<Json>
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
      driver_cash_handins: {
        Row: {
          approved_at: string | null
          approved_by: string | null
          approver_notes: string | null
          collection_count: number
          currency: Database["public"]["Enums"]["currency_code"]
          declared_amount: number
          document_number: string
          driver_notes: string | null
          driver_user_id: string
          expected_amount: number
          id: string
          journal_entry_id: string | null
          owed_amount: number
          reason_code: string | null
          received_amount: number | null
          received_at: string | null
          received_by: string | null
          receiver_notes: string | null
          recovered_amount: number
          status: string
          submitted_at: string
          variance: number | null
          written_off_amount: number
        }
        ComputedFields: never
        Insert: {
          approved_at?: string | null
          approved_by?: string | null
          approver_notes?: string | null
          collection_count: number
          currency: Database["public"]["Enums"]["currency_code"]
          declared_amount: number
          document_number: string
          driver_notes?: string | null
          driver_user_id: string
          expected_amount: number
          id?: string
          journal_entry_id?: string | null
          owed_amount?: number
          reason_code?: string | null
          received_amount?: number | null
          received_at?: string | null
          received_by?: string | null
          receiver_notes?: string | null
          recovered_amount?: number
          status: string
          submitted_at?: string
          variance?: number | null
          written_off_amount?: number
        }
        Update: {
          approved_at?: string | null
          approved_by?: string | null
          approver_notes?: string | null
          collection_count?: number
          currency?: Database["public"]["Enums"]["currency_code"]
          declared_amount?: number
          document_number?: string
          driver_notes?: string | null
          driver_user_id?: string
          expected_amount?: number
          id?: string
          journal_entry_id?: string | null
          owed_amount?: number
          reason_code?: string | null
          received_amount?: number | null
          received_at?: string | null
          received_by?: string | null
          receiver_notes?: string | null
          recovered_amount?: number
          status?: string
          submitted_at?: string
          variance?: number | null
          written_off_amount?: number
        }
        Relationships: [
          {
            foreignKeyName: "driver_cash_handins_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      driver_cash_recoveries: {
        Row: {
          amount: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          handin_id: string
          id: string
          journal_entry_id: string
          kind: string
          notes: string | null
          recorded_by: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          handin_id: string
          id?: string
          journal_entry_id: string
          kind: string
          notes?: string | null
          recorded_by: string
        }
        Update: {
          amount?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          handin_id?: string
          id?: string
          journal_entry_id?: string
          kind?: string
          notes?: string | null
          recorded_by?: string
        }
        Relationships: [
          {
            foreignKeyName: "driver_cash_recoveries_handin_id_fkey"
            columns: ["handin_id"]
            isOneToOne: false
            referencedRelation: "driver_cash_handins"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "driver_cash_recoveries_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
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
        ComputedFields: never
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
      ecocash_payment_intents: {
        Row: {
          amount: number
          channel: string
          commerce_order_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          exchange_rate_applied: number
          external_ref: string
          failure_reason: string | null
          id: string
          last_webhook_at: string | null
          metadata: NonNullable<Json>
          payer_mode: string
          payer_msisdn: string
          payment_entry_id: string | null
          provider_ref: string | null
          sales_invoice_id: string | null
          settlement_amount: number | null
          settlement_currency:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate: number | null
          status: Database["public"]["Enums"]["ecocash_intent_status"]
          updated_at: string
          webhook_payload_hash: string | null
          whatsapp_flow_order_id: string | null
        }
        ComputedFields: never
        Insert: {
          amount: number
          channel?: string
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
          payer_mode?: string
          payer_msisdn: string
          payment_entry_id?: string | null
          provider_ref?: string | null
          sales_invoice_id?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["ecocash_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
          whatsapp_flow_order_id?: string | null
        }
        Update: {
          amount?: number
          channel?: string
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref?: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
          payer_mode?: string
          payer_msisdn?: string
          payment_entry_id?: string | null
          provider_ref?: string | null
          sales_invoice_id?: string | null
          settlement_amount?: number | null
          settlement_currency?:
            | Database["public"]["Enums"]["currency_code"]
            | null
          settlement_exchange_rate?: number | null
          status?: Database["public"]["Enums"]["ecocash_intent_status"]
          updated_at?: string
          webhook_payload_hash?: string | null
          whatsapp_flow_order_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "ecocash_payment_intents_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "ecocash_payment_intents_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "ecocash_payment_intents_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "ecocash_payment_intents_whatsapp_flow_order_id_fkey"
            columns: ["whatsapp_flow_order_id"]
            isOneToOne: false
            referencedRelation: "whatsapp_flow_orders"
            referencedColumns: ["id"]
          },
        ]
      }
      ecocash_webhook_events: {
        Row: {
          created_at: string
          external_ref: string | null
          id: string
          intent_id: string | null
          payload_hash: string
          processed: boolean
          result_note: string | null
        }
        ComputedFields: never
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
            foreignKeyName: "ecocash_webhook_events_intent_id_fkey"
            columns: ["intent_id"]
            isOneToOne: false
            referencedRelation: "ecocash_payment_intents"
            referencedColumns: ["id"]
          },
        ]
      }
      employees: {
        Row: {
          created_at: string
          email: string | null
          employee_code: string
          full_name: string
          grade_id: string | null
          hire_date: string | null
          hr_role_id: string | null
          id: string
          phone_e164: string | null
          signature_captured_at: string | null
          signature_mime_type: string | null
          signature_sha256: string | null
          signature_storage_bucket: string | null
          signature_storage_path: string | null
          signature_updated_by: string | null
          status: Database["public"]["Enums"]["employee_status"]
          updated_at: string
          user_id: string | null
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          email?: string | null
          employee_code: string
          full_name: string
          grade_id?: string | null
          hire_date?: string | null
          hr_role_id?: string | null
          id?: string
          phone_e164?: string | null
          signature_captured_at?: string | null
          signature_mime_type?: string | null
          signature_sha256?: string | null
          signature_storage_bucket?: string | null
          signature_storage_path?: string | null
          signature_updated_by?: string | null
          status?: Database["public"]["Enums"]["employee_status"]
          updated_at?: string
          user_id?: string | null
        }
        Update: {
          created_at?: string
          email?: string | null
          employee_code?: string
          full_name?: string
          grade_id?: string | null
          hire_date?: string | null
          hr_role_id?: string | null
          id?: string
          phone_e164?: string | null
          signature_captured_at?: string | null
          signature_mime_type?: string | null
          signature_sha256?: string | null
          signature_storage_bucket?: string | null
          signature_storage_path?: string | null
          signature_updated_by?: string | null
          status?: Database["public"]["Enums"]["employee_status"]
          updated_at?: string
          user_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "employees_grade_id_fkey"
            columns: ["grade_id"]
            isOneToOne: false
            referencedRelation: "hr_grades"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "employees_hr_role_id_fkey"
            columns: ["hr_role_id"]
            isOneToOne: false
            referencedRelation: "hr_roles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "employees_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_audit_log: {
        Row: {
          action: string
          actor_user_id: string | null
          after_state: Json | null
          before_state: Json | null
          created_at: string
          entity_id: string | null
          entity_type: string
          id: string
        }
        ComputedFields: never
        Insert: {
          action: string
          actor_user_id?: string | null
          after_state?: Json | null
          before_state?: Json | null
          created_at?: string
          entity_id?: string | null
          entity_type: string
          id?: string
        }
        Update: {
          action?: string
          actor_user_id?: string | null
          after_state?: Json | null
          before_state?: Json | null
          created_at?: string
          entity_id?: string | null
          entity_type?: string
          id?: string
        }
        Relationships: []
      }
      finance_refunds: {
        Row: {
          amount: number
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          document_number: string | null
          exchange_rate_applied: number
          id: string
          notes: string | null
          original_invoice_id: string
          posted_at: string
          posted_by: string | null
          reversing_journal_entry_id: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          original_invoice_id: string
          posted_at?: string
          posted_by?: string | null
          reversing_journal_entry_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          original_invoice_id?: string
          posted_at?: string
          posted_by?: string | null
          reversing_journal_entry_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_refunds_original_invoice_id_fkey"
            columns: ["original_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "finance_refunds_reversing_journal_entry_id_fkey"
            columns: ["reversing_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_requisition_approvals: {
        Row: {
          approved_at: string
          approver_user_id: string
          id: string
          note: string | null
          requisition_id: string
        }
        ComputedFields: never
        Insert: {
          approved_at?: string
          approver_user_id: string
          id?: string
          note?: string | null
          requisition_id: string
        }
        Update: {
          approved_at?: string
          approver_user_id?: string
          id?: string
          note?: string | null
          requisition_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisition_approvals_requisition_id_fkey"
            columns: ["requisition_id"]
            isOneToOne: false
            referencedRelation: "finance_requisitions"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_requisition_lines: {
        Row: {
          amount: number
          created_at: string
          description: string | null
          expense_account_code: string
          id: string
          line_no: number
          requisition_id: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          description?: string | null
          expense_account_code: string
          id?: string
          line_no: number
          requisition_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          description?: string | null
          expense_account_code?: string
          id?: string
          line_no?: number
          requisition_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisition_lines_expense_account_code_fkey"
            columns: ["expense_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisition_lines_requisition_id_fkey"
            columns: ["requisition_id"]
            isOneToOne: false
            referencedRelation: "finance_requisitions"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_requisition_receipts: {
        Row: {
          content_type: string | null
          created_at: string
          created_by: string | null
          id: string
          requisition_id: string
          storage_path: string
        }
        ComputedFields: never
        Insert: {
          content_type?: string | null
          created_at?: string
          created_by?: string | null
          id?: string
          requisition_id: string
          storage_path: string
        }
        Update: {
          content_type?: string | null
          created_at?: string
          created_by?: string | null
          id?: string
          requisition_id?: string
          storage_path?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisition_receipts_requisition_id_fkey"
            columns: ["requisition_id"]
            isOneToOne: false
            referencedRelation: "finance_requisitions"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_requisition_thresholds: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          is_active: boolean
          min_amount: number
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          required_approvals: number
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          min_amount?: number
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          required_approvals?: number
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          min_amount?: number
          req_type?: Database["public"]["Enums"]["finance_requisition_type"]
          required_approvals?: number
        }
        Relationships: []
      }
      finance_requisitions: {
        Row: {
          amount: number
          approval_count: number
          approved_at: string | null
          approved_by: string | null
          cancelled_at: string | null
          cancelled_by: string | null
          cash_account_code: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          disbursed_at: string | null
          disbursed_by: string | null
          document_number: string | null
          exchange_rate_applied: number | null
          expense_account_code: string
          id: string
          intended_entry_date: string | null
          journal_entry_id: string | null
          memo: string | null
          payee: string | null
          payment_entry_id: string | null
          rejected_at: string | null
          rejected_by: string | null
          rejection_reason: string | null
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by: string
          required_approvals: number
          status: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at: string | null
          updated_at: string
          _finance_req_can_approve: boolean | null
        }
        ComputedFields: "_finance_req_can_approve"
        Insert: {
          amount: number
          approval_count?: number
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          cancelled_by?: string | null
          cash_account_code: string
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          disbursed_at?: string | null
          disbursed_by?: string | null
          document_number?: string | null
          exchange_rate_applied?: number | null
          expense_account_code: string
          id?: string
          intended_entry_date?: string | null
          journal_entry_id?: string | null
          memo?: string | null
          payee?: string | null
          payment_entry_id?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by: string
          required_approvals?: number
          status?: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Update: {
          amount?: number
          approval_count?: number
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          cancelled_by?: string | null
          cash_account_code?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          disbursed_at?: string | null
          disbursed_by?: string | null
          document_number?: string | null
          exchange_rate_applied?: number | null
          expense_account_code?: string
          id?: string
          intended_entry_date?: string | null
          journal_entry_id?: string | null
          memo?: string | null
          payee?: string | null
          payment_entry_id?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          req_type?: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by?: string
          required_approvals?: number
          status?: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisitions_cash_account_code_fkey"
            columns: ["cash_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisitions_expense_account_code_fkey"
            columns: ["expense_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisitions_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "finance_requisitions_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
      }
      fleet_vehicles: {
        Row: {
          assigned_driver_user_id: string | null
          created_at: string
          created_by: string | null
          id: string
          label: string | null
          notes: string | null
          plate: string
          status: Database["public"]["Enums"]["fleet_vehicle_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          assigned_driver_user_id?: string | null
          created_at?: string
          created_by?: string | null
          id?: string
          label?: string | null
          notes?: string | null
          plate: string
          status?: Database["public"]["Enums"]["fleet_vehicle_status"]
          updated_at?: string
        }
        Update: {
          assigned_driver_user_id?: string | null
          created_at?: string
          created_by?: string | null
          id?: string
          label?: string | null
          notes?: string | null
          plate?: string
          status?: Database["public"]["Enums"]["fleet_vehicle_status"]
          updated_at?: string
        }
        Relationships: []
      }
      forecast_suggestions: {
        Row: {
          converted_at: string | null
          created_at: string
          horizon_days: number
          id: string
          material_request_id: string | null
          on_hand: number
          reason: NonNullable<Json>
          status: Database["public"]["Enums"]["forecast_suggestion_status"]
          stock_item_id: string
          suggested_qty: number
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          converted_at?: string | null
          created_at?: string
          horizon_days?: number
          id?: string
          material_request_id?: string | null
          on_hand?: number
          reason?: NonNullable<Json>
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
          reason?: NonNullable<Json>
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
            foreignKeyName: "forecast_suggestions_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
            foreignKeyName: "goods_receipt_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          supplier_invoice_path: string | null
          supplier_invoice_uploaded_at: string | null
          updated_at: string
          warehouse_id: string
        }
        ComputedFields: never
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
          supplier_invoice_path?: string | null
          supplier_invoice_uploaded_at?: string | null
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
          supplier_invoice_path?: string | null
          supplier_invoice_uploaded_at?: string | null
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
      hr_auth_provision_locks: {
        Row: {
          claim_token: string
          claimed_at: string
          employee_id: string
          expires_at: string
        }
        ComputedFields: never
        Insert: {
          claim_token: string
          claimed_at?: string
          employee_id: string
          expires_at: string
        }
        Update: {
          claim_token?: string
          claimed_at?: string
          employee_id?: string
          expires_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "hr_auth_provision_locks_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: true
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      hr_contract_clause_templates: {
        Row: {
          body_md: string
          code: string
          created_at: string
          id: string
          is_active: boolean
          title: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          body_md: string
          code: string
          created_at?: string
          id?: string
          is_active?: boolean
          title: string
          updated_at?: string
        }
        Update: {
          body_md?: string
          code?: string
          created_at?: string
          id?: string
          is_active?: boolean
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      hr_credential_outbox: {
        Row: {
          attempt_count: number
          body: string
          channel: Database["public"]["Enums"]["hr_credential_channel"]
          claimed_at: string | null
          created_at: string
          created_by: string | null
          employee_id: string
          id: string
          last_error: string | null
          provider_message_id: string | null
          recipient: string
          sent_at: string | null
          status: Database["public"]["Enums"]["hr_credential_outbox_status"]
          user_id: string
        }
        ComputedFields: never
        Insert: {
          attempt_count?: number
          body: string
          channel: Database["public"]["Enums"]["hr_credential_channel"]
          claimed_at?: string | null
          created_at?: string
          created_by?: string | null
          employee_id: string
          id?: string
          last_error?: string | null
          provider_message_id?: string | null
          recipient: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["hr_credential_outbox_status"]
          user_id: string
        }
        Update: {
          attempt_count?: number
          body?: string
          channel?: Database["public"]["Enums"]["hr_credential_channel"]
          claimed_at?: string | null
          created_at?: string
          created_by?: string | null
          employee_id?: string
          id?: string
          last_error?: string | null
          provider_message_id?: string | null
          recipient?: string
          sent_at?: string | null
          status?: Database["public"]["Enums"]["hr_credential_outbox_status"]
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "hr_credential_outbox_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "hr_credential_outbox_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      hr_grades: {
        Row: {
          code: string
          created_at: string
          id: string
          is_active: boolean
          sort_order: number
          title: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          code: string
          created_at?: string
          id?: string
          is_active?: boolean
          sort_order?: number
          title: string
          updated_at?: string
        }
        Update: {
          code?: string
          created_at?: string
          id?: string
          is_active?: boolean
          sort_order?: number
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      hr_leave_balances: {
        Row: {
          employee_id: string
          entitlement_days: number
          id: string
          leave_type_id: string
          updated_at: string
          used_days: number
          year: number
        }
        ComputedFields: never
        Insert: {
          employee_id: string
          entitlement_days?: number
          id?: string
          leave_type_id: string
          updated_at?: string
          used_days?: number
          year: number
        }
        Update: {
          employee_id?: string
          entitlement_days?: number
          id?: string
          leave_type_id?: string
          updated_at?: string
          used_days?: number
          year?: number
        }
        Relationships: [
          {
            foreignKeyName: "hr_leave_balances_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "hr_leave_balances_leave_type_id_fkey"
            columns: ["leave_type_id"]
            isOneToOne: false
            referencedRelation: "hr_leave_types"
            referencedColumns: ["id"]
          },
        ]
      }
      hr_leave_requests: {
        Row: {
          created_at: string
          days: number
          decided_at: string | null
          decided_by: string | null
          decision_note: string | null
          employee_id: string
          end_date: string
          id: string
          leave_type_id: string
          reason: string | null
          start_date: string
          status: Database["public"]["Enums"]["hr_leave_request_status"]
          submitted_at: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          days: number
          decided_at?: string | null
          decided_by?: string | null
          decision_note?: string | null
          employee_id: string
          end_date: string
          id?: string
          leave_type_id: string
          reason?: string | null
          start_date: string
          status?: Database["public"]["Enums"]["hr_leave_request_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Update: {
          created_at?: string
          days?: number
          decided_at?: string | null
          decided_by?: string | null
          decision_note?: string | null
          employee_id?: string
          end_date?: string
          id?: string
          leave_type_id?: string
          reason?: string | null
          start_date?: string
          status?: Database["public"]["Enums"]["hr_leave_request_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "hr_leave_requests_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "hr_leave_requests_leave_type_id_fkey"
            columns: ["leave_type_id"]
            isOneToOne: false
            referencedRelation: "hr_leave_types"
            referencedColumns: ["id"]
          },
        ]
      }
      hr_leave_types: {
        Row: {
          annual_allowance_days: number
          code: string
          created_at: string
          id: string
          is_active: boolean
          is_paid: boolean
          title: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          annual_allowance_days?: number
          code: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_paid?: boolean
          title: string
          updated_at?: string
        }
        Update: {
          annual_allowance_days?: number
          code?: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_paid?: boolean
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      hr_onboarding_drafts: {
        Row: {
          banking_json: Json | null
          completed_at: string | null
          created_at: string
          created_by: string | null
          employee_id: string | null
          health_json: Json | null
          id: string
          payload: NonNullable<Json>
          stage: Database["public"]["Enums"]["hr_onboarding_stage"]
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          banking_json?: Json | null
          completed_at?: string | null
          created_at?: string
          created_by?: string | null
          employee_id?: string | null
          health_json?: Json | null
          id?: string
          payload?: NonNullable<Json>
          stage?: Database["public"]["Enums"]["hr_onboarding_stage"]
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          banking_json?: Json | null
          completed_at?: string | null
          created_at?: string
          created_by?: string | null
          employee_id?: string | null
          health_json?: Json | null
          id?: string
          payload?: NonNullable<Json>
          stage?: Database["public"]["Enums"]["hr_onboarding_stage"]
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "hr_onboarding_drafts_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      hr_payslip_schedules: {
        Row: {
          created_at: string
          cron_expr: string
          id: string
          is_active: boolean
          last_run_at: string | null
          next_run_at: string | null
          pay_frequency: Database["public"]["Enums"]["hr_pay_frequency"]
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          cron_expr: string
          id?: string
          is_active?: boolean
          last_run_at?: string | null
          next_run_at?: string | null
          pay_frequency: Database["public"]["Enums"]["hr_pay_frequency"]
        }
        Update: {
          created_at?: string
          cron_expr?: string
          id?: string
          is_active?: boolean
          last_run_at?: string | null
          next_run_at?: string | null
          pay_frequency?: Database["public"]["Enums"]["hr_pay_frequency"]
        }
        Relationships: []
      }
      hr_roles: {
        Row: {
          approval_flags: NonNullable<Json>
          archived_at: string | null
          clause_template_ids: string[]
          comms_preferences: NonNullable<Json>
          created_at: string
          created_by: string | null
          default_landing: string | null
          department: string | null
          duties_md: string | null
          grade_id: string
          id: string
          is_active: boolean
          module_access: NonNullable<Json>
          parent_role_id: string | null
          pay_frequency: Database["public"]["Enums"]["hr_pay_frequency"]
          remuneration_notes: string | null
          title: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          approval_flags?: NonNullable<Json>
          archived_at?: string | null
          clause_template_ids?: string[]
          comms_preferences?: NonNullable<Json>
          created_at?: string
          created_by?: string | null
          default_landing?: string | null
          department?: string | null
          duties_md?: string | null
          grade_id: string
          id?: string
          is_active?: boolean
          module_access?: NonNullable<Json>
          parent_role_id?: string | null
          pay_frequency?: Database["public"]["Enums"]["hr_pay_frequency"]
          remuneration_notes?: string | null
          title: string
          updated_at?: string
        }
        Update: {
          approval_flags?: NonNullable<Json>
          archived_at?: string | null
          clause_template_ids?: string[]
          comms_preferences?: NonNullable<Json>
          created_at?: string
          created_by?: string | null
          default_landing?: string | null
          department?: string | null
          duties_md?: string | null
          grade_id?: string
          id?: string
          is_active?: boolean
          module_access?: NonNullable<Json>
          parent_role_id?: string | null
          pay_frequency?: Database["public"]["Enums"]["hr_pay_frequency"]
          remuneration_notes?: string | null
          title?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "hr_roles_grade_id_fkey"
            columns: ["grade_id"]
            isOneToOne: false
            referencedRelation: "hr_grades"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "hr_roles_parent_role_id_fkey"
            columns: ["parent_role_id"]
            isOneToOne: false
            referencedRelation: "hr_roles"
            referencedColumns: ["id"]
          },
        ]
      }
      inventory_abc_snapshots: {
        Row: {
          abc_class: Database["public"]["Enums"]["inventory_abc_class"]
          as_of: string
          cumulative_share: number
          id: string
          oem_part_number: string
          period_end: string
          period_start: string
          qty_sold: number
          revenue_share: number
          revenue_usd: number
          stock_item_id: string
        }
        ComputedFields: never
        Insert: {
          abc_class: Database["public"]["Enums"]["inventory_abc_class"]
          as_of?: string
          cumulative_share?: number
          id?: string
          oem_part_number: string
          period_end: string
          period_start: string
          qty_sold?: number
          revenue_share?: number
          revenue_usd?: number
          stock_item_id: string
        }
        Update: {
          abc_class?: Database["public"]["Enums"]["inventory_abc_class"]
          as_of?: string
          cumulative_share?: number
          id?: string
          oem_part_number?: string
          period_end?: string
          period_start?: string
          qty_sold?: number
          revenue_share?: number
          revenue_usd?: number
          stock_item_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "inventory_abc_snapshots_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_abc_snapshots_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      inventory_ai_directives: {
        Row: {
          created_at: string
          created_by: string | null
          directives: NonNullable<Json>
          error: string | null
          gemini_used: boolean
          id: string
          kpi_json: NonNullable<Json>
          narrative: string | null
          period_end: string | null
          period_start: string | null
          warehouse_id: string | null
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          created_by?: string | null
          directives?: NonNullable<Json>
          error?: string | null
          gemini_used?: boolean
          id?: string
          kpi_json?: NonNullable<Json>
          narrative?: string | null
          period_end?: string | null
          period_start?: string | null
          warehouse_id?: string | null
        }
        Update: {
          created_at?: string
          created_by?: string | null
          directives?: NonNullable<Json>
          error?: string | null
          gemini_used?: boolean
          id?: string
          kpi_json?: NonNullable<Json>
          narrative?: string | null
          period_end?: string | null
          period_start?: string | null
          warehouse_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "inventory_ai_directives_warehouse_id_fkey"
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
        ComputedFields: never
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
          {
            foreignKeyName: "inventory_qr_codes_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      inventory_reservations: {
        Row: {
          commerce_order_id: string | null
          consumed_at: string | null
          consumed_qty: number
          created_at: string
          expires_at: string | null
          id: string
          pos_fulfillment_request_id: string | null
          released_at: string | null
          required_qty: number
          reservation_reason: string
          reserved_qty: number
          state: Database["public"]["Enums"]["inventory_reservation_state"]
          stock_item_id: string
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          commerce_order_id?: string | null
          consumed_at?: string | null
          consumed_qty?: number
          created_at?: string
          expires_at?: string | null
          id?: string
          pos_fulfillment_request_id?: string | null
          released_at?: string | null
          required_qty: number
          reservation_reason?: string
          reserved_qty: number
          state?: Database["public"]["Enums"]["inventory_reservation_state"]
          stock_item_id: string
          warehouse_id: string
        }
        Update: {
          commerce_order_id?: string | null
          consumed_at?: string | null
          consumed_qty?: number
          created_at?: string
          expires_at?: string | null
          id?: string
          pos_fulfillment_request_id?: string | null
          released_at?: string | null
          required_qty?: number
          reservation_reason?: string
          reserved_qty?: number
          state?: Database["public"]["Enums"]["inventory_reservation_state"]
          stock_item_id?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "inventory_reservations_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_reservations_pos_fulfillment_request_id_fkey"
            columns: ["pos_fulfillment_request_id"]
            isOneToOne: false
            referencedRelation: "pos_fulfillment_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_reservations_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "inventory_reservations_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "inventory_reservations_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
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
        ComputedFields: never
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
            foreignKeyName: "item_kit_components_component_item_id_fkey"
            columns: ["component_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
          {
            foreignKeyName: "item_kits_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: true
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
            foreignKeyName: "item_uom_conversions_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
      lost_demand: {
        Row: {
          created_at: string
          created_by: string
          id: string
          note: string | null
          qty: number
          stock_item_id: string
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          created_by: string
          id?: string
          note?: string | null
          qty: number
          stock_item_id: string
          warehouse_id: string
        }
        Update: {
          created_at?: string
          created_by?: string
          id?: string
          note?: string | null
          qty?: number
          stock_item_id?: string
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "lost_demand_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "lost_demand_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "lost_demand_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
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
        ComputedFields: never
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
          money_value_minor: number | null
          movement: Database["public"]["Enums"]["loyalty_movement"]
          points: number
          points_balance_after: number
          reason: string | null
          reverses_ledger_id: string | null
          sales_invoice_id: string | null
        }
        ComputedFields: never
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
          money_value_minor?: number | null
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
          money_value_minor?: number | null
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "material_request_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          approved_at: string | null
          approved_by: string | null
          cancelled_at: string | null
          created_at: string
          created_by: string | null
          document_number: string | null
          id: string
          needed_by: string | null
          notes: string | null
          rejected_at: string | null
          rejected_by: string | null
          rejection_reason: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          updated_at: string
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string | null
          document_number?: string | null
          id?: string
          needed_by?: string | null
          notes?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "panic_events_acknowledged_by_fkey"
            columns: ["acknowledged_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
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
        ]
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
        ComputedFields: never
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
          search_vector?: never
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
          search_vector?: never
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
      password_reset_challenges: {
        Row: {
          attempt_count: number
          channel: string
          code_hash: string
          consumed_at: string | null
          created_at: string
          expires_at: string
          id: string
          identifier: string
        }
        ComputedFields: never
        Insert: {
          attempt_count?: number
          channel: string
          code_hash: string
          consumed_at?: string | null
          created_at?: string
          expires_at: string
          id?: string
          identifier: string
        }
        Update: {
          attempt_count?: number
          channel?: string
          code_hash?: string
          consumed_at?: string | null
          created_at?: string
          expires_at?: string
          id?: string
          identifier?: string
        }
        Relationships: []
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
        ComputedFields: never
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
        ComputedFields: never
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
      payment_resolution_letters: {
        Row: {
          amount: number
          authorization_code: string | null
          card_last4: string | null
          card_scheme: string | null
          commerce_order_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          customer_name: string | null
          document_number: string
          external_reference: string | null
          failure_detail: string | null
          id: string
          invoice_document_number: string | null
          issue_notes: string | null
          issued_at: string
          manager_employee_code: string
          manager_employee_id: string
          manager_name: string
          manager_title: string | null
          manager_user_id: string
          observed_status: string
          provider: string
          provider_reference: string | null
          rendered_at: string | null
          rendered_sha256: string | null
          rendered_storage_bucket: string | null
          rendered_storage_path: string | null
          rrn: string | null
          sales_invoice_id: string | null
          signature_mime_type: string
          signature_sha256: string
          signature_storage_bucket: string
          signature_storage_path: string
          source_id: string
          source_kind: Database["public"]["Enums"]["payment_resolution_source_kind"]
          terminal_transaction_id: string | null
        }
        ComputedFields: never
        Insert: {
          amount: number
          authorization_code?: string | null
          card_last4?: string | null
          card_scheme?: string | null
          commerce_order_id?: string | null
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          customer_name?: string | null
          document_number: string
          external_reference?: string | null
          failure_detail?: string | null
          id?: string
          invoice_document_number?: string | null
          issue_notes?: string | null
          issued_at?: string
          manager_employee_code: string
          manager_employee_id: string
          manager_name: string
          manager_title?: string | null
          manager_user_id: string
          observed_status: string
          provider: string
          provider_reference?: string | null
          rendered_at?: string | null
          rendered_sha256?: string | null
          rendered_storage_bucket?: string | null
          rendered_storage_path?: string | null
          rrn?: string | null
          sales_invoice_id?: string | null
          signature_mime_type: string
          signature_sha256: string
          signature_storage_bucket: string
          signature_storage_path: string
          source_id: string
          source_kind: Database["public"]["Enums"]["payment_resolution_source_kind"]
          terminal_transaction_id?: string | null
        }
        Update: {
          amount?: number
          authorization_code?: string | null
          card_last4?: string | null
          card_scheme?: string | null
          commerce_order_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          customer_name?: string | null
          document_number?: string
          external_reference?: string | null
          failure_detail?: string | null
          id?: string
          invoice_document_number?: string | null
          issue_notes?: string | null
          issued_at?: string
          manager_employee_code?: string
          manager_employee_id?: string
          manager_name?: string
          manager_title?: string | null
          manager_user_id?: string
          observed_status?: string
          provider?: string
          provider_reference?: string | null
          rendered_at?: string | null
          rendered_sha256?: string | null
          rendered_storage_bucket?: string | null
          rendered_storage_path?: string | null
          rrn?: string | null
          sales_invoice_id?: string | null
          signature_mime_type?: string
          signature_sha256?: string
          signature_storage_bucket?: string
          signature_storage_path?: string
          source_id?: string
          source_kind?: Database["public"]["Enums"]["payment_resolution_source_kind"]
          terminal_transaction_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "payment_resolution_letters_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_resolution_letters_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_resolution_letters_manager_employee_id_fkey"
            columns: ["manager_employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payment_resolution_letters_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      payment_tender_gl_accounts: {
        Row: {
          account_code: string
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          account_code: string
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Update: {
          account_code?: string
          tender?: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "payment_tender_gl_accounts_account_code_fkey"
            columns: ["account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
        ]
      }
      paynow_payment_intents: {
        Row: {
          amount: number
          commerce_order_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          exchange_rate_applied: number
          external_ref: string
          failure_reason: string | null
          id: string
          last_webhook_at: string | null
          metadata: NonNullable<Json>
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
        ComputedFields: never
        Insert: {
          amount: number
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
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
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          exchange_rate_applied?: number
          external_ref?: string
          failure_reason?: string | null
          id?: string
          last_webhook_at?: string | null
          metadata?: NonNullable<Json>
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
            foreignKeyName: "paynow_payment_intents_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
      payroll_run_funding: {
        Row: {
          created_at: string
          funded_at: string
          funded_by: string | null
          journal_entry_id: string
          payroll_run_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          funded_at?: string
          funded_by?: string | null
          journal_entry_id: string
          payroll_run_id: string
        }
        Update: {
          created_at?: string
          funded_at?: string
          funded_by?: string | null
          journal_entry_id?: string
          payroll_run_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "payroll_run_funding_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: true
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payroll_run_funding_payroll_run_id_fkey"
            columns: ["payroll_run_id"]
            isOneToOne: true
            referencedRelation: "payroll_runs"
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "pick_list_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
          assembly_group_id: string | null
          catalog_section_path: string | null
          category_name: string
          created_at: string
          pcdb_part_type_id: number | null
          pnc_code: string
          search_vector: unknown
          subcategory_name: string | null
        }
        ComputedFields: never
        Insert: {
          assembly_group_id?: string | null
          catalog_section_path?: string | null
          category_name: string
          created_at?: string
          pcdb_part_type_id?: number | null
          pnc_code: string
          search_vector?: never
          subcategory_name?: string | null
        }
        Update: {
          assembly_group_id?: string | null
          catalog_section_path?: string | null
          category_name?: string
          created_at?: string
          pcdb_part_type_id?: number | null
          pnc_code?: string
          search_vector?: never
          subcategory_name?: string | null
        }
        Relationships: []
      }
      pos_action_audit: {
        Row: {
          action: string
          actor_user_id: string | null
          after_state: Json | null
          approval_policy_action: string | null
          approved_by_employee_id: string | null
          approved_by_user_id: string | null
          before_state: Json | null
          created_at: string
          entity_id: string | null
          entity_type: string
          id: string
          notes: string | null
          reason_code: string | null
        }
        ComputedFields: never
        Insert: {
          action: string
          actor_user_id?: string | null
          after_state?: Json | null
          approval_policy_action?: string | null
          approved_by_employee_id?: string | null
          approved_by_user_id?: string | null
          before_state?: Json | null
          created_at?: string
          entity_id?: string | null
          entity_type: string
          id?: string
          notes?: string | null
          reason_code?: string | null
        }
        Update: {
          action?: string
          actor_user_id?: string | null
          after_state?: Json | null
          approval_policy_action?: string | null
          approved_by_employee_id?: string | null
          approved_by_user_id?: string | null
          before_state?: Json | null
          created_at?: string
          entity_id?: string | null
          entity_type?: string
          id?: string
          notes?: string | null
          reason_code?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_action_audit_approved_by_employee_id_fkey"
            columns: ["approved_by_employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_approval_policies: {
        Row: {
          action: string
          always_require_manager: boolean
          reason_required: boolean
          threshold_value: number
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          action: string
          always_require_manager?: boolean
          reason_required?: boolean
          threshold_value?: number
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          action?: string
          always_require_manager?: boolean
          reason_required?: boolean
          threshold_value?: number
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: []
      }
      pos_approval_reason_codes: {
        Row: {
          action: string
          code: string
          is_active: boolean
          label: string
          requires_notes: boolean
          sort_order: number
        }
        ComputedFields: never
        Insert: {
          action: string
          code: string
          is_active?: boolean
          label: string
          requires_notes?: boolean
          sort_order?: number
        }
        Update: {
          action?: string
          code?: string
          is_active?: boolean
          label?: string
          requires_notes?: boolean
          sort_order?: number
        }
        Relationships: []
      }
      pos_card_terminal_attempts: {
        Row: {
          amount: number
          approved_at: string | null
          authorization_code: string | null
          card_last4: string | null
          card_scheme: string | null
          commerce_order_id: string | null
          created_at: string
          created_by: string
          credit_note_id: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string | null
          external_ref: string
          finalization_error: string | null
          finalized_at: string | null
          finalized_by: string | null
          finance_refund_id: string | null
          id: string
          invoice_id: string | null
          operation: Database["public"]["Enums"]["pos_card_terminal_operation"]
          parent_attempt_id: string | null
          payment_entry_id: string | null
          request_id: string
          response_code: string | null
          response_message: string | null
          result_recorded_at: string | null
          result_recorded_by: string | null
          rrn: string | null
          source_invoice_id: string | null
          split_leg_id: string | null
          split_refund_request_id: string | null
          status: Database["public"]["Enums"]["pos_card_terminal_attempt_status"]
          terminal_id: string
          terminal_transaction_id: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          approved_at?: string | null
          authorization_code?: string | null
          card_last4?: string | null
          card_scheme?: string | null
          commerce_order_id?: string | null
          created_at?: string
          created_by: string
          credit_note_id?: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id?: string | null
          external_ref: string
          finalization_error?: string | null
          finalized_at?: string | null
          finalized_by?: string | null
          finance_refund_id?: string | null
          id?: string
          invoice_id?: string | null
          operation: Database["public"]["Enums"]["pos_card_terminal_operation"]
          parent_attempt_id?: string | null
          payment_entry_id?: string | null
          request_id: string
          response_code?: string | null
          response_message?: string | null
          result_recorded_at?: string | null
          result_recorded_by?: string | null
          rrn?: string | null
          source_invoice_id?: string | null
          split_leg_id?: string | null
          split_refund_request_id?: string | null
          status?: Database["public"]["Enums"]["pos_card_terminal_attempt_status"]
          terminal_id: string
          terminal_transaction_id?: string | null
          updated_at?: string
        }
        Update: {
          amount?: number
          approved_at?: string | null
          authorization_code?: string | null
          card_last4?: string | null
          card_scheme?: string | null
          commerce_order_id?: string | null
          created_at?: string
          created_by?: string
          credit_note_id?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_job_id?: string | null
          external_ref?: string
          finalization_error?: string | null
          finalized_at?: string | null
          finalized_by?: string | null
          finance_refund_id?: string | null
          id?: string
          invoice_id?: string | null
          operation?: Database["public"]["Enums"]["pos_card_terminal_operation"]
          parent_attempt_id?: string | null
          payment_entry_id?: string | null
          request_id?: string
          response_code?: string | null
          response_message?: string | null
          result_recorded_at?: string | null
          result_recorded_by?: string | null
          rrn?: string | null
          source_invoice_id?: string | null
          split_leg_id?: string | null
          split_refund_request_id?: string | null
          status?: Database["public"]["Enums"]["pos_card_terminal_attempt_status"]
          terminal_id?: string
          terminal_transaction_id?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_card_terminal_attempts_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_finance_refund_id_fkey"
            columns: ["finance_refund_id"]
            isOneToOne: false
            referencedRelation: "finance_refunds"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_invoice_id_fkey"
            columns: ["invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_parent_attempt_id_fkey"
            columns: ["parent_attempt_id"]
            isOneToOne: false
            referencedRelation: "pos_card_terminal_attempts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_source_invoice_id_fkey"
            columns: ["source_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_split_leg_id_fkey"
            columns: ["split_leg_id"]
            isOneToOne: false
            referencedRelation: "pos_split_payment_legs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_split_refund_request_id_fkey"
            columns: ["split_refund_request_id"]
            isOneToOne: false
            referencedRelation: "pos_split_refund_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_card_terminal_attempts_terminal_id_fkey"
            columns: ["terminal_id"]
            isOneToOne: false
            referencedRelation: "pos_card_terminals"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_card_terminal_device_keys: {
        Row: {
          created_at: string
          created_by: string | null
          device_id: string
          id: string
          is_active: boolean
          key_sha256: string
          public_key_spki_base64: string
          revoked_at: string | null
          terminal_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          created_by?: string | null
          device_id: string
          id?: string
          is_active?: boolean
          key_sha256: string
          public_key_spki_base64: string
          revoked_at?: string | null
          terminal_id: string
        }
        Update: {
          created_at?: string
          created_by?: string | null
          device_id?: string
          id?: string
          is_active?: boolean
          key_sha256?: string
          public_key_spki_base64?: string
          revoked_at?: string | null
          terminal_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_card_terminal_device_keys_terminal_id_fkey"
            columns: ["terminal_id"]
            isOneToOne: false
            referencedRelation: "pos_card_terminals"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_card_terminals: {
        Row: {
          acquirer_name: string | null
          adapter_config: NonNullable<Json>
          adapter_key: string
          allow_delivery: boolean
          code: string
          created_at: string
          created_by: string | null
          device_id: string | null
          external_terminal_id: string | null
          id: string
          is_active: boolean
          label: string
          updated_at: string
          updated_by: string | null
          warehouse_id: string | null
        }
        ComputedFields: never
        Insert: {
          acquirer_name?: string | null
          adapter_config?: NonNullable<Json>
          adapter_key?: string
          allow_delivery?: boolean
          code: string
          created_at?: string
          created_by?: string | null
          device_id?: string | null
          external_terminal_id?: string | null
          id?: string
          is_active?: boolean
          label: string
          updated_at?: string
          updated_by?: string | null
          warehouse_id?: string | null
        }
        Update: {
          acquirer_name?: string | null
          adapter_config?: NonNullable<Json>
          adapter_key?: string
          allow_delivery?: boolean
          code?: string
          created_at?: string
          created_by?: string | null
          device_id?: string | null
          external_terminal_id?: string | null
          id?: string
          is_active?: boolean
          label?: string
          updated_at?: string
          updated_by?: string | null
          warehouse_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_card_terminals_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
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
        ComputedFields: never
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
            foreignKeyName: "pos_cart_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          checkout_locked_at: string | null
          checkout_order_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          delivery_payment_method:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
          document_number: string | null
          exchange_rate_applied: number
          fulfillment_mode: Database["public"]["Enums"]["fulfillment_mode"]
          id: string
          status: string
          till_session_id: string | null
          updated_at: string
          vehicle_chassis_code: string | null
          vehicle_contexts: NonNullable<Json>
          vehicle_engine_code: string | null
          vehicle_generation: string | null
          vehicle_model_name: string | null
          vehicle_model_slug: string | null
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          channel?: Database["public"]["Enums"]["cart_channel"]
          checkout_locked_at?: string | null
          checkout_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          delivery_payment_method?:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          status?: string
          till_session_id?: string | null
          updated_at?: string
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
          warehouse_id: string
        }
        Update: {
          channel?: Database["public"]["Enums"]["cart_channel"]
          checkout_locked_at?: string | null
          checkout_order_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          delivery_payment_method?:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
          document_number?: string | null
          exchange_rate_applied?: number
          fulfillment_mode?: Database["public"]["Enums"]["fulfillment_mode"]
          id?: string
          status?: string
          till_session_id?: string | null
          updated_at?: string
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_carts_checkout_order_id_fkey"
            columns: ["checkout_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_carts_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_carts_till_session_id_fkey"
            columns: ["till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
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
      pos_commerce_tender_settlements: {
        Row: {
          commerce_order_id: string
          completed_at: string | null
          created_at: string
          created_by: string
          invoice_id: string | null
          payment_entry_ids: NonNullable<Json>
          payment_request_id: string
          tender_snapshot: NonNullable<Json>
        }
        ComputedFields: never
        Insert: {
          commerce_order_id: string
          completed_at?: string | null
          created_at?: string
          created_by: string
          invoice_id?: string | null
          payment_entry_ids?: NonNullable<Json>
          payment_request_id: string
          tender_snapshot: NonNullable<Json>
        }
        Update: {
          commerce_order_id?: string
          completed_at?: string | null
          created_at?: string
          created_by?: string
          invoice_id?: string | null
          payment_entry_ids?: NonNullable<Json>
          payment_request_id?: string
          tender_snapshot?: NonNullable<Json>
        }
        Relationships: [
          {
            foreignKeyName: "pos_commerce_tender_settlements_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: false
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_commerce_tender_settlements_invoice_id_fkey"
            columns: ["invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_core_returns: {
        Row: {
          amount: number
          created_at: string
          credit_note_id: string | null
          document_number: string
          id: string
          journal_entry_id: string | null
          notes: string | null
          posted_at: string
          posted_by: string
          qty: number
          quarantine_stock_entry_id: string | null
          reason_code: string
          resolution: Database["public"]["Enums"]["pos_core_return_resolution"]
          source_core_line_id: string
          source_invoice_id: string
          store_credit_ledger_id: string | null
          till_session_id: string | null
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          credit_note_id?: string | null
          document_number: string
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string
          posted_by: string
          qty: number
          quarantine_stock_entry_id?: string | null
          reason_code: string
          resolution: Database["public"]["Enums"]["pos_core_return_resolution"]
          source_core_line_id: string
          source_invoice_id: string
          store_credit_ledger_id?: string | null
          till_session_id?: string | null
        }
        Update: {
          amount?: number
          created_at?: string
          credit_note_id?: string | null
          document_number?: string
          id?: string
          journal_entry_id?: string | null
          notes?: string | null
          posted_at?: string
          posted_by?: string
          qty?: number
          quarantine_stock_entry_id?: string | null
          reason_code?: string
          resolution?: Database["public"]["Enums"]["pos_core_return_resolution"]
          source_core_line_id?: string
          source_invoice_id?: string
          store_credit_ledger_id?: string | null
          till_session_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_core_returns_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_quarantine_stock_entry_id_fkey"
            columns: ["quarantine_stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_source_core_line_id_fkey"
            columns: ["source_core_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_source_invoice_id_fkey"
            columns: ["source_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_store_credit_ledger_id_fkey"
            columns: ["store_credit_ledger_id"]
            isOneToOne: false
            referencedRelation: "store_credit_ledger"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_core_returns_till_session_id_fkey"
            columns: ["till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_fulfillment_deposits: {
        Row: {
          amount: number
          created_at: string
          created_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string
          exchange_rate_applied: number
          id: string
          journal_entry_id: string
          reference: string | null
          refund_journal_entry_id: string | null
          refund_notes: string | null
          refund_till_session_id: string | null
          refunded_at: string | null
          refunded_by: string | null
          request_id: string
          status: string
          store_credit_ledger_id: string
          tender: Database["public"]["Enums"]["payment_tender"]
          till_session_id: string | null
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          created_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string
          exchange_rate_applied: number
          id?: string
          journal_entry_id: string
          reference?: string | null
          refund_journal_entry_id?: string | null
          refund_notes?: string | null
          refund_till_session_id?: string | null
          refunded_at?: string | null
          refunded_by?: string | null
          request_id: string
          status?: string
          store_credit_ledger_id: string
          tender: Database["public"]["Enums"]["payment_tender"]
          till_session_id?: string | null
        }
        Update: {
          amount?: number
          created_at?: string
          created_by?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          document_number?: string
          exchange_rate_applied?: number
          id?: string
          journal_entry_id?: string
          reference?: string | null
          refund_journal_entry_id?: string | null
          refund_notes?: string | null
          refund_till_session_id?: string | null
          refunded_at?: string | null
          refunded_by?: string | null
          request_id?: string
          status?: string
          store_credit_ledger_id?: string
          tender?: Database["public"]["Enums"]["payment_tender"]
          till_session_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_fulfillment_deposits_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_refund_journal_entry_id_fkey"
            columns: ["refund_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_refund_till_session_id_fkey"
            columns: ["refund_till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_request_id_fkey"
            columns: ["request_id"]
            isOneToOne: false
            referencedRelation: "pos_fulfillment_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_store_credit_ledger_id_fkey"
            columns: ["store_credit_ledger_id"]
            isOneToOne: false
            referencedRelation: "store_credit_ledger"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_deposits_till_session_id_fkey"
            columns: ["till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_fulfillment_requests: {
        Row: {
          approved_by: string | null
          cancelled_at: string | null
          cart_id: string | null
          collected_at: string | null
          created_at: string
          customer_id: string | null
          destination_warehouse_id: string | null
          document_number: string
          expires_at: string | null
          id: string
          invoice_id: string | null
          kind: Database["public"]["Enums"]["pos_fulfillment_kind"]
          notes: string | null
          qty: number
          ready_at: string | null
          requested_by: string
          source_warehouse_id: string | null
          status: Database["public"]["Enums"]["pos_fulfillment_status"]
          stock_entry_id: string | null
          stock_item_id: string
          uom_id: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          approved_by?: string | null
          cancelled_at?: string | null
          cart_id?: string | null
          collected_at?: string | null
          created_at?: string
          customer_id?: string | null
          destination_warehouse_id?: string | null
          document_number: string
          expires_at?: string | null
          id?: string
          invoice_id?: string | null
          kind: Database["public"]["Enums"]["pos_fulfillment_kind"]
          notes?: string | null
          qty: number
          ready_at?: string | null
          requested_by: string
          source_warehouse_id?: string | null
          status?: Database["public"]["Enums"]["pos_fulfillment_status"]
          stock_entry_id?: string | null
          stock_item_id: string
          uom_id: string
          updated_at?: string
        }
        Update: {
          approved_by?: string | null
          cancelled_at?: string | null
          cart_id?: string | null
          collected_at?: string | null
          created_at?: string
          customer_id?: string | null
          destination_warehouse_id?: string | null
          document_number?: string
          expires_at?: string | null
          id?: string
          invoice_id?: string | null
          kind?: Database["public"]["Enums"]["pos_fulfillment_kind"]
          notes?: string | null
          qty?: number
          ready_at?: string | null
          requested_by?: string
          source_warehouse_id?: string | null
          status?: Database["public"]["Enums"]["pos_fulfillment_status"]
          stock_entry_id?: string | null
          stock_item_id?: string
          uom_id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_fulfillment_requests_cart_id_fkey"
            columns: ["cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_destination_warehouse_id_fkey"
            columns: ["destination_warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_invoice_id_fkey"
            columns: ["invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_source_warehouse_id_fkey"
            columns: ["source_warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_stock_entry_id_fkey"
            columns: ["stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "pos_fulfillment_requests_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_offline_sale_receipts: {
        Row: {
          actor_user_id: string
          client_sale_id: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          device_id: string | null
          id: string
          invoice_id: string
          payload_hash: string
          warehouse_id: string | null
        }
        ComputedFields: never
        Insert: {
          actor_user_id: string
          client_sale_id: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          device_id?: string | null
          id?: string
          invoice_id: string
          payload_hash: string
          warehouse_id?: string | null
        }
        Update: {
          actor_user_id?: string
          client_sale_id?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          device_id?: string | null
          id?: string
          invoice_id?: string
          payload_hash?: string
          warehouse_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_offline_sale_receipts_invoice_id_fkey"
            columns: ["invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_offline_sale_receipts_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_operator_hidden_bestsellers: {
        Row: {
          hidden_at: string
          stock_item_id: string
          user_id: string
        }
        ComputedFields: never
        Insert: {
          hidden_at?: string
          stock_item_id: string
          user_id: string
        }
        Update: {
          hidden_at?: string
          stock_item_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_operator_hidden_bestsellers_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_operator_hidden_bestsellers_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      pos_operator_popular_pins: {
        Row: {
          category_name: string | null
          created_at: string
          id: string
          image_url: string | null
          item_key: string
          item_type: string
          label: string
          maker_slug: string | null
          model_slug: string | null
          oem_part_number: string | null
          search_query: string
          subcategory_name: string | null
          subtitle: string | null
          updated_at: string
          user_id: string
        }
        ComputedFields: never
        Insert: {
          category_name?: string | null
          created_at?: string
          id?: string
          image_url?: string | null
          item_key: string
          item_type: string
          label: string
          maker_slug?: string | null
          model_slug?: string | null
          oem_part_number?: string | null
          search_query: string
          subcategory_name?: string | null
          subtitle?: string | null
          updated_at?: string
          user_id: string
        }
        Update: {
          category_name?: string | null
          created_at?: string
          id?: string
          image_url?: string | null
          item_key?: string
          item_type?: string
          label?: string
          maker_slug?: string | null
          model_slug?: string | null
          oem_part_number?: string | null
          search_query?: string
          subcategory_name?: string | null
          subtitle?: string | null
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      pos_quotation_lines: {
        Row: {
          created_at: string
          id: string
          is_core_charge: boolean
          line_total: number
          parent_line_id: string | null
          qty: number
          qty_base: number
          quotation_id: string
          sort_order: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          id?: string
          is_core_charge?: boolean
          line_total: number
          parent_line_id?: string | null
          qty: number
          qty_base: number
          quotation_id: string
          sort_order?: number
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          created_at?: string
          id?: string
          is_core_charge?: boolean
          line_total?: number
          parent_line_id?: string | null
          qty?: number
          qty_base?: number
          quotation_id?: string
          sort_order?: number
          stock_item_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_quotation_lines_parent_line_id_fkey"
            columns: ["parent_line_id"]
            isOneToOne: false
            referencedRelation: "pos_quotation_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotation_lines_quotation_id_fkey"
            columns: ["quotation_id"]
            isOneToOne: false
            referencedRelation: "pos_quotations"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "pos_quotation_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_quotations: {
        Row: {
          converted_cart_id: string | null
          converted_invoice_id: string | null
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          document_number: string | null
          exchange_rate_applied: number
          id: string
          notes: string | null
          sent_at: string | null
          sent_channel: string | null
          sent_contact: string | null
          source_cart_id: string | null
          status: Database["public"]["Enums"]["pos_quotation_status"]
          updated_at: string
          valid_until: string | null
          vehicle_chassis_code: string | null
          vehicle_contexts: NonNullable<Json>
          vehicle_engine_code: string | null
          vehicle_generation: string | null
          vehicle_model_name: string | null
          vehicle_model_slug: string | null
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          converted_cart_id?: string | null
          converted_invoice_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          sent_at?: string | null
          sent_channel?: string | null
          sent_contact?: string | null
          source_cart_id?: string | null
          status?: Database["public"]["Enums"]["pos_quotation_status"]
          updated_at?: string
          valid_until?: string | null
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
          warehouse_id: string
        }
        Update: {
          converted_cart_id?: string | null
          converted_invoice_id?: string | null
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          notes?: string | null
          sent_at?: string | null
          sent_channel?: string | null
          sent_contact?: string | null
          source_cart_id?: string | null
          status?: Database["public"]["Enums"]["pos_quotation_status"]
          updated_at?: string
          valid_until?: string | null
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_quotations_converted_cart_id_fkey"
            columns: ["converted_cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotations_converted_invoice_id_fkey"
            columns: ["converted_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotations_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotations_source_cart_id_fkey"
            columns: ["source_cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_quotations_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_return_case_lines: {
        Row: {
          condition: Database["public"]["Enums"]["pos_return_condition"]
          created_at: string
          id: string
          qty: number
          return_case_id: string
          source_invoice_line_id: string
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        ComputedFields: never
        Insert: {
          condition: Database["public"]["Enums"]["pos_return_condition"]
          created_at?: string
          id?: string
          qty: number
          return_case_id: string
          source_invoice_line_id: string
          stock_item_id: string
          unit_price: number
          uom_id: string
        }
        Update: {
          condition?: Database["public"]["Enums"]["pos_return_condition"]
          created_at?: string
          id?: string
          qty?: number
          return_case_id?: string
          source_invoice_line_id?: string
          stock_item_id?: string
          unit_price?: number
          uom_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_return_case_lines_return_case_id_fkey"
            columns: ["return_case_id"]
            isOneToOne: false
            referencedRelation: "pos_return_cases"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_case_lines_source_invoice_line_id_fkey"
            columns: ["source_invoice_line_id"]
            isOneToOne: false
            referencedRelation: "sales_invoice_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_case_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_case_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "pos_return_case_lines_uom_id_fkey"
            columns: ["uom_id"]
            isOneToOne: false
            referencedRelation: "uoms"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_return_cases: {
        Row: {
          adjustment_journal_entry_id: string | null
          approved_at: string | null
          approved_by: string | null
          cancelled_at: string | null
          created_at: string
          created_by: string
          credit_note_id: string | null
          document_number: string
          id: string
          notes: string | null
          posted_at: string | null
          reason_code: string
          replacement_lines: Json | null
          replacement_stock_entry_id: string | null
          resolution: Database["public"]["Enums"]["pos_return_resolution"]
          source_invoice_id: string
          status: Database["public"]["Enums"]["pos_return_case_status"]
          store_credit_ledger_id: string | null
          till_session_id: string | null
          updated_at: string
          warranty_claim_id: string | null
        }
        ComputedFields: never
        Insert: {
          adjustment_journal_entry_id?: string | null
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by: string
          credit_note_id?: string | null
          document_number: string
          id?: string
          notes?: string | null
          posted_at?: string | null
          reason_code: string
          replacement_lines?: Json | null
          replacement_stock_entry_id?: string | null
          resolution: Database["public"]["Enums"]["pos_return_resolution"]
          source_invoice_id: string
          status?: Database["public"]["Enums"]["pos_return_case_status"]
          store_credit_ledger_id?: string | null
          till_session_id?: string | null
          updated_at?: string
          warranty_claim_id?: string | null
        }
        Update: {
          adjustment_journal_entry_id?: string | null
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          created_at?: string
          created_by?: string
          credit_note_id?: string | null
          document_number?: string
          id?: string
          notes?: string | null
          posted_at?: string | null
          reason_code?: string
          replacement_lines?: Json | null
          replacement_stock_entry_id?: string | null
          resolution?: Database["public"]["Enums"]["pos_return_resolution"]
          source_invoice_id?: string
          status?: Database["public"]["Enums"]["pos_return_case_status"]
          store_credit_ledger_id?: string | null
          till_session_id?: string | null
          updated_at?: string
          warranty_claim_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_return_cases_adjustment_journal_entry_id_fkey"
            columns: ["adjustment_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_replacement_stock_entry_id_fkey"
            columns: ["replacement_stock_entry_id"]
            isOneToOne: false
            referencedRelation: "stock_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_source_invoice_id_fkey"
            columns: ["source_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_store_credit_ledger_id_fkey"
            columns: ["store_credit_ledger_id"]
            isOneToOne: false
            referencedRelation: "store_credit_ledger"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_till_session_id_fkey"
            columns: ["till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_return_cases_warranty_claim_id_fkey"
            columns: ["warranty_claim_id"]
            isOneToOne: false
            referencedRelation: "warranty_claims"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_scan_sessions: {
        Row: {
          cart_id: string
          claimed_at: string | null
          created_at: string
          expires_at: string
          id: string
          owner_user_id: string
          pairing_code: string
          revoked_at: string | null
          scanner_user_id: string | null
          status: Database["public"]["Enums"]["pos_scan_session_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          cart_id: string
          claimed_at?: string | null
          created_at?: string
          expires_at: string
          id?: string
          owner_user_id: string
          pairing_code: string
          revoked_at?: string | null
          scanner_user_id?: string | null
          status?: Database["public"]["Enums"]["pos_scan_session_status"]
          updated_at?: string
        }
        Update: {
          cart_id?: string
          claimed_at?: string | null
          created_at?: string
          expires_at?: string
          id?: string
          owner_user_id?: string
          pairing_code?: string
          revoked_at?: string | null
          scanner_user_id?: string | null
          status?: Database["public"]["Enums"]["pos_scan_session_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_scan_sessions_cart_id_fkey"
            columns: ["cart_id"]
            isOneToOne: false
            referencedRelation: "pos_carts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_scan_sessions_owner_user_id_fkey"
            columns: ["owner_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_scan_sessions_scanner_user_id_fkey"
            columns: ["scanner_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_split_acceptance_lines: {
        Row: {
          accepted_at: string
          accepted_by: string
          accepted_line_total: number
          accepted_qty: number
          auto_included_core: boolean
          cart_line_id: string
          id: string
          original_qty: number
          session_id: string
        }
        ComputedFields: never
        Insert: {
          accepted_at?: string
          accepted_by: string
          accepted_line_total: number
          accepted_qty: number
          auto_included_core?: boolean
          cart_line_id: string
          id?: string
          original_qty: number
          session_id: string
        }
        Update: {
          accepted_at?: string
          accepted_by?: string
          accepted_line_total?: number
          accepted_qty?: number
          auto_included_core?: boolean
          cart_line_id?: string
          id?: string
          original_qty?: number
          session_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_split_acceptance_lines_cart_line_id_fkey"
            columns: ["cart_line_id"]
            isOneToOne: false
            referencedRelation: "pos_cart_lines"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_split_acceptance_lines_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_split_payment_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_split_payment_legs: {
        Row: {
          allocated_at: string | null
          allocation_journal_entry_id: string | null
          applied_target_amount: number | null
          card_terminal_attempt_id: string | null
          created_at: string
          created_by: string
          external_reference: string | null
          failed_at: string | null
          id: string
          locked_at: string | null
          metadata: NonNullable<Json>
          payment_entry_id: string | null
          provider_intent_id: string | null
          provider_ref: string | null
          refund_required_amount: number
          refunded_at: string | null
          request_id: string
          requested_amount: number
          sequence_no: number
          session_id: string
          status: Database["public"]["Enums"]["pos_split_payment_leg_status"]
          status_detail: string | null
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          allocated_at?: string | null
          allocation_journal_entry_id?: string | null
          applied_target_amount?: number | null
          card_terminal_attempt_id?: string | null
          created_at?: string
          created_by: string
          external_reference?: string | null
          failed_at?: string | null
          id?: string
          locked_at?: string | null
          metadata?: NonNullable<Json>
          payment_entry_id?: string | null
          provider_intent_id?: string | null
          provider_ref?: string | null
          refund_required_amount?: number
          refunded_at?: string | null
          request_id: string
          requested_amount: number
          sequence_no: number
          session_id: string
          status?: Database["public"]["Enums"]["pos_split_payment_leg_status"]
          status_detail?: string | null
          tender: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Update: {
          allocated_at?: string | null
          allocation_journal_entry_id?: string | null
          applied_target_amount?: number | null
          card_terminal_attempt_id?: string | null
          created_at?: string
          created_by?: string
          external_reference?: string | null
          failed_at?: string | null
          id?: string
          locked_at?: string | null
          metadata?: NonNullable<Json>
          payment_entry_id?: string | null
          provider_intent_id?: string | null
          provider_ref?: string | null
          refund_required_amount?: number
          refunded_at?: string | null
          request_id?: string
          requested_amount?: number
          sequence_no?: number
          session_id?: string
          status?: Database["public"]["Enums"]["pos_split_payment_leg_status"]
          status_detail?: string | null
          tender?: Database["public"]["Enums"]["payment_tender"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_split_payment_legs_allocation_journal_entry_id_fkey"
            columns: ["allocation_journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_split_payment_legs_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_split_payment_legs_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_split_payment_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_split_payment_sessions: {
        Row: {
          cancelled_at: string | null
          captured_amount: number
          commerce_order_id: string
          created_at: string
          created_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          final_invoice_id: string | null
          finalization_error: string | null
          held_amount: number
          id: string
          pending_amount: number
          reduced_basket_accepted_at: string | null
          reduced_basket_accepted_by: string | null
          reduced_basket_customer_confirmed: boolean
          reduced_basket_notes: string | null
          refunded_amount: number
          settled_at: string | null
          status: Database["public"]["Enums"]["pos_split_payment_session_status"]
          total_amount: number
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          cancelled_at?: string | null
          captured_amount?: number
          commerce_order_id: string
          created_at?: string
          created_by: string
          currency: Database["public"]["Enums"]["currency_code"]
          final_invoice_id?: string | null
          finalization_error?: string | null
          held_amount?: number
          id?: string
          pending_amount?: number
          reduced_basket_accepted_at?: string | null
          reduced_basket_accepted_by?: string | null
          reduced_basket_customer_confirmed?: boolean
          reduced_basket_notes?: string | null
          refunded_amount?: number
          settled_at?: string | null
          status?: Database["public"]["Enums"]["pos_split_payment_session_status"]
          total_amount: number
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          cancelled_at?: string | null
          captured_amount?: number
          commerce_order_id?: string
          created_at?: string
          created_by?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          final_invoice_id?: string | null
          finalization_error?: string | null
          held_amount?: number
          id?: string
          pending_amount?: number
          reduced_basket_accepted_at?: string | null
          reduced_basket_accepted_by?: string | null
          reduced_basket_customer_confirmed?: boolean
          reduced_basket_notes?: string | null
          refunded_amount?: number
          settled_at?: string | null
          status?: Database["public"]["Enums"]["pos_split_payment_session_status"]
          total_amount?: number
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pos_split_payment_sessions_commerce_order_id_fkey"
            columns: ["commerce_order_id"]
            isOneToOne: true
            referencedRelation: "commerce_orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_split_payment_sessions_final_invoice_id_fkey"
            columns: ["final_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_split_refund_requests: {
        Row: {
          actual_customer_fee: number
          actual_provider_fee: number
          actual_transfer_fee: number
          approved_at: string | null
          approved_by: string | null
          approved_customer_fee: number
          created_at: string
          created_by: string
          estimated_provider_fee: number
          estimated_transfer_fee: number
          expected_settlement_at: string | null
          external_reference: string | null
          failure_reason: string | null
          fee_policy: string
          gross_amount: number
          id: string
          leg_id: string
          net_customer_refund: number | null
          notes: string | null
          provider_ref: string | null
          session_id: string
          settled_at: string | null
          settled_by: string | null
          status: Database["public"]["Enums"]["pos_split_refund_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          actual_customer_fee?: number
          actual_provider_fee?: number
          actual_transfer_fee?: number
          approved_at?: string | null
          approved_by?: string | null
          approved_customer_fee?: number
          created_at?: string
          created_by: string
          estimated_provider_fee?: number
          estimated_transfer_fee?: number
          expected_settlement_at?: string | null
          external_reference?: string | null
          failure_reason?: string | null
          fee_policy?: string
          gross_amount: number
          id?: string
          leg_id: string
          net_customer_refund?: number | null
          notes?: string | null
          provider_ref?: string | null
          session_id: string
          settled_at?: string | null
          settled_by?: string | null
          status?: Database["public"]["Enums"]["pos_split_refund_status"]
          updated_at?: string
        }
        Update: {
          actual_customer_fee?: number
          actual_provider_fee?: number
          actual_transfer_fee?: number
          approved_at?: string | null
          approved_by?: string | null
          approved_customer_fee?: number
          created_at?: string
          created_by?: string
          estimated_provider_fee?: number
          estimated_transfer_fee?: number
          expected_settlement_at?: string | null
          external_reference?: string | null
          failure_reason?: string | null
          fee_policy?: string
          gross_amount?: number
          id?: string
          leg_id?: string
          net_customer_refund?: number | null
          notes?: string | null
          provider_ref?: string | null
          session_id?: string
          settled_at?: string | null
          settled_by?: string | null
          status?: Database["public"]["Enums"]["pos_split_refund_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_split_refund_requests_leg_id_fkey"
            columns: ["leg_id"]
            isOneToOne: true
            referencedRelation: "pos_split_payment_legs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_split_refund_requests_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_split_payment_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_till_cash_movements: {
        Row: {
          actor_user_id: string
          amount: number
          created_at: string
          finance_refund_id: string | null
          id: string
          kind: Database["public"]["Enums"]["pos_till_cash_movement_kind"]
          notes: string | null
          reason_code: string
          session_id: string
        }
        ComputedFields: never
        Insert: {
          actor_user_id: string
          amount: number
          created_at?: string
          finance_refund_id?: string | null
          id?: string
          kind: Database["public"]["Enums"]["pos_till_cash_movement_kind"]
          notes?: string | null
          reason_code: string
          session_id: string
        }
        Update: {
          actor_user_id?: string
          amount?: number
          created_at?: string
          finance_refund_id?: string | null
          id?: string
          kind?: Database["public"]["Enums"]["pos_till_cash_movement_kind"]
          notes?: string | null
          reason_code?: string
          session_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_till_cash_movements_finance_refund_id_fkey"
            columns: ["finance_refund_id"]
            isOneToOne: false
            referencedRelation: "finance_refunds"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pos_till_cash_movements_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_till_count_lines: {
        Row: {
          amount: number
          counted_by: string
          created_at: string
          denomination: number
          id: string
          quantity: number
          session_id: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          counted_by: string
          created_at?: string
          denomination: number
          id?: string
          quantity: number
          session_id: string
        }
        Update: {
          amount?: number
          counted_by?: string
          created_at?: string
          denomination?: number
          id?: string
          quantity?: number
          session_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_till_count_lines_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_till_session_events: {
        Row: {
          actor_user_id: string | null
          created_at: string
          detail: NonNullable<Json>
          event_type: string
          id: string
          session_id: string
        }
        ComputedFields: never
        Insert: {
          actor_user_id?: string | null
          created_at?: string
          detail?: NonNullable<Json>
          event_type: string
          id?: string
          session_id: string
        }
        Update: {
          actor_user_id?: string | null
          created_at?: string
          detail?: NonNullable<Json>
          event_type?: string
          id?: string
          session_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_till_session_events_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
            referencedColumns: ["id"]
          },
        ]
      }
      pos_till_sessions: {
        Row: {
          approved_at: string | null
          approved_by: string | null
          close_notes: string | null
          closed_at: string | null
          counted_cash: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          device_id: string
          expected_cash: number | null
          id: string
          opened_at: string
          opened_by: string
          opening_float: number
          operator_user_id: string
          status: Database["public"]["Enums"]["pos_till_session_status"]
          updated_at: string
          variance: number | null
          variance_reason_code: string | null
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          approved_at?: string | null
          approved_by?: string | null
          close_notes?: string | null
          closed_at?: string | null
          counted_cash?: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          device_id: string
          expected_cash?: number | null
          id?: string
          opened_at?: string
          opened_by: string
          opening_float: number
          operator_user_id: string
          status?: Database["public"]["Enums"]["pos_till_session_status"]
          updated_at?: string
          variance?: number | null
          variance_reason_code?: string | null
          warehouse_id: string
        }
        Update: {
          approved_at?: string | null
          approved_by?: string | null
          close_notes?: string | null
          closed_at?: string | null
          counted_cash?: number | null
          currency?: Database["public"]["Enums"]["currency_code"]
          device_id?: string
          expected_cash?: number | null
          id?: string
          opened_at?: string
          opened_by?: string
          opening_float?: number
          operator_user_id?: string
          status?: Database["public"]["Enums"]["pos_till_session_status"]
          updated_at?: string
          variance?: number | null
          variance_reason_code?: string | null
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pos_till_sessions_warehouse_id_fkey"
            columns: ["warehouse_id"]
            isOneToOne: false
            referencedRelation: "warehouses"
            referencedColumns: ["id"]
          },
        ]
      }
      price_changes: {
        Row: {
          changed_by: string
          created_at: string
          id: string
          new_price: number
          old_price: number
          price_list_item_id: string
          reason: string
          stock_item_id: string
        }
        ComputedFields: never
        Insert: {
          changed_by: string
          created_at?: string
          id?: string
          new_price: number
          old_price: number
          price_list_item_id: string
          reason: string
          stock_item_id: string
        }
        Update: {
          changed_by?: string
          created_at?: string
          id?: string
          new_price?: number
          old_price?: number
          price_list_item_id?: string
          reason?: string
          stock_item_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "price_changes_price_list_item_id_fkey"
            columns: ["price_list_item_id"]
            isOneToOne: false
            referencedRelation: "price_list_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "price_changes_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "price_changes_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
          {
            foreignKeyName: "price_list_items_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
      procurement_fund_releases: {
        Row: {
          amount: number
          amount_minor: number | null
          approved_by: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          notes: string | null
          payment_entry_id: string | null
          purchase_order_id: string
          released_at: string
          requesting_official_id: string | null
          status: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          amount_minor?: number | null
          approved_by?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          payment_entry_id?: string | null
          purchase_order_id: string
          released_at?: string
          requesting_official_id?: string | null
          status?: string
        }
        Update: {
          amount?: number
          amount_minor?: number | null
          approved_by?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          payment_entry_id?: string | null
          purchase_order_id?: string
          released_at?: string
          requesting_official_id?: string | null
          status?: string
        }
        Relationships: [
          {
            foreignKeyName: "procurement_fund_releases_purchase_order_id_fkey"
            columns: ["purchase_order_id"]
            isOneToOne: true
            referencedRelation: "purchase_orders"
            referencedColumns: ["id"]
          },
        ]
      }
      profiles: {
        Row: {
          created_at: string
          full_name: string | null
          id: string
          is_staff: boolean
          must_change_password: boolean
          phone_e164: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          full_name?: string | null
          id: string
          is_staff?: boolean
          must_change_password?: boolean
          phone_e164?: string | null
          updated_at?: string
        }
        Update: {
          created_at?: string
          full_name?: string | null
          id?: string
          is_staff?: boolean
          must_change_password?: boolean
          phone_e164?: string | null
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
          unit_price_minor: number | null
          uom_id: string
        }
        ComputedFields: never
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
          unit_price_minor?: number | null
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
          unit_price_minor?: number | null
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
            foreignKeyName: "purchase_order_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          approved_at: string | null
          approved_by: string | null
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
          funds_released_at: string | null
          id: string
          is_blanket: boolean
          material_request_id: string | null
          notes: string | null
          order_date: string
          progress_step: string | null
          rejected_at: string | null
          rejected_by: string | null
          rejection_reason: string | null
          rfq_id: string | null
          status: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at: string | null
          supplier_id: string
          updated_at: string
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          approved_at?: string | null
          approved_by?: string | null
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
          funds_released_at?: string | null
          id?: string
          is_blanket?: boolean
          material_request_id?: string | null
          notes?: string | null
          order_date?: string
          progress_step?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          rfq_id?: string | null
          status?: Database["public"]["Enums"]["procurement_doc_status"]
          submitted_at?: string | null
          supplier_id: string
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          approved_at?: string | null
          approved_by?: string | null
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
          funds_released_at?: string | null
          id?: string
          is_blanket?: boolean
          material_request_id?: string | null
          notes?: string | null
          order_date?: string
          progress_step?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
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
        ComputedFields: never
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
      return_refund_requests: {
        Row: {
          amount: number
          created_at: string
          credit_note_id: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          exchange_rate_applied: number
          failure_reason: string | null
          id: string
          method: Database["public"]["Enums"]["return_resolution_method"]
          original_payment_entry_id: string | null
          processed_at: string | null
          processed_by: string | null
          return_request_id: string
          settlement_reference: string | null
          source_invoice_id: string
          status: Database["public"]["Enums"]["return_refund_status"]
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          amount: number
          created_at?: string
          credit_note_id: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          exchange_rate_applied?: number
          failure_reason?: string | null
          id?: string
          method?: Database["public"]["Enums"]["return_resolution_method"]
          original_payment_entry_id?: string | null
          processed_at?: string | null
          processed_by?: string | null
          return_request_id: string
          settlement_reference?: string | null
          source_invoice_id: string
          status?: Database["public"]["Enums"]["return_refund_status"]
          updated_at?: string
        }
        Update: {
          amount?: number
          created_at?: string
          credit_note_id?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string
          exchange_rate_applied?: number
          failure_reason?: string | null
          id?: string
          method?: Database["public"]["Enums"]["return_resolution_method"]
          original_payment_entry_id?: string | null
          processed_at?: string | null
          processed_by?: string | null
          return_request_id?: string
          settlement_reference?: string | null
          source_invoice_id?: string
          status?: Database["public"]["Enums"]["return_refund_status"]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "return_refund_requests_credit_note_id_fkey"
            columns: ["credit_note_id"]
            isOneToOne: true
            referencedRelation: "sales_invoices"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "return_refund_requests_customer_id_fkey"
            columns: ["customer_id"]
            isOneToOne: false
            referencedRelation: "customers"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "return_refund_requests_original_payment_entry_id_fkey"
            columns: ["original_payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "return_refund_requests_return_request_id_fkey"
            columns: ["return_request_id"]
            isOneToOne: true
            referencedRelation: "customer_return_requests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "return_refund_requests_source_invoice_id_fkey"
            columns: ["source_invoice_id"]
            isOneToOne: false
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
        ComputedFields: never
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
            foreignKeyName: "rfq_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
        ComputedFields: never
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
        ComputedFields: never
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
          cost_total_basis: number
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
          return_against_line_id: string | null
          stock_batch_id: string | null
          stock_item_id: string
          unit_cost_basis: number | null
          unit_price: number
          uom_id: string
        }
        ComputedFields: never
        Insert: {
          cost_total_basis?: number
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
          return_against_line_id?: string | null
          stock_batch_id?: string | null
          stock_item_id: string
          unit_cost_basis?: number | null
          unit_price: number
          uom_id: string
        }
        Update: {
          cost_total_basis?: number
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
          return_against_line_id?: string | null
          stock_batch_id?: string | null
          stock_item_id?: string
          unit_cost_basis?: number | null
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
            foreignKeyName: "sales_invoice_lines_return_against_line_id_fkey"
            columns: ["return_against_line_id"]
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
            foreignKeyName: "sales_invoice_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          customer_business_name: string | null
          customer_display_name: string | null
          customer_email: string | null
          customer_id: string | null
          customer_phone_e164: string | null
          customer_whatsapp_e164: string | null
          delivery_payment_method:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
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
          till_session_id: string | null
          total: number
          vehicle_chassis_code: string | null
          vehicle_contexts: NonNullable<Json>
          vehicle_engine_code: string | null
          vehicle_generation: string | null
          vehicle_model_name: string | null
          vehicle_model_slug: string | null
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          amount_paid?: number
          cart_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_business_name?: string | null
          customer_display_name?: string | null
          customer_email?: string | null
          customer_id?: string | null
          customer_phone_e164?: string | null
          customer_whatsapp_e164?: string | null
          delivery_payment_method?:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
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
          till_session_id?: string | null
          total?: number
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
          warehouse_id: string
        }
        Update: {
          amount_paid?: number
          cart_id?: string | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_business_name?: string | null
          customer_display_name?: string | null
          customer_email?: string | null
          customer_id?: string | null
          customer_phone_e164?: string | null
          customer_whatsapp_e164?: string | null
          delivery_payment_method?:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
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
          till_session_id?: string | null
          total?: number
          vehicle_chassis_code?: string | null
          vehicle_contexts?: NonNullable<Json>
          vehicle_engine_code?: string | null
          vehicle_generation?: string | null
          vehicle_model_name?: string | null
          vehicle_model_slug?: string | null
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
            foreignKeyName: "sales_invoices_till_session_id_fkey"
            columns: ["till_session_id"]
            isOneToOne: false
            referencedRelation: "pos_till_sessions"
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
        ComputedFields: never
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
          channel: string
          claimed_at: string | null
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
        ComputedFields: never
        Insert: {
          attempt_count?: number
          body: string
          channel?: string
          claimed_at?: string | null
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
          channel?: string
          claimed_at?: string | null
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
      staff_alert_settings: {
        Row: {
          channel: string
          morning_summary: boolean
          phone_e164: string | null
          updated_at: string
          urgent_approvals: boolean
          user_id: string
        }
        ComputedFields: never
        Insert: {
          channel?: string
          morning_summary?: boolean
          phone_e164?: string | null
          updated_at?: string
          urgent_approvals?: boolean
          user_id: string
        }
        Update: {
          channel?: string
          morning_summary?: boolean
          phone_e164?: string | null
          updated_at?: string
          urgent_approvals?: boolean
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "staff_alert_settings_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_approval_badges: {
        Row: {
          employee_id: string | null
          expires_at: string
          id: string
          issued_at: string
          issued_by: string
          label: string | null
          last_used_at: string | null
          revoke_reason: string | null
          revoked_at: string | null
          revoked_by: string | null
          token_sha256: string
          use_count: number
          user_id: string | null
        }
        ComputedFields: never
        Insert: {
          employee_id?: string | null
          expires_at: string
          id?: string
          issued_at?: string
          issued_by: string
          label?: string | null
          last_used_at?: string | null
          revoke_reason?: string | null
          revoked_at?: string | null
          revoked_by?: string | null
          token_sha256: string
          use_count?: number
          user_id?: string | null
        }
        Update: {
          employee_id?: string | null
          expires_at?: string
          id?: string
          issued_at?: string
          issued_by?: string
          label?: string | null
          last_used_at?: string | null
          revoke_reason?: string | null
          revoked_at?: string | null
          revoked_by?: string | null
          token_sha256?: string
          use_count?: number
          user_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "staff_approval_badges_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_approver_admin_events: {
        Row: {
          actor_id: string
          badge_id: string | null
          created_at: string
          employee_id: string | null
          event: string
          id: string
          notes: string | null
          user_id: string | null
        }
        ComputedFields: never
        Insert: {
          actor_id: string
          badge_id?: string | null
          created_at?: string
          employee_id?: string | null
          event: string
          id?: string
          notes?: string | null
          user_id?: string | null
        }
        Update: {
          actor_id?: string
          badge_id?: string | null
          created_at?: string
          employee_id?: string | null
          event?: string
          id?: string
          notes?: string | null
          user_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "staff_approver_admin_events_badge_id_fkey"
            columns: ["badge_id"]
            isOneToOne: false
            referencedRelation: "staff_approval_badges"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "staff_approver_admin_events_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_approver_assignments: {
        Row: {
          employee_id: string
          granted_at: string
          granted_by: string
          notes: string | null
          revoked_at: string | null
          revoked_by: string | null
        }
        ComputedFields: never
        Insert: {
          employee_id: string
          granted_at?: string
          granted_by: string
          notes?: string | null
          revoked_at?: string | null
          revoked_by?: string | null
        }
        Update: {
          employee_id?: string
          granted_at?: string
          granted_by?: string
          notes?: string | null
          revoked_at?: string | null
          revoked_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "staff_approver_assignments_employee_id_fkey"
            columns: ["employee_id"]
            isOneToOne: true
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_badge_approvals: {
        Row: {
          action: string
          approver_employee_id: string | null
          approver_user_id: string | null
          args: NonNullable<Json>
          badge_id: string | null
          created_at: string
          device_id: string | null
          error: string | null
          id: string
          outcome: string
          requested_by: string
          result: Json | null
        }
        ComputedFields: never
        Insert: {
          action: string
          approver_employee_id?: string | null
          approver_user_id?: string | null
          args?: NonNullable<Json>
          badge_id?: string | null
          created_at?: string
          device_id?: string | null
          error?: string | null
          id?: string
          outcome: string
          requested_by: string
          result?: Json | null
        }
        Update: {
          action?: string
          approver_employee_id?: string | null
          approver_user_id?: string | null
          args?: NonNullable<Json>
          badge_id?: string | null
          created_at?: string
          device_id?: string | null
          error?: string | null
          id?: string
          outcome?: string
          requested_by?: string
          result?: Json | null
        }
        Relationships: [
          {
            foreignKeyName: "staff_badge_approvals_approver_employee_id_fkey"
            columns: ["approver_employee_id"]
            isOneToOne: false
            referencedRelation: "employees"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "staff_badge_approvals_badge_id_fkey"
            columns: ["badge_id"]
            isOneToOne: false
            referencedRelation: "staff_approval_badges"
            referencedColumns: ["id"]
          },
        ]
      }
      staff_login_failures: {
        Row: {
          attempted_at: string
          id: string
          identifier_hash: string
          success: boolean
        }
        ComputedFields: never
        Insert: {
          attempted_at?: string
          id?: string
          identifier_hash: string
          success?: boolean
        }
        Update: {
          attempted_at?: string
          id?: string
          identifier_hash?: string
          success?: boolean
        }
        Relationships: []
      }
      staff_login_resolve_attempts: {
        Row: {
          attempted_at: string
          id: string
          identifier_hash: string
          success: boolean
        }
        ComputedFields: never
        Insert: {
          attempted_at?: string
          id?: string
          identifier_hash: string
          success?: boolean
        }
        Update: {
          attempted_at?: string
          id?: string
          identifier_hash?: string
          success?: boolean
        }
        Relationships: []
      }
      staff_ops_notifications: {
        Row: {
          body: string
          created_at: string
          delivery_job_id: string | null
          href: string | null
          id: string
          kind: Database["public"]["Enums"]["staff_ops_notification_kind"]
          read_at: string | null
          recipient_user_id: string
          ref_key: string | null
          sales_invoice_id: string | null
          title: string
        }
        ComputedFields: never
        Insert: {
          body: string
          created_at?: string
          delivery_job_id?: string | null
          href?: string | null
          id?: string
          kind: Database["public"]["Enums"]["staff_ops_notification_kind"]
          read_at?: string | null
          recipient_user_id: string
          ref_key?: string | null
          sales_invoice_id?: string | null
          title: string
        }
        Update: {
          body?: string
          created_at?: string
          delivery_job_id?: string | null
          href?: string | null
          id?: string
          kind?: Database["public"]["Enums"]["staff_ops_notification_kind"]
          read_at?: string | null
          recipient_user_id?: string
          ref_key?: string | null
          sales_invoice_id?: string | null
          title?: string
        }
        Relationships: [
          {
            foreignKeyName: "staff_ops_notifications_delivery_job_id_fkey"
            columns: ["delivery_job_id"]
            isOneToOne: false
            referencedRelation: "delivery_jobs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "staff_ops_notifications_recipient_user_id_fkey"
            columns: ["recipient_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "staff_ops_notifications_sales_invoice_id_fkey"
            columns: ["sales_invoice_id"]
            isOneToOne: false
            referencedRelation: "sales_invoices"
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "stock_batches_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "stock_entry_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
      stock_item_images: {
        Row: {
          created_at: string
          created_by: string | null
          id: string
          is_primary: boolean
          sort_order: number
          stock_item_id: string
          storage_path: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          created_by?: string | null
          id?: string
          is_primary?: boolean
          sort_order?: number
          stock_item_id: string
          storage_path: string
        }
        Update: {
          created_at?: string
          created_by?: string | null
          id?: string
          is_primary?: boolean
          sort_order?: number
          stock_item_id?: string
          storage_path?: string
        }
        Relationships: [
          {
            foreignKeyName: "stock_item_images_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_item_images_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      stock_item_shop_merch: {
        Row: {
          discount_description: string | null
          discount_kind: string
          discount_value: number
          stock_item_id: string
          updated_at: string
          updated_by: string | null
        }
        ComputedFields: never
        Insert: {
          discount_description?: string | null
          discount_kind?: string
          discount_value?: number
          stock_item_id: string
          updated_at?: string
          updated_by?: string | null
        }
        Update: {
          discount_description?: string | null
          discount_kind?: string
          discount_value?: number
          stock_item_id?: string
          updated_at?: string
          updated_by?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "stock_item_shop_merch_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: true
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_item_shop_merch_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: true
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
        ComputedFields: never
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
            foreignKeyName: "stock_levels_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
          variance_qty?: never
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
          variance_qty?: never
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
            foreignKeyName: "stock_reconciliation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
      stock_reorder_points: {
        Row: {
          reorder_point: number
          reorder_qty: number | null
          stock_item_id: string
          updated_at: string
          updated_by: string | null
          warehouse_id: string
        }
        ComputedFields: never
        Insert: {
          reorder_point: number
          reorder_qty?: number | null
          stock_item_id: string
          updated_at?: string
          updated_by?: string | null
          warehouse_id: string
        }
        Update: {
          reorder_point?: number
          reorder_qty?: number | null
          stock_item_id?: string
          updated_at?: string
          updated_by?: string | null
          warehouse_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "stock_reorder_points_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "stock_reorder_points_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "stock_reorder_points_warehouse_id_fkey"
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
        ComputedFields: never
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
            foreignKeyName: "stock_serials_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
          balance_minor: number | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id: string
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          balance?: number
          balance_minor?: number | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          id?: string
          updated_at?: string
        }
        Update: {
          balance?: number
          balance_minor?: number | null
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
          amount_minor: number | null
          balance_after: number
          balance_after_minor: number | null
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
        ComputedFields: never
        Insert: {
          account_id: string
          amount: number
          amount_minor?: number | null
          balance_after: number
          balance_after_minor?: number | null
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
          amount_minor?: number | null
          balance_after?: number
          balance_after_minor?: number | null
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
      supplier_preferred_skus: {
        Row: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          is_active: boolean
          last_quoted_unit_cost: number | null
          notes: string | null
          oem_part_number: string | null
          stock_item_id: string | null
          supplier_id: string
          typical_lead_days: number | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          last_quoted_unit_cost?: number | null
          notes?: string | null
          oem_part_number?: string | null
          stock_item_id?: string | null
          supplier_id: string
          typical_lead_days?: number | null
          updated_at?: string
        }
        Update: {
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          is_active?: boolean
          last_quoted_unit_cost?: number | null
          notes?: string | null
          oem_part_number?: string | null
          stock_item_id?: string | null
          supplier_id?: string
          typical_lead_days?: number | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "supplier_preferred_skus_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "supplier_preferred_skus_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
          {
            foreignKeyName: "supplier_preferred_skus_supplier_id_fkey"
            columns: ["supplier_id"]
            isOneToOne: false
            referencedRelation: "suppliers"
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
        ComputedFields: never
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
            foreignKeyName: "supplier_quotation_lines_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
        ComputedFields: never
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
          address_text: string | null
          code: string
          created_at: string
          default_currency: Database["public"]["Enums"]["currency_code"]
          email: string | null
          id: string
          is_active: boolean
          is_preferred: boolean
          name: string
          payment_terms: string | null
          phone_e164: string | null
          product_categories: string[]
          profile_id: string | null
          relationship_notes: string | null
          tax_id: string | null
          updated_at: string
        }
        ComputedFields: never
        Insert: {
          address_text?: string | null
          code: string
          created_at?: string
          default_currency?: Database["public"]["Enums"]["currency_code"]
          email?: string | null
          id?: string
          is_active?: boolean
          is_preferred?: boolean
          name: string
          payment_terms?: string | null
          phone_e164?: string | null
          product_categories?: string[]
          profile_id?: string | null
          relationship_notes?: string | null
          tax_id?: string | null
          updated_at?: string
        }
        Update: {
          address_text?: string | null
          code?: string
          created_at?: string
          default_currency?: Database["public"]["Enums"]["currency_code"]
          email?: string | null
          id?: string
          is_active?: boolean
          is_preferred?: boolean
          name?: string
          payment_terms?: string | null
          phone_e164?: string | null
          product_categories?: string[]
          profile_id?: string | null
          relationship_notes?: string | null
          tax_id?: string | null
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
        ComputedFields: never
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
        ComputedFields: never
        Insert: {
          chassis_code: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant: string
          production_year?: number | null
          search_vector?: never
          vin_prefix?: string | null
        }
        Update: {
          chassis_code?: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant?: string
          production_year?: number | null
          search_vector?: never
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
        ComputedFields: never
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
          role_code: string | null
        }
        ComputedFields: never
        Insert: {
          code: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_quarantine?: boolean
          name: string
          role_code?: string | null
        }
        Update: {
          code?: string
          created_at?: string
          id?: string
          is_active?: boolean
          is_quarantine?: boolean
          name?: string
          role_code?: string | null
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
        ComputedFields: never
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
            foreignKeyName: "warranty_claims_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
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
      whatsapp_bot_rate_limits: {
        Row: {
          request_count: number
          updated_at: string
          wa_from: string
          window_started_at: string
        }
        ComputedFields: never
        Insert: {
          request_count?: number
          updated_at?: string
          wa_from: string
          window_started_at?: string
        }
        Update: {
          request_count?: number
          updated_at?: string
          wa_from?: string
          window_started_at?: string
        }
        Relationships: []
      }
      whatsapp_flow_orders: {
        Row: {
          channel: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_fee: number
          delivery_method: string
          delivery_notes: string | null
          ecocash_payer_mode: string | null
          ecocash_payer_msisdn: string | null
          id: string
          lines: NonNullable<Json>
          payment_link: string | null
          payment_provider: string
          payment_reference: string | null
          payment_source_reference: string | null
          status: string
          subtotal: number
          total: number
          updated_at: string
          wa_id: string | null
        }
        ComputedFields: never
        Insert: {
          channel?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_fee?: number
          delivery_method?: string
          delivery_notes?: string | null
          ecocash_payer_mode?: string | null
          ecocash_payer_msisdn?: string | null
          id?: string
          lines?: NonNullable<Json>
          payment_link?: string | null
          payment_provider?: string
          payment_reference?: string | null
          payment_source_reference?: string | null
          status?: string
          subtotal?: number
          total?: number
          updated_at?: string
          wa_id?: string | null
        }
        Update: {
          channel?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          delivery_fee?: number
          delivery_method?: string
          delivery_notes?: string | null
          ecocash_payer_mode?: string | null
          ecocash_payer_msisdn?: string | null
          id?: string
          lines?: NonNullable<Json>
          payment_link?: string | null
          payment_provider?: string
          payment_reference?: string | null
          payment_source_reference?: string | null
          status?: string
          subtotal?: number
          total?: number
          updated_at?: string
          wa_id?: string | null
        }
        Relationships: []
      }
    }
    Views: {
      product_review_aggregates: {
        Row: {
          avg_rating: number | null
          review_count: number | null
          stock_item_id: string | null
        }
        ComputedFields: never
        Relationships: [
          {
            foreignKeyName: "customer_product_reviews_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "stock_items"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "customer_product_reviews_stock_item_id_fkey"
            columns: ["stock_item_id"]
            isOneToOne: false
            referencedRelation: "v_master_stock"
            referencedColumns: ["stock_item_id"]
          },
        ]
      }
      v_master_stock: {
        Row: {
          description: string | null
          oem_part_number: string | null
          qty_total: number | null
          qty_wh1: number | null
          qty_wh2: number | null
          stock_item_id: string | null
        }
        ComputedFields: never
        Relationships: []
      }
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
      _assert_ai_analytics_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _assert_ai_crm_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _assert_ai_finance_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _assert_ai_stores_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _assert_bin_in_warehouse: {
        Args: { p_bin_id: string; p_warehouse_id: string }
        Returns: undefined
      }
      _assert_customer_owns_invoice: {
        Args: { p_invoice_id: string }
        Returns: {
          amount_paid: number
          cart_id: string | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_business_name: string | null
          customer_display_name: string | null
          customer_email: string | null
          customer_id: string | null
          customer_phone_e164: string | null
          customer_whatsapp_e164: string | null
          delivery_payment_method:
            | Database["public"]["Enums"]["delivery_payment_method"]
            | null
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
          till_session_id: string | null
          total: number
          vehicle_chassis_code: string | null
          vehicle_contexts: NonNullable<Json>
          vehicle_engine_code: string | null
          vehicle_generation: string | null
          vehicle_model_name: string | null
          vehicle_model_slug: string | null
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
      _assert_delivery_pod_object_for_job: {
        Args: { p_delivery_job_id: string; p_kind: string; p_path: string }
        Returns: string
      }
      _assert_fleet_driver_assignee: {
        Args: { p_user_id: string }
        Returns: undefined
      }
      _assert_journal_balanced: {
        Args: { p_entry_id: string }
        Returns: undefined
      }
      _assert_procurement_period_open: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _assert_supplier_invited_to_rfq: {
        Args: { p_rfq_id: string; p_supplier_id: string }
        Returns: undefined
      }
      _auto_ship_online_dispatch_after_pick: {
        Args: { p_pick_list_id: string }
        Returns: string
      }
      _aws_uri_encode: {
        Args: { p_encode_slash?: boolean; p_value: string }
        Returns: string
      }
      _can_access_petty_cash_receipt_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _can_select_chat_thread: {
        Args: {
          p_thread: Omit<
            Database["public"]["Tables"]["chat_threads"]["Row"],
            Database["public"]["Tables"]["chat_threads"]["ComputedFields"]
          >
        }
        Returns: boolean
      }
      _can_select_delivery_pod_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _can_select_review_photo_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _can_write_delivery_pod_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _can_write_product_image_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _can_write_review_photo_object: {
        Args: { p_name: string }
        Returns: boolean
      }
      _chat_staff_roles: {
        Args: Record<PropertyKey, never>
        Returns: Database["public"]["Enums"]["staff_role"][]
      }
      _consume_auth_rate_limit: {
        Args: {
          p_key_hash: string
          p_max_requests: number
          p_scope: string
          p_window_seconds: number
        }
        Returns: Json
      }
      _consume_fifo_batches: {
        Args: { p_item: string; p_qty_base: number; p_warehouse: string }
        Returns: undefined
      }
      _current_customer_id: {
        Args: Record<PropertyKey, never>
        Returns: string
      }
      _customer_compare_max_items: {
        Args: Record<PropertyKey, never>
        Returns: number
      }
      _customer_owns_delivery_job: {
        Args: { p_job_id: string }
        Returns: boolean
      }
      _delivery_job_customer_contact: {
        Args: { p_delivery_job_id: string }
        Returns: {
          phone_e164: string
          profile_id: string
          sales_invoice_id: string
          whatsapp_e164: string
        }[]
      }
      _delivery_pod_job_id_from_path: {
        Args: { p_name: string }
        Returns: string
      }
      _driver_eligible_for_assign: {
        Args: { p_at?: string; p_user_id: string }
        Returns: boolean
      }
      _driver_open_job_count: { Args: { p_user_id: string }; Returns: number }
      _driver_shift_active: {
        Args: { p_at?: string; p_ends: string; p_starts: string }
        Returns: boolean
      }
      _enqueue_customer_sms: {
        Args: {
          p_actor_user_id?: string
          p_dedupe_key: string
          p_event_code: string
          p_message_body?: string
          p_payload?: Json
          p_phone_e164: string
          p_profile_id: string
        }
        Returns: string
      }
      _enqueue_delivery_customer_sms: {
        Args: {
          p_body: string
          p_dedupe_key: string
          p_delivery_job_id: string
          p_event_code: string
        }
        Returns: boolean
      }
      _ensure_loyalty_account: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
        }
        Returns: string
      }
      _ensure_pick_list_for_invoice: {
        Args: { p_invoice_id: string }
        Returns: string
      }
      _ensure_store_credit_account: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_customer_id: string
        }
        Returns: string
      }
      _expire_stale_pos_scan_sessions: {
        Args: { p_cart_id?: string }
        Returns: undefined
      }
      _finalize_online_dispatch_order: {
        Args: { p_invoice_id: string }
        Returns: string
      }
      _finance_period_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _finance_period_rpc_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _finance_req_approver_on_reporting_line: {
        Args: { p_approver_user_id: string; p_requester_user_id: string }
        Returns: boolean
      }
      _finance_req_can_approve: {
        Args: {
          p_requisition: Omit<
            Database["public"]["Tables"]["finance_requisitions"]["Row"],
            Database["public"]["Tables"]["finance_requisitions"]["ComputedFields"]
          >
        }
        Returns: boolean
      }
      _finance_req_id_from_receipt_path: {
        Args: { p_name: string }
        Returns: string
      }
      _finance_req_required_approvals: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_req_type: Database["public"]["Enums"]["finance_requisition_type"]
        }
        Returns: number
      }
      _finance_req_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _finance_req_rpc_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _fleet_begin_rpc: { Args: Record<PropertyKey, never>; Returns: undefined }
      _fleet_rpc_active: { Args: Record<PropertyKey, never>; Returns: boolean }
      _hash_delivery_pod_otp: { Args: { p_code: string }; Returns: string }
      _hash_delivery_track_token: { Args: { p_token: string }; Returns: string }
      _haversine_eta_seconds: {
        Args: {
          p_from_lat: number
          p_from_lng: number
          p_to_lat: number
          p_to_lng: number
        }
        Returns: number
      }
      _haversine_meters: {
        Args: { p_lat1: number; p_lat2: number; p_lng1: number; p_lng2: number }
        Returns: number
      }
      _invoice_is_storefront_dispatch: {
        Args: { p_invoice_id: string }
        Returns: boolean
      }
      _invoice_line_open_qty_base: {
        Args: { p_invoice_line_id: string }
        Returns: number
      }
      _is_chat_staff: { Args: Record<PropertyKey, never>; Returns: boolean }
      _line_usd_equiv: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_rate: number
        }
        Returns: number
      }
      _log_pos_action: {
        Args: {
          p_action: string
          p_after?: Json
          p_before?: Json
          p_entity_id: string
          p_entity_type: string
          p_notes?: string
        }
        Returns: string
      }
      _logistics_begin_rpc: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _logistics_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _loyalty_money_value: { Args: { p_points: number }; Returns: number }
      _loyalty_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _loyalty_rpc_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _major_to_minor: { Args: { p_amount: number }; Returns: number }
      _next_payroll_schedule_at: {
        Args: {
          p_after: string
          p_frequency: Database["public"]["Enums"]["hr_pay_frequency"]
        }
        Returns: string
      }
      _normalize_delivery_pod_object_path: {
        Args: { p_path: string }
        Returns: string
      }
      _normalize_e164: { Args: { p_phone: string }; Returns: string }
      _normalize_fleet_plate: { Args: { p_plate: string }; Returns: string }
      _normalize_receipt_email: { Args: { p_email: string }; Returns: string }
      _notify_out_for_delivery: {
        Args: { p_delivery_job_id: string; p_track_token: string }
        Returns: undefined
      }
      _notify_sales_prep: { Args: { p_invoice_id: string }; Returns: undefined }
      _notify_wishlist_back_in_stock: {
        Args: { p_stock_item_id: string }
        Returns: undefined
      }
      _online_dispatch_auto_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _online_dispatch_auto_begin: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _online_dispatch_auto_clear: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _payments_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _payments_rpc_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _payroll_begin_rpc: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _payroll_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _po_quoted_total: {
        Args: { p_purchase_order_id: string }
        Returns: number
      }
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
      _procurement_begin_rpc: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _procurement_end_rpc: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _procurement_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _recompute_delivery_eta_haversine: {
        Args: { p_job_id: string; p_lat: number; p_lng: number }
        Returns: undefined
      }
      _recon_assert_single_currency: {
        Args: { p_reconciliation_id: string }
        Returns: undefined
      }
      _recon_begin_rpc: { Args: Record<PropertyKey, never>; Returns: undefined }
      _recon_compute_variance_value: {
        Args: { p_reconciliation_id: string }
        Returns: number
      }
      _recon_dual_auth_threshold: {
        Args: { p_currency: Database["public"]["Enums"]["currency_code"] }
        Returns: number
      }
      _recon_rpc_active: { Args: Record<PropertyKey, never>; Returns: boolean }
      _refresh_payroll_run_totals: {
        Args: { p_run_id: string }
        Returns: undefined
      }
      _require_cart_mutate: { Args: { p_cart_id: string }; Returns: undefined }
      _require_consignment_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_dispatcher_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_fleet_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_hr_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_kit_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_logistics_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_loyalty_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_payments_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_procurement_finance: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_procurement_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_return_post: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_sales_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_warehouse_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _require_warranty_staff: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _resolve_customer_stock_item: {
        Args: { p_oem_part_number: string; p_stock_item_id: string }
        Returns: string
      }
      _reverse_journal_inventory: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
      }
      _review_photo_review_id_from_path: {
        Args: { p_name: string }
        Returns: string
      }
      _sales_checkout_accounting_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _sales_checkout_accounting_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _sales_checkout_accounting_exit: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _staff_ops_notify_begin: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _stock_item_id_from_product_image_path: {
        Args: { p_name: string }
        Returns: string
      }
      _storefront_customer_provision_denied: {
        Args: { p_uid: string }
        Returns: boolean
      }
      _storefront_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      _storefront_rpc_enter: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _storefront_rpc_exit: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _try_auto_assign_delivery_job: {
        Args: { p_delivery_job_id: string }
        Returns: string
      }
      _warranty_begin_rpc: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      _warranty_rpc_active: {
        Args: Record<PropertyKey, never>
        Returns: boolean
      }
      accept_pos_split_affordable_items: {
        Args: {
          p_customer_confirmed: boolean
          p_items: Json
          p_notes?: string
          p_session_id: string
        }
        Returns: Json
      }
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
      add_customer_compare_item: {
        Args: { p_oem_part_number?: string; p_stock_item_id?: string }
        Returns: string
      }
      add_customer_product_review_photo: {
        Args: {
          p_review_id: string
          p_sort_order?: number
          p_storage_path: string
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
      add_pos_split_payment_leg: {
        Args: {
          p_amount: number
          p_external_reference?: string
          p_request_id: string
          p_session_id: string
          p_tender: Database["public"]["Enums"]["payment_tender"]
        }
        Returns: Json
      }
      allocate_payment: {
        Args: { p_allocations: Json; p_payment_entry_id: string }
        Returns: string
      }
      apply_pos_cart_discount: {
        Args: {
          p_cart_id: string
          p_discount_percent: number
          p_notes?: string
        }
        Returns: string
      }
      apply_pos_cart_discount_governed: {
        Args: {
          p_cart_id: string
          p_discount_percent: number
          p_notes?: string
          p_reason_code: string
        }
        Returns: string
      }
      apply_pos_line_price_override: {
        Args: { p_line_id: string; p_notes?: string; p_unit_price: number }
        Returns: string
      }
      apply_pos_line_price_override_governed: {
        Args: {
          p_line_id: string
          p_notes?: string
          p_reason_code: string
          p_unit_price: number
        }
        Returns: string
      }
      approve_driver_cash_variance: {
        Args: { p_handin_id: string; p_notes?: string; p_reason_code: string }
        Returns: Json
      }
      approve_finance_requisition: {
        Args: { p_note?: string; p_requisition_id: string }
        Returns: string
      }
      approve_material_request: {
        Args: { p_material_request_id: string }
        Returns: string
      }
      approve_pos_fulfillment_request: {
        Args: { p_notes?: string; p_request_id: string }
        Returns: string
      }
      approve_pos_split_refund: {
        Args: {
          p_customer_fee?: number
          p_estimated_provider_fee?: number
          p_estimated_transfer_fee?: number
          p_expected_days?: number
          p_fee_policy: string
          p_notes?: string
          p_refund_id: string
        }
        Returns: Json
      }
      approve_pos_till_variance: {
        Args: { p_notes?: string; p_reason_code: string; p_session_id: string }
        Returns: string
      }
      approve_pos_warranty_claim: {
        Args: {
          p_claim_id: string
          p_lines?: Json
          p_replacement_lines?: Json
          p_resolution: Database["public"]["Enums"]["warranty_claim_resolution"]
        }
        Returns: string
      }
      approve_purchase_order: {
        Args: { p_purchase_order_id: string }
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
      archive_hr_role: { Args: { p_role_id: string }; Returns: string }
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
      attach_finance_requisition_receipt: {
        Args: {
          p_content_type?: string
          p_requisition_id: string
          p_storage_path: string
        }
        Returns: string
      }
      attach_goods_receipt_invoice: {
        Args: { p_goods_receipt_id: string; p_storage_path: string }
        Returns: string
      }
      attach_pos_cart_till_session: {
        Args: { p_cart_id: string; p_session_id: string }
        Returns: string
      }
      attach_pos_fulfillment_to_cart: {
        Args: { p_cart_id: string; p_request_id: string }
        Returns: string
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
      begin_delivery_card_terminal_payment: {
        Args: {
          p_amount: number
          p_delivery_job_id: string
          p_device_id: string
          p_request_id: string
          p_terminal_id: string
        }
        Returns: Json
      }
      begin_pos_card_terminal_purchase: {
        Args: {
          p_order_id: string
          p_request_id: string
          p_terminal_id: string
        }
        Returns: Json
      }
      begin_pos_card_terminal_refund: {
        Args: {
          p_invoice_id: string
          p_request_id: string
          p_terminal_id: string
        }
        Returns: Json
      }
      begin_pos_card_terminal_reversal: {
        Args: { p_purchase_attempt_id: string; p_request_id: string }
        Returns: Json
      }
      begin_pos_card_terminal_split_refund: {
        Args: {
          p_refund_id: string
          p_request_id: string
          p_terminal_id: string
        }
        Returns: Json
      }
      begin_pos_split_card_terminal_leg: {
        Args: { p_leg_id: string; p_request_id: string; p_terminal_id: string }
        Returns: Json
      }
      build_qr_payload: {
        Args: {
          p_batch_code: string
          p_oem: string
          p_valuation: Database["public"]["Enums"]["valuation_method"]
        }
        Returns: string
      }
      cancel_auth_challenge: {
        Args: { p_challenge_id: string; p_kind: string }
        Returns: boolean
      }
      cancel_consignment_entry: {
        Args: { p_entry_id: string }
        Returns: string
      }
      cancel_delivery_note: {
        Args: { p_delivery_note_id: string }
        Returns: string
      }
      cancel_finance_requisition: {
        Args: { p_requisition_id: string }
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
      cancel_pos_commerce_checkout: {
        Args: { p_order_id: string; p_reason: string }
        Returns: string
      }
      cancel_pos_commerce_checkout_legacy: {
        Args: { p_order_id: string; p_reason: string }
        Returns: string
      }
      cancel_pos_fulfillment_request: {
        Args: { p_notes?: string; p_request_id: string }
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
      canonical_staff_drift: {
        Args: Record<PropertyKey, never>
        Returns: {
          auth_present: boolean
          email: string
          email_confirmed: boolean
          email_matches: boolean
          in_sync: boolean
          is_staff: boolean
          no_extra_roles: boolean
          profile_present: boolean
          provisioned_via_hr: boolean
          role: Database["public"]["Enums"]["staff_role"]
          role_matches: boolean
          user_id: string
        }[]
      }
      catalog_commerce_stock_for_oems: {
        Args: { p_oems: string[] }
        Returns: {
          currency: string
          description: string
          normalized_oem: string
          oem_part_number: string
          reorder_point: number
          saleable_qty: number
          stock_item_id: string
          unit_price: number
        }[]
      }
      catalog_offline_current_release_public: {
        Args: Record<PropertyKey, never>
        Returns: {
          diagram_count: number
          encrypted_sha256: string
          encrypted_size_bytes: number
          encryption_format: string
          fitment_count: number
          image_count: number
          published_at: string
          r2_object_key: string
          release_id: string
          schema_version: number
          section_count: number
          source_release: string
          sqlite_page_size: number
          vehicle_count: number
          version: string
        }[]
      }
      catalog_offline_current_release_secret: {
        Args: Record<PropertyKey, never>
        Returns: {
          content_key_b64: string
          diagram_count: number
          encrypted_sha256: string
          encrypted_size_bytes: number
          encryption_format: string
          fitment_count: number
          image_count: number
          r2_object_key: string
          release_id: string
          schema_version: number
          section_count: number
          source_release: string
          sqlite_page_size: number
          vehicle_count: number
          version: string
        }[]
      }
      catalog_offline_device_is_revoked: {
        Args: {
          p_app_flavor: string
          p_device_key_sha256: string
          p_release_id: string
          p_user_id: string
        }
        Returns: boolean
      }
      catalog_offline_record_device_grant: {
        Args: {
          p_app_flavor: string
          p_device_key_sha256: string
          p_release_id: string
          p_user_id: string
        }
        Returns: undefined
      }
      catalog_r2_presign_batch: {
        Args: {
          p_build_token: string
          p_expires?: number
          p_limit?: number
          p_object_kind: string
          p_offset?: number
        }
        Returns: {
          bytes: number
          content_encoding: string
          object_key: string
          row_count: number
          scope_key: string
          sha256: string
          url: string
        }[]
      }
      catalog_r2_presign_get: {
        Args: { p_expires?: number; p_object_key: string }
        Returns: string
      }
      catalog_r2_presign_list: {
        Args: { p_expires?: number; p_prefix?: string }
        Returns: string
      }
      catalog_r2_presign_list_after: {
        Args: {
          p_expires?: number
          p_max_keys?: number
          p_prefix: string
          p_start_after?: string
        }
        Returns: string
      }
      catalog_r2_presign_list_page: {
        Args: {
          p_continuation_token?: string
          p_expires?: number
          p_max_keys?: number
          p_prefix: string
        }
        Returns: string
      }
      catalog_r2_presign_prefixes: {
        Args: { p_expires?: number; p_prefix?: string }
        Returns: string
      }
      catalog_r2_runtime_config: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      catalog_r2_vehicle_master_list: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      catalog_seed_vehicle_master: {
        Args: { p_build_token: string }
        Returns: {
          chassis_code: string
          created_at: string
          engine_code: string
          maker_slug: string
          model: string
          r2_scope_key: string
          sales_region: string | null
          source_release_version: string
          updated_at: string
          year_end: number | null
          year_start: number | null
        }[]
        SetofOptions: {
          from: "*"
          to: "catalog_r2_vehicle_master"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      catalog_v2_ingest_r2_serving_objects_batch: {
        Args: { p_rows: Json }
        Returns: number
      }
      chat_unread_count: { Args: { p_thread_id?: string }; Returns: number }
      check_whatsapp_bot_rate_limit: {
        Args: {
          p_max_requests?: number
          p_wa_from: string
          p_window_seconds?: number
        }
        Returns: Json
      }
      checkout_customer_cart:
        | { Args: { p_cart_id: string }; Returns: string }
        | {
            Args: { p_cart_id: string; p_checkout_request_id: string }
            Returns: string
          }
      checkout_customer_cart_v2: {
        Args: {
          p_cart_id: string
          p_delivery_payment_method?: Database["public"]["Enums"]["delivery_payment_method"]
        }
        Returns: string
      }
      checkout_pos_cart: {
        Args: {
          p_cart_id: string
          p_receipt_email?: string
          p_receipt_phone_e164?: string
          p_receipt_whatsapp_e164?: string
        }
        Returns: string
      }
      checkout_pos_cart_on_account: {
        Args: {
          p_cart_id: string
          p_receipt_email?: string
          p_receipt_phone_e164?: string
          p_receipt_whatsapp_e164?: string
        }
        Returns: string
      }
      checkout_pos_cart_with_tenders: {
        Args: {
          p_cart_id: string
          p_receipt_email?: string
          p_receipt_phone_e164?: string
          p_receipt_whatsapp_e164?: string
          p_tenders: Json
        }
        Returns: string
      }
      claim_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      claim_hr_auth_provision: {
        Args: {
          p_claim_token: string
          p_employee_id: string
          p_ttl_seconds?: number
        }
        Returns: Json
      }
      claim_pos_scan_session: {
        Args: { p_pairing_code: string }
        Returns: string
      }
      claim_receipt_outbox_batch: {
        Args: { p_limit?: number }
        Returns: {
          attempt_count: number
          channel: Database["public"]["Enums"]["receipt_channel"]
          claimed_at: string | null
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
        }[]
        SetofOptions: {
          from: "*"
          to: "customer_receipt_outbox"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      claim_sms_outbox_batch: {
        Args: { p_limit?: number }
        Returns: {
          attempt_count: number
          body: string
          channel: string
          claimed_at: string | null
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
        }[]
        SetofOptions: {
          from: "*"
          to: "sms_outbox"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      clear_bank_matches: { Args: { p_match_ids: string[] }; Returns: number }
      clear_must_change_password: {
        Args: Record<PropertyKey, never>
        Returns: undefined
      }
      clock_attendance: {
        Args: {
          p_employee_id: string
          p_event_type: Database["public"]["Enums"]["attendance_event_type"]
          p_notes?: string
          p_occurred_at?: string
        }
        Returns: string
      }
      close_account_period: {
        Args: {
          p_notes?: string
          p_period_id: string
          p_physical_count?: number
        }
        Returns: string
      }
      close_chat_thread: { Args: { p_thread_id: string }; Returns: undefined }
      close_pos_till_session: {
        Args: {
          p_counted_cash: number
          p_notes?: string
          p_session_id: string
          p_variance_reason_code?: string
        }
        Returns: Json
      }
      close_warranty_claim: { Args: { p_claim_id: string }; Returns: string }
      collect_delivery_cash: {
        Args: {
          p_amount: number
          p_delivery_job_id: string
          p_notes?: string
          p_request_id: string
        }
        Returns: Json
      }
      collect_pos_commerce_order: {
        Args: { p_notes?: string; p_order_id: string }
        Returns: string
      }
      collect_pos_fulfillment_request: {
        Args: { p_notes?: string; p_request_id: string }
        Returns: string
      }
      complete_hr_credential_outbox: {
        Args: {
          p_error?: string
          p_id: string
          p_provider_message_id?: string
          p_success: boolean
        }
        Returns: undefined
      }
      complete_hr_onboarding: { Args: { p_draft_id: string }; Returns: Json }
      complete_pos_split_refund: {
        Args: {
          p_actual_customer_fee?: number
          p_actual_provider_fee?: number
          p_actual_transfer_fee?: number
          p_notes?: string
          p_provider_ref: string
          p_refund_id: string
        }
        Returns: Json
      }
      complete_receipt_outbox: {
        Args: { p_error?: string; p_id: string; p_success: boolean }
        Returns: undefined
      }
      complete_return_refund_manual: {
        Args: {
          p_return_request_id: string
          p_settlement_reference: string
          p_tender: Database["public"]["Enums"]["payment_tender"]
        }
        Returns: string
      }
      complete_sms_outbox: {
        Args: {
          p_error?: string
          p_id: string
          p_provider_message_id?: string
          p_success: boolean
        }
        Returns: undefined
      }
      compute_payroll_run: {
        Args: { p_employee_ids?: string[]; p_payroll_run_id: string }
        Returns: string
      }
      compute_petty_cash_replenish_amount: {
        Args: {
          p_as_of?: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
        }
        Returns: number
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
      convert_pos_quotation_to_cart: {
        Args: { p_quotation_id: string }
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
      create_customer_ecocash_intent: {
        Args: {
          p_amount?: number
          p_channel?: string
          p_external_ref?: string
          p_metadata?: Json
          p_payer_mode?: string
          p_payer_msisdn: string
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
      create_ecocash_intent: {
        Args: {
          p_amount: number
          p_channel?: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id?: string
          p_exchange_rate?: number
          p_external_ref: string
          p_metadata?: Json
          p_payer_mode?: string
          p_payer_msisdn: string
          p_sales_invoice_id?: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
          p_whatsapp_flow_order_id?: string
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
      create_finance_requisition: {
        Args: {
          p_amount: number
          p_cash_account_code?: string
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_expense_account_code?: string
          p_memo?: string
          p_payee?: string
          p_req_type: Database["public"]["Enums"]["finance_requisition_type"]
        }
        Returns: string
      }
      create_goods_receipt: {
        Args: { p_lines: Json; p_notes?: string; p_purchase_order_id: string }
        Returns: string
      }
      create_hr_grade: {
        Args: { p_code: string; p_sort_order?: number; p_title: string }
        Returns: string
      }
      create_hr_role: {
        Args: {
          p_clause_template_ids?: string[]
          p_comms_preferences?: Json
          p_department?: string
          p_duties_md?: string
          p_grade_id: string
          p_module_access?: Json
          p_parent_role_id?: string
          p_pay_frequency?: Database["public"]["Enums"]["hr_pay_frequency"]
          p_remuneration_notes?: string
          p_title: string
        }
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
      create_kit_with_components: {
        Args: {
          p_chassis_code?: string
          p_components: Json
          p_oem: string
          p_sell_mode?: Database["public"]["Enums"]["kit_sell_mode"]
          p_title: string
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
      create_payment_resolution_letter: {
        Args: {
          p_issue_notes?: string
          p_source_id: string
          p_source_kind: Database["public"]["Enums"]["payment_resolution_source_kind"]
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
      create_petty_cash_expense_request: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_description: string
          p_entry_date?: string
          p_exchange_rate?: number
          p_expense_account_code?: string
        }
        Returns: string
      }
      create_petty_cash_float_request: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_description: string
          p_entry_date?: string
          p_exchange_rate?: number
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
      create_pos_contipay_intent: {
        Args: {
          p_external_ref: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["contipay_method"]
          p_order_id: string
        }
        Returns: string
      }
      create_pos_customer: {
        Args: {
          p_business_name?: string
          p_customer_kind: string
          p_display_name: string
          p_email?: string
          p_phone_e164?: string
          p_whatsapp_e164?: string
        }
        Returns: string
      }
      create_pos_ecocash_intent: {
        Args: {
          p_external_ref: string
          p_metadata?: Json
          p_order_id: string
          p_payer_msisdn: string
        }
        Returns: string
      }
      create_pos_fulfillment_request: {
        Args: {
          p_cart_id?: string
          p_customer_id?: string
          p_destination_warehouse_id?: string
          p_hold_minutes?: number
          p_invoice_id?: string
          p_kind: Database["public"]["Enums"]["pos_fulfillment_kind"]
          p_notes?: string
          p_qty: number
          p_source_warehouse_id?: string
          p_stock_item_id: string
          p_uom_id: string
        }
        Returns: string
      }
      create_pos_paynow_intent: {
        Args: {
          p_external_ref: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["paynow_method"]
          p_order_id: string
        }
        Returns: string
      }
      create_pos_quotation_from_cart: {
        Args: { p_cart_id: string; p_notes?: string; p_valid_until?: string }
        Returns: string
      }
      create_pos_return_case: {
        Args: {
          p_invoice_id: string
          p_lines: Json
          p_notes?: string
          p_reason_code: string
          p_replacement_lines?: Json
          p_resolution: Database["public"]["Enums"]["pos_return_resolution"]
          p_till_session_id?: string
        }
        Returns: string
      }
      create_pos_scan_session: {
        Args: { p_cart_id: string; p_ttl?: string }
        Returns: {
          expires_at: string
          pairing_code: string
          session_id: string
        }[]
      }
      create_pos_split_contipay_intent: {
        Args: {
          p_external_ref: string
          p_leg_id: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["contipay_method"]
        }
        Returns: string
      }
      create_pos_split_ecocash_intent: {
        Args: {
          p_external_ref: string
          p_leg_id: string
          p_metadata?: Json
          p_payer_msisdn: string
        }
        Returns: string
      }
      create_pos_split_paynow_intent: {
        Args: {
          p_external_ref: string
          p_leg_id: string
          p_metadata?: Json
          p_method: Database["public"]["Enums"]["paynow_method"]
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
      current_employee_id: { Args: Record<PropertyKey, never>; Returns: string }
      current_supplier_id: { Args: Record<PropertyKey, never>; Returns: string }
      deactivate_preferred_supplier: {
        Args: { p_supplier_id: string }
        Returns: string
      }
      deactivate_warehouse_bin: { Args: { p_bin_id: string }; Returns: string }
      decide_delivery_balance_on_account: {
        Args: { p_approval_id: string; p_approve: boolean; p_note?: string }
        Returns: Json
      }
      decide_leave_request: {
        Args: { p_approve: boolean; p_note?: string; p_request_id: string }
        Returns: string
      }
      delete_customer_address: { Args: { p_id: string }; Returns: undefined }
      delete_customer_garage_vehicle: {
        Args: { p_id: string }
        Returns: undefined
      }
      delete_pos_popular_pin: {
        Args: { p_item_key: string; p_item_type: string }
        Returns: boolean
      }
      delete_stock_item_image: { Args: { p_image_id: string }; Returns: string }
      delivery_geofence_suggestion: {
        Args: {
          p_arrive_radius_m?: number
          p_complete_radius_m?: number
          p_delivery_job_id: string
          p_lat: number
          p_lng: number
        }
        Returns: {
          distance_m: number
          suggest_arrive: boolean
          suggest_complete: boolean
        }[]
      }
      disburse_finance_requisition: {
        Args: { p_entry_date?: string; p_requisition_id: string }
        Returns: string
      }
      dismiss_approval_alert: { Args: { p_id: string }; Returns: undefined }
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
      enforce_auth_edge_rate_limit: {
        Args: {
          p_device_hash?: string
          p_identifier_hash: string
          p_ip_hash?: string
          p_operation: string
        }
        Returns: Json
      }
      enqueue_customer_receipts: {
        Args: { p_invoice_id: string }
        Returns: number
      }
      enqueue_hr_credential_outbox: {
        Args: {
          p_body: string
          p_channel: Database["public"]["Enums"]["hr_credential_channel"]
          p_employee_id: string
          p_recipient: string
          p_user_id: string
        }
        Returns: string
      }
      ensure_customer_for_user: { Args: { p_uid: string }; Returns: string }
      ensure_own_customer: { Args: Record<PropertyKey, never>; Returns: string }
      ensure_pos_walkin_customer: {
        Args: Record<PropertyKey, never>
        Returns: string
      }
      expire_commerce_checkouts: {
        Args: Record<PropertyKey, never>
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
      fail_delivery_job: {
        Args: {
          p_create_reattempt?: boolean
          p_delivery_job_id: string
          p_notes?: string
          p_reason: Database["public"]["Enums"]["delivery_failure_reason"]
        }
        Returns: string
      }
      fail_pos_split_refund: {
        Args: { p_reason: string; p_refund_id: string }
        Returns: Json
      }
      finalize_ai_promo_run: {
        Args: {
          p_candidates: number
          p_error?: string
          p_gemini_used: boolean
          p_queued: number
          p_run_id: string
          p_status: Database["public"]["Enums"]["ai_promo_run_status"]
        }
        Returns: undefined
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
      finalize_delivery_card_terminal_payment: {
        Args: { p_attempt_id: string }
        Returns: Json
      }
      finalize_pos_account_credit_order: {
        Args: { p_order_id: string }
        Returns: string
      }
      finalize_pos_card_terminal_purchase: {
        Args: { p_attempt_id: string }
        Returns: Json
      }
      finalize_pos_card_terminal_purchase_full_legacy: {
        Args: { p_attempt_id: string }
        Returns: Json
      }
      finalize_pos_card_terminal_refund: {
        Args: { p_attempt_id: string; p_notes?: string }
        Returns: Json
      }
      finalize_pos_card_terminal_split_refund: {
        Args: { p_attempt_id: string; p_notes?: string }
        Returns: Json
      }
      find_pos_split_payment: { Args: { p_order_id: string }; Returns: Json }
      find_pos_warranty_serial: {
        Args: { p_serial_number: string }
        Returns: {
          id: string
          oem_part_number: string
          serial_number: string
          status: string
          stock_item_id: string
          warehouse_id: string
        }[]
      }
      fund_payroll_run: { Args: { p_payroll_run_id: string }; Returns: Json }
      generate_delivery_pod_otp: {
        Args: { p_delivery_job_id: string; p_ttl?: string }
        Returns: string
      }
      generate_forecast_suggestions: {
        Args: {
          p_default_reorder_qty?: number
          p_horizon_days?: number
          p_warehouse_id: string
        }
        Returns: number
      }
      get_business_document_profile: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      get_catalog_diagram: {
        Args: {
          p_maker_slug: string
          p_model_slug: string
          p_section_slug: string
          p_variant_slug: string
        }
        Returns: Json
      }
      get_catalog_diagram_by_slug: {
        Args: {
          p_diagram_slug: string
          p_maker_slug: string
          p_model_slug: string
          p_section_slug: string
          p_variant_slug: string
        }
        Returns: Json
      }
      get_customer_order: { Args: { p_invoice_id: string }; Returns: Json }
      get_customer_suspension: {
        Args: { p_customer_id: string }
        Returns: Json
      }
      get_daily_dashboard: {
        Args: { p_date?: string; p_warehouse_id?: string }
        Returns: Json
      }
      get_delivery_balance_approval: {
        Args: { p_delivery_job_id: string }
        Returns: Json
      }
      get_delivery_card_terminal_recovery: {
        Args: { p_delivery_job_id: string }
        Returns: Json
      }
      get_delivery_job_lines: {
        Args: { p_delivery_job_id: string }
        Returns: {
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string
          description: string
          is_core_charge: boolean
          line_id: string
          line_total: number
          line_total_minor: number
          oem_part_number: string
          qty: number
          unit_price: number
          unit_price_minor: number
        }[]
      }
      get_delivery_job_payment_context: {
        Args: { p_delivery_job_id: string }
        Returns: Json
      }
      get_delivery_job_settlement: {
        Args: { p_delivery_job_id: string }
        Returns: {
          amount_due: number
          amount_due_minor: number
          amount_paid: number
          amount_paid_minor: number
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string
          invoice_total: number
          invoice_total_minor: number
        }[]
      }
      get_delivery_track_point: {
        Args: { p_delivery_job_id?: string; p_token?: string }
        Returns: {
          delivery_job_id: string
          eta_at: string
          eta_seconds: number
          lat: number
          lng: number
          recorded_at: string
          status: Database["public"]["Enums"]["delivery_job_status"]
        }[]
      }
      get_exception_report: {
        Args: { p_from?: string; p_to?: string; p_warehouse_id?: string }
        Returns: Json
      }
      get_inventory_available_quantity: {
        Args: { p_stock_item_id: string; p_warehouse_id: string }
        Returns: number
      }
      get_loyalty_balance: {
        Args: { p_customer_id: string }
        Returns: {
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          estimated_liability: number
          estimated_liability_minor: number
          liability_per_point: number
          points_balance: number
        }[]
      }
      get_my_account_suspension: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      get_my_alert_settings: { Args: Record<PropertyKey, never>; Returns: Json }
      get_my_delivery_codes: { Args: Record<PropertyKey, never>; Returns: Json }
      get_my_driver_cash: { Args: Record<PropertyKey, never>; Returns: Json }
      get_my_manager_signature: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      get_my_open_pos_till_session: {
        Args: { p_device_id?: string }
        Returns: Json
      }
      get_my_pos_approver_status: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      get_payment_resolution_letter_render_data: {
        Args: { p_letter_id: string }
        Returns: Json
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
      get_pos_approval_policy: { Args: { p_action: string }; Returns: Json }
      get_pos_card_terminal_attempt: {
        Args: { p_attempt_id: string }
        Returns: Json
      }
      get_pos_invoice_detail: { Args: { p_invoice_id: string }; Returns: Json }
      get_pos_payment_status: { Args: { p_order_id: string }; Returns: Json }
      get_pos_split_payment: { Args: { p_session_id: string }; Returns: Json }
      get_product_review_stats: {
        Args: { p_oem_part_number?: string; p_stock_item_id?: string }
        Returns: {
          avg_rating: number
          review_count: number
          stock_item_id: string
        }[]
      }
      get_restock_suggestions: {
        Args: {
          p_cover_days?: number
          p_lead_days?: number
          p_safety_days?: number
          p_sales_days?: number
          p_warehouse_id?: string
        }
        Returns: Json
      }
      get_zig_exchange_rate: { Args: { p_as_of?: string }; Returns: number }
      gl_account_for_payment_tender: {
        Args: { p_tender: Database["public"]["Enums"]["payment_tender"] }
        Returns: string
      }
      handover_pos_till_session: {
        Args: {
          p_new_operator_user_id: string
          p_notes?: string
          p_session_id: string
        }
        Returns: string
      }
      has_staff_role: {
        Args: { roles: Database["public"]["Enums"]["staff_role"][] }
        Returns: boolean
      }
      hide_pos_bestseller: {
        Args: { p_stock_item_id: string }
        Returns: boolean
      }
      hook_before_user_created: { Args: { event: Json }; Returns: Json }
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
      insert_ai_promo_delivery: {
        Args: {
          p_body_preview: string
          p_channel: Database["public"]["Enums"]["ai_delivery_channel"]
          p_customer_id: string
          p_error?: string
          p_gemini_used?: boolean
          p_oem_skus: string[]
          p_provider_ref?: string
          p_recipient: string
          p_run_id: string
          p_status: Database["public"]["Enums"]["ai_delivery_status"]
          p_vehicle_label: string
        }
        Returns: string
      }
      insert_ai_promo_run: { Args: Record<PropertyKey, never>; Returns: string }
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
      insert_inventory_ai_directive: {
        Args: {
          p_directives: Json
          p_error?: string
          p_gemini_used: boolean
          p_kpi_json: Json
          p_narrative: string
          p_period_end: string
          p_period_start: string
          p_warehouse_id: string
        }
        Returns: string
      }
      is_period_locked: { Args: { p_date: string }; Returns: boolean }
      is_pos_approver: { Args: Record<PropertyKey, never>; Returns: boolean }
      is_staff: { Args: Record<PropertyKey, never>; Returns: boolean }
      issue_approval_badge: {
        Args: {
          p_employee_id?: string
          p_label?: string
          p_user_id?: string
          p_valid_days?: number
        }
        Returns: Json
      }
      issue_auth_challenge: {
        Args: {
          p_channel: string
          p_code_hash: string
          p_device_hash?: string
          p_expires_at: string
          p_identifier: string
          p_ip_hash?: string
          p_kind: string
        }
        Returns: string
      }
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
      kpi_ar_aging_snapshot: { Args: Record<PropertyKey, never>; Returns: Json }
      kpi_credit_holds_summary: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
      kpi_finance_performance_v1: {
        Args: { p_from: string; p_to: string; p_top_expenses?: number }
        Returns: Json
      }
      kpi_inventory_summary: { Args: Record<PropertyKey, never>; Returns: Json }
      kpi_open_deliveries_summary: {
        Args: Record<PropertyKey, never>
        Returns: Json
      }
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
      kpi_stores_forecast_v1: {
        Args: {
          p_from?: string
          p_run_abc?: boolean
          p_to?: string
          p_warehouse_id: string
        }
        Returns: Json
      }
      kpi_top_skus: {
        Args: { p_from: string; p_limit?: number; p_to: string }
        Returns: Json
      }
      lift_customer_suspension: {
        Args: { p_reason: string; p_suspension_id: string }
        Returns: Json
      }
      link_employee_auth_user: {
        Args: {
          p_employee_id: string
          p_full_name?: string
          p_phone_e164?: string
          p_user_id: string
        }
        Returns: string
      }
      link_supplier_profile: {
        Args: { p_profile_id: string; p_supplier_id: string }
        Returns: undefined
      }
      list_approval_badges: {
        Args: Record<PropertyKey, never>
        Returns: {
          badge_id: string
          employee_id: string
          expires_at: string
          full_name: string
          issued_at: string
          label: string
          last_used_at: string
          revoke_reason: string
          revoked_at: string
          status: string
          use_count: number
          user_id: string
        }[]
      }
      list_approval_trail: {
        Args: { p_limit?: number }
        Returns: {
          action: string
          at: string
          detail: string
          device_id: string
          manager_name: string
          method: string
          outcome: string
          reason_code: string
          requested_by_name: string
        }[]
      }
      list_approver_candidates: {
        Args: Record<PropertyKey, never>
        Returns: {
          active_badges: number
          assigned: boolean
          department: string
          employee_code: string
          employee_id: string
          full_name: string
          grade: string
          has_login: boolean
          holder_type: string
          is_approver: boolean
          role_title: string
          source: string
          user_id: string
        }[]
      }
      list_catalog_diagrams: {
        Args: {
          p_maker_slug: string
          p_model_slug: string
          p_section_slug: string
          p_variant_slug: string
        }
        Returns: Json
      }
      list_catalog_makers: { Args: Record<PropertyKey, never>; Returns: Json }
      list_catalog_models: { Args: { p_maker_slug: string }; Returns: Json }
      list_catalog_sections: {
        Args: {
          p_maker_slug: string
          p_model_slug: string
          p_variant_slug: string
        }
        Returns: Json
      }
      list_catalog_variants: {
        Args: { p_maker_slug: string; p_model_slug: string }
        Returns: Json
      }
      list_crm_promo_candidates: {
        Args: { p_force?: boolean; p_limit?: number }
        Returns: Json
      }
      list_customer_compare_items: {
        Args: Record<PropertyKey, never>
        Returns: {
          created_at: string
          description: string
          id: string
          oem_part_number: string
          stock_item_id: string
        }[]
      }
      list_customer_suspensions: {
        Args: { p_limit?: number; p_status?: string }
        Returns: Json
      }
      list_customer_vehicle_master: {
        Args: { p_limit?: number; p_maker?: string; p_offset?: number }
        Returns: {
          chassis_code: string
          display_name: string
          engine_code: string
          id: string
          make: string
          model_family: string
          model_variant: string
          production_year: number
          sales_region: string
          vin_prefix: string
          year_end: number
          year_start: number
        }[]
      }
      list_delivery_balance_approvals: {
        Args: { p_limit?: number; p_status?: string }
        Returns: Json
      }
      list_delivery_card_terminals: {
        Args: { p_device_id: string; p_warehouse_id: string }
        Returns: Json
      }
      list_driver_cash: {
        Args: { p_limit?: number; p_status?: string }
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
      list_fleet_vehicles: {
        Args: { p_status?: Database["public"]["Enums"]["fleet_vehicle_status"] }
        Returns: {
          assigned_driver_user_id: string | null
          created_at: string
          created_by: string | null
          id: string
          label: string | null
          notes: string | null
          plate: string
          status: Database["public"]["Enums"]["fleet_vehicle_status"]
          updated_at: string
        }[]
        SetofOptions: {
          from: "*"
          to: "fleet_vehicles"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      list_master_stock: {
        Args: { p_limit?: number; p_query?: string }
        Returns: {
          description: string
          oem_part_number: string
          qty_total: number
          qty_wh1: number
          qty_wh2: number
          stock_item_id: string
        }[]
      }
      list_my_approvals: { Args: Record<PropertyKey, never>; Returns: Json }
      list_online_prep_queue: {
        Args: { p_limit?: number }
        Returns: {
          assignee_user_id: string
          currency: Database["public"]["Enums"]["currency_code"]
          delivery_job_id: string
          delivery_job_status: Database["public"]["Enums"]["delivery_job_status"]
          document_number: string
          invoice_id: string
          pick_list_id: string
          pick_status: Database["public"]["Enums"]["pick_list_status"]
          posted_at: string
          total: number
        }[]
      }
      list_payment_resolution_letters: {
        Args: {
          p_limit?: number
          p_query?: string
          p_source_id?: string
          p_source_kind?: Database["public"]["Enums"]["payment_resolution_source_kind"]
        }
        Returns: {
          amount: number
          currency: string
          customer_name: string
          document_number: string
          id: string
          invoice_document_number: string
          issued_at: string
          manager_name: string
          manager_title: string
          observed_status: string
          provider: string
          source_id: string
          source_kind: string
        }[]
      }
      list_pos_approval_policies: {
        Args: Record<PropertyKey, never>
        Returns: {
          action: string
          always_require_manager: boolean
          reason_required: boolean
          threshold_value: number
          updated_at: string
        }[]
      }
      list_pos_approval_reasons: {
        Args: { p_action: string }
        Returns: {
          code: string
          label: string
          requires_notes: boolean
          sort_order: number
        }[]
      }
      list_pos_card_terminal_recovery: {
        Args: { p_limit?: number }
        Returns: {
          amount: number
          authorization_code: string
          card_last4: string
          card_scheme: string
          commerce_order_id: string
          created_at: string
          currency: string
          external_ref: string
          finalization_error: string
          id: string
          operation: string
          response_code: string
          response_message: string
          rrn: string
          source_invoice_id: string
          status: string
          terminal_id: string
          terminal_label: string
          terminal_transaction_id: string
          updated_at: string
        }[]
      }
      list_pos_card_terminals: {
        Args: { p_device_id?: string; p_warehouse_id?: string }
        Returns: {
          acquirer_name: string
          adapter_config: Json
          adapter_key: string
          allow_delivery: boolean
          code: string
          device_id: string
          external_terminal_id: string
          id: string
          is_active: boolean
          label: string
          warehouse_id: string
        }[]
      }
      list_pos_customer_garage: {
        Args: { p_customer_id: string }
        Returns: {
          chassis_code: string
          customer_id: string
          engine: string
          generation: string
          id: string
          is_primary: boolean
          make: string
          model: string
          model_slug: string
          vin: string
        }[]
      }
      list_pos_customers: {
        Args: { p_limit?: number; p_query?: string }
        Returns: {
          business_name: string
          customer_kind: string
          display_name: string
          email: string
          id: string
          phone_e164: string
          whatsapp_e164: string
        }[]
      }
      list_pos_fulfillment_deposits: {
        Args: { p_request_id: string }
        Returns: Json
      }
      list_pos_fulfillment_requests: {
        Args: { p_limit?: number; p_query?: string; p_status?: string }
        Returns: {
          cart_id: string
          collected_at: string
          created_at: string
          customer_id: string
          deposits_held: Json
          description: string
          destination_warehouse_id: string
          destination_warehouse_name: string
          document_number: string
          expires_at: string
          id: string
          invoice_id: string
          kind: string
          oem_part_number: string
          qty: number
          ready_at: string
          source_warehouse_id: string
          source_warehouse_name: string
          status: string
          stock_entry_id: string
          stock_item_id: string
          uom_id: string
        }[]
      }
      list_pos_handover_operators: {
        Args: Record<PropertyKey, never>
        Returns: {
          employee_code: string
          full_name: string
          roles: string[]
          user_id: string
        }[]
      }
      list_pos_hidden_bestsellers: {
        Args: Record<PropertyKey, never>
        Returns: {
          hidden_at: string
          stock_item_id: string
        }[]
      }
      list_pos_payment_recovery: {
        Args: { p_limit?: number }
        Returns: {
          active_intent_id: string
          active_provider: string
          cart_id: string
          currency: string
          open_exception_count: number
          order_id: string
          payment_exception: string
          reservation_expires_at: string
          sales_invoice_id: string
          settled_provider: string
          settled_provider_ref: string
          state: string
          total: number
          updated_at: string
        }[]
      }
      list_pos_pickup_orders: {
        Args: { p_limit?: number; p_query?: string }
        Returns: {
          currency: string
          customer_id: string
          customer_name: string
          document_number: string
          order_id: string
          sales_invoice_id: string
          settled_provider: string
          state: string
          total: number
          updated_at: string
        }[]
      }
      list_pos_popular_pins: {
        Args: Record<PropertyKey, never>
        Returns: {
          category_name: string
          image_url: string
          item_key: string
          item_type: string
          label: string
          maker_slug: string
          model_slug: string
          oem_part_number: string
          search_query: string
          subcategory_name: string
          subtitle: string
          updated_at: string
        }[]
      }
      list_pos_popular_spares: {
        Args: { p_days?: number; p_limit?: number }
        Returns: {
          currency: Database["public"]["Enums"]["currency_code"]
          description: string
          image_storage_path: string
          oem_part_number: string
          saleable_qty: number
          stock_item_id: string
          unit_price: number
          units_sold: number
        }[]
      }
      list_pos_quotations: {
        Args: {
          p_limit?: number
          p_status?: Database["public"]["Enums"]["pos_quotation_status"]
        }
        Returns: {
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          document_number: string
          id: string
          line_count: number
          sent_channel: string
          status: Database["public"]["Enums"]["pos_quotation_status"]
          total: number
          valid_until: string
          warehouse_id: string
        }[]
      }
      list_pos_recent_invoices: {
        Args: { p_limit?: number; p_query?: string }
        Returns: {
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          customer_name: string
          document_number: string
          id: string
          posted_at: string
          total: number
          vehicle_chassis_code: string
          vehicle_engine_code: string
          vehicle_generation: string
          vehicle_model_name: string
        }[]
      }
      list_pos_split_payment_recovery: {
        Args: { p_limit?: number }
        Returns: {
          currency: string
          customer_name: string
          document_number: string
          order_id: string
          payload: Json
          session_id: string
          status: string
          total: number
          updated_at: string
        }[]
      }
      list_pos_stock_availability: {
        Args: { p_stock_item_id: string }
        Returns: {
          available: number
          on_hand: number
          reserved: number
          transfer_incoming: number
          warehouse_code: string
          warehouse_id: string
          warehouse_name: string
        }[]
      }
      list_pos_till_items: {
        Args: {
          p_category?: string
          p_chassis_code?: string
          p_engine_code?: string
          p_in_stock_only?: boolean
          p_limit?: number
          p_oems?: string[]
          p_offset?: number
          p_section_key?: string
          p_source: string
          p_warehouse_id: string
        }
        Returns: Json
      }
      list_pos_till_sessions: {
        Args: { p_limit?: number; p_status?: string }
        Returns: {
          approved_at: string | null
          approved_by: string | null
          close_notes: string | null
          closed_at: string | null
          counted_cash: number | null
          currency: Database["public"]["Enums"]["currency_code"]
          device_id: string
          expected_cash: number | null
          id: string
          opened_at: string
          opened_by: string
          opening_float: number
          operator_user_id: string
          status: Database["public"]["Enums"]["pos_till_session_status"]
          updated_at: string
          variance: number | null
          variance_reason_code: string | null
          warehouse_id: string
        }[]
        SetofOptions: {
          from: "*"
          to: "pos_till_sessions"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      list_pos_warranty_claims: {
        Args: { p_limit?: number; p_query?: string; p_status?: string }
        Returns: {
          closed_at: string
          created_at: string
          credit_note_id: string
          customer_id: string
          decided_at: string
          document_number: string
          id: string
          invoice_number: string
          notes: string
          oem_part_number: string
          quarantine_stock_entry_id: string
          reject_reason: string
          replacement_stock_entry_id: string
          resolution: string
          sales_invoice_id: string
          serial_number: string
          status: string
          stock_item_id: string
          stock_serial_id: string
        }[]
      }
      list_receipt_documents_needing_pdf: {
        Args: { p_limit?: number }
        Returns: {
          document_id: string
        }[]
      }
      list_staff_ops_notifications: {
        Args: { p_limit?: number }
        Returns: {
          body: string
          created_at: string
          delivery_job_id: string | null
          href: string | null
          id: string
          kind: Database["public"]["Enums"]["staff_ops_notification_kind"]
          read_at: string | null
          recipient_user_id: string
          ref_key: string | null
          sales_invoice_id: string | null
          title: string
        }[]
        SetofOptions: {
          from: "*"
          to: "staff_ops_notifications"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      list_staff_product_pages: {
        Args: { p_limit?: number; p_query?: string }
        Returns: {
          catalog_title: string
          currency: Database["public"]["Enums"]["currency_code"]
          discount_description: string
          discount_kind: string
          discount_value: number
          image_count: number
          oem_part_number: string
          primary_image_path: string
          qty_saleable: number
          stock_item_id: string
          unit_price: number
        }[]
      }
      list_storefront_home_rails: {
        Args: { p_limit?: number }
        Returns: {
          catalog_title: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          discount_description: string
          discount_kind: string
          discount_value: number
          oem_part_number: string
          qty_saleable: number
          rail: string
          reorder_point: number
          unit_price: number
        }[]
      }
      list_zig_exchange_rates: {
        Args: { p_limit?: number }
        Returns: {
          created_at: string
          id: string
          notes: string
          rate: number
          rate_date: string
          set_by: string
        }[]
      }
      lock_accounting_period: {
        Args: { p_period_id: string }
        Returns: undefined
      }
      log_finance_audit: {
        Args: {
          p_action: string
          p_after?: Json
          p_before?: Json
          p_entity_id: string
          p_entity_type: string
        }
        Returns: string
      }
      mark_chat_thread_read: {
        Args: { p_thread_id: string }
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
      mark_ecocash_settled: {
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
      mark_pos_fulfillment_ready: {
        Args: { p_notes?: string; p_request_id: string }
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
      mark_staff_ops_notification_read: {
        Args: { p_id: string }
        Returns: string
      }
      markdown_stock_item: {
        Args: { p_percent: number; p_reason: string; p_stock_item_id: string }
        Returns: Json
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
      my_default_landing: { Args: Record<PropertyKey, never>; Returns: string }
      my_module_access: { Args: Record<PropertyKey, never>; Returns: Json }
      next_employee_code_for_grade: {
        Args: { p_grade_code: string }
        Returns: string
      }
      next_series_value: { Args: { p_prefix: string }; Returns: string }
      open_account_period: {
        Args: {
          p_account_code: string
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_notes?: string
          p_opening_balance?: number
          p_period_end: string
          p_period_start: string
        }
        Returns: string
      }
      open_pos_till_session: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_device_id: string
          p_opening_float: number
          p_warehouse_id: string
        }
        Returns: string
      }
      open_pos_warranty_claim: {
        Args: {
          p_invoice_line_id: string
          p_notes?: string
          p_sales_invoice_id: string
          p_stock_serial_id?: string
        }
        Returns: string
      }
      open_warranty_claim: {
        Args: {
          p_notes?: string
          p_sales_invoice_id?: string
          p_stock_batch_id?: string
          p_stock_serial_id?: string
        }
        Returns: string
      }
      optimize_driver_stops: {
        Args: { p_driver_user_id: string }
        Returns: {
          delivery_job_id: string
          distance_m: number
          route_sequence: number
        }[]
      }
      park_pos_cart: { Args: { p_cart_id: string }; Returns: string }
      payslip_render_payload: {
        Args: { p_payroll_line_id: string }
        Returns: Json
      }
      petty_cash_funding_account_code: {
        Args: Record<PropertyKey, never>
        Returns: string
      }
      pos_action_requires_manager: {
        Args: { p_action: string; p_value?: number }
        Returns: boolean
      }
      pos_badge_approve: {
        Args: {
          p_action: string
          p_args?: Json
          p_badge: string
          p_device_id?: string
        }
        Returns: Json
      }
      pos_till_expected_cash: {
        Args: { p_session_id: string }
        Returns: number
      }
      post_chat_message: {
        Args: { p_body: string; p_thread_id: string }
        Returns: string
      }
      post_customer_return_credit_note: {
        Args: { p_invoice_id: string; p_lines: Json }
        Returns: string
      }
      post_finance_refund: {
        Args: { p_invoice_id: string; p_notes?: string }
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
      post_pos_core_return: {
        Args: {
          p_invoice_id: string
          p_notes?: string
          p_qty: number
          p_reason_code: string
          p_resolution: Database["public"]["Enums"]["pos_core_return_resolution"]
          p_source_core_line_id: string
          p_till_session_id?: string
        }
        Returns: string
      }
      post_pos_refund: {
        Args: { p_invoice_id: string; p_notes?: string }
        Returns: string
      }
      post_pos_refund_governed: {
        Args: { p_invoice_id: string; p_notes?: string; p_reason_code: string }
        Returns: string
      }
      post_pos_return_case: {
        Args: { p_return_case_id: string }
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
      prepare_customer_checkout: {
        Args: {
          p_cart_id: string
          p_checkout_request_id: string
          p_reservation_ttl?: string
        }
        Returns: string
      }
      prepare_pos_commerce_checkout: {
        Args: {
          p_cart_id: string
          p_checkout_request_id: string
          p_reservation_ttl?: string
        }
        Returns: string
      }
      prepare_pos_commerce_checkout_v2: {
        Args: {
          p_cart_id: string
          p_checkout_request_id: string
          p_receipt_email?: string
          p_receipt_phone_e164?: string
          p_receipt_whatsapp_e164?: string
          p_reservation_ttl?: string
        }
        Returns: string
      }
      process_receipt_outbox_batch: {
        Args: { p_limit?: number; p_stub_success?: boolean }
        Returns: number
      }
      pull_pos_offline_snapshot: {
        Args: { p_warehouse_id: string }
        Returns: Json
      }
      purge_delivery_locations: {
        Args: { p_older_than?: string }
        Returns: number
      }
      raise_delivery_panic: {
        Args: { p_delivery_job_id?: string; p_lat?: number; p_lng?: number }
        Returns: string
      }
      receive_customer_return: {
        Args: { p_return_request_id: string }
        Returns: string
      }
      receive_driver_cash_handin: {
        Args: {
          p_counted_amount: number
          p_handin_id: string
          p_notes?: string
          p_reason_code?: string
        }
        Returns: Json
      }
      record_driver_cash_recovery: {
        Args: { p_amount: number; p_handin_id: string; p_notes?: string }
        Returns: Json
      }
      record_lost_demand: {
        Args: {
          p_note?: string
          p_qty: number
          p_stock_item_id: string
          p_warehouse_id: string
        }
        Returns: string
      }
      record_pos_card_terminal_result: {
        Args: {
          p_actor_user_id: string
          p_attempt_id: string
          p_authorization_code?: string
          p_card_last4?: string
          p_card_scheme?: string
          p_outcome: string
          p_response_code?: string
          p_response_message?: string
          p_rrn?: string
          p_terminal_transaction_id?: string
        }
        Returns: Json
      }
      record_pos_till_cash_movement: {
        Args: {
          p_amount: number
          p_finance_refund_id?: string
          p_kind: Database["public"]["Enums"]["pos_till_cash_movement_kind"]
          p_notes?: string
          p_reason_code: string
          p_session_id: string
        }
        Returns: string
      }
      record_staff_login_attempt: {
        Args: { p_identifier: string; p_success: boolean }
        Returns: undefined
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
      refund_pos_fulfillment_deposit: {
        Args: {
          p_deposit_id: string
          p_notes?: string
          p_till_session_id?: string
        }
        Returns: Json
      }
      register_delivery_card_terminal_device_key: {
        Args: {
          p_device_id: string
          p_key_sha256: string
          p_public_key_spki_base64: string
          p_terminal_id: string
        }
        Returns: string
      }
      register_my_manager_signature: {
        Args: { p_mime_type: string; p_sha256: string; p_storage_path: string }
        Returns: Json
      }
      register_pos_card_terminal_device_key: {
        Args: {
          p_device_id: string
          p_key_sha256: string
          p_public_key_spki_base64: string
          p_terminal_id: string
        }
        Returns: string
      }
      register_stock_item_image: {
        Args: {
          p_is_primary?: boolean
          p_sort_order?: number
          p_stock_item_id: string
          p_storage_path: string
        }
        Returns: string
      }
      reject_finance_requisition: {
        Args: { p_reason?: string; p_requisition_id: string }
        Returns: string
      }
      reject_material_request: {
        Args: { p_material_request_id: string; p_reason?: string }
        Returns: string
      }
      reject_pos_warranty_claim: {
        Args: { p_claim_id: string; p_reason: string }
        Returns: string
      }
      reject_purchase_order: {
        Args: { p_purchase_order_id: string; p_reason?: string }
        Returns: string
      }
      reject_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
      reject_warranty_claim: {
        Args: { p_claim_id: string; p_reason?: string }
        Returns: string
      }
      release_hr_auth_provision: {
        Args: { p_claim_token: string; p_employee_id: string }
        Returns: boolean
      }
      remove_customer_compare_item: {
        Args: {
          p_compare_id?: string
          p_oem_part_number?: string
          p_stock_item_id?: string
        }
        Returns: undefined
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
      repair_pos_paid_order: {
        Args: { p_notes?: string; p_order_id: string }
        Returns: string
      }
      replay_offline_pos_sale: {
        Args: { p_client_sale_id: string; p_payload: Json }
        Returns: string
      }
      report_account_register: {
        Args: {
          p_account_code: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_from: string
          p_to: string
        }
        Returns: {
          credit: number
          currency: Database["public"]["Enums"]["currency_code"]
          debit: number
          description: string
          document_number: string
          entry_date: string
          journal_entry_id: string
          running_balance: number
        }[]
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
      request_customer_return: {
        Args: { p_invoice_id: string; p_lines: Json }
        Returns: string
      }
      request_delivery_balance_on_account: {
        Args: { p_delivery_job_id: string; p_reason: string }
        Returns: Json
      }
      request_pos_split_cancellation: {
        Args: { p_fee_policy?: string; p_reason: string; p_session_id: string }
        Returns: Json
      }
      resolve_auth_user: {
        Args: { p_email?: string; p_phone_e164?: string }
        Returns: string
      }
      resolve_customer_for_receipt_contacts: {
        Args: {
          p_email?: string
          p_phone_e164?: string
          p_whatsapp_e164?: string
        }
        Returns: string
      }
      resolve_item_price: {
        Args: { p_customer_id: string; p_stock_item_id: string }
        Returns: {
          core_charge: number
          currency: Database["public"]["Enums"]["currency_code"]
          unit_price: number
        }[]
      }
      resolve_password_reset_user: {
        Args: { p_email?: string; p_phone_e164?: string }
        Returns: string
      }
      resolve_staff_login_email: {
        Args: { p_identifier: string }
        Returns: string
      }
      resolve_stock_item_by_oem: { Args: { p_oem: string }; Returns: string }
      resume_pos_cart: { Args: { p_cart_id: string }; Returns: string }
      retry_pos_split_finalization: {
        Args: { p_session_id: string }
        Returns: Json
      }
      reverse_journal: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
      }
      reverse_loyalty_movement: {
        Args: { p_ledger_id: string }
        Returns: string
      }
      review_customer_return: {
        Args: {
          p_approve: boolean
          p_notes?: string
          p_return_request_id: string
        }
        Returns: string
      }
      revoke_approval_badge: {
        Args: { p_badge_id: string; p_reason: string }
        Returns: string
      }
      revoke_pos_scan_session: {
        Args: { p_session_id: string }
        Returns: string
      }
      revoke_staff_role: {
        Args: {
          p_role: Database["public"]["Enums"]["staff_role"]
          p_user_id: string
        }
        Returns: undefined
      }
      run_due_payroll_schedules: { Args: { p_as_of?: string }; Returns: Json }
      run_inventory_abc_classification: {
        Args: { p_from: string; p_to: string }
        Returns: number
      }
      run_scheduled_payroll_for_frequency: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_frequency: Database["public"]["Enums"]["hr_pay_frequency"]
          p_period_end: string
          p_period_start: string
        }
        Returns: string
      }
      save_hr_onboarding_stage: {
        Args: {
          p_banking_json?: Json
          p_draft_id?: string
          p_employee_id?: string
          p_health_json?: Json
          p_payload?: Json
          p_stage?: Database["public"]["Enums"]["hr_onboarding_stage"]
        }
        Returns: string
      }
      scrub_hr_credential_outbox_bodies: {
        Args: { p_max_age_seconds?: number }
        Returns: number
      }
      search_catalog: {
        Args: { p_mode: string; p_query: string }
        Returns: Json
      }
      search_pos_stock_items: {
        Args: { p_limit?: number; p_query: string }
        Returns: Json
      }
      search_pos_vehicle_spares: {
        Args: {
          p_chassis_code: string
          p_engine_code: string
          p_limit?: number
          p_model_slug: string
          p_query: string
        }
        Returns: Json
      }
      send_pos_quotation: {
        Args: { p_channel: string; p_contact?: string; p_quotation_id: string }
        Returns: string
      }
      set_approver_assignment: {
        Args: { p_assigned: boolean; p_employee_id: string; p_notes?: string }
        Returns: boolean
      }
      set_branch_reorder_point: {
        Args: {
          p_reorder_point: number
          p_reorder_qty?: number
          p_stock_item_id: string
          p_warehouse_id: string
        }
        Returns: Json
      }
      set_business_document_profile: {
        Args: {
          p_address_line1?: string
          p_address_line2?: string
          p_city?: string
          p_country?: string
          p_domain: string
          p_email?: string
          p_legal_name: string
          p_phone_e164?: string
          p_registration_number?: string
          p_trading_name: string
        }
        Returns: Json
      }
      set_customer_cart_delivery_payment_method: {
        Args: {
          p_cart_id: string
          p_method: Database["public"]["Enums"]["delivery_payment_method"]
        }
        Returns: string
      }
      set_customer_cart_line_qty: {
        Args: { p_line_id: string; p_qty: number }
        Returns: string
      }
      set_customer_credit: {
        Args: {
          p_credit_hold?: boolean
          p_credit_limit?: number
          p_customer_id: string
        }
        Returns: {
          credit_hold: boolean
          credit_limit: number
          credit_limit_minor: number
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string
          open_balance: number
          open_balance_minor: number
        }[]
      }
      set_customer_marketing_opt_in: {
        Args: { p_customer_id: string; p_opt_in: boolean }
        Returns: string
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
      set_finance_requisition_lines: {
        Args: { p_lines: Json; p_requisition_id: string }
        Returns: string
      }
      set_fleet_vehicle_status: {
        Args: {
          p_id: string
          p_status: Database["public"]["Enums"]["fleet_vehicle_status"]
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
      set_must_change_password: {
        Args: { p_required?: boolean; p_user_id: string }
        Returns: undefined
      }
      set_my_alert_settings: {
        Args: {
          p_channel: string
          p_morning_summary: boolean
          p_phone_e164: string
          p_urgent_approvals: boolean
        }
        Returns: Json
      }
      set_own_marketing_opt_in: { Args: { p_opt_in: boolean }; Returns: string }
      set_pos_approval_policy: {
        Args: {
          p_action: string
          p_always_require_manager: boolean
          p_reason_required: boolean
          p_threshold_value: number
        }
        Returns: string
      }
      set_pos_card_terminal_delivery_enabled: {
        Args: { p_enabled: boolean; p_terminal_id: string }
        Returns: string
      }
      set_pos_cart_customer: {
        Args: { p_cart_id: string; p_customer_id?: string }
        Returns: string
      }
      set_pos_cart_line_qty: {
        Args: { p_line_id: string; p_qty: number }
        Returns: string
      }
      set_pos_cart_vehicle: {
        Args: {
          p_cart_id: string
          p_chassis_code?: string
          p_engine_code?: string
          p_generation?: string
          p_model_name?: string
          p_model_slug?: string
        }
        Returns: string
      }
      set_stock_item_primary_image: {
        Args: { p_image_id: string }
        Returns: string
      }
      set_stock_level_bin: {
        Args: {
          p_bin_id?: string
          p_stock_item_id: string
          p_warehouse_id: string
        }
        Returns: string
      }
      set_wishlist_notify_when_in_stock: {
        Args: {
          p_notify: boolean
          p_oem_part_number?: string
          p_stock_item_id?: string
          p_wishlist_id?: string
        }
        Returns: string
      }
      set_zig_exchange_rate: {
        Args: { p_notes?: string; p_rate: number; p_rate_date?: string }
        Returns: string
      }
      settle_commerce_manual_payment: {
        Args: {
          p_order_id: string
          p_payment_request_id: string
          p_reference?: string
          p_settlement_amount?: number
          p_settlement_currency?: Database["public"]["Enums"]["currency_code"]
          p_settlement_exchange_rate?: number
          p_tender: Database["public"]["Enums"]["payment_tender"]
        }
        Returns: string
      }
      settle_invoice_tenders: {
        Args: { p_invoice_id: string; p_tenders: Json }
        Returns: string
      }
      settle_pos_commerce_tenders: {
        Args: {
          p_order_id: string
          p_payment_request_id: string
          p_tenders: Json
        }
        Returns: Json
      }
      settle_return_refund_store_credit: {
        Args: { p_notes?: string; p_return_request_id: string }
        Returns: string
      }
      staff_login_is_locked: {
        Args: { p_identifier: string }
        Returns: boolean
      }
      start_chat_thread: {
        Args: {
          p_body?: string
          p_kind?: Database["public"]["Enums"]["chat_thread_kind"]
          p_subject?: string
        }
        Returns: string
      }
      start_pos_split_payment: { Args: { p_order_id: string }; Returns: Json }
      store_catalog_r2_credentials_once: {
        Args: {
          p_access_key_id: string
          p_account_id: string
          p_bucket: string
          p_endpoint?: string
          p_secret_access_key: string
        }
        Returns: undefined
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
      submit_delivery_note_integrity_v1: {
        Args: { p_delivery_note_id: string }
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
      submit_driver_cash_handin: {
        Args: {
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_declared_amount: number
          p_notes?: string
        }
        Returns: Json
      }
      submit_finance_requisition: {
        Args: { p_requisition_id: string }
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
      submit_leave_request: { Args: { p_request_id: string }; Returns: string }
      submit_material_request: {
        Args: { p_material_request_id: string }
        Returns: string
      }
      submit_payroll_run: {
        Args: { p_payroll_run_id: string }
        Returns: string
      }
      submit_pos_till_denominated_close: {
        Args: {
          p_denominations: Json
          p_notes?: string
          p_session_id: string
          p_variance_reason_code?: string
        }
        Returns: Json
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
      suggest_delivery_assignees: {
        Args: { p_delivery_job_id: string; p_limit?: number }
        Returns: {
          capacity: number
          distance_m: number
          last_lat: number
          last_lng: number
          last_seen_at: string
          open_jobs: number
          status: Database["public"]["Enums"]["driver_presence_status"]
          user_id: string
        }[]
      }
      suspend_customer: {
        Args: { p_customer_id: string; p_reason: string }
        Returns: Json
      }
      take_pos_fulfillment_deposit: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_reference?: string
          p_request_id: string
          p_tender: Database["public"]["Enums"]["payment_tender"]
          p_till_session_id?: string
        }
        Returns: Json
      }
      touch_ai_worker_schedule: {
        Args: { p_worker_key: string }
        Returns: undefined
      }
      unhide_pos_bestseller: {
        Args: { p_stock_item_id: string }
        Returns: boolean
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
          p_title?: string
        }
        Returns: string
      }
      update_own_customer_profile: {
        Args: {
          p_display_name?: string
          p_email?: string
          p_email_receipts?: boolean
          p_phone_e164?: string
          p_sms_receipts?: boolean
          p_whatsapp_e164?: string
          p_whatsapp_receipts?: boolean
        }
        Returns: string
      }
      update_pos_customer: {
        Args: {
          p_business_name?: string
          p_customer_id: string
          p_customer_kind: string
          p_display_name: string
          p_email?: string
          p_phone_e164?: string
          p_whatsapp_e164?: string
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
      upsert_customer_address: {
        Args: {
          p_city?: string
          p_country?: string
          p_id?: string
          p_is_default?: boolean
          p_label?: string
          p_line1?: string
          p_line2?: string
          p_postal_code?: string
          p_province?: string
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
      upsert_fleet_vehicle: {
        Args: {
          p_assigned_driver_user_id?: string
          p_id?: string
          p_label?: string
          p_notes?: string
          p_plate: string
          p_status?: Database["public"]["Enums"]["fleet_vehicle_status"]
        }
        Returns: string
      }
      upsert_pos_card_terminal: {
        Args: {
          p_acquirer_name: string
          p_adapter_config: Json
          p_adapter_key: string
          p_code: string
          p_device_id: string
          p_external_terminal_id: string
          p_id: string
          p_is_active: boolean
          p_label: string
          p_warehouse_id: string
        }
        Returns: string
      }
      upsert_pos_customer_garage_vehicle: {
        Args: {
          p_chassis_code?: string
          p_customer_id: string
          p_engine?: string
          p_generation?: string
          p_is_primary?: boolean
          p_make?: string
          p_model?: string
          p_model_slug?: string
          p_vehicle_id?: string
          p_vin?: string
        }
        Returns: string
      }
      upsert_pos_popular_pin: {
        Args: {
          p_category_name?: string
          p_image_url?: string
          p_item_key: string
          p_item_type: string
          p_label: string
          p_maker_slug?: string
          p_model_slug?: string
          p_oem_part_number?: string
          p_search_query?: string
          p_subcategory_name?: string
          p_subtitle?: string
        }
        Returns: string
      }
      upsert_preferred_supplier: {
        Args: {
          p_address?: string
          p_categories?: string[]
          p_code: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_email?: string
          p_name: string
          p_notes?: string
          p_payment_terms?: string
          p_phone_e164?: string
          p_tax_id?: string
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
      upsert_staff_product_page: {
        Args: {
          p_discount_description?: string
          p_discount_kind?: string
          p_discount_value?: number
          p_stock_item_id: string
          p_unit_price: number
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
      verify_auth_challenge: {
        Args: {
          p_channel: string
          p_code_hash: string
          p_device_hash?: string
          p_identifier: string
          p_ip_hash?: string
          p_kind: string
        }
        Returns: string
      }
      verify_delivery_pod_otp: {
        Args: { p_code: string; p_delivery_job_id: string }
        Returns: boolean
      }
      void_pos_cart: {
        Args: { p_cart_id: string; p_notes?: string }
        Returns: string
      }
      void_pos_cart_governed: {
        Args: { p_cart_id: string; p_notes?: string; p_reason_code: string }
        Returns: string
      }
      wishlist_move_to_cart: {
        Args: {
          p_cart_id: string
          p_oem_part_number?: string
          p_qty?: number
          p_remove_from_wishlist?: boolean
          p_stock_item_id?: string
          p_wishlist_id?: string
        }
        Returns: string
      }
      write_off_driver_cash_shortage: {
        Args: {
          p_amount: number
          p_handin_id: string
          p_notes?: string
          p_reason_code: string
        }
        Returns: Json
      }
    }
    Enums: {
      account_period_status: "open" | "closed"
      account_type: "asset" | "liability" | "equity" | "income" | "expense"
      ai_delivery_channel: "email" | "whatsapp"
      ai_delivery_status: "queued" | "sent" | "failed" | "skipped"
      ai_promo_run_status:
        | "pending"
        | "running"
        | "succeeded"
        | "partial"
        | "failed"
        | "skipped"
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
      commerce_order_state:
        | "checkout_pending"
        | "awaiting_payment"
        | "payment_processing"
        | "paid"
        | "allocation_pending"
        | "ready_for_pick"
        | "picking"
        | "packed"
        | "ready_for_collection"
        | "dispatch_ready"
        | "dispatched"
        | "delivered"
        | "payment_failed"
        | "payment_expired"
        | "cancelled"
        | "partially_fulfilled"
        | "refunded"
        | "returned"
        | "account_invoiced"
      commerce_outbox_state: "pending" | "processing" | "delivered" | "failed"
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
      customer_return_status:
        | "requested"
        | "approved"
        | "rejected"
        | "received"
        | "refund_pending"
        | "completed"
        | "cancelled"
      delivery_completed_via: "pod" | "manual" | "admin"
      delivery_eta_source: "haversine" | "osrm" | "manual"
      delivery_failure_reason:
        | "customer_absent"
        | "refused"
        | "wrong_address"
        | "damaged"
        | "other"
      delivery_job_status: "pending" | "dispatched" | "completed" | "failed"
      delivery_note_status: "draft" | "submitted" | "cancelled"
      delivery_payment_method:
        | "prepay"
        | "cash_on_delivery"
        | "card_on_delivery"
        | "cash_or_card_on_delivery"
      driver_presence_status: "available" | "on_duty" | "break" | "offline"
      ecocash_intent_status:
        | "pending"
        | "authorized"
        | "settled"
        | "failed"
        | "cancelled"
      employee_status: "active" | "inactive" | "terminated"
      finance_requisition_status:
        | "draft"
        | "submitted"
        | "approved"
        | "rejected"
        | "disbursed"
        | "cancelled"
      finance_requisition_type:
        | "petty_cash"
        | "payment"
        | "salary"
        | "refund"
        | "asset_capex"
        | "vendor"
        | "petty_float"
      fleet_vehicle_status: "active" | "in_service" | "retired"
      forecast_suggestion_status: "open" | "converted" | "dismissed"
      fulfillment_mode: "immediate" | "dispatch"
      hr_credential_channel: "email" | "sms" | "whatsapp"
      hr_credential_outbox_status:
        | "pending"
        | "sending"
        | "sent"
        | "failed"
        | "skipped"
      hr_leave_request_status:
        | "draft"
        | "submitted"
        | "approved"
        | "rejected"
        | "cancelled"
      hr_onboarding_stage:
        | "personal"
        | "role_contract"
        | "banking_health"
        | "documents"
        | "credentials"
      hr_pay_frequency: "weekly" | "fortnightly" | "monthly"
      inventory_abc_class: "A" | "B" | "C"
      inventory_reservation_state:
        | "active"
        | "allocated"
        | "consumed"
        | "released"
        | "expired"
      journal_status: "draft" | "posted"
      kit_line_kind: "header" | "component"
      kit_sell_mode: "stocked" | "explode"
      loyalty_movement: "earn" | "redeem" | "expire" | "reverse"
      payment_entry_status: "draft" | "posted" | "cancelled"
      payment_resolution_source_kind:
        | "card_terminal"
        | "ecocash"
        | "paynow"
        | "contipay"
        | "split_leg"
        | "split_refund"
      payment_tender:
        | "cash"
        | "bank"
        | "contipay"
        | "store_credit"
        | "paynow"
        | "ecocash"
        | "card_terminal"
      paynow_intent_status:
        | "pending"
        | "authorized"
        | "settled"
        | "failed"
        | "cancelled"
      paynow_method: "ecocash" | "onemoney" | "innbucks" | "visa"
      payroll_run_status: "draft" | "submitted" | "cancelled"
      pick_list_status: "draft" | "done" | "cancelled"
      pos_card_terminal_attempt_status:
        | "initiated"
        | "approved"
        | "declined"
        | "cancelled"
        | "unknown"
        | "failed"
        | "settled"
        | "reversed"
      pos_card_terminal_operation: "purchase" | "refund" | "reversal"
      pos_core_return_resolution:
        | "cash_refund"
        | "account_credit"
        | "store_credit"
      pos_fulfillment_kind:
        | "branch_transfer"
        | "alternate_pickup"
        | "customer_collection"
        | "backorder"
      pos_fulfillment_status:
        | "requested"
        | "reserved"
        | "awaiting_transfer_approval"
        | "ready"
        | "collected"
        | "cancelled"
        | "rejected"
      pos_quotation_status:
        | "draft"
        | "issued"
        | "sent"
        | "converted"
        | "cancelled"
        | "expired"
      pos_return_case_status: "draft" | "posted" | "cancelled"
      pos_return_condition:
        | "sealed"
        | "unopened"
        | "opened"
        | "damaged"
        | "defective"
      pos_return_resolution:
        | "credit_note"
        | "cash_refund"
        | "store_credit"
        | "replacement"
        | "warranty"
      pos_scan_session_status: "open" | "claimed" | "revoked" | "expired"
      pos_split_payment_leg_status:
        | "planned"
        | "held"
        | "pending"
        | "captured"
        | "failed"
        | "unknown"
        | "allocated"
        | "refund_review"
        | "refund_pending"
        | "refunded"
        | "cancelled"
      pos_split_payment_session_status:
        | "open"
        | "partially_captured"
        | "leg_pending"
        | "fully_committed"
        | "finalizing"
        | "settled"
        | "finalization_failed"
        | "refund_review"
        | "refund_pending"
        | "refunded"
        | "cancelled"
      pos_split_refund_status:
        | "review"
        | "pending"
        | "settled"
        | "failed"
        | "cancelled"
      pos_till_cash_movement_kind:
        | "cash_in"
        | "cash_out"
        | "petty_cash"
        | "bank_drop"
        | "cash_refund"
      pos_till_session_status: "open" | "variance_pending" | "closed"
      procurement_doc_status:
        | "draft"
        | "submitted"
        | "cancelled"
        | "approved"
        | "rejected"
      product_review_status: "pending" | "approved" | "rejected"
      receipt_channel: "sms" | "email" | "whatsapp"
      receipt_outbox_status:
        | "pending"
        | "rendering"
        | "sending"
        | "sent"
        | "failed"
        | "cancelled"
      return_refund_status:
        | "pending"
        | "processing"
        | "completed"
        | "failed"
        | "cancelled"
      return_resolution_method:
        | "original_payment"
        | "store_credit"
        | "cash"
        | "bank"
      salary_pay_type: "hourly" | "salary"
      sales_doc_status: "draft" | "posted" | "cancelled" | "on_hold"
      sales_doc_type: "invoice" | "credit_note"
      sms_event_priority: "low" | "normal" | "high"
      sms_outbox_status: "pending" | "sending" | "sent" | "failed" | "cancelled"
      staff_ops_notification_kind:
        | "sales_prep"
        | "driver_assigned"
        | "approval_waiting"
        | "morning_summary"
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
      account_period_status: ["open", "closed"],
      account_type: ["asset", "liability", "equity", "income", "expense"],
      ai_delivery_channel: ["email", "whatsapp"],
      ai_delivery_status: ["queued", "sent", "failed", "skipped"],
      ai_promo_run_status: [
        "pending",
        "running",
        "succeeded",
        "partial",
        "failed",
        "skipped",
      ],
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
      commerce_order_state: [
        "checkout_pending",
        "awaiting_payment",
        "payment_processing",
        "paid",
        "allocation_pending",
        "ready_for_pick",
        "picking",
        "packed",
        "ready_for_collection",
        "dispatch_ready",
        "dispatched",
        "delivered",
        "payment_failed",
        "payment_expired",
        "cancelled",
        "partially_fulfilled",
        "refunded",
        "returned",
        "account_invoiced",
      ],
      commerce_outbox_state: ["pending", "processing", "delivered", "failed"],
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
      customer_return_status: [
        "requested",
        "approved",
        "rejected",
        "received",
        "refund_pending",
        "completed",
        "cancelled",
      ],
      delivery_completed_via: ["pod", "manual", "admin"],
      delivery_eta_source: ["haversine", "osrm", "manual"],
      delivery_failure_reason: [
        "customer_absent",
        "refused",
        "wrong_address",
        "damaged",
        "other",
      ],
      delivery_job_status: ["pending", "dispatched", "completed", "failed"],
      delivery_note_status: ["draft", "submitted", "cancelled"],
      delivery_payment_method: [
        "prepay",
        "cash_on_delivery",
        "card_on_delivery",
        "cash_or_card_on_delivery",
      ],
      driver_presence_status: ["available", "on_duty", "break", "offline"],
      ecocash_intent_status: [
        "pending",
        "authorized",
        "settled",
        "failed",
        "cancelled",
      ],
      employee_status: ["active", "inactive", "terminated"],
      finance_requisition_status: [
        "draft",
        "submitted",
        "approved",
        "rejected",
        "disbursed",
        "cancelled",
      ],
      finance_requisition_type: [
        "petty_cash",
        "payment",
        "salary",
        "refund",
        "asset_capex",
        "vendor",
        "petty_float",
      ],
      fleet_vehicle_status: ["active", "in_service", "retired"],
      forecast_suggestion_status: ["open", "converted", "dismissed"],
      fulfillment_mode: ["immediate", "dispatch"],
      hr_credential_channel: ["email", "sms", "whatsapp"],
      hr_credential_outbox_status: [
        "pending",
        "sending",
        "sent",
        "failed",
        "skipped",
      ],
      hr_leave_request_status: [
        "draft",
        "submitted",
        "approved",
        "rejected",
        "cancelled",
      ],
      hr_onboarding_stage: [
        "personal",
        "role_contract",
        "banking_health",
        "documents",
        "credentials",
      ],
      hr_pay_frequency: ["weekly", "fortnightly", "monthly"],
      inventory_abc_class: ["A", "B", "C"],
      inventory_reservation_state: [
        "active",
        "allocated",
        "consumed",
        "released",
        "expired",
      ],
      journal_status: ["draft", "posted"],
      kit_line_kind: ["header", "component"],
      kit_sell_mode: ["stocked", "explode"],
      loyalty_movement: ["earn", "redeem", "expire", "reverse"],
      payment_entry_status: ["draft", "posted", "cancelled"],
      payment_resolution_source_kind: [
        "card_terminal",
        "ecocash",
        "paynow",
        "contipay",
        "split_leg",
        "split_refund",
      ],
      payment_tender: [
        "cash",
        "bank",
        "contipay",
        "store_credit",
        "paynow",
        "ecocash",
        "card_terminal",
      ],
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
      pos_card_terminal_attempt_status: [
        "initiated",
        "approved",
        "declined",
        "cancelled",
        "unknown",
        "failed",
        "settled",
        "reversed",
      ],
      pos_card_terminal_operation: ["purchase", "refund", "reversal"],
      pos_core_return_resolution: [
        "cash_refund",
        "account_credit",
        "store_credit",
      ],
      pos_fulfillment_kind: [
        "branch_transfer",
        "alternate_pickup",
        "customer_collection",
        "backorder",
      ],
      pos_fulfillment_status: [
        "requested",
        "reserved",
        "awaiting_transfer_approval",
        "ready",
        "collected",
        "cancelled",
        "rejected",
      ],
      pos_quotation_status: [
        "draft",
        "issued",
        "sent",
        "converted",
        "cancelled",
        "expired",
      ],
      pos_return_case_status: ["draft", "posted", "cancelled"],
      pos_return_condition: [
        "sealed",
        "unopened",
        "opened",
        "damaged",
        "defective",
      ],
      pos_return_resolution: [
        "credit_note",
        "cash_refund",
        "store_credit",
        "replacement",
        "warranty",
      ],
      pos_scan_session_status: ["open", "claimed", "revoked", "expired"],
      pos_split_payment_leg_status: [
        "planned",
        "held",
        "pending",
        "captured",
        "failed",
        "unknown",
        "allocated",
        "refund_review",
        "refund_pending",
        "refunded",
        "cancelled",
      ],
      pos_split_payment_session_status: [
        "open",
        "partially_captured",
        "leg_pending",
        "fully_committed",
        "finalizing",
        "settled",
        "finalization_failed",
        "refund_review",
        "refund_pending",
        "refunded",
        "cancelled",
      ],
      pos_split_refund_status: [
        "review",
        "pending",
        "settled",
        "failed",
        "cancelled",
      ],
      pos_till_cash_movement_kind: [
        "cash_in",
        "cash_out",
        "petty_cash",
        "bank_drop",
        "cash_refund",
      ],
      pos_till_session_status: ["open", "variance_pending", "closed"],
      procurement_doc_status: [
        "draft",
        "submitted",
        "cancelled",
        "approved",
        "rejected",
      ],
      product_review_status: ["pending", "approved", "rejected"],
      receipt_channel: ["sms", "email", "whatsapp"],
      receipt_outbox_status: [
        "pending",
        "rendering",
        "sending",
        "sent",
        "failed",
        "cancelled",
      ],
      return_refund_status: [
        "pending",
        "processing",
        "completed",
        "failed",
        "cancelled",
      ],
      return_resolution_method: [
        "original_payment",
        "store_credit",
        "cash",
        "bank",
      ],
      salary_pay_type: ["hourly", "salary"],
      sales_doc_status: ["draft", "posted", "cancelled", "on_hold"],
      sales_doc_type: ["invoice", "credit_note"],
      sms_event_priority: ["low", "normal", "high"],
      sms_outbox_status: ["pending", "sending", "sent", "failed", "cancelled"],
      staff_ops_notification_kind: [
        "sales_prep",
        "driver_assigned",
        "approval_waiting",
        "morning_summary",
      ],
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
