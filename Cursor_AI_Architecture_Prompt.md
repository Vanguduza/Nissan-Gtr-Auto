# Cursor AI Implementation Prompt: Autonomous ERP Intelligence Layer

**System Context & Tech Stack:**
Act as a senior architectural engineer specializing in event-driven AI systems. We are integrating a fully autonomous, zero-touch AI layer into an existing multi-platform ERP. The current architecture utilizes Kotlin and Jetpack Compose for the client interfaces, with Supabase (PostgreSQL) as the primary backend data pipeline. 

**Core Directive:**
Design and implement three autonomous AI-driven modules (CRM, Finance, Stores) using Supabase Edge Functions, `pg_cron` for scheduling, and structured LLM API calls. The system must operate completely headlessly with zero manual human-in-the-loop approvals required for execution.

### 1. Autonomous CRM & Promotional Engine (Zero-Touch)
**Objective:** Deploy an automated pipeline that sends highly personalized automotive kits to clients based on seasonal shifts and buying frequency.
**Implementation Steps:**
- Create a scheduled Supabase Edge Function (via `pg_cron`) that runs daily to identify clients where `current_date - last_purchase_date > threshold`.
- Implement a database join to cross-reference the client's saved garage vehicles with compatible active SKUs in the parts catalog.
- Format a strict JSON payload containing the client name, vehicle model, and compatible SKUs.
- Pass the payload to the LLM API to generate a personalized, conversational promotional SMS/Email.
- Dispatch the message via a messaging gateway (e.g., Twilio/Resend) and autonomously update the `last_promotional_message_date` in the database to prevent duplicate outreach.

### 2. Financial Management: Agentic Performance Reviews
**Objective:** Generate dynamic, on-demand, and scheduled financial performance narratives.
**Implementation Steps:**
- Implement a secure, read-only Text-to-SQL architecture (e.g., Vanna AI logic) within a designated microservice or Edge Function.
- Create a routine that aggregates revenue, expenses, and cash flow metrics over defined periods (monthly/quarterly/on-demand).
- Pass these raw SQL outputs to the LLM with a system prompt instructing it to draft a structured financial narrative highlighting net profit margins and top expense categories.
- Expose this output via a REST endpoint to be cleanly parsed and displayed on the Kotlin/Jetpack Compose financial dashboard.

### 3. Stores Module: Inventory & Performance Forecasting
**Objective:** Autonomously identify fast/slow-moving stock and generate seasonal restocking directives.
**Implementation Steps:**
- Write a scheduled database script to perform ABC classification on the entire catalog based on sales volume and revenue generation.
- Integrate time-series forecasting logic (e.g., StatsForecast/Prophet via a Python backend) for Tier A (fast-moving) products to project seasonal demand spikes.
- Pass the forecasted metrics, current stock levels, and Tier C (slow-moving) product lists to the LLM.
- Instruct the LLM to output actionable, structured JSON directives (e.g., specific restocking quantities for fast movers, clearance bundle strategies for slow movers).
- Sync these generated insights directly into the ERP stores dashboard to drive stocking decisions.

**Security & Execution Constraints:**
- Ensure all LLM responses are strictly typed (using JSON schemas) where applicable to prevent hallucinated data from breaking the Kotlin frontend UI.
- Apply strict Row-Level Security (RLS) and dedicated read-only database roles for all financial and inventory queries executed by the AI modules.
- Provide the exact Supabase migration scripts for the `pg_cron` scheduling and the TypeScript code for the Edge Functions orchestration.
