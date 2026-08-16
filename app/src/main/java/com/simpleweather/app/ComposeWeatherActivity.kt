package com.simpleweather.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * v9.90：Compose 新界面（实验性）。
 *
 * 双主题引擎：
 *   - 风格 m3   ：MaterialTheme（Material 3，蓝种子 #0061A4，与经典界面同色板）
 *   - Material 3：MaterialTheme（与经典界面同源色板）
 *
 * 与经典界面完全兼容：复用 WeatherCenter / WeatherCache / WeatherApi 等
 * 纯 Java 逻辑层，可随时在设置中切回经典 View 界面。
 */
class ComposeWeatherActivity : ComponentActivity() {

    /** 当前显示的天气状态 */
    private var city by mutableStateOf("")
    private var tempText by mutableStateOf("--°")
    private var descText by mutableStateOf("加载中…")
    private var feelsText by mutableStateOf("")
    private var detailText by mutableStateOf("")
    private var sourceText by mutableStateOf("")
    private var updating by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读缓存立即展示，弱网不空白
        val c = WeatherCenter.get().freshCache(this)
        if (c != null && c.json != null) {
            applyJson(c.json, c.city)
        }
        setContent { AppRoot() }
    }

    // ---------- 主题根 ----------

    @Composable
    fun AppRoot() {
        val dark = Theme.isDark(this@ComposeWeatherActivity)
        // Material 3：与经典界面同源色板
        val scheme = if (dark) darkColorScheme(
            primary = Color(0xFFA2C8FF),
            primaryContainer = Color(0xFF00497C),
            onPrimaryContainer = Color(0xFFA2C8FF),
            surface = Color(0xFF0D1526),
            surfaceVariant = Color(0xFF2B2D33),
        ) else lightColorScheme(
            primary = Color(0xFF0061A4),
            primaryContainer = Color(0xFFD2E4FF),
            onPrimaryContainer = Color(0xFF001D36),
            surface = Color(0xFFF6F7F9),
            surfaceVariant = Color(0xFFE6E9F0),
        )
        MaterialTheme(colorScheme = scheme) { MainScreen() }
    }

    // ---------- 主界面 ----------

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val dark = Theme.isDark(this@ComposeWeatherActivity)
        val accent = Color(Theme.accent(this@ComposeWeatherActivity))
        Scaffold(
            containerColor = Color(if (dark) 0xFF0D1526 else 0xFFF6F7F9),
            topBar = {
                TopAppBar(
                    title = { Text("简洁天气 · Compose", fontSize = 17.sp) },
                    navigationIcon = {},
                    actions = {
                        IconButton(onClick = { doRefresh() }) {
                            Icon(Icons.Default.Refresh, "刷新",
                                 tint = accent, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "设置",
                                 tint = accent, modifier = Modifier.size(22.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(Theme.textPrimary(this@ComposeWeatherActivity))
                    )
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))

                // 城市 + 温度主卡
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(Theme.surfaceContainerHigh(this@ComposeWeatherActivity))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(city.ifEmpty { "未定位" },
                             color = Color(Theme.textPrimary(this@ComposeWeatherActivity)),
                             fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(tempText,
                             color = accent,
                             fontSize = 64.sp, fontWeight = FontWeight.Bold,
                             lineHeight = 70.sp)
                        Text(descText,
                             color = Color(Theme.textPrimary(this@ComposeWeatherActivity)),
                             fontSize = 16.sp)
                        if (feelsText.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(feelsText,
                                 color = Color(Theme.textSecondary(this@ComposeWeatherActivity)),
                                 fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 详情卡
                if (detailText.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(Theme.surfaceContainerLow(this@ComposeWeatherActivity))
                        )
                    ) {
                        Text(detailText,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(16.dp),
                             color = Color(Theme.textPrimary(this@ComposeWeatherActivity)),
                             fontSize = 14.sp,
                             lineHeight = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // 刷新按钮
                Button(
                    onClick = { doRefresh() },
                    enabled = !updating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(if (dark) 0xFF001D36 else 0xFFFFFFFF)
                    )
                ) {
                    Text(if (updating) "刷新中…" else "立即刷新", fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))
                if (sourceText.isNotEmpty()) {
                    Text(sourceText,
                         color = Color(Theme.textHint(this@ComposeWeatherActivity)),
                         fontSize = 11.sp)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        if (showSettings) SettingsDialog()
    }

    // ---------- 设置弹窗（引擎 / 风格 / 深浅色） ----------

    @Composable
    fun SettingsDialog() {
        val dark = Theme.isDark(this@ComposeWeatherActivity)
        val accent = Color(Theme.accent(this@ComposeWeatherActivity))

        @Composable fun RadioRow(label: String, sub: String, selected: Boolean, onPick: () -> Unit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) Color(Theme.setCardSelected(this@ComposeWeatherActivity))
                        else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = onPick,
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = accent
                            ))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, color = Color(Theme.textPrimary(this@ComposeWeatherActivity)), fontSize = 15.sp)
                    Text(sub, color = Color(Theme.textSecondary(this@ComposeWeatherActivity)), fontSize = 11.sp)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color(Theme.surfaceContainerHigh(this@ComposeWeatherActivity)),
            titleContentColor = Color(Theme.textPrimary(this@ComposeWeatherActivity)),
            textContentColor = Color(Theme.textPrimary(this@ComposeWeatherActivity)),
            title = { Text("设置 · 测试版") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("界面引擎", fontSize = 12.sp,
                         color = Color(Theme.textSecondary(this@ComposeWeatherActivity)),
                         modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                    RadioRow("Compose 新界面", "Compose + Material 3（当前）",
                             Theme.isCompose(this@ComposeWeatherActivity), { pickEngine(Theme.ENGINE_COMPOSE) })
                    RadioRow("经典界面", "Java View · 稳定兜底",
                             !Theme.isCompose(this@ComposeWeatherActivity), { pickEngine(Theme.ENGINE_VIEW) })

                    Text("外观", fontSize = 12.sp,
                         color = Color(Theme.textSecondary(this@ComposeWeatherActivity)),
                         modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    RadioRow("跟随系统", "随系统深浅自动切换",
                             "system".equals(Theme.mode(this@ComposeWeatherActivity)), { pickMode("system") })
                    RadioRow("深色", "夜间更护眼",
                             "dark".equals(Theme.mode(this@ComposeWeatherActivity)), { pickMode("dark") })
                    RadioRow("浅色", "白天更清爽",
                             "light".equals(Theme.mode(this@ComposeWeatherActivity)), { pickMode("light") })
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("完成", color = accent)
                }
            }
        )
    }

    // ---------- 交互 ----------

    private fun pickEngine(e: String) {
        showSettings = false
        Theme.setEngine(this, e)
        if (Theme.ENGINE_VIEW.equals(e)) {
            // 切回经典界面：启动 MainActivity 并关掉自身
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            recreate()
        }
    }

    private fun pickMode(m: String) {
        showSettings = false
        Theme.setMode(this, m)
        recreate()
    }

    private fun doRefresh() {
        if (updating) return
        updating = true
        Thread {
            try {
                val c = WeatherCenter.get().freshCache(this)
                val lat = if (c != null) c.lat else 39.9042
                val lng = if (c != null) c.lng else 116.4074
                val cityName = if (c != null && c.city.isNotEmpty()) c.city else "北京"
                val json = WeatherCenter.get().fetchWeather(this, lat, lng, cityName)
                handler.post {
                    if (json != null) {
                        applyJson(json.toString(), cityName)
                        WeatherCache.save(this, json.toString(), cityName, lat, lng)
                    } else {
                        Toast.makeText(this, "刷新失败，请检查网络", Toast.LENGTH_SHORT).show()
                    }
                    updating = false
                }
            } catch (t: Throwable) {
                handler.post {
                    Toast.makeText(this, "刷新失败：" + t.message, Toast.LENGTH_SHORT).show()
                    updating = false
                }
            }
        }.start()
    }

    // ---------- JSON 解析（与经典界面同字段，Open-Meteo） ----------

    private fun applyJson(json: String, cityName: String) {
        try {
            val j = JSONObject(json)
            val cur = j.getJSONObject("current")
            val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
            val feels = Math.round(cur.getDouble("apparent_temperature")).toInt()
            val code = cur.optInt("weather_code", 0)
            val isDay = cur.optInt("is_day", 1) == 1
            val hum = cur.optInt("relative_humidity_2m", -1)
            val wind = cur.optDouble("wind_speed_10m", -1.0)
            val uv = cur.optDouble("uv_index", -1.0)
            val cloud = cur.optInt("cloud_cover", -1)

            city = cityName
            tempText = "$temp°"
            descText = WeatherApi.text(code) + "  " + WeatherApi.icon(code, isDay)
            feelsText = "体感 $feels°"
            val sb = StringBuilder()
            if (hum >= 0) sb.append("湿度 $hum%")
            if (wind >= 0) {
                if (sb.isNotEmpty()) sb.append("  ·  ")
                sb.append("风 ${Math.round(wind)} km/h")
            }
            if (uv >= 0) {
                if (sb.isNotEmpty()) sb.append("  ·  ")
                sb.append("UV $uv")
            }
            if (cloud >= 0) {
                if (sb.isNotEmpty()) sb.append("  ·  ")
                sb.append("云量 $cloud%")
            }
            detailText = sb.toString()
            sourceText = "数据来源：Open-Meteo  ·  " + j.optString("timezone", "")
        } catch (t: Throwable) {
            detailText = ""
        }
    }
}
