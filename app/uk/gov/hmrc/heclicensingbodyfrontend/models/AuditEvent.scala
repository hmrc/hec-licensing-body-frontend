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

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked.SubmittedData
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType

sealed trait AuditEvent {

  val auditType: String

  val transactionName: String

}

object AuditEvent {

  final case class TaxCheckCodeChecked(
    result: HECTaxCheckStatus,
    submittedData: SubmittedData,
    tooManyAttempts: Boolean
  ) extends AuditEvent {

    val auditType: String = "TaxCheckCodeChecked"

    val transactionName: String = "tax-check-code-checked"

  }

  object TaxCheckCodeChecked {

    final case class SubmittedData(
      taxCheckCode: HECTaxCheckCode,
      entityType: EntityType,
      licenceType: LicenceType,
      dateOfBirth: Option[DateOfBirth],
      crn: Option[CRN]
    )

    implicit val submittedDataWrites: OWrites[SubmittedData] = Json.writes

    implicit val taxCheckCodeCheckedWRites: OWrites[TaxCheckCodeChecked] = Json.writes
  }

}