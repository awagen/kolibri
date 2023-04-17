package de.awagen.kolibri.fleet.akka.jobdefinitions

import akka.actor.Props

object ProcessingActorProps extends Enumeration {
  type ProcessingActorProps = Val

  case class Val(props: Props)


}
