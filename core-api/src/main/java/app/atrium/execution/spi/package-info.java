/**
 * The LLM provider SPI — records and interfaces are VERBATIM from
 * atrium-docs/13 §1.1 (normative; renames are a contract change: update the
 * doc first). Business code depends only on {@code LlmClient}; providers
 * implement {@code LlmProvider} and are discovered via Spring.
 */
package app.atrium.execution.spi;
