package com.superdl.launcher.offers

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/**
 * A gépi próba PDF-szövege: az asztali PDFBox 2.0.27, PONTOSAN azokkal a
 * beállításokkal, amiket [PdfTextSettings] leír. Az alkalmazásban ugyanez a
 * pár sor fut a pdfbox-android (com.tom_roush.pdfbox) osztályaival.
 */
class OfferStripper : PDFTextStripper() {
    init {
        sortByPosition = PdfTextSettings.SORT_BY_POSITION
        lineSeparator = PdfTextSettings.LINE_SEPARATOR
        wordSeparator = PdfTextSettings.WORD_SEPARATOR
        paragraphStart = PdfTextSettings.PARAGRAPH_START
        paragraphEnd = PdfTextSettings.PARAGRAPH_END
        pageStart = PdfTextSettings.PAGE_START
        pageEnd = PdfTextSettings.PAGE_END
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (textPositions.isNotEmpty()) {
            val a = textPositions.first()
            val b = textPositions.last()
            output.write(PdfTextSettings.wordMark(a.xDirAdj, a.yDirAdj, b.xDirAdj + b.widthDirAdj - a.xDirAdj, a.heightDir))
        }
        super.writeString(text, textPositions)
    }
}

object PdfTestText {
    /** Az oldalak jelölt szövege — ez a `pdfPages` a próbában. */
    fun pages(bytes: ByteArray): List<String> = PDDocument.load(bytes).use { doc ->
        val s = OfferStripper()
        (1..doc.numberOfPages).map { p ->
            s.startPage = p
            s.endPage = p
            s.getText(doc)
        }
    }
}
