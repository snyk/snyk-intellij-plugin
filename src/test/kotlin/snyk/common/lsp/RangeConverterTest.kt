package snyk.common.lsp

import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class RangeConverterTest : BasePlatformTestCase() {

    fun testConvertToTextRangeDocumentReturnsRangeForValidInput() {
        val document = EditorFactory.getInstance().createDocument("abc\ndef\n")
        val range = Range(Position(0, 1), Position(0, 3))

        val actual = RangeConverter.convertToTextRange(document, range)

        assertNotNull(actual)
        assertEquals(1, actual!!.startOffset)
        assertEquals(3, actual.endOffset)
    }

    fun testConvertToTextRangeDocumentReturnsNullWhenStartLineOutOfBounds() {
        val document = EditorFactory.getInstance().createDocument("a\nb\n")
        val range = Range(Position(10, 0), Position(10, 1))

        val actual = RangeConverter.convertToTextRange(document, range)

        assertNull(actual)
    }

    fun testConvertToTextRangeDocumentReturnsNullWhenEndLineOutOfBounds() {
        val document = EditorFactory.getInstance().createDocument("a\nb\n")
        val range = Range(Position(0, 0), Position(10, 1))

        val actual = RangeConverter.convertToTextRange(document, range)

        assertNull(actual)
    }

    fun testConvertToTextRangeDocumentReturnsNullWhenLineIsNegative() {
        val document = EditorFactory.getInstance().createDocument("a\n")
        val range = Range(Position(-1, 0), Position(0, 1))

        val actual = RangeConverter.convertToTextRange(document, range)

        assertNull(actual)
    }

    fun testConvertToTextRangeDocumentReturnsNullWhenCharacterOutOfBounds() {
        val document = EditorFactory.getInstance().createDocument("a\n")
        val range = Range(Position(0, 999), Position(0, 1000))

        val actual = RangeConverter.convertToTextRange(document, range)

        assertNull(actual)
    }
}
