# Order Transaction Reconciliation System (`order-trxn-recon`)

A Spring Boot backend application designed to ingest store order data (`orders.csv`) and payment processor transaction data (`payments.csv`), deterministically match transactions, identify data anomalies and discrepancies, and report financial risks.

---

## Table of Contents

1. [Overview & Tech Stack](#overview--tech-stack)
2. [Local Setup & How to Run](#local-setup--how-to-run)
3. [System Architecture](#system-architecture)
4. [Reconciliation Logic & Business Rules](#reconciliation-logic--business-rules)
5. [Data Findings & Business Impact Analysis](#data-findings--business-impact-analysis)
6. [API Reference](#api-reference)

---

## Overview & Tech Stack

An online merchant operates two systems that must agree:
* **Store Order System (`orders.csv`)**: What the store believes it sold.
* **Payment Processor (`payments.csv`)**: What was actually charged, refunded, fee-deducted, or settled.

This application provides a **deterministic and repeatable** reconciliation engine that normalizes raw data, isolates ingestion errors, reconciles clean records, persists findings into a database, and exposes paginated/searchable endpoints and CSV reports.

### Tech Stack
* **Java 21**
* **Spring Boot 3 / 4** (Web, Data JPA, Security, DevTools)
* **Spring Security & JWT** (HMAC-SHA Stateless Authentication)
* **H2 Database / JPA Persistence**
* **OpenCSV** (CSV parsing & validation)
* **Lombok & Maven**

---

## Local Setup & How to Run

### Prerequisites
* **Java Development Kit (JDK) 21+**
* **Maven 3.8+** (or use included `./mvnw` wrapper)

### 1. Clone & Build
```bash
git clone <repository-url>
cd order-trxn-recon

# Compile project
./mvnw clean compile
```

### 2. Run Tests
```bash
./mvnw test
```

### 3. Start Application
```bash
./mvnw spring-boot:run
```
The backend server will launch at `http://localhost:8080`.

---

## System Architecture

The application is structured into decoupled layers to ensure performance, maintainability, and clean separation of concerns:

```
src/main/java/com/projects/ordertrxnrecon/
├── controller/         # REST API Endpoints (Auth, Order, Payment, Reconciliation)
├── dto/                # Data Transfer Objects (Requests, Responses, Summaries, Paginated DTOs)
├── entity/             # JPA Entities (User, Order, Payment, ReconciliationRecord, TokenBlacklist)
├── repository/         # Spring Data JPA Repositories & Dynamic Specifications
├── security/           # JWT Authentication Filters & Spring Security Configuration
└── service/            # Business Services (Auth, CsvParser, Upload, Reconciliation Engine)
```

### Decoupled Processing & Persistence Model
1. **Data Ingestion (`UploadService` + `CsvParserService`)**:
   * Accepts uploaded CSV files (`orders.csv` and `payments.csv`).
   * Validates required fields and numerical formats.
   * Performs duplicate primary key checks (`order_id` / `transaction_ref`) within uploaded files.
   * Assigns `rowStatus = 'VALID'` or `rowStatus = 'INVALID'` per row.
   * Clears stale stored reconciliation records for the user.

2. **Reconciliation Engine (`ReconciliationService.processAndSaveReconciliation`)**:
   * Executed via `GET /api/reconciliation` or `POST /api/reconciliation`.
   * Normalizes valid order IDs and payment references (trimming whitespace & converting to uppercase).
   * Executes a deterministic 10-category matching pipeline.
   * Calculates gateway fees (`fee`) and net settled amounts (`netSettled`).
   * Replaces and persists reconciliation results into the `reconciliation_records` database table.

3. **Read-Only Reporting & Export**:
   * `GET /api/reconciliation/summary`: Reads stored database records to compute headline metrics and category breakdown without re-running matching logic.
   * `GET /api/reconciliation/discrepancies`: Serves paginated discrepancy records directly from the database using JPA Specifications with full search, filter, and sorting support.
   * `GET /api/reconciliation/export`: Streams full CSV reports (`reconciliation_report.csv`) from stored database records.

---

## Reconciliation Logic & Business Rules

### Matching & Normalization Rules
1. **Reference Normalization**: Raw order references and order IDs are stripped of leading/trailing whitespace and converted to uppercase (e.g., `" ord-1801 "` $\rightarrow$ `"ORD-1801"`).
2. **Duplicate Ingestion Handling**: Primary key duplicates in uploaded source files (such as duplicate `ORD-1004` rows) are marked `rowStatus = 'INVALID'` during ingestion and excluded from matching.
3. **Multi-Transaction Aggregation**: Multiple transactions linked to a single order reference are aggregated by type:
   $$\text{netPaid} = \sum \text{charges} - \sum \text{refunds}$$
   $$\text{netSettled} = \text{netPaid} - \sum \text{gatewayFees}$$

### Tolerances & Rules
* **Rounding Tolerance Threshold ($\le \$0.05$)**: Minor cent-level differences between order net amount and payment charged amount ($\le \$0.05$) are classified as `ROUNDING_DIFFERENCE` with `LOW` severity.
* **Major Amount Mismatches ($> \$0.05$)**: Differences greater than $\$0.05$ are classified as `AMOUNT_MISMATCH` with `HIGH` severity.

### Discrepancy Classification Pipeline

| Discrepancy Category | Severity | Matching Criteria & Rule |
| :--- | :--- | :--- |
| **`MATCHED`** | `NONE` | Order and Payment match perfectly in status, currency, and net amount. |
| **`ROUNDING_DIFFERENCE`** | `LOW` | Net amount difference is $\le \$0.05$. |
| **`AMOUNT_MISMATCH`** | `HIGH` | Net amount difference is $> \$0.05$. |
| **`UNPAID_ORDER`** | `CRITICAL` | Order status is `completed`, but zero payment transactions exist in gateway. |
| **`ORPHAN_PAYMENT`** | `HIGH` | Payment settled in gateway, but no matching order exists in store system. |
| **`DOUBLE_CHARGED`** | `HIGH` | Order has multiple `charge` payment transactions recorded. |
| **`CURRENCY_MISMATCH`** | `HIGH` | Order currency differs from payment transaction currency (e.g. USD vs EUR). |
| **`FULFILLED_UNSETTLED`** | `HIGH` | Order status is `completed`, but payment transaction status is `failed` or `pending`. |
| **`CANCELLED_CHARGED`** | `HIGH` | Order status is `cancelled`, but payment was charged & settled without refund. |
| **`REFUND_MISMATCH`** | `HIGH` | Order status is `completed` but gateway issued refund, OR order is `refunded` but gateway refund was incomplete. |

---

## Data Findings & Business Impact Analysis

Reconciliation analysis on the provided [orders.csv](file:///Users/purua/dev/projects/order-trxn-recon/orders.csv) (185 rows) and [payments.csv](file:///Users/purua/dev/projects/order-trxn-recon/payments.csv) (187 rows) revealed the following headline figures and real-world messiness:

### Headline Financial Figures
* **Total Valid Orders**: 184 orders (**$42,296.99**)
* **Total Valid Payment Charges**: 185 charges (**$42,500.38**)
* **Total Gateway Processing Fees**: **$1,287.96**
* **Total Net Settled to Bank**: **$40,993.42**
* **Total Value Reconciled**: **$39,963.28** (165 matched + 3 rounding diffs)
* **Total Value in Dispute**: **$2,973.36**
* **Total Money at Risk**: **$2,178.47**

### Specific Findings & Business Impact

1. **Unpaid Orders / Fulfilled Without Payment ($392.35 at risk)**:
   * **Orders**: `ORD-1201` ($94.87), `ORD-1202` ($80.83), `ORD-1203` ($59.52), `ORD-1204` ($157.13).
   * **Finding**: Marked `completed` in store database, but zero payment transactions exist in the payment processor.
   * **Business Impact**: Direct revenue loss. Goods were shipped without collecting payment.

2. **Double Charged Customers ($248.58 overcharged)**:
   * **Orders**: `ORD-1501` (charged 2x $119.84), `ORD-1502` (charged 2x $128.74).
   * **Finding**: Payment processor executed duplicate charge transactions for the same order reference.
   * **Business Impact**: Severe customer dissatisfaction, brand damage, and immediate risk of chargeback penalties.

3. **Orphan Payments ($308.00 captured)**:
   * **Payments**: `TXN700161` (`ORD-1301` - $79.51), `TXN700162` (`ORD-1302` - $78.98), `TXN700163` (`ORD-1303` - $149.51).
   * **Finding**: Money was charged and settled in the gateway, but no order record exists in the store system.
   * **Business Impact**: Unallocated funds, tax/accounting liability, and unfulfilled customer expectations.

4. **Fulfilled on Failed / Pending Payments ($377.00 at risk)**:
   * **Orders**: `ORD-2001` (payment `TXN700183` status `failed` - $310.00), `ORD-2002` (payment `TXN700184` status `pending` - $67.00).
   * **Finding**: Store fulfilled orders while gateway payment failed or remained pending.
   * **Business Impact**: Capital loss from fulfilling order before securing payment confirmation.

5. **Cancelled Order Charged Without Refund ($175.00 at risk)**:
   * **Order**: `ORD-1701` ($175.00).
   * **Finding**: Order was cancelled in store, but gateway charged $175.00 and never issued a refund.
   * **Business Impact**: Customer overcharge for cancelled purchase.

6. **Refund State Mismatches ($219.00 at risk)**:
   * **`ORD-1703`**: Order marked `completed` in store, but gateway issued a $99.00 refund (`TXN700177`).
   * **`ORD-1702`**: Order marked `refunded` ($240.00) in store, but gateway only issued a partial refund of $120.00 (`TXN700175`).
   * **Business Impact**: Unsynced order statuses and refund shortfalls between store and gateway.

7. **Currency Processing Mismatches**:
   * `ORD-1601` (Store: USD 210.00 vs Gateway: EUR 210.00), `ORD-1602` (Store: EUR 145.00 vs Gateway: USD 145.00).
   * **Business Impact**: Foreign exchange gain/loss accounting discrepancies.

8. **Source File Duplicates & Formatting Messiness**:
   * Duplicate row `ORD-1004` in `orders.csv`.
   * Whitespace and lowercase references in `payments.csv` (`" ord-1801 "`, `"ord-1802"`).
   * Inconsistent date string formats (`YYYY-MM-DD HH:MM:SS` vs `DD/MM/YYYY HH:MM`).

---

## API Reference

All endpoints except authentication require the `Authorization: Bearer <token>` header.

### Authentication
* `POST /api/auth/signup` — Register a new account.
* `POST /api/auth/login` — Authenticate and receive JWT token.

### Data Ingestion
* `POST /api/orders/upload` — Upload `orders.csv` (`multipart/form-data`).
* `POST /api/payments/upload` — Upload `payments.csv` (`multipart/form-data`).

### Reconciliation Endpoints
* **`GET /api/reconciliation`** / **`POST /api/reconciliation`**
  * Processes reconciliation matching logic and stores results in `reconciliation_records` DB table.
  * Returns summary metrics DTO.
* **`GET /api/reconciliation/summary`**
  * Reads headline metrics, totals, and category breakdown directly from the DB.
* **`GET /api/reconciliation/discrepancies`**
  * Reads paginated discrepancy records directly from DB.
  * **Query Params**: `page` (default `0`), `size` (default `20`), `search`, `type`, `severity`, `sortBy`, `sortDir`.
* **`GET /api/reconciliation/export`**
  * Streams CSV download (`reconciliation_report.csv`) of stored records matching active filters.
