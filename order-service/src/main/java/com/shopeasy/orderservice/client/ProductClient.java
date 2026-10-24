package com.shopeasy.orderservice.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.shopeasy.orderservice.dto.ProductResponse;
import com.shopeasy.orderservice.dto.StockUpdateRequest;
import com.shopeasy.orderservice.exception.InsufficientStockException;
import com.shopeasy.orderservice.exception.ProductNotFoundException;
import com.shopeasy.orderservice.exception.ProductServiceUnavailableException;

@Component
public class ProductClient {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final RestClient restClient;
    private final String internalKey;

    public ProductClient(@Value("${product.service.url}") String baseUrl,
                         @Value("${app.internal.api-key}") String internalKey) {
        if (internalKey.length() < 32) {
            throw new IllegalStateException("app.internal.api-key must be at least 32 characters. Set it in application-local.yml.");
        }
        this.internalKey = internalKey;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public ProductResponse getProduct(Long id) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", id)
                    .retrieve()
                    .body(ProductResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ProductServiceUnavailableException();
        }
    }

    public void reduceStock(Long id, int quantity) {
        try {
            restClient.patch()
                    .uri("/api/products/{id}/stock", id)
                    .header(INTERNAL_KEY_HEADER, internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockUpdateRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(id);
        } catch (HttpClientErrorException.Conflict e) {
            throw new InsufficientStockException("Insufficient stock for product id " + id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ProductServiceUnavailableException();
        }
    }

    public void restoreStock(Long id, int quantity) {
        try {
            restClient.patch()
                    .uri("/api/products/{id}/stock/restore", id)
                    .header(INTERNAL_KEY_HEADER, internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockUpdateRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ProductServiceUnavailableException();
        }
    }
}