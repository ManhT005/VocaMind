package com.vocamind.app.domain.quiz;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Runs the exact same official expectations on both supported SDK boundaries. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public class AndroidNfcConformanceTest extends NfcConformanceTest {}
