package com.syncrow.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.syncrow.R
import com.syncrow.data.db.TrainingDao
import com.syncrow.data.db.TrainingSegment
import com.syncrow.data.model.TrainingPlanDto
import com.syncrow.data.model.toDto
import com.syncrow.data.model.toEntity
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlanExchange(private val context: Context, private val trainingDao: TrainingDao) {

  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  suspend fun exportPlan(planId: Long) {
    withContext(Dispatchers.IO) {
      val plan = trainingDao.getPlanByIdSync(planId) ?: return@withContext
      val blocks = trainingDao.getBlocksForPlanSync(planId)
      val segmentsMap = mutableMapOf<Long, List<TrainingSegment>>()

      blocks.forEach { block ->
        segmentsMap[block.id] = trainingDao.getSegmentsForBlockSync(block.id)
      }

      val dto = plan.toDto(blocks, segmentsMap)
      val jsonString = gson.toJson(dto)

      // Create file in cache
      val fileName = "SyncRow_Plan_${sanitizeFilename(plan.name)}.json"
      val file = File(context.cacheDir, fileName)
      file.writeText(jsonString)

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "application/json"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, "Workout Plan: ${plan.name}")
          putExtra(Intent.EXTRA_TEXT, "Here is a SyncRow workout plan: ${plan.name}")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          clipData = android.content.ClipData.newRawUri(null, uri)
        }

      val chooser = Intent.createChooser(intent, context.getString(R.string.title_share_plan))
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    }
  }

  suspend fun importPlan(uri: Uri): Long? {
    return withContext(Dispatchers.IO) {
      try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val reader = InputStreamReader(inputStream)
        val dto = gson.fromJson(reader, TrainingPlanDto::class.java)
        reader.close()
        inputStream.close()

        savePlanToDb(dto)
      } catch (e: Exception) {
        Log.e("PlanExchange", "Error importing plan", e)
        null
      }
    }
  }

  private suspend fun savePlanToDb(dto: TrainingPlanDto): Long {
    // Check if a plan with this globalId already exists to avoid duplication
    dto.globalId?.let { gid ->
      val existing = trainingDao.getPlanByGlobalId(gid)
      if (existing != null) {
        return existing.id
      }
    }

    // Save Plan
    val plan = dto.toEntity().copy(name = "${dto.name} (Imported)")
    val planId = trainingDao.insertTrainingPlan(plan)

    // Save Blocks & Segments
    dto.blocks.forEachIndexed { bIndex, blockDto ->
      val block = blockDto.toEntity(planId, bIndex)
      val blockId = trainingDao.insertBlock(block)

      val segments =
        blockDto.segments.mapIndexed { sIndex, segDto -> segDto.toEntity(blockId, sIndex) }
      if (segments.isNotEmpty()) {
        trainingDao.insertSegments(segments)
      }
    }
    return planId
  }

  private fun sanitizeFilename(name: String): String {
    return name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
  }
}
