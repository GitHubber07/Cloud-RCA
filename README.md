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

- **JDBC Connection Pooling**: Managed connection with Supabase PostgreSQL, featuring an automatic local **H2 database** fallback.
- **Workflow State Machine**: Extensible action-routing graph (`QueryAction`, `ScopeSwitchAction`, `MitigationAction`).
- **Interactive Web Dashboard**: Embedded HTTP server serving a rich visual interface to browse logs, workflows, and run evaluations.
- **Local FastText Embeddings**: Lightweight pure-Java vector centroid averaging with subword (n-gram) lookups.
- **Temporal Decay Math**: Implementation of time-decayed Euclidean distance similarity.
- **Hyperparameter Tuning**: Automated grid-search parameter sweep for optimizing retrieval performance.
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

### 2. Configure Environment (Optional)
If you want to use a Supabase (PostgreSQL) instance, export the credentials:
```bash
export SUPABASE_DB_URL="jdbc:postgresql://<host>:5432/postgres"
export SUPABASE_DB_USER="postgres"
export SUPABASE_DB_PASSWORD="your-supabase-password"
```

> [!NOTE]
> **Database Fallback:** If these environment variables are missing, the system will automatically fall back to a local embedded **H2 Database** (`h2_rca_db`), making it run out-of-the-box with zero configuration required.

To connect with live Llama 3 models:
```bash
export GROQ_API_KEY="your-groq-key"
```

### 3. Run Build & Tests
Verify the installation by running the test suite:
```bash
./mvnw test
```

### 4. Running the Application

You can execute the application in either **Interactive Web Server** mode or **CLI Simulation** mode.

#### A. Web Server Mode (Interactive Dashboard)
Run the application with no arguments to start the built-in HTTP server:
```bash
./mvnw exec:java
```
Or explicitly:
```bash
./mvnw exec:java -Dexec.args="--server"
```
Once started:
1. Open **`http://localhost:8080`** in your browser.
2. View historical and test incidents, telemetry logs, visualize workflow definitions, or click **"Run Simulation"** to regenerate mock datasets and run evaluations asynchronously.

#### B. CLI Simulation Mode
Execute evaluation directly from the terminal:
```bash
# Offline CLI simulation (populates DB with 30 history, 5 test incidents, runs evaluation)
./mvnw exec:java -Dexec.args="--init-db --num-history 30 --num-test 5"

# Live run (using Groq Llama 3 API)
./mvnw exec:java -Dexec.args="--init-db --num-history 30 --num-test 5 --live"
```

#### C. Hyperparameter Tuning Sweep
You can search for the optimal retrieval parameters (K neighbors and temporal decay alpha):
```bash
./mvnw exec:java -Dexec.args="--tune"
```
This runs a grid-search sweep across combinations of $K \in \{1, 3, 5\}$ and $\alpha \in \{0.0, 0.1, 0.3, 0.5, 0.8, 1.0\}$ to output micro and macro F1 scores and determine the optimal retrieval parameters.

#### D. Custom CLI Parameters
Configure retrieval settings dynamically:
```bash
# Set specific neighbor count (K) and decay rate (alpha)
./mvnw exec:java -Dexec.args="--neighbors 3 --alpha 0.5"
```
