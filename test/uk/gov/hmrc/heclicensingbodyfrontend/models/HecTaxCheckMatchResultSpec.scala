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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.Match
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class HecTaxCheckMatchResultSpec extends AnyWordSpec with Matchers {

  "HecTaxCheckMatchResultSpec" must {

    "perform JSON de/serialisation correctly" must {

      val hecTaxCheckCode                = HECTaxCheckCode("ABC DEF 123")
      val dateOfBirth                    = DateOfBirth(LocalDate.of(1922, 12, 1))
      val dateTimeChecked: ZonedDateTime = ZonedDateTime.of(2021, 9, 10, 8, 2, 0, 0, ZoneId.of("Europe/London"))
      val crn                            = CRN("SS123456")

      val matchRequestIndividual =
        HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(dateOfBirth))
      val matchRequestCompany    =
        HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.OperatorOfPrivateHireVehicles, Left(crn))

      val hecTaxCheckMatchResultIndividual = HECTaxCheckMatchResult(matchRequestIndividual, dateTimeChecked, Match)
      val hecTaxCheckMatchResultCompany    = HECTaxCheckMatchResult(matchRequestCompany, dateTimeChecked, Match)

      val matchResultIndividual =
        Json.parse(s"""
           |{
           |  "matchRequest": {
           |    "taxCheckCode": "ABC DEF 123",
           |    "licenceType": "DriverOfTaxisAndPrivateHires",
           |    "verifier": {"dateofbirth":"19221201"}
           |  },
           |  "dateTimeChecked" : "2021-09-10T08:02:00+01:00[Europe/London]",
           |  "status" : "Match"
           |  
           |}
           |""".stripMargin)

      val matchResultCompany = Json.parse(s"""
                      |{
                      |  "matchRequest": {
                      |  "taxCheckCode": "ABC DEF 123",
                      |  "licenceType": "OperatorOfPrivateHireVehicles",
                      |  "verifier":{"crn":"SS123456"}
                      |},
                      |  "dateTimeChecked" : "2021-09-10T08:02:00+01:00[Europe/London]",
                      |  "status" : "Match"
                      |}
                      |
                      |""".stripMargin)

      "serialize Individual check match code" in {
        Json.toJson(hecTaxCheckMatchResultIndividual) shouldBe matchResultIndividual
      }

      "serialize Company check match code" in {
        Json.toJson(hecTaxCheckMatchResultCompany) shouldBe matchResultCompany
      }

    }

  }
}
