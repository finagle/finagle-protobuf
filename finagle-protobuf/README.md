
# Finagle Protobuf

This is a [Finagle][finagle] protocol which allows for the use of [Google Protocol Buffers][protobuf] for message and RPC formatting.

[finagle]: https://github.com/twitter/finagle
[protobuf]: https://developers.google.com/protocol-buffers/

## Structure

This protocol brokers between Finagle and Protobuf, so derives some parts of its structure from each of those sources, as
well as having some of its own constructs.

- Codec - Based on Finagle, matches up decoders and encoders.
- Decoder and Encoder - Interact with the Netty Pipeline.
- RpcChannel - the client-side implementation, is called from within a protobuf service stub and calls finagle across the wire.
- RpcServer and ServiceDispatcher - the server-side implementation, calls the service implementation and brokers the response back
to finagle.
- ExceptionHandlers - convert exceptions to/from protobuf messages for communication over the wire.
- RpcControllerWithOnFailureCallback - brokers failures into callbacks
- MethodService - maps a method into a code for communication over the wire. Based on hashing the method name.
- RpcFactory - create stubs and servers.

The basic flow, is for a client to create a client stub for a service. Calls to this stub call the underlying RpcChannel to
make the finagle call over the wire. RpcChannel uses the Finagle ClientBuilder internally and maintains this Finagle client.
After the server responds with success or failure, the RpcChannel sends failures to the RpcController and successes to the
RpcCallback.

Internally at Tendril, the SIF implements both RpcController and RpcCallback with the same class which converts these into
a single Guava ListenableFuture.

On the server side, the RpcServer uses the Finagle ServerBuilder to set up a server and delegate calls to the ServiceDispatcher.
The ServiceDispatcher makes a call to the appropriate service implementation, providing a callback which resolves the Finagle
Future. Successes resolve the future directly, while failures resolve the future with a mapped failure (in this sense, the
Future finagle receives from the call is never failed, only resolved with successes representing application-level failures).

Finagle-protobuf does nothing special or different with configuration. All configs are passed as-is to the ClientBuilder or
ServerBuilder.


## Sample

##### Sample Protobuf based RPC (see RpcIntegrationTest for more samples)

```java
RpcFactory factory = new RpcFactoryImpl();

ServerBuilder serverBuilder = ServerBuilder.get()
    .maxConcurrentRequests(10);

ClientBuilder clientBuilder = ClientBuilder
    .get()
    .hosts(String.format("localhost:%s", port))
    .hostConnectionLimit(1)
    .retries(2)
    .requestTimeout(
            Duration.apply(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS));


SampleWeatherServiceImpl service = new SampleWeatherServiceImpl(80,
        null);
RpcServer server = factory.createServer(serverBuilder, port, service,
        executorService);
WeatherService stub = factory.<WeatherService> createStub(
        clientBuilder, WeatherService.newStub(null), executorService);

RpcControllerWithOnFailureCallback controller = (RpcControllerWithOnFailureCallback) factory
        .createController();

final CountDownLatch l = new CountDownLatch(1);
final AtomicInteger result = new AtomicInteger();
GetWeatherForecastRequest request = GetWeatherForecastRequest
        .newBuilder().setZip("80301").build();
stub.getWeatherForecast(
        controller.onFailure(new RpcCallback<Throwable>() {

            @Override
            public void run(Throwable e) {
            }
        }), request, new RpcCallback<GetWeatherForecastResponse>() {

            @Override
            public void run(GetWeatherForecastResponse resp) {
                result.set(resp.getTemp());
                l.countDown();
            }
        });

l.await(CLIENT_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS);
server.close(new Duration(TimeUnit.SECONDS.toNanos(1)));

assertEquals(service.getTemperature(), result.get());
```
		


