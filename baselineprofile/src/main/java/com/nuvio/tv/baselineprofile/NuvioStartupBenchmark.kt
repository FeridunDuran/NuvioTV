package com.nuvio.tv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold launch to the first frame with and without the packaged Baseline Profile.
 * Onboarding is completed in setup so both modes measure the same home screen launch.
 */
@RunWith(AndroidJUnit4::class)
class NuvioStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupWithBaselineProfile() =
        measureStartup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun startupWithoutCompilation() = measureStartup(CompilationMode.None())

    private fun measureStartup(compilationMode: CompilationMode) {
        val device = uiDevice()
        val packageName = resolveTargetPackage(device, BENCHMARK_TARGET_PACKAGE)
        // The labels of the journeys are the English ones, so the device locale is pinned for
        // the whole measurement, exactly like the profile generator does it.
        device.withEnglishLocale {
            benchmarkRule.measureRepeated(
                packageName = packageName,
                metrics = listOf(StartupTimingMetric()),
                compilationMode = compilationMode,
                // A cold start: the library kills the app process after the setup block and the
                // launch inside the measured block is therefore a real cold start.
                startupMode = StartupMode.COLD,
                iterations = ITERATIONS,
                setupBlock = {
                    pressHome()
                    startActivityAndWait()
                    device.prepareForJourney(packageName)
                },
                measureBlock = {
                    // The launch has to happen inside the measured block: the startup metric reads
                    // the launch from the trace of the measured block, a launch in the setup block
                    // is not part of that trace.
                    startActivityAndWait()
                }
            )
        }
    }

    private companion object {
        /** Startup medians are noisy, so this runs more iterations than the frame journeys. */
        const val ITERATIONS = 5
    }
}
