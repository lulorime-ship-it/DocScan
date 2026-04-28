package com.docscan.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.docscan.R
import com.docscan.export.FileHelper
import com.docscan.export.PdfExporter
import com.docscan.util.AppSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var rvPages: RecyclerView
    private lateinit var tvEmptyHint: TextView
    private lateinit var fabScan: FloatingActionButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnViewPdfs: MaterialButton
    private lateinit var btnSelectMode: MaterialButton
    private lateinit var llSelectBar: View
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnDeselectAll: MaterialButton
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnMergePdf: MaterialButton
    private lateinit var btnCancelSelect: MaterialButton

    private val scanFiles = mutableListOf<File>()
    private lateinit var pageAdapter: PageAdapter

    private var selectMode = false
    private val selectedFiles = mutableSetOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.app_name)

        rvPages = findViewById(R.id.rvPages)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        fabScan = findViewById(R.id.fabScan)
        btnSettings = findViewById(R.id.btnSettings)
        btnViewPdfs = findViewById(R.id.btnViewPdfs)
        btnSelectMode = findViewById(R.id.btnSelectMode)
        llSelectBar = findViewById(R.id.llSelectBar)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnMergePdf = findViewById(R.id.btnMergePdf)
        btnCancelSelect = findViewById(R.id.btnCancelSelect)

        pageAdapter = PageAdapter(
            onClick = { file ->
                if (selectMode) {
                    toggleSelection(file)
                } else {
                    openFile(file)
                }
            },
            onLongClick = { file ->
                if (!selectMode) showDeleteDialog(file)
            },
            onSelectChanged = { file, selected ->
                if (selected) selectedFiles.add(file) else selectedFiles.remove(file)
                updateSelectedCount()
            }
        )

        rvPages.layoutManager = GridLayoutManager(this, 3)
        rvPages.adapter = pageAdapter

        fabScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java).apply {
                putExtra(ScanActivity.EXTRA_MULTI_PAGE, true)
            })
        }

        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnViewPdfs.setOnClickListener { openPdfDirectory() }

        btnSelectMode.setOnClickListener {
            selectMode = !selectMode
            if (selectMode) {
                btnSelectMode.text = "取消"
                llSelectBar.visibility = View.VISIBLE
                selectedFiles.clear()
                pageAdapter.setSelectMode(true)
                updateSelectedCount()
            } else {
                exitSelectMode()
            }
        }

        btnSelectAll.setOnClickListener {
            selectedFiles.clear()
            selectedFiles.addAll(scanFiles)
            pageAdapter.selectAll()
            updateSelectedCount()
        }

        btnDeselectAll.setOnClickListener {
            selectedFiles.clear()
            pageAdapter.deselectAll()
            updateSelectedCount()
        }

        btnMergePdf.setOnClickListener {
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sortedFiles = selectedFiles.sortedBy { scanFiles.indexOf(it) }
            val paths = sortedFiles.map { it.absolutePath }
            val intent = Intent(this, PdfOrderActivity::class.java).apply {
                putStringArrayListExtra("page_paths", ArrayList(paths))
            }
            startActivity(intent)
            exitSelectMode()
        }

        btnCancelSelect.setOnClickListener { exitSelectMode() }

        loadScannedFiles()
    }

    override fun onResume() {
        super.onResume()
        loadScannedFiles()
    }

    private fun exitSelectMode() {
        selectMode = false
        btnSelectMode.text = "选择"
        llSelectBar.visibility = View.GONE
        selectedFiles.clear()
        pageAdapter.setSelectMode(false)
    }

    private fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        pageAdapter.toggleSelection(file)
        updateSelectedCount()
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = "已选 ${selectedFiles.size} 个文件"
    }

    private fun loadScannedFiles() {
        val scanDir = FileHelper.getScanDir(this)
        scanFiles.clear()

        val files = scanDir.listFiles()
            ?.filter { it.extension in listOf("jpg", "jpeg", "png") }
            ?.sortedByDescending { it.lastModified() }

        if (files != null) scanFiles.addAll(files)

        pageAdapter.updateFiles(scanFiles)

        if (scanFiles.isEmpty()) {
            tvEmptyHint.visibility = View.VISIBLE
            rvPages.visibility = View.GONE
        } else {
            tvEmptyHint.visibility = View.GONE
            rvPages.visibility = View.VISIBLE
        }
    }

    private fun openFile(file: File) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putStringArrayListExtra(ScanActivity.EXTRA_CROPPED_PATHS, arrayListOf(file.absolutePath))
        })
    }

    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle("删除文档")
            .setMessage("确定要删除此扫描文档吗？")
            .setPositiveButton("删除") { _, _ ->
                if (file.delete()) { loadScannedFiles(); Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openPdfDirectory() {
        val pdfDir = File(AppSettings.getPdfSaveDir(this))
        if (!pdfDir.exists()) pdfDir.mkdirs()
        val pdfFiles = pdfDir.listFiles()?.filter { it.extension == "pdf" }?.sortedByDescending { it.lastModified() }
        if (pdfFiles.isNullOrEmpty()) {
            Toast.makeText(this, "没有找到已保存的PDF文件\n目录: ${pdfDir.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }
        if (pdfFiles.size == 1) { openPdfFile(pdfFiles[0]); return }
        val fileNames = pdfFiles.map { "${it.name} (${it.length() / 1024}KB)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择PDF文件")
            .setItems(fileNames) { _, which -> openPdfFile(pdfFiles[which]) }
            .setNeutralButton("打开目录") { _, _ -> openDirectoryInFileManager(pdfDir.absolutePath) }
            .show()
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
                    setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "选择PDF查看器"))
            } catch (_: Exception) {
                Toast.makeText(this, "PDF路径: ${file.absolutePath}\n请使用文件管理器打开", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDirectoryInFileManager(path: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(path), "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "PDF保存目录: $path\n请使用文件管理器导航到该目录", Toast.LENGTH_LONG).show()
        }
    }

    private class PageAdapter(
        private val onClick: (File) -> Unit,
        private val onLongClick: (File) -> Unit,
        private val onSelectChanged: (File, Boolean) -> Unit
    ) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        private val files = mutableListOf<File>()
        private var isSelectMode = false
        private val selectedSet = mutableSetOf<File>()

        fun updateFiles(newFiles: List<File>) {
            files.clear(); files.addAll(newFiles); notifyDataSetChanged()
        }

        fun setSelectMode(enabled: Boolean) {
            isSelectMode = enabled
            selectedSet.clear()
            notifyDataSetChanged()
        }

        fun selectAll() {
            selectedSet.clear(); selectedSet.addAll(files); notifyDataSetChanged()
        }

        fun deselectAll() {
            selectedSet.clear(); notifyDataSetChanged()
        }

        fun toggleSelection(file: File) {
            if (selectedSet.contains(file)) selectedSet.remove(file) else selectedSet.add(file)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val file = files[position]
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) holder.imageView.setImageBitmap(bitmap)
            holder.pageNumber.text = "${position + 1}"

            val isSelected = selectedSet.contains(file)

            holder.checkBox.visibility = if (isSelectMode) View.VISIBLE else View.GONE
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = isSelected
            holder.checkBox.setOnCheckedChangeListener { _, checked ->
                onSelectChanged(file, checked)
            }

            holder.selectedOverlay.visibility = if (isSelectMode && isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onClick(file) }
            holder.itemView.setOnLongClickListener { onLongClick(file); true }
        }

        override fun getItemCount() = files.size

        override fun onViewRecycled(holder: PageViewHolder) {
            super.onViewRecycled(holder)
            holder.imageView.setImageDrawable(null)
        }

        class PageViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.ivPageThumb)
            val pageNumber: TextView = view.findViewById(R.id.tvPageNumber)
            val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
            val selectedOverlay: View = view.findViewById(R.id.selectedOverlay)
        }
    }
}
