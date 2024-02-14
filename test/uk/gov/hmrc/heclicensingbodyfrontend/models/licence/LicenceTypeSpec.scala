package uk.gov.hmrc.heclicensingbodyfrontend.models.licence

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, Json}

class LicenceTypeSpec extends AnyWordSpec with Matchers {

  "LicenceType" should {

    "write to JSON" when {

      s"the licence type is ${LicenceType.DriverOfTaxisAndPrivateHires}" in {
        Json.toJson[LicenceType](LicenceType.DriverOfTaxisAndPrivateHires) shouldBe JsString(
          "DriverOfTaxisAndPrivateHires"
        )
      }

      s"the licence type is ${LicenceType.OperatorOfPrivateHireVehicles}" in {
        Json.toJson[LicenceType](LicenceType.OperatorOfPrivateHireVehicles) shouldBe JsString(
          "OperatorOfPrivateHireVehicles"
        )
      }

      s"the licence type is ${LicenceType.ScrapMetalMobileCollector}" in {
        Json.toJson[LicenceType](LicenceType.ScrapMetalMobileCollector) shouldBe JsString("ScrapMetalMobileCollector")
      }

      s"the licence type is ${LicenceType.ScrapMetalDealerSite}" in {
        Json.toJson[LicenceType](LicenceType.ScrapMetalDealerSite) shouldBe JsString("ScrapMetalDealerSite")
      }

      s"the licence type is ${LicenceType.BookingOffice}" in {
        Json.toJson[LicenceType](LicenceType.BookingOffice) shouldBe JsString("BookingOffice")
      }
    }

    "read from JSON" when {

      s"the licence type is ${LicenceType.DriverOfTaxisAndPrivateHires}" in {
        JsString("DriverOfTaxisAndPrivateHires").as[LicenceType] shouldBe LicenceType.DriverOfTaxisAndPrivateHires
      }

      s"the licence type is ${LicenceType.OperatorOfPrivateHireVehicles}" in {
        JsString("OperatorOfPrivateHireVehicles").as[LicenceType] shouldBe LicenceType.OperatorOfPrivateHireVehicles
      }

      s"the licence type is ${LicenceType.ScrapMetalMobileCollector}" in {
        JsString("ScrapMetalMobileCollector").as[LicenceType] shouldBe LicenceType.ScrapMetalMobileCollector
      }

      s"the licence type is ${LicenceType.ScrapMetalDealerSite}" in {
        JsString("ScrapMetalDealerSite").as[LicenceType] shouldBe LicenceType.ScrapMetalDealerSite
      }

      s"the licence type is ${LicenceType.BookingOffice}" in {
        JsString("BookingOffice").as[LicenceType] shouldBe LicenceType.BookingOffice
      }
    }

    "fail to read from JSON" when {

      val js = JsString("aaaaaaa")

      s"the licence type is not recognised" in {
        js.validate[LicenceType] shouldBe JsError(s"Unknown licence type: ${js.toString()}")

      }
    }
  }

}
