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

package de.awagen.kolibri.datatypes.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.stream.scaladsl.Source
import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object ParameterUtils {

  def generateSource(experiment: GridOrderedMultiValues, maxNrElements: Int): Source[String, NotUsed] = {
    val vals: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, maxNrElements)
    val params: Seq[String] = experiment.getParameterNameSequence
    val queryStrings: Seq[String] = parameterValuesToQueryStrings((params, vals))
    Source(queryStrings.toList)
  }

  def queryStringFromParameterNamesAndValues(map: Map[String, Seq[String]]): String = {
    var formattedParamSequence: Seq[String] = Seq.empty
    for (key <- map.keys){
      for (value <- map(key)){
        formattedParamSequence = formattedParamSequence :+ getFormattedQueryString(value, key)
      }
    }
    formattedParamSequence.mkString("&")
  }

  def queryStringFromParameterNamesAndValues(tuple: (Seq[String], Seq[Any])): String = {
    val formattedParamSequence: Array[String] = new Array(tuple._1.size)
    for (index <- tuple._1.indices){
      formattedParamSequence(index) = getFormattedQueryString(tuple._2(index),tuple._1(index))
    }
    formattedParamSequence.mkString("&")
  }

  def parameterValuesToQueryStrings(tuple: (Seq[String], Seq[Seq[Any]])): Seq[String] = {
    val queryStrings: ListBuffer[String] = mutable.ListBuffer.empty
    for (params <- tuple._2){
      queryStrings += queryStringFromParameterNamesAndValues((tuple._1, params))
    }
    queryStrings.result()
  }

  def getFormattedQueryString(arg: Any, parameterName: String): String = {
    val value = arg match {
      case arg: Float => "%.2f".formatLocal(java.util.Locale.US, arg)
      case arg: Double => "%.2f".formatLocal(java.util.Locale.US, arg)
      case _ => s"$arg"
    }
    s"$parameterName=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString)}"
  }

}
