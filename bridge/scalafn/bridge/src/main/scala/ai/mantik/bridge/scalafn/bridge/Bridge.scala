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
package ai.mantik.bridge.scalafn.bridge

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.mnp.server.MnpServer

/**
  * Scala Mantik Bridge Server
  * Note: if not used in a singe bridge anymore, this should be moved into a Library.
  */
class Bridge(backend: BridgeBackend)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {
  val port = akkaRuntime.config.getInt("mantik.bridge.port")
  val mnpServer = new MnpServer(backend, interface = "0.0.0.0", chosenPort = port)

  def run(): Unit = {
    mnpServer.start()
    sys.addShutdownHook {
      mnpServer.stop()
    }
    logger.info(s"Started Bridge for ${backend.name} on MNP: ${mnpServer.address}")
    mnpServer.awaitTermination()
  }
}
