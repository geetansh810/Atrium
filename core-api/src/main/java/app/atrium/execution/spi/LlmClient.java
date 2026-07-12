package app.atrium.execution.spi;

/** The only entry point business code uses. (13 §1.1, normative) */
public interface LlmClient {

    LlmResult complete(LlmRequest r) throws LlmException;   // routes by r.provider()
}
