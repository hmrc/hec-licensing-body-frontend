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

import play.api.libs.json._

sealed trait HECTaxCheckStatus extends Product with Serializable

object HECTaxCheckStatus {

  case object Match extends HECTaxCheckStatus

  final case class NoMatch(failureReason: MatchFailureReason) extends HECTaxCheckStatus

  case object Expired extends HECTaxCheckStatus

  implicit class HECTaxCheckStatusOps(private val s: HECTaxCheckStatus) extends AnyVal {

    def matchFailureReason: Option[MatchFailureReason] = s match {
      case NoMatch(reason) => Some(reason)
      case _               => None
    }

  }

  @SuppressWarnings(Array("org.wartremover.warts.All"))
  implicit val format: Format[HECTaxCheckStatus] = new Format[HECTaxCheckStatus] {
    override def reads(json: JsValue): JsResult[HECTaxCheckStatus] = json match {
      case JsString("Match")   => JsSuccess(Match)
      case JsString("Expired") => JsSuccess(Expired)
      case _: JsObject         => (json \ "failureReason").validate[MatchFailureReason].map(NoMatch)
      case _                   => JsError(s"Failure reason")
    }

    override def writes(o: HECTaxCheckStatus): JsValue = o match {
      case Match                  => JsString("Match")
      case Expired                => JsString("Expired")
      case NoMatch(failureReason) => Json.obj("failureReason" -> failureReason)
    }
  }

}
