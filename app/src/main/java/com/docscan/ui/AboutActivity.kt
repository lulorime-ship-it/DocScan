package com.docscan.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.docscan.R
import com.google.android.material.appbar.MaterialToolbar

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<ImageView>(R.id.ivQrXmr).setImageResource(R.drawable.qr_xmr)
        findViewById<ImageView>(R.id.ivQrUsdtTrc20).setImageResource(R.drawable.qr_usdt_trc20)
        findViewById<ImageView>(R.id.ivQrUsdtErc20).setImageResource(R.drawable.qr_usdt_erc20)

        setupCopyOnClick(findViewById(R.id.tvEmail), "邮箱已复制")
        setupCopyOnClick(findViewById(R.id.tvXmrAddr), "XMR地址已复制")
        setupCopyOnClick(findViewById(R.id.tvUsdtTrc20Addr), "USDT TRC20地址已复制")
        setupCopyOnClick(findViewById(R.id.tvUsdtErc20Addr), "USDT ERC20地址已复制")
    }

    private fun setupCopyOnClick(textView: TextView, toastMsg: String) {
        textView.setOnClickListener {
            val text = textView.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("copy", text))
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
