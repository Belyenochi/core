package ai.mantik.ds.element

import ai.mantik.ds._
import ai.mantik.ds.converter.Cast.findCast
import ai.mantik.ds.converter.StringPreviewGenerator
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.formats.messagepack.MessagePackReaderWriter
import akka.stream.scaladsl.{ Compression, FileIO, Keep, Sink, Source }
import akka.util.ByteString
import io.circe.{ Decoder, Encoder, ObjectEncoder }

import scala.concurrent.{ Await, ExecutionContext, Future }

/**
 * An in-memory data bundle
 *
 * This is either a [[TabularBundle]] or a [[SingleElementBundle]].
 */
sealed trait Bundle {

  /** The underlying data type. */
  def model: DataType

  /** Returns the rows of the bundle. In case of single elements Vector[SingleElement] is returned. */
  def rows: Vector[RootElement]

  /** Encode as stream  */
  def encode(withHeader: Boolean): Source[ByteString, _] = {
    val source = Source(rows)
    val encoder = new MessagePackReaderWriter(model, withHeader).encoder()
    source.via(encoder)
  }

  /** Renders the Bundle. */
  def render(maxLines: Int = 20): String = {
    new StringPreviewGenerator(maxLines).render(this)
  }

  override def toString: String = {
    try {
      new StringPreviewGenerator().renderSingleLine(this)
    } catch {
      case e: Exception => "<Error Bundle>"
    }
  }

  /**
   * Returns the single element contained in the bundle.
   * This works only for Bundles which are not tabular.
   */
  def single: Option[Element]

  /**
   * Convert a tabular bundle into a single element bundle by inserting a wrapped tabular element.
   * SingleElementBundles are not touched.
   */
  def toSingleElementBundle: SingleElementBundle
}

/** A Bundle which contains a single element. */
case class SingleElementBundle(
    model: DataType,
    element: Element
) extends Bundle {
  override def rows: Vector[RootElement] = Vector(SingleElement(element))

  override def single: Option[Element] = Some(element)

  /**
   * Cast this bundle to a new type.
   * Note: loosing precision is only deducted from the types. It is possible
   * that a cast is marked as loosing precision but it's not in practice
   * (e.g. 100.0 (float64)--> 100 (int))
   * @param allowLoosing if true, it's allowed when the cast looses precision.
   */
  def cast(to: DataType, allowLoosing: Boolean = false): Either[String, SingleElementBundle] = {
    findCast(model, to) match {
      case Left(error) => Left(error)
      case Right(c) if !c.loosing || allowLoosing =>
        try {
          Right(SingleElementBundle(to, c.op(element)))
        } catch {
          case e: Exception =>
            Left(s"Cast failed ${e.getMessage}")
        }
      case Right(c) => Left("Cast would loose precision")
    }
  }

  override def toSingleElementBundle: SingleElementBundle = this
}

/** A  Bundle which contains tabular data. */
case class TabularBundle(
    model: TabularData,
    rows: Vector[TabularRow]
) extends Bundle {
  override def single: Option[Element] = None

  override def toSingleElementBundle: SingleElementBundle = SingleElementBundle(model, EmbeddedTabularElement(rows))
}

object Bundle {

  /**
   * Constructs a bundle from data type and elements.
   * @throws IllegalArgumentException if the bundle is invalid.
   */
  def apply(model: DataType, elements: Vector[RootElement]): Bundle = {
    elements match {
      case Vector(s: SingleElement) => SingleElementBundle(model, s.element)
      case rows =>
        val tabularRows = rows.collect {
          case r: TabularRow => r
          case _             => throw new IllegalArgumentException(s"Got a bundle non tabular rows, which have not count 1")
        }
        model match {
          case t: TabularData => TabularBundle(t, tabularRows)
          case _ =>
            throw new IllegalArgumentException("Got a non tabular bundle with tabular rows")
        }
    }
  }

  /** Deserializes the bundle from a stream without header. */
  def fromStreamWithoutHeader(dataType: DataType)(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val readerWriter = new MessagePackReaderWriter(dataType, withHeader = false)
    val sink: Sink[ByteString, Future[Seq[RootElement]]] = readerWriter.decoder().toMat(Sink.seq[RootElement])(Keep.right)
    sink.mapMaterializedValue { elementsFuture =>
      elementsFuture.map { elements =>
        Bundle(
          dataType,
          elements.toVector
        )
      }
    }
  }

  /** Deserializes from a Stream including Header. */
  def fromStreamWithHeader()(implicit ec: ExecutionContext): Sink[ByteString, Future[Bundle]] = {
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    val sink: Sink[ByteString, (Future[DataType], Future[Seq[RootElement]])] =
      decoder.toMat(Sink.seq)(Keep.both)
    sink.mapMaterializedValue {
      case (dataTypeFuture, elementsFuture) =>
        for {
          dataType <- dataTypeFuture
          elements <- elementsFuture
        } yield Bundle(dataType, elements.toVector)
    }
  }

  /** Experimental Builder for Natural types. */
  class Builder(tabularData: TabularData) {
    private val rowBuilder = Vector.newBuilder[TabularRow]

    /** Add a row (just use the pure Scala Types, no Primitives or similar. */
    def row(values: Any*): Builder = {
      addCheckedRow(values)
      this
    }

    def result: TabularBundle = TabularBundle(
      tabularData, rowBuilder.result()
    )

    private def addCheckedRow(values: Seq[Any]): Unit = {
      require(values.length == tabularData.columns.size)
      val converted = values.zip(tabularData.columns).map {
        case (value, (columnName, pt: FundamentalType)) =>
          val encoder = PrimitiveEncoder.lookup(pt)
          require(encoder.convert.isDefinedAt(value), s"Value  ${value} of class ${value.getClass} must fit to ${pt}")
          encoder.wrap(encoder.convert(value))
        case (value, (columnName, i: Image)) =>
          require(value.isInstanceOf[ImageElement])
          value.asInstanceOf[ImageElement]
        case (value: EmbeddedTabularElement, (columnName, d: TabularData)) =>
          value
        case (value, (columnName, i: Tensor)) =>
          require(value.isInstanceOf[TensorElement[_]])
          value.asInstanceOf[Element]
        case (value, (columnName, n: Nullable)) =>
          value match {
            case NullElement    => NullElement
            case None           => NullElement
            case s: SomeElement => s
            case v if n.underlying.isInstanceOf[FundamentalType] =>
              val encoder = PrimitiveEncoder.lookup(n.underlying.asInstanceOf[FundamentalType])
              require(encoder.convert.isDefinedAt(v), s"Value ${value} of class ${value.getClass} must fit into ${n}")
              SomeElement(encoder.wrap(encoder.convert(v)))
            case unsupported =>
              throw new IllegalArgumentException(s"Unsupported value ${unsupported} for ${n}")
          }
        case (other, (columnName, dataType)) =>
          throw new IllegalArgumentException(s"Could not encode ${other} as ${dataType}")
      }
      rowBuilder += TabularRow(converted.toVector)
    }

  }

  /** Experimental builder for tabular data. */
  def build(tabularData: TabularData): Builder = new Builder(tabularData)

  def buildColumnWise: ColumnWiseBundleBuilder = ColumnWiseBundleBuilder()

  /** Build a non-tabular value. */
  def build(nonTabular: DataType, value: Element): SingleElementBundle = {
    require(!nonTabular.isInstanceOf[TabularData], "Builder can only be used for nontabular data")
    SingleElementBundle(nonTabular, value)
  }

  /** Wrap a single primitive non tabular value. */
  def fundamental[ST](x: ST)(implicit valueEncoder: ValueEncoder[ST]): SingleElementBundle = {
    SingleElementBundle(valueEncoder.fundamentalType, valueEncoder.wrap(x))
  }

  /** The empty value. */
  def void: SingleElementBundle = Bundle.build(FundamentalType.VoidType, Primitive.unit)

  /** A nullable void Null element value (for comparison with other nullable values) */
  def voidNull: SingleElementBundle = Bundle.build(Nullable(FundamentalType.VoidType), NullElement)

  /** JSON Encoder. */
  implicit val encoder: ObjectEncoder[Bundle] = JsonFormat
  /** JSON Decoder. */
  implicit val decoder: Decoder[Bundle] = JsonFormat
}

object SingleElementBundle {

  /** Encoder for SingleElementBundle. */
  implicit val encoder: Encoder[SingleElementBundle] = JsonFormat.contramap[SingleElementBundle](identity)

  implicit val decoder: Decoder[SingleElementBundle] = JsonFormat.map {
    case b: SingleElementBundle => b
    case b: TabularBundle       => b.toSingleElementBundle
  }
}

