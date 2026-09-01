package com.changba.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212) // Flet DARK 預設深色背景
                ) {
                    ChangbaMainScreen()
                }
            }
        }
    }
}

// ==========================================
// 🎨 核心 UI 主畫面（1對1 還原您的 Flet 佈局）
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangbaMainScreen() {
    val context = LocalContext.current
    
    // 狀態變數管理（對應 Flet 的變數 value 調整）
    var wechatPath by remember { mutableStateOf("/storage/emulated/0/Download/WeChat") }
    var changbaPath by remember { mutableStateOf("/storage/emulated/0/唱吧本地作品备份") }
    var statusText by remember { mutableStateOf("提示：Android 13 請先點擊下方執行按鈕，跳轉並開啟「允許管理所有檔案」才能正常運作。") }
    var statusColor by remember { mutableStateOf(Color(0xFFFFB300)) } // Amber 琥珀色

    // 彈跳視窗控制開關
    var showWechatDialog by remember { mutableStateOf(false) }
    var showChangbaDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "唱吧音訊一鍵替換工具", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        HorizontalDivider(color = Color(0xFF333333))

        // 1. 微信輸入框與按鈕
        OutlinedTextField(
            value = wechatPath,
            onValueChange = { wechatPath = it },
            label = { Text("1. 來源：微信音訊檔案路徑", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Button(
            onClick = { showWechatDialog = true },
            modifier = Modifier.width(220.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("1. 選擇微信 MP3 檔案", color = Color.White)
        }

        Spacer(modifier = Modifier.height(5.dp))

        // 2. 唱吧輸入框與按鈕
        OutlinedTextField(
            value = changbaPath,
            onValueChange = { changbaPath = it },
            label = { Text("2. 目標：唱吧備份資料夾路徑", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Button(
            onClick = { showChangbaDialog = true },
            modifier = Modifier.width(220.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("2. 選擇唱吧備份資料夾", color = Color.White)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 執行替換核心按鈕（💥 整合 Android 13 原生權限動態跳轉）
        Button(
            onClick = {
                // 檢查並觸發 Android 13 所有檔案存取權跳轉
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        statusText = "⏳ 偵測到無權限，已為您自動跳轉，請開啟後返回 App。"
                        statusColor = Color(0xFFFF9800) // Orange
                        
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}") // 🔴 動態綁定合規包名
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 萬一 package 解析失敗的保險跳轉
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                        return@Button
                    }
                }

                // 執行您的原生替換邏輯（對應 btn_replace_click）
                statusText = "正在處理中，請稍候..."
                statusColor = Color(0xFFFF9800)

                val result = executeReplaceLogic(wechatPath, changbaPath)
                if (result.startsWith("✨")) {
                    statusText = result
                    statusColor = Color(0xFF4CAF50) // Green
                } else {
                    statusText = result
                    statusColor = Color(0xFFF44336) // Red
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)), // 藍色按鈕
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("3: 執行取代替換", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 狀態文字提示框（100% 還原您的 txt_status 樣式）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF333333), shape = RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                fontSize = 13.sp,
                color = statusColor,
                fontWeight = FontWeight.Normal
            )
        }
    }

    // ==========================================
    // 📁 微信檔案選擇器彈窗 (AlertDialog)
    // ==========================================
    if (showWechatDialog) {
        var fileList by remember { mutableStateOf(listOf<File>()) }
        var errorMsg by remember { mutableStateOf("") }

        LaunchedEffect(wechatPath) {
            try {
                val targetDir = File(wechatPath)
                val finalDir = if (targetDir.isDirectory) targetDir else targetDir.parentFile
                if (finalDir != null && finalDir.exists()) {
                    val files = finalDir.listFiles { file ->
                        file.isFile && (file.extension in listOf("mp3", "m4a", "aac", "wav"))
                    }
                    fileList = files?.toList() ?: emptyList()
                } else {
                    errorMsg = "❌ 找不到目錄！\n原因：路徑不存在，或手機未開啟「管理所有檔案」權限。"
                    statusText = "❌ 讀取失敗！請確認是否已點擊執行按鈕進行權限跳轉。"
                    statusColor = Color.Red
                }
            } catch (ex: Exception) {
                errorMsg = "💥 系統阻擋或讀取錯誤:\n${ex.message}"
            }
        }

        Dialog(onDismissRequest = { showWechatDialog = false }) {
            Card(
                modifier = Modifier.size(350.dp, 350.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. 選擇微信音訊檔案", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                        } else if (fileList.isEmpty()) {
                            Text("該目錄下目前沒有找到音訊檔案。", color = Color.Gray, fontSize = 13.sp)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(fileList) { file ->
                                    Text(
                                        text = "🎵 ${file.name}",
                                        color = Color(0xFF2196F3),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                wechatPath = file.absolutePath
                                                showWechatDialog = false
                                            }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { showWechatDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("關閉") }
                }
            }
        }
    }
