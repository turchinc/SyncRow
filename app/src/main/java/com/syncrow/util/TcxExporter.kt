package com.syncrow.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Xml
import androidx.core.content.FileProvider
import com.syncrow.R
import com.syncrow.data.db.MetricPoint
import com.syncrow.data.db.Workout
import java.io.File
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import org.xmlpull.v1.XmlSerializer

class TcxExporter(private val context: Context) {

  fun exportWorkout(workout: Workout, points: List<MetricPoint>) {
    val tcxString = generateTcx(workout, points)

    val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    val dateStr = fileDateFormat.format(Date(workout.startTime))
    val fileName = "SyncRow_$dateStr.tcx"

    val file = File(context.cacheDir, fileName)
    file.writeText(tcxString)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TITLE, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(null, uri)
      }

    val chooserTitle = context.getString(R.string.btn_export_tcx)
    val chooser = Intent.createChooser(intent, chooserTitle)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }

  fun generateTcx(workout: Workout, points: List<MetricPoint>): String {
    val isoFormat =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
    val startTimeStr = isoFormat.format(Date(workout.startTime))

    val writer = StringWriter()
    val serializer = Xml.newSerializer()

    try {
      serializer.setOutput(writer)
      serializer.startDocument("UTF-8", true)
      // makes the XML human-readable
      serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

      serializer.startTag(null, "TrainingCenterDatabase")
      serializer.attribute(
        null,
        "xmlns",
        "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
      )

      serializer.startTag(null, "Activities")
      serializer.startTag(null, "Activity")
      serializer.attribute(null, "Sport", "Rowing")

      serializer.tag("Id", startTimeStr)

      serializer.startTag(null, "Lap")
      serializer.attribute(null, "StartTime", startTimeStr)

      serializer.tag("TotalTimeSeconds", workout.totalSeconds.toString())
      serializer.tag("DistanceMeters", workout.totalDistanceMeters.toString())
      serializer.tag("Intensity", "Active")
      serializer.tag("TriggerMethod", "Manual")

      serializer.startTag(null, "Track")
      points.forEach { point ->
        serializer.startTag(null, "Trackpoint")
        serializer.tag("Time", isoFormat.format(Date(point.timestamp)))
        serializer.tag("DistanceMeters", point.distance.toString())

        if (point.heartRate > 0) {
          serializer.startTag(null, "HeartRateBpm")
          serializer.tag("Value", point.heartRate.toString())
          serializer.endTag(null, "HeartRateBpm")
        }

        serializer.tag("Cadence", point.strokeRate.toString())

        // Garmin TPX Extensions for Watts/Power
        serializer.startTag(null, "Extensions")
        serializer.startTag(null, "TPX")
        serializer.attribute(null, "xmlns", "http://www.garmin.com/xmlschemas/ActivityExtension/v2")
        serializer.tag("Watts", point.power.toString())
        serializer.endTag(null, "TPX")
        serializer.endTag(null, "Extensions")

        serializer.endTag(null, "Trackpoint")
      }
      serializer.endTag(null, "Track")
      serializer.endTag(null, "Lap")

      serializer.tag("Notes", workout.notes ?: "SyncRow Virtual Row")

      serializer.endTag(null, "Activity")
      serializer.endTag(null, "Activities")
      serializer.endTag(null, "TrainingCenterDatabase")
      serializer.endDocument()

      return writer.toString()
    } catch (e: Exception) {
      e.printStackTrace()
      return ""
    }
  }

  private fun XmlSerializer.tag(name: String, text: String) {
    startTag(null, name)
    text(text)
    endTag(null, name)
  }
}
