/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, Json}

class MatchFailureReasonSpec extends AnyWordSpec with Matchers {

  "MatchFailureReason" should {

    "write to JSON" when {

      s"the match failure reason is ${MatchFailureReason.TaxCheckCodeNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.TaxCheckCodeNotMatched) shouldBe JsString(
          "TaxCheckCodeNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.EntityTypeNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.EntityTypeNotMatched) shouldBe JsString(
          "EntityTypeNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.DateOfBirthNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.DateOfBirthNotMatched) shouldBe JsString(
          "DateOfBirthNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.CRNNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.CRNNotMatched) shouldBe JsString("CRNNotMatched")
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.LicenceTypeNotMatched) shouldBe JsString(
          "LicenceTypeNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeEntityTypeNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.LicenceTypeEntityTypeNotMatched) shouldBe JsString(
          "LicenceTypeEntityTypeNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeDateOfBirthNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.LicenceTypeDateOfBirthNotMatched) shouldBe JsString(
          "LicenceTypeDateOfBirthNotMatched"
        )
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeCRNNotMatched}" in {
        Json.toJson[MatchFailureReason](MatchFailureReason.LicenceTypeCRNNotMatched) shouldBe JsString(
          "LicenceTypeCRNNotMatched"
        )
      }
    }

    "read from JSON" when {

      s"the match failure reason is ${MatchFailureReason.TaxCheckCodeNotMatched}" in {
        JsString("TaxCheckCodeNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.TaxCheckCodeNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.EntityTypeNotMatched}" in {
        JsString("EntityTypeNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.EntityTypeNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.DateOfBirthNotMatched}" in {
        JsString("DateOfBirthNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.DateOfBirthNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.CRNNotMatched}" in {
        JsString("CRNNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.CRNNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeNotMatched}" in {
        JsString("LicenceTypeNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.LicenceTypeNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeEntityTypeNotMatched}" in {
        JsString("LicenceTypeEntityTypeNotMatched")
          .as[MatchFailureReason] shouldBe MatchFailureReason.LicenceTypeEntityTypeNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeDateOfBirthNotMatched}" in {
        JsString("LicenceTypeDateOfBirthNotMatched")
          .as[MatchFailureReason] shouldBe MatchFailureReason.LicenceTypeDateOfBirthNotMatched
      }

      s"the match failure reason is ${MatchFailureReason.LicenceTypeCRNNotMatched}" in {
        JsString("LicenceTypeCRNNotMatched").as[MatchFailureReason] shouldBe MatchFailureReason.LicenceTypeCRNNotMatched
      }
    }

    "fail to read from JSON" when {

      val js = JsString("aaaaaaa")

      "the match failure reason is not recognised" in {
        js.validate[MatchFailureReason] shouldBe JsError(s"Unknown match failure reason: ${js.toString()}")
      }
    }
  }

}
