package com.example.b11109009_hw3.ui.theme

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.b11109009_hw3.R
import com.example.b11109009_hw3.loadBitmapFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MyWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    private val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager

    @SuppressLint("SuspiciousIndentation")
        override suspend fun doWork(): Result {
            val imageUri = inputData.getString("imageUri")
            val blurRadius = inputData.getFloat("blurRadius", 0f)
            return try {
                //讀取照片
                val bitmap = loadBitmapFromUri(applicationContext, imageUri)

                //模糊化讀取的照片
                val blurredBitmap = blurBitmap(bitmap, blurRadius)

                //儲存模糊後相片
                val outputUri = saveBitmapToFile(applicationContext, blurredBitmap)

                //創立DATA轉換URI成字串
                val outputData = workDataOf("outputUri" to outputUri.toString())

                //傳送模糊成功通知
                val areNotificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()

                if (areNotificationsEnabled) {
                    sendNotification("模糊完成！", "照片已保存至相簿。")
                }
                Result.success(outputData)
            } catch (e: Exception) {
                Result.failure()
            }
        }
    //模糊功能
    companion object {
        suspend fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap = withContext(
             Dispatchers.Default
     ) {
         val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
         val canvas = Canvas(output)
         val paint = Paint()
         paint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
         canvas.drawBitmap(bitmap, 0f, 0f, paint)
            output
        }
    }
    //儲存模糊後照片
    private suspend fun saveBitmapToFile(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "blurred_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        context.contentResolver.openOutputStream(uri!!).use { outputStream ->
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        uri
    }

    private fun sendNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Blur Channel"
            val descriptionText = "Channel for image blur notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("blur_channel", name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
        val bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.heart)
        val notification = NotificationCompat.Builder(applicationContext, "blur_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(1, notification)
    }
}

