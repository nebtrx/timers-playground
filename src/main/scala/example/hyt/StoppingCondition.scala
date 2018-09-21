package example.hyt

sealed trait StoppingCondition
case object NotMatched extends StoppingCondition
case object Matched extends StoppingCondition
