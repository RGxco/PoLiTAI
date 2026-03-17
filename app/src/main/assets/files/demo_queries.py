"""
GOVAI VECTOR DB — DEMO QUERY TEST
Tests all the key query types from the spec
"""
import sys
sys.path.insert(0, "/home/claude/govai_knowledge_base/vectordb")
from engine import GovAIVectorDB

db = GovAIVectorDB()
db.load()

SEPARATOR = "\n" + "="*70 + "\n"

def run_query(label, query, bucket=None, n=3, year_range=None, filters=None):
    print(SEPARATOR)
    print(f"❓ QUERY: {label}")
    print(f"   '{query}'")
    results = db.search(query, n_results=n, bucket=bucket, year_range=year_range, filters=filters)
    for i, r in enumerate(results, 1):
        doc = r["doc"]
        print(f"\n  [{i}] Score: {r['score']:.3f} | {r['bucket'].upper()} | {doc.get('type','?')}")
        print(f"      {doc.get('text','')[:180]}...")
    if not results:
        print("  ⚠ No results found")

# ── GOVERNANCE QUERIES ──
run_query(
    "Which decisions are pending?",
    "pending decisions action items not yet complete",
    bucket="governance", n=4
)

run_query(
    "Rural infra decisions Jan 2024",
    "rural infrastructure development decisions January 2024",
    bucket="governance"
)

run_query(
    "Which ministry has highest delays?",
    "ministry delays incomplete deadline overdue implementation status",
    bucket="governance", n=5
)

# ── ECONOMY QUERIES ──
run_query(
    "India CPI Inflation Jan 2026 trend",
    "India CPI inflation January 2026 trend rising falling",
    bucket="economy"
)

run_query(
    "GDP growth during COVID 2020",
    "GDP growth rate 2020 COVID contraction India",
    bucket="economy"
)

run_query(
    "Repo rate history during crisis periods",
    "RBI repo rate emergency cut crisis 2008 2020",
    bucket="economy", n=4
)

run_query(
    "GST collections above 1.5 lakh crore",
    "GST collection above 150 thousand crore record high",
    bucket="economy", n=4
)

run_query(
    "Fiscal deficit highest year",
    "fiscal deficit highest percent GDP worst year",
    bucket="economy", n=3
)

# ── POLICY QUERIES ──
run_query(
    "Status of National Education Policy implementation",
    "National Education Policy NEP 2020 implementation status",
    bucket="policy"
)

run_query(
    "Farm laws status",
    "farm laws repeal status agriculture bills 2020",
    bucket="policy"
)

run_query(
    "Ayushman Bharat scheme beneficiaries",
    "Ayushman Bharat health insurance PM-JAY beneficiaries cards",
    bucket="policy"
)

run_query(
    "Digital India and BharatNet",
    "Digital India BharatNet optical fibre rural connectivity scheme",
    bucket="policy"
)

# ── GLOBAL QUERIES ──
run_query(
    "Oil price impact India 2022",
    "Brent crude oil price 2022 India trade deficit",
    bucket="global_social", n=3
)

run_query(
    "India poverty reduction progress",
    "India poverty reduction headcount poor population progress",
    bucket="global_social", n=3
)

run_query(
    "Public sentiment demonetization",
    "public sentiment opinion demonetization 2016",
    bucket="global_social"
)

run_query(
    "USA Federal Reserve rate hikes vs India",
    "Federal Reserve rate hike USA India repo rate comparison 2022",
    n=4
)

# ── PREDICTION MODE ──
run_query(
    "Historical: Do rate hikes reduce inflation?",
    "historically rate hikes RBI reduced inflation how many months",
    bucket="global_social", n=3
)

run_query(
    "India GDP 2025 forecast",
    "India GDP forecast 2025 growth projection",
    bucket="global_social"
)

# ── YEAR RANGE FILTER ──
run_query(
    "India economy 2008 financial crisis",
    "India economy recession global financial crisis",
    year_range=(2007, 2010), n=4
)

print(SEPARATOR)
print("✅ All demo queries completed!")
print(f"\n📊 Total DB docs: {sum(len(b['docs']) for b in db.buckets.values())}")
for name, b in db.buckets.items():
    print(f"   {name}: {len(b['docs'])} docs")
