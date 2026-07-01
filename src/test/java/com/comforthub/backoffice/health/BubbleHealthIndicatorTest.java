package com.comforthub.backoffice.health;

import com.comforthub.backoffice.client.BubbleClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BubbleHealthIndicatorTest {

    @Mock
    private BubbleClient bubbleClient;

    @Test
    void pingSucceeds_reportsUp() {
        doNothing().when(bubbleClient).ping();

        Health health = new BubbleHealthIndicator(bubbleClient).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "Bubble Data API");
        verify(bubbleClient).ping();
    }

    @Test
    void pingThrows_reportsDown_andDoesNotPropagate() {
        doThrow(new RuntimeException("connection refused")).when(bubbleClient).ping();

        Health health = new BubbleHealthIndicator(bubbleClient).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Bubble Data API");
        assertThat(health.getDetails()).containsEntry("error", "connection refused");
    }

    @Test
    void pingThrowsWithNullMessage_stillReportsDown_andDoesNotPropagate() {
        doThrow(new RuntimeException()).when(bubbleClient).ping();

        Health health = new BubbleHealthIndicator(bubbleClient).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
