export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
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
      pnc_categories: {
        Row: {
          category_name: string
          created_at: string
          pnc_code: string
          subcategory_name: string | null
        }
        Insert: {
          category_name: string
          created_at?: string
          pnc_code: string
          subcategory_name?: string | null
        }
        Update: {
          category_name?: string
          created_at?: string
          pnc_code?: string
          subcategory_name?: string | null
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
          requires_serial: boolean
        }
        Insert: {
          base_uom_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          oem_part_number: string
          requires_serial?: boolean
        }
        Update: {
          base_uom_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          oem_part_number?: string
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
          vin_prefix: string | null
        }
        Insert: {
          chassis_code: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant: string
          production_year?: number | null
          vin_prefix?: string | null
        }
        Update: {
          chassis_code?: string
          created_at?: string
          engine_code?: string | null
          id?: string
          model_variant?: string
          production_year?: number | null
          vin_prefix?: string | null
        }
        Relationships: []
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
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
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
      _assert_journal_balanced: {
        Args: { p_entry_id: string }
        Returns: undefined
      }
      _consume_fifo_batches: {
        Args: { p_item: string; p_qty_base: number; p_warehouse: string }
        Returns: undefined
      }
      _line_usd_equiv: {
        Args: {
          p_amount: number
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_rate: number
        }
        Returns: number
      }
      _require_warehouse_staff: { Args: never; Returns: undefined }
      approve_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
      assign_staff_role: {
        Args: {
          p_role: Database["public"]["Enums"]["staff_role"]
          p_user_id: string
        }
        Returns: undefined
      }
      build_qr_payload: {
        Args: {
          p_batch_code: string
          p_oem: string
          p_valuation: Database["public"]["Enums"]["valuation_method"]
        }
        Returns: string
      }
      clear_bank_matches: { Args: { p_match_ids: string[] }; Returns: number }
      convert_to_base_uom: {
        Args: { p_from_uom_id: string; p_qty: number; p_stock_item_id: string }
        Returns: number
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
      create_stock_transfer: {
        Args: {
          p_from_warehouse_id: string
          p_lines: Json
          p_notes: string
          p_to_warehouse_id: string
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
      has_staff_role: {
        Args: { roles: Database["public"]["Enums"]["staff_role"][] }
        Returns: boolean
      }
      is_period_locked: { Args: { p_date: string }; Returns: boolean }
      is_staff: { Args: never; Returns: boolean }
      lock_accounting_period: {
        Args: { p_period_id: string }
        Returns: undefined
      }
      next_series_value: { Args: { p_prefix: string }; Returns: string }
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
      post_return_to_quarantine: {
        Args: { p_from_warehouse_id: string; p_lines: Json; p_notes: string }
        Returns: string
      }
      post_stock_receipt: {
        Args: { p_lines: Json; p_notes: string; p_to_warehouse_id: string }
        Returns: string
      }
      reject_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
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
      reverse_journal: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
      }
      revoke_staff_role: {
        Args: {
          p_role: Database["public"]["Enums"]["staff_role"]
          p_user_id: string
        }
        Returns: undefined
      }
    }
    Enums: {
      account_type: "asset" | "liability" | "equity" | "income" | "expense"
      bank_recon_status: "open" | "matched" | "cleared"
      currency_code: "USD" | "ZIG"
      journal_status: "draft" | "posted"
      sms_event_priority: "low" | "normal" | "high"
      sms_outbox_status: "pending" | "sending" | "sent" | "failed" | "cancelled"
      staff_role:
        | "admin"
        | "finance"
        | "warehouse"
        | "sales"
        | "dispatcher"
        | "hr"
      stock_entry_status:
        | "draft"
        | "pending_approval"
        | "posted"
        | "cancelled"
        | "rejected"
      stock_entry_type: "receipt" | "transfer" | "issue"
      valuation_method: "FIFO" | "AVG"
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
      bank_recon_status: ["open", "matched", "cleared"],
      currency_code: ["USD", "ZIG"],
      journal_status: ["draft", "posted"],
      sms_event_priority: ["low", "normal", "high"],
      sms_outbox_status: ["pending", "sending", "sent", "failed", "cancelled"],
      staff_role: [
        "admin",
        "finance",
        "warehouse",
        "sales",
        "dispatcher",
        "hr",
      ],
      stock_entry_status: [
        "draft",
        "pending_approval",
        "posted",
        "cancelled",
        "rejected",
      ],
      stock_entry_type: ["receipt", "transfer", "issue"],
      valuation_method: ["FIFO", "AVG"],
    },
  },
} as const
