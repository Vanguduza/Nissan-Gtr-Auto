"use client";

import {
  createContext,
  useContext,
  type ReactNode,
} from "react";
import type { StaffContext } from "@/lib/staff-auth";

const StaffAuthContext = createContext<StaffContext | null>(null);

export function StaffAuthProvider({
  value,
  children,
}: {
  value: StaffContext;
  children: ReactNode;
}) {
  return (
    <StaffAuthContext.Provider value={value}>
      {children}
    </StaffAuthContext.Provider>
  );
}

export function useStaffAuth(): StaffContext | null {
  return useContext(StaffAuthContext);
}
