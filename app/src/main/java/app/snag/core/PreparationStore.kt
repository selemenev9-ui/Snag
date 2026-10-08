package app.snag.core

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Serializable

enum class StudioOp : Serializable { CLIP, GIF, STICKER, STICKER_STATIC, MP3, FRAME, RINGTONE }

data class StudioOptsData(
    val speed: Float = 1f,
    val mute: Boolean = false,
    val crop: FfmpegRunner.Crop = FfmpegRunner.Crop.ORIG,
    val rotate: Int = 0,
    val skip: List<SponsorBlock.Segment> = emptyList(),
) : Serializable {
    fun toStudioOpts() = FfmpegRunner.StudioOpts(
        speed = speed,
        mute = mute,
        crop = crop,
        rotate = rotate,
        skip = skip,
    )

    companion object {
        fun from(opts: FfmpegRunner.StudioOpts) = StudioOptsData(
            speed = opts.speed,
            mute = opts.mute,
            crop = opts.crop,
            rotate = opts.rotate,
            skip = opts.skip,
        )
    }
}

data class PreparationSnapshot(
    val op: StudioOp,
    val startMs: Long,
    val endMs: Long,
    val opts: StudioOptsData = StudioOptsData(),
    val targetBytes: Long? = null,
    val framePositionMs: Long? = null,
) : Serializable {
    fun matches(
        currentOp: StudioOp,
        currentStartMs: Long,
        currentEndMs: Long,
        currentOpts: StudioOptsData,
        currentTargetBytes: Long?,
        currentFramePosMs: Long? = null,
    ): Boolean {
        if (op != currentOp) return false
        if (op == StudioOp.STICKER_STATIC || op == StudioOp.FRAME) {
            val thisFrame = framePositionMs ?: startMs
            val thatFrame = currentFramePosMs ?: currentStartMs
            return thisFrame == thatFrame
        }
        if (startMs != currentStartMs) return false
        if (endMs != currentEndMs) return false
        if (targetBytes != currentTargetBytes) return false
        if (opts.mute != currentOpts.mute) return false
        if (opts.crop != currentOpts.crop) return false
        if (opts.rotate != currentOpts.rotate) return false
        if (opts.skip != currentOpts.skip) return false
        if (kotlin.math.abs(opts.speed - currentOpts.speed) > 0.01f) return false
        return true
    }
}

data class PreparationRequest(
    val workId: String,
    val sourceJobId: String,
    val sourceUri: Uri,
    val title: String,
    val site: String,
    val thumbnail: String?,
    val sourceUrl: String,
    val op: StudioOp,
    val startMs: Long,
    val endMs: Long,
    val opts: StudioOptsData,
    val targetBytes: Long?,
    val framePositionMs: Long? = null,
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
)

data class PreparationState(
    val workId: String = "",
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val pct: Int = 0,
    val uri: Uri? = null,
    val name: String? = null,
    val bytes: Long = 0,
    val ephemeral: Boolean = false,
    val error: String? = null,
    val phase: Int = 0, // 0 preparing, 1 compressing, 2 saving
    val saved: Boolean = false,
    val resultJobId: String? = null,
    val op: StudioOp = StudioOp.CLIP,
    val sourceJobId: String? = null,
    val snapshot: PreparationSnapshot? = null,
    val showReady: Boolean = true,
)

/**
 * Shared registry for active preparation tasks so that any UI instance
 * (or re-opened screen/activity) reconnects to the existing task instead of duplicating it.
 */
object PreparationStore {
    private val _state = MutableStateFlow(PreparationState())
    val state: StateFlow<PreparationState> = _state.asStateFlow()

    private var activeRequest: PreparationRequest? = null

    @Synchronized
    fun currentRequest(): PreparationRequest? = activeRequest

    @Synchronized
    fun setRequest(req: PreparationRequest?) {
        activeRequest = req
    }

    @Synchronized
    fun update(f: (PreparationState) -> PreparationState) {
        _state.value = f(_state.value)
    }

    @Synchronized
    fun reset() {
        if (!_state.value.running && !_state.value.cancelling) {
            activeRequest = null
            _state.value = PreparationState()
        }
    }

    @Synchronized
    fun forceReset() {
        activeRequest = null
        _state.value = PreparationState()
    }
}
