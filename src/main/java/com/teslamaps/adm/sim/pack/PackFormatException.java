// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

public class PackFormatException extends RuntimeException {
    public PackFormatException(String message, Throwable cause) { super(message, cause); }
    public PackFormatException(String message) { this(message, null); }
}
