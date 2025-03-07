
# hec-licensing-body-frontend

More information about the HEC (Hidden Economy Conditionality) project can be found [here](https://www.gov.uk/government/publications/new-tax-checks-on-licence-renewal-applications).

This microservice serves the public digital UI to allow licensing bodies (LB's) to verify a tax 
check code. The LB will need to have some other verifying information about the  licence application 
in order to successfully do a check. Too many unsuccessful checks for a particular tax check code 
will lock the user from verifying that tax check code for a period of time.

## Running the service
When running locally, the dependant services can be run using the service manager command
```
sm2 --start HEC_DEP
```
All HEC services can run via
```
sm2 --start HEC_ALL
```
By default, this service runs on port **'10107'**.

To stop the frontend microservice from running on service manager (e.g. to run your own version locally), you can run:

```
sm2 -stop HEC_LICENSING_BODY_FRONTEND 
```


### Using localhost

To run this frontend microservice locally on the configured port **'10107'**, you can run:

```
sbt run 
```

**NOTE:** Ensure that you are not running the microservice via service manager before starting your service locally (vice versa)
or the service will fail to start

## Accessing the service

This service requires authentication stubbing before it can be accessed. Details can be found on the
[DDCY Live Services Credentials sheet](https://docs.google.com/spreadsheets/d/1ecLTROmzZtv97jxM-5LgoujinGxmDoAuZauu2tFoAVU/edit?gid=1186990023#gid=1186990023)
for both staging and local url's or check the Tech Overview section in the
[service summary page](https://confluence.tools.tax.service.gov.uk/display/ELSY/HEC+Service+Summary)


## Running tests via terminal

You can run tests in Intellij by running:

```
sbt test
```

This service uses sbt-scoverage to provide test coverage reports.
Use the following command to run the tests with coverage and generate a report:

```
sbt clean coverage test coverageReport
```

## Patterns

### Starting a journey
A journey can be started via the start endpoint
```
GET /confirm-tax-check-code/start
```
This service is unauthenticated.


### Navigation
The rules defining the route from one page to another are contained in `JourneyService`. As well as defining the routes
forward, this service also automatically calculates the previous page relative to a current page based upon the session
state. This is used to make the back links on the pages point to the right place.


### Test data
In order to get some valid tax check data that can be checked in this service, either complete a successful tax check 
journey on the [applicant frontend microservice](https://github.com/hmrc/hec-applicant-frontend) or use the 
test-only endpoint in the [back end microservice](https://github.com/hmrc/hec) to save a tax check.

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


## Other helpful documentation

* [Service Runbook](https://confluence.tools.tax.service.gov.uk/display/ELSY/Hidden+Economy+Conditionality+%28HEC%29+Runbook)

* [Architecture Links](https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=872972492)