package app.atrium.execution.spi;

/** SPI. One bean per provider; discovered via Spring List&lt;LlmProvider&gt;. (13 §1.1, normative) */
public interface LlmProvider {

    String id();                              // 'anthropic' — matches model_catalog.provider

    LlmResult complete(LlmRequest r) throws LlmException;
}
