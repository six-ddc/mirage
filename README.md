# mirage

![](https://github.com/six-ddc/mirage/workflows/mvn/badge.svg)

A web DSL to easily create a simple HTTP server

## Features

- **DSL syntax:** Human-readable [DSL](http://docs.groovy-lang.org/docs/latest/html/documentation/core-domain-specific-languages.html) syntax, let us be more intuitive write we need to provide HTTP server.

- **Data Faker:** Add [Java Faker](https://github.com/DiUS/java-faker) for generating mock data such as names, addresses, and phone numbers.

- **Out of the box:** After a simple installation, you run it on CLI without any configuration or modification.

- **File watching:** Server will automatically reload when the specified files has been changed.

- **High performance:** The Web framework implementation is based on [Spring Boot](https://spring.io/projects/spring-boot), and DSL syntax is based on [Groovy](http://groovy-lang.org/).

## Requirements

- Java (1.7 or newer)
- Maven (if build)

## Download / Installation

```shell
make && make install
```

Release binaries are available from [GitHub releases](https://github.com/six-ddc/mirage/releases).

## Usage

```
usage: mirage [-p <port>] [-c <script>] [file|dir]...
A web DSL to easily create a simple HTTP server

 -c,--script <script>       raw dsl script
    --ext <ext>             specify DSL file extension when the command
                            arguments contains directory, (default: mir)
 -h,--help                  print help message
 -n,--interval <interval>   specify update interval in seconds
 -p,--port <port>           specify server port (default 8080)

For more information, see https://github.com/six-ddc/mirage
```

### Examples

#### hello world

```shell
mirage -c 'get("/hello") { resp.println "world!"}'
# curl http://127.0.0.1:8080/hello
# world!
```

#### static file server

```shell
mirage -c 'get("/files/**") { resp.index "." }'
# curl http://127.0.0.1:8080/files/pom.xml
```

#### mock user data

* create file `user.mir` and typing

```groovy
handle path: '/user/{uid}/get', method: "GET", {

    sleep 100.millisecond

    resp.json {
        id req.pathVariables.uid
        name random.forRegex(/[A-Z][a-z]{3,10}/)
        t new Date()
        data {
            contact 1..2, {
                id it
                name faker.name().name()
                address faker.address().fullAddress()
                phone faker.phoneNumber().cellPhone()
            }
        }
    }
}

get('/user/{uid}/get2') {

    // and also you can write json response directly
    resp.eval """{
        "id": "${req.pathVariables.uid}",
        "name": "${random.forRegex(/[A-Z][a-z]{3,10}/)}",
        "t": "${new Date()}",
        "data": {
            "contact": [
                {
                    "id": 1,
                    "name": "${faker.name().name()}",
                    "address": "${faker.address().fullAddress()}",
                    "phone": "${faker.phoneNumber().cellPhone()}"
                },
                {
                    "id": 2,
                    "name": "${faker.name().name()}",
                    "address": "${faker.address().fullAddress()}",
                    "phone": "${faker.phoneNumber().cellPhone()}"
                }
            ]
        }
    }"""
}
```

* run

```shell
mirage user.mir
```

* test server

```json
$ curl -s http://127.0.0.1:8080/user/1234/get | jq .
$ curl -s http://127.0.0.1:8080/user/1234/get2 | jq .
{
  "id": "1234",
  "name": "Astn",
  "t": "2019-11-17T04:04:55+0000",
  "data": {
    "contact": [
      {
        "id": 1,
        "name": "Bryan Bashirian",
        "address": "22474 Bashirian Ways, New Thanhfurt, MA 32424-5437",
        "phone": "(973) 158-7100"
      },
      {
        "id": 2,
        "name": "Charley Jast",
        "address": "526 Corkery Rue, Lake Moises, MO 28747",
        "phone": "1-418-198-2865"
      }
    ]
  }
}
```

## Performance

My test machine is an Intel Core i5 2.7 GHz, 2 CPUs, 8 GB Memory

```shell
$ mirage -c 'get("/hello") { resp.println "world!"}'
```

```shell
$ wrk -c 10 -t 10 -d 1s --latency http://127.0.0.1:8080/hello
Running 1s test @ http://127.0.0.1:8080/hello
  10 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   847.57us    1.45ms  27.75ms   93.86%
    Req/Sec     1.74k   528.60     3.22k    77.27%
  Latency Distribution
     50%  457.00us
     75%  815.00us
     90%    1.63ms
     99%    7.03ms
  19082 requests in 1.10s, 2.31MB read
Requests/sec:  17335.71
Transfer/sec:      2.10MB
```

## License

[MIT](https://tldrlegal.com/license/mit-license)
