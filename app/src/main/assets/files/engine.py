"""
GOVAI VECTOR DATABASE ENGINE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Custom TF-IDF + Cosine Similarity vector search engine
Architecture mirrors ChromaDB/Pinecone interface for easy swap-in later
4 Knowledge Buckets: governance | economy | policy | global_social
"""

import json
import os
import math
import pickle
import re
import time
from collections import defaultdict, Counter
from typing import List, Dict, Optional, Tuple

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
BASE_DIR = "/home/claude/govai_knowledge_base"
DB_PATH = f"{BASE_DIR}/vectordb/govai.db"
os.makedirs(f"{BASE_DIR}/vectordb", exist_ok=True)

BUCKET_PATHS = {
    "governance":  [f"{BASE_DIR}/data/tier1_governance"],
    "economy":     [f"{BASE_DIR}/data/tier1_economy"],
    "policy":      [f"{BASE_DIR}/data/tier1_policy"],
    "global_social": [
        f"{BASE_DIR}/data/tier1_social",
        f"{BASE_DIR}/data/tier2_global",
        f"{BASE_DIR}/data/tier3_sentiment",
        f"{BASE_DIR}/data/tier3_historical",
    ],
}

# Query → Bucket routing keywords
BUCKET_ROUTES = {
    "governance":  ["meeting", "decision", "ministry", "action item", "status", "deadline", "pending", "approved", "committee", "implementation", "progress", "complete"],
    "economy":     ["gdp", "inflation", "cpi", "repo rate", "rbi", "fiscal", "gst", "budget allocation", "unemployment", "growth rate", "economy", "deficit", "tax", "interest rate"],
    "policy":      ["bill", "act", "scheme", "policy", "law", "programme", "yojana", "legislation", "nep", "nep 2020", "rera", "ibc", "reform", "amendment", "launch"],
    "global_social": ["usa", "uk", "oil", "gold", "usd", "inr", "exchange", "ftse", "s&p", "nasdaq", "poverty", "hdi", "literacy", "health", "crime", "sentiment", "fdi", "trade", "global", "forecast"],
}


# ─────────────────────────────────────────────────────────────────────────────
# TEXT PROCESSING
# ─────────────────────────────────────────────────────────────────────────────
STOP_WORDS = set([
    "a","an","the","is","are","was","were","be","been","being","have","has","had",
    "do","does","did","will","would","shall","should","may","might","can","could",
    "to","of","in","on","at","by","for","with","about","from","as","into","through",
    "and","or","but","not","if","this","that","these","those","it","its","we","our",
    "they","their","he","she","i","you","we","all","any","each","most","more","than",
    "also","both","during","after","before","above","below","between","while","however",
])

def tokenize(text: str) -> List[str]:
    text = text.lower()
    text = re.sub(r'[₹$%,./\-–—()""\'\']+', ' ', text)
    tokens = re.findall(r'\b[a-z]{2,}\b|\b\d{4}\b', text)
    return [t for t in tokens if t not in STOP_WORDS]

def compute_tfidf(documents: List[str]) -> Tuple[List[Dict], Dict]:
    """Compute TF-IDF vectors for all documents"""
    N = len(documents)
    tokenized = [tokenize(d) for d in documents]
    
    # Document frequency
    df = defaultdict(int)
    for tokens in tokenized:
        for term in set(tokens):
            df[term] += 1
    
    # Build vocabulary (top 15000 terms by df)
    vocab = sorted(df.keys(), key=lambda x: -df[x])[:15000]
    vocab_idx = {term: i for i, term in enumerate(vocab)}
    
    # TF-IDF vectors (sparse as dict)
    vectors = []
    for tokens in tokenized:
        tf = Counter(tokens)
        total = len(tokens) or 1
        vec = {}
        for term, count in tf.items():
            if term in vocab_idx:
                tfidf = (count / total) * math.log((N + 1) / (df[term] + 1))
                vec[vocab_idx[term]] = tfidf
        # Normalize
        norm = math.sqrt(sum(v*v for v in vec.values())) or 1.0
        vec = {k: v/norm for k, v in vec.items()}
        vectors.append(vec)
    
    return vectors, vocab_idx

def cosine_similarity(vec1: Dict, vec2: Dict) -> float:
    """Dot product of two normalized sparse vectors"""
    if not vec1 or not vec2:
        return 0.0
    return sum(vec1.get(k, 0) * v for k, v in vec2.items())

def query_vector(query: str, vocab_idx: Dict) -> Dict:
    """Vectorize a query using learned vocabulary"""
    tokens = tokenize(query)
    tf = Counter(tokens)
    total = len(tokens) or 1
    vec = {}
    for term, count in tf.items():
        if term in vocab_idx:
            vec[vocab_idx[term]] = count / total
    norm = math.sqrt(sum(v*v for v in vec.values())) or 1.0
    return {k: v/norm for k, v in vec.items()}


# ─────────────────────────────────────────────────────────────────────────────
# VECTOR DATABASE CLASS
# ─────────────────────────────────────────────────────────────────────────────
class GovAIVectorDB:
    """
    4-bucket vector database for GovAI knowledge base.
    Interface mirrors ChromaDB for easy future migration.
    """
    
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        self.buckets = {}  # bucket_name -> {docs, vectors, vocab_idx}
    
    def load_json_files(self, paths: List[str]) -> List[Dict]:
        """Load all JSON files from given directory paths"""
        all_docs = []
        for path in paths:
            if not os.path.exists(path):
                continue
            for fname in sorted(os.listdir(path)):
                if fname.endswith(".json"):
                    with open(os.path.join(path, fname)) as f:
                        data = json.load(f)
                    if isinstance(data, list):
                        all_docs.extend(data)
                    else:
                        all_docs.append(data)
        return all_docs
    
    def build_index(self, bucket_name: str, paths: List[str]):
        """Build TF-IDF index for a bucket"""
        print(f"  Building [{bucket_name}] index...", end="", flush=True)
        docs = self.load_json_files(paths)
        if not docs:
            print(f" ⚠ No docs found")
            return
        
        texts = [d.get("text", d.get("id", str(d))) for d in docs]
        vectors, vocab_idx = compute_tfidf(texts)
        
        self.buckets[bucket_name] = {
            "docs": docs,
            "texts": texts,
            "vectors": vectors,
            "vocab_idx": vocab_idx,
        }
        print(f" ✓ {len(docs)} docs indexed")
    
    def build_all(self):
        """Build all 4 knowledge buckets"""
        print("\n🔨 Building GovAI Vector DB...")
        t0 = time.time()
        for bucket, paths in BUCKET_PATHS.items():
            self.build_index(bucket, paths)
        print(f"⚡ Total build time: {time.time()-t0:.1f}s")
    
    def save(self):
        """Persist DB to disk"""
        with open(self.db_path, "wb") as f:
            pickle.dump(self.buckets, f, protocol=4)
        size_mb = os.path.getsize(self.db_path) / (1024*1024)
        print(f"💾 DB saved → {self.db_path} ({size_mb:.1f} MB)")
    
    def load(self):
        """Load DB from disk"""
        with open(self.db_path, "rb") as f:
            self.buckets = pickle.load(f)
        total = sum(len(b["docs"]) for b in self.buckets.values())
        print(f"✅ DB loaded — {len(self.buckets)} buckets, {total} total documents")
    
    def detect_bucket(self, query: str) -> Optional[str]:
        """Route query to best bucket based on keyword matching"""
        q_lower = query.lower()
        scores = {}
        for bucket, keywords in BUCKET_ROUTES.items():
            scores[bucket] = sum(1 for kw in keywords if kw in q_lower)
        best = max(scores, key=scores.get)
        if scores[best] == 0:
            return None  # search all
        return best
    
    def search(
        self,
        query: str,
        n_results: int = 5,
        bucket: Optional[str] = None,
        filters: Optional[Dict] = None,
        year_range: Optional[Tuple[int, int]] = None,
    ) -> List[Dict]:
        """
        Search the vector DB.
        
        Args:
            query: Natural language query
            n_results: Top K results
            bucket: Force a specific bucket (governance/economy/policy/global_social)
                    If None, auto-routes based on query keywords
            filters: Dict of field:value to filter results (e.g. {"ministry": "Ministry of Finance"})
            year_range: (start_year, end_year) to filter by year field
        
        Returns:
            List of {score, doc, bucket} dicts
        """
        if bucket and bucket not in self.buckets:
            return []
        
        # Auto-route
        target_buckets = [bucket] if bucket else ([self.detect_bucket(query)] if self.detect_bucket(query) else list(self.buckets.keys()))
        
        all_results = []
        
        for bname in target_buckets:
            if bname not in self.buckets:
                continue
            b = self.buckets[bname]
            qvec = query_vector(query, b["vocab_idx"])
            
            for i, (doc, vec) in enumerate(zip(b["docs"], b["vectors"])):
                # Apply filters
                if filters:
                    skip = False
                    for key, val in filters.items():
                        doc_val = str(doc.get(key, "")).lower()
                        if val.lower() not in doc_val:
                            skip = True
                            break
                    if skip:
                        continue
                
                # Apply year range
                if year_range:
                    yr = doc.get("year") or doc.get("Year")
                    if yr and not (year_range[0] <= int(yr) <= year_range[1]):
                        continue
                
                score = cosine_similarity(qvec, vec)
                if score > 0.001:
                    all_results.append({
                        "score": round(score, 4),
                        "doc": doc,
                        "bucket": bname,
                    })
        
        # Sort by score
        all_results.sort(key=lambda x: -x["score"])
        return all_results[:n_results]
    
    def get_stats(self) -> Dict:
        """Return DB statistics"""
        stats = {"total_docs": 0, "buckets": {}}
        for name, b in self.buckets.items():
            n = len(b["docs"])
            stats["buckets"][name] = {
                "doc_count": n,
                "vocab_size": len(b["vocab_idx"]),
                "types": list(set(d.get("type","?") for d in b["docs"]))
            }
            stats["total_docs"] += n
        return stats


# ─────────────────────────────────────────────────────────────────────────────
# QUERY ENGINE
# ─────────────────────────────────────────────────────────────────────────────
class GovAIQueryEngine:
    """
    High-level query engine with natural language post-processing
    """
    
    def __init__(self):
        self.db = GovAIVectorDB()
        self.db.load()
    
    def query(self, question: str, n: int = 5, **kwargs) -> str:
        """
        Run a natural language query and return formatted answer context
        """
        results = self.db.search(question, n_results=n, **kwargs)
        
        if not results:
            return "No relevant documents found."
        
        lines = [f"🔍 Query: '{question}'", f"📚 {len(results)} relevant records found\n"]
        lines.append("=" * 70)
        
        for i, r in enumerate(results, 1):
            doc = r["doc"]
            score = r["score"]
            bucket = r["bucket"]
            text = doc.get("text", "")
            
            lines.append(f"\n[{i}] Relevance: {score:.3f} | Bucket: {bucket.upper()} | Type: {doc.get('type','?')}")
            if doc.get("year"):
                lines.append(f"    Year: {doc['year']}")
            lines.append(f"    {text}")
        
        return "\n".join(lines)
    
    def summary_stats(self) -> str:
        stats = self.db.get_stats()
        lines = ["\n📊 GovAI Knowledge Base Statistics", "=" * 50]
        lines.append(f"Total documents: {stats['total_docs']}")
        for bucket, info in stats["buckets"].items():
            lines.append(f"\n  Bucket: {bucket.upper()}")
            lines.append(f"    Documents: {info['doc_count']}")
            lines.append(f"    Vocab size: {info['vocab_size']}")
            lines.append(f"    Data types: {', '.join(info['types'][:5])}...")
        return "\n".join(lines)


if __name__ == "__main__":
    # Build and save the DB
    db = GovAIVectorDB()
    db.build_all()
    db.save()
    
    # Print stats
    stats = db.get_stats()
    print(f"\n📊 Database Stats:")
    print(f"Total documents: {stats['total_docs']}")
    for bucket, info in stats['buckets'].items():
        print(f"  {bucket}: {info['doc_count']} docs, vocab={info['vocab_size']}")
    print("\n✅ GovAI Vector DB built successfully!")
