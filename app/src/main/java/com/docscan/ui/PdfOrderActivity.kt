package com.docscan.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.docscan.R
import com.docscan.export.FileHelper
import com.docscan.export.PdfExporter
import com.docscan.util.AppSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfOrderActivity : AppCompatActivity() {

    private lateinit var rvPageOrder: RecyclerView
    private lateinit var tvPageCount: TextView
    private lateinit var btnMoveUp: MaterialButton
    private lateinit var btnMoveDown: MaterialButton
    private lateinit var btnMoveToFirst: MaterialButton
    private lateinit var btnMoveToLast: MaterialButton
    private lateinit var btnRemovePage: MaterialButton
    private lateinit var btnSavePdf: MaterialButton

    private val pagePaths = mutableListOf<String>()
    private lateinit var adapter: PageOrderAdapter
    private var selectedPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_order)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvPageOrder = findViewById(R.id.rvPageOrder)
        tvPageCount = findViewById(R.id.tvPageCount)
        btnMoveUp = findViewById(R.id.btnMoveUp)
        btnMoveDown = findViewById(R.id.btnMoveDown)
        btnMoveToFirst = findViewById(R.id.btnMoveToFirst)
        btnMoveToLast = findViewById(R.id.btnMoveToLast)
        btnRemovePage = findViewById(R.id.btnRemovePage)
        btnSavePdf = findViewById(R.id.btnSavePdf)

        val paths = intent.getStringArrayListExtra("page_paths")
        if (paths != null) pagePaths.addAll(paths)

        if (pagePaths.isEmpty()) {
            Toast.makeText(this, "没有选择文件", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = PageOrderAdapter(pagePaths) { position ->
            selectedPosition = position
            adapter.setSelected(position)
        }

        rvPageOrder.layoutManager = LinearLayoutManager(this)
        rvPageOrder.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                val item = pagePaths.removeAt(from)
                pagePaths.add(to, item)
                adapter.notifyItemMoved(from, to)
                selectedPosition = to
                updatePageCount()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(rvPageOrder)

        btnMoveUp.setOnClickListener {
            if (selectedPosition > 0) {
                val item = pagePaths.removeAt(selectedPosition)
                pagePaths.add(selectedPosition - 1, item)
                selectedPosition--
                adapter.notifyItemRangeChanged(selectedPosition, 2)
                updatePageCount()
                rvPageOrder.scrollToPosition(selectedPosition)
            }
        }

        btnMoveDown.setOnClickListener {
            if (selectedPosition < pagePaths.size - 1 && selectedPosition >= 0) {
                val item = pagePaths.removeAt(selectedPosition)
                pagePaths.add(selectedPosition + 1, item)
                selectedPosition++
                adapter.notifyItemRangeChanged(selectedPosition - 1, 2)
                updatePageCount()
                rvPageOrder.scrollToPosition(selectedPosition)
            }
        }

        btnMoveToFirst.setOnClickListener {
            if (selectedPosition > 0) {
                val item = pagePaths.removeAt(selectedPosition)
                pagePaths.add(0, item)
                selectedPosition = 0
                adapter.notifyDataSetChanged()
                updatePageCount()
                rvPageOrder.scrollToPosition(0)
            }
        }

        btnMoveToLast.setOnClickListener {
            if (selectedPosition >= 0 && selectedPosition < pagePaths.size - 1) {
                val item = pagePaths.removeAt(selectedPosition)
                pagePaths.add(pagePaths.size, item)
                selectedPosition = pagePaths.size - 1
                adapter.notifyDataSetChanged()
                updatePageCount()
                rvPageOrder.scrollToPosition(selectedPosition)
            }
        }

        btnRemovePage.setOnClickListener {
            if (selectedPosition >= 0 && selectedPosition < pagePaths.size) {
                pagePaths.removeAt(selectedPosition)
                if (selectedPosition >= pagePaths.size) selectedPosition = pagePaths.size - 1
                adapter.notifyDataSetChanged()
                updatePageCount()
            }
        }

        btnSavePdf.setOnClickListener { saveAsPdf() }

        if (pagePaths.isNotEmpty()) {
            selectedPosition = 0
            adapter.setSelected(0)
        }
        updatePageCount()
    }

    private fun updatePageCount() {
        tvPageCount.text = "共 ${pagePaths.size} 页，长按拖拽可调整顺序"
    }

    private fun saveAsPdf() {
        if (pagePaths.isEmpty()) {
            Toast.makeText(this, "没有页面可保存", Toast.LENGTH_SHORT).show()
            return
        }

        btnSavePdf.isEnabled = false
        btnSavePdf.text = "保存中..."

        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    PdfExporter.init(this@PdfOrderActivity)
                    val dir = File(AppSettings.getPdfSaveDir(this@PdfOrderActivity))
                    if (!dir.exists()) dir.mkdirs()
                    val file = FileHelper.createPdfFile(this@PdfOrderActivity, "DocScan")
                    val targetFile = File(dir, file.name)
                    PdfExporter.exportToPdf(this@PdfOrderActivity, pagePaths, targetFile)
                    targetFile
                }

                Toast.makeText(this@PdfOrderActivity,
                    "PDF已保存: ${pdfFile.name}\n目录: ${pdfFile.parent}", Toast.LENGTH_LONG).show()

                openPdfFile(pdfFile)
            } catch (e: Exception) {
                Toast.makeText(this@PdfOrderActivity, "PDF保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSavePdf.isEnabled = true
                btnSavePdf.text = "保存为PDF"
            }
        }
    }

    private fun openPdfFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            })
        } catch (e: Exception) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "选择PDF查看器"))
            } catch (_: Exception) {
                Toast.makeText(this, "PDF已保存到: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private class PageOrderAdapter(
        private val paths: MutableList<String>,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<PageOrderAdapter.ViewHolder>() {

        private var selectedPos = -1

        fun setSelected(pos: Int) {
            selectedPos = pos
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val path = paths[position]
            val file = File(path)
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap != null) holder.ivThumb.setImageBitmap(bitmap)

            holder.tvPageNum.text = "第 ${position + 1} 页"
            holder.tvFileName.text = file.name
            holder.tvFileSize.text = "${file.length() / 1024} KB"

            holder.itemView.setBackgroundColor(
                if (position == selectedPos) 0x334CAF50.toInt()
                else 0x00000000
            )

            holder.itemView.setOnClickListener { onSelect(position) }
        }

        override fun getItemCount() = paths.size

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.ivThumb.setImageDrawable(null)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
            val tvPageNum: TextView = view.findViewById(R.id.tvPageNum)
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        }
    }
}
