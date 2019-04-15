package ai.mantik.repository

import java.net.InetSocketAddress

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

/** Responsible for File Storage. */
trait FileRepository {

  /** Request the storage of a new file. */
  def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult]

  /**
   * Request the loading of a file.
   * @param optimistic if true, the file handle will also be returned, if the file is not yet existant.
   */
  def requestFileGet(id: String, optimistic: Boolean = false): Future[FileRepository.FileGetResult]

  /** Request storing a file (must be requested at first). */
  def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]]

  /** Request retrieval of a file. */
  def loadFile(id: String): Future[Source[ByteString, _]]

  /** Returns the address of the repository (must be reachable from the executor). */
  def address(): InetSocketAddress
}

object FileRepository {

  /** Result of file storage request. */
  case class FileStorageResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String,
      // Name of the file resource
      resource: String
  )

  /** Result of get file request. */
  case class FileGetResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String,
      resource: String,
      contentType: Option[String]
  )

  /** Content Type for Mantik Bundles. */
  val MantikBundleContentType = "application/x-mantik-bundle"
}