package ai.mantik.executor.model

import ai.mantik.executor.model.GraphAnalysis._

import scala.annotation.tailrec

/**
 * Helper for analyzing the graph.
 * @tparam T the node service type.
 */
@throws[AnalyzerException]
class GraphAnalysis[T](graph: Graph[T]) {

  graph.links.foreach {
    case Link(from, to) =>
      validateResourceExistance(from)
      validateResourceExistance(to)
  }
  private def validateResourceExistance(ref: NodeResourceRef): Unit = {
    val node = graph.nodes.getOrElse(ref.node, throw new ResourceNotFoundException(ref))
    if (!node.resources.contains(ref.resource)) {
      throw new ResourceNotFoundException(ref)
    }
  }

  /** All links leading to a destination node. */
  val reverseLinks: Map[NodeResourceRef, Seq[NodeResourceRef]] = graph.links.groupBy(_.to).mapValues { v =>
    v.map(_.from)
  }

  /** Returns the flows in a graph. */
  val flows: Set[Flow] = determineFlows()

  private def determineFlows(): Set[Flow] = {
    val result = for {
      (name, node) <- graph.nodes
      (resourceName, nodeResource) <- node.resources
      if nodeResource.resourceType == ResourceType.Sink
    } yield determineFlow(NodeResourceRef(name, resourceName))
    result.toSet
  }

  private def determineFlow(start: NodeResourceRef): Flow = {
    @tailrec
    def findPath(isStart: Boolean, current: NodeResourceRef, way: List[NodeResourceRef]): List[NodeResourceRef] = {
      val node = graph.nodes.getOrElse(current.node, throw new ResourceNotFoundException(current))
      val resource = node.resources.getOrElse(current.resource, throw new ResourceNotFoundException(current))
      resource.resourceType match {
        case ResourceType.Sink if !isStart =>
          throw new FlowFromSinkException(current)
        case ResourceType.Transformer | ResourceType.Sink =>
          val origin = findSingleSourceForLink(current)
          if (way.contains(origin)) {
            throw new CycleDetectedException(current)
          }
          findPath(isStart = false, origin, origin :: way)
        case ResourceType.Source =>
          // done
          way.reverse
      }
    }
    val path = findPath(isStart = true, start, List(start))
    // note: the path is reversed
    Flow(path.reverse)
  }

  private def findSingleSourceForLink(node: NodeResourceRef): NodeResourceRef = {
    reverseLinks.get(node) match {
      case Some(Seq(source)) => source
      case Some(_)           => throw new MultiTargetDetected(node)
      case None              => throw new UnreachableNodeDetected(node)
    }
  }
}

object GraphAnalysis {

  /**
   * A Data Flow, deducted from a graph.
   * @param nodes from the source to the sink.
   */
  case class Flow(
      nodes: Seq[NodeResourceRef]
  )

  object Flow {
    /** Convenience constructor. */
    def fromRefs(refs: NodeResourceRef*): Flow = Flow(refs)
  }

  abstract class AnalyzerException(resource: NodeResourceRef, msg: String) extends RuntimeException(msg)

  class CycleDetectedException(resource: NodeResourceRef) extends AnalyzerException(resource, s"Cycle in $resource detectecd")

  class UnreachableNodeDetected(resource: NodeResourceRef) extends AnalyzerException(resource, s"Unreachable resource $resource")

  class MultiTargetDetected(resource: NodeResourceRef) extends AnalyzerException(resource, s"Multi target $resource detected")

  class FlowFromSinkException(resource: NodeResourceRef) extends AnalyzerException(resource, s"Detected data flow from $resource")

  class ResourceNotFoundException(resource: NodeResourceRef) extends AnalyzerException(resource, s"Recource not found $resource")

}
