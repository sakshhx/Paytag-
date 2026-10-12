package com.example.paytagplus

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity)
    : FragmentStateAdapter(activity) {

    private val images = listOf(
        R.drawable.first,
        R.drawable.second,
        R.drawable.third
    )

    override fun getItemCount() = images.size

    override fun createFragment(position: Int): Fragment {
        return OnboardingFragment.newInstance(images[position])
    }
}