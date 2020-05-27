package ai.mantik.executor.docker

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.{ Executor, ExecutorForIntegrationTest }
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import com.typesafe.config.{ Config => TypesafeConfig }
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class DockerExecutorForIntegrationTest(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime) extends ExecutorForIntegrationTest {
  val logger = Logger(getClass)

  val executorConfig = DockerExecutorConfig.fromTypesafeConfig(config)
  val dockerClient = new DockerClient()
  var _executor: Option[DockerExecutor] = None

  override def executor: Executor = _executor.getOrElse(
    throw new IllegalStateException("Not yet started")
  )

  override def start(): Unit = {
    _executor = Some(new DockerExecutor(dockerClient, executorConfig))
  }

  /** Remove old containers */
  def scrap(): Unit = {
    def await[T](f: Future[T]): T = {
      Await.result(f, 60.seconds)
    }
    val mantikContainers = await(dockerClient.listContainersFiltered(true, ListContainerRequestFilter.forLabelKeyValue(
      DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue
    )))

    if (mantikContainers.isEmpty) {
      logger.info("No old mantik containers to kill")
    }
    mantikContainers.foreach { container =>
      logger.info(s"Killing Container ${container.Names}/${container.Id}")
      await(dockerClient.removeContainer(container.Id, true))
    }
    val volumes = await(dockerClient.listVolumes(()))
    val mantikVolumes = volumes.Volumes.filter(
      _.effectiveLabels.get(DockerConstants.ManagedByLabelValue).contains(DockerConstants.ManagedByLabelName)
    )
    if (mantikVolumes.isEmpty) {
      logger.info("No old mantik volumes to kill")
    }
    mantikVolumes.foreach { volume =>
      logger.info(s"Killing Volume ${volume.Name}")
      await(dockerClient.removeVolume(volume.Name))
    }
  }

  def stop(): Unit = {
    // nothing extra to do
  }
}
