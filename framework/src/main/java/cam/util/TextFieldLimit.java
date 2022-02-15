package cam.util;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * @class public class TextFieldLimit extends PlainDocument
 * @brief TextFieldLimit class
 */
public class TextFieldLimit extends PlainDocument {

    private final int limit;

    public TextFieldLimit(int limit) {
        super();

        this.limit = limit;
    }

    @Override
    public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
        if (str == null) { return; }

        if ((getLength() + str.length()) <= limit) {
            super.insertString(offset, str, attr);
        }
    }

}
