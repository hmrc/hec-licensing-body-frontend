/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.heclicensingbodyfrontend.models

import cats.Eq
import play.api.libs.json._

sealed trait EntityType extends Product with Serializable

object EntityType {

  case object Individual extends EntityType

  case object Company extends EntityType

  implicit val eq: Eq[EntityType] = Eq.fromUniversalEquals

  @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.Equals"))
  implicit val format: Format[EntityType] = new Format[EntityType] {
    override def writes(o: EntityType): JsValue = JsString(o.toString)

    override def reads(json: JsValue): JsResult[EntityType] = json match {
      case JsString("Individual") => JsSuccess(Individual)
      case JsString("Company")    => JsSuccess(Company)
      case _                      => JsError(s"Unknown entity type: ${json.toString()}")
    }
  }

}
