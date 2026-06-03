# Cloud-RCA: Automated Incident Root Cause Analysis

A Java-based simulation of the **RCACopilot** system, designed to automate Root Cause Analysis (RCA) for cloud service incidents. The system combines structured rule-based workflow handlers with semantic similarity matching and large language model (LLM) reasoning to diagnose system failures.

---

## Architecture Flow

1. **Incident Trigger**: When an alert is detected, the corresponding `IncidentHandler` is retrieved.
2. **Telemetry Collection**: The handler executes a series of queries (fetching exceptions, thread stacks, socket metrics, etc.) and aggregates the diagnostic data.
3. **Semantic Retrieval**: The aggregated text is converted into vector space using a local FastText embedding model. The system queries history for similar cases, applying a temporal decay factor to weight recent identical failures higher.
4. **LLM Prediction**: The target logs are summarized and passed alongside top historical demonstrations to Llama 3 (via Groq API) to predict the root cause category and explain the fault.

---

## Features

- **JDBC Connection Pooling**: Managed connection with Supabase PostgreSQL instance.
- **Workflow State Machine**: Extensible action-routing graph (`QueryAction`, `ScopeSwitchAction`, `MitigationAction`).
- **Local FastText Embeddings**: Lightweight pure-Java vector centroid averaging with subword (n-gram) lookups.
- **Temporal Decay Math**: Implementation of time-decayed Euclidean distance similarity.
- **Mock Data Generator**: Synthetic 1-year timeline dataset creator for testing accuracy and pipeline capacity.

---

## Prerequisites

- **Java 21** or higher.
- **Supabase** (PostgreSQL) database.
- **Groq API Key** (optional, for live Llama 3 predictions).

---

## Quick Start

### 1. Database Setup
Execute the SQL commands in `schema.sql` on your Supabase instance to create the necessary tables and indexes.

### 2. Configure Environment
Export your database and API credentials in your terminal:
```bash
export SUPABASE_DB_URL="jdbc:postgresql://<host>:5432/postgres"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="your-supabase-password"

# Optional (for Llama 3 predictions)
export GROQ_API_KEY="your-groq-key"
```

### 3. Run Build & Tests
Verify the installation by running the test suite:
```bash
./mvnw test
```

### 4. Execute the Simulation
To populate mock logs and run the evaluation pipeline:
```bash
# Offline run (uses mock templates)
./mvnw exec:java -Dexec.args="--init-db --num-history 30 --num-test 5"

# Live run (calls Groq Llama 3)
./mvnw exec:java -Dexec.args="--init-db --num-history 30 --num-test 5 --live"
```
