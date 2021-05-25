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
package ai.mantik.bridge.scalafn

import io.circe.{Decoder, Encoder}

/** Defines the type of serialized Code to be expected. */
sealed abstract class ScalaFnType(val name: String)

object ScalaFnType {
  case object RowMapperType extends ScalaFnType("rowMapper")

  val all = Seq(RowMapperType)

  implicit val encoder: Encoder[ScalaFnType] = Encoder.encodeString.contramap[ScalaFnType](_.name)
  implicit val decoder: Decoder[ScalaFnType] = Decoder.decodeString.emap { name =>
    all.find(_.name == name) match {
      case None      => Left(s"Unexpected type ${name}, expected one of : ${all.map(_.name)}")
      case Some(got) => Right(got)
    }
  }
}
