package com.dshclient.app

import androidx.compose.ui.graphics.ImageBitmap

/** 解码 base64 图片为 Compose ImageBitmap（平台实现） */
expect fun decodeImageBase64(base64: String): ImageBitmap?
