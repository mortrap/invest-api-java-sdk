package ru.russianinvestments.piapi.core;

import com.google.protobuf.Timestamp;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import ru.russianinvestments.piapi.core.exception.ReadonlyModeViolationException;
import ru.russianinvestments.piapi.core.utils.DateUtils;
import ru.russianinvestments.piapi.contract.v1.CancelOrderRequest;
import ru.russianinvestments.piapi.contract.v1.CancelOrderResponse;
import ru.russianinvestments.piapi.contract.v1.GetOrderStateRequest;
import ru.russianinvestments.piapi.contract.v1.GetOrdersRequest;
import ru.russianinvestments.piapi.contract.v1.GetOrdersResponse;
import ru.russianinvestments.piapi.contract.v1.OrderDirection;
import ru.russianinvestments.piapi.contract.v1.OrderState;
import ru.russianinvestments.piapi.contract.v1.OrderType;
import ru.russianinvestments.piapi.contract.v1.OrdersServiceGrpc;
import ru.russianinvestments.piapi.contract.v1.PostOrderRequest;
import ru.russianinvestments.piapi.contract.v1.PostOrderResponse;
import ru.russianinvestments.piapi.contract.v1.Quotation;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OrdersServiceTest extends GrpcClientTester<OrdersService> {

  @Rule
  public ExpectedException futureThrown = ExpectedException.none();

  @Override
  protected OrdersService createClient(Channel channel) {
    return new OrdersService(
      OrdersServiceGrpc.newBlockingStub(channel),
      OrdersServiceGrpc.newStub(channel),
      false);
  }


  @Test
  void postOrder_Test() {
    var expected = PostOrderResponse.newBuilder()
      .setOrderId("orderId")
      .setFigi("figi")
      .setDirection(OrderDirection.ORDER_DIRECTION_BUY)
      .build();
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class, delegatesTo(
      new OrdersServiceGrpc.OrdersServiceImplBase() {
        @Override
        public void postOrder(PostOrderRequest request,
                              StreamObserver<PostOrderResponse> responseObserver) {
          responseObserver.onNext(expected);
          responseObserver.onCompleted();
        }
      }));
    var service = mkClientBasedOnServer(grpcService);

    var inArg = PostOrderRequest.newBuilder()
      .setAccountId("accountId")
      .setInstrumentId(expected.getFigi())
      .setDirection(expected.getDirection())
      .setPrice(Quotation.newBuilder().build())
      .build();
    var actualSync = service.postOrderSync(
      inArg.getInstrumentId(), inArg.getQuantity(), inArg.getPrice(), inArg.getDirection(),
      inArg.getAccountId(), inArg.getOrderType(), inArg.getOrderId());
    var actualAsync = service.postOrder(
        inArg.getInstrumentId(), inArg.getQuantity(), inArg.getPrice(), inArg.getDirection(),
        inArg.getAccountId(), inArg.getOrderType(), inArg.getOrderId())
      .join();

    assertEquals(expected, actualSync);
    assertEquals(expected, actualAsync);

    verify(grpcService, times(2)).postOrder(eq(inArg), any());
  }

  @Test
  void postOrder_forbiddenInReadonly_Test() {
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class);
    var readonlyService = mkClientBasedOnServer(
      grpcService,
      channel -> new OrdersService(
        OrdersServiceGrpc.newBlockingStub(channel),
        OrdersServiceGrpc.newStub(channel),
        true));

    assertThrows(
      ReadonlyModeViolationException.class,
      () -> readonlyService.postOrderSync(
        "", 0, Quotation.getDefaultInstance(), OrderDirection.ORDER_DIRECTION_UNSPECIFIED,
        "", OrderType.ORDER_TYPE_UNSPECIFIED, ""));
    futureThrown.expect(CompletionException.class);
    futureThrown.expectCause(IsInstanceOf.instanceOf(ReadonlyModeViolationException.class));
    assertThrows(ReadonlyModeViolationException.class, () -> readonlyService.postOrder(
      "", 0, Quotation.getDefaultInstance(), OrderDirection.ORDER_DIRECTION_UNSPECIFIED,
      "", OrderType.ORDER_TYPE_UNSPECIFIED, ""));
  }

  @Test
  void getOrders_Test() {
    var accountId = "accountId";
    var expected = GetOrdersResponse.newBuilder()
      .addOrders(OrderState.newBuilder().setOrderId("orderId").build())
      .build();
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class, delegatesTo(
      new OrdersServiceGrpc.OrdersServiceImplBase() {
        @Override
        public void getOrders(GetOrdersRequest request,
                              StreamObserver<GetOrdersResponse> responseObserver) {
          responseObserver.onNext(expected);
          responseObserver.onCompleted();
        }
      }));
    var service = mkClientBasedOnServer(grpcService);

    var actualSync = service.getOrdersSync(accountId);
    var actualAsync = service.getOrders(accountId).join();

    assertIterableEquals(expected.getOrdersList(), actualSync);
    assertIterableEquals(expected.getOrdersList(), actualAsync);

    var inArg = GetOrdersRequest.newBuilder()
      .setAccountId(accountId)
      .build();
    verify(grpcService, times(2)).getOrders(eq(inArg), any());
  }

  @Test
  void cancelOrder_Test() {
    var accountId = "accountId";
    var orderId = "orderId";
    var expected = CancelOrderResponse.newBuilder()
      .setTime(Timestamp.newBuilder().setSeconds(1234567890).setNanos(0).build())
      .build();
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class, delegatesTo(
      new OrdersServiceGrpc.OrdersServiceImplBase() {
        @Override
        public void cancelOrder(CancelOrderRequest request,
                                StreamObserver<CancelOrderResponse> responseObserver) {
          responseObserver.onNext(expected);
          responseObserver.onCompleted();
        }
      }));
    var service = mkClientBasedOnServer(grpcService);

    var actualSync = service.cancelOrderSync(accountId, orderId);
    var actualAsync = service.cancelOrder(accountId, orderId).join();

    assertEquals(DateUtils.timestampToInstant(expected.getTime()), actualSync);
    assertEquals(DateUtils.timestampToInstant(expected.getTime()), actualAsync);

    var inArg = CancelOrderRequest.newBuilder()
      .setAccountId(accountId)
      .setOrderId(orderId)
      .build();
    verify(grpcService, times(2)).cancelOrder(eq(inArg), any());
  }

  @Test
  void cancelOrder_forbiddenInReadonly_Test() {
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class);
    var readonlyService = mkClientBasedOnServer(
      grpcService,
      channel -> new OrdersService(
        OrdersServiceGrpc.newBlockingStub(channel),
        OrdersServiceGrpc.newStub(channel),
        true));

    assertThrows(
      ReadonlyModeViolationException.class,
      () -> readonlyService.cancelOrderSync("", ""));
    futureThrown.expect(CompletionException.class);
    futureThrown.expectCause(IsInstanceOf.instanceOf(ReadonlyModeViolationException.class));
    assertThrows(ReadonlyModeViolationException.class, () -> readonlyService.cancelOrder("", ""));
  }

  @Test
  void getOrderState_Test() {
    var accountId = "accountId";
    var orderId = "orderId";
    var expected = OrderState.newBuilder()
      .setOrderId(orderId)
      .build();
    var grpcService = mock(OrdersServiceGrpc.OrdersServiceImplBase.class, delegatesTo(
      new OrdersServiceGrpc.OrdersServiceImplBase() {
        @Override
        public void getOrderState(GetOrderStateRequest request,
                                  StreamObserver<OrderState> responseObserver) {
          responseObserver.onNext(expected);
          responseObserver.onCompleted();
        }
      }));
    var service = mkClientBasedOnServer(grpcService);

    var actualSync = service.getOrderStateSync(accountId, orderId);
    var actualAsync = service.getOrderState(accountId, orderId).join();

    assertEquals(expected, actualSync);
    assertEquals(expected, actualAsync);

    var inArg = GetOrderStateRequest.newBuilder()
      .setAccountId(accountId)
      .setOrderId(orderId)
      .build();
    verify(grpcService, times(2)).getOrderState(eq(inArg), any());
  }

}
