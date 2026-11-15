package com.example.multidict

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.ItemOnboardingPageBinding

data class OnboardingPage(
    @DrawableRes val illustrationRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
)

/**
 * ViewPager2 adapter for the 4-page first-run welcome carousel.
 */
class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage) {
            binding.ivOnboardingIllustration.setImageResource(page.illustrationRes)
            binding.tvOnboardingTitle.setText(page.titleRes)
            binding.tvOnboardingDesc.setText(page.descRes)

            // Gentle entrance animation for title and description
            binding.tvOnboardingTitle.alpha = 0f
            binding.tvOnboardingTitle.translationY = 20f
            binding.tvOnboardingDesc.alpha = 0f
            binding.tvOnboardingDesc.translationY = 24f

            binding.tvOnboardingTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .start()

            binding.tvOnboardingDesc.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(70L)
                .setDuration(300L)
                .start()
        }
    }
}
