package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.QuoteSubscriptions;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;
import com.forinvest.dashboard.domain.port.QuoteUpdatePublisher;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

@ExtendWith(MockitoExtension.class)
class BroadcastQuoteUpdatesUseCaseTest {

    private static final SubscriberId ONE = SubscriberId.of("session-1");
    private static final SubscriberId TWO = SubscriberId.of("session-2");

    @Mock
    private QuoteSubscriptionRegistry subscriptionRegistry;

    @Mock
    private StockQuoteProvider stockQuoteProvider;

    @Mock
    private QuoteUpdatePublisher quoteUpdatePublisher;

    @Captor
    private ArgumentCaptor<List<Ticker>> requestedTickers;

    @Captor
    private ArgumentCaptor<StockQuoteLookup> published;

    private BroadcastQuoteUpdatesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BroadcastQuoteUpdatesUseCase(subscriptionRegistry, stockQuoteProvider, quoteUpdatePublisher);
    }

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal(price), new BigDecimal("100"));
    }

    private static QuoteSubscription subscription(SubscriberId subscriber, String... symbols) {
        return QuoteSubscription.of(subscriber, List.of(symbols));
    }

    @Test
    @DisplayName("fetches the union of every subscription in a single call, however many clients there are")
    void fetchesTheUnionOnce() {
        when(subscriptionRegistry.current())
                .thenReturn(QuoteSubscriptions.of(
                        List.of(subscription(ONE, "AAPL", "MSFT"), subscription(TWO, "aapl", "TSLA"))));
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenReturn(List.of(quote("AAPL", "110"), quote("MSFT", "120"), quote("TSLA", "130")));

        int published = useCase.execute();

        verify(stockQuoteProvider, times(1)).findQuotes(requestedTickers.capture());
        assertThat(requestedTickers.getValue())
                .containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA"));
        assertThat(published).isEqualTo(2);
    }

    @Test
    @DisplayName("gives each subscriber only the symbols it asked for, in its own order")
    void publishesEachSubscribersOwnShare() {
        when(subscriptionRegistry.current())
                .thenReturn(
                        QuoteSubscriptions.of(List.of(subscription(ONE, "AAPL", "MSFT"), subscription(TWO, "TSLA"))));
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenReturn(List.of(quote("AAPL", "110"), quote("MSFT", "120"), quote("TSLA", "130")));

        useCase.execute();

        verify(quoteUpdatePublisher).publishQuotes(eq(ONE), published.capture());
        assertThat(published.getValue().quotes())
                .extracting(StockQuote::ticker)
                .containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));

        verify(quoteUpdatePublisher).publishQuotes(eq(TWO), published.capture());
        assertThat(published.getValue().quotes()).extracting(StockQuote::ticker).containsExactly(Ticker.of("TSLA"));
    }

    @Test
    @DisplayName("tells a subscriber which of its own symbols the provider had no data for")
    void reportsUnresolvedSymbolsPerSubscriber() {
        when(subscriptionRegistry.current())
                .thenReturn(QuoteSubscriptions.of(List.of(subscription(ONE, "AAPL", "NOSUCH"))));
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110")));

        useCase.execute();

        verify(quoteUpdatePublisher).publishQuotes(eq(ONE), published.capture());
        assertThat(published.getValue().unresolved()).containsExactly(Ticker.of("NOSUCH"));
    }

    @Test
    @DisplayName("does not call the provider at all while nobody is listening")
    void fetchesNothingWithoutSubscribers() {
        when(subscriptionRegistry.current()).thenReturn(QuoteSubscriptions.none());

        assertThat(useCase.execute()).isZero();

        verifyNoInteractions(stockQuoteProvider, quoteUpdatePublisher);
    }

    @Test
    @DisplayName("turns a provider outage into a message for every subscriber, not a failed tick")
    void reportsProviderOutageToEverySubscriber() {
        when(subscriptionRegistry.current())
                .thenReturn(QuoteSubscriptions.of(List.of(subscription(ONE, "AAPL"), subscription(TWO, "TSLA"))));
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenThrow(new StockQuoteUnavailableException("upstream refused the request"));

        int published = useCase.execute();

        verify(quoteUpdatePublisher).publishUnavailable(ONE, "upstream refused the request");
        verify(quoteUpdatePublisher).publishUnavailable(TWO, "upstream refused the request");
        verify(quoteUpdatePublisher, never()).publishQuotes(any(), any());
        assertThat(published).isEqualTo(2);
    }

    @Test
    @DisplayName("keeps ticking after an outage: the next tick is the retry")
    void recoversOnTheNextTick() {
        when(subscriptionRegistry.current()).thenReturn(QuoteSubscriptions.of(List.of(subscription(ONE, "AAPL"))));
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenThrow(new StockQuoteUnavailableException("upstream refused the request"))
                .thenReturn(List.of(quote("AAPL", "110")));

        useCase.execute();
        useCase.execute();

        verify(quoteUpdatePublisher).publishUnavailable(eq(ONE), anyString());
        verify(quoteUpdatePublisher).publishQuotes(eq(ONE), published.capture());
        assertThat(published.getValue().quotes()).hasSize(1);
    }
}
