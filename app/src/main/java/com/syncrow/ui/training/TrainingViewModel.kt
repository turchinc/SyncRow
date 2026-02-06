package com.syncrow.ui.training

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syncrow.data.db.DurationType
import com.syncrow.data.db.SegmentType
import com.syncrow.data.db.TrainingBlock
import com.syncrow.data.db.TrainingDao
import com.syncrow.data.db.TrainingPlan
import com.syncrow.data.db.TrainingSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditorBlock(val block: TrainingBlock, val segments: List<TrainingSegment>)

private data class BlockData(
  val name: String,
  val repeatCount: Int,
  val segments: List<SegmentData>
)

private data class SegmentData(
  val type: SegmentType,
  val durationType: DurationType,
  val durationValue: Int,
  val targetSpm: Int? = null,
  val targetWatts: Int? = null,
  val targetPace: Int? = null,
  val targetHr: Int? = null
)

class TrainingViewModel(application: Application, private val trainingDao: TrainingDao) :
  AndroidViewModel(application) {

  private val prefs = application.getSharedPreferences("training_prefs", Context.MODE_PRIVATE)
  private val _allPlans = trainingDao.getAllTrainingPlans()
  val favoritePlans = trainingDao.getFavoriteTrainingPlans()

  // Filter/Sort State
  private val _sortOrder =
    MutableStateFlow(prefs.getString("sort_order", "Difficulty (Beginner-Advanced)")!!)
  val sortOrder = _sortOrder.asStateFlow()

  private val _filterDifficulty = MutableStateFlow(prefs.getString("filter_difficulty", "All")!!)
  val filterDifficulty = _filterDifficulty.asStateFlow()

  // Combined Flow for UI List
  val filteredPlans =
    combine(_allPlans, _sortOrder, _filterDifficulty) { plans, sort, filter ->
      var result =
        when (filter) {
          "All" -> plans
          "Favorites" -> plans.filter { it.isFavorite }
          else -> plans.filter { it.difficulty == filter }
        }

      result =
        when (sort) {
          "Name" -> result.sortedBy { it.name }
          "Difficulty (Beginner-Advanced)" -> result.sortedBy { difficultyLevel(it.difficulty) }
          "Difficulty (Advanced-Beginner)" ->
            result.sortedByDescending { difficultyLevel(it.difficulty) }
          "Intensity" -> result.sortedBy { it.intensity }
          else -> result.sortedByDescending { it.createdAt }
        }
      result
    }

  private val _editorPlan = MutableStateFlow<TrainingPlan?>(null)
  val editorPlan: StateFlow<TrainingPlan?> = _editorPlan.asStateFlow()

  private val _editorBlocks = MutableStateFlow<List<EditorBlock>>(emptyList())
  val editorBlocks: StateFlow<List<EditorBlock>> = _editorBlocks.asStateFlow()

  init {
    viewModelScope.launch {
      if (trainingDao.getAllTrainingPlans().first().isEmpty()) {
        seedDefaults()
      }
    }
  }

  fun setSortOrder(order: String) {
    _sortOrder.value = order
    prefs.edit().putString("sort_order", order).apply()
  }

  fun setFilterDifficulty(difficulty: String) {
    _filterDifficulty.value = difficulty
    prefs.edit().putString("filter_difficulty", difficulty).apply()
  }

  fun resetFilters() {
    setSortOrder("Difficulty (Beginner-Advanced)")
    setFilterDifficulty("All")
  }

  private fun difficultyLevel(diff: String): Int {
    return when (diff.lowercase()) {
      "beginner" -> 1
      "intermediate" -> 2
      "advanced" -> 3
      else -> 0
    }
  }

  fun createNewPlan() {
    _editorPlan.value =
      TrainingPlan(name = "", description = "", difficulty = "Beginner", intensity = "Medium")
    // Add one default block
    _editorBlocks.value =
      listOf(
        EditorBlock(
          block = TrainingBlock(planId = 0, orderIndex = 0, name = "Main Set", repeatCount = 1),
          segments = emptyList()
        )
      )
  }

  fun loadPlanForEditing(planId: Long) {
    viewModelScope.launch {
      val plan = _allPlans.first().find { it.id == planId }
      if (plan != null) {
        _editorPlan.value = plan

        val blocks = trainingDao.getBlocksForPlanSync(planId)
        val fullBlocks =
          blocks.map { block ->
            val segments = trainingDao.getSegmentsForBlockSync(block.id)
            EditorBlock(block, segments)
          }
        _editorBlocks.value = fullBlocks
      }
    }
  }

  fun copyPlan(planId: Long) {
    viewModelScope.launch {
      val originalPlan = _allPlans.first().find { it.id == planId } ?: return@launch

      // Load full structure
      val blocks = trainingDao.getBlocksForPlanSync(planId)
      val fullBlocks =
        blocks.map { block ->
          val segments = trainingDao.getSegmentsForBlockSync(block.id)
          EditorBlock(block, segments)
        }

      // Create new plan with "Copy of" name
      val newPlan =
        originalPlan.copy(
          id = 0,
          name = "Copy of ${originalPlan.name}",
          isFavorite = false,
          createdAt = System.currentTimeMillis()
        )

      _editorPlan.value = newPlan

      // Reset IDs in blocks for the new plan
      _editorBlocks.value =
        fullBlocks.map { eb ->
          eb.copy(
            block = eb.block.copy(id = 0, planId = 0),
            segments = eb.segments.map { it.copy(id = 0, blockId = 0) }
          )
        }
    }
  }

  fun updateEditorPlan(plan: TrainingPlan) {
    _editorPlan.value = plan
  }

  fun addBlock() {
    val currentBlocks = _editorBlocks.value
    val newBlock =
      TrainingBlock(
        planId = _editorPlan.value?.id ?: 0,
        orderIndex = currentBlocks.size,
        name = "New Block",
        repeatCount = 1
      )
    _editorBlocks.value = currentBlocks + EditorBlock(newBlock, emptyList())
  }

  fun updateBlock(index: Int, block: TrainingBlock) {
    val list = _editorBlocks.value.toMutableList()
    if (index in list.indices) {
      list[index] = list[index].copy(block = block)
      _editorBlocks.value = list
    }
  }

  fun removeBlock(index: Int) {
    val list = _editorBlocks.value.toMutableList()
    if (index in list.indices) {
      list.removeAt(index)
      _editorBlocks.value = list
    }
  }

  fun addSegmentToBlock(blockIndex: Int, segment: TrainingSegment) {
    val list = _editorBlocks.value.toMutableList()
    if (blockIndex in list.indices) {
      val currentBlock = list[blockIndex]
      val newSegments = currentBlock.segments + segment
      list[blockIndex] = currentBlock.copy(segments = newSegments)
      _editorBlocks.value = list
    }
  }

  fun updateSegment(blockIndex: Int, segmentIndex: Int, segment: TrainingSegment) {
    val list = _editorBlocks.value.toMutableList()
    if (blockIndex in list.indices) {
      val currentBlock = list[blockIndex]
      val segList = currentBlock.segments.toMutableList()
      if (segmentIndex in segList.indices) {
        segList[segmentIndex] = segment
        list[blockIndex] = currentBlock.copy(segments = segList)
        _editorBlocks.value = list
      }
    }
  }

  fun removeSegment(blockIndex: Int, segmentIndex: Int) {
    val list = _editorBlocks.value.toMutableList()
    if (blockIndex in list.indices) {
      val currentBlock = list[blockIndex]
      val segList = currentBlock.segments.toMutableList()
      if (segmentIndex in segList.indices) {
        segList.removeAt(segmentIndex)
        list[blockIndex] = currentBlock.copy(segments = segList)
        _editorBlocks.value = list
      }
    }
  }

  fun savePlan() {
    val plan = _editorPlan.value ?: return
    val blocks = _editorBlocks.value

    viewModelScope.launch {
      val planId =
        if (plan.id == 0L) {
          trainingDao.insertTrainingPlan(plan)
        } else {
          trainingDao.updateTrainingPlan(plan)
          // Clear old data
          trainingDao.deleteBlocksForPlan(plan.id)
          plan.id
        }

      blocks.forEachIndexed { bIndex, editorBlock ->
        val block = editorBlock.block.copy(planId = planId, orderIndex = bIndex)
        val blockId = trainingDao.insertBlock(block)

        val segments =
          editorBlock.segments.mapIndexed { sIndex, seg ->
            seg.copy(blockId = blockId, orderIndex = sIndex)
          }
        if (segments.isNotEmpty()) {
          trainingDao.insertSegments(segments)
        }
      }
    }
  }

  fun deletePlan(plan: TrainingPlan) {
    viewModelScope.launch { trainingDao.deleteTrainingPlan(plan) }
  }

  fun toggleFavorite(plan: TrainingPlan) {
    viewModelScope.launch {
      trainingDao.updateTrainingPlan(plan.copy(isFavorite = !plan.isFavorite))
    }
  }

  private suspend fun seedDefaults() {
    // 1. Beginner: "The Foundation"
    createPlan(
      name = "Beginner: The Foundation",
      description = "Build aerobic base and stroke consistency.",
      difficulty = "Beginner",
      intensity = "Low",
      blocks =
        listOf(
          BlockData(
            "Warm-up",
            1,
            listOf(SegmentData(SegmentType.WARMUP, DurationType.TIME, 180, targetSpm = 19))
          ),
          BlockData(
            "Main Set",
            1,
            listOf(SegmentData(SegmentType.ACTIVE, DurationType.TIME, 900, targetSpm = 21))
          ),
          BlockData(
            "Cool-down",
            1,
            listOf(SegmentData(SegmentType.COOLDOWN, DurationType.TIME, 120, targetSpm = 18))
          )
        )
    )

    // 2. Beginner: "SPM Ladder"
    createPlan(
      name = "Beginner: SPM Ladder",
      description = "Learn Gears (shifting intensity).",
      difficulty = "Beginner",
      intensity = "Medium",
      blocks =
        listOf(
          BlockData(
            "Warm-up",
            1,
            listOf(SegmentData(SegmentType.WARMUP, DurationType.TIME, 300, targetSpm = 20))
          ),
          BlockData(
            "Intervals",
            3,
            listOf(
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 120, targetSpm = 20),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 120, targetSpm = 22),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 60, targetSpm = 24),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 60, targetSpm = 16)
            )
          ),
          BlockData(
            "Cool-down",
            1,
            listOf(SegmentData(SegmentType.COOLDOWN, DurationType.TIME, 180, targetSpm = 18))
          )
        )
    )

    // 3. Intermediate: "The Pyramid"
    createPlan(
      name = "Intermediate: The Pyramid",
      description = "Anaerobic threshold and recovery under load.",
      difficulty = "Intermediate",
      intensity = "Hard",
      blocks =
        listOf(
          BlockData(
            "Warm-up",
            1,
            listOf(SegmentData(SegmentType.WARMUP, DurationType.TIME, 300, targetSpm = 20))
          ),
          BlockData(
            "Pyramid",
            1,
            listOf(
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 60, targetSpm = 24),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 90),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 120, targetSpm = 26),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 90),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 180, targetSpm = 28),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 90),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 120, targetSpm = 26),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 90),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 60, targetSpm = 24)
            )
          ),
          BlockData(
            "Cool-down",
            1,
            listOf(SegmentData(SegmentType.COOLDOWN, DurationType.TIME, 300, targetSpm = 18))
          )
        )
    )

    // 4. Intermediate: "Power 10s"
    createPlan(
      name = "Intermediate: Power 10s",
      description = "Explosive power and leg drive.",
      difficulty = "Intermediate",
      intensity = "Hard",
      blocks =
        listOf(
          BlockData(
            "Warm-up",
            1,
            listOf(SegmentData(SegmentType.WARMUP, DurationType.TIME, 600, targetSpm = 20))
          ),
          BlockData(
            "Main Set",
            10,
            listOf(
              SegmentData(
                SegmentType.ACTIVE,
                DurationType.TIME,
                20,
                targetSpm = 30
              ), // Power 10 approx
              SegmentData(
                SegmentType.ACTIVE,
                DurationType.TIME,
                100,
                targetSpm = 18
              ) // Active Recovery
            )
          )
        )
    )

    // 5. Advanced: "2K Simulator"
    createPlan(
      name = "Advanced: 2K Simulator",
      description = "High-intensity endurance and mental toughness.",
      difficulty = "Advanced",
      intensity = "Hard",
      blocks =
        listOf(
          BlockData(
            "Warm-up",
            1,
            listOf(
              SegmentData(SegmentType.WARMUP, DurationType.TIME, 600, targetSpm = 20),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 30, targetSpm = 30),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 30),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 30, targetSpm = 30),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 30),
              SegmentData(SegmentType.ACTIVE, DurationType.TIME, 30, targetSpm = 30),
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 30)
            )
          ),
          BlockData(
            "Main Set",
            4,
            listOf(
              SegmentData(
                SegmentType.ACTIVE,
                DurationType.DISTANCE,
                500,
                targetSpm = 30,
                targetPace = 115
              ), // 1:55
              SegmentData(SegmentType.RECOVERY, DurationType.TIME, 120)
            )
          ),
          BlockData(
            "Cool-down",
            1,
            listOf(SegmentData(SegmentType.COOLDOWN, DurationType.TIME, 600, targetSpm = 18))
          )
        )
    )
  }

  private suspend fun createPlan(
    name: String,
    description: String,
    difficulty: String,
    intensity: String,
    blocks: List<BlockData>
  ) {
    val plan =
      TrainingPlan(
        name = name,
        description = description,
        difficulty = difficulty,
        intensity = intensity
      )
    val planId = trainingDao.insertTrainingPlan(plan)

    blocks.forEachIndexed { bIndex, blockData ->
      val block =
        TrainingBlock(
          planId = planId,
          orderIndex = bIndex,
          name = blockData.name,
          repeatCount = blockData.repeatCount
        )
      val blockId = trainingDao.insertBlock(block)

      val segments =
        blockData.segments.mapIndexed { sIndex, segData ->
          TrainingSegment(
            blockId = blockId,
            orderIndex = sIndex,
            segmentType = segData.type.name,
            durationType = segData.durationType.name,
            durationValue = segData.durationValue,
            targetSpm = segData.targetSpm,
            targetWatts = segData.targetWatts,
            targetPace = segData.targetPace,
            targetHr = segData.targetHr
          )
        }
      trainingDao.insertSegments(segments)
    }
  }

  class Factory(private val application: Application, private val trainingDao: TrainingDao) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      TrainingViewModel(application, trainingDao) as T
  }
}
