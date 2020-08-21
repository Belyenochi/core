package ai.mantik.executor.client

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Errors.ExecutorException
import ai.mantik.executor.model._
import ai.mantik.executor.{ Errors, Executor, ExecutorApi }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import javax.inject.{ Inject, Provider, Singleton }
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

/** Akka based client for the Executor. */
class ExecutorClient(url: Uri)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Executor {
  private val http = Http()
  private val apiClient = new ApiClient(http, url)

  private val publishServiceCall = apiClient.prepare(ExecutorApi.publishService)
  private val versionCall = apiClient.prepare(ExecutorApi.nameAndVersion)
  private val grpcProxyCall = apiClient.prepare(ExecutorApi.grpcProxy)
  private val startWorkerCall = apiClient.prepare(ExecutorApi.startWorker)
  private val listWorkerCall = apiClient.prepare(ExecutorApi.listWorker)
  private val stopWorkerCall = apiClient.prepare(ExecutorApi.stopWorker)

  private def unpackError[T](in: Future[Either[(Int, ExecutorException), T]]): Future[T] = {
    in.recoverWith {
      case e: Exception => Future.failed(new Errors.InternalException(e.getMessage))
    }.flatMap {
      case Left((_, error)) => Future.failed(error)
      case Right(ok)        => Future.successful(ok)
    }
  }

  override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
    unpackError {
      publishServiceCall(publishServiceRequest)
    }
  }

  override def nameAndVersion: Future[String] = {
    unpackError {
      versionCall(())
    }
  }

  override def grpcProxy(isolationSpace: String): Future[GrpcProxy] = {
    unpackError {
      grpcProxyCall(isolationSpace)
    }
  }

  override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] = {
    unpackError {
      startWorkerCall(startWorkerRequest)
    }
  }

  override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] = {
    unpackError {
      listWorkerCall(listWorkerRequest)
    }
  }

  override def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse] = {
    unpackError {
      stopWorkerCall(stopWorkerRequest)
    }
  }
}

class ExecutorClientProvider @Inject() (implicit akkaRuntime: AkkaRuntime) extends Provider[ExecutorClient] {
  @Singleton
  override def get(): ExecutorClient = {
    val executorUrl = akkaRuntime.config.getString("mantik.executor.client.executorUrl")
    new ExecutorClient(executorUrl)
  }
}