/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.integration

import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.planner.DataSet

class SimpleAlgorithmSpec extends IntegrationTestBase with Samples {

  it should "calculate a transformation" in new EnvWithAlgorithm {
    val dataset = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives("x", 1.0, 2.0)
        .result
    )

    val result = context.execute(
      doubleMultiply(dataset).fetch
    )

    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("y", 2.0, 4.0)
      .result
  }
}
