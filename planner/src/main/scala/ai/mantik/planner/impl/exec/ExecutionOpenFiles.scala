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
package ai.mantik.planner.impl.exec

import ai.mantik.planner.PlanExecutor.InvalidPlanException
import ai.mantik.planner.PlanFileReference
import ai.mantik.planner.repository.FileRepository.{FileGetResult, FileStorageResult}

/**
  * Handles Open Files during Plan Execution, part of [[MnpPlanExecutor]].
  *
  * Generated by [[ExecutionOpenFilesBuilder]]
  *
  * @param readFiles files for reading
  * @param writeFiles files for writing
  */
private[impl] case class ExecutionOpenFiles(
    private[exec] val readFiles: Map[PlanFileReference, FileGetResult] = Map.empty,
    private[exec] val writeFiles: Map[PlanFileReference, FileStorageResult] = Map.empty
) {

  lazy val fileIds: Map[PlanFileReference, String] = {
    readFiles.view.mapValues(_.fileId).toMap ++ writeFiles.view.mapValues(_.fileId).toMap
  }

  def resolveFileWrite(fileReference: PlanFileReference): FileStorageResult = {
    writeFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to write $fileReference is not opened"))
  }

  def resolveFileRead(fileReference: PlanFileReference): FileGetResult = {
    readFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to read $fileReference is not opened"))
  }

  def resolveFileId(fileReference: PlanFileReference): String = {
    fileIds.getOrElse(fileReference, throw new InvalidPlanException(s"File $fileReference has no file id associated"))
  }
}
