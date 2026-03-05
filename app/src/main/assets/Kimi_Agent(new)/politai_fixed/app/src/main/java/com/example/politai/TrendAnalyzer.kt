package com.example.politai

import android.content.Context

/**
 * PoLiTAI - Trend Analysis Module
 */
class TrendAnalyzer(private val context: Context) {
    
    fun getTop3Issues(district: String? = null): List<String> {
        return listOf(
            "Water Supply: 15 complaints (30%)",
            "Road Repair: 12 complaints (24%)",
            "Electricity: 10 complaints (20%)"
        )
    }
    
    fun analyzeBudgetUtilization(): List<BudgetAnalysis> {
        return listOf(
            BudgetAnalysis("Healthcare", 1000.0, 850.0, 85.0, 150.0, UtilizationStatus.GOOD),
            BudgetAnalysis("Education", 800.0, 500.0, 62.5, 300.0, UtilizationStatus.MODERATE),
            BudgetAnalysis("Infrastructure", 1200.0, 400.0, 33.3, 800.0, UtilizationStatus.POOR)
        )
    }
}

data class BudgetAnalysis(
    val sector: String,
    val allocatedBudget: Double,
    val utilizedBudget: Double,
    val utilizationPercentage: Double,
    val underutilizationAmount: Double,
    val status: UtilizationStatus
)

enum class UtilizationStatus {
    EXCELLENT, GOOD, MODERATE, POOR, CRITICAL
}

fun Context.createTrendAnalyzer(): TrendAnalyzer {
    return TrendAnalyzer(this)
}