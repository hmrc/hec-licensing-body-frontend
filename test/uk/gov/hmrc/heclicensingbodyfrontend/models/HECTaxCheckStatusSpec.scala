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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString}

class HECTaxCheckStatusSpec extends AnyWordSpec with Matchers {

  "HECTaxCheckStatus" must {

    "have a 'matchFailureReason' method which returns the correct value" when afterWord("called on a") {

      "Match" in {
        HECTaxCheckStatus.Match.matchFailureReason shouldBe None
      }

      "NoMatch" in {
        List(
          MatchFailureReason.TaxCheckCodeNotMatched,
          MatchFailureReason.EntityTypeNotMatched,
          MatchFailureReason.DateOfBirthNotMatched,
          MatchFailureReason.CRNNotMatched,
          MatchFailureReason.LicenceTypeNotMatched,
          MatchFailureReason.LicenceTypeEntityTypeNotMatched,
          MatchFailureReason.LicenceTypeDateOfBirthNotMatched,
          MatchFailureReason.LicenceTypeCRNNotMatched
        ).foreach { reason =>
          withClue(s"For match failure reason '$reason': ") {
            HECTaxCheckStatus.NoMatch(reason).matchFailureReason shouldBe Some(reason)
          }

        }
      }

      "Expired" in {
        HECTaxCheckStatus.Expired.matchFailureReason shouldBe None

      }

    }

    "fail to read from JSON" when {

      val js = JsString("aaaaaaa")

      "the HEC tax check status is not recognised" in {
        js.validate[HECTaxCheckStatus] shouldBe JsError(s"Unknown failure reason ${js.toString()}")
      }
    }

  }

}
