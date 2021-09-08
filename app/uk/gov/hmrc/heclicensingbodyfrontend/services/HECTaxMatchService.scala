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

package uk.gov.hmrc.heclicensingbodyfrontend.services

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import play.mvc.Http.Status.OK
import com.google.inject.ImplementedBy
import uk.gov.hmrc.heclicensingbodyfrontend.connectors.HECConnector
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECTaxCheckMatchRequest, HECTaxCheckMatchResult}
import uk.gov.hmrc.heclicensingbodyfrontend.util.HttpResponseOps._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HECTaxMatchServiceImpl])
trait HECTaxMatchService {
  def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HECTaxCheckMatchResult]
}

@Singleton
class HECTaxMatchServiceImpl @Inject() (hecConnector: HECConnector)(implicit ec: ExecutionContext)
    extends HECTaxMatchService {
  override def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HECTaxCheckMatchResult] = hecConnector
    .matchTaxCheck(taxCheckMatchRequest)
    .subflatMap { response =>
      if (response.status =!= OK)
        Left(Error(s"Call to match tax check data came back with status ${response.status}"))
      else
        response.parseJSON[HECTaxCheckMatchResult].leftMap(Error(_))
    }
}
