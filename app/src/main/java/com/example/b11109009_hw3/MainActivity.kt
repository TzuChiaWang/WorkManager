package com.example.b11109009_hw3

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.b11109009_hw3.ui.theme.MyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    val notificationManagerCompat = NotificationManagerCompat.from(context)
                    //畫面導航
                    NavHost(navController = navController, startDestination = "notification") {
                        composable("main") { TakePictureAndShow(navController) }
                        composable("notification") { CheckScreen(navController) }
                        composable("blur/{imageUri}") { backStackEntry ->
                            val imageUri = backStackEntry.arguments?.getString("imageUri")
                            if (imageUri != null) {
                                BlurScreen(imageUri,navController)
                            }
                        }
                    }
                    //檢查是否擁有通知權限
                    LaunchedEffect(key1 = true) {
                        if (notificationManagerCompat.areNotificationsEnabled()) {
                            navController.navigate("main")
                        } else {
                            navController.navigate("notification")
                        }

                    }
                }
            }
        }
    }
}


@Composable
fun TakePictureAndShow(navController:NavController) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    //拍照成功後跳轉模糊畫面
    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess&& imageUri!=null) {
                val encodedImageUri = Uri.encode(imageUri.toString())
                navController.navigate("blur/${encodedImageUri}")
            } else {
                imageUri = null
            }
        }
    //選擇照片成功後跳轉模糊畫面
    val pickPictureLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            imageUri = uri
            if (uri != null) {
                val encodedUri = Uri.encode(uri.toString())
                navController.navigate("blur/${encodedUri}")
            }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.welcome),
            contentDescription = null,
            modifier = Modifier
                .padding(5.dp)
                .fillMaxWidth()
                .height(600.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))
        //拍照並儲存
        Button(
            onClick = {
                val photoUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues()
                )
                imageUri = photoUri
                if (photoUri != null) {
                    takePictureLauncher.launch(photoUri)
                }
            },
            modifier = Modifier
                .height(70.dp)
                .width(350.dp)
        ) {
            Text("Take Picture", fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(50.dp))
        //從相簿選取照片
        Button(
            onClick = {
                pickPictureLauncher.launch("image/*")
            },
            modifier = Modifier
                .height(70.dp)
                .width(350.dp)
        ) {
            Text("Pick Picture", fontSize = 30.sp)
        }
    }
}

@Composable
fun BlurScreen(imageUri: String, navController:NavController) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    val options = listOf(0.001f,10.0f, 50.0f, 125.0f)
    var selectedOption by remember { mutableStateOf(options[0]) }
    val blurredBitmap = remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Button(
            onClick = { navController.navigateUp() },
            modifier = Modifier.align(Alignment.Start)
                .padding(top=10.dp)
        ) {
            Text(text = "Back", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))
        //讀取照片
        LaunchedEffect(imageUri) {
            bitmap.value = withContext(Dispatchers.IO) {
                loadBitmapFromUri(context, imageUri)
            }
        }
        //根據使用者選項，讀取模糊照片
        bitmap.value?.let { initialBitmap ->
            LaunchedEffect(selectedOption) {
                initialBitmap.let { bitmap ->
                    blurredBitmap.value = withContext(Dispatchers.Default) {
                        MyWorker.blurBitmap(bitmap, selectedOption)
                    }
                }
            }
        }
        //若使用者尚未選擇模糊選項，則顯示原照片
        if (blurredBitmap.value == null) {
            bitmap.value?.let { loadBitmap ->
                Image(
                    bitmap = loadBitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        //根據使用者選取模糊選項顯示照片
        } else {
            blurredBitmap.value?.let { blurredBitmap ->
                Image(
                    bitmap = blurredBitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        // 顯示模糊選項按鈕
        Column {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(1.dp)
                        .clickable { selectedOption = option },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == option,
                        onClick = { selectedOption = option }
                    )
                    Text(
                        text = when (option) {
                            0.001f -> "Original"
                            10.0f -> "Low Blur"
                            50.0f -> "Medium Blur"
                            125.0f -> "High Blur"
                            else -> ""
                        },
                        style = TextStyle(fontSize = 20.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        //將模糊化照片儲存至相簿
        Button(
            onClick = {
                val inputData =
                    workDataOf("imageUri" to imageUri, "blurRadius" to selectedOption)
                val blurRequest = OneTimeWorkRequestBuilder<MyWorker>()
                    .setInputData(inputData)
                    .build()
                WorkManager.getInstance(context).enqueue(blurRequest)
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        ) {
            Text("SAVE", fontSize = 20.sp)
        }
    }
}

suspend fun loadBitmapFromUri(context: Context, imageUri: String?): Bitmap {
    val uri = Uri.parse(imageUri)
    return withContext(Dispatchers.IO) {
        val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = parcelFileDescriptor?.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor?.close()
        image
    }
}

@Composable
fun CheckScreen(navController: NavController) {
    val context = LocalContext.current
    var showOpenNotificationDialog: Boolean by remember { mutableStateOf(false) }
    val notificationManagerCompat = NotificationManagerCompat.from(context)
    //彈窗跳轉頁面
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                openNotificationAction(context)
                navController.navigate("main")
            } else {
                noNotificationAction(context)
            }
        }
    Image(
        painter = painterResource(id = R.drawable.taiwantechim),
        contentDescription = null,
        modifier = Modifier
            .padding(5.dp)
            .fillMaxWidth()
            .height(600.dp)
            .clip(RoundedCornerShape(50.dp))
    )
    //點擊檢查推播權限按鈕，按下有權限限跳轉主畫面，無則顯示彈窗
    Button(
        onClick = {
        if (notificationManagerCompat.areNotificationsEnabled()) {
            openNotificationAction(context)
            navController.navigate("main")
        } else {
            showOpenNotificationDialog = true
        }
    },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 650.dp)

    ) {
        Text(text = "檢查推播權限", fontSize = 20.sp)
    }
//onDismiss 彈窗消失
//launcher 系統通知頁回傳
    if (showOpenNotificationDialog) {
        OpenNotificationDialog(
            onDismiss = { showOpenNotificationDialog = false },
            launcher = launcher
        )
    }
}

@Composable
private fun OpenNotificationDialog(
    onDismiss: () -> Unit,
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
) {
    val context = LocalContext.current
    //彈窗UI
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "開啟推播權限", modifier = Modifier.padding(vertical = 20.dp), fontSize = 30.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
            ) {
                Button(
                    onClick = {
                        noNotificationAction(context)
                        onDismiss.invoke()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                ) {
                    Text(text = "放棄", fontSize = 20.sp)
                }
                //跳轉權限頁面
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        launcher.launch(intent)
                        onDismiss.invoke()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    Text(text = "開啟", fontSize = 20.sp)
                }
            }
        }
    }
}

private fun openNotificationAction(context: Context) {
    Toast.makeText(context, "推播權限已開", Toast.LENGTH_LONG).show()
    Toast.makeText(context, "歡迎進入程式", Toast.LENGTH_LONG).show()
}

private fun noNotificationAction(context: Context) {
    Toast.makeText(context, "推播權限未開", Toast.LENGTH_SHORT).show()
    Toast.makeText(context, "權限開啟後將進入程式", Toast.LENGTH_LONG).show()
}












