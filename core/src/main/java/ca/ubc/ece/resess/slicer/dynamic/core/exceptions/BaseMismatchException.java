package ca.ubc.ece.resess.slicer.dynamic.core.exceptions;

public class BaseMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public BaseMismatchException () {
        super("Bases of access paths mismatch!", new Throwable());
    }
}
