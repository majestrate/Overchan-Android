/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.ui.posting.markup;

import android.text.Editable;
import android.widget.EditText;

import static nya.miku.wishmaster.ui.posting.PostFormMarkup.FEATURE_STRIKE;

public class Mark410Chan extends CustomMarkupModel {
    public Mark410Chan(int baseMarkup) {
        this.baseMarkup = baseMarkup;
    }

    @Override
    public boolean hasMarkupFeature(int feature) {
        return super.hasMarkupFeature(feature);
    }

    @Override
    public void markup(EditText commentField, int feature) {
        Editable comment = commentField.getEditableText();
        String text = comment.toString();
        int selectionStart = Math.max(0, commentField.getSelectionStart());
        int selectionEnd = Math.min(text.length(), commentField.getSelectionEnd());
        text = text.substring(selectionStart, selectionEnd);

        if (feature == FEATURE_STRIKE) {
            comment.replace(selectionStart, selectionEnd, "^^" + text.replace("\n", "^^\n^^") + "^^");
            commentField.setSelection(selectionStart + 2);
        } else {
            super.markup(commentField, feature);
        }
    }
}
