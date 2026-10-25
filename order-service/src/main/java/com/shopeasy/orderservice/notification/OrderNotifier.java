package com.shopeasy.orderservice.notification;

import com.shopeasy.orderservice.dto.OrderItemResponse;
import com.shopeasy.orderservice.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    private final MailService mailService;
    private final String frontendUrl;

    public OrderNotifier(MailService mailService, @Value("${app.cors.allowed-origin}") String frontendUrl) {
        this.mailService = mailService;
        this.frontendUrl = frontendUrl;
    }

    // Runs on a separate thread. Whatever happens here can never affect the order itself.
    @Async("notificationExecutor")
    public void orderPlaced(OrderResponse order) {
        try {
            mailService.send(order.customerEmail(), "Your ShopEasy order #" + order.id(), buildBody(order));
        } catch (RuntimeException e) {
            log.error("Could not send the confirmation email for order {}: {}", order.id(), e.getMessage());
        }
    }

    private String buildBody(OrderResponse order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(order.customerName()).append(",\n\n");
        sb.append("Thanks for your order. Order #").append(order.id()).append(" is confirmed.\n\n");
        sb.append("Items\n");
        for (OrderItemResponse item : order.items()) {
            sb.append("- ").append(item.productName())
                    .append(" x ").append(item.quantity())
                    .append(": INR ").append(money(item.lineTotal())).append('\n');
        }
        sb.append("\nTotal: INR ").append(money(order.totalAmount())).append("\n\n");
        sb.append("You can see this order any time under My orders after you sign in: ")
                .append(frontendUrl).append("/orders\n\n");
        sb.append("If you did not place this order, tell us right away: ")
                .append(frontendUrl).append("/contact\n");
        return sb.toString();
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.ENGLISH, "%,.2f", value);
    }
}