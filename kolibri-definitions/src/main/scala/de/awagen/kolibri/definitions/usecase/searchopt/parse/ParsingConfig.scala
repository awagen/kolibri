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


package de.awagen.kolibri.definitions.usecase.searchopt.parse

import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.NamedAndTypedSelector


/**
  * container to provide typed json selectors
  *
  * we are only referencing specific types here as this is the level on which the json protocols are available at the moment
  * after generalizing that, can switch to SingleValueSelector and SeqSelector
  *
  * @param singleSelectors - single value selector, e.g non-recursive
  * @param seqSelectors    - seq selectors, e.g recursive
  */
case class ParsingConfig(selectors: Seq[NamedAndTypedSelector[Any]])
