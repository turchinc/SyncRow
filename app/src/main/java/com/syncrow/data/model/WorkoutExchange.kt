package com.syncrow.data.model

import com.google.gson.annotations.SerializedName
import com.syncrow.data.db.ActivityType
import com.syncrow.data.db.TrainingBlock
import com.syncrow.data.db.TrainingPlan
import com.syncrow.data.db.TrainingSegment
import java.util.UUID

/** Data Transfer Object (DTO) for exchanging a full workout plan (e.g. via JSON/Firebase). */
data class TrainingPlanDto(
  @SerializedName("globalId") val globalId: String? = null,
  @SerializedName("name") val name: String,
  @SerializedName("description") val description: String,
  @SerializedName("difficulty") val difficulty: String,
  @SerializedName("intensity") val intensity: String,
  @SerializedName("activityType") val activityType: String = ActivityType.ROWING.name,
  @SerializedName("blocks") val blocks: List<TrainingBlockDto>
)

data class TrainingBlockDto(
  @SerializedName("name") val name: String,
  @SerializedName("repeatCount") val repeatCount: Int = 1,
  @SerializedName("segments") val segments: List<TrainingSegmentDto>
)

data class TrainingSegmentDto(
  @SerializedName("segmentType") val segmentType: String, // ACTIVE, RECOVERY, WARMUP, COOLDOWN
  @SerializedName("durationType") val durationType: String, // TIME, DISTANCE
  @SerializedName("durationValue") val durationValue: Int,
  @SerializedName("targetSpm") val targetSpm: Int? = null,
  @SerializedName("targetWatts") val targetWatts: Int? = null,
  @SerializedName("targetPace") val targetPace: Int? = null,
  @SerializedName("targetHr") val targetHr: Int? = null
)

// --- Mappers ---

fun TrainingPlanDto.toEntity(): TrainingPlan {
  return TrainingPlan(
    globalId =
      this.globalId
        ?: UUID.randomUUID().toString(), // Use existing ID if present, else generate new
    name = this.name,
    description = this.description,
    difficulty = this.difficulty,
    intensity = this.intensity,
    activityType = this.activityType,
    isFavorite = false,
    createdAt = System.currentTimeMillis()
  )
}

fun TrainingBlockDto.toEntity(planId: Long, orderIndex: Int): TrainingBlock {
  return TrainingBlock(
    planId = planId,
    orderIndex = orderIndex,
    name = this.name,
    repeatCount = this.repeatCount
  )
}

fun TrainingSegmentDto.toEntity(blockId: Long, orderIndex: Int): TrainingSegment {
  return TrainingSegment(
    blockId = blockId,
    orderIndex = orderIndex,
    segmentType = this.segmentType,
    durationType = this.durationType,
    durationValue = this.durationValue,
    targetSpm = this.targetSpm,
    targetWatts = this.targetWatts,
    targetPace = this.targetPace,
    targetHr = this.targetHr
  )
}

/**
 * Extension to convert a full Plan+Blocks+Segments structure from DB to DTO. This requires fetching
 * the full hierarchy first (which Room relations can do).
 */
fun TrainingPlan.toDto(
  blocks: List<TrainingBlock>,
  segmentsMap: Map<Long, List<TrainingSegment>> // Map blockId -> segments
): TrainingPlanDto {
  return TrainingPlanDto(
    globalId = this.globalId,
    name = this.name,
    description = this.description,
    difficulty = this.difficulty,
    intensity = this.intensity,
    activityType = this.activityType,
    blocks =
      blocks
        .sortedBy { it.orderIndex }
        .map { block ->
          TrainingBlockDto(
            name = block.name,
            repeatCount = block.repeatCount,
            segments =
              segmentsMap[block.id]
                ?.sortedBy { it.orderIndex }
                ?.map { seg ->
                  TrainingSegmentDto(
                    segmentType = seg.segmentType,
                    durationType = seg.durationType,
                    durationValue = seg.durationValue,
                    targetSpm = seg.targetSpm,
                    targetWatts = seg.targetWatts,
                    targetPace = seg.targetPace,
                    targetHr = seg.targetHr
                  )
                } ?: emptyList()
          )
        }
  )
}
