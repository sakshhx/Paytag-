package com.example.paytagplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment

class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_onboarding, container, false)

        val imageRes = arguments?.getInt("image") ?: 0
        view.findViewById<ImageView>(R.id.imgOnboard)
            .setImageResource(imageRes)

        return view
    }

    companion object {
        fun newInstance(imageRes: Int): OnboardingFragment {
            val fragment = OnboardingFragment()
            val bundle = Bundle()
            bundle.putInt("image", imageRes)
            fragment.arguments = bundle
            return fragment
        }
    }
}
