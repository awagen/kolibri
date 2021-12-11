/**
  * Copyright 2021 Andreas Wagenmann
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


package de.awagen.kolibri.base.config.di.modules.connections

import com.softwaremill.macwire.wire
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.config.di.modules.Modules.HttpConnectionPoolDIModule
import de.awagen.kolibri.base.config.di.modules.connections.HttpModule.logger
import org.slf4j.{Logger, LoggerFactory}

object HttpModule {
  lazy private val logger: Logger = LoggerFactory.getLogger(this.getClass)
}

class HttpModule {

  lazy val httpDIModule: HttpConnectionPoolDIModule = AppProperties.config.httpConnectionPoolMode match {
    case "STANDARD" => wire[HttpConnectionPoolModule]
    case "CLASS" =>
      val module: String = AppProperties.config.httpConnectionPoolModuleClass.get
      logger.info(s"using classloader to load http module: $module")
      this.getClass.getClassLoader.loadClass(module).getDeclaredConstructor().newInstance().asInstanceOf[HttpConnectionPoolDIModule]
    case _ => wire[HttpConnectionPoolModule]
  }

}
