package com.wittighouse.crowsnest

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*

/**
 * Quarterdeck HUD — the full-screen task display for lock screen / home screen.
 * Shows active tasks, progress, and next action in a clean, glanceable format.
 */
class QuarterdeckHUDFragment : Fragment() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var taskContainer: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var nextActionText: TextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: ImageView
    private var currentState: QuarterdeckState? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Build the entire HUD layout programmatically for maximum control
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF0F172A.toInt()) // slate-900
        }

        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        // ── Header ──
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val titleBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleIcon = TextView(requireContext()).apply {
            text = "🏴"
            textSize = 28f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
        }
        headerRow.addView(titleIcon)

        val titleText = TextView(requireContext()).apply {
            text = "QUARTERDECK"
            textSize = 24f
            setTextColor(0xFFF8FAFC.toInt())
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        titleBlock.addView(titleText)

        val subtitleText = TextView(requireContext()).apply {
            text = "Fleet Admiral's Task Board"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
        }
        titleBlock.addView(subtitleText)
        headerRow.addView(titleBlock)

        refreshButton = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_rotate)
            setColorFilter(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { refreshHUD() }
        }
        headerRow.addView(refreshButton)

        rootLayout.addView(headerRow)

        // ── Progress Card ──
        val progressCard = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            cardElevation = 4f
            radius = dp(16)
            setCardBackgroundColor(0xFF1E293B.toInt()) // slate-800
            setContentPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val progressLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        progressText = TextView(requireContext()).apply {
            text = "Loading tasks..."
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        progressLayout.addView(progressText)

        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
            ).apply { bottomMargin = dp(6) }
            max = 100
            progress = 0
            progressDrawable = createProgressDrawable(0xFF10B981.toInt())
        }
        progressLayout.addView(progressBar)

        progressCard.addView(progressLayout)
        rootLayout.addView(progressCard)

        // ── Active Tasks Card ──
        val taskCard = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            cardElevation = 4f
            radius = dp(16)
            setCardBackgroundColor(0xFF1E293B.toInt())
            setContentPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val taskCardLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val taskHeader = TextView(requireContext()).apply {
            text = "📋 ACTIVE TASKS"
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        taskCardLayout.addView(taskHeader)

        taskContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        taskCardLayout.addView(taskContainer)
        taskCard.addView(taskCardLayout)
        rootLayout.addView(taskCard)

        // ── Next Action Card ──
        val nextCard = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            cardElevation = 4f
            radius = dp(16)
            setCardBackgroundColor(0xFF312E81.toInt()) // indigo-900
            setContentPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val nextLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        TextView(requireContext()).apply {
            text = "▶ NEXT ACTION"
            textSize = 12f
            setTextColor(0xFFA5B4FC.toInt())
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }.also { nextLayout.addView(it) }

        nextActionText = TextView(requireContext()).apply {
            text = "—"
            textSize = 16f
            setTextColor(0xFFE0E7FF.toInt())
            setTypeface(null, Typeface.BOLD)
        }
        nextLayout.addView(nextActionText)

        nextCard.addView(nextLayout)
        rootLayout.addView(nextCard)

        // ── Status bar ──
        statusText = TextView(requireContext()).apply {
            text = ""
            textSize = 11f
            setTextColor(0xFF475569.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER
        }
        val statusRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        statusRow.addView(statusText)
        rootLayout.addView(statusRow)

        scrollView.addView(rootLayout)
        
        // Start auto-refresh
        startAutoRefresh()
        
        return scrollView
    }

    private fun startAutoRefresh() {
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                refreshHUD()
                delay(30_000) // refresh every 30 seconds
            }
        }
    }

    fun refreshHUD() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = QuarterdeckHUD.fetchState()
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { state ->
                        currentState = state
                        renderState(state)
                        statusText.text = "✓ Updated ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
                        statusText.setTextColor(0xFF10B981.toInt())
                    },
                    onFailure = { e ->
                        statusText.text = "⚠ ${e.message?.take(40) ?: "Connection failed"}"
                        statusText.setTextColor(0xFFEF4444.toInt())
                        // Keep showing last known state if available
                    }
                )
            }
        }
    }

    private fun renderState(state: QuarterdeckState) {
        val total = state.totalActive.coerceAtLeast(1)
        val completed = state.completedToday
        progressText.text = "${completed} / ${state.totalActive} tasks completed today"
        progressBar.progress = ((completed.toFloat() / total) * 100).toInt().coerceIn(0, 100)

        // Render tasks
        taskContainer.removeAllViews()
        val tasks = state.active.take(8) // show at most 8
        if (tasks.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "🎉 All clear, Admiral!"
                textSize = 15f
                setTextColor(0xFF64748B.toInt())
                setTypeface(null, Typeface.ITALIC)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }
            taskContainer.addView(emptyView)
        } else {
            for ((index, task) in tasks.withIndex()) {
                taskContainer.addView(createTaskRow(index + 1, task))
            }
        }

        // Next action
        nextActionText.text = state.nextAction ?: "No pending actions"
    }

    private fun createTaskRow(number: Int, task: QuarterdeckTask): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Priority indicator
        val priorityColor = when (task.priority) {
            "high", "urgent" -> 0xFFEF4444.toInt()
            "medium" -> 0xFFF59E0B.toInt()
            else -> 0xFF64748B.toInt()
        }

        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(4)).apply { marginEnd = dp(10) }
            setBackgroundColor(priorityColor)
            // make it round via a drawable
            background = ContextCompat.getDrawable(requireContext(), R.drawable.status_dot_online)
        }.also { row.addView(it) }

        // Task text
        val taskText = TextView(requireContext()).apply {
            text = "$number. ${task.task}"
            textSize = 14f
            setTextColor(0xFFCBD5E1.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            // Strike-through if done
            if (task.done) {
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                setTextColor(0xFF475569.toInt())
            }
        }
        row.addView(taskText)

        // Due date badge if present
        if (task.due != null && task.due.isNotBlank()) {
            val dueBadge = TextView(requireContext()).apply {
                text = task.due
                textSize = 10f
                setTextColor(0xFF94A3B8.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setBackgroundColor(0xFF334155.toInt())
            }
            row.addView(dueBadge)
        }

        // Tap to toggle done
        row.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                QuarterdeckHUD.toggleTask(task.id)
                withContext(Dispatchers.Main) {
                    refreshHUD()
                }
            }
        }

        return row
    }

    private fun createProgressDrawable(color: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(color)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refreshJob?.cancel()
    }
}
