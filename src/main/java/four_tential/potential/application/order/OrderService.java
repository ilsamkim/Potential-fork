package four_tential.potential.application.order;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(UUID memberId, OrderCreateRequest request) {
        Order order = Order.register(
                memberId,
                request.courseId(),
                request.orderCount(),
                request.priceSnap(),
                request.titleSnap()
        );
        return orderRepository.save(order);
    }
}
