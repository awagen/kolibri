/**
 * Copyright 2023 Andreas Wagenmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives

import java.util.Objects

/**
 *
 */
object JobDirectives {

  val JOB_DIRECTIVE_PREFIX: String = "KDIR_"

  object JobDirective {
    def parse(str: String): JobDirective = {
      if (Objects.isNull(str) || !str.startsWith(JOB_DIRECTIVE_PREFIX)) Unknown
      else {
        str.stripPrefix(JOB_DIRECTIVE_PREFIX) match {
          case "UNKNOWN" => Unknown
          case "STOP_PROCESSING" => StopProcessing
          case e if e.startsWith("ONLY_NODE") => OnlyNode(e.stripPrefix("ONLY_NODE__"))
          case "PROCESS" => Process
          case _ => Unknown
        }
      }
    }

    def toString(jobDirective: JobDirective): String = jobDirective match {
      case Unknown => s"${JOB_DIRECTIVE_PREFIX}UNKNOWN"
      case StopProcessing => s"${JOB_DIRECTIVE_PREFIX}STOP_PROCESSING"
      case e: OnlyNode => s"${JOB_DIRECTIVE_PREFIX}ONLY_NODE__${e.nodeHash}"
      case Process => s"${JOB_DIRECTIVE_PREFIX}PROCESS"
    }
  }

  sealed trait JobDirective {
    def stringId: String
  }

  case object Unknown extends JobDirective {
    override def stringId: String = "UNKNOWN"
  }

  case object StopProcessing extends JobDirective {
    override def stringId: String = "STOP_PROCESSING"
  }

  sealed case class OnlyNode(nodeHash: String) extends JobDirective {
    override def stringId: String = "ONLY_NODE"
  }

  case object Process extends JobDirective {
    override def stringId: String = "PROCESS"
  }

}
