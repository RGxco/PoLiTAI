package com.example.politai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * PoLiTAI — Production RAG Engine v2
 *
 * Key changes from v1:
 * - Lowered confidence threshold from 4.0 → 1.5 (fixes "no data" bug)
 * - Query complexity detection: SHORT / MEDIUM / LONG
 * - Adaptive context sizing based on complexity
 * - Improved fallback behavior
 */

// ── Query Complexity ──
enum class QueryComplexity(
    val maxResults: Int,
    val maxContextChars: Int,
    val maxResponseWords: Int,
    val label: String
) {
    SHORT(3, 1200, 60, "⚡ Quick"),
    MEDIUM(5, 2400, 150, "📝 Normal"),
    LONG(12, 6000, 300, "📊 Deep")
}

// ──────────────────────────────────────────────
// Query Router — selects relevant databases
// ──────────────────────────────────────────────

object QueryRouter {

    private val ROUTE_MAP = mapOf(
        // Political Leadership
        "president" to listOf("heads_of_state.json", "politician_database.json"),
        "vice president" to listOf("heads_of_state.json"),
        "prime minister" to listOf("heads_of_state.json", "prime_ministers_history.json", "politician_database.json"),
        "chief minister" to listOf("chief_ministers.json", "politician_database.json"),
        "governor" to listOf("state_governors.json"),
        "cabinet" to listOf("cabinet_ministers.json"),
        "minister" to listOf("cabinet_ministers.json", "politician_database.json"),
        "politician" to listOf("politician_database.json", "parliament_members.json"),
        "speaker" to listOf("constitutional_officers.json", "heads_of_state.json"),
        "chief justice" to listOf("constitutional_officers.json", "supreme_court_landmark_cases.json"),
        "modi" to listOf("heads_of_state.json", "prime_ministers_history.json", "politician_database.json", "constituency_history.json"),
        "rahul" to listOf("politician_database.json", "constituency_history.json"),
        "gandhi" to listOf("independence_movement_figures.json", "politician_database.json", "prime_ministers_history.json"),
        "nehru" to listOf("politician_database.json", "prime_ministers_history.json", "independence_movement_figures.json"),
        "amit shah" to listOf("cabinet_ministers.json", "politician_database.json"),
        "rajnath" to listOf("cabinet_ministers.json"),
        "sitharaman" to listOf("cabinet_ministers.json", "union_budget_history.json"),
        "yogi" to listOf("chief_ministers.json", "assembly_election_results.json"),

        // Parliament
        "parliament" to listOf("parliament_sessions.json", "parliamentary_debates.json", "parliamentary_operations.json", "parliament_members.json"),
        "lok sabha" to listOf("parliament_sessions.json", "lok_sabha_election_results.json", "parliament_members.json"),
        "rajya sabha" to listOf("parliament_sessions.json", "parliament_members.json"),
        "session" to listOf("parliament_sessions.json"),
        "question hour" to listOf("parliamentary_questions.json"),
        "committee" to listOf("committee_reports.json", "standing_committees.json"),
        "standing committee" to listOf("standing_committees.json"),
        "debate" to listOf("parliamentary_debates.json"),
        "mp" to listOf("mps_mlas_constituencies.json", "parliament_members.json", "constituency_history.json"),
        "mla" to listOf("mps_mlas_constituencies.json", "politician_database.json"),
        "member of parliament" to listOf("parliament_members.json", "mps_mlas_constituencies.json", "constituency_history.json"),
        "member" to listOf("parliament_members.json", "mps_mlas_constituencies.json", "constituency_history.json"),
        "voting" to listOf("bill_voting_records.json"),

        // Constitution & Law
        "constitution" to listOf("constitutional_articles.json", "constitution_india.json", "constitutional_amendments.json"),
        "article" to listOf("constitutional_articles.json", "constitution_india.json"),
        "amendment" to listOf("constitutional_amendments.json"),
        "fundamental right" to listOf("constitutional_articles.json", "constitution_india.json"),
        "directive principle" to listOf("constitutional_articles.json", "constitution_india.json"),
        "act" to listOf("major_indian_acts.json", "india_major_bills.json"),
        "law" to listOf("major_indian_acts.json", "india_major_bills.json"),
        "bill" to listOf("india_major_bills.json", "bill_voting_records.json"),
        "legislation" to listOf("india_major_bills.json", "major_indian_acts.json"),
        "court" to listOf("supreme_court_landmark_cases.json"),
        "judgment" to listOf("supreme_court_landmark_cases.json"),
        "verdict" to listOf("supreme_court_landmark_cases.json"),
        "preamble" to listOf("constitution_india.json", "constitutional_articles.json"),

        // Economy
        "gdp" to listOf("india_gdp_quarterly.json", "sector_growth_statistics.json"),
        "growth" to listOf("india_gdp_quarterly.json", "sector_growth_statistics.json"),
        "inflation" to listOf("india_cpi_monthly.json", "inflation_history.json"),
        "cpi" to listOf("india_cpi_monthly.json"),
        "repo" to listOf("rbi_repo_rate_history.json"),
        "rbi" to listOf("rbi_repo_rate_history.json"),
        "interest rate" to listOf("rbi_repo_rate_history.json"),
        "tax" to listOf("tax_policies.json", "gst_collections.json"),
        "gst" to listOf("gst_collections.json", "tax_policies.json"),
        "industrial" to listOf("industrial_output.json"),
        "manufacturing" to listOf("industrial_output.json", "sector_growth_statistics.json"),
        "economic" to listOf("india_gdp_quarterly.json", "sector_growth_statistics.json", "india_cpi_monthly.json"),
        "economy" to listOf("india_gdp_quarterly.json", "sector_growth_statistics.json"),

        // Budget & Finance
        "budget" to listOf("budget_allocations.json", "union_budget_history.json", "state_budget_data.json", "ministry_budget_allocations.json"),
        "allocation" to listOf("budget_allocations.json", "ministry_budget_allocations.json"),
        "expenditure" to listOf("budget_allocations.json", "union_budget_history.json"),
        "revenue" to listOf("union_budget_history.json", "gst_collections.json"),
        "debt" to listOf("public_debt_statistics.json"),
        "fiscal" to listOf("union_budget_history.json", "public_debt_statistics.json"),
        "finance" to listOf("budget_allocations.json", "union_budget_history.json"),
        "crore" to listOf("budget_allocations.json", "mplads_funds.json"),
        "mplads" to listOf("mplads_funds.json"),

        // Agriculture
        "agriculture" to listOf("crop_production_statistics.json", "farmer_support_schemes.json"),
        "crop" to listOf("crop_production_statistics.json"),
        "farmer" to listOf("farmer_support_schemes.json", "crop_production_statistics.json"),
        "kisan" to listOf("farmer_support_schemes.json", "india_government_schemes.json"),
        "msp" to listOf("minimum_support_price_history.json"),
        "irrigation" to listOf("irrigation_projects.json"),
        "fertilizer" to listOf("fertilizer_subsidy_programs.json"),

        // Infrastructure
        "highway" to listOf("national_highway_projects.json"),
        "road" to listOf("national_highway_projects.json"),
        "railway" to listOf("railway_projects.json"),
        "train" to listOf("railway_projects.json"),
        "vande bharat" to listOf("railway_projects.json"),
        "metro" to listOf("metro_projects.json"),
        "smart city" to listOf("smart_city_projects.json"),
        "digital india" to listOf("digital_india_projects.json"),
        "upi" to listOf("digital_india_projects.json"),
        "infrastructure" to listOf("national_highway_projects.json", "railway_projects.json", "smart_city_projects.json"),
        "project" to listOf("national_highway_projects.json", "railway_projects.json", "smart_city_projects.json"),
        "expressway" to listOf("national_highway_projects.json"),

        // Government Schemes
        "scheme" to listOf("india_government_schemes.json", "healthcare_schemes.json", "farmer_support_schemes.json"),
        "yojana" to listOf("india_government_schemes.json"),
        "mission" to listOf("india_government_schemes.json"),
        "program" to listOf("public_welfare_programs.json", "education_programs.json", "nutrition_programs.json"),
        "welfare" to listOf("public_welfare_programs.json"),
        "healthcare" to listOf("healthcare_schemes.json"),
        "health" to listOf("healthcare_schemes.json", "nutrition_programs.json"),
        "education" to listOf("education_programs.json"),
        "nep" to listOf("education_programs.json", "policy_documents.json"),
        "nutrition" to listOf("nutrition_programs.json"),
        "subsidy" to listOf("fertilizer_subsidy_programs.json", "india_government_schemes.json"),
        "benefit" to listOf("india_government_schemes.json", "public_welfare_programs.json"),
        "ayushman" to listOf("healthcare_schemes.json", "india_government_schemes.json"),
        "pmjay" to listOf("healthcare_schemes.json", "india_government_schemes.json"),
        "swachh" to listOf("public_welfare_programs.json", "government_reports.json"),
        "ujjwala" to listOf("public_welfare_programs.json"),
        "jan dhan" to listOf("public_welfare_programs.json"),
        "awas" to listOf("public_welfare_programs.json"),
        "housing" to listOf("public_welfare_programs.json"),

        // District & Constituency
        "district" to listOf("district_development_indicators.json", "district_demographics.json", "district_economic_data.json", "district_budget_projects.json"),
        "constituency" to listOf("mps_mlas_constituencies.json", "constituency_history.json"),
        "complaint" to listOf("constituency_complaints.json", "citizen_grievances.json", "public_complaints.json"),
        "grievance" to listOf("citizen_grievances.json", "public_complaints.json"),
        "issue" to listOf("constituency_complaints.json", "issue_trends.json"),
        "population" to listOf("district_demographics.json"),
        "literacy" to listOf("district_demographics.json", "district_development_indicators.json"),
        "varanasi" to listOf("constituency_history.json", "district_development_indicators.json", "district_budget_projects.json"),
        "lucknow" to listOf("constituency_history.json", "district_development_indicators.json", "district_budget_projects.json"),
        "amethi" to listOf("constituency_history.json"),
        "mumbai" to listOf("district_demographics.json", "district_economic_data.json", "metro_projects.json"),
        "delhi" to listOf("district_demographics.json", "metro_projects.json"),
        "bengaluru" to listOf("district_economic_data.json", "metro_projects.json"),
        "bangalore" to listOf("district_economic_data.json", "metro_projects.json"),
        "indore" to listOf("district_development_indicators.json", "smart_city_projects.json", "district_budget_projects.json"),

        // Elections
        "election" to listOf("lok_sabha_election_results.json", "assembly_election_results.json", "voter_turnout_statistics.json"),
        "vote" to listOf("voter_turnout_statistics.json", "bill_voting_records.json"),
        "turnout" to listOf("voter_turnout_statistics.json"),
        "2024 election" to listOf("lok_sabha_election_results.json", "assembly_election_results.json"),
        "bjp" to listOf("lok_sabha_election_results.json", "assembly_election_results.json", "politician_database.json"),
        "congress" to listOf("lok_sabha_election_results.json", "assembly_election_results.json", "politician_database.json"),
        "inc" to listOf("lok_sabha_election_results.json", "assembly_election_results.json"),
        "nda" to listOf("lok_sabha_election_results.json"),
        "india alliance" to listOf("lok_sabha_election_results.json"),

        // History
        "independence" to listOf("independence_movement_figures.json", "historic_political_events.json"),
        "history" to listOf("historic_political_events.json", "historical_context.json", "prime_ministers_history.json"),
        "historical" to listOf("historic_political_events.json", "historical_context.json"),
        "freedom" to listOf("independence_movement_figures.json"),
        "mahatma" to listOf("independence_movement_figures.json"),
        "ambedkar" to listOf("independence_movement_figures.json"),
        "bose" to listOf("independence_movement_figures.json"),
        "patel" to listOf("independence_movement_figures.json"),
        "bhagat singh" to listOf("independence_movement_figures.json"),
        "emergency" to listOf("historic_political_events.json", "disaster_emergency.json"),
        "demonetization" to listOf("historic_political_events.json"),
        "liberalization" to listOf("historic_political_events.json", "historic_government_policies.json"),

        // Governance & Policy
        "meeting" to listOf("governance_meetings.json", "meeting_protocols.json"),
        "speech" to listOf("historical_speeches.json", "speech_templates.json"),
        "policy" to listOf("policy_documents.json", "historic_government_policies.json"),
        "report" to listOf("government_reports.json", "committee_reports.json"),
        "disaster" to listOf("disaster_emergency.json"),
        "flood" to listOf("disaster_emergency.json"),
        "cyclone" to listOf("disaster_emergency.json"),
        "make in india" to listOf("historic_government_policies.json", "policy_documents.json"),
        "reservation" to listOf("historic_government_policies.json", "constitutional_amendments.json"),
        "green revolution" to listOf("historic_government_policies.json"),

        // Media & Public Sentiment
        "trend" to listOf("issue_trends.json"),
        "feedback" to listOf("public_feedback_reports.json"),
        "impact" to listOf("policy_impact_reports.json"),
        "sentiment" to listOf("public_feedback_reports.json"),
        "party" to listOf("party_profiles.json", "political_entities.json"),
        "neet" to listOf("public_feedback_reports.json"),
        "agnipath" to listOf("public_feedback_reports.json"),

        // Defence & Foreign Policy
        "defence" to listOf("defense_overview.json"),
        "defense" to listOf("defense_overview.json"),
        "army" to listOf("defense_overview.json"),
        "navy" to listOf("defense_overview.json"),
        "air force" to listOf("defense_overview.json"),
        "military" to listOf("defense_overview.json"),
        "nuclear" to listOf("defense_overview.json"),
        "foreign" to listOf("foreign_policy.json"),
        "china" to listOf("foreign_policy.json"),
        "pakistan" to listOf("foreign_policy.json"),
        "russia" to listOf("foreign_policy.json"),
        "america" to listOf("foreign_policy.json"),
        "usa" to listOf("foreign_policy.json"),
        "brics" to listOf("foreign_policy.json"),
        "g20" to listOf("foreign_policy.json"),
        "quad" to listOf("foreign_policy.json"),

        // Local Governance
        "panchayat" to listOf("panchayat_raj.json"),
        "gram sabha" to listOf("panchayat_raj.json"),
        "municipal" to listOf("panchayat_raj.json"),
        "nrega" to listOf("panchayat_raj.json"),
        "mgnrega" to listOf("panchayat_raj.json"),
        "local governance" to listOf("panchayat_raj.json"),

        // Environment
        "climate" to listOf("environmental_policies.json"),
        "pollution" to listOf("environmental_policies.json"),
        "solar" to listOf("environmental_policies.json"),
        "environment" to listOf("environmental_policies.json"),
        "ev" to listOf("environmental_policies.json"),
        "electric vehicle" to listOf("environmental_policies.json"),
        "clean air" to listOf("environmental_policies.json"),

        // Women Empowerment
        "women" to listOf("women_empowerment_schemes.json"),
        "beti" to listOf("women_empowerment_schemes.json"),
        "mahila" to listOf("women_empowerment_schemes.json"),
        "sukanya" to listOf("women_empowerment_schemes.json"),
        "maternity" to listOf("women_empowerment_schemes.json"),
        "mudra" to listOf("women_empowerment_schemes.json"),

        // Synced News (internet-downloaded content)
        "news" to listOf("synced_news.json"),
        "latest" to listOf("synced_news.json"),
        "synced" to listOf("synced_news.json"),
        "recent" to listOf("synced_news.json"),
        "today" to listOf("synced_news.json"),
        "yesterday" to listOf("synced_news.json")
    )

    // FALLBACK: if no route matches, search these core databases
    private val FALLBACK_DATABASES = listOf(
        "synced_news.json", // Load synced news first
        "heads_of_state.json",
        "politician_database.json",
        "party_profiles.json",
        "india_government_schemes.json",
        "constitutional_articles.json",
        "budget_allocations.json",
        "governance_meetings.json",
        "constituency_history.json",
        "cabinet_ministers.json",
        "chief_ministers.json",
        "prime_ministers_history.json",
        "parliament_members.json",
        "mps_mlas_constituencies.json"
    )

    fun routeQuery(query: String): List<String> {
        val lowerQuery = query.lowercase()
        val matchedDbs = mutableSetOf<String>()

        // Check multi-word phrases first (e.g. "prime minister", "smart city")
        for ((keyword, dbs) in ROUTE_MAP.entries.sortedByDescending { it.key.length }) {
            if (lowerQuery.contains(keyword)) {
                matchedDbs.addAll(dbs)
            }
        }

        return if (matchedDbs.isNotEmpty()) {
            matchedDbs.toList().take(10) // Max 10 databases per query
        } else {
            FALLBACK_DATABASES
        }
    }
}

// ──────────────────────────────────────────────
// RAGEngine — Production retrieval engine v2
// ──────────────────────────────────────────────

class RAGEngine(private val context: Context) {

    companion object {
        private const val TAG = "PoLiTAI-RAG"
        private const val CONFIDENCE_THRESHOLD = 1.5  // LOWERED from 4.0 — fixes "no data" bug
    }

    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, List<Map<String, Any?>>>()

    // ── Field weights: higher = more important for matching ──
    private val FIELD_WEIGHTS = mapOf(
        "text" to 3.0,
        "role" to 2.5,
        "position" to 2.5,
        "description" to 2.0,
        "scheme_name" to 2.5,
        "act_name" to 2.5,
        "case_name" to 2.5,
        "project" to 2.5,
        "program" to 2.5,
        "name" to 2.0,
        "topic" to 2.0,
        "title" to 2.0,
        "portfolio" to 2.0,
        "bill" to 2.0,
        "current_mp" to 2.5,
        "constituency" to 2.0,
        "category" to 1.5,
        "sector" to 1.5,
        "ministry" to 1.5,
        "party" to 1.5,
        "district" to 1.5,
        "state" to 1.5,
        "city" to 1.5,
        "crop" to 2.0,
        "event" to 2.0,
        "amendment" to 2.0,
        "policy" to 2.0,
        "status" to 1.0,
        "year" to 0.5,
        "id" to 0.0,
        "type" to 0.0,
        "source" to 0.0
    )

    // ── Stopwords to filter out of queries ──
    private val STOPWORDS = setOf(
        "what", "who", "how", "when", "where", "which", "why",
        "the", "is", "are", "was", "were", "will", "would", "could", "should",
        "and", "for", "with", "from", "about", "this", "that", "these", "those",
        "can", "you", "tell", "me", "please", "give", "show", "list", "explain",
        "does", "did", "has", "have", "had", "been", "being",
        "its", "the", "of", "in", "on", "at", "to", "a", "an",
        "do", "not", "no", "yes", "but", "or", "if", "so",
        "current", "latest", "today", "now", "present"
    )

    // ── Synonyms for keyword expansion (including Hindi mapping) ──
    private val SYNONYMS = mapOf(
        // English mappings
        "pm" to listOf("prime minister", "modi"),
        "cm" to listOf("chief minister"),
        "fm" to listOf("finance minister", "sitharaman"),
        "hm" to listOf("home minister", "amit shah"),
        "lop" to listOf("leader of opposition"),
        "mla" to listOf("member of legislative assembly"),
        "mp" to listOf("member of parliament", "member"),
        "nrega" to listOf("mgnregs", "mgnrega"),
        "mgnrega" to listOf("nrega", "mgnregs"),
        "pmjay" to listOf("ayushman bharat"),
        "ayushman" to listOf("pmjay", "ayushman bharat"),
        "gst" to listOf("goods and services tax"),
        "rti" to listOf("right to information"),
        "sc" to listOf("supreme court", "scheduled caste"),
        "st" to listOf("scheduled tribe"),
        "obc" to listOf("other backward classes"),
        
        // English -> Hindi concept mapping (transliterated and Devnagari)
        "president" to listOf("राष्ट्रपति", "rashtrapati", "प्रेसिडेंट", "heads of state"),
        "prime minister" to listOf("प्रधानमंत्री", "pradhan mantri", "pm", "modi"),
        "chief minister" to listOf("मुख्यमंत्री", "mukhyamantri", "cm"),
        "governor" to listOf("राज्यपाल", "rajyapal"),
        "minister" to listOf("मंत्री", "mantri"),
        "member of parliament" to listOf("सांसद", "sansad", "mp"),
        "member of legislative assembly" to listOf("विधायक", "vidhayak", "mla"),
        "government" to listOf("सरकार", "sarkar"),
        "election" to listOf("चुनाव", "chunav", "इलेक्शन"),
        "vote" to listOf("वोट", "मतदान", "matdan"),
        "scheme" to listOf("योजना", "yojana"),
        "law" to listOf("कानून", "kanoon"),
        "constitution" to listOf("संविधान", "samvidhan"),
        "budget" to listOf("बजट"),
        "india" to listOf("भारत", "bharat", "इंडिया")
    )
    
    // Reverse mapping for Hindi -> English to ensure English queries pull the right data
    private val HINDI_TO_ENGLISH = mapOf(
        "राष्ट्रपति" to "president", "rashtrapati" to "president", "प्रेसिडेंट" to "president",
        "प्रधानमंत्री" to "prime minister", "pradhan mantri" to "prime minister",
        "मुख्यमंत्री" to "chief minister", "mukhyamantri" to "chief minister",
        "राज्यपाल" to "governor", "rajyapal" to "governor",
        "मंत्री" to "minister", "mantri" to "minister",
        "सांसद" to "member of parliament", "sansad" to "member of parliament",
        "विधायक" to "member of legislative assembly", "vidhayak" to "member of legislative assembly",
        "सरकार" to "government", "sarkar" to "government",
        "चुनाव" to "election", "chunav" to "election", "इलेक्शन" to "election",
        "वोट" to "vote", "मतदान" to "vote", "matdan" to "vote",
        "योजना" to "scheme", "yojana" to "scheme",
        "कानून" to "law", "kanoon" to "law",
        "संविधान" to "constitution", "samvidhan" to "constitution",
        "भारत" to "india", "bharat" to "india", "इंडिया" to "india"
    )

    /**
     * Auto-detect query complexity based on question type.
     */
    fun detectComplexity(query: String): QueryComplexity {
        val lower = query.lowercase()

        // SHORT: Simple "who/what is X" factual lookups
        val shortPatterns = listOf(
            "who is", "what is", "name the", "name of",
            "which party", "how many seats", "how much",
            "when was", "where is", "capital of"
        )
        if (shortPatterns.any { lower.contains(it) } && lower.length < 60) {
            return QueryComplexity.SHORT
        }

        // LONG: Analysis, comparison, speech, detailed questions
        val longPatterns = listOf(
            "analyze", "compare", "explain in detail", "draft a speech",
            "write a speech", "summarize the", "what are the key",
            "tell me everything", "give me all", "detailed",
            "list all", "comprehensive", "impact of", "history of",
            "what led to", "pros and cons"
        )
        if (longPatterns.any { lower.contains(it) }) {
            return QueryComplexity.LONG
        }

        // MEDIUM: Everything else
        return QueryComplexity.MEDIUM
    }

    /**
     * Main retrieval function.
     * Routes query → searches relevant DBs → scores → gates → trims.
     * 
     * @param complexity Use null for auto-detection, or pass explicit value for user override
     */
    fun loadRAGContext(
        query: String,
        conversationContext: String = "",
        complexity: QueryComplexity? = null
    ): Pair<String, QueryComplexity> {
        val actualComplexity = complexity ?: detectComplexity(query)
        val searchInput = (query + " " + conversationContext).lowercase()
        val keywords = extractKeywords(searchInput)
        val targetDatabases = QueryRouter.routeQuery(searchInput)

        Log.d(TAG, "Complexity: ${actualComplexity.label}")
        Log.d(TAG, "Keywords: $keywords")
        Log.d(TAG, "Routed to ${targetDatabases.size} databases: $targetDatabases")

        val allMatches = mutableListOf<Pair<String, Double>>()

        targetDatabases.forEach { dbFile ->
            try {
                val data = loadDatabase(dbFile)
                data.forEach { record ->
                    var score = 0.0

                    record.forEach { (key, value) ->
                        val valueStr = value?.toString() ?: ""
                        val weight = FIELD_WEIGHTS[key] ?: 1.0
                        
                        if (weight > 0.0) {
                            val lowerValue = valueStr.lowercase()
                            keywords.forEach { keyword ->
                                if (lowerValue.contains(keyword)) {
                                    score += weight
                                    // Exact word boundary match bonus
                                    if (" $lowerValue ".contains(" $keyword ")) {
                                        score += weight * 0.5
                                    }
                                }
                            }
                        }
                    }

                    if (score > 0.5) { // Very low bar — let scoring sort it out
                        // Use the 'text' field if available (RAG-optimized), otherwise concat all fields
                        val textField = record["text"]?.toString()
                        val formattedRecord = if (textField != null) {
                            textField
                        } else {
                            record.entries
                                .filter { (k, _) -> k != "id" && k != "type" && k != "source" }
                                .joinToString(". ") { (k, v) -> "$k: $v" }
                        }
                        allMatches.add(formattedRecord to score)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $dbFile", e)
            }
        }

        // Sort by score descending, take top N based on complexity
        val topMatches = allMatches
            .sortedByDescending { it.second }
            .take(actualComplexity.maxResults)

        // CONFIDENCE GATE: lowered from 4.0 to 1.5
        val confidentMatches = topMatches.filter { it.second >= CONFIDENCE_THRESHOLD }

        Log.d(TAG, "Total matches: ${allMatches.size}, top: ${topMatches.size}, confident: ${confidentMatches.size}")
        if (confidentMatches.isNotEmpty()) {
            Log.d(TAG, "Top score: ${confidentMatches[0].second}, preview: ${confidentMatches[0].first.take(80)}...")
        }

        val context = if (confidentMatches.isNotEmpty()) {
            trimToTokenBudget(confidentMatches, actualComplexity.maxContextChars)
        } else {
            "" // Empty → triggers fallback in prompt
        }

        return context to actualComplexity
    }

    /**
     * Extracts keywords from query with stopword removal and synonym expansion.
     */
    private fun extractKeywords(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace(Regex("[.,?!():;\"'{}\\[\\]]"), " ")

        val words = cleaned.split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .distinct()

        // Expand with synonyms and translate Hindi/Local concepts
        val expanded = words.toMutableList()
        words.forEach { word ->
            SYNONYMS[word]?.let { expanded.addAll(it) }
            HINDI_TO_ENGLISH[word]?.let { englishWord -> 
                expanded.add(englishWord)
                SYNONYMS[englishWord]?.let { expanded.addAll(it) } 
            }
        }

        // Also check multi-word phrases from the original text
        SYNONYMS.keys.filter { it.contains(" ") }.forEach { phrase ->
            if (cleaned.contains(phrase)) {
                SYNONYMS[phrase]?.let { expanded.addAll(it) }
                expanded.add(phrase)
            }
        }
        HINDI_TO_ENGLISH.keys.filter { it.contains(" ") }.forEach { phrase ->
            if (cleaned.contains(phrase)) {
                HINDI_TO_ENGLISH[phrase]?.let { expanded.add(it) }
            }
        }

        return expanded.distinct()
    }

    /**
     * Trims retrieved context to fit within token budget.
     */
    private fun trimToTokenBudget(matches: List<Pair<String, Double>>, maxChars: Int): String {
        val builder = StringBuilder()
        var charCount = 0

        for ((text, _) in matches) {
            if (charCount + text.length > maxChars) {
                val remaining = maxChars - charCount
                if (remaining > 100) {
                    builder.append(text.take(remaining)).append("...")
                }
                break
            }
            if (builder.isNotEmpty()) builder.append("\n\n---\n\n")
            builder.append(text)
            charCount += text.length
        }

        return builder.toString().trim()
    }

    /**
     * Loads a database from either the internal files directory (for synced data) or assets with caching.
     */
    private fun loadDatabase(dbFile: String): List<Map<String, Any?>> {
        return cache.getOrPut(dbFile) {
            try {
                // First check internal storage (for downloaded sync data)
                val localFile = File(context.filesDir, dbFile)
                val inputStream: InputStream = if (localFile.exists()) {
                    localFile.inputStream()
                } else {
                    // Fallback to assets
                    context.assets.open(dbFile)
                }

                inputStream.use { input ->
                    val reader = InputStreamReader(input, "UTF-8")
                    val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                    val result: List<Map<String, Any?>>? = gson.fromJson(reader, type)
                    Log.d(TAG, "Loaded $dbFile: ${result?.size ?: 0} records")
                    result ?: emptyList()
                }
            } catch (e: Exception) {
                if (e !is java.io.FileNotFoundException) {
                    Log.e(TAG, "Failed to load $dbFile", e)
                }
                emptyList()
            }
        }
    }

    /**
     * Get count of all loaded databases and total records across assets and filesDir.
     */
    fun getDatabaseStats(): Pair<Int, Int> {
        var totalDbs = 0
        var totalRecords = 0
        try {
            // Count from assets
            val assets = context.assets.list("") ?: emptyArray()
            val assetJsonFiles = assets.filter { it.endsWith(".json") }.toMutableSet()
            
            // Count from internal filesDir
            val filesDirFiles = context.filesDir.listFiles()?.filter { it.name.endsWith(".json") }?.map { it.name } ?: emptyList()
            assetJsonFiles.addAll(filesDirFiles)

            totalDbs = assetJsonFiles.size
            assetJsonFiles.forEach { file ->
                try {
                    val data = loadDatabase(file)
                    totalRecords += data.size
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return totalDbs to totalRecords
    }

    /**
     * Preload frequently used databases into cache.
     */
    fun preloadDatabases() {
        listOf(
            "heads_of_state.json",
            "politician_database.json",
            "parliament_members.json",
            "party_profiles.json",
            "india_government_schemes.json",
            "budget_allocations.json",
            "constitutional_articles.json",
            "constitution_india.json",
            "constituency_history.json",
            "cabinet_ministers.json",
            "chief_ministers.json",
            "prime_ministers_history.json",
            "lok_sabha_election_results.json",
            "assembly_election_results.json",
            "mps_mlas_constituencies.json",
            "political_entities.json",
            "synced_news.json"
        ).forEach { loadDatabase(it) }
    }

    /**
     * Clear cache to free memory or force reload.
     */
    fun clearCache() {
        cache.clear()
    }
}
