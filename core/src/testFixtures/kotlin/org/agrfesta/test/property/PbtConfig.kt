package org.agrfesta.test.property

import io.kotest.property.PropTestConfig

/** Default number of generated inputs per property. */
const val PBT_ITERATIONS = 1000

/**
 * Fixed seed for property-based tests: makes CI failures reproducible. Kotest also prints the failing
 * seed on a counterexample, so a local rerun with a different seed remains possible.
 */
const val PBT_SEED = 1_234_567_890L

/** Shared configuration applied to every property-based test in the project. */
val pbtConfig = PropTestConfig(iterations = PBT_ITERATIONS, seed = PBT_SEED)
