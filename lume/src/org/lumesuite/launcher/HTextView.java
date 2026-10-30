package org.lumesuite.launcher;

import android.content.Context;
import android.widget.TextView;

/** A TextView that shows labels in the device language (see {@link It}); everything else is unchanged. */
final class HTextView extends TextView {
    HTextView(Context c) {
        super(c);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(It.tr(text), type);
    }
}
