package ca.ubc.ece.resess.slicer.dynamic.core.exceptions;

public class NullTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public NullTypeException () {
        super("Null type!", new Throwable());
    }
}
