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
package ai.mantik.ds.operations

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec

sealed trait BinaryOperation

case object BinaryOperation {
  case object Add extends BinaryOperation
  case object Sub extends BinaryOperation
  case object Mul extends BinaryOperation
  case object Div extends BinaryOperation

  /** JSON Codec for [[BinaryOperation]]. */
  implicit val BinaryOperationCodec = new EnumDiscriminatorCodec[BinaryOperation](
    Seq(
      "add" -> BinaryOperation.Add,
      "sub" -> BinaryOperation.Sub,
      "mul" -> BinaryOperation.Mul,
      "div" -> BinaryOperation.Div
    )
  )
}
