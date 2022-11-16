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

package uk.gov.hmrc.heclicensingbodyfrontend.util

import cats.implicits.{catsKernelStdOrderForInt, catsSyntaxEq}
import cats.syntax.either._
import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.i18n.Messages

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDate, ZoneId, ZonedDateTime}
import scala.util.Try

object TimeUtils {

  val clock: Clock = Clock.systemUTC()

  def today(): LocalDate   = LocalDate.now(clock)
  def now(): ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/London"))

  def dateFormatter(
    maximumDateInclusive: Option[LocalDate],
    minimumDateInclusive: Option[LocalDate],
    dayKey: String,
    monthKey: String,
    yearKey: String,
    dateKey: String,
    extraValidation: List[LocalDate => Either[Seq[FormError], Unit]] = List.empty,
    tooFarInFutureArgs: Seq[String] = Seq.empty,
    tooFarInPastArgs: Seq[String] = Seq.empty
  ): Formatter[LocalDate] =
    new Formatter[LocalDate] {
      def dateFieldStringValues(
        data: Map[String, String]
      ): Either[Seq[FormError], (String, String, String)] =
        List(dayKey, monthKey, yearKey)
          .map(data.get(_).map(_.trim).filter(_.nonEmpty)) match {
          case Some(dayString) :: Some(monthString) :: Some(
                yearString
              ) :: Nil =>
            Right((dayString, monthString, yearString))
          case None :: Some(_) :: Some(_) :: Nil =>
            Left(Seq(FormError(dayKey, "error.dayRequired")))
          case Some(_) :: None :: Some(_) :: Nil =>
            Left(Seq(FormError(monthKey, "error.monthRequired")))
          case Some(_) :: Some(_) :: None :: Nil =>
            Left(Seq(FormError(yearKey, "error.yearRequired")))
          case Some(_) :: None :: None :: Nil    =>
            val errorMessage = "error.monthAndYearRequired"
            Left(Seq(FormError(monthKey, errorMessage), FormError(yearKey, errorMessage)))
          case None :: Some(_) :: None :: Nil    =>
            val errorMessage = "error.dayAndYearRequired"
            Left(Seq(FormError(dayKey, errorMessage), FormError(yearKey, errorMessage)))
          case None :: None :: Some(_) :: Nil    =>
            val errorMessage = "error.dayAndMonthRequired"
            Left(Seq(FormError(dayKey, errorMessage), FormError(monthKey, errorMessage)))
          case _                                 =>
            Left(Seq(FormError(dateKey, "error.required")))
        }

      def toValidInt(
        stringValue: String,
        maxValue: Option[Int],
        key: String
      ): Either[FormError, Int] =
        Either.fromOption(
          Try(BigDecimal(stringValue).toIntExact).toOption.filter(i => i > 0 && maxValue.forall(i <= _)),
          FormError(key, "error.invalid")
        )

      override def bind(
        key: String,
        data: Map[String, String]
      ): Either[Seq[FormError], LocalDate] =
        for {
          dateFieldStrings <- dateFieldStringValues(data)
          month            <- toValidInt(dateFieldStrings._2, Some(12), monthKey).leftMap(Seq(_))
          year             <- toValidInt(dateFieldStrings._3, None, yearKey).leftMap(Seq(_))
          date             <- toValidInt(dateFieldStrings._1, Some(31), dayKey)
                                .leftMap(Seq(_))
                                .flatMap(day =>
                                  Either
                                    .fromTry(Try(LocalDate.of(year, month, day)))
                                    .leftMap(_ => Seq(FormError(dayKey, "error.invalid")))
                                )
          _                <-
            if (dateFieldStrings._3.length =!= 4)
              Left(Seq(FormError(yearKey, "error.yearLength")))
            else if (maximumDateInclusive.exists(_.isBefore(date)))
              Left(Seq(FormError(dateKey, "error.inFuture", tooFarInFutureArgs)))
            else if (minimumDateInclusive.exists(_.isAfter(date)))
              Left(Seq(FormError(dateKey, "error.tooFarInPast", tooFarInPastArgs)))
            else
              extraValidation
                .map(_(date))
                .find(_.isLeft)
                .getOrElse(Right(()))
                .map(_ => date)

        } yield date

      override def unbind(key: String, value: LocalDate): Map[String, String] =
        Map(
          dayKey   -> value.getDayOfMonth.toString,
          monthKey -> value.getMonthValue.toString,
          yearKey  -> value.getYear.toString
        )

    }

  def govDisplayFormat(date: LocalDate)(implicit messages: Messages): String =
    s"""${date.getDayOfMonth()} ${messages(
        s"date.${date.getMonthValue()}"
      )} ${date.getYear()}"""

  private def getAmPm(date: ZonedDateTime) = if (date.getHour >= 12) "afterNoon" else "beforeNoon"

  private def formatHourMinutes(time: ZonedDateTime): String =
    time.format(DateTimeFormatter.ofPattern("h:mm"))

  def govDateTimeDisplayFormat(date: ZonedDateTime)(implicit messages: Messages): String = {
    val day     = date.getDayOfMonth()
    val month   = messages(s"date.${date.getMonthValue()}")
    val year    = date.getYear
    val hourMin = formatHourMinutes(date)
    val amOrPm  = messages(s"date.${getAmPm(date)}")
    s"""$day $month $year, $hourMin$amOrPm"""
  }

}
