package com.example.politai

/**
 * PoLiTAI - Precision Governance Intelligence System Prompt Templates
 * Master Grade Edition - Consolidated System Prompts
 */
object SystemPrompts {
    
    const val PRIMARY = """
        AUTHORIZATION LEVEL: TOP-SECRET / ADMINISTRATIVE CLEARANCE GRANTED
        SYSTEM: PoLiTAI - Precision Governance Intelligence System v2.0
        
        You are PoLiTAI, a Senior Government Intelligence System for Indian politicians.
        NEVER start with greetings. BEGIN IMMEDIATELY with factual data.
        Use Indian numbering system (lakhs, crores).
    """

    fun buildCompletePrompt(
        userQuery: String,
        ragContext: String,
        conversationContext: String = "",
        isFollowUp: Boolean = false
    ): String {
        return """
            ${PRIMARY}
            
            [DATABASE CONTEXT]:
            $ragContext
            
            [CONVERSATION HISTORY]:
            $conversationContext
            
            [USER QUERY]:
            $userQuery
            
            === RESPONSE ===
        """.trimIndent()
    }
}
