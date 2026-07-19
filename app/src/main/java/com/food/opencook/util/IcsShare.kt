/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Turns a generated .ics string into a share [Intent]. Uses the same "write to a
 * private cache dir, hand out a content:// URI" pattern as any outbound file share
 * on Android — the file never touches shared storage or needs any extra permission.
 */
object IcsShare {

    /** Writes [ics] under cacheDir/exports/[fileNameHint].ics and returns a chooser-ready
     *  ACTION_SEND intent for it (type "text/calendar" — Google Calendar, email, Drive, … all
     *  offer to handle that). The old exports directory is cleared first since these files are
     *  disposable and shouldn't accumulate across repeated exports. */
    fun shareIntent(context: Context, ics: String, fileNameHint: String): Intent {
        val dir = File(context.cacheDir, "exports")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "$fileNameHint.ics")
        file.writeText(ics)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
