package four_tential.potential.presentation.order.dto;

/**
 * 주문 처리 결과를 나타내는 상위 인터페이스
 */
public sealed interface OrderPlaceResult 
    permits OrderCreateResponse, OrderWaitingResponse {
}
