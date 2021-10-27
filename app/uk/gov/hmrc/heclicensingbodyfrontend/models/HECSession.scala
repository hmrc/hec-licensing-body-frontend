/*
 * Copyright 2021 HM Revenue & Customs
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

//import ai.x.play.json.implicits.formatSingleton
import cats.Eq
//import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.MapWrites.mapWrites
import play.api.libs.json.Reads.mapReads
import play.api.libs.json._

import java.time.ZonedDateTime

final case class Attempts(count: Int, lockExpiresAt: Option[ZonedDateTime])

object Attempts {
  implicit val format: OFormat[Attempts] = Json.format
}
final case class HECSession(
  userAnswers: UserAnswers,
  taxCheckMatch: Option[HECTaxCheckMatchResult],
  verificationAttempts: Map[HECTaxCheckCode, Attempts] = Map.empty
)

object HECSession {

  implicit val eq: Eq[HECSession] = Eq.fromUniversalEquals

  implicit val reads: Reads[Map[HECTaxCheckCode, Attempts]] =
    (jv: JsValue) =>
      JsSuccess(jv.as[Map[String, JsValue]].map { case (k, v) =>
        val attempts = v.as[Attempts]
        HECTaxCheckCode(k) -> attempts
      })

  implicit val writes: Writes[Map[HECTaxCheckCode, Attempts]] =
    mapWrites[Attempts].contramap(_.map { case (k, v) => k.value -> v })

  implicit val format: OFormat[HECSession]                    = Json.format

}
