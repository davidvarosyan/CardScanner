package com.fastcredit.fcbank.scanner.sdk.ndk

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import com.fastcredit.fcbank.scanner.BuildConfig
import java.io.*

internal class NeuroDataHelper(context: Context) {
    val dataBasePath: File
    private val mAssetManager: AssetManager

    init {
        val appContext = context.applicationContext
        mAssetManager = appContext.assets
        dataBasePath = File(
            context.cacheDir,
            Constants.MODEL_DIR + "/" + Constants.NEURO_DATA_VERSION.toString()
        )
    }

    @Throws(IOException::class)
    fun unpackAssets() {
        unpackFileOrDir("")
    }

    @Throws(IOException::class)
    private fun unpackFileOrDir(assetsPath: String) {
        val assets: Array<String>? = mAssetManager.list(Constants.MODEL_DIR + assetsPath)
        if (assets?.size == 0) {
            copyAssetToCacheDir(assetsPath)
        } else {
            val dir = getDstPath(assetsPath)
            if (!dir.exists()) {
                if (DBG) Log.v(TAG, "Create cache dir " + dir.absolutePath)
                dir.mkdirs()
            }
            assets?.let {
                for (i in assets.indices) {
                    unpackFileOrDir(assetsPath + "/" + assets[i])
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun copyAssetToCacheDir(assetsPath: String): String {
        val f = getDstPath(assetsPath)
        var `is`: InputStream? = null
        var os: OutputStream? = null
        try {
            `is` = mAssetManager.open(Constants.MODEL_DIR + assetsPath)
            val fileSize = `is`.available()
            if (f.length() != fileSize.toLong()) {
                if (DBG) Log.d(TAG, "copyAssetToCacheDir() rewrite file $assetsPath")
                os = FileOutputStream(f, false)
                val buffer = ByteArray(1024)
                var len: Int
                while (`is`.read(buffer).also { len = it } > 0) {
                    os.write(buffer, 0, len)
                }
            }
        } finally {
            try {
                `is`?.close()
            } catch (ioe: IOException) {
                // IGNORE
            }
            try {
                if (os != null) {
                    os.flush()
                    os.close()
                }
            } catch (ioe: IOException) {
                // IGNORE
            }
        }
        return f.path
    }

    private fun getDstPath(assetsPath: String): File {
        return File(dataBasePath, assetsPath)
    }

    companion object {
        private val DBG = BuildConfig.DEBUG
        private const val TAG = "RecognitionCore"
    }
}