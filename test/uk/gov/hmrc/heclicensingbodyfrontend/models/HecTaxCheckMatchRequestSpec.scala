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
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.NoMatch
import uk.gov.hmrc.heclicensingbodyfrontend.models.MatchFailureReason.TaxCheckCodeNotMatched
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType.DriverOfTaxisAndPrivateHires
import uk.gov.hmrc.heclicensingbodyfrontend.util.HttpResponseOps._
import uk.gov.hmrc.http.HttpResponse
import play.api.libs.json._

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class HecTaxCheckMatchRequestSpec extends AnyWordSpec with Matchers {

val json = """{
             |  "userAnswers": {
             |    "taxCheckCode": "266RLRTK6",
             |    "licenceType": "DriverOfTaxisAndPrivateHires",
             |    "dateOfBirth": "1995-12-23"
             |  },
             |  "taxCheckMatch": {
             |    "matchRequest": {
             |      "taxCheckCode": "266RLRTK6",
             |      "licenceType": "DriverOfTaxisAndPrivateHires",
             |      "verifier": {
             |        "dateofbirth": "1995-12-23"
             |      }
             |    },
             |    "dateTimeChecked": "2024-02-20T11:44:23.662233Z[Europe/London]",
             |    "status": "NoMatch(TaxCheckCodeNotMatched)"
             |  },
             |  "verificationAttempts": {
             |    "266RLRTK6": {
             |      "count": 1
             |    }
             |  }
             |}""".stripMargin

  "hec tax check match request" should {

    "parse json" in {
      val result = Json.parse(json).as[HECSession]
      result shouldBe Left("string")
    }
  }

}
