/**
 * Placeholder until `pnpm db:types` generates from local Supabase.
 */
export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[];

export interface Database {
  public: {
    Tables: {
      chart_of_accounts: {
        Row: {
          code: string;
          name: string;
          account_type: string;
          is_active: boolean;
          created_at: string;
        };
        Insert: {
          code: string;
          name: string;
          account_type: string;
          is_active?: boolean;
          created_at?: string;
        };
        Update: {
          name?: string;
          account_type?: string;
          is_active?: boolean;
        };
        Relationships: [];
      };
    };
    Views: Record<string, never>;
    Functions: Record<string, never>;
    Enums: {
      currency_code: "USD" | "ZIG";
      account_type: "asset" | "liability" | "equity" | "income" | "expense";
      valuation_method: "FIFO" | "AVG";
    };
    CompositeTypes: Record<string, never>;
  };
}
