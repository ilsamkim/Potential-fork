package four_tential.potential.presentation.order.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCreateResponse(
        UUID orderId,
        String status,
        LocalDateTime expireAt,
        String message
) implements OrderPlaceResult {
}
