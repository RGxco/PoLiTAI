#!/usr/bin/env python3
"""
PoLiTAI - JSON to JSONL Dataset Converter
Converts governance JSON databases into instruction-context-response pairs for fine-tuning

Usage:
    python json_to_jsonl_converter.py --input-dir ./databases --output ./politai_training.jsonl
"""

import json
import os
import argparse
import random
from pathlib import Path
from typing import List, Dict, Any

# System prompt for fine-tuning (must match your Android app)
SYSTEM_PROMPT = """You are PoLiTAI, a Senior Government Data Analyst for Indian governance.
Provide professional, factual, and high-precision answers based on the provided data.
Rules:
- Start immediately with the answer (No greetings like "Hello", "Hi").
- Use bullet points (•) for lists.
- Do NOT repeat labels like 'DATA' or 'CONTEXT' in your response.
- Answer directly and authoritatively.
- Use Indian numbering (lakhs, crores) where appropriate.
- If data is insufficient, state "Based on available records..." and provide best estimate."""


class JSONtoJSONLConverter:
    def __init__(self, input_dir: str):
        self.input_dir = Path(input_dir)
        self.training_data: List[Dict[str, str]] = []
        
    def convert_all(self) -> List[Dict[str, str]]:
        """Convert all JSON files in the input directory"""
        json_files = list(self.input_dir.glob("*.json"))
        print(f"Found {len(json_files)} JSON files to process...")
        
        for json_file in json_files:
            print(f"Processing: {json_file.name}")
            try:
                self._process_file(json_file)
            except Exception as e:
                print(f"  ⚠️ Error processing {json_file.name}: {e}")
                
        print(f"\n✅ Generated {len(self.training_data)} training examples")
        return self.training_data
    
    def _process_file(self, filepath: Path):
        """Process a single JSON file based on its schema type"""
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
            
        filename = filepath.stem.lower()
        
        # Route to appropriate processor based on filename
        if 'politician' in filename or 'mp' in filename or 'mla' in filename:
            self._process_politician_data(data, filename)
        elif 'scheme' in filename or 'welfare' in filename:
            self._process_scheme_data(data, filename)
        elif 'meeting' in filename or 'debate' in filename:
            self._process_meeting_data(data, filename)
        elif 'budget' in filename or 'allocation' in filename:
            self._process_budget_data(data, filename)
        elif 'bill' in filename or 'act' in filename:
            self._process_bill_data(data, filename)
        elif 'complaint' in filename or 'grievance' in filename:
            self._process_complaint_data(data, filename)
        elif 'constitution' in filename:
            self._process_constitution_data(data, filename)
        elif 'disaster' in filename or 'emergency' in filename:
            self._process_disaster_data(data, filename)
        elif 'speech' in filename:
            self._process_speech_data(data, filename)
        elif 'economic' in filename or 'cpi' in filename or 'gdp' in filename or 'rbi' in filename:
            self._process_economic_data(data, filename)
        else:
            self._process_generic_data(data, filename)
    
    def _add_example(self, instruction: str, context: str, response: str):
        """Add a training example"""
        self.training_data.append({
            "system": SYSTEM_PROMPT,
            "instruction": instruction.strip(),
            "context": context.strip(),
            "response": response.strip()
        })
    
    def _process_politician_data(self, data: List[Dict], source: str):
        """Generate training examples from politician data"""
        templates = [
            "Tell me about {name}",
            "Who is {name}?",
            "What is the political background of {name}?",
            "Give me details on {name} from {party}",
            "What are {name}'s focus areas?",
            "Summarize {name}'s profile",
            "Key facts about {name}",
        ]
        
        for item in data:
            name = item.get('name', item.get('Name', 'Unknown'))
            party = item.get('party', item.get('Party', 'Unknown'))
            constituency = item.get('constituency', item.get('Constituency', 'Unknown'))
            
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            # Generate 2-3 examples per politician
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(name=name, party=party)
                response = self._generate_politician_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_politician_response(self, item: Dict) -> str:
        """Generate a natural response for politician data"""
        name = item.get('name', item.get('Name', 'Unknown'))
        party = item.get('party', item.get('Party', 'Unknown'))
        constituency = item.get('constituency', item.get('Constituency', 'Unknown'))
        focus_areas = item.get('focus_areas', item.get('Focus Areas', []))
        
        response = f"**{name}** is a prominent leader from the **{party}** party, representing **{constituency}**.\n\n"
        
        if focus_areas:
            response += "**Key Focus Areas:**\n"
            for area in focus_areas[:5]:
                response += f"• {area}\n"
        
        # Add historical facts if available
        historical = item.get('historical_facts', item.get('Historical Facts', []))
        if historical:
            response += "\n**Notable Facts:**\n"
            for fact in historical[:3]:
                response += f"• {fact}\n"
                
        return response
    
    def _process_scheme_data(self, data: List[Dict], source: str):
        """Generate training examples from scheme data"""
        templates = [
            "What is {scheme_name}?",
            "Tell me about {scheme_name} scheme",
            "Who benefits from {scheme_name}?",
            "What is the budget for {scheme_name}?",
            "Explain {scheme_name} in detail",
            "Eligibility for {scheme_name}",
        ]
        
        for item in data:
            scheme_name = item.get('scheme_name', item.get('name', 'Unknown'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(scheme_name=scheme_name)
                response = self._generate_scheme_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_scheme_response(self, item: Dict) -> str:
        """Generate a natural response for scheme data"""
        name = item.get('scheme_name', item.get('name', 'Unknown'))
        ministry = item.get('ministry', item.get('Ministry', 'Unknown'))
        benefits = item.get('benefits', item.get('Benefits', 'N/A'))
        budget = item.get('budget_allocation', item.get('Budget', 'N/A'))
        target = item.get('target_group', item.get('Target', 'General public'))
        
        response = f"**{name}** is a flagship scheme under the **{ministry}**.\n\n"
        response += f"**Benefits:** {benefits}\n"
        response += f"**Target Group:** {target}\n"
        if budget != 'N/A':
            response += f"**Budget Allocation:** {budget}\n"
            
        return response
    
    def _process_meeting_data(self, data: List[Dict], source: str):
        """Generate training examples from meeting data"""
        templates = [
            "Summarize the meeting on {topic}",
            "What were the decisions in the {topic} meeting?",
            "Meeting minutes for {topic}",
            "Key points from {topic} discussion",
        ]
        
        for item in data:
            topic = item.get('topic', item.get('Topic', 'General'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(topic=topic)
                response = self._generate_meeting_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_meeting_response(self, item: Dict) -> str:
        """Generate meeting minutes format response"""
        topic = item.get('topic', item.get('Topic', 'General'))
        date = item.get('date', item.get('Date', 'N/A'))
        decisions = item.get('decisions', item.get('Decisions', []))
        attendees = item.get('attendees', item.get('Attendees', []))
        
        response = f"**Meeting: {topic}**\n"
        response += f"**Date:** {date}\n\n"
        
        if attendees:
            response += f"**Attendees:** {', '.join(attendees[:5])}\n\n"
            
        if decisions:
            response += "**Key Decisions:**\n"
            for i, decision in enumerate(decisions[:5], 1):
                response += f"{i}. {decision}\n"
                
        return response
    
    def _process_budget_data(self, data: List[Dict], source: str):
        """Generate training examples from budget data"""
        templates = [
            "What is the budget for {sector}?",
            "Budget allocation for {sector}",
            "How much was allocated to {sector}?",
            "Budget utilization in {sector}",
        ]
        
        for item in data:
            sector = item.get('sector', item.get('Sector', 'General'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(sector=sector)
                response = self._generate_budget_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_budget_response(self, item: Dict) -> str:
        """Generate budget analysis response"""
        sector = item.get('sector', item.get('Sector', 'General'))
        allocated = item.get('allocated', item.get('Allocated', 'N/A'))
        utilized = item.get('utilized', item.get('Utilized', 'N/A'))
        year = item.get('year', item.get('Year', 'N/A'))
        
        response = f"**Budget Analysis: {sector} ({year})**\n\n"
        response += f"• **Allocated:** {allocated}\n"
        response += f"• **Utilized:** {utilized}\n"
        
        if allocated != 'N/A' and utilized != 'N/A':
            try:
                alloc_val = float(str(allocated).replace('₹', '').replace(' crore', '').replace(',', ''))
                util_val = float(str(utilized).replace('₹', '').replace(' crore', '').replace(',', ''))
                pct = (util_val / alloc_val * 100) if alloc_val > 0 else 0
                response += f"• **Utilization Rate:** {pct:.1f}%\n"
            except:
                pass
                
        return response
    
    def _process_bill_data(self, data: List[Dict], source: str):
        """Generate training examples from bill/act data"""
        templates = [
            "What is the {bill_name}?",
            "Explain {bill_name}",
            "Key provisions of {bill_name}",
            "Status of {bill_name}",
        ]
        
        for item in data:
            bill_name = item.get('bill_name', item.get('name', item.get('Name', 'Unknown')))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(bill_name=bill_name)
                response = self._generate_bill_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_bill_response(self, item: Dict) -> str:
        """Generate bill/act response"""
        name = item.get('bill_name', item.get('name', item.get('Name', 'Unknown')))
        status = item.get('status', item.get('Status', 'Unknown'))
        provisions = item.get('provisions', item.get('Provisions', []))
        
        response = f"**{name}**\n"
        response += f"**Status:** {status}\n\n"
        
        if provisions:
            response += "**Key Provisions:**\n"
            for provision in provisions[:5]:
                response += f"• {provision}\n"
                
        return response
    
    def _process_complaint_data(self, data: List[Dict], source: str):
        """Generate training examples from complaint data"""
        templates = [
            "What are the top issues in {district}?",
            "Complaints from {district}",
            "Issues reported in {district}",
            "Status of complaints in {district}",
        ]
        
        # Group by district
        districts = {}
        for item in data:
            district = item.get('district', item.get('District', 'Unknown'))
            if district not in districts:
                districts[district] = []
            districts[district].append(item)
        
        for district, complaints in districts.items():
            context = json.dumps(complaints[:10], ensure_ascii=False, indent=2)
            
            for template in templates:
                instruction = template.format(district=district)
                response = self._generate_complaint_response(district, complaints)
                self._add_example(instruction, context, response)
    
    def _generate_complaint_response(self, district: str, complaints: List[Dict]) -> str:
        """Generate complaint summary response"""
        response = f"**Complaint Summary: {district}**\n\n"
        response += f"**Total Complaints:** {len(complaints)}\n\n"
        
        # Count by category
        categories = {}
        for c in complaints:
            cat = c.get('issue_category', c.get('Category', 'General'))
            categories[cat] = categories.get(cat, 0) + 1
        
        response += "**Top Issues:**\n"
        for cat, count in sorted(categories.items(), key=lambda x: -x[1])[:5]:
            response += f"• {cat}: {count} complaints\n"
            
        return response
    
    def _process_constitution_data(self, data: List[Dict], source: str):
        """Generate training examples from constitution data"""
        templates = [
            "What does Article {article} say?",
            "Explain Article {article} of the Constitution",
            "Constitutional provision for {topic}",
        ]
        
        for item in data:
            article = item.get('article', item.get('Article', 'Unknown'))
            topic = item.get('topic', item.get('Topic', 'General'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in random.sample(templates, min(2, len(templates))):
                instruction = template.format(article=article, topic=topic)
                response = self._generate_constitution_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_constitution_response(self, item: Dict) -> str:
        """Generate constitution article response"""
        article = item.get('article', item.get('Article', 'Unknown'))
        title = item.get('title', item.get('Title', 'N/A'))
        content = item.get('content', item.get('Content', 'N/A'))
        
        response = f"**{article}: {title}**\n\n"
        response += f"{content}\n"
        
        return response
    
    def _process_disaster_data(self, data: List[Dict], source: str):
        """Generate training examples from disaster data"""
        templates = [
            "Emergency situation in {district}",
            "Disaster update for {district}",
            "Relief status in {district}",
        ]
        
        for item in data:
            district = item.get('district', item.get('District', 'Unknown'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in templates:
                instruction = template.format(district=district)
                response = self._generate_disaster_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_disaster_response(self, item: Dict) -> str:
        """Generate disaster/emergency response"""
        event = item.get('event_type', item.get('Event', 'Emergency'))
        district = item.get('district', item.get('District', 'Unknown'))
        severity = item.get('severity', item.get('Severity', 'Unknown'))
        status = item.get('relief_status', item.get('Status', 'Unknown'))
        
        response = f"🚨 **{event} Alert: {district}**\n\n"
        response += f"**Severity:** {severity}\n"
        response += f"**Relief Status:** {status}\n"
        
        needs = item.get('urgent_needs', [])
        if needs:
            response += "\n**Urgent Needs:**\n"
            for need in needs:
                response += f"• {need}\n"
                
        return response
    
    def _process_speech_data(self, data: List[Dict], source: str):
        """Generate training examples from speech templates"""
        templates = [
            "Draft a speech on {topic}",
            "Speech for {topic} event",
            "Address on {topic}",
        ]
        
        for item in data:
            topic = item.get('topic', item.get('Topic', 'General'))
            context = json.dumps(item, ensure_ascii=False, indent=2)
            
            for template in templates:
                instruction = template.format(topic=topic)
                response = self._generate_speech_response(item)
                self._add_example(instruction, context, response)
    
    def _generate_speech_response(self, item: Dict) -> str:
        """Generate speech draft response"""
        topic = item.get('topic', item.get('Topic', 'General'))
        template = item.get('template', item.get('Template', ''))
        
        response = f"**Speech Draft: {topic}**\n\n"
        response += template if template else "[Speech template content would go here]"
        
        return response
    
    def _process_economic_data(self, data: List[Dict], source: str):
        """Generate training examples from economic data"""
        templates = [
            "What is the current {indicator}?",
            "Latest {indicator} data",
            "Trend in {indicator}",
            "Analysis of {indicator}",
        ]
        
        indicator_name = "economic indicators"
        if 'cpi' in source:
            indicator_name = "CPI inflation"
        elif 'gdp' in source:
            indicator_name = "GDP growth"
        elif 'rbi' in source or 'repo' in source:
            indicator_name = "RBI repo rate"
        
        context = json.dumps(data[:20], ensure_ascii=False, indent=2)
        
        for template in templates:
            instruction = template.format(indicator=indicator_name)
            response = self._generate_economic_response(data, indicator_name)
            self._add_example(instruction, context, response)
    
    def _generate_economic_response(self, data: List[Dict], indicator: str) -> str:
        """Generate economic indicator response"""
        if not data:
            return f"No data available for {indicator}."
        
        latest = data[-1]
        
        response = f"**{indicator.title()} Analysis**\n\n"
        
        # Extract key values
        for key, value in latest.items():
            if key not in ['date', 'Date', 'timestamp']:
                response += f"• **{key.replace('_', ' ').title()}:** {value}\n"
        
        return response
    
    def _process_generic_data(self, data: List[Dict], source: str):
        """Process any generic JSON data"""
        templates = [
            f"Tell me about {source.replace('_', ' ')}",
            f"Information from {source.replace('_', ' ')}",
            f"What does {source.replace('_', ' ')} contain?",
        ]
        
        context = json.dumps(data[:5], ensure_ascii=False, indent=2)
        
        for template in templates:
            instruction = template
            response = f"**{source.replace('_', ' ').title()}**\n\n"
            response += f"This database contains {len(data)} records.\n\n"
            response += "**Sample Entry:**\n"
            if data:
                for key, value in list(data[0].items())[:5]:
                    response += f"• **{key}:** {value}\n"
            self._add_example(instruction, context, response)
    
    def save_jsonl(self, output_path: str):
        """Save training data to JSONL file"""
        with open(output_path, 'w', encoding='utf-8') as f:
            for example in self.training_data:
                f.write(json.dumps(example, ensure_ascii=False) + '\n')
        print(f"\n💾 Saved {len(self.training_data)} examples to {output_path}")
    
    def print_stats(self):
        """Print dataset statistics"""
        print("\n" + "="*50)
        print("📊 DATASET STATISTICS")
        print("="*50)
        print(f"Total training examples: {len(self.training_data)}")
        
        # Calculate average lengths
        avg_instruction = sum(len(e['instruction']) for e in self.training_data) / len(self.training_data)
        avg_context = sum(len(e['context']) for e in self.training_data) / len(self.training_data)
        avg_response = sum(len(e['response']) for e in self.training_data) / len(self.training_data)
        
        print(f"\nAverage lengths:")
        print(f"  • Instruction: {avg_instruction:.0f} chars")
        print(f"  • Context: {avg_context:.0f} chars")
        print(f"  • Response: {avg_response:.0f} chars")
        print("="*50)


def main():
    parser = argparse.ArgumentParser(description='Convert JSON databases to JSONL training dataset')
    parser.add_argument('--input-dir', '-i', required=True, help='Directory containing JSON files')
    parser.add_argument('--output', '-o', default='politai_training.jsonl', help='Output JSONL file')
    parser.add_argument('--sample', '-s', action='store_true', help='Print a sample example')
    
    args = parser.parse_args()
    
    converter = JSONtoJSONLConverter(args.input_dir)
    converter.convert_all()
    converter.save_jsonl(args.output)
    converter.print_stats()
    
    if args.sample and converter.training_data:
        print("\n📄 SAMPLE EXAMPLE:")
        print("="*50)
        sample = random.choice(converter.training_data)
        print(f"INSTRUCTION: {sample['instruction'][:100]}...")
        print(f"CONTEXT: {sample['context'][:200]}...")
        print(f"RESPONSE: {sample['response'][:200]}...")


if __name__ == '__main__':
    main()