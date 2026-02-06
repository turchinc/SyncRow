package com.syncrow.util

import android.net.Uri
import androidx.core.content.FileProvider

/** Custom FileProvider to ensure .tcx files are recognized as XML rather than binary. */
class TcxProvider : FileProvider() {
  override fun getType(uri: Uri): String {
    val path = uri.path
    return if (path != null && path.endsWith(".tcx")) {
      "application/xml"
    } else {
      super.getType(uri) ?: "application/octet-stream"
    }
  }
}
