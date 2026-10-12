package com.example.paytagplus

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnBoardingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_on_boarding)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val dots = findViewById<TabLayout>(R.id.tabDots)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // 1️⃣ Set adapter
        viewPager.adapter = OnboardingAdapter(this)

        // 2️⃣ Attach dots
        TabLayoutMediator(dots, viewPager) { _, _ -> }.attach()

        // 3️⃣ 👇 WRITE THIS CODE HERE
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == viewPager.adapter!!.itemCount - 1) {
                    btnNext.text = "Get Started"
                } else {
                    btnNext.text = "Next"
                }
            }
        })

        // 4️⃣ Button click logic
        btnNext.setOnClickListener {
            if (viewPager.currentItem < viewPager.adapter!!.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                // Last page → go to Register/Login screen
                startActivity(Intent(this, RegisterActivity::class.java))
                finish()
            }
        }
    }
}


