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
          created_at: string
          created_by: string | null
          currency: Database["public"]["Enums"]["currency_code"]
          customer_id: string | null
          document_number: string | null
          exchange_rate_applied: number
          id: string
          status: string
          updated_at: string
          warehouse_id: string
        }
        Insert: {
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
          id?: string
          status?: string
          updated_at?: string
          warehouse_id: string
        }
        Update: {
          created_at?: string
          created_by?: string | null
          currency?: Database["public"]["Enums"]["currency_code"]
          customer_id?: string | null
          document_number?: string | null
          exchange_rate_applied?: number
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
      sales_invoice_lines: {
        Row: {
          created_at: string
          id: string
          invoice_id: string
          is_core_charge: boolean
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
          resolution: Database["public"]["Enums"]["warranty_claim_resolution"] | null
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
          resolution?: Database["public"]["Enums"]["warranty_claim_resolution"] | null
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
          resolution?: Database["public"]["Enums"]["warranty_claim_resolution"] | null
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
      _recon_assert_single_currency: {
        Args: { p_reconciliation_id: string }
        Returns: undefined
      }
      _recon_compute_variance_value: {
        Args: { p_reconciliation_id: string }
        Returns: number
      }
      _recon_dual_auth_threshold: {
        Args: { p_currency: Database["public"]["Enums"]["currency_code"] }
        Returns: number
      }
      _require_sales_staff: { Args: never; Returns: undefined }
      _require_warehouse_staff: { Args: never; Returns: undefined }
      _reverse_journal_inventory: {
        Args: { p_description?: string; p_entry_id: string }
        Returns: string
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
      cancel_stock_reconciliation: {
        Args: { p_notes?: string; p_reconciliation_id: string }
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
      cancel_purchase_order: {
        Args: { p_notes?: string; p_purchase_order_id: string }
        Returns: string
      }
      checkout_pos_cart: { Args: { p_cart_id: string }; Returns: string }
      close_warranty_claim: { Args: { p_claim_id: string }; Returns: string }
      clear_bank_matches: { Args: { p_match_ids: string[] }; Returns: number }
      convert_to_base_uom: {
        Args: { p_from_uom_id: string; p_qty: number; p_stock_item_id: string }
        Returns: number
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
      create_goods_receipt: {
        Args: {
          p_lines: Json
          p_notes?: string
          p_purchase_order_id: string
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
      create_pos_cart: {
        Args: {
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_customer_id?: string
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
      current_supplier_id: { Args: never; Returns: string }
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
      enqueue_customer_receipts: {
        Args: { p_invoice_id: string }
        Returns: number
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
      link_supplier_profile: {
        Args: { p_profile_id: string; p_supplier_id: string }
        Returns: undefined
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
      post_return_credit_note: {
        Args: { p_invoice_id: string; p_lines: Json }
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
      post_stock_issue: {
        Args: { p_from_warehouse_id: string; p_lines: Json; p_notes: string }
        Returns: string
      }
      reject_stock_transfer: { Args: { p_entry_id: string }; Returns: string }
      reject_warranty_claim: {
        Args: { p_claim_id: string; p_reason?: string }
        Returns: string
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
      submit_purchase_order: {
        Args: { p_purchase_order_id: string }
        Returns: string
      }
      submit_stock_reconciliation: {
        Args: { p_reconciliation_id: string }
        Returns: string
      }
      upsert_stock_reconciliation_lines: {
        Args: { p_lines: Json; p_reconciliation_id: string }
        Returns: number
      }
    }
    Enums: {
      account_type: "asset" | "liability" | "equity" | "income" | "expense"
      bank_recon_status: "open" | "matched" | "cleared"
      currency_code: "USD" | "ZIG"
      journal_status: "draft" | "posted"
      receipt_channel: "sms" | "email" | "whatsapp"
      receipt_outbox_status:
        | "pending"
        | "rendering"
        | "sending"
        | "sent"
        | "failed"
        | "cancelled"
      sales_doc_status: "draft" | "posted" | "cancelled" | "on_hold"
      sales_doc_type: "invoice" | "credit_note"
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
      stock_reconciliation_scope: "full" | "partial"
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
      bank_recon_status: ["open", "matched", "cleared"],
      currency_code: ["USD", "ZIG"],
      journal_status: ["draft", "posted"],
      receipt_channel: ["sms", "email", "whatsapp"],
      receipt_outbox_status: [
        "pending",
        "rendering",
        "sending",
        "sent",
        "failed",
        "cancelled",
      ],
      sales_doc_status: ["draft", "posted", "cancelled", "on_hold"],
      sales_doc_type: ["invoice", "credit_note"],
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
      stock_reconciliation_scope: ["full", "partial"],
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
