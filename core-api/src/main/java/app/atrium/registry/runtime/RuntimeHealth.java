package app.atrium.registry.runtime;

/** RUNNING | IDLE | STOPPED | ERROR(reason) per 13 §3.1. */
public record RuntimeHealth(Status status, String reason) {

    public enum Status { RUNNING, IDLE, STOPPED, ERROR }

    public static RuntimeHealth running() { return new RuntimeHealth(Status.RUNNING, null); }
    public static RuntimeHealth idle() { return new RuntimeHealth(Status.IDLE, null); }
    public static RuntimeHealth stopped() { return new RuntimeHealth(Status.STOPPED, null); }
    public static RuntimeHealth error(String reason) { return new RuntimeHealth(Status.ERROR, reason); }
}
