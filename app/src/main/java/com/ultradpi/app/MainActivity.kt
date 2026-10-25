package com.ultradpi.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var isConnected = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSubtitle: TextView

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        btnToggle.setOnClickListener {
            if (!isConnected) {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) {
                    vpnLauncher.launch(prepareIntent)
                } else {
                    startVpnService()
                }
            } else {
                stopVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, UltraDpiVpnService::class.java).apply {
            action = UltraDpiVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        isConnected = true
        updateUi()
    }

    private fun stopVpnService() {
        val intent = Intent(this, UltraDpiVpnService::class.java).apply {
            action = UltraDpiVpnService.ACTION_STOP
        }
        startService(intent)
        isConnected = false
        updateUi()
    }

    private fun updateUi() {
        if (isConnected) {
            btnToggle.text = "ВЫКЛЮЧИТЬ"
            btnToggle.setBackgroundColor(getColor(R.color.color_active))
            tvStatus.text = "ОБХОД АКТИВЕН"
            tvStatus.setTextColor(getColor(R.color.color_active))
            tvSubtitle.text = "Трафик защищен локально • 100% Скорость"
        } else {
            btnToggle.text = "ВКЛЮЧИТЬ"
            btnToggle.setBackgroundColor(getColor(R.color.color_inactive))
            tvStatus.text = "ОТКЛЮЧЕНО"
            tvStatus.setTextColor(getColor(R.color.color_text_muted))
            tvSubtitle.text = "Нажмите большую кнопку для старта"
        }
    }
}
