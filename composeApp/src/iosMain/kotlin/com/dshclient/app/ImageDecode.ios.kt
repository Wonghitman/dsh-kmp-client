package com.dshclient.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.create
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageBase64(base64: String): ImageBitmap? {
    return try {
        val data = NSData.create(
            base64String = base64,
            options = NSDataBase64DecodingIgnoreUnknownCharacters,
        ) ?: return null
        val image = UIImage.imageWithData(data) ?: return null
        image.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
