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
package ai.mantik.ds.helper.akka

import ai.mantik.ds.testutil.{GlobalAkkaSupport, TestBase}
import akka.stream.scaladsl.Source
import akka.util.ByteString

class SameSizeFramerSpec extends TestBase with GlobalAkkaSupport {

  trait Env {
    val sameSize = SameSizeFramer.make(2)
  }

  it should "work for empty" in new Env {
    val input = Source.apply[ByteString](
      Vector.empty
    )
    collectSource(input.via(sameSize)) shouldBe empty
  }

  it should "work for a typical example" in new Env {
    val input = Source.apply[ByteString](
      Vector(
        ByteString(),
        ByteString(1),
        ByteString(2, 3),
        ByteString(4),
        ByteString(5, 6, 7, 8, 9),
        ByteString(10)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(1, 2),
      ByteString(3, 4),
      ByteString(5, 6),
      ByteString(7, 8),
      ByteString(9, 10)
    )
  }

  it should "cap missing data" in new Env {
    val input = Source.apply(
      Vector(
        ByteString(1, 2),
        ByteString(3),
        ByteString(4),
        ByteString(5)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(1, 2),
      ByteString(3, 4)
    )
  }

  it should "work if there is a lot of stuff pendign at the end" in new Env {
    val input = Source.apply(
      Vector(
        ByteString(0),
        ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(0, 1),
      ByteString(2, 3),
      ByteString(4, 5),
      ByteString(6, 7),
      ByteString(8, 9)
    )
  }
}
