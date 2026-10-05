package com.example.ecoroute

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val factorCar = 0.20
    private val factorMotorcycle = 0.20
    private val factorJeepneyBus = 0.09
    private val factorWalkBike = 0.0

    // Paste your FreeLLMAPI unified key here (starts with freellmapi-)
    private val apiKey = "freellmapi-4901eff6a8a5fa99b45467f0154c37e07831bab30e967c33"

    // 10.0.2.2 is how the Android emulator reaches your computer
    private val apiUrl = "http://localhost:31415/v1/chat/completions"
    private lateinit var etDistance: EditText
    private lateinit var rgTransportMode: RadioGroup
    private lateinit var btnCalculate: Button
    private lateinit var btnReset: Button
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDistance = findViewById(R.id.etDistance)
        rgTransportMode = findViewById(R.id.rgTransportMode)
        btnCalculate = findViewById(R.id.btnCalculate)
        btnReset = findViewById(R.id.btnReset)
        tvResult = findViewById(R.id.tvResult)

        btnCalculate.setOnClickListener { calculateCarbon() }
        btnReset.setOnClickListener { resetForm() }
    }

    private fun calculateCarbon() {
        val distanceInput = etDistance.text.toString().trim()

        if (distanceInput.isEmpty()) {
            etDistance.error = "Please enter a distance"
            tvResult.text = ""
            return
        }

        val distance = distanceInput.toDoubleOrNull()
        if (distance == null) {
            etDistance.error = "Enter a valid number"
            tvResult.text = ""
            return
        }

        if (distance <= 0) {
            etDistance.error = "Distance must be greater than 0"
            tvResult.text = ""
            return
        }

        val selectedId = rgTransportMode.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a transport mode", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedMode: RadioButton = findViewById(selectedId)
        val modeName = selectedMode.text.toString()
        val factor = getFactorForMode(selectedId)

        val emissions = distance * factor
        val carBaseline = distance * factorCar
        val saved = (carBaseline - emissions).coerceAtLeast(0.0)

        val result = String.format(
            Locale.getDefault(),
            "Mode: %s\nEstimated CO2: %.2f kg\nCO2 saved vs. driving: %.2f kg",
            modeName, emissions, saved
        )

        tvResult.text = result
        getAiTip("I traveled $distance km by $modeName. Give one short tip to reduce my carbon emissions. Use simple English, maximum 2 sentences.")
    }

    private fun getFactorForMode(radioButtonId: Int): Double {
        return when (radioButtonId) {
            R.id.rbWalkBike -> factorWalkBike
            R.id.rbMotorcycle -> factorMotorcycle
            R.id.rbJeepneyBus -> factorJeepneyBus
            else -> factorCar
        }
    }

    private fun resetForm() {
        etDistance.text.clear()
        etDistance.error = null
        rgTransportMode.clearCheck()
        tvResult.text = ""
    }

    private fun getAiTip(prompt: String) {
        // Remember the calculator result so the tip can be added under it
        val baseResult = tvResult.text.toString()
        tvResult.text = "$baseResult\n\nGetting AI tip..."

        Thread {
            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000   // 10 seconds to connect
                conn.readTimeout = 60000      // 60 seconds to wait for the answer
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true

                val message = JSONObject()
                    .put("role", "user")
                    .put("content", prompt)
                val body = JSONObject()
                    .put("model", "auto")
                    .put("max_tokens", 500)
                    .put("messages", JSONArray().put(message))

                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    runOnUiThread {
                        tvResult.text = "$baseResult\n\nAI tip failed ($code): $response"
                    }
                    return@Thread
                }

                val answer = JSONObject(response)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                runOnUiThread { tvResult.text = "$baseResult\n\nTip: $answer" }
            } catch (e: Exception) {
                runOnUiThread {
                    tvResult.text =
                        "$baseResult\n\nAI tip failed: ${e.javaClass.simpleName} ${e.message}"
                }
            }
        }.start()
    }
}