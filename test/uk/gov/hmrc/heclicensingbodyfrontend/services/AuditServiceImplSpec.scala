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

package uk.gov.hmrc.heclicensingbodyfrontend.services

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import play.api.mvc.{Headers, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked.SubmittedData
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, EntityType, HECTaxCheckCode, HECTaxCheckStatus, Language, MatchFailureReason}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuditServiceImplSpec extends Matchers with AnyWordSpecLike with MockFactory {

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  def mockSendExtendedEvent(expectedEvent: ExtendedDataEvent)(result: Future[AuditResult]) =
    (mockAuditConnector
      .sendExtendedEvent(_: ExtendedDataEvent)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where[ExtendedDataEvent, HeaderCarrier, ExecutionContext] {
        case (actualEvent: ExtendedDataEvent, _: HeaderCarrier, _: ExecutionContext) =>
          actualEvent.auditType === expectedEvent.auditType
          actualEvent.auditSource === expectedEvent.auditSource
          actualEvent.detail === expectedEvent.detail
          actualEvent.tags === expectedEvent.tags

      })
      .returning(result)

  val service = new AuditServiceImpl(mockAuditConnector)

  "AuditServiceImpl" when {

    "handling requests to audit an event" must {

      "return successfully" when {

        val requestUri = "/uri"

        implicit val request: Request[_] = FakeRequest("GET", requestUri, Headers(), "")

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val auditEvent = TaxCheckCodeChecked(
          HECTaxCheckStatus.Match,
          SubmittedData(
            HECTaxCheckCode(""),
            EntityType.Individual,
            LicenceType.OperatorOfPrivateHireVehicles,
            Some(DateOfBirth(LocalDate.now())),
            None
          ),
          tooManyAttempts = false,
          Language.English,
          Some(MatchFailureReason.DateOfBirthNotMatched)
        )

        val extendedDataEvent = ExtendedDataEvent(
          auditSource = "hidden-economy-conditionality",
          auditType = auditEvent.auditType,
          detail = Json.toJson(auditEvent),
          tags = hc.toAuditTags(auditEvent.transactionName, requestUri)
        )

        "a 'Successful' AuditResult is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Success))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "a 'Disabled' audit result is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Disabled))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "a 'Failure' audit result is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Failure("")))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "the call to audit fails" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.failed(new Exception("")))

          service.sendEvent(auditEvent) shouldBe (())
        }

      }

    }

  }

}
