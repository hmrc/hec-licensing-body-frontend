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

package uk.gov.hmrc.heclicensingbodyfrontend.connectors

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Writes
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECTaxCheckMatchRequest}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HECConnectorImpl])
trait HECConnector {
  def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class HECConnectorImpl @Inject() (http: HttpClient, config: Configuration)(implicit
  ec: ExecutionContext
) extends HECConnector {
  private val servicesConfig           = new ServicesConfig(config)
  private val baseUrl: String          = servicesConfig.baseUrl("hec")
  private val matchTaxCheckUrl: String = s"$baseUrl/hec/match-tax-check"
  val internalAuthToken: String        = config.get[String]("internal-auth.token")

  override def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = EitherT[Future, Error, HttpResponse] {
    val headers = Seq(HeaderNames.authorisation -> internalAuthToken)
    http
      .POST[HECTaxCheckMatchRequest, HttpResponse](matchTaxCheckUrl, taxCheckMatchRequest, headers)(
        implicitly[Writes[HECTaxCheckMatchRequest]],
        implicitly[HttpReads[HttpResponse]],
        implicitly[HeaderCarrier].copy(authorization = None),
        ec
      )
      .map(Right(_))
      .recover { case e => Left(Error(e)) }
  }
}
