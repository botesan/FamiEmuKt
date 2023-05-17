package jp.mito.famiemukt.emurator.apu

enum class SequencerMode {
    Step4, Step5,
}

enum class FrameCounter {
    Quarter, Half, Interrupt,
}

// https://www.nesdev.org/wiki/APU_Frame_Counter
// Mode 0: 4-Step Sequence (bit 7 of $4017 clear)
//  Step |    APU cycles     | Envelopes &               | Length counters &  | Frame interrupt flag
//       |                   | triangle's linear counter | sweep units        |
//       |  NTSC   |   PAL   | (Quarter frame)           | (Half frame)       |
//   1   |  3728.5 |  4156.5 | Clock                     |                    |
//   2   |  7456.5 |  8313.5 | Clock                     | Clock              |
//   3   | 11185.5 | 12469.5 | Clock                     |                    |
//   4   | 14914   | 16626   |                           |                    | Set if interrupt inhibit is clear
//       | 14914.5 | 16626.5 | Clock                     | Clock              | Set if interrupt inhibit is clear
//       | 0(14915)| 0(16627)|                           |                    | Set if interrupt inhibit is clear
//                           |NTSC: 240 Hz(approx)       |NTSC: 120 Hz(approx)|NTSC: 60 Hz(approx)
//                           |PAL:  200 Hz(approx)       |PAL:  100 Hz(approx)|PAL:  50 Hz(approx)
private val FRAME_SEQUENCERS_4 = listOf(
    7457 to listOf(FrameCounter.Quarter),
    14913 to listOf(FrameCounter.Quarter, FrameCounter.Half),
    22371 to listOf(FrameCounter.Quarter),
    29828 to listOf(FrameCounter.Interrupt),
    29829 to listOf(FrameCounter.Quarter, FrameCounter.Half, FrameCounter.Interrupt),
    29830 to listOf(FrameCounter.Interrupt),
)

// https://www.nesdev.org/wiki/APU_Frame_Counter
// Mode 1: 5-Step Sequence (bit 7 of $4017 set)
//  Step |    APU cycles     | Envelopes &                      | Length counters &
//       |                   | triangle's linear counter        | sweep units
//       |  NTSC   |   PAL   | (Quarter frame)                  | (Half frame)
//   1   |  3728.5 |  4156.5 | Clock                            |
//   2   |  7456.5 |  8313.5 | Clock                            | Clock
//   3   | 11185.5 | 12469.5 | Clock                            |
//   4   | 14914.5 | 16626.5 |                                  |
//   5   | 18640.5 | 20782.5 | Clock                            | Clock
//       | 0(18641)| 0(20783)|                                  |
//                           |NTSC: 192 Hz(approx),uneven timing|NTSC: 96 Hz(approx),uneven timing
//                           |PAL:  160 Hz(approx),uneven timing|PAL:  80 Hz(approx),uneven timing
private val FRAME_SEQUENCERS_5 = listOf(
    7457 to listOf(FrameCounter.Quarter),
    14913 to listOf(FrameCounter.Quarter, FrameCounter.Half),
    22371 to listOf(FrameCounter.Quarter),
    29829 to emptyList(),
    37281 to listOf(FrameCounter.Quarter, FrameCounter.Half),
    37282 to emptyList(),
)

fun getFrameSequencers(mode: SequencerMode): List<Pair<Int, List<FrameCounter>>> = when (mode) {
    SequencerMode.Step4 -> FRAME_SEQUENCERS_4
    SequencerMode.Step5 -> FRAME_SEQUENCERS_5
}
