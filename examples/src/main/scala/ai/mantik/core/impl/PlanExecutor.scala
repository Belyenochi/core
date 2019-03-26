package ai.mantik.core.impl

import ai.mantik.core.Plan

import scala.concurrent.Future

/** Responsible for executing plans. */
trait PlanExecutor {

  /**
   * Execute a Plan.
   * @return a future to a plans result which can be casted accordingly.
   */
  def execute(plan: Plan): Future[Any]
}
