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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.Request
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def sendEvent[A <: AuditEvent](auditEvent: A)(implicit
    hc: HeaderCarrier,
    writes: Writes[A],
    request: Request[_]
  ): Unit

}

@Singleton
class AuditServiceImpl @Inject() (auditConnector: AuditConnector)(implicit ec: ExecutionContext)
    extends AuditService
    with Logging {

  private val auditSource: String = "hidden-economy-conditionality"

  override def sendEvent[A <: AuditEvent](
    auditEvent: A
  )(implicit
    hc: HeaderCarrier,
    writes: Writes[A],
    request: Request[_]
  ): Unit = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = auditSource,
      auditType = auditEvent.auditType,
      detail = Json.toJson(auditEvent),
      tags = hc.toAuditTags(auditEvent.transactionName, request.uri)
    )
    auditConnector.sendExtendedEvent(extendedDataEvent).onComplete {
      case Success(_) => ()
      case Failure(e) =>
        // WARNING: do not change log message here without updating alert-config as well
        logger.warn(s"Could not audit ${auditEvent.auditType} event: ${e.getMessage}")
    }
  }

}
