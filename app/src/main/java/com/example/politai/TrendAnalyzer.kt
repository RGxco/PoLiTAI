package com.example.politai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * PoLiTAI - Trend Analysis Module
 * 
 * Lightweight Kotlin utility to analyze JSON databases for:
 * - Top Repeated Complaints by District/Category
 * - Budget Underutilization Analysis
 * - Scheme Performance Metrics
 * - Economic Indicator Trends
 */

// Data classes for trend analysis results
data class ComplaintTrend(
    val category: String,
    val district: String,
    val count: Int,
    val priorityBreakdown: Map<String, Int>,
    val statusBreakdown: Map<String, Int>
)

data class BudgetAnalysis(
    val sector: String,
    val allocatedBudget: Double,
    val utilizedBudget: Double,
    val utilizationPercentage: Double,
    val underutilizationAmount: Double,
    val status: UtilizationStatus
)

enum class UtilizationStatus {
    EXCELLENT,    // >90%
    GOOD,         // 70-90%
    MODERATE,     // 50-70%
    POOR,         // 30-50%
    CRITICAL      // <30%
}

data class SchemePerformance(
    val schemeName: String,
    val ministry: String,
    val launchYear: Int,
    val budgetAllocation: String,
    val currentStatus: String,
    val performanceScore: Double
)

data class EconomicTrend(
    val indicator: String,
    val currentValue: Double,
    val previousValue: Double,
    val changePercent: Double,
    val trend: TrendDirection,
    val assessment: String
)

enum class TrendDirection {
    INCREASING, DECREASING, STABLE, VOLATILE
}

data class DistrictIssueReport(
    val district: String,
    val topIssues: List<IssueSummary>,
    val totalComplaints: Int,
    val criticalIssues: Int,
    val pendingIssues: Int
)

data class IssueSummary(
    val category: String,
    val count: Int,
    val percentage: Double,
    val priority: String
)

/**
 * Trend Analyzer - Main analysis engine
 */
class TrendAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-Trend"
        private val gson = Gson()
    }
    
    /**
     * Analyze top complaints in a specific district
     * Returns sorted list of complaint categories with counts
     */
    fun analyzeDistrictComplaints(district: String): DistrictIssueReport {
        Log.d(TAG, "Analyzing complaints for district: $district")
        
        // In a real implementation, load from constituency_complaints.json
        // For now, we'll simulate with governance meetings data
        val meetings = loadMeetingsData()
        
        val categoryCounts = mutableMapOf<String, Int>()
        val priorityCounts = mutableMapOf<String, Int>()
        val statusCounts = mutableMapOf<String, Int>()
        
        // Filter meetings related to the district
        val relevantMeetings = meetings.filter { meeting ->
            val text = meeting["text"] as? String ?: ""
            text.contains(district, ignoreCase = true)
        }
        
        relevantMeetings.forEach { meeting ->
            val topic = meeting["topic"] as? String ?: "General"
            val riskLevel = meeting["risk_level"] as? String ?: "Medium"
            val status = meeting["implementation_status"] as? String ?: "Pending"
            
            categoryCounts[topic] = categoryCounts.getOrDefault(topic, 0) + 1
            priorityCounts[riskLevel] = priorityCounts.getOrDefault(riskLevel, 0) + 1
            statusCounts[status] = statusCounts.getOrDefault(status, 0) + 1
        }
        
        val totalComplaints = categoryCounts.values.sum()
        val criticalIssues = priorityCounts.filter { 
            it.key.contains("Critical", ignoreCase = true) || 
            it.key.contains("High", ignoreCase = true) 
        }.values.sum()
        val pendingIssues = statusCounts.filter { 
            it.key.contains("Pending", ignoreCase = true) 
        }.values.sum()
        
        val topIssues = categoryCounts
            .map { (category, count) ->
                IssueSummary(
                    category = category,
                    count = count,
                    percentage = if (totalComplaints > 0) (count * 100.0 / totalComplaints).roundToInt().toDouble() else 0.0,
                    priority = if (priorityCounts.any { it.key.contains("Critical") }) "High" else "Medium"
                )
            }
            .sortedByDescending { it.count }
            .take(5)
        
        return DistrictIssueReport(
            district = district,
            topIssues = topIssues,
            totalComplaints = totalComplaints,
            criticalIssues = criticalIssues,
            pendingIssues = pendingIssues
        )
    }
    
    /**
     * Analyze budget utilization across sectors
     * Returns sectors with underutilization warnings
     */
    fun analyzeBudgetUtilization(): List<BudgetAnalysis> {
        Log.d(TAG, "Analyzing budget utilization")
        
        val meetings = loadMeetingsData()
        val sectorBudgets = mutableMapOf<String, Pair<Double, Double>>()
        
        meetings.forEach { meeting ->
            val topic = meeting["topic"] as? String ?: return@forEach
            val budgetImpact = meeting["budget_impact"] as? String ?: return@forEach
            
            // Extract budget numbers from text
            val allocated = extractBudgetAmount(budgetImpact)
            val utilized = allocated * (meeting["completion_pct"] as? Double ?: 0.0) / 100.0
            
            val sector = extractSectorFromTopic(topic)
            val current = sectorBudgets.getOrDefault(sector, Pair(0.0, 0.0))
            sectorBudgets[sector] = Pair(
                current.first + allocated,
                current.second + utilized
            )
        }
        
        return sectorBudgets.map { (sector, budgets) ->
            val (allocated, utilized) = budgets
            val utilizationPct = if (allocated > 0) (utilized / allocated * 100) else 0.0
            
            BudgetAnalysis(
                sector = sector,
                allocatedBudget = allocated,
                utilizedBudget = utilized,
                utilizationPercentage = utilizationPct,
                underutilizationAmount = allocated - utilized,
                status = when {
                    utilizationPct >= 90 -> UtilizationStatus.EXCELLENT
                    utilizationPct >= 70 -> UtilizationStatus.GOOD
                    utilizationPct >= 50 -> UtilizationStatus.MODERATE
                    utilizationPct >= 30 -> UtilizationStatus.POOR
                    else -> UtilizationStatus.CRITICAL
                }
            )
        }.sortedBy { it.utilizationPercentage }
    }
    
    /**
     * Get top 3 issues across all districts or specific district
     */
    fun getTop3Issues(district: String? = null): List<String> {
        val targetDistrict = district ?: "All Districts"
        val report = analyzeDistrictComplaints(targetDistrict)
        
        return report.topIssues.take(3).map { issue ->
            "${issue.category}: ${issue.count} complaints (${issue.percentage}%)"
        }
    }
    
    /**
     * Analyze economic indicators (CPI, GDP, Repo Rate)
     */
    fun analyzeEconomicTrends(): List<EconomicTrend> {
        Log.d(TAG, "Analyzing economic trends")
        
        val trends = mutableListOf<EconomicTrend>()
        
        // Analyze CPI trend
        val cpiData = loadCpiData()
        if (cpiData.size >= 2) {
            val current = cpiData.last()
            val previous = cpiData[cpiData.size - 2]
            
            val currentValue = (current["cpi_yoy_pct"] as? Number)?.toDouble() ?: 0.0
            val previousValue = (previous["cpi_yoy_pct"] as? Number)?.toDouble() ?: 0.0
            val change = currentValue - previousValue
            
            trends.add(EconomicTrend(
                indicator = "CPI Inflation",
                currentValue = currentValue,
                previousValue = previousValue,
                changePercent = change,
                trend = when {
                    change > 0.5 -> TrendDirection.INCREASING
                    change < -0.5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                },
                assessment = when {
                    currentValue > 6 -> "Above RBI target band"
                    currentValue < 2 -> "Below RBI target band"
                    else -> "Within RBI target band (2-6%)"
                }
            ))
        }
        
        // Analyze GDP trend
        val gdpData = loadGdpData()
        if (gdpData.size >= 2) {
            val current = gdpData.last()
            val previous = gdpData[gdpData.size - 2]
            
            val currentGrowth = (current["yoy_growth_pct"] as? Number)?.toDouble() ?: 0.0
            val previousGrowth = (previous["yoy_growth_pct"] as? Number)?.toDouble() ?: 0.0
            
            trends.add(EconomicTrend(
                indicator = "GDP Growth",
                currentValue = currentGrowth,
                previousValue = previousGrowth,
                changePercent = currentGrowth - previousGrowth,
                trend = when {
                    currentGrowth > previousGrowth + 0.5 -> TrendDirection.INCREASING
                    currentGrowth < previousGrowth - 0.5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                },
                assessment = when {
                    currentGrowth > 7 -> "Strong growth"
                    currentGrowth > 5 -> "Moderate growth"
                    currentGrowth > 0 -> "Slow growth"
                    else -> "Contraction"
                }
            ))
        }
        
        // Analyze Repo Rate
        val repoData = loadRepoData()
        if (repoData.isNotEmpty()) {
            val latest = repoData.last()
            val rate = (latest["rate_pct"] as? Number)?.toDouble() ?: 6.5
            val direction = latest["direction"] as? String ?: "hold"
            
            trends.add(EconomicTrend(
                indicator = "RBI Repo Rate",
                currentValue = rate,
                previousValue = rate,
                changePercent = 0.0,
                trend = when (direction) {
                    "hike" -> TrendDirection.INCREASING
                    "cut" -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                },
                assessment = "Current stance: ${latest["source"] ?: "Neutral"}"
            ))
        }
        
        return trends
    }
    
    /**
     * Generate comprehensive district report
     */
    fun generateDistrictReport(district: String): String {
        val report = analyzeDistrictComplaints(district)
        val budgetAnalysis = analyzeBudgetUtilization()
        
        return buildString {
            appendLine("=== DISTRICT GOVERNANCE REPORT: $district ===")
            appendLine()
            appendLine("📊 COMPLAINT OVERVIEW:")
            appendLine("   • Total Issues Tracked: ${report.totalComplaints}")
            appendLine("   • Critical Priority: ${report.criticalIssues}")
            appendLine("   • Pending Resolution: ${report.pendingIssues}")
            appendLine()
            appendLine("🔝 TOP 3 ISSUES:")
            report.topIssues.take(3).forEachIndexed { index, issue ->
                appendLine("   ${index + 1}. ${issue.category}")
                appendLine("      → ${issue.count} complaints (${issue.percentage}%)")
            }
            appendLine()
            appendLine("💰 BUDGET UTILIZATION:")
            budgetAnalysis.take(3).forEach { budget ->
                val statusEmoji = when (budget.status) {
                    UtilizationStatus.EXCELLENT -> "✅"
                    UtilizationStatus.GOOD -> "🟢"
                    UtilizationStatus.MODERATE -> "🟡"
                    UtilizationStatus.POOR -> "🟠"
                    UtilizationStatus.CRITICAL -> "🔴"
                }
                appendLine("   $statusEmoji ${budget.sector}: ${budget.utilizationPercentage.roundToInt()}% utilized")
            }
        }
    }
    
    /**
     * Get scheme performance summary
     */
    fun getSchemePerformanceSummary(): List<SchemePerformance> {
        val schemes = loadSchemesData()
        
        return schemes.map { scheme ->
            val name = scheme["scheme_name"] as? String ?: "Unknown"
            val ministry = scheme["ministry"] as? String ?: "Unknown"
            val year = (scheme["year"] as? Number)?.toInt() ?: 0
            val budget = scheme["budget_allocation"] as? String ?: "N/A"
            val status = scheme["status"] as? String ?: "Unknown"
            
            // Calculate performance score based on status
            val score = when {
                status.contains("Active", ignoreCase = true) -> 85.0
                status.contains("Completed", ignoreCase = true) -> 100.0
                status.contains("Superseded", ignoreCase = true) -> 70.0
                else -> 50.0
            }
            
            SchemePerformance(
                schemeName = name,
                ministry = ministry,
                launchYear = year,
                budgetAllocation = budget,
                currentStatus = status,
                performanceScore = score
            )
        }.sortedByDescending { it.performanceScore }
    }
    
    // Helper functions for data loading
    
    private fun loadMeetingsData(): List<Map<String, Any>> {
        return loadJsonData("governance_meetings.json")
    }
    
    private fun loadCpiData(): List<Map<String, Any>> {
        return loadJsonData("india_cpi_monthly.json")
    }
    
    private fun loadGdpData(): List<Map<String, Any>> {
        return loadJsonData("india_gdp_quarterly.json")
    }
    
    private fun loadRepoData(): List<Map<String, Any>> {
        return loadJsonData("rbi_repo_rate_history.json")
    }
    
    private fun loadSchemesData(): List<Map<String, Any>> {
        return loadJsonData("india_government_schemes.json")
    }
    
    private fun loadJsonData(filename: String): List<Map<String, Any>> {
        return try {
            context.assets.open(filename).use { stream ->
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                gson.fromJson(InputStreamReader(stream), type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $filename: ${e.message}")
            emptyList()
        }
    }
    
    private fun extractBudgetAmount(text: String): Double {
        // Extract numeric value from budget strings like "₹3,200 crore"
        val regex = Regex("[₹]?\\s*([0-9,.]+)\\s*(crore|lakh|cr)", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }
    
    private fun extractSectorFromTopic(topic: String): String {
        return when {
            topic.contains("Health", ignoreCase = true) -> "Healthcare"
            topic.contains("Education", ignoreCase = true) -> "Education"
            topic.contains("Rural", ignoreCase = true) -> "Rural Development"
            topic.contains("Urban", ignoreCase = true) -> "Urban Development"
            topic.contains("Agriculture", ignoreCase = true) -> "Agriculture"
            topic.contains("Infrastructure", ignoreCase = true) -> "Infrastructure"
            topic.contains("Finance", ignoreCase = true) -> "Finance"
            topic.contains("Defence", ignoreCase = true) -> "Defence"
            topic.contains("Environment", ignoreCase = true) -> "Environment"
            else -> "General Administration"
        }
    }
}

/**
 * Extension function for easy trend analysis access
 */
fun Context.createTrendAnalyzer(): TrendAnalyzer {
    return TrendAnalyzer(this)
}