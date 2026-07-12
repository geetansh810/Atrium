package app.atrium.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The list-endpoint envelope (04 rule 3): {@code {data, nextCursor?}}. */
public record PageEnvelope<T>(
        List<T> data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor) {
}
