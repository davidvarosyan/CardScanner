package com.fastcredit.fcbank.scanner.sdk.ui.views

//noinspection SuspiciousImport
import android.R
import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.widget.ProgressBar

class ProgressBarIndeterminate @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ProgressBar(context, attrs, defStyleAttr) {

    override fun setVisibility(v: Int) {
        super.setVisibility(v)
        clearAnimation()
        if (v == VISIBLE) {
            alpha = 1f
        }
    }

    fun hideSlow() {
        if (visibility != VISIBLE) return
        animate()
            .alpha(0f)
            .setDuration(resources.getInteger(R.integer.config_shortAnimTime).toLong())
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    alpha = 1f
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
    }
}