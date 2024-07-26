
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
By default, this service runs on port `10107`.


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
