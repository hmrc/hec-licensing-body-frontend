/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked.SubmittedData
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType

import java.time.LocalDate

class AuditEventSpec extends Matchers with AnyWordSpecLike {

  "TaxCheckCodeChecked" must {

    "have the correct JSON" when {

      val taxCheckStatusTestCases =
        List(
          HECTaxCheckStatus.Match   -> "Success",
          HECTaxCheckStatus.NoMatch -> "Failed",
          HECTaxCheckStatus.Expired -> "Expired"
        )

      "individual data is given" in {
        taxCheckStatusTestCases.foreach { case (hecTaxCheckStatus, hecTaxCheckStatusString) =>
          val date  = LocalDate.of(2000, 12, 31)
          val event = TaxCheckCodeChecked(
            hecTaxCheckStatus,
            SubmittedData(
              HECTaxCheckCode("ABC"),
              EntityType.Individual,
              LicenceType.OperatorOfPrivateHireVehicles,
              Some(DateOfBirth(date)),
              None
            ),
            tooManyAttempts = false,
            Language.English
          )

          event.auditType       shouldBe "TaxCheckCodeChecked"
          event.transactionName shouldBe "tax-check-code-checked"
          Json.toJson(event)    shouldBe Json.parse(
            s"""
                |{
                |  "result": "$hecTaxCheckStatusString",
                |  "submittedData" : {
                |    "taxCheckCode": "ABC",
                |    "entityType": "Individual",
                |    "licenceType": "OperatorOfPrivateHireVehicles",
                |    "dateOfBirth": "2000-12-31"
                |  },
                |  "tooManyAttempts": false,
                |  "languagePreference": "English"
                |}
                |""".stripMargin
          )
        }

      }

      "company data is given" in {
        taxCheckStatusTestCases.foreach { case (hecTaxCheckStatus, hecTaxCheckStatusString) =>
          val crn   = CRN("crn")
          val event = TaxCheckCodeChecked(
            hecTaxCheckStatus,
            SubmittedData(
              HECTaxCheckCode("ABC"),
              EntityType.Company,
              LicenceType.ScrapMetalMobileCollector,
              None,
              Some(crn)
            ),
            tooManyAttempts = true,
            Language.Welsh
          )

          event.auditType       shouldBe "TaxCheckCodeChecked"
          event.transactionName shouldBe "tax-check-code-checked"
          Json.toJson(event)    shouldBe Json.parse(
            s"""
              |{
              |  "result": "$hecTaxCheckStatusString",
              |  "submittedData" : {
              |    "taxCheckCode": "ABC",
              |    "entityType": "Company",
              |    "licenceType": "ScrapMetalMobileCollector",
              |    "companyRegistrationNumber": "crn"
              |  },
              |  "tooManyAttempts": true,
              |  "languagePreference": "Welsh"
              |}
              |""".stripMargin
          )
        }
      }

    }

  }

}
