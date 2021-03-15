package mandoline.exceptions;

public class EmptyAccessPathException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public EmptyAccessPathException () {
        super("Empty access path!", new Throwable());
    }
}
