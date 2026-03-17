# GovAI Knowledge Base — Vector Database System
### Complete 20-Year India Governance Intelligence Dataset + Search Engine

---

## 📦 What's Included

```
govai_knowledge_base/
├── data/
│   ├── tier1_governance/          ← Internal Gov meetings (196 records)
│   ├── tier1_economy/             ← India GDP, CPI, Repo, GST, Budget (541 records)
│   ├── tier1_policy/              ← Bills, Schemes, Budget Highlights (120 records)
│   ├── tier1_social/              ← Poverty, Health, HDI, Education, Crime
│   ├── tier2_global/              ← USA, UK, Oil, Gold, USD/INR, Trade/FDI
│   ├── tier3_sentiment/           ← Public sentiment events
│   └── tier3_historical/          ← Rate-inflation patterns, forecasts
├── vectordb/
│   ├── engine.py                  ← Vector DB engine (TF-IDF + Cosine)
│   └── govai.db                   ← Pre-built binary index (1033 docs)
└── scripts/
    ├── gen_tier1_economy.py
    ├── gen_tier1_policy_governance.py
    ├── gen_social_global.py
    ├── gen_tier3.py
    └── demo_queries.py
```

---

## 📊 Dataset Summary

| Bucket | Data Type | Records | Time Span |
|--------|-----------|---------|-----------|
| **GOVERNANCE** | Meeting transcripts, decisions, action items | 196 | 2004–2024 |
| **ECONOMY** | GDP quarterly, CPI monthly, Repo rate, GST, Fiscal deficit, Budget | 541 | 2004–2024 |
| **POLICY** | Bills/Acts passed, Government schemes, Budget highlights | 120 | 2004–2024 |
| **GLOBAL_SOCIAL** | Poverty, HDI, Health, Education, Crime, USA/UK data, Commodities, FDI, Sentiment, Patterns | 176 | 2004–2024 |
| **TOTAL** | | **1,033** | **20 years** |

---

## 🚀 Quick Start

### Option 1: Use Pre-built DB

```python
import sys
sys.path.insert(0, "/path/to/govai_knowledge_base/vectordb")
from engine import GovAIVectorDB, GovAIQueryEngine

# Load pre-built index
engine = GovAIQueryEngine()

# Ask questions
print(engine.query("Which decisions are pending in rural development?"))
print(engine.query("India CPI inflation trend 2022 2023"))
print(engine.query("Farm laws repeal status"))
print(engine.query("Repo rate history during COVID"))
```

### Option 2: Rebuild from Scratch

```python
from engine import GovAIVectorDB
db = GovAIVectorDB()
db.build_all()   # reads all JSON from /data/
db.save()        # saves to govai.db
```

---

## 🔍 Search API

```python
from engine import GovAIVectorDB

db = GovAIVectorDB()
db.load()

# Basic search (auto-routes to best bucket)
results = db.search("What was decided about rural infrastructure?", n_results=5)

# Force a specific bucket
results = db.search("GDP growth 2020", n_results=3, bucket="economy")

# Filter by year range
results = db.search("repo rate cut", n_results=5, year_range=(2008, 2010))

# Filter by field value
results = db.search("infrastructure pending", n_results=5,
                    filters={"lead_ministry": "Ministry of Rural Development"})

# Each result contains:
for r in results:
    print(r["score"])    # relevance score 0-1
    print(r["bucket"])   # which knowledge bucket
    print(r["doc"])      # full document dict
    print(r["doc"]["text"])  # human-readable summary
```

---

## 🗂️ 4 Knowledge Buckets

### 1. GOVERNANCE (Internal)
Simulated meeting data representing real governance workflows.

**Fields:** `meeting_id`, `date`, `topic`, `lead_ministry`, `decisions`, `action_items`, `budget_impact`, `deadline`, `implementation_status`, `completion_pct`, `risk_level`

**Sample queries:**
- "Which decisions are pending?"
- "What was decided about rural infra in 2024?"
- "Which ministry has highest delays?"
- "Show all High risk items"

### 2. ECONOMY (India)
**Datasets:**
- `india_gdp_quarterly.json` — 84 quarterly records, 2004-2024
- `india_cpi_monthly.json` — 252 monthly records, 2004-2024
- `rbi_repo_rate_history.json` — 52 rate decisions with rationale
- `india_fiscal_deficit_annual.json` — 21 annual records
- `india_gst_monthly.json` — 90 monthly records, Jul 2017-Dec 2024
- `india_budget_sector_allocation.json` — 21 annual sector budgets
- `india_unemployment_annual.json` — 21 annual records

### 3. POLICY (India)
**Datasets:**
- `india_major_bills.json` — 52 key bills/acts 2004-2024
- `india_government_schemes.json` — 47 major schemes with status & budget
- `india_budget_highlights.json` — 21 annual budget summaries

### 4. GLOBAL & SOCIAL
**Datasets:**
- `india_poverty_data.json` — Headcount, rural/urban split
- `india_education_data.json` — Literacy, GER, spending
- `india_health_data.json` — IMR, MMR, life expectancy
- `india_hdi_data.json` — UNDP HDI ranking
- `india_crime_data.json` — NCRB annual statistics
- `usa_economic_data.json` — GDP, unemployment, S&P, Fed rate
- `uk_economic_data.json` — GDP, FTSE, BoE rate, inflation
- `global_commodity_rates.json` — Brent, Gold, USD/INR, Silver
- `india_trade_fdi_data.json` — FDI, exports, trade balance, FII
- `india_public_sentiment.json` — 16 major event sentiment records
- `rate_inflation_historical_patterns.json` — 5 RBI cycle patterns
- `india_forecast_scenarios.json` — 2025/2030 projections

---

## 🔄 Upgrade to ChromaDB (Production)

```python
# Drop-in replacement when you have internet/ChromaDB installed:
import chromadb
from sentence_transformers import SentenceTransformer

client = chromadb.PersistentClient(path="./govai_chroma")
model = SentenceTransformer('all-MiniLM-L6-v2')

for bucket_name in ["governance", "economy", "policy", "global_social"]:
    collection = client.get_or_create_collection(bucket_name)
    # Load your JSON docs
    for doc in load_docs(bucket_name):
        collection.add(
            ids=[doc["id"]],
            embeddings=[model.encode(doc["text"]).tolist()],
            metadatas=[{k: v for k, v in doc.items() if k != "text"}],
            documents=[doc["text"]]
        )

# Query
results = collection.query(
    query_embeddings=[model.encode("fiscal deficit 2020").tolist()],
    n_results=5
)
```

---

## 📡 Connect to LLM (RAG Pattern)

```python
# RAG integration example
from engine import GovAIVectorDB

db = GovAIVectorDB()
db.load()

def answer_with_context(user_question: str, llm_fn) -> str:
    # Retrieve relevant docs
    results = db.search(user_question, n_results=5)
    
    # Build context
    context = "\n\n".join([
        f"[Source: {r['bucket'].upper()} | {r['doc'].get('type')}]\n{r['doc']['text']}"
        for r in results
    ])
    
    # Send to LLM
    prompt = f"""You are a Government Policy Intelligence AI.
    
Answer the following question using ONLY the provided context.
If the context doesn't contain the answer, say so.

Context:
{context}

Question: {user_question}

Answer:"""
    
    return llm_fn(prompt)
```

---

## 🔧 Add More Data

Each JSON file is just a list of documents. Add any new record by appending:

```python
new_record = {
    "id": "GDP_Q_2025_1",
    "type": "India_GDP_Quarterly",
    "year": 2025,
    "quarter": "Q1 (Apr-Jun)",
    "gdp_lakh_crore": 54.1,
    "yoy_growth_pct": 6.9,
    "text": "India GDP 2025 Q1: ₹54.1 lakh crore, growth 6.9% YoY..."
}
# Add to data file, then rebuild:
db.build_index("economy", BUCKET_PATHS["economy"])
db.save()
```

---

## ✅ Verified Query Examples

| Query | Top Result Score | Bucket |
|-------|-----------------|--------|
| "Which decisions are pending?" | 0.072 | GOVERNANCE |
| "rural infrastructure decisions" | 0.309 | GOVERNANCE |
| "GDP growth during COVID 2020" | 0.400 | ECONOMY |
| "repo rate emergency cut crisis" | 0.490 | ECONOMY |
| "GST collection record high" | 0.740 | ECONOMY |
| "farm laws repeal status" | 0.489 | POLICY |
| "Ayushman Bharat beneficiaries" | 0.691 | POLICY |
| "Digital India BharatNet" | 0.422 | POLICY |
| "oil price India 2022 deficit" | 0.362 | GLOBAL |

---

*Data sourced from: MOSPI, RBI, Ministry of Finance, PRS Legislative Research, NCRB, UNDP, World Bank, PIB, Union Budget Documents*
*All figures are reference data — verify with primary sources before policy use*
