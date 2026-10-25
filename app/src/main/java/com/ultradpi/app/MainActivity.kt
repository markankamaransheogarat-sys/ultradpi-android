package com.ultradpi.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var isConnected = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var spinnerPreset: Spinner
    private lateinit var tvPresetHint: TextView
    private lateinit var etCustomArgs: EditText
    private lateinit var cbIpv6: CheckBox

    private val PREFS_NAME = "ultradpi_prefs"
    private val PREF_KEY_PRESET = "selected_preset_idx"
    private val PREF_KEY_IPV6 = "enable_ipv6_route"
    private val PREF_KEY_CUSTOM_ARGS = "custom_cmd_args"

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
        spinnerPreset = findViewById(R.id.spinnerPreset)
        tvPresetHint = findViewById(R.id.tvPresetHint)
        etCustomArgs = findViewById(R.id.etCustomArgs)
        cbIpv6 = findViewById(R.id.cbIpv6)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setupPreferences()

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

    private fun setupPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val titles = BypassPreset.entries.map { it.title }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerPreset.adapter = adapter

        val savedIndex = prefs.getInt(PREF_KEY_PRESET, 0)
        spinnerPreset.setSelection(savedIndex)
        tvPresetHint.text = BypassPreset.fromIndex(savedIndex).description

        val savedIpv6 = prefs.getBoolean(PREF_KEY_IPV6, true)
        cbIpv6.isChecked = savedIpv6
        cbIpv6.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_IPV6, isChecked).apply()
        }

        val savedCustomArgs = prefs.getString(PREF_KEY_CUSTOM_ARGS, "-s1 -d1 -a1") ?: "-s1 -d1 -a1"
        etCustomArgs.setText(savedCustomArgs)
        etCustomArgs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString(PREF_KEY_CUSTOM_ARGS, s?.toString() ?: "").apply()
            }
        })

        updateCustomVisibility(savedIndex)

        spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(PREF_KEY_PRESET, position).apply()
                tvPresetHint.text = BypassPreset.fromIndex(position).description
                updateCustomVisibility(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateCustomVisibility(position: Int) {
        val isCustom = (position == BypassPreset.CUSTOM.ordinal)
        etCustomArgs.visibility = if (isCustom) View.VISIBLE else View.GONE
    }

    private fun startVpnService() {
        val selectedPresetIdx = spinnerPreset.selectedItemPosition
        val enableIpv6 = cbIpv6.isChecked
        val customArgs = etCustomArgs.text.toString()

        val intent = Intent(this, UltraDpiVpnService::class.java).apply {
            action = UltraDpiVpnService.ACTION_START
            putExtra(UltraDpiVpnService.EXTRA_PRESET, selectedPresetIdx)
            putExtra(UltraDpiVpnService.EXTRA_ENABLE_IPV6, enableIpv6)
            putExtra(UltraDpiVpnService.EXTRA_CUSTOM_ARGS, customArgs)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isConnected = true
            updateUi()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, UltraDpiVpnService::class.java).apply {
            action = UltraDpiVpnService.ACTION_STOP
        }
        try {
            startService(intent)
            isConnected = false
            updateUi()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun updateUi() {
        if (isConnected) {
            btnToggle.text = "ВЫКЛЮЧИТЬ"
            btnToggle.setBackgroundColor(getColor(R.color.color_active))
            tvStatus.text = "ОБХОД АКТИВЕН"
            tvStatus.setTextColor(getColor(R.color.color_active))
            tvSubtitle.text = "Трафик защищен локально • 100% Скорость"
            spinnerPreset.isEnabled = false
            cbIpv6.isEnabled = false
            etCustomArgs.isEnabled = false
        } else {
            btnToggle.text = "ВКЛЮЧИТЬ"
            btnToggle.setBackgroundColor(getColor(R.color.color_inactive))
            tvStatus.text = "ОТКЛЮЧЕНО"
            tvStatus.setTextColor(getColor(R.color.color_text_muted))
            tvSubtitle.text = "Нажмите большую кнопку для старта"
            spinnerPreset.isEnabled = true
            cbIpv6.isEnabled = true
            etCustomArgs.isEnabled = true
        }
    }
}
