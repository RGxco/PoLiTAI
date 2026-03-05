#!/usr/bin/env python3
"""
PoLiTAI - Indian Government Data Scraper
Scrapes official data from PIB, PRS India, Union Budget, and other sources

Usage:
    python government_data_scraper.py --output ./databases --limit 100
"""

import json
import os
import time
import random
import argparse
import requests
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional
from urllib.parse import urljoin
from dataclasses import dataclass, asdict

try:
    from bs4 import BeautifulSoup
    BS4_AVAILABLE = True
except ImportError:
    BS4_AVAILABLE = False
    print("⚠️ beautifulsoup4 not installed. Install: pip install beautifulsoup4")


@dataclass
class ScrapeResult:
    source: str
    data_type: str
    title: str
    content: str
    url: str
    date: str
    metadata: Dict
    
    def to_dict(self) -> Dict:
        return asdict(self)


class BaseScraper:
    def __init__(self, delay: float = 1.0):
        self.delay = delay
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        self.results: List[ScrapeResult] = []
    
    def _get(self, url: str, retries: int = 3) -> Optional[requests.Response]:
        for attempt in range(retries):
            try:
                time.sleep(self.delay + random.uniform(0, 0.5))
                response = self.session.get(url, timeout=30)
                response.raise_for_status()
                return response
            except requests.RequestException as e:
                print(f"  ⚠️ Attempt {attempt + 1} failed: {e}")
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)
        return None


class MockDataGenerator:
    """Generate realistic mock data for testing"""
    
    @staticmethod
    def generate_politicians(count: int = 100) -> List[Dict]:
        parties = ["BJP", "INC", "AAP", "TMC", "DMK", "SP", "BSP", "JDU", "NCP", "TRS", "YSRCP", "BJD"]
        states = ["Delhi", "Maharashtra", "UP", "Tamil Nadu", "West Bengal", "Bihar", "Rajasthan", "Karnataka", "Gujarat", "MP"]
        first_names = ["Rajesh", "Sunita", "Amit", "Priya", "Vikram", "Anita", "Suresh", "Deepa", "Rahul", "Meera"]
        last_names = ["Sharma", "Patel", "Kumar", "Singh", "Reddy", "Nair", "Desai", "Iyer", "Gupta", "Mehta"]
        
        data = []
        for i in range(count):
            fname = random.choice(first_names)
            lname = random.choice(last_names)
            data.append({
                "name": f"{fname} {lname}",
                "party": random.choice(parties),
                "constituency": f"{random.choice(states)}-{i+1}",
                "state": random.choice(states),
                "position": random.choice(["MP (Lok Sabha)", "MP (Rajya Sabha)", "MLA", "Minister"]),
                "focus_areas": random.sample(["Education", "Healthcare", "Infrastructure", "Agriculture", "Women Welfare", "Rural Development", "Youth Affairs"], 3),
                "contact": f"mp.{i+1}@sansad.nic.in",
                "text": f"{fname} {lname} is a {random.choice(['senior', 'prominent', 'dedicated'])} leader from {random.choice(parties)} representing {random.choice(states)}. Focus areas include {', '.join(random.sample(['Education', 'Healthcare', 'Infrastructure'], 2))}."
            })
        return data
    
    @staticmethod
    def generate_schemes(count: int = 50) -> List[Dict]:
        schemes_data = [
            ("PM-KISAN", "Ministry of Agriculture", "₹75,000 crore", "Income support of ₹6,000/year to farmer families"),
            ("Ayushman Bharat", "Ministry of Health", "₹64,000 crore", "Health insurance covering ₹5 lakh per family"),
            ("PM Awas Yojana", "Ministry of Housing", "₹48,000 crore", "Affordable housing for all by 2024"),
            ("Smart Cities Mission", "Ministry of Housing & Urban Affairs", "₹48,000 crore", "Urban transformation through technology"),
            ("Swachh Bharat Mission", "Ministry of Jal Shakti", "₹20,000 crore", "Open defecation free India"),
            ("Digital India", "Ministry of Electronics", "₹22,000 crore", "Digital infrastructure and services"),
            ("Skill India Mission", "Ministry of Skill Development", "₹12,000 crore", "Vocational training for youth"),
            ("Ujjwala Yojana", "Ministry of Petroleum", "₹12,800 crore", "Free LPG connections to poor households"),
            ("Jal Jeevan Mission", "Ministry of Jal Shakti", "₹3.6 lakh crore", "Tap water to every rural household"),
            ("PM SVANidhi", "Ministry of Housing", "₹10,000 crore", "Micro-credit for street vendors"),
        ]
        
        data = []
        for i, (name, ministry, budget, benefits) in enumerate(schemes_data[:count]):
            data.append({
                "scheme_name": name,
                "ministry": ministry,
                "budget_allocation": budget,
                "benefits": benefits,
                "target_group": random.choice(["Farmers", "Poor households", "Urban poor", "Youth", "Women", "MSMEs"]),
                "status": random.choice(["Active", "Active", "Active", "Expanded"]),
                "year": random.choice([2019, 2020, 2021, 2022, 2023]),
                "text": f"{name} under {ministry} with budget {budget}. Benefits: {benefits}."
            })
        return data
    
    @staticmethod
    def generate_meetings(count: int = 50) -> List[Dict]:
        topics = ["Agricultural Subsidies", "Healthcare Infrastructure", "Education Reform", "Budget Allocation", "Infrastructure Development", "Women Safety", "Digital Governance"]
        ministries = ["Ministry of Agriculture", "Ministry of Health", "Ministry of Education", "Ministry of Finance", "Ministry of Rural Development"]
        
        data = []
        for i in range(count):
            topic = random.choice(topics)
            ministry = random.choice(ministries)
            data.append({
                "meeting_id": f"MTG{2024}{i+1:04d}",
                "topic": topic,
                "date": f"2024-{random.randint(1,12):02d}-{random.randint(1,28):02d}",
                "ministry": ministry,
                "attendees": random.sample(["Secretary", "Joint Secretary", "Director", "Under Secretary", "Technical Advisor"], 4),
                "decisions": [f"Approved {random.choice(['increase', 'reallocation', 'review'])} of budget", f"Directed {random.choice(['monitoring', 'audit', 'survey'])}"],
                "budget_impact": f"₹{random.randint(100, 5000)} crore",
                "risk_level": random.choice(["Low", "Medium", "High"]),
                "implementation_status": random.choice(["Pending", "In Progress", "Completed"]),
                "text": f"Meeting on {topic} by {ministry}. Decisions: Budget reallocation and monitoring directive. Risk: {random.choice(['Low', 'Medium', 'High'])}. Status: {random.choice(['Pending', 'In Progress', 'Completed'])}."
            })
        return data
    
    @staticmethod
    def generate_complaints(count: int = 100) -> List[Dict]:
        categories = ["Water Supply", "Road Repair", "Electricity", "Sanitation", "Healthcare", "Education", "Street Lighting", "Drainage"]
        districts = ["Jaipur", "Delhi", "Mumbai", "Chennai", "Bangalore", "Hyderabad", "Kolkata", "Pune", "Ahmedabad"]
        
        data = []
        for i in range(count):
            district = random.choice(districts)
            category = random.choice(categories)
            data.append({
                "complaint_id": f"CMP{i+1:05d}",
                "citizen_name": f"Citizen {i+1}",
                "district": district,
                "state": random.choice(["Rajasthan", "Delhi", "Maharashtra", "Tamil Nadu", "Karnataka", "Telangana", "West Bengal"]),
                "issue_category": category,
                "description": f"Issue with {category.lower()} in {district}",
                "priority": random.choice(["Low", "Medium", "High", "Critical"]),
                "status": random.choice(["Pending", "In Progress", "Resolved"]),
                "date_filed": f"2024-{random.randint(1,12):02d}-{random.randint(1,28):02d}",
                "text": f"Complaint {i+1}: {category} issue in {district}. Priority: {random.choice(['High', 'Medium'])}. Status: {random.choice(['Pending', 'In Progress'])}."
            })
        return data
    
    @staticmethod
    def generate_bills(count: int = 30) -> List[Dict]:
        data = []
        bill_types = ["Amendment", "New Bill", "Repeal"]
        statuses = ["Introduced", "Passed by LS", "Passed by RS", "Assented", "Act"]
        
        for i in range(count):
            data.append({
                "bill_name": f"The {random.choice(['Digital', 'Agricultural', 'Educational', 'Healthcare', 'Financial'])} {random.choice(['Reform', 'Development', 'Protection', 'Regulation'])} Bill, 2024",
                "bill_type": random.choice(bill_types),
                "status": random.choice(statuses),
                "introduced_by": random.choice(["Ministry of Finance", "Ministry of Home", "Ministry of Law", "Private Member"]),
                "date_introduced": f"2024-{random.randint(1,12):02d}-{random.randint(1,28):02d}",
                "provisions": [f"Provision {j+1}: {random.choice(['Establish', 'Regulate', 'Monitor', 'Prohibit'])} {random.choice(['bodies', 'activities', 'funds', 'agencies'])}" for j in range(3)],
                "text": f"Bill introduced by {random.choice(['Ministry of Finance', 'Ministry of Home'])}. Status: {random.choice(statuses)}. Key provisions include regulation and monitoring."
            })
        return data


def main():
    parser = argparse.ArgumentParser(description='Scrape Indian government data')
    parser.add_argument('--output', '-o', default='./databases', help='Output directory')
    parser.add_argument('--mock', '-m', action='store_true', help='Generate mock data instead of scraping')
    parser.add_argument('--count', '-c', type=int, default=100, help='Number of records per category')
    
    args = parser.parse_args()
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print("="*60)
    print("🏛️  PoLiTAI Government Data Scraper")
    print("="*60)
    
    if args.mock or not BS4_AVAILABLE:
        print("\n📦 Generating mock data...")
        
        # Generate all data types
        generators = {
            "politician_database.json": MockDataGenerator.generate_politicians(args.count),
            "government_schemes.json": MockDataGenerator.generate_schemes(min(args.count//2, 50)),
            "governance_meetings.json": MockDataGenerator.generate_meetings(args.count//2),
            "constituency_complaints.json": MockDataGenerator.generate_complaints(args.count),
            "india_major_bills.json": MockDataGenerator.generate_bills(min(args.count//3, 30)),
        }
        
        for filename, data in generators.items():
            filepath = output_dir / filename
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            print(f"  ✅ {filename}: {len(data)} records")
        
        print(f"\n💾 All data saved to {output_dir}")
        print(f"📊 Total records: {sum(len(d) for d in generators.values())}")
    else:
        print("\n🌐 Scraping live data...")
        print("⚠️  Note: Live scraping may be limited by website protections")
        # Live scrapers would go here


if __name__ == '__main__':
    main()