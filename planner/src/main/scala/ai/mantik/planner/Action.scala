package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.MantikId

/**
 * An Action is something the user requests to be executed.
 *
 * They are translated to a Plan by the [[Planner]].
 *
 * @tparam T the value returned by this action
 */
sealed trait Action[T]

object Action {

  /** Fetch a dataset. */
  case class FetchAction(dataSet: DataSet) extends Action[Bundle]

  /** Something is going to be saved. */
  case class SaveAction(item: MantikItem, id: MantikId) extends Action[Unit]

  /**
   * Deploy some item.
   * Returns the deployment state of the item.
   */
  case class Deploy(item: MantikItem, nameHint: Option[String] = None, ingressName: Option[String] = None) extends Action[DeploymentState]
}
