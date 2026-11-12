# Airtel Money Connector

Payment Hub Connector containing Zeebe workers that are responsible for communicating with [Airtel
Money APIs](https://developers.airtel.africa/documentation). It is a part of the OAF Payment Hub EE
setup. See
the [Payment Hub EE documentation](https://mifos.gitbook.io/docs/payment-hub-ee/overview)
for more information about Zeebe projects and the Payment Hub in general.

[![Build Status](https://dev.azure.com/OAFDev/prd-pipelines/_apis/build/status%2Fone-acre-fund.ph-ee-connector-airtel?branchName=main)](https://dev.azure.com/OAFDev/prd-pipelines/_build/latest?definitionId=179&branchName=main)

## Tech Stack

- Java 17
- Spring Boot
- Apache Camel
- Zeebe Java Client

## Getting Started

Clone the project

  ```bash
    git clone https://github.com/one-acre-fund/ph-ee-connector-airtel.git
    cd ph-ee-connector-airtel
  ```

This connector is expected to be run alongside other connectors/services. It depends on some of
those services being up
and healthy. For local development, the services that are most critical for running this project
have been included in
the `docker-compose.yml` file. The following components are included:

- Zeebe: A workflow engine for microservices orchestration. This must be running in a healthy state
  otherwise errors
  will occur when the services below attempt to connect to it.
- Zeebe-ops: Provides APIs for carrying out certain operations on zeebe such as uploading a bpmn
  file
- Channel-connector: Provides APIs for initiating collection requests
- Erply-connector: An Account Management System (AMS) connector for Erply. Any AMS connector can be
  used here

A lot more services can be added to the above based on your needs, but to run this connector
locally,
the ones listed above are the required minimum.
Please note that the `docker-compose.yml` file in this repository should NOT be used in a production
environment.

## Running with Docker

Some images listed in the `docker-compose.yml` are available on OAF's Azure Container Registry (
ACR). To be able to pull
them, certain permissions must be granted to your azure account. Follow the steps below to
successfully run the project:

- Ensure [Docker](https://docs.docker.com/get-docker/) is installed on your machine

- Authenticate with
  azure. [Install the Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)
  on your machine if it's not already available, and then run the command below

  ```bash
      az acr login -n oaftech # Log in to OAF's ACR through the Docker CLI.
   ```

- Run the project:

  Update `src/main/resources/application.yml` with the appropriate values where necessary, or
  provide the
  values as environment variables in the `services.airtel-connector.environment` section of
  the `docker-compose.yml`
  file, and run the command below:

  ```bash
      docker compose up -d
   ```

## Usage

To initiate a collection request, and have the airtel connector handle the transaction, follow the
steps below:

- Upload the erply bpmn (found in `src/main/resources/airtel_flow_erply-oaf.bpmn`) through *
  *zeebe-ops** by sending a
  POST
  request to `http://localhost:5001/zeebe/upload` with the file attached.

- Send a collection request through the **channel-connector** by sending a POST request
  to `http://localhost:5002/channel/collection`
  with a sample body as shown below:
  ```json
  {
    "payer": [
        {
            "key": "MSISDN",
            "value": "250730000000"
        },
        {
            "key": "WALLETID",
            "value": "60649568"
        }
    ],
    "amount": {
        "amount": "20",
        "currency": "RWF"
    }
  }
  ```
- Check the logs in the **airtel-connector** container to see that the tasks got executed
  successfully

## Calls to Airtel: timeouts, retries and circuit breaker

Every call to Airtel (token, collection, transaction status) has its own timeouts, retries and
circuit breaker, one per country, endpoint and host (for example
`airtel-rwanda-collection-openapi.airtel.co.rw`).

**Retries** are bounded, with exponential backoff and jitter. A call is retried when:

- the connection fails (the request never reached Airtel);
- Airtel answers with a status in `retry-status-codes`;
- no response comes back (read timeout), if `retry-when-outcome-unknown` is true.

**A collection is only retried when it never reached Airtel**, so a retry cannot prompt the
customer twice. When it was sent but no response came back, Airtel may still prompt the customer,
so the payment is not failed: the callback or the status check decides. A status check that gets no answer, or a 5xx,
keeps the payment pending until `airtel.max-retry-count` is reached.

**Retries stay within the Zeebe job.** An attempt (first call or retry) is only made if it can
finish, at its connect and read timeouts, `job-deadline-margin` (10s) before the job's deadline.
Otherwise the call ends with the previous attempt's result, or as not sent when no attempt was
made. A job therefore never times out while a call is running, so Zeebe cannot hand it to another
worker that would call Airtel again.

**Circuit breaker.** When `failure-rate-threshold` percent of the last `sliding-window-size` calls
fail (connection failures, timeouts, 5xx; 4xx do not count), the circuit opens and calls fail at
once without reaching Airtel: a collection is failed with `CIRCUIT_OPEN`, a status check stays
pending. After `wait-duration-in-open-state`, `permitted-calls-in-half-open-state` probe calls are
let through; if they succeed the circuit closes, otherwise it opens again.

**Defaults** (`airtel.resilience.<endpoint>` in `application.yml`; `<endpoint>` is `token`,
`collection` or `transaction-status`):

| Setting | token | collection | transaction-status |
|---|---|---|---|
| `connect-timeout` | 5s | 5s | 5s |
| `read-timeout` | `airtel.timeout` (6s) | `airtel.timeout` (6s) | `airtel.timeout` (6s) |
| `retry.max-attempts` (including the first call) | 3 | 3 | 3 |
| `retry.initial-backoff` / `backoff-multiplier` / `jitter` | 500ms / 2.0 / 0.5 | 500ms / 2.0 / 0.5 | 500ms / 2.0 / 0.5 |
| `retry.retry-status-codes` | 500,502,503,504 | none | 500,502,503,504 |
| `retry.retry-when-outcome-unknown` | true | false | true |
| `circuit-breaker.failure-rate-threshold` (%) | 50 | 50 | 50 |
| `circuit-breaker.sliding-window-size` / `minimum-number-of-calls` | 20 / 10 | 20 / 10 | 20 / 10 |
| `circuit-breaker.wait-duration-in-open-state` | 30s | 30s | 30s |
| `circuit-breaker.permitted-calls-in-half-open-state` | 3 | 3 | 3 |

Override any value with an environment variable, for example
`AIRTEL_RESILIENCE_COLLECTION_READTIMEOUT=20s`, `AIRTEL_RESILIENCE_TOKEN_RETRY_MAXATTEMPTS=2` or
`AIRTEL_RESILIENCE_TRANSACTIONSTATUS_CIRCUITBREAKER_WAITDURATIONINOPENSTATE=1m`.

**Monitoring.**

- Metrics on `/actuator/prometheus`: `resilience4j_circuitbreaker_state{name,state}`,
  `resilience4j_circuitbreaker_calls_seconds_*{name,kind}` and
  `resilience4j_retry_calls_total{name,kind}` (`kind="failed_with_retry"` counts calls that failed
  after all retries).
- Log events, for alerting:
  - `event=airtel_circuit_state_changed circuit=<name> from=<state> to=<state>`
  - `event=airtel_call_rejected circuit=<name>` (failed fast while open)
  - `event=airtel_call_retry call=<name> attempt=<n> wait_ms=<ms> reason=<reason>`
  - `event=airtel_retry_exhausted call=<name> attempts=<n> reason=<reason>`
  - `event=airtel_call_skipped call=<name> reason=job_deadline after=<reason>` (no time left
    before the job deadline)
- Reasons name the status code or exception class only, never URLs, headers or bodies.

## Troubleshooting

If an error occurs while carrying out any of the steps above, check if the zeebe container is in a
healthy state by
either viewing its state through `docker ps` or sending a GET request
to `http://localhost:9600/health`.
If the zeebe container shows state as unhealthy or the health endpoint doesn't return a 204 status
response, restart the
zeebe container.
