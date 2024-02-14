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

sealed trait MatchFailureReason extends Product with Serializable

object MatchFailureReason {

  case object TaxCheckCodeNotMatched extends MatchFailureReason

  case object EntityTypeNotMatched extends MatchFailureReason

  case object DateOfBirthNotMatched extends MatchFailureReason

  case object CRNNotMatched extends MatchFailureReason

  case object LicenceTypeNotMatched extends MatchFailureReason

  case object LicenceTypeEntityTypeNotMatched extends MatchFailureReason

  case object LicenceTypeDateOfBirthNotMatched extends MatchFailureReason

  case object LicenceTypeCRNNotMatched extends MatchFailureReason

  @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.Equals"))
  implicit val format: Format[MatchFailureReason] = new Format[MatchFailureReason] {
    override def writes(o: MatchFailureReason): JsValue = JsString(o.toString)

    override def reads(json: JsValue): JsResult[MatchFailureReason] = json match {
      case JsString("TaxCheckCodeNotMatched")           => JsSuccess(TaxCheckCodeNotMatched)
      case JsString("EntityTypeNotMatched")             => JsSuccess(EntityTypeNotMatched)
      case JsString("DateOfBirthNotMatched")            => JsSuccess(DateOfBirthNotMatched)
      case JsString("CRNNotMatched")                    => JsSuccess(CRNNotMatched)
      case JsString("LicenceTypeNotMatched")            => JsSuccess(LicenceTypeNotMatched)
      case JsString("LicenceTypeEntityTypeNotMatched")  => JsSuccess(LicenceTypeEntityTypeNotMatched)
      case JsString("LicenceTypeDateOfBirthNotMatched") => JsSuccess(LicenceTypeDateOfBirthNotMatched)
      case JsString("LicenceTypeCRNNotMatched")         => JsSuccess(LicenceTypeCRNNotMatched)
      case _                                            => JsError(s"Unknown match failure reason: ${json.toString()}")
    }
  }

}
