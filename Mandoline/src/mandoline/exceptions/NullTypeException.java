package mandoline.exceptions;

public class NullTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public NullTypeException () {
        super("Null type!", new Throwable());
    }
}
