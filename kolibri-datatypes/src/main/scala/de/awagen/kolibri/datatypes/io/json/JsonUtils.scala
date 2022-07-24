package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol.TYPE_KEY
import spray.json.{JsObject, JsString, JsValue}

object JsonUtils {

  def enrichJsonWithType(typeName: String, jsObj: JsObject): JsObject = {
    val objJsonFields: Map[String, JsValue] = jsObj.fields
    val jsonWithType: Map[String, JsValue] = objJsonFields + (TYPE_KEY -> JsString(typeName))
    JsObject(jsonWithType)
  }

}
