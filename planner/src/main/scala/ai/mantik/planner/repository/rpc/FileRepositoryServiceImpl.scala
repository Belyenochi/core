package ai.mantik.planner.repository.rpc

import ai.mantik.componently.rpc.{ RpcConversions, StreamConversions }
import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryService
import ai.mantik.planner.repository.protos.file_repository.{ CopyFileRequest, CopyFileResponse, DeleteFileRequest, DeleteFileResponse, LoadFileRequest, LoadFileResponse, RequestFileGetRequest, RequestFileGetResponse, RequestFileStorageRequest, RequestFileStorageResponse, StoreFileRequest, StoreFileResponse }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger
import io.grpc.stub.{ StreamObserver, StreamObservers }
import javax.inject.Inject

import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class FileRepositoryServiceImpl @Inject() (backend: FileRepository)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with FileRepositoryService {
  override def requestFileStorage(request: RequestFileStorageRequest): Future[RequestFileStorageResponse] = {
    Conversions.encodeErrorsIn {
      backend.requestFileStorage(
        request.temporary
      ).map { response =>
          RequestFileStorageResponse(
            fileId = response.fileId,
            path = response.path
          )
        }
    }
  }

  override def requestFileGet(request: RequestFileGetRequest): Future[RequestFileGetResponse] = {
    Conversions.encodeErrorsIn {
      backend.requestFileGet(request.fileId, optimistic = request.optimistic).map { response =>
        RequestFileGetResponse(
          fileId = response.fileId,
          isTemporary = response.isTemporary,
          path = response.path,
          contentType = RpcConversions.encodeOptionalString(response.contentType)
        )
      }
    }
  }

  override def storeFile(responseObserver: StreamObserver[StoreFileResponse]): StreamObserver[StoreFileRequest] = {
    StreamConversions.splitFirst[StoreFileRequest] {
      case Success(req) =>
        // We could also buffer, but gRpc is ok with us blocking the on next Call.
        val sink = Await.result(
          backend.storeFile(req.fileId, req.contentType),
          60.seconds)
        val source = StreamConversions.streamObserverSource[StoreFileRequest]()
        val withFirstElement = source.prepend(Source.single(req))
        val byteBlobs = withFirstElement.map { req =>
          RpcConversions.decodeByteString(req.chunk)
        }
        val (streamObserver, result) = byteBlobs.toMat(sink)(Keep.both).run()
        result.onComplete {
          case Success(_) =>
            responseObserver.onNext(StoreFileResponse())
            responseObserver.onCompleted()
          case Failure(e) => responseObserver.onError(e)
        }
        streamObserver
      case Failure(e) =>
        responseObserver.onError(Conversions.encodeErrorIfPossible(e))
        StreamConversions.empty
    }
  }

  override def deleteFile(request: DeleteFileRequest): Future[DeleteFileResponse] = {
    Conversions.encodeErrorsIn {
      backend.deleteFile(request.fileId).map { existed =>
        DeleteFileResponse(existed = existed)
      }
    }
  }

  override def loadFile(request: LoadFileRequest, responseObserver: StreamObserver[LoadFileResponse]): Unit = {
    backend.loadFile(request.fileId).onComplete {
      case Success((contentType, source)) =>
        val header = Source.single(LoadFileResponse(
          contentType = contentType
        ))
        val adaptedSource: Source[LoadFileResponse, _] = source.map { byteString =>
          LoadFileResponse(RpcConversions.encodeByteString(byteString))
        }
        val combined = header ++ adaptedSource
        StreamConversions.pumpSourceIntoStreamObserver(combined, responseObserver)
      case Failure(failure) =>
        responseObserver.onError(Conversions.encodeErrorIfPossible(failure))
    }
  }

  override def copyFile(request: CopyFileRequest): Future[CopyFileResponse] = {
    Conversions.encodeErrorsIn {
      backend.copy(from = request.fromId, to = request.toId).map { _ =>
        CopyFileResponse()
      }
    }
  }
}
