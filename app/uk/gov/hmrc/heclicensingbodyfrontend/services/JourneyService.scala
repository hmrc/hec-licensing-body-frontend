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

import cats.Eq
import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.mvc.Call
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.routes
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{EntityType, Error, HECSession}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyServiceImpl])
trait JourneyService {

  def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[_],
    hc: HeaderCarrier
  ): EitherT[Future, Error, Call]

  def previous(current: Call)(implicit r: RequestWithSessionData[_]): Call

  def firstPage: Call

}

@Singleton
class JourneyServiceImpl @Inject() (sessionStore: SessionStore)(implicit ex: ExecutionContext) extends JourneyService {

  implicit val callEq: Eq[Call] = Eq.instance(_.url === _.url)

  // map representing routes from one page to another when users submit answers. The keys are the current page and the
  // values are the destination pages which come after the current page. The destination can sometimes depend
  // on state (e.g. the type of user or the answers users have submitted), hence the value type `HECSession => Call`
  lazy val paths: Map[Call, HECSession => Call] = Map(
    routes.StartController.start()                     -> (_ => firstPage),
    routes.HECTaxCheckCodeController.hecTaxCheckCode() -> (_ => routes.LicenceTypeController.licenceType()),
    routes.LicenceTypeController.licenceType()         -> licenceTypeRoute,
    routes.EntityTypeController.entityType()           -> entityTypeRoute
  )

  lazy val firstPage: Call = routes.HECTaxCheckCodeController.hecTaxCheckCode()

  override def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[_],
    hc: HeaderCarrier
  ): EitherT[Future, Error, Call] =
    paths.get(current).map(_(updatedSession)) match {
      case None       =>
        EitherT.leftT(Error(s"Could not find next for $current"))
      case Some(next) =>
        if (r.sessionData === updatedSession)
          EitherT.pure(next)
        else
          sessionStore.store(updatedSession).map(_ => next)
    }

  override def previous(current: Call)(implicit
    r: RequestWithSessionData[_]
  ): Call = {
    @tailrec
    def loop(previous: Call): Option[Call] =
      paths.get(previous) match {
        case Some(calculateNext) =>
          val next = calculateNext(r.sessionData)
          if (next === current) Some(previous)
          else loop(next)
        case _                   => None
      }

    if (current === routes.StartController.start())
      current
    else
      loop(routes.StartController.start())
        .getOrElse(sys.error(s"Could not find previous for $current"))
  }

  private def licenceTypeRoute(session: HECSession): Call = {
    val licenceType = session.userAnswers.licenceType
    if (licenceType.exists(licenceTypeForIndividualAndCompany)) routes.EntityTypeController.entityType()
    else routes.DateOfBirthController.dateOfBirth()
  }

  private def licenceTypeForIndividualAndCompany(licenceType: LicenceType): Boolean =
    licenceType =!= LicenceType.DriverOfTaxisAndPrivateHires

  private def entityTypeRoute(session: HECSession): Call = {
    val entityType = session.userAnswers.entityType
    if (entityType.contains(EntityType.Individual)) {
      routes.DateOfBirthController.dateOfBirth()
    } else {
      routes.CRNController.companyRegistrationNumber()
    }
  }

}
