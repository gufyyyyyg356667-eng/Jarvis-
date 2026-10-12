package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivityPrivacyShieldBinding
import com.jarvis.assistant.util.ThemeManager

class PrivacyShieldActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyShieldBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyShieldBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnJoinOfficialTelegram.setOnClickListener {
            openUrl("https://t.me/H_a_b_i_b_owner")
        }

        binding.btnJoinTelegram.setOnClickListener {
            openUrl("https://t.me/H_a_b_i_b_owner")
        }

        binding.btnJoinInstagram.setOnClickListener {
            openUrl("https://www.facebook.com/profile.php?id=61579292174748")
        }

        binding.btnJoinYoutube.setOnClickListener {
            openUrl("https://www.facebook.com/profile.php?id=61579292174748")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
