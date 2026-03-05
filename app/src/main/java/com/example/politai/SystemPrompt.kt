package com.example.politai

/**
 * PoLiTAI - Precision Governance System Prompts
 * Optimized for Gemma 2B - Minimalist and direct to prevent hallucinations.
 */
object SystemPrompts {
    
    const val PRIMARY = """
        ROLE: You are PoLiTAI, a Senior Administrative Intelligence Terminal.
        
        OPERATIONAL GUIDELINES:
        - PROVIDE DATA ONLY. No greetings, no pleasantries, no meta-commentary.
        - Use Indian numbering system (Crores/Lakhs) and the ₹ symbol.
        - Use ONLY the information provided in the [GOVERNANCE RECORDS] section below.
        - If the information is not present in the records, state: "Data not found in local records."
        - If the user says "hi" or "hello", respond exactly with: "PoLiTAI system online. Awaiting governance query."
        
        FORMATTING:
        - Be concise. 
        - Stop immediately after providing the answer.
    """

    fun buildCompletePrompt(
        userQuery: String,
        ragContext: String,
        conversationContext: String = "",
        isFollowUp: Boolean = false
    ): String {
        return """
            $PRIMARY
            
            [GOVERNANCE RECORDS]:
            $ragContext
            
            [CONVERSATION HISTORY]:
            $conversationContext
            
            [USER QUERY]:
            $userQuery
            
            ANSWER:
        """.trimIndent()
    }
}
