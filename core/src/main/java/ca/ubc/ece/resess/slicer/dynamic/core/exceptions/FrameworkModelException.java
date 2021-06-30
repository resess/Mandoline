package ca.ubc.ece.resess.slicer.dynamic.core.exceptions;

public class FrameworkModelException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = -1739440583893435651L;
    

    public FrameworkModelException (String message) {
        super(message, new Throwable());
    }
}
