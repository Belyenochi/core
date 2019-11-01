package ai.mantik.planner.repository.impl

import ai.mantik.componently.utils.SecretReader
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.registry.api._
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile, NamedMantikId }
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.{ CustomLoginToken, Errors, MantikArtifact, MantikRegistry, RemoteMantikRegistry }
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import javax.inject.Inject
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.Future
import scala.util.{ Success, Try }

private[mantik] class MantikRegistryImpl @Inject() (implicit akkaRuntime: AkkaRuntime)
  extends ComponentBase with RemoteMantikRegistry with FailFastCirceSupport {

  private val registryCredentials = new DefaultRegistryCredentials(config)

  private val executor: ApiClient.RequestExecutor = { request =>
    val t0 = System.currentTimeMillis()
    Http().singleRequest(request).andThen {
      case Success(response) =>
        val t1 = System.currentTimeMillis()
        logger.debug(s"Calling ${request.method.name()} ${request.uri} ${response.status.intValue()} within ${t1 - t0}ms")
    }
  }
  private val defaultApi = new MantikRegistryApi(registryCredentials.url, executor)
  private val defaultTokenProvider = new MantikRegistryTokenProvider(defaultApi, registryCredentials.user, registryCredentials.password)

  /** Provides a token. */
  private[mantik] def token(): Future[String] = defaultTokenProvider.getToken()

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    defaultCall(getImpl(_, _, mantikId))
  }

  private def getImpl(api: MantikRegistryApi, token: String, mantikId: MantikId): Future[MantikArtifact] = {
    for {
      artifactResponse <- api.artifact(token, mantikId)
      artifact <- Future.fromTry(decodeMantikArtifact(mantikId, artifactResponse))
    } yield artifact
  }

  override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    defaultCall(ensureMantikIdImpl(_, _, itemId, mantikId))
  }

  private def ensureMantikIdImpl(api: MantikRegistryApi, token: String, itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    api.tag(token, ApiTagRequest(itemId, mantikId)).map(_.updated)
  }

  private def decodeMantikArtifact(mantikId: MantikId, apiGetArtifactResponse: ApiGetArtifactResponse): Try[MantikArtifact] = {
    for {
      _ <- Mantikfile.fromYaml(apiGetArtifactResponse.mantikDefinition).toTry
    } yield {
      MantikArtifact(
        apiGetArtifactResponse.mantikDefinition,
        fileId = apiGetArtifactResponse.fileId,
        namedId = apiGetArtifactResponse.namedId,
        itemId = apiGetArtifactResponse.itemId
      )
    }
  }

  override def getPayload(fileId: String): Future[PayloadSource] = {
    defaultCall(getPayloadImpl(_, _, fileId))
  }

  private def getPayloadImpl(api: MantikRegistryApi, token: String, fileId: String): Future[PayloadSource] = {
    api.file(token, fileId)
  }

  override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[PayloadSource]): Future[MantikArtifact] = {
    defaultCall(addMantikArtifactImpl(_, _, mantikArtifact, payload))
  }

  private def addMantikArtifactImpl(api: MantikRegistryApi, token: String, mantikArtifact: MantikArtifact, payload: Option[PayloadSource]): Future[MantikArtifact] = {
    for {
      uploadResponse <- api.prepareUpload(
        token,
        ApiPrepareUploadRequest(
          namedId = mantikArtifact.namedId,
          itemId = mantikArtifact.itemId,
          mantikfile = mantikArtifact.mantikfile,
          hasFile = payload.nonEmpty
        )
      )
      remoteFileId <- payload match {
        case Some((contentType, source)) =>
          // Akka HTTP Crashes on empty Chunks.
          val withoutEmptyChunks = source.filter(_.nonEmpty)
          api.uploadFile(
            token, mantikArtifact.itemId.toString, contentType, withoutEmptyChunks
          ).map(response => Some(response.fileId))
        case None =>
          Future.successful(None)
      }
    } yield {
      val result = mantikArtifact.copy(
        fileId = remoteFileId
      )
      result
    }
  }

  override def login(url: String, user: String, password: String): Future[ApiLoginResponse] = {
    val subApi = new MantikRegistryApi(url, executor)
    subApi.login(
      ApiLoginRequest(user, password, MantikRegistryTokenProvider.Requester)
    )
  }

  override def withCustomToken(token: CustomLoginToken): MantikRegistry = return new MantikRegistry {
    override def get(mantikId: MantikId): Future[MantikArtifact] = {
      customCall(token, getImpl(_, _, mantikId))
    }

    override def getPayload(fileId: String): Future[(String, Source[ByteString, _])] = {
      customCall(token, getPayloadImpl(_, _, fileId))
    }

    override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[(String, Source[ByteString, _])]): Future[MantikArtifact] = {
      customCall(token, addMantikArtifactImpl(_, _, mantikArtifact, payload))
    }

    override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
      customCall(token, ensureMantikIdImpl(_, _, itemId, mantikId))
    }

    override implicit protected def akkaRuntime: AkkaRuntime = MantikRegistryImpl.this.akkaRuntime
  }

  /**
   * Runs a call on the default API.
   * @param f called with API and Token
   */
  private def defaultCall[T](f: (MantikRegistryApi, String) => Future[T]): Future[T] = {
    errorHandling {
      for {
        token <- defaultTokenProvider.getToken()
        response <- f(defaultApi, token)
      } yield response
    }
  }

  /**
   * Runs a call on a own-logged-in-API API.
   * @param f called with API and Token
   */
  private def customCall[T](customLoginToken: CustomLoginToken, f: (MantikRegistryApi, String) => Future[T]): Future[T] = {
    errorHandling {
      val subApi = new MantikRegistryApi(customLoginToken.url, executor)
      f(subApi, customLoginToken.token)
    }
  }

  private def errorHandling[T](f: => Future[T]): Future[T] = {
    f.recoverWith {
      case e: Errors.RepositoryError => Future.failed(e) // already mapped
      case f @ MantikRegistryApi.WrappedError(e) =>
        e.code match {
          case x if x == ApiErrorResponse.NotFound => Future.failed(new Errors.NotFoundException(e.message.getOrElse("")))
          case _                                   => Future.failed(f)
        }
      case e => Future.failed(new Errors.RepositoryError(e.getMessage, e))
    }
  }
}
