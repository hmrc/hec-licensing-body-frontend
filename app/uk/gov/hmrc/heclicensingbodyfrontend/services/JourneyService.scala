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

import cats.Eq
import cats.data.EitherT
import cats.instances.future.*
import cats.instances.string.*
import cats.syntax.eq.*
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.mvc.Call
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.routes
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.*
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{EntityType, Error, HECSession, HECTaxCheckMatchResult, InconsistentSessionState}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyServiceImpl])
trait JourneyService {

  def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[?],
    hc: HeaderCarrier
  ): EitherT[Future, Error, Call]

  def previous(current: Call)(implicit r: RequestWithSessionData[?]): Call

  def firstPage: Call

}

@Singleton
class JourneyServiceImpl @Inject() (
  sessionStore: SessionStore,
  verificationService: VerificationService
)(implicit
  ex: ExecutionContext
) extends JourneyService {

  implicit val callEq: Eq[Call] = Eq.instance(_.url === _.url)

  // map representing routes from one page to another when users submit answers. The keys are the current page and the
  // values are the destination pages which come after the current page. The destination can sometimes depend
  // on state (e.g. the type of user or the answers users have submitted), hence the value type `HECSession => Call`
  lazy val paths: Map[Call, HECSession => Call] = Map(
    routes.StartController.start                     -> (_ => firstPage),
    routes.HECTaxCheckCodeController.hecTaxCheckCode -> taxCheckCodeRoute,
    routes.LicenceTypeController.licenceType         -> licenceTypeRoute,
    routes.EntityTypeController.entityType           -> entityTypeRoute,
    routes.DateOfBirthController.dateOfBirth         -> dateOfBirthOrCRNRoute,
    routes.CRNController.companyRegistrationNumber   -> dateOfBirthOrCRNRoute
  )

  lazy val firstPage: Call = routes.HECTaxCheckCodeController.hecTaxCheckCode

  override def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[?],
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
    r: RequestWithSessionData[?]
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

    if (current === routes.StartController.start)
      current
    else
      loop(routes.StartController.start)
        .getOrElse(InconsistentSessionState(s"Could not find previous for $current").doThrow)
  }

  private def taxCheckCodeRoute(session: HECSession): Call =
    session.userAnswers.taxCheckCode match {
      case None =>
        InconsistentSessionState("Could not find tax check code for tax check code route").doThrow

      case Some(_) =>
        routes.LicenceTypeController.licenceType
    }

  private def licenceTypeRoute(session: HECSession): Call =
    session.userAnswers.licenceType match {
      case None =>
        InconsistentSessionState("Could not find licence type for licence type route").doThrow

      case Some(licenceType) =>
        if (licenceTypeForIndividualAndCompany(licenceType)) routes.EntityTypeController.entityType
        else routes.DateOfBirthController.dateOfBirth

    }

  private def licenceTypeForIndividualAndCompany(licenceType: LicenceType): Boolean =
    licenceType =!= LicenceType.DriverOfTaxisAndPrivateHires

  private def entityTypeRoute(session: HECSession): Call =
    session.userAnswers.entityType match {
      case None =>
        InconsistentSessionState("Could not find entity type for entity type route").doThrow

      case Some(EntityType.Individual) =>
        routes.DateOfBirthController.dateOfBirth

      case Some(EntityType.Company) =>
        routes.CRNController.companyRegistrationNumber

    }

  private def dateOfBirthOrCRNRoute(session: HECSession): Call = {
    val taxCode           =
      session.userAnswers.taxCheckCode.getOrElse(InconsistentSessionState("taxCheckCode is not in session").doThrow)
    val maxAttemptReached = verificationService.maxVerificationAttemptReached(taxCode)(session)
    if (maxAttemptReached) {
      routes.TaxCheckResultController.tooManyVerificationAttempts
    } else {
      session.taxCheckMatch match {
        case Some(taxMatch) =>
          taxMatch match {
            case HECTaxCheckMatchResult(_, _, Match)      => routes.TaxCheckResultController.taxCheckMatch
            case HECTaxCheckMatchResult(_, _, Expired)    => routes.TaxCheckResultController.taxCheckExpired
            case HECTaxCheckMatchResult(_, _, NoMatch(_)) => routes.TaxCheckResultController.taxCheckNotMatch
          }

        case None =>
          session.taxCheckMatch.map(_.matchRequest.verifier) match {
            case Some(Left(_))  => InconsistentSessionState("Could not find tax match result for crn route").doThrow
            case Some(Right(_)) =>
              InconsistentSessionState("Could not find tax match result for date of birth route").doThrow
            case None           => InconsistentSessionState("Neither date of birth nor crn in session").doThrow
          }

      }
    }

  }

}
