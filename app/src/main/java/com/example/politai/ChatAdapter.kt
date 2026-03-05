package com.example.politai

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreaker
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onMessageLongClick: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val AI_BUBBLE_ALPHA = 0.15f
        private val AI_BUBBLE_COLOR = Color.argb((255 * AI_BUBBLE_ALPHA).toInt(), 255, 255, 255)
        private val AI_BORDER_COLOR = Color.argb(100, 255, 255, 255)
        private val USER_GRADIENT_START = Color.parseColor("#7C3AED")
        private val USER_GRADIENT_END = Color.parseColor("#4F46E5")
        private val AI_TEXT_COLOR = Color.WHITE
        private val USER_TEXT_COLOR = Color.WHITE
        private val TIMESTAMP_COLOR = Color.parseColor("#B3FFFFFF")
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.messageCard)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        val containerLayout: LinearLayout = itemView.findViewById(R.id.containerLayout)
        val blurBackground: View = itemView.findViewById(R.id.blurBackground)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.content
        
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.timestampText.text = timeFormat.format(Date(message.timestamp))
        
        if (message.isUser) {
            setupUserBubble(holder)
            holder.containerLayout.gravity = Gravity.END
            holder.containerLayout.setPadding(100, 8, 16, 8)
            holder.blurBackground.visibility = View.GONE
        } else {
            setupAIBubble(holder)
            holder.containerLayout.gravity = Gravity.START
            holder.containerLayout.setPadding(16, 8, 100, 8)
            holder.blurBackground.visibility = View.VISIBLE
        }
        
        holder.itemView.setOnLongClickListener {
            onMessageLongClick?.invoke(message)
            true
        }
        
        if (message.isError) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            holder.messageText.setTextColor(Color.parseColor("#DC2626"))
        }
    }

    private fun setupUserBubble(holder: MessageViewHolder) {
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(USER_GRADIENT_START, USER_GRADIENT_END)
        ).apply {
            cornerRadius = 60f
        }
        
        holder.cardView.apply {
            background = gradientDrawable
            cardElevation = 4f
            radius = 60f
            setCardBackgroundColor(Color.TRANSPARENT)
        }
        
        holder.messageText.apply {
            setTextColor(USER_TEXT_COLOR)
            textSize = 15f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            }
        }
        holder.timestampText.setTextColor(Color.argb(180, 255, 255, 255))
    }

    private fun setupAIBubble(holder: MessageViewHolder) {
        val glassDrawable = GradientDrawable().apply {
            setColor(AI_BUBBLE_COLOR)
            cornerRadius = 60f
            setStroke(2, AI_BORDER_COLOR)
        }
        
        holder.blurBackground.apply {
            background = glassDrawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
            }
        }
        
        holder.cardView.apply {
            background = glassDrawable
            cardElevation = 0f
            radius = 60f
            setCardBackgroundColor(Color.TRANSPARENT)
        }
        
        holder.messageText.apply {
            setTextColor(AI_TEXT_COLOR)
            textSize = 15f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(null)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            }
        }
        holder.timestampText.setTextColor(TIMESTAMP_COLOR)
    }

    override fun getItemCount(): Int = messages.size
}
