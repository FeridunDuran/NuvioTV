package com.nuvio.tv.core.player.dvmkv

import androidx.media3.extractor.PositionHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: HDHub-style MKV with first SeekHead -> trailing SeekHead -> Tracks + Cues. */
class MatroskaTrailingIndexTracksTest {
    @Test fun `trailing index reads tracks before cues and returns to original cluster`() {
        val extractor = MatroskaExtractor()
        fun set(name: String, value: Any) = MatroskaExtractor::class.java.getDeclaredField(name).run {
            isAccessible = true
            set(extractor, value)
        }
        fun invokeSeek(position: Long): PositionHolder {
            val holder = PositionHolder()
            val method = MatroskaExtractor::class.java.getDeclaredMethod(
                "maybeSeekForCues", PositionHolder::class.java, java.lang.Long.TYPE
            ).apply { isAccessible = true }
            assertTrue(method.invoke(extractor, holder, position) as Boolean)
            return holder
        }
        val cluster = 1_604L
        val tracks = 2_715_301_580L
        val cues = 2_715_266_316L
        set("seekPositionAfterBuildingCues", cluster)
        set("tracksContentPosition", tracks)
        set("cuesContentPosition", cues)
        MatroskaExtractor::class.java.getDeclaredMethod("maybeFollowPendingIndexAfterSeekHead").apply {
            isAccessible = true
        }.invoke(extractor)
        assertEquals(tracks, invokeSeek(2_715_328_517L).position)
        // end(Tracks) requests the return. It must go to the original Cluster, not EOF.
        set("returnFromTracks", true)
        assertEquals(cluster, invokeSeek(tracks + 800L).position)
    }
}
