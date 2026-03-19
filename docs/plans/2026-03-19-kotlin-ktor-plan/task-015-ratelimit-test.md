# Task 015: Rate Limiting tests (BDD Feature 9)

**type**: test
**depends-on**: [004, 007-auth-impl]

## Description

Write BDD-driven test cases for Feature 9: Rate Limiting. This covers the Redis sliding window rate limiter with different limits per route group (auth, pub, user, admin), rate limit headers, and sliding window recovery. Tests should fail initially (Red phase).

Rate limit groups:
- `auth`: 20 req/min by IP (register, login, refresh)
- `pub`: 120 req/min by IP (public video/user listing)
- `user`: 200 req/min by customer_id (authenticated user actions)
- `admin`: 200 req/min by admin_id (all admin endpoints)

## Gherkin Specifications

```gherkin
Feature: Rate Limiting
  Redis sliding window rate limiter. Different limits per route group.
  auth: 20 req/min by IP, pub: 120 req/min by IP,
  user: 200 req/min by customer_id, admin: 200 req/min by admin_id.

  Scenario: Normal request within limit
    Given the auth rate limit is 20 requests per minute
    And I have made 10 requests this minute
    When I POST /api/v1/auth/login
    Then the response status is 200 (or normal error)
    And the header X-RateLimit-Limit is "20"
    And the header X-RateLimit-Remaining is "9"

  Scenario: Exceed auth rate limit
    Given the auth rate limit is 20 requests per minute
    And I have made 21 requests from the same IP in the last minute
    When I POST /api/v1/auth/login
    Then the response status is 429
    And the error message is "too many requests, please try again later"
    And the header Retry-After is present

  Scenario: User rate limit keyed by customer_id
    Given the user rate limit is 200 requests per minute
    And customer "alice" (id=1) has made 201 requests this minute
    When alice makes another request to /api/v1/videos/me
    Then the response status is 429
    But customer "bob" can still make requests (different key)

  Scenario: Public rate limit keyed by IP
    Given the public rate limit is 120 requests per minute
    And IP 10.0.0.1 has made 121 requests this minute
    When another request comes from 10.0.0.1 to /api/v1/videos
    Then the response status is 429
    But IP 10.0.0.2 can still make requests

  Scenario: Rate limit window slides
    Given I exceeded the limit at minute 0
    And the window is 1 minute
    When 61 seconds have passed
    Then new requests are allowed again
```

## Files

- `kotlin/src/test/kotlin/lab/gabon/plugin/RateLimitTest.kt` -- test cases for rate limit headers, auth/pub/user limit enforcement, key isolation, sliding window recovery

## Verification

1. `./gradlew test --tests "lab.gabon.plugin.RateLimitTest"` compiles
2. All tests fail (Red) because no rate limit plugin exists yet
