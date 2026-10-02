package com.zaminchaman.app.data.repo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.zaminchaman.app.core.formatMoney
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun cellText(v: Any): String = if (v is Long) v.formatMoney() else v.toString()

object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 32f

    fun export(
        file: File,
        title: String,
        subtitle: String,
        summary: List<Pair<String, String>>,
        tables: List<ReportTable>
    ) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10f; color = Color.BLACK }
        val bold = Paint(normal).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val head = Paint(bold).apply { textSize = 18f }
        val sub = Paint(normal).apply { color = Color.DKGRAY; textSize = 10f }
        val fill = Paint().apply { color = Color.rgb(230, 238, 231); style = Paint.Style.FILL }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val right = W - MARGIN
        val width = W - 2 * MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun fit(text: String, p: Paint, maxWidth: Float): String {
            if (p.measureText(text) <= maxWidth) return text
            val n = p.breakText(text, true, maxWidth - 8f, null)
            return text.take((n - 1).coerceAtLeast(0)) + "…"
        }

        canvas.drawText(title, right, y + 16f, head); y += 28f
        canvas.drawText(subtitle, right, y, sub); y += 20f

        for ((k, v) in summary) {
            if (y > H - MARGIN) newPage()
            canvas.drawText(k, right, y, bold)
            canvas.drawText(v, right - 150f, y, normal)
            y += 15f
        }
        y += 10f

        for (t in tables) {
            if (y > H - 90f) newPage()
            canvas.drawText(t.title, right, y + 12f, bold.apply { textSize = 13f }); bold.textSize = 10f
            y += 22f
            val total = t.weights.sum()
            val widths = t.weights.map { it / total * width }

            fun drawRow(cells: List<String>, header: Boolean) {
                if (y > H - MARGIN - 16f) newPage()
                if (header) canvas.drawRect(MARGIN, y - 11f, right, y + 5f, fill)
                var x = right
                cells.forEachIndexed { i, c ->
                    val p = if (header) bold else normal
                    canvas.drawText(fit(c, p, widths[i]), x - 3f, y, p)
                    x -= widths[i]
                }
                canvas.drawLine(MARGIN, y + 5f, right, y + 5f, line)
                y += 17f
            }

            drawRow(t.headers, true)
            t.rows.forEach { r -> drawRow(r.map(::cellText), false) }
            t.footer?.let {
                if (y > H - MARGIN) newPage()
                canvas.drawText(fit(it, bold, width), right, y + 2f, bold); y += 14f
            }
            y += 14f
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

/** Minimal .xlsx writer (inline strings, right-to-left sheets). No third-party library needed. */
object XlsxExporter {
    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .filter { it.code >= 32 || it == '\n' || it == '\t' }

    private fun col(i: Int): String = ('A' + i).toString()

    private fun sheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><sheetData>""")
        rows.forEachIndexed { r, cells ->
            sb.append("""<row r="${r + 1}">""")
            cells.forEachIndexed { c, v ->
                val ref = "${col(c)}${r + 1}"
                if (v is Long) sb.append("""<c r="$ref"><v>$v</v></c>""")
                else sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun sheetName(s: String, used: MutableSet<String>): String {
        var n = s.filter { it !in "[]:*?/\\" }.take(28).ifBlank { "Sheet" }
        var i = 2
        while (!used.add(n)) { n = n.take(25) + " " + i; i++ }
        return n
    }

    fun export(file: File, title: String, subtitle: String, summary: List<Pair<String, String>>, tables: List<ReportTable>) {
        val sheets = mutableListOf<Pair<String, List<List<Any>>>>()
        val used = mutableSetOf<String>()
        val summaryRows = mutableListOf<List<Any>>(listOf(title), listOf(subtitle), emptyList())
        summary.forEach { summaryRows += listOf<Any>(it.first, it.second) }
        sheets += sheetName("خلاصه", used) to summaryRows
        tables.forEach { t ->
            val rows = mutableListOf<List<Any>>(t.headers)
            rows.addAll(t.rows)
            t.footer?.let { rows += emptyList<Any>(); rows += listOf<Any>(it) }
            sheets += sheetName(t.title, used) to rows
        }
        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            val ct = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            sheets.indices.forEach { ct.append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            val wb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            val rels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.forEachIndexed { i, (name, _) ->
                wb.append("""<sheet name="${esc(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                rels.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
            }
            wb.append("</sheets></workbook>"); rels.append("</Relationships>")
            put("xl/workbook.xml", wb.toString())
            put("xl/_rels/workbook.xml.rels", rels.toString())
            sheets.forEachIndexed { i, (_, rows) -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(rows)) }
        }
    }
}
