package com.docscan

import android.app.Application
import android.content.Context
import android.util.Log
import com.docscan.util.LocaleHelper
import org.opencv.android.OpenCVLoader

class DocScanApp : Application() {

    companion object {
        private const val TAG = "DocScan"
        var isOpenCVInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        initOpenCV()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    private fun initOpenCV() {
        val success = OpenCVLoader.initDebug()
        isOpenCVInitialized = success
        if (success) {
            Log.d(TAG, "OpenCV initialized successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed, will use fallback detection")
        }
    }
}
