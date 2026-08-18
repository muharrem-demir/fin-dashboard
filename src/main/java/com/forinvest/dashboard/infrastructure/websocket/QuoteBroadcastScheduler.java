package com.forinvest.dashboard.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.forinvest.dashboard.application.usecase.BroadcastQuoteUpdatesUseCase;

/**
 * Drives the feed. The clock is infrastructure; what happens on each tick is the use case.
 *
 * <p>{@code fixedDelay}, not {@code fixedRate}: the delay is counted from the end of the previous
 * tick, so a slow upstream call cannot cause ticks to pile up behind each other and turn one slow
 * provider into a queue of concurrent requests to it. The consequence is that the interval is the
 * gap between updates rather than a guaranteed period, which is the right trade for a price feed.
 *
 * <p>A tick that throws is logged by Spring's scheduler and the next one still runs, so a transient
 * failure cannot stop the feed for everybody. Provider outages do not even get that far — the use
 * case turns them into a message for each subscriber.
 */
@Component
class QuoteBroadcastScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteBroadcastScheduler.class);

    private final BroadcastQuoteUpdatesUseCase broadcastQuoteUpdates;

    QuoteBroadcastScheduler(BroadcastQuoteUpdatesUseCase broadcastQuoteUpdates) {
        this.broadcastQuoteUpdates = broadcastQuoteUpdates;
    }

    @Scheduled(fixedDelayString = "${dashboard.quotes.stream.interval-millis}")
    void publishQuoteUpdates() {
        int published = broadcastQuoteUpdates.execute();
        if (published > 0) {
            LOG.debug("Published a quote update to {} subscribers", published);
        }
    }
}
