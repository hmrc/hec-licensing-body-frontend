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

package uk.gov.hmrc.heclicensingbodyfrontend.views.helpers
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import play.api.data.Form

object FormErrorMessage {
  def formErrorMessage(form: Form[_], key: String)(implicit messages: Messages): Option[ErrorMessage] = form
    .error(key)
    .map(e =>
      ErrorMessage(
        content = Text(messages(s"${e.key}.${e.message}")),
        visuallyHiddenText = Some(messages("generic.errorPrefix"))
      )
    )
}
